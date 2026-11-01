<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import MemberGate from '../components/MemberGate.vue'
import { useMemberStore } from '../stores/member'
import { usePlansStore } from '../stores/plans'
import { useLifesimStore } from '../stores/lifesim'
import type { DailySpendBand, LifesimAnswers, LifesimSource, TravelMode } from '../services/lifesim'

const route = useRoute()
const router = useRouter()
const member = useMemberStore()
const plans = usePlansStore()
const store = useLifesimStore()
const planId = computed(() => String(route.params.id || ''))
const step = ref<'time' | 'spend'>('time')
const dirty = ref(false)
const blank = (): LifesimAnswers => ({ commuteMinutes: null, workStart: null, sleepTime: null, overtimeDays: null, overtimeEnd: null, dailySpendBand: null, fixedCost: null, cookingDays: null })
const answers = ref<LifesimAnswers>(blank())

const workStarts = ['07:30', '08:00', '08:30', '09:00', '09:30', '10:00']
const overtimeOptions = [0, 1, 2, 3, 5]
const overtimeEnds = ['20:00', '21:00', '22:00', '22:30']
const sleepTimes = ['22:00', '22:30', '23:00', '23:30', '00:00']
const cookingOptions = [0, 1, 3, 5, 7]
const spendOptions: { value: DailySpendBand; label: string }[] = [
  { value: 'UNDER_1000', label: '1000 以内' },
  { value: '1000_2000', label: '1000–2000' },
  { value: '2000_3000', label: '2000–3000' },
  { value: 'OVER_3000', label: '3000 以上' },
]
const modeLabel: Record<string, string> = { WALKING: '步行', CYCLING: '骑行', TRANSIT: '公共交通', DRIVING: '驾车' }
const sourceLabel: Record<LifesimSource, string> = { FACT: '已核验', USER: '你填的', DERIVED: '推算', ASSUMED: '假设', UNKNOWN: '未知' }
const view = computed(() => store.view)
const planOptions = computed(() => plans.plans.length ? plans.plans.map(p => ({ id: p.id, title: p.title })) : view.value ? [{ id: view.value.planId, title: view.value.title }] : [])
const current = computed(() => plans.plans.find(p => p.id === planId.value))
const coverage = computed(() => current.value?.coverage === 'PARTIAL' ? '部分核验' : current.value ? '已核验' : '')

