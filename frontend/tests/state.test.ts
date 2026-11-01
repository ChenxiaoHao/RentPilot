import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePilotStore } from '../src/stores/pilot'

beforeEach(() => setActivePinia(createPinia()))
afterEach(() => vi.unstubAllGlobals())
describe('RentPilot user decisions', () => {
  it('rejects blank messages and preserves multiline user input', () => {
    const store = usePilotStore()
    expect(store.sendMessage('  \n ')).toBe(false)
    expect(store.messages).toHaveLength(0)
    expect(store.sendMessage('我在张江上班\n预算 5000 元')).toBe(true)
    expect(store.messages[0]?.text).toBe('我在张江上班\n预算 5000 元')
    expect(store.messages.at(-1)?.role).toBe('assistant')
  })
  it('keeps preview drafts isolated until apply and creates a recoverable version', () => {
    const store = usePilotStore()
    const draft = { ...store.weights, commute: 100 }
    expect(store.weights.commute).toBe(85)
    const before = store.versions.length
    store.applyWeights(draft)
    draft.commute = 0
    expect(store.weights.commute).toBe(100)
    expect(store.versions.length).toBe(before + 1)
    expect(store.activePlan.commute).toBe(16)
    expect(store.activePlan.rent).toBe(4900)
    const previous = store.versions.find(v => v.weights.commute === 85)!
    expect(store.restoreVersion(previous.id)).toBe(true)
    expect(store.weights.commute).toBe(85)
    expect(store.activePlan.rent).toBe(4800)
    expect(store.restoreVersion('missing')).toBe(false)
  })
  it('normalizes weights, ignores invalid plan ids and toggles saves', () => {
    const store = usePilotStore()
    store.applyWeights({ budget: -20, commute: 120, lifestyle: Number.NaN })
    expect(store.weights).toEqual({ budget: 0, commute: 100, lifestyle: 50 })
    const selected = store.activePlanId
    store.selectPlan('invalid')
    expect(store.activePlanId).toBe(selected)
    store.toggleSaved('h1')
    expect(store.savedListings).toContain('h1')
    store.toggleSaved('h1')
    expect(store.savedListings).not.toContain('h1')
  })
  it('applies the exact confirmed negotiation metrics and preserves earlier versions', () => {
    const store = usePilotStore()
    store.applyNegotiation('A')
    expect(store.activePlan.rent).toBe(4000)
    expect(store.activePlan.commute).toBe(15)
    store.applyNegotiation('B')
    expect(store.activePlan.rent).toBe(3500)
    expect(store.activePlan.commute).toBe(32)
    expect(store.versions).toHaveLength(5)
    expect(store.restoreVersion('v2.1')).toBe(true)
    expect(store.activePlan.rent).toBe(4800)
  })
  it('rejects incomplete cached versions instead of crashing financial cards', () => {
    vi.stubGlobal('localStorage', { getItem: () => JSON.stringify({ versions: [{ id: 'v2.1', planId: 'commute', weights: {} }] }) })
    const store = usePilotStore()
    store.hydrate()
    expect(store.activePlan.rent).toBe(4800)
    expect(store.versions[0]?.title).toBe('通勤优先方案')
  })
})
