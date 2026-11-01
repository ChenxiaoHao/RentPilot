<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import { money, type Weights } from '../data/mock'
import { previewWeights, usePilotStore } from '../stores/pilot'

const store = usePilotStore()
const router = useRouter()
const draft = reactive<Weights>({ ...store.weights })
const preview = computed(() => previewWeights(draft))
const baseline = computed(() => previewWeights(store.weights))
const changedCount = computed(() => (Object.keys(draft) as (keyof Weights)[]).filter(key => draft[key] !== store.weights[key]).length)
const changed = computed(() => changedCount.value > 0)
const controls: { key: keyof Weights; title: string; subtitle: string; icon: string; presets: { value: number; label: string }[] }[] = [
  { key: 'budget', title: '租金预算权重', subtitle: '对财务支出的敏感程度', icon: 'wallet', presets: [{ value: 90, label: '紧缩节省' }, { value: 60, label: '平衡适中' }, { value: 25, label: '预算宽松' }] },
  { key: 'commute', title: '通勤时效权重', subtitle: '职住距离与每日通勤节奏', icon: 'bike', presets: [{ value: 20, label: '可接受长通勤' }, { value: 55, label: '适度通勤' }, { value: 85, label: '优先近距离' }] },
  { key: 'lifestyle', title: '生活品质与周边', subtitle: '采光、安静与日常生活配套', icon: 'coffee', presets: [{ value: 20, label: '基础住宿' }, { value: 50, label: '烟火商圈' }, { value: 85, label: '品质街区' }] },
]
function description(key: keyof Weights) {
  if (key === 'budget') return draft.budget >= 75 ? '更看重租金可负担，给生活多留一些预算' : draft.budget >= 40 ? '在租金与居住体验之间，寻找舒适平衡' : '愿意为更合适的空间，留出更多租金预算'
  if (key === 'commute') return draft.commute >= 75 ? '希望更靠近工作地，减少每日路上时间' : draft.commute >= 40 ? '兼顾通勤效率，也保留更多选址空间' : '可以接受较长通勤，换取更多居住选择'
  return draft.lifestyle >= 75 ? '更看重社区品质、舒适采光与完整配套' : draft.lifestyle >= 40 ? '关注采光、安静和步行可达的日常配套' : '先满足基础居住需求，让租住更轻松'
}
function resetDraft() { Object.assign(draft, { budget: 60, commute: 85, lifestyle: 50 });  }
function apply() { if (!changed.value) return; store.applyWeights({ ...draft }); router.push('/generating?next=plans') }
const combinations = [{name:'通勤优先',weights:{budget:50,commute:95,lifestyle:55}},{name:'预算优先',weights:{budget:95,commute:45,lifestyle:40}},{name:'均衡生活',weights:{budget:65,commute:65,lifestyle:65}}]
const commuteChange = computed(() => preview.value.commute - baseline.value.commute)
const rentChange = computed(() => preview.value.rent - baseline.value.rent)
</script>

<template>
  <AppHeader title="居住偏好" back />
  <main class="page preferences-page stack">
    <section class="preference-intro"><h1>怎样住，更像你？</h1><p class="small muted">把你在意的事，排得更靠前一点。</p></section>
    <div class="choice-chips preference-combinations"><button v-for="combo in combinations" :key="combo.name" :aria-pressed="Object.keys(combo.weights).every(key => draft[key as keyof Weights] === combo.weights[key as keyof Weights])" @click="Object.assign(draft,combo.weights)">{{ combo.name }}</button></div>
    <section class="weight-list"><article v-for="control in controls" :key="control.key" class="weight-card"><div class="between"><label :for="`weight-${control.key}`"><AppIcon :name="control.icon" :size="19" /><strong>{{ control.title.replace('权重','') }}</strong></label><output :for="`weight-${control.key}`">{{ draft[control.key] }}<small>/100</small></output></div><input :id="`weight-${control.key}`" v-model.number="draft[control.key]" type="range" min="0" max="100" step="1" :aria-label="control.title" :aria-valuetext="`${draft[control.key]}，${description(control.key)}`" :style="{'--weight':`${draft[control.key]}%`}" /><div class="range-labels"><button v-for="preset in control.presets" :key="preset.value" :class="{selected:draft[control.key] === preset.value}" :aria-pressed="draft[control.key] === preset.value" @click="draft[control.key]=preset.value">{{ preset.label }}</button></div></article></section>
    <section class="preview-card" aria-label="偏好修改预览" aria-live="polite"><div class="between"><h2>调整后的示例</h2><span>{{ changed ? '变更预览（未生效）' : '当前偏好预览' }}</span></div><div class="preview-metrics"><div><p>参考月租</p><strong>{{ money(preview.rent) }}</strong><small>{{ rentChange === 0 ? '与当前一致' : rentChange < 0 ? `减少 ${money(Math.abs(rentChange))}` : `增加 ${money(rentChange)}` }}</small></div><div><p>单程通勤</p><strong>{{ preview.commute }}<small>分钟</small></strong><small>{{ commuteChange === 0 ? '与当前一致' : commuteChange < 0 ? `缩短 ${Math.abs(commuteChange)} 分钟` : `增加 ${commuteChange} 分钟` }}</small></div></div></section>
    <p class="draft-status" role="status" aria-label="偏好草稿状态"><AppIcon :name="changed ? 'sliders' : 'check'" :size="15" />{{ changed ? `${changedCount} 项偏好待应用` : '与已应用偏好一致' }}</p>
  </main>
  <footer class="preferences-actions"><button class="text-action" @click="resetDraft">重置权重</button><button class="primary" :disabled="!changed" @click="apply">应用修改并重新规划<AppIcon name="arrow-right" :size="17" /></button></footer>
