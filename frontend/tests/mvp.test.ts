import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePilotStore } from '../src/stores/pilot'
import { queryListings } from '../src/services/listing-query'
import { listings } from '../src/data/mock'

beforeEach(() => setActivePinia(createPinia()))

describe('mobile MVP interactions', () => {
  it('archives and reopens a conversation without losing the current one', () => {
    const store = usePilotStore()
    store.sendMessage('我想住在张江')
    const original = store.messages.map(message => ({ ...message }))
    store.startConversation()
    expect(store.messages).toHaveLength(0)
    expect(store.conversations).toHaveLength(1)
    const id = store.conversations[0]!.id
    store.sendMessage('看看唐镇')
    expect(store.openConversation(id)).toBe(true)
    expect(store.messages).toEqual(original)
    expect(store.conversations[0]!.messages[0]!.text).toBe('看看唐镇')
    expect(store.openConversation('missing')).toBe(false)
  })

  it('search and ordering work together and do not mutate source listings', () => {
    const original = listings.map(h => h.id)
    const result = queryListings(listings, { query: '金科路', budget: 5000, sort: 'rent-asc' })
    expect(result.map(h => h.id)).toEqual(['h3', 'h1'])
    expect(queryListings(listings, { query: '不存在的社区' })).toEqual([])
    expect(queryListings(listings, { savedOnly: true, savedIds: ['h2'], sort: 'commute' }).map(h => h.id)).toEqual(['h2'])
    expect(listings.map(h => h.id)).toEqual(original)
  })

  it('starting a new conversation does not silently discard older archives or long messages', () => {
    const store = usePilotStore()
    for (let i = 0; i < 21; i++) { store.sendMessage(`会话 ${i}`); store.startConversation() }
    expect(store.conversations).toHaveLength(21)
    expect(store.conversations.at(-1)!.messages[0]!.text).toBe('会话 0')
    for (let i = 0; i < 55; i++) store.sendMessage(`长对话 ${i}`)
    store.startConversation()
    expect(store.conversations[0]!.messages).toHaveLength(110)
    expect(store.conversations[0]!.messages[0]!.text).toBe('长对话 0')
  })

  it('keeps a validated local viewing plan and replaces duplicates', () => {
    const store = usePilotStore()
    expect(store.scheduleViewing('missing', '2026-10-30', '下午 14:00')).toBe(false)
    expect(store.scheduleViewing('h1', '', '')).toBe(false)
    expect(store.scheduleViewing('h1', '2026-10-30', '下午 14:00')).toBe(true)
    expect(store.scheduleViewing('h1', '2026-10-31', '上午 10:00')).toBe(true)
    expect(store.viewings).toHaveLength(1)
    expect(store.viewings[0]!.date).toBe('2026-10-31')
    store.cancelViewing(store.viewings[0]!.id)
    expect(store.viewings).toHaveLength(0)
  })
})
