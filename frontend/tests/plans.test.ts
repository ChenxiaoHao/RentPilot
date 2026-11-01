import { afterEach, describe, expect, it, vi } from 'vitest'
import { comparePlans, getPlan, listPlans, savePlan } from '../src/services/plans'

afterEach(() => vi.unstubAllGlobals())
const listing: any = { id: 'SH-MOCK-000001', name: '模拟小区 001', district: '闵行区', town: '吴泾镇', community: '吴泾社区', address: '地址', layout: '一室', floor: '1', orientation: '南', decoration: '简装', nearestMetro: '', paymentMethod: '押一付三', availability: '', description: '', dataNature: '', datasetId: 'sh-1500', sourceSha256: 'a'.repeat(64), rent: 3000, area: 30, deposit: 3000, bedrooms: 1, rentalType: 'ENTIRE', dataKind: 'SIMULATED', propertyFeePeriod: 'UNSPECIFIED', locationStatus: 'UNVERIFIED', longitude: null, latitude: null, metroWalkMinutes: null, metroDistanceMeters: null, propertyFee: null, metroLines: [], amenities: Object.fromEntries(['privateBathroom', 'privateKitchen', 'gas', 'balcony', 'airConditioning', 'refrigerator', 'washingMachine', 'waterHeater', 'elevator', 'furnished', 'petsAllowed', 'cookingAllowed'].map(key => [key, 'UNKNOWN'])) }
const insight = { evidence: [{ id: 'rent', label: '月租', text: '月租 3000 元', status: 'MATCHED', source: 'LISTING', fields: ['rent'] }], costs: { monthlyRent: 3000, deposit: 3000, paymentMethod: '押一付三', propertyFee: null, propertyFeePeriod: 'UNSPECIFIED', prepaidMonths: 3, firstRentPayment: 9000, knownInitialPayment: 12000, excludedItems: ['物业费'] } }
const source = { id: 'sh-1500', rowCount: 1500, sha256: 'a'.repeat(64), dataKind: 'SIMULATED' }
const snapshot = { capturedAt: '2026-10-31T08:00:00Z', snapshotKind: 'HISTORICAL', realtime: false, dataKind: 'SIMULATED', criteria: { maxRent: 5000 }, limitations: ['房源为模拟数据。'], source, commute: { state: 'CHECKED', mode: 'CYCLING', maxMinutes: 30, checkedCount: 12, candidateCount: 30, unverifiedCount: 18, coverage: 'PARTIAL', estimated: true }, recommendations: [{ listing, reasons: ['月租 3000 元'], commute: { mode: 'CYCLING', durationSeconds: 660, minutes: 11, distanceMeters: 3000, source: 'AMAP', estimated: true, checkedAt: '2026-10-31T08:00:00Z' }, insight }] }
const summary = { id: 'p1', versionId: 'v1', versionNo: 1, title: '吴泾通勤方案', primaryListingId: listing.id, primaryHomeName: listing.name, primaryDistrict: listing.district, primaryTown: listing.town, primaryRent: 3000, primaryCommuteMinutes: 11, homeCount: 1, coverage: 'PARTIAL', capturedAt: '2026-10-31T08:00:00Z', updatedAt: '2026-10-31T08:00:00Z' }
const detail = { plan: summary, versions: [{ id: 'v1', versionNo: 1, homeCount: 1, coverage: 'PARTIAL', capturedAt: '2026-10-31T08:00:00Z' }], snapshot }
const side = { planId: 'p1', versionId: 'v1', versionNo: 1, title: 'A', capturedAt: '2026-10-31T08:00:00Z', primaryListingId: listing.id, primaryHomeName: listing.name, primaryDistrict: listing.district, primaryTown: listing.town, rent: 3000, deposit: 3000, paymentMethod: '押一付三', propertyFee: null, propertyFeePeriod: 'UNSPECIFIED', knownInitialPayment: 12000, commuteMode: 'CYCLING', commuteMinutes: 11, commuteCheckedAt: '2026-10-31T08:00:00Z', coverage: 'PARTIAL', homeCount: 1, partialCoverage: true }

describe('living plan service', () => {
  it('loads saved plans and keeps the bearer token server-side', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify([summary]), { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    await expect(listPlans('a'.repeat(43))).resolves.toHaveLength(1)
    expect(fetch.mock.calls[0]![0]).toBe('/api/me/plans')
    expect(fetch.mock.calls[0]![1].headers.Authorization).toBe(`Bearer ${'a'.repeat(43)}`)
  })
  it('rejects plans whose snapshot is presented as realtime', async () => {
    const tampered = { ...detail, snapshot: { ...snapshot, realtime: true } }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(tampered), { status: 200 })))
    await expect(getPlan('a'.repeat(43), 'p1')).rejects.toThrow('不完整')
  })
  it('rejects snapshot homes whose costs contradict the listing', async () => {
    const tampered = { ...detail, snapshot: { ...snapshot, recommendations: [{ ...snapshot.recommendations[0], insight: { ...insight, costs: { ...insight.costs, knownInitialPayment: 15000 } } }] } }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(tampered), { status: 200 })))
    await expect(getPlan('a'.repeat(43), 'p1')).rejects.toThrow('不完整')
  })
  it('sends only the search token and selected homes when saving', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(detail), { status: 201 }))
    vi.stubGlobal('fetch', fetch)
    await expect(savePlan('a'.repeat(43), { searchToken: 'token-a', listingIds: ['SH-MOCK-000001'], title: '方案' })).resolves.toBeTruthy()
    expect(fetch.mock.calls[0]![1].method).toBe('POST')
    expect(JSON.parse(fetch.mock.calls[0]![1].body)).toEqual({ searchToken: 'token-a', listingIds: ['SH-MOCK-000001'], title: '方案' })
  })
  it('accepts unknown differences and rejects invented numbers', async () => {
    const comparison = { left: side, right: { ...side, planId: 'p2', versionId: 'v2', title: 'B', rent: 2600, commuteMinutes: 9 }, rentDifference: null, commuteMinutesDifference: -2, knownInitialPaymentDifference: null, notes: ['至少一侧缺少已确认的首期费用，无法比较首期差。'] }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(comparison), { status: 200 })))
    await expect(comparePlans('a'.repeat(43), 'p1', 'p2')).resolves.toBeTruthy()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...comparison, rentDifference: '很多' }), { status: 200 })))
    await expect(comparePlans('a'.repeat(43), 'p1', 'p2')).rejects.toThrow('不完整')
  })
})