</template>
<style scoped>
.preferences-page{padding-top:12px;padding-bottom:calc(114px + var(--safe-bottom));gap:24px}.preference-intro h1{font-size:27px}.preference-intro p{margin-top:10px}.preference-combinations{gap:8px}.preference-combinations button{flex:1;font-size:12px;padding-inline:8px;white-space:nowrap}.weight-list{border-top:1px solid var(--line)}.weight-card{padding:22px 0 16px;border-bottom:1px solid var(--line)}.weight-card label{display:flex;align-items:center;gap:10px}.weight-card label strong{font-size:14px;font-weight:500}.weight-card output{font-size:25px;letter-spacing:-.04em;font-weight:500;white-space:nowrap}.weight-card output small{font-size:12px;margin-left:4px;color:var(--muted);font-weight:400;letter-spacing:0}.weight-card input[type=range]{width:100%;height:44px;margin:12px 0 0;appearance:none;background:transparent;cursor:pointer;display:block}.weight-card input::-webkit-slider-runnable-track{height:4px;background:linear-gradient(to right,var(--ink) 0,var(--ink) var(--weight),#e5e5e5 var(--weight),#e5e5e5 100%);border-radius:8px}.weight-card input::-webkit-slider-thumb{appearance:none;width:22px;height:22px;background:var(--ink);border:3px solid white;border-radius:50%;margin-top:-9px;box-shadow:0 0 0 1px var(--ink)}.weight-card input::-moz-range-track{height:4px;background:#e5e5e5}.weight-card input::-moz-range-progress{height:4px;background:var(--ink)}.weight-card input::-moz-range-thumb{width:20px;height:20px;border-radius:50%;border:2px solid white;background:var(--ink)}.range-labels{display:flex;justify-content:space-between;gap:8px}.range-labels button{font-size:12px;color:var(--muted);min-height:44px;padding:8px 0}.range-labels button.selected{color:var(--ink);font-weight:600}.preview-card{background:var(--surface);border-radius:20px;padding:20px}.preview-card>.between h2{font-size:15px;font-weight:500}.preview-card>.between>span{font-size:12px;color:var(--muted)}.preview-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin-top:22px;gap:16px}.preview-metrics>div+div{padding-left:18px;border-left:1px solid #ddd}.preview-metrics p{font-size:12px;color:var(--muted)}.preview-metrics strong{font-size:26px;font-weight:500;letter-spacing:-.04em;display:block;line-height:1.5;margin:6px 0}.preview-metrics strong small{font-size:12px;margin-left:4px;font-weight:400;letter-spacing:0}.preview-metrics>div>small{font-size:12px;color:var(--muted)}.draft-status{display:flex;gap:7px;align-items:center;justify-content:center;font-size:12px;color:var(--muted);margin-top:-6px}.preferences-actions{position:fixed;left:50%;transform:translateX(-50%);bottom:0;width:min(100%,640px);z-index:50;display:flex;align-items:center;gap:18px;padding:16px var(--gutter) calc(16px + var(--safe-bottom));background:#fff;border-top:1px solid var(--line)}.preferences-actions>.text-action{font-size:13px;flex-shrink:0}.preferences-actions>.primary{flex:1;min-width:0}
@media(max-width:359px){.preference-intro h1{font-size:24px}.weight-card label strong{font-size:13px}.weight-card label{gap:8px}.weight-card output{font-size:23px}.preferences-actions{gap:12px}.preferences-actions>.primary{font-size:12px;padding-inline:12px}.preview-card{padding:16px}.preview-card>.between{gap:6px}.preview-card>.between>span{font-size:12px}.preview-metrics strong{font-size:23px}}
</style>