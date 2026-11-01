import { apiRequest, ApiError } from './http'
import { isListing } from './catalog'
import type { CatalogListing, CatalogSource } from './catalog-types'
import { isInsight, isRouteCommute } from './ai'
import type { Insight, RouteCommute, TravelMode, Workplace } from './ai'

export type PlanCoverage = 'COMPLETE' | 'PARTIAL'
export interface PlanSummary {
  id: string; versionId: string; versionNo: number; title: string
  primaryListingId: string; primaryHomeName: string; primaryDistrict: string; primaryTown: string
  primaryRent: number; primaryCommuteMinutes: number | null; homeCount: number
  coverage: PlanCoverage; capturedAt: string; updatedAt: string
}
export interface PlanVersion { id: string; versionNo: number; homeCount: number; coverage: PlanCoverage; capturedAt: string }
export interface PlanSnapshot {
  capturedAt: string; snapshotKind: 'HISTORICAL'; realtime: false; dataKind: 'SIMULATED'
  criteria: Record<string, unknown>; limitations: string[]; source: CatalogSource
  commute?: { state: 'CHECKED'; mode: TravelMode; maxMinutes?: number; workplace?: Workplace; checkedCount: number; candidateCount: number; unverifiedCount: number; coverage: PlanCoverage; estimated: true } | null
  recommendations: { listing: CatalogListing; reasons: string[]; commute?: RouteCommute | null; insight?: Insight | null }[]
}
export interface PlanDetail { plan: PlanSummary; versions: PlanVersion[]; snapshot: PlanSnapshot }
export interface CompareSide {
  planId: string; versionId: string; versionNo: number; title: string; capturedAt: string
  primaryListingId: string; primaryHomeName: string; primaryDistrict: string; primaryTown: string
  rent: number | null; deposit: number | null; paymentMethod: string | null
  propertyFee: number | null; propertyFeePeriod: string | null; knownInitialPayment: number | null
  commuteMode: TravelMode | null; commuteMinutes: number | null; commuteCheckedAt: string | null
  coverage: PlanCoverage; homeCount: number; partialCoverage: boolean
}
export interface PlanComparison {
  left: CompareSide; right: CompareSide
  rentDifference: number | null; commuteMinutesDifference: number | null; knownInitialPaymentDifference: number | null
  notes: string[]
}
export interface SavePlanInput { searchToken: string; title?: string; listingIds?: string[]; planId?: string }

const nonnegative = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v) && v >= 0
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isInteger(v) && v >= 0
const timestamp = (v: unknown): v is string => typeof v === 'string' && Number.isFinite(Date.parse(v))
const coverage = (v: unknown): v is PlanCoverage => v === 'COMPLETE' || v === 'PARTIAL'
const text = (v: unknown): v is string => typeof v === 'string'
const nullableNumber = (v: unknown) => v === null || nonnegative(v)

