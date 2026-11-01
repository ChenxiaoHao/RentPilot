import { apiRequest, ApiError } from './http'
export { ApiError as CatalogError } from './http'
import type { CatalogFilters, CatalogListing, CatalogPage, CatalogQuery } from './catalog-types'

const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object' && !Array.isArray(value)
const nonnegative = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value) && value >= 0
const textFields = ['id','name','district','town','community','address','layout','floor','orientation','decoration','nearestMetro','paymentMethod','availability','description','dataNature','datasetId','sourceSha256']
const facilityFields = ['privateBathroom','privateKitchen','gas','balcony','airConditioning','refrigerator','washingMachine','waterHeater','elevator','furnished','petsAllowed','cookingAllowed']
export function isListing(value: unknown): value is CatalogListing {
  if (!object(value) || !textFields.every(key => typeof value[key] === 'string') || !/^SH-MOCK-\d{6}$/.test(value.id as string)) return false
  if (!nonnegative(value.rent) || value.rent === 0 || !nonnegative(value.area) || value.area === 0 || !nonnegative(value.deposit)) return false
  if (!Number.isInteger(value.bedrooms) || Number(value.bedrooms) < 1 || Number(value.bedrooms) > 10) return false
  if (!['ENTIRE','SHARED'].includes(String(value.rentalType)) || value.dataKind !== 'SIMULATED' || value.propertyFeePeriod !== 'UNSPECIFIED') return false
  if (!['COMMUNITY_REFERENCE','UNVERIFIED'].includes(String(value.locationStatus))) return false
  if (![value.longitude,value.latitude].every(v => v === null || typeof v === 'number' && Number.isFinite(v)) || (value.longitude === null) !== (value.latitude === null)) return false
  if (![value.metroWalkMinutes,value.metroDistanceMeters,value.propertyFee].every(v => v === null || nonnegative(v))) return false
  if (!Array.isArray(value.metroLines) || !value.metroLines.every(line => typeof line === 'string') || !object(value.amenities)) return false
  const amenities = value.amenities
  return facilityFields.every(key => ['YES','NO','UNKNOWN'].includes(String(amenities[key])))
}
function isSource(value: unknown) {
  return object(value) && typeof value.id === 'string' && value.dataKind === 'SIMULATED'
    && typeof value.sha256 === 'string' && /^[a-f\d]{64}$/i.test(value.sha256) && Number.isInteger(value.rowCount) && Number(value.rowCount) > 0
}
function incomplete(): never { throw new ApiError('服务返回的房源数据不完整，请重试') }

export function fetchCatalog(query: CatalogQuery, page = 0, signal?: AbortSignal): Promise<CatalogPage> {
  const parameters = new URLSearchParams({ page: String(page), size: '20' })
  for (const [key, value] of Object.entries(query)) if (value !== undefined && value !== '') parameters.set(key, String(value))
  return apiRequest<CatalogPage>('/listings?' + parameters, { signal }).then(result => {
    if (!Array.isArray(result.items) || !result.items.every(isListing) || new Set(result.items.map(item => item.id)).size !== result.items.length
      || !Number.isInteger(result.total) || result.total < 0 || result.dataKind !== 'SIMULATED' || !isSource(result.source)
      || result.page !== page || result.size !== 20 || result.totalPages !== Math.ceil(result.total / 20)
      || result.items.length !== Math.min(20, Math.max(0, result.total - page * 20))) incomplete()
    return result
  })
}
export function fetchCatalogFilters(signal?: AbortSignal) {
  return apiRequest<CatalogFilters>('/listing-filters', { signal }).then(result => {
    if (!Array.isArray(result.districts) || !result.districts.every(row => object(row) && typeof row.value === 'string' && Number.isInteger(row.count) && row.count > 0)
      || !Array.isArray(result.metroLines) || !result.metroLines.every(line => typeof line === 'string') || !isSource(result.source)) incomplete()
    return result
  })
}
export function fetchCatalogListing(id: string, signal?: AbortSignal) {
  return apiRequest<CatalogListing>('/listings/' + encodeURIComponent(id), { signal }).then(result => {
    if (!isListing(result) || result.id !== id) incomplete()
    return result
  })
}
export function fetchMetroLineStations(line: string, signal?: AbortSignal) {
  return apiRequest<{line: string; status: string; stations: string[]}>(`/metro-lines/${encodeURIComponent(line)}/stations`, { signal }).then(result => {
    if (result.line !== line || !Array.isArray(result.stations) || !result.stations.every(s => typeof s === 'string')) throw new Error('站点数据不完整')
    return result
  })
}