onMounted(async () => {
  if (!member.loggedIn) return
  await plans.load(true)
  await load()
})
watch(planId, () => { if (member.loggedIn) void load() })
async function load() {
  const result = await store.load(planId.value)
  if (result) {
    answers.value = { ...result.answers }
    dirty.value = false
    step.value = result.answers.workStart && result.answers.sleepTime ? 'spend' : 'time'
  }
}
function set<K extends keyof LifesimAnswers>(key: K, value: LifesimAnswers[K]) {
  answers.value = { ...answers.value, [key]: value }
  dirty.value = true
}
function skip(key: keyof LifesimAnswers) { set(key, null) }
async function submit() {
  try {
    await store.save(planId.value, answers.value)
    dirty.value = false
  } catch { /* store surfaces the error */ }
}
function changePlan(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  if (value && value !== planId.value) router.replace(`/lifesim/${value}`)
}
function money(value: number | null) { return value == null ? '未知' : `¥${Math.round(value).toLocaleString('zh-CN')}` }
function n(value: number) { return Math.round(value).toLocaleString('zh-CN') }
function monthlyRange() {
  const c = view.value?.costs
  if (!c || c.monthlyLow == null) return '未知'
  if (c.monthlyHigh == null) return `¥${n(c.monthlyLow)} 起`
  if (c.monthlyHigh === c.monthlyLow) return `¥${n(c.monthlyLow)}`
  return `¥${n(c.monthlyLow)}–${n(c.monthlyHigh)}`
}
function sourceClass(source: LifesimSource) { return `src-${source.toLowerCase()}` }
function captured(value: string) { return new Date(value).toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
</script>

<template>
  <AppHeader title="生活账本" back />
  <main v-if="!member.loggedIn && !member.loading" class="page stack"><MemberGate title="登录，看看你的一天" /></main>
  <main v-else-if="store.loading && !view" class="page stack"><div class="skeleton skeleton-card" role="status" aria-label="正在读取方案"></div></main>
  <main v-else-if="store.error && !view" class="page empty-fallback"><AppIcon name="help" :size="34" /><h1>生活账本暂时打不开</h1><p class="muted">{{ store.error }}</p><RouterLink to="/plans" class="primary">返回我的方案<AppIcon name="arrow-right" /></RouterLink></main>
  <main v-else-if="view" class="page lifesim-page stack">
    <section class="plan-switch">
      <div class="plan-select"><p class="eyebrow">基于已保存的方案</p><label class="sr-only" for="lifesim-plan">选择方案</label><select id="lifesim-plan" :value="planId" @change="changePlan"><option v-for="plan in planOptions" :key="plan.id" :value="plan.id">{{ plan.title }}</option></select></div>
      <span v-if="coverage" class="badge">{{ coverage }}</span>
    </section>

    <section class="summary-grid">
      <div><span class="tiny muted">主房源月租</span><strong>{{ money(view.costs.monthlyRent) }}<small>/月</small></strong></div>
      <div><span class="tiny muted">单程通勤</span><strong>{{ view.commuteMinutes == null ? '未核验' : `${view.commuteMinutes} 分钟` }}</strong><span v-if="view.sources.commute === 'USER'" class="tiny muted">你填的</span></div>
      <div><span class="tiny muted">核验快照</span><strong class="small-strong">{{ captured(view.capturedAt) }}</strong></div>
    </section>

    <section v-if="view.status === 'NEEDS_COMMUTE'" class="notice-card"><AppIcon name="help" :size="22" /><div><h2>这套方案还没有通勤时间</h2><p>你可以在下面先填一个估计值排出时间轴；它会标成“你填的”，不会冒充路线核验。</p><RouterLink to="/chat" class="text-action">去重新搜索核验<AppIcon name="arrow-right" :size="15" /></RouterLink></div></section>

    <template v-if="view">
      <section class="ask-card">
        <div class="between"><div><p class="eyebrow">先问你几个问题</p><h2>你的一天怎么过？</h2></div><span class="step-count">{{ step === 'time' ? '1 / 2' : '2 / 2' }}</span></div>
        <div class="step-tabs" role="tablist"><button role="tab" :aria-selected="step === 'time'" @click="step = 'time'">时间</button><button role="tab" :aria-selected="step === 'spend'" @click="step = 'spend'">消费</button></div>

        <div v-if="step === 'time'" class="question-stack">
          <fieldset class="question"><legend>单程通勤大概多少分钟？</legend><label class="commute-input"><input type="number" min="1" max="300" inputmode="numeric" placeholder="例如 35" :value="answers.commuteMinutes ?? ''" @input="set('commuteMinutes', ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value))" /><span>分钟</span></label><p v-if="view.sources.commute === 'USER'" class="tiny muted">当前使用你填的估计值；方案里已核验的值仍保留。</p><p v-else-if="view.commuteMinutes != null" class="tiny muted">方案已核验 {{ view.commuteMinutes }} 分钟；你可以改成自己的估计。</p><p v-else class="tiny muted">方案还没有核验通勤；先填一个估计值，时间轴会标成“你填的”。</p><button class="skip" type="button" @click="skip('commuteMinutes')">不确定，先跳过</button></fieldset>
          <fieldset class="question"><legend>通常几点上班？</legend><div class="chips"><button v-for="time in workStarts" :key="time" :aria-pressed="answers.workStart === time" @click="set('workStart', time)">{{ time }}</button></div><button class="skip" type="button" @click="skip('workStart')">不确定，先跳过</button></fieldset>
          <fieldset class="question"><legend>一周大概几天加班？</legend><div class="chips"><button v-for="days in overtimeOptions" :key="days" :aria-pressed="answers.overtimeDays === days" @click="set('overtimeDays', days)">{{ days === 0 ? '不加班' : `${days} 天` }}</button></div><button class="skip" type="button" @click="skip('overtimeDays')">不确定，先跳过</button></fieldset>
          <fieldset v-if="(answers.overtimeDays ?? 0) > 0" class="question"><legend>加班时大概几点下班？</legend><div class="chips"><button v-for="time in overtimeEnds" :key="time" :aria-pressed="answers.overtimeEnd === time" @click="set('overtimeEnd', time)">{{ time }}</button></div><button class="skip" type="button" @click="skip('overtimeEnd')">不确定，先跳过</button></fieldset>
          <fieldset class="question"><legend>通常几点睡觉？</legend><div class="chips"><button v-for="time in sleepTimes" :key="time" :aria-pressed="answers.sleepTime === time" @click="set('sleepTime', time)">{{ time }}</button></div><button class="skip" type="button" @click="skip('sleepTime')">不确定，先跳过</button></fieldset>
        </div>

        <div v-else class="question-stack">
          <fieldset class="question"><legend>每月日常开销大概多少？（不含房租）</legend><div class="chips"><button v-for="option in spendOptions" :key="option.value" :aria-pressed="answers.dailySpendBand === option.value" @click="set('dailySpendBand', option.value)">{{ option.label }}</button></div><button class="skip" type="button" @click="skip('dailySpendBand')">不确定，先跳过</button></fieldset>
          <fieldset class="question"><legend>每月固定支出（健身、宠物、通勤卡等）</legend><label class="money-input"><span>¥</span><input type="number" min="0" max="100000" inputmode="numeric" placeholder="未填写" :value="answers.fixedCost ?? ''" @input="set('fixedCost', ($event.target as HTMLInputElement).value === '' ? null : Number(($event.target as HTMLInputElement).value))" /></label><button class="skip" type="button" @click="skip('fixedCost')">不确定，先跳过</button></fieldset>
          <fieldset class="question"><legend>一周大概在家做几次饭？</legend><div class="chips"><button v-for="days in cookingOptions" :key="days" :aria-pressed="answers.cookingDays === days" @click="set('cookingDays', days)">{{ days === 0 ? '基本不做' : `${days} 次` }}</button></div><button class="skip" type="button" @click="skip('cookingDays')">不确定，先跳过</button></fieldset>
        </div>

        <p v-if="store.error" class="form-error" role="alert">{{ store.error }}</p>
        <button class="primary generate" :disabled="store.saving" @click="submit"><AppIcon name="sparkles" :size="17" />{{ store.saving ? '正在计算…' : view.updatedAt ? '更新我的一天' : '生成我的一天' }}</button>
        <p class="tiny muted footnote">只会用你填写的内容和已核验的通勤；跳过的不估算。</p>
      </section>

      <section v-if="view.timeline.length" class="timeline-section">
        <p v-if="view.status === 'INCOMPLETE'" class="preview-note">先给你一版默认假设（09:00 上班、23:30 睡觉），改成你的作息就会重算。</p>
        <div class="between"><h2>你的一天</h2><span class="tiny muted">{{ view.commuteMinutes }} 分钟{{ modeLabel[view.mode || ''] || '通勤' }}</span></div>
        <ol class="timeline"><li v-for="moment in view.timeline" :key="moment.key"><span class="moment-time">{{ moment.time }}</span><span class="moment-dot" :class="sourceClass(moment.source)" /><div class="moment-body"><div class="between"><strong>{{ moment.label }}</strong><span class="source-badge" :class="sourceClass(moment.source)">{{ sourceLabel[moment.source] }}</span></div><p>{{ moment.detail }}</p></div></li></ol>
      </section>

      <section class="cost-card">
        <div class="between"><span class="cost-label">每月居住开销</span><span class="source-badge src-derived">仅含已知项</span></div>
        <p class="cost-range">{{ monthlyRange() }}<small>/月</small></p>
        <dl class="cost-rows">
          <div><dt>月租 <span class="source-badge src-fact">已核验</span></dt><dd>{{ money(view.costs.monthlyRent) }}</dd></div>
          <div><dt>物业费 <span class="source-badge" :class="view.costs.propertyFee == null || view.costs.propertyFeePeriod === 'UNSPECIFIED' ? 'src-unknown' : 'src-fact'">{{ view.costs.propertyFee == null || view.costs.propertyFeePeriod === 'UNSPECIFIED' ? '未知' : '已核验' }}</span></dt><dd>{{ view.costs.propertyFee == null ? '未知' : money(view.costs.propertyFee) }}</dd></div>
          <div><dt>固定支出 <span class="source-badge" :class="view.costs.fixedCost == null ? 'src-unknown' : 'src-user'">{{ view.costs.fixedCost == null ? '未知' : '你填的' }}</span></dt><dd>{{ money(view.costs.fixedCost) }}</dd></div>
          <div><dt>日常开销 <span class="source-badge" :class="view.costs.dailySpendBand == null ? 'src-unknown' : 'src-user'">{{ view.costs.dailySpendBand == null ? '未知' : '你填的' }}</span></dt><dd>{{ view.costs.dailySpendLabel }}</dd></div>
          <div><dt>首期（租金 + 押金）<span class="source-badge src-fact">已核验</span></dt><dd>{{ money(view.costs.knownInitialPayment) }}</dd></div>
        </dl>
      </section>
    </template>

    <section class="legend"><p class="eyebrow">标记说明</p><div class="legend-items"><span><i class="src-fact" />已核验事实</span><span><i class="src-user" />你填的</span><span><i class="src-derived" />系统推算</span><span><i class="src-assumed" />固定假设</span><span><i class="src-unknown" />未知</span></div></section>
    <section class="limits"><h2>这份账本的边界</h2><ul><li v-for="limit in view.limitations" :key="limit">{{ limit }}</li></ul><p v-if="view.updatedAt" class="tiny muted">你上次更新：{{ captured(view.updatedAt) }}</p></section>
    <div class="sim-actions"><RouterLink :to="`/compare?left=${planId}`" class="secondary"><AppIcon name="scale" :size="16" />与另一方案对比</RouterLink></div>
  </main>
</template>

<style scoped>
.lifesim-page{padding-top:10px;gap:22px}.plan-switch{display:flex;align-items:center;gap:12px;padding:14px 16px;background:var(--surface);border-radius:16px}.plan-select{flex:1;min-width:0}.eyebrow{font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--muted);margin-bottom:5px}.plan-select select{display:block;width:100%;min-height:34px;border:0;background:transparent;font-size:15px;font-weight:600;padding:0;appearance:none}.summary-grid{display:grid;grid-template-columns:1.15fr 1fr 1fr;border-top:1px solid var(--line);border-bottom:1px solid var(--line);padding:16px 0}.summary-grid>div+div{border-left:1px solid var(--line);padding-left:14px}.summary-grid strong{display:block;font-size:22px;font-weight:600;letter-spacing:-.04em;line-height:1.55;margin-top:3px;white-space:nowrap}.summary-grid strong small{font-size:11px;color:var(--muted);font-weight:400;margin-left:2px;letter-spacing:0}.summary-grid .small-strong{font-size:13px;letter-spacing:0;font-weight:500;white-space:normal}.notice-card{display:flex;gap:12px;padding:18px;border:1px solid var(--line);border-radius:18px;background:var(--surface)}.notice-card>svg{flex-shrink:0;color:var(--muted);margin-top:2px}.notice-card h2{font-size:16px;line-height:1.45}.notice-card p{font-size:12px;line-height:1.75;color:var(--muted);margin:8px 0 10px}.ask-card{border:1px solid var(--line);border-radius:20px;padding:18px;display:grid;gap:16px}.ask-card h2{font-size:20px;letter-spacing:-.02em}.step-count{font-size:12px;color:var(--muted);font-variant-numeric:tabular-nums}.step-tabs{display:grid;grid-template-columns:1fr 1fr;gap:6px;background:var(--surface);padding:4px;border-radius:12px}.step-tabs button{min-height:38px;border-radius:9px;font-size:12px;color:var(--muted)}.step-tabs button[aria-selected=true]{background:#fff;color:var(--ink);box-shadow:0 1px 3px rgba(0,0,0,.08);font-weight:600}.question-stack{display:grid;gap:20px}.question{border:0;padding:0;margin:0;min-width:0}.question legend{font-size:13px;font-weight:500;padding:0;margin-bottom:10px}.chips{display:flex;flex-wrap:wrap;gap:7px}.chips button{min-height:40px;padding:0 13px;border:1px solid var(--line);border-radius:999px;background:#fff;font-size:12px;transition:transform .12s ease,background .12s ease,color .12s ease}.chips button[aria-pressed=true]{background:var(--ink);color:#fff;border-color:var(--ink);transform:scale(.97)}.skip{display:block;margin-top:8px;min-height:32px;padding:0;font-size:11px;color:var(--muted);background:none;border:0;text-decoration:underline;text-underline-offset:3px}.money-input{display:flex;align-items:center;gap:6px;border-bottom:1px solid var(--line);padding:4px 0;max-width:220px}.money-input span{font-size:15px;color:var(--muted)}.money-input input{width:100%;border:0;background:transparent;font-size:17px;font-weight:600;padding:8px 0;min-height:40px}.generate{min-height:48px;font-size:14px;gap:8px;margin-top:2px}.footnote{text-align:center}.timeline-section h2{font-size:19px}.timeline{list-style:none;margin:16px 0 0;padding:0;display:grid}.timeline li{display:grid;grid-template-columns:52px 16px 1fr;gap:10px;position:relative;padding-bottom:18px}.timeline li:not(:last-child)::before{content:'';position:absolute;left:59px;top:14px;bottom:-2px;width:1px;background:var(--line)}.moment-time{font-size:11px;color:var(--muted);font-variant-numeric:tabular-nums;padding-top:2px;text-align:right}.moment-dot{width:9px;height:9px;border-radius:50%;margin-top:4px;margin-left:3px;background:var(--ink);z-index:1}.moment-body{min-width:0;display:grid;gap:5px}.moment-body strong{font-size:14px;font-weight:500}.moment-body p{font-size:11px;line-height:1.7;color:var(--muted)}.source-badge{display:inline-flex;align-items:center;height:19px;padding:0 6px;border-radius:5px;font-size:9px;letter-spacing:.02em;border:1px solid var(--line);color:var(--muted);background:#fff;white-space:nowrap}.source-badge.src-fact,.legend i.src-fact{background:var(--ink);color:#fff;border-color:var(--ink)}.source-badge.src-user,.legend i.src-user{background:#fff;color:var(--ink);border:1px solid var(--ink)}.source-badge.src-derived,.legend i.src-derived{background:#fff;color:var(--ink);border:1px dashed var(--ink)}.source-badge.src-assumed,.legend i.src-assumed{background:var(--surface);color:var(--muted);border:1px solid var(--line)}.source-badge.src-unknown,.legend i.src-unknown{background:#fff;color:#9a9a9a;border:1px dotted #9a9a9a}.moment-dot.src-user{background:#fff;border:2px solid var(--ink)}.moment-dot.src-derived{background:#fff;border:2px dashed var(--ink)}.moment-dot.src-assumed{background:var(--surface);border:2px solid var(--line)}.moment-dot.src-unknown{background:#fff;border:2px dotted #9a9a9a}.cost-card{background:var(--ink);color:#fff;border-radius:20px;padding:20px;display:grid;gap:14px}.cost-label{font-size:11px;letter-spacing:.08em;text-transform:uppercase;opacity:.65}.cost-card .source-badge{border-color:rgba(255,255,255,.3);background:transparent;color:rgba(255,255,255,.7)}.cost-range{font-size:30px;font-weight:500;letter-spacing:-.05em;line-height:1.3;white-space:nowrap}.cost-range small{font-size:11px;font-weight:400;letter-spacing:0;opacity:.6;margin-left:4px}.cost-rows{margin:0;display:grid;gap:0}.cost-rows>div{display:flex;align-items:center;justify-content:space-between;gap:14px;padding:11px 0;border-top:1px solid rgba(255,255,255,.16);font-size:12px}.cost-rows dt{display:flex;align-items:center;gap:7px;color:rgba(255,255,255,.7);min-width:0}.cost-rows dd{margin:0;font-variant-numeric:tabular-nums;white-space:nowrap}.cost-rows .source-badge{height:17px;font-size:8px}.legend{padding:0 2px}.legend-items{display:flex;flex-wrap:wrap;gap:8px 14px;font-size:10px;color:var(--muted)}.legend-items span{display:inline-flex;align-items:center;gap:5px}.legend-items i{display:inline-block;width:12px;height:8px;border-radius:3px}.limits h2{font-size:15px;margin-bottom:10px}.limits ul{margin:0;padding-left:17px;display:grid;gap:6px;font-size:11px;line-height:1.75;color:var(--muted)}.limits .tiny{margin-top:10px}.sim-actions .secondary{min-height:46px;font-size:13px;gap:7px}.form-error{font-size:12px;color:#8a4a4a}.empty-fallback{align-items:flex-start}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}@media(max-width:359px){.summary-grid strong{font-size:19px}.cost-range{font-size:25px}}
.commute-input{display:flex;align-items:center;gap:8px;border-bottom:1px solid var(--line);max-width:190px;padding:4px 0}.commute-input input{width:100%;border:0;background:transparent;font-size:17px;font-weight:600;min-height:40px;padding:8px 0}.commute-input span{font-size:12px;color:var(--muted)}
.preview-note{font-size:12px;color:var(--muted);background:var(--surface);padding:10px 12px;border-radius:12px;line-height:1.6;margin-bottom:12px}
</style>
