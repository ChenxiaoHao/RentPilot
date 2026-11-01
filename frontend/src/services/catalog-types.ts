export type FacilityState = 'YES' | 'NO' | 'UNKNOWN'
export interface CatalogListing {
  id: string; name: string; district: string; town: string; community: string; address: string
  rentalType: 'ENTIRE' | 'SHARED'; layout: string; bedrooms: number; rent: number; area: number
  floor: string; orientation: string; decoration: string; nearestMetro: string; metroLines: string[]
  longitude: number | null; latitude: number | null; metroWalkMinutes: number | null; metroDistanceMeters: number | null
  amenities: Record<string, FacilityState>; deposit: number; paymentMethod: string
  propertyFee: number | null; propertyFeePeriod: 'UNSPECIFIED'; availability: string; description: string
  dataKind: 'SIMULATED'; dataNature: string; locationStatus: 'COMMUNITY_REFERENCE' | 'UNVERIFIED'
  datasetId: string; sourceSha256: string
}
export interface CatalogSource { id: string; rowCount: number; sha256: string; dataKind: 'SIMULATED' }
export interface CatalogPage { items: CatalogListing[]; page: number; size: number; total: number; totalPages: number; dataKind: 'SIMULATED'; source: CatalogSource }
export interface CatalogFilters {
  districts: { value: string; count: number }[]
  towns: { district: string; value: string; count: number }[]
  metroStations: { value: string; count: number }[]
  metroLines: string[]
  source: CatalogSource
}
export type CatalogSort = 'id' | 'rentAsc' | 'rentDesc' | 'areaDesc' | 'metroWalkAsc'
export interface CatalogQuery {
  q?: string; district?: string; town?: string; nearestMetro?: string; rentalType?: 'ENTIRE' | 'SHARED'; bedrooms?: number
  maxRent?: number; maxMetroWalkMinutes?: number; metroLine?: string; sort?: CatalogSort
}


