<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import { money } from '../data/mock'
import { usePilotStore } from '../stores/pilot'

const route = useRoute()
const router = useRouter()
const store = usePilotStore()
const budget = computed(() => { const value = Number(route.query.budget ?? 3000); return Number.isFinite(value) && value >= 0 ? value : 0 })
const commute = computed(() => { const value = Number(route.query.commute ?? 5); return Number.isFinite(value) && value >= 0 ? value : 0 })
const originalFilters = computed(() => ({ district: String(route.query.district ?? 'all'), type: String(route.query.type ?? 'one'), metro: String(route.query.metro ?? 'all') }))
const originalQuery = computed(() => ({ ...route.query, ...originalFilters.value, budget: budget.value ? String(budget.value) : undefined, commute: commute.value ? String(commute.value) : undefined }))
const filterSuggestion = computed(() => originalFilters.value.type === 'two' ? '调整户型为一居' : originalFilters.value.district !== 'all' ? '查看全部区域' : originalFilters.value.metro !== 'all' ? '查看全部地铁线路' : '查看全部房源')
const relaxedBudget = computed(() => Math.max(4200, budget.value + 500))
const relaxedCommute = computed(() => Math.max(40, commute.value + 15))
function applySuggestion(kind: 'budget' | 'commute') {
  router.push({ path: '/map', query: { ...originalQuery.value, ...(kind === 'budget' ? { budget: String(relaxedBudget.value) } : { commute: String(relaxedCommute.value) }) } })
}
function relaxOtherFilter() {
  const query = { ...originalQuery.value }
  if (query.type === 'two') query.type = 'one'
  else if (query.district !== 'all') query.district = 'all'
  else query.metro = 'all'
  router.push({ path: '/map', query })
}
function toggleNotifications() { store.notifications = !store.notifications; store.notify(store.notifications ? '已保存本地 Mock 通知偏好，不会发送实际推送' : '已关闭本地 Mock 通知偏好') }
</script>

<template>
  <AppHeader title="找房建议" back />
  <main class="page empty-page stack">
    <RouterLink :to="{path:'/map',query:originalQuery}" class="search-summary" aria-label="返回房源地图"><AppIcon name="search" :size="17" /><span>{{ originalFilters.district === 'all' ? '上海' : originalFilters.district }} · {{ budget ? `${money(budget)} 以内` : '预算不限' }} · {{ commute ? `${commute} 分钟内` : '通勤不限' }}</span><AppIcon name="sliders" :size="17" /></RouterLink>
    <section class="empty-intro"><div class="empty-orbit"><AppIcon name="search" :size="33" /></div><h1>还没遇到，<br />完全合适的那套。</h1><p>试着调整一项条件，给生活多一点可能。</p></section>
    <section class="suggestion-section"><h2>换个方向，继续找</h2><article v-if="budget" class="suggestion-card"><AppIcon name="wallet" :size="21" /><div><h3>租金放宽到 {{ money(relaxedBudget) }}</h3><p>增加 {{ money(relaxedBudget-budget) }}，其他条件保持。</p><button class="text-action" @click="applySuggestion('budget')">应用此建议<AppIcon name="arrow-right" :size="15" /></button></div></article><article v-if="commute" class="suggestion-card"><AppIcon name="bike" :size="21" /><div><h3>通勤放宽到 {{ relaxedCommute }} 分钟</h3><p>{{ budget ? `预算仍是 ${money(budget)} 以内。` : '预算仍保持不限。' }}</p><button class="text-action" @click="applySuggestion('commute')">应用此建议<AppIcon name="arrow-right" :size="15" /></button></div></article><article v-if="!budget || !commute" class="suggestion-card"><AppIcon name="sliders" :size="21" /><div><h3>{{ filterSuggestion }}</h3><p>只调整这一项，保留其他要求。</p><button class="text-action" @click="relaxOtherFilter">{{ filterSuggestion }}<AppIcon name="arrow-right" :size="15" /></button></div></article></section>
    <div class="empty-actions"><RouterLink to="/chat" class="primary">再聊聊我的找房需求<AppIcon name="message" :size="18" /></RouterLink><RouterLink :to="{path:'/map',query:originalQuery}" class="text-action muted">自己修改筛选条件<AppIcon name="chevron-right" :size="15" /></RouterLink></div>
    <section class="notification-card"><div><h2>有合适的房源再提醒我</h2><p>仅保存本地偏好，暂不发送推送</p></div><button class="toggle" :class="{enabled:store.notifications}" role="switch" :aria-checked="store.notifications" aria-label="本地 Mock 房源变动提醒" @click="toggleNotifications"><span></span></button></section>
  </main>
</template>
<style scoped>
.empty-page{padding-top:10px;gap:28px}.search-summary{display:flex;align-items:center;gap:10px;min-height:48px;padding:10px 14px;background:var(--surface);border-radius:14px}.search-summary>span{flex:1;min-width:0;font-size:12px;color:var(--muted);line-height:1.7}.search-summary>svg{flex-shrink:0;color:var(--muted)}.empty-intro{padding:8px 0 4px}.empty-orbit{width:62px;height:62px;display:grid;place-items:center;background:var(--surface);border-radius:50%;margin-bottom:22px}.empty-intro h1{font-size:28px;line-height:1.5;letter-spacing:-.04em}.empty-intro p{font-size:13px;color:var(--muted);margin-top:14px}.suggestion-section>h2{font-size:18px;margin-bottom:4px}.suggestion-card{display:flex;align-items:flex-start;gap:16px;padding:22px 0 12px;border-bottom:1px solid var(--line)}.suggestion-card>svg{flex-shrink:0;margin-top:2px}.suggestion-card>div{flex:1;min-width:0}.suggestion-card h3{font-size:15px;font-weight:500}.suggestion-card p{font-size:12px;color:var(--muted);margin-top:8px}.suggestion-card button{font-size:12px;margin-top:6px}.empty-actions{display:flex;flex-direction:column;gap:8px}.notification-card{display:flex;align-items:center;gap:14px;padding-top:20px;border-top:1px solid var(--line)}.notification-card>div{flex:1;min-width:0}.notification-card h2{font-size:13px;font-weight:500}.notification-card p{font-size:12px;color:var(--muted);margin-top:8px}
@media(max-width:359px){.empty-intro h1{font-size:25px}.suggestion-card{gap:12px}.suggestion-card h3{font-size:14px}.notification-card{gap:8px}.notification-card h2{font-size:12px}}
</style>
