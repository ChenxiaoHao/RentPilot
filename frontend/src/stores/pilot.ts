import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { initialVersions, listings, plans, type Version, type Weights } from '../data/mock'

export interface Message { id: string; role: 'user' | 'assistant'; text: string }
export interface Conversation { id: string; title: string; updatedAt: string; messages: Message[] }
export interface Viewing { id: string; listingId: string; date: string; slot: string }
export const viewingSlots = ['上午 10:00', '下午 14:00', '晚上 18:30']
function validMessages(value: unknown): value is Message[] {
  return Array.isArray(value) && value.every(m => m && typeof m.id === 'string' && typeof m.text === 'string' && ['user', 'assistant'].includes(m.role))
}
function validViewing(value: unknown): value is Viewing {
  if (!value || typeof value !== 'object') return false
  const v = value as Viewing
  return typeof v.id === 'string' && listings.some(h => h.id === v.listingId)
    && /^\d{4}-\d{2}-\d{2}$/.test(v.date) && Number.isFinite(Date.parse(v.date))
    && new Date(v.date).toISOString().slice(0, 10) === v.date && viewingSlots.includes(v.slot)
}
function validWeights(value: unknown): value is Weights {
  if (!value || typeof value !== 'object') return false
  return ['budget', 'commute', 'lifestyle'].every(key => {
    const n = (value as Record<string, unknown>)[key]
    return typeof n === 'number' && Number.isFinite(n) && n >= 0 && n <= 100
  })
}
function validVersion(value: unknown): value is Version {
  if (!value || typeof value !== 'object') return false
  const v = value as Version
  return typeof v.id === 'string' && typeof v.title === 'string' && typeof v.date === 'string'
    && plans.some(p => p.id === v.planId) && validWeights(v.weights)
    && [v.rent, v.commute, v.match].every(n => typeof n === 'number' && Number.isFinite(n) && n >= 0)
    && v.match <= 100
}
export function previewWeights(weights: Weights) {
  return { commute: Math.round(36 - weights.commute * 0.2), rent: Math.round((4000 + weights.lifestyle * 12 + weights.commute * 5 - weights.budget * 3) / 50) * 50, score: Math.min(99, Math.round(78 + weights.lifestyle * 0.15 + weights.commute * 0.1)) }
}
export const usePilotStore = defineStore('pilot', () => {
  const weights = ref<Weights>({ budget: 60, commute: 85, lifestyle: 50 })
  const versions = ref<Version[]>(structuredClone(initialVersions))
  const activeVersionId = ref('v2.1')
  const activePlanId = ref('commute')
  const savedListings = ref<string[]>([])
  const savedPlans = ref<string[]>([])
  const messages = ref<Message[]>([])
  const conversations = ref<Conversation[]>([])
  const viewings = ref<Viewing[]>([])
  const nickname = ref('上海租房人')
  const scene = ref('weekday')
  const notifications = ref(false)
  const toast = ref('')
  let toastTimer: ReturnType<typeof setTimeout> | undefined
  let storageWarningShown = false
  const displayPlans = computed(() => {
    const version = versions.value.find(v => v.id === activeVersionId.value)
    return plans.map(plan => version?.planId === plan.id ? { ...plan, rent: version.rent, commute: version.commute, score: version.match / 10, cost: plan.cost + version.rent - plan.rent } : plan)
  })
  const activePlan = computed(() => displayPlans.value.find(p => p.id === activePlanId.value) ?? displayPlans.value[1]!)
  function notify(text: string) {
    toast.value = text
    clearTimeout(toastTimer)
    toastTimer = setTimeout(() => { toast.value = '' }, 2600)
  }
  function sendMessage(input: string) {
    const text = input.trim()
    if (!text) return false
    messages.value.push({ id: crypto.randomUUID(), role: 'user', text })
    const reply = messages.value.filter(m => m.role === 'user').length === 1
      ? '先把生活半径确定下来。\n你在哪里上班？每月愿意花多少租金？希望通勤多久？\n也可以直接编辑下方的找房条件。'
      : '可以先看看张江、康桥和唐镇的不同选择：靠近工作地，或者把预算留给生活。\n下面用这组示例房源，带你比较两种日常。'
    messages.value.push({ id: crypto.randomUUID(), role: 'assistant', text: reply })
    return true
  }
  function startConversation() {
    if (!messages.value.length) return
    conversations.value.unshift({ id: crypto.randomUUID(), title: messages.value.find(m => m.role === 'user')?.text.slice(0, 24) ?? '找房对话', updatedAt: new Date().toISOString(), messages: messages.value.map(m => ({ ...m })) })
    messages.value = []
  }
  function openConversation(id: string) {
    const conversation = conversations.value.find(item => item.id === id)
    if (!conversation) return false
    conversations.value = conversations.value.filter(item => item.id !== id)
    startConversation()
    messages.value = conversation.messages.map(m => ({ ...m }))
    return true
  }
  function scheduleViewing(listingId: string, date: string, slot: string) {
    const viewing = { id: crypto.randomUUID(), listingId, date, slot }
    if (!validViewing(viewing)) return false
    viewings.value = [viewing, ...viewings.value.filter(v => v.listingId !== listingId)].slice(0, 30)
    return true
  }
  function cancelViewing(id: string) { viewings.value = viewings.value.filter(v => v.id !== id) }
  function selectPlan(id: string) { if (plans.some(p => p.id === id)) activePlanId.value = id }
  function toggleSaved(id: string) {
    savedListings.value = savedListings.value.includes(id) ? savedListings.value.filter(v => v !== id) : [...savedListings.value, id]
  }
  function togglePlan(id: string) {
    savedPlans.value = savedPlans.value.includes(id) ? savedPlans.value.filter(v => v !== id) : [...savedPlans.value, id]
  }
  function applyWeights(draft: Weights) {
    const normalize = (n: number) => Number.isFinite(n) ? Math.round(Math.min(100, Math.max(0, n))) : 50
    weights.value = { budget: normalize(draft.budget), commute: normalize(draft.commute), lifestyle: normalize(draft.lifestyle) }
    const preview = previewWeights(weights.value)
    const id = `v2.${versions.value.length - initialVersions.length + 2}`
    versions.value.unshift({ id, title: '偏好调整方案', date: new Date().toLocaleDateString('zh-CN'), planId: activePlanId.value, weights: { ...weights.value }, rent: preview.rent, commute: preview.commute, match: preview.score })
    activeVersionId.value = id
  }
  function applyNegotiation(choice: 'A' | 'B') {
    const isA = choice === 'A'
    const id = `v2.${versions.value.length - initialVersions.length + 2}`
    activePlanId.value = isA ? 'commute' : 'economy'
    versions.value.unshift({ id, title: isA ? '预算协调 · 短途通勤方案' : '预算协调 · 经济优先方案', date: new Date().toLocaleDateString('zh-CN'), planId: activePlanId.value, weights: { ...weights.value }, rent: isA ? 4000 : 3500, commute: isA ? 15 : 32, match: isA ? 90 : 86 })
    activeVersionId.value = id
  }
  function restoreVersion(id: string) {
    const version = versions.value.find(v => v.id === id)
    if (!version) return false
    weights.value = { ...version.weights }
    activeVersionId.value = version.id
    activePlanId.value = version.planId
    return true
  }
  function hydrate() {
    try {
      const data = JSON.parse(localStorage.getItem('rentpilot-demo-v1') ?? 'null')
      if (!data || typeof data !== 'object') return
      if (plans.some(p => p.id === data.activePlanId)) activePlanId.value = data.activePlanId
      if (Array.isArray(data.savedListings)) savedListings.value = data.savedListings.filter((id: unknown) => typeof id === 'string')
      if (Array.isArray(data.savedPlans)) savedPlans.value = data.savedPlans.filter((id: unknown) => typeof id === 'string')
      if (Array.isArray(data.messages)) messages.value = data.messages.filter((m: Message) => m && typeof m.text === 'string' && ['user', 'assistant'].includes(m.role))
      if (Array.isArray(data.conversations)) conversations.value = data.conversations.filter((c: Conversation) => c && typeof c.id === 'string' && typeof c.title === 'string' && typeof c.updatedAt === 'string' && validMessages(c.messages))
      if (Array.isArray(data.viewings)) viewings.value = data.viewings.filter(validViewing).slice(0, 30)
      if (typeof data.nickname === 'string' && data.nickname.trim()) nickname.value = data.nickname.trim().slice(0, 16)
      if (validWeights(data.weights)) weights.value = { ...data.weights }
      if (Array.isArray(data.versions) && data.versions.length && data.versions.every(validVersion)) versions.value = data.versions
      if (versions.value.some(v => v.id === data.activeVersionId)) activeVersionId.value = data.activeVersionId
      if (['weekday', 'overtime', 'weekend', 'pet'].includes(data.scene)) scene.value = data.scene
      notifications.value = data.notifications === true
    } catch { /* A broken or unavailable local cache must not prevent startup. */ }
  }
  function persist() {
    try {
      localStorage.setItem('rentpilot-demo-v1', JSON.stringify({ weights: weights.value, versions: versions.value, activeVersionId: activeVersionId.value, activePlanId: activePlanId.value, savedListings: savedListings.value, savedPlans: savedPlans.value, messages: messages.value, conversations: conversations.value, viewings: viewings.value, nickname: nickname.value, scene: scene.value, notifications: notifications.value }))
      storageWarningShown = false
    } catch {
      if (!storageWarningShown) { storageWarningShown = true; notify('本机存储不可用，新内容暂未保存') }
    }
  }
  return { weights, versions, activeVersionId, activePlanId, displayPlans, activePlan, savedListings, savedPlans, messages, conversations, viewings, nickname, scene, notifications, toast, notify, sendMessage, startConversation, openConversation, scheduleViewing, cancelViewing, selectPlan, toggleSaved, togglePlan, applyWeights, applyNegotiation, restoreVersion, hydrate, persist }
})
