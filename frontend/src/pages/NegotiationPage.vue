<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { usePilotStore } from '../stores/pilot'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
const choice = ref<'A' | 'B'>('A'), router = useRouter(), store = usePilotStore()
function apply() { store.applyNegotiation(choice.value === 'A' ? 'A' : 'B'); store.notify('已应用本地协商选择，结果为 Mock 方案'); router.push('/generating') }
function onChoiceKeydown(event: KeyboardEvent) {
  if (!['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  choice.value = event.key === 'Home' ? 'A' : event.key === 'End' ? 'B' : choice.value === 'A' ? 'B' : 'A'
  const group = (event.currentTarget as HTMLElement).closest('[role="radiogroup"]')
  group?.querySelector<HTMLButtonElement>(`[data-choice="${choice.value}"]`)?.focus()
}
</script>
<template>
  <AppHeader title="一起调整需求" back />
  <main class="page negotiation-page stack">
    <section><h1>预算和距离，<br />我们选哪一边？</h1><p class="small muted intro-copy">这组示例条件暂时无法兼顾。<br />只改你愿意调整的那一项。</p></section>
    <div class="request"><AppIcon name="message" :size="17" /><p>张江 · 地铁步行 5 分钟 · 整租一居<br /><strong>月租 ¥3,500 以内</strong></p></div>
    <section class="choice-group stack" role="radiogroup" aria-label="预算与通勤取舍方案"><button v-for="item in [{id:'A',name:'离工作近一点',subtitle:'增加预算，保留短途通勤',rent:'¥4,000',time:'15',mode:'骑行'},{id:'B',name:'把预算留给生活',subtitle:'保持租金，扩大通勤范围',rent:'¥3,500',time:'32',mode:'地铁'}]" :key="item.id" class="choice" role="radio" :data-choice="item.id" :aria-checked="choice === item.id" :tabindex="choice === item.id ? 0 : -1" :class="{selected:choice === item.id}" @click="choice=item.id as 'A'|'B'" @keydown="onChoiceKeydown"><div class="between"><span class="option-letter">方案 {{ item.id }}</span><span class="radio" :class="{checked:choice === item.id}"><AppIcon v-if="choice === item.id" name="check" :size="12" /></span></div><h2>{{ item.name }}</h2><p class="option-subtitle">{{ item.subtitle }}</p><div class="option-metrics"><div><span>参考月租</span><strong>{{ item.rent }}<small>/月</small></strong></div><div><span>单程通勤 · {{ item.mode }}</span><strong>{{ item.time }}<small>分钟</small></strong></div></div></button></section>
    <section class="negotiation-actions"><div role="status" aria-live="polite" aria-atomic="true" class="selection-preview"><p>已选定方案 {{ choice }} 预览</p><strong>{{ choice === 'A' ? '¥4,000 /月 · 骑行 15 分钟' : '¥3,500 /月 · 地铁 32 分钟' }}</strong></div><button class="primary" @click="apply">确认应用调整<AppIcon name="arrow-right" /></button><RouterLink to="/chat" class="text-action muted">保留原要求，继续聊聊<AppIcon name="chevron-right" :size="15" /></RouterLink></section>
  </main>
</template>
<style scoped>
.negotiation-page{padding-top:12px;gap:24px}.negotiation-page h1{font-size:27px;line-height:1.5}.intro-copy{margin-top:14px}.request{display:flex;gap:12px;align-items:flex-start;background:var(--surface);border-radius:16px;padding:18px;font-size:12px}.request>svg{margin-top:3px;flex-shrink:0}.request strong{display:block;margin-top:6px;font-weight:500}.choice-group{gap:14px}.choice{border:1px solid var(--line);border-radius:20px;padding:20px;text-align:left}.choice.selected{border-color:var(--ink);box-shadow:inset 0 0 0 .5px var(--ink)}.option-letter{font-size:12px;color:var(--muted)}.radio{width:20px;height:20px;border-radius:50%;border:1px solid #bbb;display:grid;place-items:center}.radio.checked{background:var(--ink);border-color:var(--ink);color:#fff}.choice h2{font-size:19px;font-weight:500;margin:12px 0 6px}.option-subtitle{font-size:12px;color:var(--muted)}.option-metrics{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));margin-top:22px;gap:14px}.option-metrics span{display:block;font-size:12px;color:var(--muted)}.option-metrics strong{font-size:24px;letter-spacing:-.04em;font-weight:500;display:block;line-height:1.6;margin-top:6px}.option-metrics small{font-size:12px;letter-spacing:0;font-weight:400;color:var(--muted);margin-left:4px}.negotiation-actions{display:flex;flex-direction:column;gap:10px}.selection-preview{text-align:center;padding:4px 0 8px}.selection-preview p{font-size:12px;color:var(--muted)}.selection-preview strong{font-size:13px;font-weight:400;display:block;margin-top:6px}.negotiation-actions>.text-action{font-size:12px}
@media(max-width:359px){.negotiation-page h1{font-size:25px}.choice{padding:16px}.choice h2{font-size:17px}.option-metrics strong{font-size:22px}}
</style>