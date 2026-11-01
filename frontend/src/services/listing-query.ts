import type { Listing } from '../data/mock'

export interface ListingQuery {
  query?: string
  district?: string
  budget?: number
  commute?: number
  metro?: string
  type?: string
  savedOnly?: boolean
  savedIds?: string[]
  sort?: 'recommended' | 'rent-asc' | 'rent-desc' | 'commute'
}

export function queryListings(source: Listing[], filters: ListingQuery = {}): Listing[] {
  const query = filters.query?.trim().toLocaleLowerCase() ?? ''
  const rows = source.filter(home =>
    (!query || [home.name, home.address, home.district, home.metro, ...home.tags].join(' ').toLocaleLowerCase().includes(query))
    && (!filters.district || filters.district === 'all' || home.district === filters.district)
    && (!filters.budget || home.price <= filters.budget)
    && (!filters.commute || home.commute <= filters.commute)
    && (!filters.metro || filters.metro === 'all' || home.metro.startsWith(filters.metro))
    && filters.type !== 'two'
    && (!filters.savedOnly || filters.savedIds?.includes(home.id)),
  )
  if (filters.sort === 'rent-asc') rows.sort((a, b) => a.price - b.price)
  if (filters.sort === 'rent-desc') rows.sort((a, b) => b.price - a.price)
  if (filters.sort === 'commute') rows.sort((a, b) => a.commute - b.commute)
  return rows
}
