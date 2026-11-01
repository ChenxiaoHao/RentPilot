<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import AppSheet from '../components/AppSheet.vue'
import { money, plans, type Version } from '../data/mock'
import { usePilotStore } from '../stores/pilot'

const store = usePilotStore()
const showComparison = ref(false)
const fromId = ref(store.versions.find(version => version.id !== store.activeVersionId)?.id ?? store.activeVersionId)
const toId = ref(store.activeVersionId)
const comparison = ref<HTMLElement>()
const showRestore = ref(false)
const pendingRestoreId = ref('')
const pendingVersion = computed(() => store.versions.find(version => version.id === pendingRestoreId.value))
const active = computed(() => store.versions.find(version => version.id === store.activeVersionId) ?? store.versions[0])
const from = computed(() => store.versions.find(version => version.id === fromId.value))
const to = computed(() => store.versions.find(version => version.id === toId.value))
const delta = computed(() => from.value && to.value ? { rent: to.value.rent - from.value.rent, commute: to.value.commute - from.value.commute, match: to.value.match - from.value.match } : null)
const restoreTarget = computed(() => from.value?.id !== store.activeVersionId ? from.value : to.value?.id !== store.activeVersionId ? to.value : undefined)
function planArea(version: Version) { return plans.find(plan => plan.id === version.planId)?.area ?? '示例区域' }
function planName(version: Version) { return plans.find(plan => plan.id === version.planId)?.name ?? version.title }
async function compareVersion(id: string) {
  fromId.value = id
  toId.value = store.activeVersionId
  showComparison.value = true
  await nextTick()
  comparison.value?.focus({ preventScroll: true })
  comparison.value?.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth', block: 'start' })
}
function signed(value: number, unit = '') { return `${value > 0 ? '+' : ''}${value}${unit}` }
function requestRestore(id: string) { pendingRestoreId.value = id; showRestore.value = true }
function confirmRestore() {
  const id = pendingRestoreId.value
  if (store.restoreVersion(id)) {
    store.notify(`已恢复 ${id} 的偏好与方案`)
    toId.value = id
    showRestore.value = false
  }
}
</script>

<template>
  <AppHeader title="方案历史" back />
  <main class="page history-page stack">
    <section v-if="active" class="current-model"><div class="between"><span class="current-state"><i></i>当前使用</span><time>{{ active.date }}</time></div><h1>{{ active.id }} · {{ active.title }}</h1><div class="current-metrics"><div><span>参考月租</span><strong>{{ money(active.rent) }}</strong></div><div><span>单程通勤</span><strong>{{ active.commute }}<small>分钟</small></strong></div></div></section>
    <section class="version-section"><div class="between version-section-heading"><h2>每一步，都有记录</h2><button class="text-action muted" :aria-expanded="showComparison" aria-controls="history-comparison" @click="showComparison=!showComparison"><AppIcon name="scale" :size="15" />差异速查</button></div><div class="version-list"><article v-for="version in store.versions" :key="version.id" class="version-card"><div class="between version-heading"><h3>{{ version.id }} <span v-if="version.id === store.activeVersionId">使用中</span></h3><time>{{ version.date }}</time></div><p class="version-title">{{ version.title }}</p><p class="version-values"><span>{{ money(version.rent) }}<small>/月</small></span><span>{{ version.commute }}<small>分钟 / 单程</small></span></p><div class="version-actions"><RouterLink :to="{path:`/plans/${version.planId}`,query:{version:version.id}}" class="text-action">查看详情<AppIcon name="chevron-right" :size="14" /></RouterLink><button v-if="version.id !== store.activeVersionId" class="text-action" @click="compareVersion(version.id)"><AppIcon name="scale" :size="15" />{{ fromId === version.id && showComparison ? '已在对比' : '与当前对比' }}</button></div></article></div></section>
    <section v-if="showComparison" id="history-comparison" ref="comparison" data-testid="history-comparison" class="comparison-card" aria-label="方案版本差异速查" tabindex="-1"><h2>这次，改变了什么？</h2><div class="comparison-selectors"><label>起始版本<select v-model="fromId" aria-label="起始版本"><option v-for="version in store.versions" :key="version.id" :value="version.id">{{ version.id }}</option></select></label><AppIcon name="arrow-right" :size="17" /><label>对比版本<select v-model="toId" aria-label="对比版本"><option v-for="version in store.versions" :key="version.id" :value="version.id">{{ version.id }}</option></select></label></div><template v-if="from && to && delta"><p v-if="from.id === to.id" class="same-version">同一版本，所有差异均为 0。</p><dl class="diff-ledger"><div><dt>月租变化</dt><dd><span>{{ money(from.rent) }} → {{ money(to.rent) }}</span><strong>{{ delta.rent > 0 ? '+' : delta.rent < 0 ? '−' : '' }}{{ money(Math.abs(delta.rent)) }}/月</strong></dd></div><div><dt>单程通勤</dt><dd><span>{{ from.commute }} → {{ to.commute }} 分钟</span><strong>{{ signed(delta.commute,' 分钟') }}</strong></dd></div><div><dt>居住区域</dt><dd><span>{{ planArea(from) }}</span><strong>{{ planArea(to) }}</strong></dd></div></dl><div class="weight-diff"><p>偏好重视程度</p><div><span>租金</span><strong>{{ from.weights.budget }} → {{ to.weights.budget }}</strong></div><div><span>通勤</span><strong>{{ from.weights.commute }} → {{ to.weights.commute }}</strong></div><div><span>生活</span><strong>{{ from.weights.lifestyle }} → {{ to.weights.lifestyle }}</strong></div></div></template></section>
    <footer v-if="showComparison && restoreTarget" class="history-restore"><button class="primary" @click="requestRestore(restoreTarget.id)"><AppIcon name="refresh" :size="17" />恢复至 {{ restoreTarget.id }} 方案并重新激活</button><p>历史版本会继续保留</p></footer>
  </main>
  <AppSheet v-model="showRestore" title="恢复这个历史版本？" description="恢复这版居住方案与偏好，其他历史版本仍会保留。"><div v-if="pendingVersion" class="restore-preview stack"><h3>{{ pendingVersion.id }} · {{ pendingVersion.title }}</h3><dl class="ledger"><div><dt>参考月租</dt><dd>{{ money(pendingVersion.rent) }}</dd></div><div><dt>单程通勤</dt><dd>{{ pendingVersion.commute }} 分钟</dd></div><div><dt>方案</dt><dd>{{ planName(pendingVersion) }}</dd></div></dl></div><template #footer><button class="secondary" @click="showRestore=false">取消</button><button class="primary" @click="confirmRestore">确认恢复</button></template></AppSheet>
