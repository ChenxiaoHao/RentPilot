import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { catalogPhoto, metroCaption } from '../src/services/catalog-photos'

const platform = vi.hoisted(() => ({ native: false, get: vi.fn() }))
vi.mock('@capacitor/core', () => ({ Capacitor: { isNativePlatform: () => platform.native }, CapacitorHttp: { get: platform.get } }))
const page = { items: [], page: 0, size: 20, total: 0, totalPages: 0, dataKind: 'SIMULATED', source: { id: 'shanghai-simulation-v1', rowCount: 1500, dataKind: 'SIMULATED', sha256: '07ff43a3600e34cffbdc0de8751406cac33f6cfc330c169472ae739ed8abdfa8' } }
beforeEach(() => { platform.native = false; platform.get.mockReset(); vi.resetModules() })
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })

describe('catalog backend boundary', () => {
  it('sends only explicit conditions with encoded text and server pagination', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...page, page: 2 })))
    vi.stubGlobal('fetch', fetch)
    const { fetchCatalog } = await import('../src/services/catalog')
    await fetchCatalog({ q: '潍坊一村', maxRent: 3000, rentalType: 'ENTIRE', bedrooms: 1, sort: 'rentAsc' }, 2)
    const url = new URL(fetch.mock.calls[0]![0], 'http://localhost')
    expect(url.pathname).toBe('/api/listings')
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '2', size: '20', q: '潍坊一村', maxRent: '3000', rentalType: 'ENTIRE', bedrooms: '1', sort: 'rentAsc' })
  })
  it('rejects non-JSON and incomplete responses rather than silently using mock listings', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>offline</html>')))
    const { fetchCatalog } = await import('../src/services/catalog')
    await expect(fetchCatalog({})).rejects.toThrow('响应格式异常')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ items: [] }))))
    await expect(fetchCatalog({})).rejects.toThrow('不完整')
  })
  it('distinguishes service errors from successful empty results', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(page))))
    const { fetchCatalog } = await import('../src/services/catalog')
    await expect(fetchCatalog({ maxRent: 1 })).resolves.toMatchObject({ total: 0, items: [] })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{}', { status: 503 })))
    await expect(fetchCatalog({})).rejects.toMatchObject({ status: 503 })
  })
  it('rejects malformed listing rows and inconsistent server pagination', async () => {
    const { fetchCatalog } = await import('../src/services/catalog')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...page, total: 1, totalPages: 1, items: [{}] }))))
    await expect(fetchCatalog({})).rejects.toThrow('不完整')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...page, page: 'zero' }))))
    await expect(fetchCatalog({})).rejects.toThrow('不完整')
  })
  it('rejects missing detail fields and malformed filter options before they reach the UI', async () => {
    const { fetchCatalogFilters, fetchCatalogListing } = await import('../src/services/catalog')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 'SH-MOCK-000001' }))))
    await expect(fetchCatalogListing('SH-MOCK-000001')).rejects.toThrow('不完整')
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ districts: null, metroLines: [] }))))
    await expect(fetchCatalogFilters()).rejects.toThrow('不完整')
  })
  it('cancels an obsolete native response promptly without waiting for the network timeout', async () => {
    platform.native = true
    let finish!: (value: unknown) => void
    platform.get.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const { fetchCatalog } = await import('../src/services/catalog')
    const controller = new AbortController()
    let result = ''
    const pending = fetchCatalog({}, 0, controller.signal).then(() => { result = 'success' }, error => { result = error.name })
    controller.abort()
    try { await vi.waitFor(() => expect(result).toBe('AbortError'), { timeout: 150, interval: 10 }) }
    finally { finish({ status: 200, data: page }); await pending }
    expect(platform.get.mock.calls[0]![0].url).toBe('http://10.0.2.2:8080/api/listings?page=0&size=20')
  })
  it('keeps missing metro time distinct from zero and reuses stable local photos', () => {
    expect(metroCaption({ metroWalkMinutes: null, nearestMetro: '待核验' })).toBe('地铁步行信息待补充')
    expect(metroCaption({ metroWalkMinutes: 0, nearestMetro: '塘桥' })).toContain('0 分钟')
    expect(catalogPhoto({ id: 'SH-MOCK-000001', rentalType: 'SHARED', bedrooms: 3 })).toBe('/assets/catalog/pexels-6750148.jpg')
    expect(catalogPhoto({ id: 'SH-MOCK-000005', rentalType: 'SHARED', bedrooms: 3 })).toBe('/assets/catalog/pexels-6750148.jpg')
  })
})
