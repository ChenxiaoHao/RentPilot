import { apiRequest, ApiError } from './http'

export type LifesimStatus = 'READY' | 'INCOMPLETE' | 'NEEDS_COMMUTE'
export type LifesimSource = 'FACT' | 'USER' | 'DERIVED' | 'ASSUMED' | 'UNKNOWN'
export type DailySpendBand = 'UNDER_1000' | '1000_2000' | '2000_3000' | 'OVER_3000'
export type TravelMode = 'WALKING' | 'CYCLING' | 'TRANSIT' | 'DRIVING'

export interface LifesimAnswers {
  commuteMinutes: number | null
  workStart: string | null; sleepTime: string | null; overtimeDays: number | null; overtimeEnd: string | null
  dailySpendBand: DailySpendBand | null; fixedCost: number | null; cookingDays: number | null
}
export interface LifesimMoment { key: string; label: string; time: string; source: LifesimSource; detail: string }
export interface LifesimCosts {
  monthlyRent: number | null; deposit: number | null; propertyFee: number | null; propertyFeePeriod: string | null
  knownInitialPayment: number | null; fixedCost: number | null; dailySpendBand: string | null; dailySpendLabel: string
  monthlyKnown: number | null; monthlyLow: number | null; monthlyHigh: number | null
}
export interface LifesimView {
  planId: string; versionId: string; versionNo: number; title: string; capturedAt: string
  status: LifesimStatus; mode: TravelMode | null; commuteMinutes: number | null
  answers: LifesimAnswers; assumptions: Record<string, unknown>; timeline: LifesimMoment[]
  costs: LifesimCosts; sources: Record<string, LifesimSource>; limitations: string[]; updatedAt: string | null
}

const sources: LifesimSource[] = ['FACT', 'USER', 'DERIVED', 'ASSUMED', 'UNKNOWN']
const bands: DailySpendBand[] = ['UNDER_1000', '1000_2000', '2000_3000', 'OVER_3000']
const modes: TravelMode[] = ['WALKING', 'CYCLING', 'TRANSIT', 'DRIVING']
const clock = (value: unknown): value is string => typeof value === 'string' && /^([01]\d|2[0-3]):[0-5]\d$/.test(value)
const text = (value: unknown): value is string => typeof value === 'string'
const num = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value)
const nullableNum = (value: unknown) => value === null || num(value)
const integer = (value: unknown) => value === null || (typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 7)
const source = (value: unknown): value is LifesimSource => typeof value === 'string' && sources.includes(value as LifesimSource)

function validAnswers(a: LifesimAnswers) {
  return !!a && (a.commuteMinutes === null || (num(a.commuteMinutes) && a.commuteMinutes >= 0 && a.commuteMinutes <= 300))
    && (a.workStart === null || clock(a.workStart)) && (a.sleepTime === null || clock(a.sleepTime))
    && integer(a.overtimeDays) && (a.overtimeEnd === null || clock(a.overtimeEnd))
    && (a.dailySpendBand === null || bands.includes(a.dailySpendBand)) && nullableNum(a.fixedCost)
    && integer(a.cookingDays)
}
function validView(v: LifesimView) {
  return !!v && text(v.planId) && text(v.versionId) && Number.isInteger(v.versionNo)
    && text(v.title) && text(v.capturedAt) && ['READY', 'INCOMPLETE', 'NEEDS_COMMUTE'].includes(v.status)
    && (v.mode === null || modes.includes(v.mode)) && nullableNum(v.commuteMinutes)
    && validAnswers(v.answers) && !!v.assumptions && typeof v.assumptions === 'object'
    && Array.isArray(v.timeline) && v.timeline.every(m => !!m && text(m.key) && text(m.label) && text(m.time) && source(m.source) && text(m.detail))
    && !!v.costs && nullableNum(v.costs.monthlyRent) && nullableNum(v.costs.deposit) && nullableNum(v.costs.propertyFee)
    && (v.costs.propertyFeePeriod === null || text(v.costs.propertyFeePeriod)) && nullableNum(v.costs.knownInitialPayment)
    && nullableNum(v.costs.fixedCost) && (v.costs.dailySpendBand === null || text(v.costs.dailySpendBand))
    && text(v.costs.dailySpendLabel) && nullableNum(v.costs.monthlyKnown) && nullableNum(v.costs.monthlyLow) && nullableNum(v.costs.monthlyHigh)
    && !!v.sources && Object.values(v.sources).every(source)
    && Array.isArray(v.limitations) && v.limitations.every(text) && (v.updatedAt === null || text(v.updatedAt))
}

export async function getLifesim(token: string, planId: string): Promise<LifesimView> {
  const r = await apiRequest<LifesimView>(`/me/plans/${encodeURIComponent(planId)}/lifesim`, { token })
  if (!validView(r)) throw new ApiError('服务返回的生活账本数据不完整，请重试')
  return r
}
export async function saveLifesim(token: string, planId: string, answers: LifesimAnswers): Promise<LifesimView> {
  const r = await apiRequest<LifesimView>(`/me/plans/${encodeURIComponent(planId)}/lifesim`, { method: 'PUT', token, data: answers })
  if (!validView(r)) throw new ApiError('服务返回的生活账本数据不完整，请重试')
  return r
}