import { afterEach, describe, expect, it, vi } from 'vitest'
import { getLifesim, saveLifesim } from '../src/services/lifesim'

afterEach(() => vi.unstubAllGlobals())
const view = {
  planId: 'p1', versionId: 'v1', versionNo: 1, title: '吴泾通勤方案', capturedAt: '2026-10-31T08:00:00Z',
  status: 'READY', mode: 'CYCLING', commuteMinutes: 11,
  answers: { commuteMinutes: 11, workStart: '09:00', sleepTime: '23:30', overtimeDays: 2, overtimeEnd: '21:00', dailySpendBand: '1000_2000', fixedCost: 500, cookingDays: 3 },
  assumptions: { workEnd: '18:00' },
  timeline: [
    { key: 'leave', label: '出门', time: '08:49', source: 'DERIVED', detail: '按 09:00 上班与 11 分钟骑行倒推' },
    { key: 'sleep', label: '睡觉', time: '23:30', source: 'USER', detail: '按你填写的时间' },
  ],
  costs: { monthlyRent: 2400, deposit: 2400, propertyFee: 200, propertyFeePeriod: 'MONTHLY', knownInitialPayment: 9600, fixedCost: 500, dailySpendBand: '1000_2000', dailySpendLabel: '1000–2000 元', monthlyKnown: 3100, monthlyLow: 4100, monthlyHigh: 5100 },
  sources: { commute: 'FACT', workStart: 'USER', freeTime: 'DERIVED', propertyFee: 'FACT' },
  limitations: ['时间账本按你填写的时间和系统固定假设推算，不代表实际生活。'],
  updatedAt: '2026-10-31T13:00:00Z',
}

describe('lifesim service', () => {
  it('loads a server-computed view with sources', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(view), { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    const result = await getLifesim('a'.repeat(43), 'p1')
    expect(result.timeline[0]!.source).toBe('DERIVED')
    expect(fetch.mock.calls[0]![0]).toBe('/api/me/plans/p1/lifesim')
  })
  it('rejects a view with an unknown source marker', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...view, sources: { commute: 'MADE_UP' } }), { status: 200 })))
    await expect(getLifesim('a'.repeat(43), 'p1')).rejects.toThrow('不完整')
  })
  it('tolerates an empty timeline when no moments have been computed', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ ...view, status: 'READY', timeline: [] }), { status: 200 })))
    await expect(getLifesim('a'.repeat(43), 'p1')).resolves.toBeTruthy()
  })
  it('sends only answers and lets the server recompute', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(view), { status: 200 }))
    vi.stubGlobal('fetch', fetch)
    const answers = { commuteMinutes: 11, workStart: '09:00', sleepTime: '23:30', overtimeDays: 2, overtimeEnd: '21:00', dailySpendBand: '1000_2000' as const, fixedCost: 500, cookingDays: 3 }
    await saveLifesim('a'.repeat(43), 'p1', answers)
    expect(fetch.mock.calls[0]![1].method).toBe('PUT')
    expect(JSON.parse(fetch.mock.calls[0]![1].body)).toEqual(answers)
  })
})