</template>
<style scoped>
.history-page{padding-top:12px;gap:28px}.current-model{background:var(--surface);border-radius:20px;padding:22px}.current-model>.between{font-size:12px;color:var(--muted)}.current-state{display:flex;gap:7px;align-items:center;color:var(--ink)}.current-state i{width:5px;height:5px;border-radius:50%;background:var(--ink)}.current-model h1{font-size:21px;font-weight:500;line-height:1.6;letter-spacing:-.025em;margin-top:18px;overflow-wrap:anywhere}.current-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin-top:22px;gap:14px}.current-metrics>div+div{border-left:1px solid #ddd;padding-left:20px}.current-metrics span{font-size:12px;color:var(--muted);display:block;margin-bottom:8px}.current-metrics strong{font-size:26px;font-weight:500;letter-spacing:-.04em}.current-metrics small{font-size:12px;color:var(--muted);font-weight:400;margin-left:4px;letter-spacing:0}.version-section-heading{margin-bottom:8px}.version-section-heading h2{font-size:18px}.version-section-heading button{font-size:12px}.version-card{border-bottom:1px solid var(--line);padding:20px 0 8px}.version-heading h3{font-size:15px;font-weight:500}.version-heading h3>span{font-size:12px;color:var(--muted);margin-left:8px;font-weight:400}.version-heading time{font-size:12px;color:var(--muted)}.version-title{font-size:13px;color:var(--muted);margin-top:10px}.version-values{display:flex;gap:22px;margin-top:12px;font-size:18px}.version-values small{color:var(--muted);font-size:12px;margin-left:4px}.version-actions{display:flex;justify-content:space-between;gap:16px;margin-top:6px}.version-actions>*{font-size:12px}.comparison-card{padding:20px;background:var(--surface);border-radius:20px;scroll-margin-top:20px}.comparison-card>h2{font-size:18px}.comparison-selectors{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);gap:14px;align-items:center;margin:22px 0}.comparison-selectors label{display:flex;flex-direction:column;gap:8px;font-size:12px;color:var(--muted)}.comparison-selectors select{width:100%;min-height:44px;border:0;background:#fff;border-radius:10px;padding:10px;color:var(--ink);font-size:13px}.comparison-selectors>svg{margin-top:20px}.diff-ledger{display:flex;flex-direction:column}.diff-ledger>div{display:flex;justify-content:space-between;gap:16px;align-items:flex-start;padding:16px 0;border-top:1px solid #e1e1e1}.diff-ledger dt{color:var(--muted);font-size:12px;flex-shrink:0}.diff-ledger dd{display:flex;flex-direction:column;align-items:flex-end;gap:8px;font-size:12px;text-align:right}.diff-ledger dd>span{font-size:12px;color:var(--muted)}.diff-ledger strong{font-weight:500}.weight-diff{padding-top:16px;border-top:1px solid #e1e1e1}.weight-diff>p{font-size:12px;margin-bottom:14px;color:var(--muted)}.weight-diff>div{display:flex;justify-content:space-between;gap:12px;font-size:12px;padding:7px 0}.weight-diff strong{font-weight:400}.same-version{font-size:12px;color:var(--muted);padding-bottom:16px}.history-restore p{text-align:center;font-size:12px;color:var(--muted);margin-top:12px}.history-restore .primary{font-size:13px}.restore-preview h3{font-size:18px}
@media(max-width:359px){.current-model{padding:18px}.current-model h1{font-size:19px}.current-metrics strong{font-size:23px}.current-metrics>div+div{padding-left:14px}.comparison-card{padding:16px}.version-section-heading h2{font-size:16px}.version-values{gap:16px}.history-restore .primary{font-size:12px;padding-inline:10px}}
</style>