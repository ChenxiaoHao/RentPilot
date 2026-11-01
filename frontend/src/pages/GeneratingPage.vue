<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
const router = useRouter()
const progress = ref(0), step = computed(() => Math.min(4, Math.floor(progress.value / 20)))
const steps = [{ title: '理解居住需求', hint: '提取预算、工作地点与生活偏好' }, { title: '分析通勤区域', hint: '匹配示例通勤走廊与生活圈' }, { title: '匹配合适房源', hint: '筛选本地 Mock 房源数据' }, { title: '评估居住风险', hint: '比较通勤、预算和生活总成本' }, { title: '生成生活方案', hint: '整理三种不同的生活方式' }]
let timer: ReturnType<typeof setInterval>
onMounted(() => { timer = setInterval(() => { progress.value = Math.min(100, progress.value + 4); if (progress.value === 100) clearInterval(timer) }, 110) })
onUnmounted(() => clearInterval(timer))
function done() { if (progress.value === 100) router.replace('/plans') }
</script>
<template>
  <AppHeader title="居住方案规划" back />
  <main class="page generating" :aria-busy="progress < 100">
    <section class="generate-intro"><div class="progress-orbit"><svg viewBox="0 0 104 104" aria-hidden="true"><circle cx="52" cy="52" r="47" fill="none" stroke="#eee" stroke-width="3"/><circle cx="52" cy="52" r="47" fill="none" stroke="currentColor" stroke-width="3" :stroke-dasharray="`${progress*2.953} 295.3`" transform="rotate(-90 52 52)"/></svg><span>{{ progress }}<small>%</small></span></div><h1>{{ progress < 100 ? '把生活，安排得更合适。' : '三种生活，都为你准备好了。' }}</h1><p>张江 · 租金 ¥4,800 · 通勤 30 分钟内</p><span class="tiny muted">固定示例条件，未解析本次需求</span></section>
    <progress class="sr-only" :value="progress" max="100" aria-label="方案生成进度" />
    <section class="pipeline"><div v-for="(item,i) in steps" :key="item.title" class="step" :class="{future:i>step}" :aria-current="i === step && progress < 100 ? 'step' : undefined"><span class="step-icon" :class="{done:i<step || progress === 100,active:i === step && progress < 100}"><AppIcon :name="i<step || progress === 100 ? 'check' : i === step ? 'loader' : 'clock'" :size="17" /></span><div><h2>{{ item.title }}</h2><p>{{ item.hint }}</p></div><span class="step-state">{{ i<step || progress === 100 ? '完成' : i === step ? '进行中' : '' }}</span></div></section>
    <div class="next-action"><p role="status" aria-live="polite" aria-atomic="true">{{ progress < 100 ? `当前阶段：${steps[step]!.title}` : '本地规划已完成，可以查看居住方案。' }}</p><button class="primary" :disabled="progress < 100" @click="done">查看居住方案<AppIcon name="arrow-right" /></button><RouterLink to="/chat" class="text-action muted">{{ progress < 100 ? '先返回，继续补充需求' : '回到对话' }}</RouterLink></div>
  </main>
</template>
<style scoped>
.generating{display:flex;flex-direction:column;gap:26px;padding-top:18px;padding-bottom:calc(24px + var(--safe-bottom));min-height:calc(var(--app-height) - 72px - var(--safe-top))}.generate-intro{text-align:center}.progress-orbit{width:104px;height:104px;position:relative;margin:0 auto 24px}.progress-orbit svg{width:100%;height:100%}.progress-orbit span{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;font-size:27px;font-variant-numeric:tabular-nums;gap:2px}.progress-orbit small{font-size:12px;align-self:center;color:var(--muted)}.generate-intro h1{font-size:23px;line-height:1.55;letter-spacing:-.03em}.generate-intro>p{font-size:12px;color:var(--muted);margin-top:12px}.generate-intro>.tiny{display:block;font-size:12px;margin-top:8px}.pipeline{padding:4px 0}.step{display:flex;gap:14px;align-items:center;min-height:62px}.step>div{flex:1;min-width:0}.step h2{font-size:14px;font-weight:500}.step p{font-size:12px;color:var(--muted);margin-top:5px}.step-icon{display:grid;place-items:center;width:28px;height:28px;flex-shrink:0;color:#777}.step-icon.done{background:var(--ink);color:#fff;border-radius:50%}.step-icon.active>svg{animation:working 2s linear infinite}.step-state{font-size:12px;color:var(--muted);white-space:nowrap}.step.future{color:#666}.step.future .step-icon{color:#888}.next-action{margin-top:auto;display:flex;flex-direction:column;gap:10px}.next-action>p{text-align:center;font-size:12px;color:var(--muted);min-height:20px}.next-action>.text-action{font-size:12px}@keyframes working{to{transform:rotate(360deg)}}
@media(max-height:720px){.generating{gap:18px;padding-top:0}.progress-orbit{width:76px;height:76px;margin-bottom:18px}.generate-intro h1{font-size:21px}.step{min-height:54px}.step p{display:none}.next-action{margin-top:0}}
@media(max-width:359px){.generate-intro h1{font-size:21px}.generate-intro>p{font-size:12px}}
</style>