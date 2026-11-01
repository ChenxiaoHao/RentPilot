import type { CatalogListing } from './catalog-types'

// Stock interiors are illustrative, not photographs of the simulated listing.
const bedrooms = ['17994859', '6750148', '5425123', '28536694']
const apartments = ['7060814', '6444255', '5425123']
export function catalogPhoto(home: Pick<CatalogListing, 'id' | 'rentalType' | 'bedrooms'>) {
  const pool = home.rentalType === 'SHARED' ? bedrooms : apartments
  const index = Number(home.id.match(/\d+$/)?.[0] ?? 0)
  return `/assets/catalog/pexels-${pool[index % pool.length]}.jpg`
}
export function catalogTitle(home: Pick<CatalogListing, 'community' | 'town' | 'rentalType' | 'bedrooms'>) {
  const place = home.community === '待核验' ? home.town : home.community
  return `${place} · ${home.rentalType === 'SHARED' ? '合租单间' : `${home.bedrooms} 居室`}`
}
export function metroCaption(home: Pick<CatalogListing, 'metroWalkMinutes' | 'nearestMetro'>) {
  return home.metroWalkMinutes === null ? '地铁步行信息待补充' : `距${home.nearestMetro}站步行 ${home.metroWalkMinutes} 分钟`
}
