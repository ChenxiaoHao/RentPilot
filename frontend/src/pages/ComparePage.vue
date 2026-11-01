<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import MemberGate from '../components/MemberGate.vue'
import { usePlansStore } from '../stores/plans'
import { useMemberStore } from '../stores/member'

const route = useRoute()
const router = useRouter()
const store = usePlansStore()
const member = useMemberStore()
const leftId = ref('')
const rightId = ref('')
const lastKey = ref('')
const modeLabel: Record<string, string> = { WALKING: '步行', CYCLING: '骑行', TRANSIT: '公共交通', DRIVING: '驾车' }
const comparison = computed(() => store.comparison)
const onlyDiff = ref(false)
const key = computed(() => leftId.value && rightId.value && leftId.value !== rightId.value ? `${leftId.value}|${rightId.value}` : '')
function known(id: string) { return store.plans.some(plan => plan.id === id) }
function syncFromRoute() {
  const queryLeft = typeof route.query.left === 'string' ? route.query.left : ''
  const queryRight = typeof route.query.right === 'string' ? route.query.right : ''
  const first = store.plans[0]?.id ?? ''
  leftId.value = known(queryLeft) ? queryLeft : first
  const second = store.plans.find(plan => plan.id !== leftId.value)?.id ?? ''
  rightId.value = known(queryRight) && queryRight !== leftId.value ? queryRight : second
}
function syncQuery() {
  if (!leftId.value || !rightId.value) return
  router.replace({ query: { ...route.query, left: leftId.value, right: rightId.value } })
}
function change(side: 'left' | 'right', event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (side === 'left') {
    if (value === rightId.value) rightId.value = leftId.value
    leftId.value = value
  } else {
    if (value === leftId.value) leftId.value = rightId.value
    rightId.value = value
  }
  syncQuery()
}
function swap() { const previous = leftId.value; leftId.value = rightId.value; rightId.value = previous; syncQuery() }
function money(value: number | null) { return value == null ? '未知' : `¥${Math.round(value).toLocaleString('zh-CN')}` }
function commute(value: number | null) { return value == null ? '未核验' : `${value} 分钟` }
function captured(value: string) { return new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
function rentDiff(value: number | null) { if (value == null) return '无法比较'; if (value === 0) return '两侧相同'; return value > 0 ? `A 每月多 ${money(value)}` : `A 每月少 ${money(-value)}` }
function initialDiff(value: number | null) { if (value == null) return '无法比较'; if (value === 0) return '两侧相同'; return value > 0 ? `A 首期多 ${money(value)}` : `A 首期少 ${money(-value)}` }
function timeDiff(value: number | null) { if (value == null) return '无法比较'; if (value === 0) return '两侧相同'; return value > 0 ? `A 单程多 ${value} 分钟` : `A 单程少 ${-value} 分钟` }
onMounted(async () => {
  if (!member.loggedIn) return
  await store.load(true)
  syncFromRoute()
})
watch(() => store.plans, () => { if (!leftId.value) syncFromRoute() })
watch(() => [route.query.left, route.query.right], () => { if (store.plans.length) { const before = key.value; syncFromRoute(); if (before === key.value) return } })
watch(key, async current => {
  if (!current || current === lastKey.value) return
  lastKey.value = current
  try { await store.compare(leftId.value, rightId.value) } catch { /* store surfaces the error */ }
})
</script>

<template>
  <AppHeader title="方案对比" back />
  <main v-if="!member.loggedIn && !member.loading" class="page stack"><MemberGate title="登录，比较你的居住方案" /></main>
  <main v-else-if="store.loading" class="page stack"><div class="skeleton skeleton-card" role="status" aria-label="正在加载方案"></div></main>
  <main v-else-if="store.error && !comparison" class="page stack"><section class="member-error" role="alert"><p>{{ store.error }}</p><button class="text-action" @click="store.load(true).then(syncFromRoute)">重新加载</button></section></main>
  <main v-else-if="!store.hasEnoughToCompare" class="page empty-fallback"><AppIcon name="scale" :size="34" /><h1>至少需要两个方案</h1><p class="muted">先在 AI 找房结果中保存两个不同条件的方案，再回来对比。</p><RouterLink to="/plans" class="primary">查看我的方案<AppIcon name="arrow-right" /></RouterLink></main>
  <main v-else-if="comparison" class="page compare-page stack">
    <section><h1>两种生活，一起看看。</h1><p class="small muted compare-subtitle">对比保存时的租金、费用与通勤，不把未知补成确定值。</p></section>
    <p class="snapshot-note">两侧都是历史快照{{ comparison.left.capturedAt === comparison.right.capturedAt ? '' : '，保存时间不同' }}，不代表当前实时结果。</p>
    <section class="compare-pair" aria-label="选择对比方案">
      <article v-for="(side, index) in [comparison.left, comparison.right]" :key="index" class="compare-plan">
        <label :for="`compare-${index}`" class="sr-only">选择方案 {{ index === 0 ? 'A' : 'B' }}</label>
        <div class="select-wrap"><select :id="`compare-${index}`" :value="index === 0 ? leftId : rightId" @change="change(index === 0 ? 'left' : 'right', $event)"><option v-for="plan in store.plans" :key="plan.id" :value="plan.id">{{ plan.title }}</option></select></div>
        <p class="small">{{ side.primaryHomeName }} · {{ side.primaryDistrict }}</p>
        <p class="tiny muted">共 {{ side.homeCount }} 套 · 保存于 {{ captured(side.capturedAt) }} · {{ side.coverage === 'PARTIAL' ? '部分核验' : '已核验' }}</p>
        <span class="pair-letter">{{ index === 0 ? 'A' : 'B' }}</span>
      </article>
      <button class="icon-button swap-button" aria-label="交换对比方案" @click="swap"><AppIcon name="swap" :size="19" /></button>
    </section>
    <div class="segmented compare-mode" role="tablist"><button :aria-selected="!onlyDiff" @click="onlyDiff = false">全部字段</button><button :aria-selected="onlyDiff" @click="onlyDiff = true">只看差异</button></div>
    <section class="difference-summary"><p>月租：<strong>{{ rentDiff(comparison.rentDifference) }}</strong></p><p>首期（租金 + 押金）：<strong>{{ initialDiff(comparison.knownInitialPaymentDifference) }}</strong></p><p>单程通勤：<strong>{{ timeDiff(comparison.commuteMinutesDifference) }}</strong></p></section>
    <section class="comparison-table" aria-label="方案事实比较">
      <div v-if="!onlyDiff || comparison.rentDifference !== 0" class="comparison-row"><h2>主房源月租</h2><div class="two-col"><div v-for="(side, index) in [comparison.left, comparison.right]" :key="index"><p class="comparison-value">{{ money(side.rent) }}<span>/月</span></p></div></div></div>
      <div v-if="!onlyDiff || comparison.knownInitialPaymentDifference !== 0" class="comparison-row"><h2>已确认首期</h2><div class="two-col"><div v-for="(side, index) in [comparison.left, comparison.right]" :key="index"><p class="comparison-value">{{ money(side.knownInitialPayment) }}</p><span class="tiny muted">租金 + 押金；未知项目不计入</span></div></div></div>
      <div v-if="!onlyDiff || comparison.commuteMinutesDifference !== 0" class="comparison-row"><h2>单程通勤</h2><div class="two-col"><div v-for="(side, index) in [comparison.left, comparison.right]" :key="index"><p class="comparison-value">{{ commute(side.commuteMinutes) }}</p><span class="tiny muted">{{ side.commuteMode ? modeLabel[side.commuteMode] : '方式未记录' }}{{ side.commuteCheckedAt ? ` · 核验于 ${captured(side.commuteCheckedAt)}` : '' }}</span></div></div></div>
      <div v-if="!onlyDiff || comparison.left.propertyFee !== comparison.right.propertyFee || comparison.left.paymentMethod !== comparison.right.paymentMethod" class="comparison-row"><h2>费用信息</h2><div class="two-col"><div v-for="(side, index) in [comparison.left, comparison.right]" :key="index"><p class="amenity-copy">押金 {{ money(side.deposit) }}</p><span class="tiny muted">付款方式 {{ side.paymentMethod || '未知' }} · 物业费 {{ side.propertyFee == null ? '未知' : `${money(side.propertyFee)}（周期未知）` }}</span></div></div></div>
      <div v-if="!onlyDiff || comparison.left.partialCoverage !== comparison.right.partialCoverage" class="comparison-row"><h2>核验范围</h2><div class="two-col"><div v-for="(side, index) in [comparison.left, comparison.right]" :key="index"><p class="amenity-copy">{{ side.partialCoverage ? '部分核验' : '已核验' }}</p><span class="tiny muted">{{ side.partialCoverage ? '含未核验候选，不代表全量' : '当前候选范围内完成核验' }}</span></div></div></div>
    </section>
    <section class="notes"><h2>对比时要注意</h2><ul><li v-for="note in comparison.notes" :key="note">{{ note }}</li></ul></section>
    <section class="sim-links"><h2>把通勤换算成时间</h2><div><RouterLink :to="`/lifesim/${comparison.left.planId}`">看看 A 的一天<AppIcon name="arrow-right" :size="15" /></RouterLink><RouterLink :to="`/lifesim/${comparison.right.planId}`">看看 B 的一天<AppIcon name="arrow-right" :size="15" /></RouterLink></div></section>
    <div class="compare-actions"><RouterLink :to="`/plans/${comparison.left.planId}`" class="secondary">查看方案 A</RouterLink><RouterLink :to="`/plans/${comparison.right.planId}`" class="secondary">查看方案 B</RouterLink></div>
  </main>
  <main v-else class="page stack"><p class="rental-loading" role="status">正在对比方案…</p></main>
</template>

<style scoped>
.compare-page{padding-top:12px;gap:22px}.compare-page h1{font-size:25px}.compare-subtitle{margin-top:8px}.snapshot-note{font-size:12px;color:var(--muted);background:var(--surface);padding:10px 14px;border-radius:10px;line-height:1.7}.compare-pair{display:grid;grid-template-columns:1fr;gap:12px;position:relative}.compare-plan{min-width:0;position:relative;border:1px solid var(--line);border-radius:16px;padding:14px}.select-wrap select{width:100%;min-height:44px;border:0;background:#fff;font-size:13px;font-weight:600;padding:6px 0}.compare-plan p{overflow:hidden;text-overflow:ellipsis;line-height:1.6}.pair-letter{position:absolute;right:10px;top:10px;background:var(--surface);width:24px;height:24px;border-radius:6px;display:grid;place-items:center;font-size:12px}.swap-button{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);background:#fff;border:1px solid var(--line);width:44px;height:44px}.difference-summary{background:var(--surface);border-radius:16px;padding:16px 18px;font-size:13px;line-height:1.9}.difference-summary strong{font-weight:600;margin-left:5px}.comparison-table{border-top:1px solid var(--line)}.comparison-row{padding:18px 0;border-bottom:1px solid var(--line)}.comparison-row h2{font-size:12px;font-weight:400;color:var(--muted);margin-bottom:10px}.comparison-row .two-col{gap:16px}.comparison-row .two-col>div{min-width:0}.comparison-value{font-size:23px;font-weight:500;letter-spacing:-.04em;line-height:1.5;margin-bottom:4px}.comparison-value>span{font-size:12px;color:var(--muted);margin-left:5px;letter-spacing:0;font-weight:400}.amenity-copy{font-size:14px;margin-bottom:5px}.notes h2{font-size:16px;margin-bottom:10px}.notes ul{margin:0;padding-left:18px;display:grid;gap:6px;font-size:12px;color:var(--muted);line-height:1.7}.compare-actions{display:grid;grid-template-columns:1fr 1fr;gap:10px}.compare-actions .secondary{min-height:46px;font-size:13px;text-align:center}.member-error{background:var(--surface);padding:16px;border-radius:14px;font-size:13px}
@media(max-width:359px){.compare-page h1{font-size:22px}.comparison-value{font-size:20px}.compare-actions{grid-template-columns:1fr}}
.sim-links{display:grid;gap:10px}.sim-links h2{font-size:16px}.sim-links>div{display:grid;grid-template-columns:1fr 1fr;gap:10px}.sim-links a{min-height:44px;display:flex;align-items:center;justify-content:center;gap:6px;border:1px solid var(--line);border-radius:12px;font-size:12px}
.compare-mode{margin:2px 0 0}.compare-page h1{letter-spacing:-.05em}
</style>