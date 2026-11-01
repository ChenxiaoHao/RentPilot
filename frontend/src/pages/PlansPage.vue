<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import MemberGate from '../components/MemberGate.vue'
import AppSheet from '../components/AppSheet.vue'
import { usePlansStore } from '../stores/plans'
import { useMemberStore } from '../stores/member'
import type { PlanSummary } from '../services/plans'

const store = usePlansStore()
const member = useMemberStore()
const pendingDelete = ref('')
const actionPlan = ref<PlanSummary | null>(null)
const actionOpen = computed({ get: () => !!actionPlan.value, set: (open: boolean) => { if (!open) actionPlan.value = null } })
let pressTimer: ReturnType<typeof setTimeout> | undefined
function pressStart(plan: PlanSummary) { pressTimer = setTimeout(() => { actionPlan.value = plan }, 450) }
function pressEnd() { if (pressTimer) clearTimeout(pressTimer) }
onMounted(() => { if (member.loggedIn) void store.load(true) })
function money(value: number) { return `¥${Math.round(value).toLocaleString('zh-CN')}` }
function commute(plan: PlanSummary) { return plan.primaryCommuteMinutes == null ? '通勤未核验' : `${plan.primaryCommuteMinutes} 分钟` }
function captured(plan: PlanSummary) { return new Date(plan.capturedAt).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
async function remove(plan: PlanSummary) {
  if (pendingDelete.value !== plan.id) { pendingDelete.value = plan.id; return }
  pendingDelete.value = ''
  try { await store.remove(plan.id); actionPlan.value = null } catch { /* store surfaces the error */ }
}
</script>

<template>
  <AppHeader hide-avatar />
  <main class="page plans-page stack">
    <section class="plans-heading"><div class="between"><h1>我的方案</h1><span class="tiny muted">{{ store.count }} 个</span></div><p class="muted">从 AI 找房结果保存，保留当时的条件与核验范围。</p></section>
    <MemberGate v-if="!member.loggedIn && !member.loading" title="登录，保存你的居住方案" />
    <div v-else-if="member.loading || store.loading" class="skeleton skeleton-card" role="status" aria-label="正在加载方案"></div>
    <section v-else-if="store.error" class="member-error" role="alert"><p>{{ store.error }}</p><button class="text-action" @click="store.load(true)">重新加载</button></section>
    <section v-else-if="!store.count" class="empty-plans stack"><AppIcon name="layers" :size="30" /><h2>还没有保存的方案</h2><p class="small muted">在 AI 找房结果中点“保存为方案”，之后可在这里查看和对比。方案不会自动更新。</p><RouterLink to="/chat" class="primary">去 AI 找房<AppIcon name="arrow-right" /></RouterLink></section>
    <template v-else>
      <p class="snapshot-note">以下都是保存时的历史快照，不随房源数据变化自动更新。</p>
      <article v-for="plan in store.plans" :key="plan.id" class="saved-plan" @contextmenu.prevent="actionPlan = plan" @touchstart="pressStart(plan)" @touchend="pressEnd" @touchmove="pressEnd">
        <div class="between"><h2>{{ plan.title }}</h2><span class="badge">{{ plan.coverage === 'PARTIAL' ? '部分核验' : '已核验' }}</span></div>
        <p class="small muted row"><AppIcon name="map-pin" :size="13" />{{ plan.primaryDistrict }} · {{ plan.primaryTown }} · {{ plan.primaryHomeName }}</p>
        <div class="stat-row"><div><span class="stat-label">主房源月租</span><strong class="stat">{{ money(plan.primaryRent) }}<small>/月</small></strong></div><div><span class="stat-label">单程通勤</span><strong class="stat">{{ commute(plan) }}</strong></div></div>
        <p class="tiny muted">共 {{ plan.homeCount }} 套房源 · 保存于 {{ captured(plan) }}</p>
        <div class="plan-actions"><RouterLink :to="`/plans/${plan.id}`" class="secondary">查看详情</RouterLink><RouterLink :to="`/compare?left=${plan.id}`" class="secondary">对比</RouterLink><button class="text-action danger" :disabled="store.busy" @click="remove(plan)">{{ pendingDelete === plan.id ? '确认删除' : '删除' }}</button></div>
      </article>
    </template>
    <AppSheet v-model="actionOpen" title="方案操作"><div v-if="actionPlan" class="stack plan-actions-sheet"><p><strong>{{ actionPlan.title }}</strong></p><p class="small muted">{{ actionPlan.primaryDistrict }} · {{ actionPlan.primaryTown }} · 保存于 {{ captured(actionPlan) }}</p><RouterLink :to="`/plans/${actionPlan.id}`" class="primary" @click="actionPlan = null">查看详情</RouterLink><RouterLink :to="`/compare?left=${actionPlan.id}`" class="secondary" @click="actionPlan = null">与其他方案对比</RouterLink><button class="text-action danger" :disabled="store.busy" @click="remove(actionPlan)">{{ pendingDelete === actionPlan.id ? '确认删除' : '删除方案' }}</button></div></AppSheet>
  </main>
</template>

<style scoped>
.plans-page{padding-top:14px;gap:20px}.plans-heading p{font-size:13px;margin-top:6px}.plans-heading>.between{align-items:center}.snapshot-note{font-size:12px;color:var(--muted);background:var(--surface);padding:10px 14px;border-radius:10px;line-height:1.7}.empty-plans{align-items:flex-start;padding:28px 0}.empty-plans h2{font-size:19px}.empty-plans .primary{margin-top:6px}.saved-plan{border:1px solid var(--line);border-radius:18px;padding:18px;display:grid;gap:12px;background:#fff}.saved-plan h2{font-size:18px;line-height:1.5;overflow-wrap:anywhere}.saved-plan .row{gap:4px}.plan-facts{display:grid;grid-template-columns:1.1fr 1fr;gap:0;border-top:1px solid var(--line);border-bottom:1px solid var(--line);padding:12px 0}.plan-facts>div+div{border-left:1px solid var(--line);padding-left:18px}.plan-facts strong{display:block;font-size:22px;font-weight:600;line-height:1.6;margin-top:3px;letter-spacing:-.03em}.plan-facts small{font-size:12px;color:var(--muted);font-weight:400;margin-left:3px}.plan-actions{display:flex;align-items:center;gap:10px}.plan-actions .secondary{flex:1;min-height:44px;font-size:13px;text-align:center}.text-action.danger{margin-left:auto;font-size:12px;color:#8a4a4a;padding:8px;min-height:44px}.member-error{background:var(--surface);padding:16px;border-radius:14px;font-size:13px}
.saved-plan{touch-action:pan-y}.plan-actions-sheet .primary,.plan-actions-sheet .secondary{width:100%;min-height:48px;text-align:center}.plan-actions-sheet .text-action{min-height:44px;align-self:center}

</style>