function validSummary(s: PlanSummary) {
  return !!s && typeof s.id === 'string' && typeof s.versionId === 'string' && integer(s.versionNo) && s.versionNo >= 1
    && text(s.title) && text(s.primaryListingId) && text(s.primaryHomeName) && text(s.primaryDistrict) && text(s.primaryTown)
    && nonnegative(s.primaryRent) && (s.primaryCommuteMinutes === null || nonnegative(s.primaryCommuteMinutes))
    && integer(s.homeCount) && s.homeCount >= 1 && coverage(s.coverage) && timestamp(s.capturedAt) && timestamp(s.updatedAt)
}
function validVersion(v: PlanVersion) {
  return !!v && typeof v.id === 'string' && integer(v.versionNo) && v.versionNo >= 1 && integer(v.homeCount) && v.homeCount >= 1
    && coverage(v.coverage) && timestamp(v.capturedAt)
}
function validSnapshot(s: PlanSnapshot) {
  if (!s || s.dataKind !== 'SIMULATED' || s.snapshotKind !== 'HISTORICAL' || s.realtime !== false) return false
  if (!timestamp(s.capturedAt) || !s.criteria || typeof s.criteria !== 'object' || !Array.isArray(s.limitations) || !s.limitations.every(text)) return false
  if (!s.source || s.source.dataKind !== 'SIMULATED' || typeof s.source.id !== 'string' || !integer(s.source.rowCount) || s.source.rowCount < 1 || typeof s.source.sha256 !== 'string' || !/^[a-f\d]{64}$/i.test(s.source.sha256)) return false
  if (!Array.isArray(s.recommendations) || s.recommendations.length < 1 || s.recommendations.length > 6) return false
  if (!s.recommendations.every(r => !!r && isListing(r.listing) && Array.isArray(r.reasons) && r.reasons.every(text) && (r.commute == null || isRouteCommute(r.commute)) && (r.insight == null || isInsight(r.insight, r.listing)))) return false
  if (s.commute != null) {
    const c = s.commute
    if (c.state !== 'CHECKED' || !['WALKING', 'CYCLING', 'TRANSIT', 'DRIVING'].includes(c.mode) || !integer(c.checkedCount) || !integer(c.candidateCount) || !integer(c.unverifiedCount) || !coverage(c.coverage) || c.estimated !== true) return false
  }
  return true
}
function validDetail(r: PlanDetail) {
  return !!r && validSummary(r.plan) && Array.isArray(r.versions) && r.versions.every(validVersion) && validSnapshot(r.snapshot)
}
function validSide(s: CompareSide) {
  return !!s && text(s.planId) && text(s.versionId) && integer(s.versionNo) && text(s.title) && timestamp(s.capturedAt)
    && text(s.primaryListingId) && text(s.primaryHomeName) && text(s.primaryDistrict) && text(s.primaryTown)
    && nullableNumber(s.rent) && nullableNumber(s.deposit) && (s.paymentMethod === null || text(s.paymentMethod))
    && nullableNumber(s.propertyFee) && (s.propertyFeePeriod === null || text(s.propertyFeePeriod))
    && nullableNumber(s.knownInitialPayment) && (s.commuteMode === null || ['WALKING', 'CYCLING', 'TRANSIT', 'DRIVING'].includes(s.commuteMode))
    && nullableNumber(s.commuteMinutes) && (s.commuteCheckedAt === null || timestamp(s.commuteCheckedAt))
    && coverage(s.coverage) && integer(s.homeCount) && typeof s.partialCoverage === 'boolean'
}
function validComparison(r: PlanComparison) {
  return !!r && validSide(r.left) && validSide(r.right)
    && (r.rentDifference === null || typeof r.rentDifference === 'number' && Number.isFinite(r.rentDifference))
    && (r.commuteMinutesDifference === null || typeof r.commuteMinutesDifference === 'number' && Number.isFinite(r.commuteMinutesDifference))
    && (r.knownInitialPaymentDifference === null || typeof r.knownInitialPaymentDifference === 'number' && Number.isFinite(r.knownInitialPaymentDifference))
    && Array.isArray(r.notes) && r.notes.every(text)
}
const invalid = () => new ApiError('服务返回的居住方案数据不完整，请重试')

export async function listPlans(token: string): Promise<PlanSummary[]> {
  const r = await apiRequest<PlanSummary[]>('/me/plans', { token })
  if (!Array.isArray(r) || !r.every(validSummary)) throw invalid()
  return r
}
export async function getPlan(token: string, id: string): Promise<PlanDetail> {
  const r = await apiRequest<PlanDetail>(`/me/plans/${encodeURIComponent(id)}`, { token })
  if (!validDetail(r)) throw invalid()
  return r
}
export async function listVersions(token: string, id: string): Promise<PlanVersion[]> {
  const r = await apiRequest<PlanVersion[]>(`/me/plans/${encodeURIComponent(id)}/versions`, { token })
  if (!Array.isArray(r) || !r.every(validVersion)) throw invalid()
  return r
}
export async function savePlan(token: string, input: SavePlanInput): Promise<PlanDetail> {
  const r = await apiRequest<PlanDetail>('/me/plans', { method: 'POST', token, data: input, timeout: 30000 })
  if (!validDetail(r)) throw invalid()
  return r
}
export async function activateVersion(token: string, planId: string, versionId: string): Promise<PlanDetail> {
  const r = await apiRequest<PlanDetail>(`/me/plans/${encodeURIComponent(planId)}/active`, { method: 'PUT', token, data: { versionId } })
  if (!validDetail(r)) throw invalid()
  return r
}
export async function deletePlan(token: string, planId: string): Promise<void> {
  await apiRequest<void>(`/me/plans/${encodeURIComponent(planId)}`, { method: 'DELETE', token })
}
export async function comparePlans(token: string, left: string, right: string): Promise<PlanComparison> {
  const query = `left=${encodeURIComponent(left)}&right=${encodeURIComponent(right)}`
  const r = await apiRequest<PlanComparison>(`/me/plans/compare?${query}`, { token })
  if (!validComparison(r)) throw invalid()
  return r
}