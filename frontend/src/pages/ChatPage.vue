<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { usePilotStore } from '../stores/pilot'
import { useAiStore } from '../stores/ai'
import { useMemberStore } from '../stores/member'
import { usePlansStore } from '../stores/plans'
import { fetchCatalog } from '../services/catalog'
import type { CatalogListing } from '../services/catalog-types'
import type { Workplace, TravelMode, SearchAdjustment } from '../services/ai'
import { catalogPhoto, catalogTitle } from '../services/catalog-photos'
import { money } from '../data/mock'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import AppSheet from '../components/AppSheet.vue'
import SearchConditionsSheet from '../components/SearchConditionsSheet.vue'
import { emptyConditions, conditionsFromResult } from '../services/search-conditions'
import CatalogCard from '../components/CatalogCard.vue'
import RecommendationDetails from '../components/RecommendationDetails.vue'

const router = useRouter()
const pilot = usePilotStore(), store = useAiStore(), member = useMemberStore(), plans = usePlansStore()
const savedTokens = ref(new Set<string>())
async function saveAsPlan(result: import('../services/ai').SearchResult) {
  if (!result.searchToken || plans.busy || savedTokens.value.has(result.searchToken)) return
  try {
    const saved = await plans.save({ searchToken: result.searchToken, listingIds: result.recommendations.map(rec => rec.listing.id) })
    savedTokens.value = new Set([...savedTokens.value, result.searchToken])
    await router.push(`/lifesim/${saved.plan.id}`)
  } catch (cause) {
    pilot.notify(cause instanceof Error ? cause.message : '保存方案失败，请重试')
  }
}
const visibleLimitations = computed(() => {
  const seen = new Set<string>()
  return store.messages.map(message => (message.result?.limitations ?? []).filter(limit => {
    const key = limit.trim().replace(/\s+/g, ' ')
    if (message.result?.commute?.coverage === 'PARTIAL' && key === '仅核验了部分候选房源，其余尚未核验。') return false
    if (!key || seen.has(key)) return false
    seen.add(key)
    return true
  }))
})
const previews = ref<CatalogListing[]>([])
const featuredPlan = computed(() => plans.plans[0])
fetchCatalog({}).then(r => previews.value = r.items.slice(0,2)).catch(() => {})
const input = ref(''), content = ref<HTMLElement>(), textarea = ref<HTMLTextAreaElement>(), fileInput = ref<HTMLInputElement>()
const reference = ref<{ name: string; url: string } | null>(null)
const awayFromLatest = ref(false), conditionsOpen = ref(false), historyOpen = ref(false)
const conditions = reactive(emptyConditions())
let prefilledResult = ''
function openConditions() {
  const message = [...store.messages].reverse().find(m => m.result)
  if (message?.result && message.id !== prefilledResult) { Object.assign(conditions, conditionsFromResult(message.result)); prefilledResult = message.id }
  conditionsOpen.value = true
}
watch(() => store.contextVersion, () => { Object.assign(conditions, emptyConditions()); prefilledResult = ''; conditionsOpen.value = false; input.value = ''; removeReference(); void nextTick(resize) })
const prompts = [
  { label: '通勤找房', hint: '先定工作地', icon: 'briefcase', text: '我想根据工作地点和通勤时间找房。' },
  { label: '预算找房', hint: '先定月租', icon: 'wallet', text: '我想根据月租预算找房，希望兼顾通勤和生活便利。' },
  { label: '生活便利', hint: '先定生活半径', icon: 'coffee', text: '我更看重生活便利，希望下楼能买菜、喝咖啡，住得安静。' },
]
function resize() {
  if (!textarea.value) return
  textarea.value.style.height = 'auto'
  textarea.value.style.height = `${Math.min(textarea.value.scrollHeight, 120)}px`
}
function latest() {
  content.value?.scrollTo({ top: content.value.scrollHeight, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' })
}
function trackScroll() {
  if (content.value) awayFromLatest.value = content.value.scrollHeight - content.value.clientHeight - content.value.scrollTop > 180
}
async function send() {
  const text = input.value.trim()
  if (!text || store.loading) return
  if (reference.value) pilot.notify('文字已发送，参考图仅在本地预览')
  const context = store.contextVersion
  input.value = ''; removeReference(); void store.sendMessage(text).then(ok => { if (store.contextVersion !== context) return; if (!ok && !store.failed && store.error && !input.value) input.value = text; void nextTick(latest) }); await nextTick(); resize(); latest()
}
const pendingAdjustment = ref<{option:SearchAdjustment;token:string;messageId:string;to:number}|null>(null)
const adjustmentLimit = computed(()=>pendingAdjustment.value?.option.changes[0]?.field==='maxRent'?1000000:180)
const adjustmentValid = computed(()=>{const p=pendingAdjustment.value;if(!p)return false;const c=p.option.changes[0];return !!c&&Number.isFinite(p.to)&&p.to>Number(c.from)&&p.to<=adjustmentLimit.value})
const adjustmentOpen = computed({get:()=>!!pendingAdjustment.value,set:(open:boolean)=>{if(!open) pendingAdjustment.value=null}})
watch(()=>[store.contextVersion, store.messages.at(-1)?.id],()=>{pendingAdjustment.value=null})
function previewAdjustment(option:SearchAdjustment,token:string,messageId:string){pendingAdjustment.value={option,token,messageId,to:Number(option.changes[0]?.to ?? 0)}}
function confirmAdjustment(){const pending=pendingAdjustment.value;if(!pending||!adjustmentValid.value||store.messages.at(-1)?.id!==pending.messageId)return;const c=pending.option.changes[0]!;const changes=`${c.field==='maxRent'?'月租上限':'通勤上限'}从 ${c.from} 调整为 ${pending.to}${c.field==='maxRent'?' 元':' 分钟'}`;pendingAdjustment.value=null;void store.sendMessage(`${changes}，其他条件不变`,false,undefined,{type:'APPLY_ADJUSTMENT',token:pending.token,adjustmentId:pending.option.id,to:pending.to}).then(()=>nextTick(latest))}
const modeLabels:Record<TravelMode,string> = { WALKING:'步行', CYCLING:'骑行', TRANSIT:'公共交通', DRIVING:'驾车' }
function sendChoice(text:string, workplaceId?:string) { if(store.loading)return; void store.sendMessage(text,false,workplaceId).then(()=>nextTick(latest)) }
function continueSearch(token:string){void store.sendMessage('继续核验，其他条件不变',false,undefined,{type:'CONTINUE',token}).then(()=>nextTick(latest))}
function chooseWorkplace(place:Workplace) { sendChoice(`工作地点选择：${place.name}（${place.address}），其他条件不变`,place.id) }
function keydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && (event.ctrlKey || event.metaKey) && !event.isComposing) { event.preventDefault(); send() }
}
function focusInput() { textarea.value?.focus() }
function choose(text: string) { input.value = text; nextTick(() => { resize(); textarea.value?.focus() }) }
function applyConditions(text: string) { input.value = text; nextTick(resize) }
function newConversation() { store.startConversation(); input.value = ''; removeReference(); nextTick(() => { resize(); content.value?.scrollTo({ top: 0 }) }) }
function reopen(id: string) { store.openConversation(id); historyOpen.value = false; nextTick(latest) }
async function copy(text: string) {
  try { await navigator.clipboard.writeText(text); pilot.notify('已复制') } catch { pilot.notify('当前环境无法复制') }
}
function removeReference() {
  if (reference.value) URL.revokeObjectURL(reference.value.url)
  reference.value = null
  if (fileInput.value) fileInput.value.value = ''
}
function attachment(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) { pilot.notify('请选择图片'); return }
  if (file.size > 10 * 1024 * 1024) { pilot.notify('图片需小于 10 MB'); if (fileInput.value) fileInput.value.value = ''; return }
  removeReference(); reference.value = { name: file.name, url: URL.createObjectURL(file) }
}
onMounted(() => { if (member.loggedIn) void plans.load(true); if (content.value && store.messages.length) content.value.scrollTop = content.value.scrollHeight })
onBeforeUnmount(() => { store.cancel(); removeReference() })
</script>

<template>
  <div class="chat-page">
    <AppHeader hide-avatar>
      <button class="icon-button" aria-label="查看历史对话" @click="historyOpen = true"><AppIcon name="history" /></button>
      <button v-if="store.messages.length" class="icon-button" aria-label="新建对话" @click="newConversation"><AppIcon name="plus" /></button>
    </AppHeader>
    <main ref="content" class="chat-content" @scroll.passive="trackScroll">
      <section v-if="!store.messages.length" class="welcome">
        <div class="welcome-heading"><div class="welcome-kicker"><button class="city-control" aria-label="编辑找房条件" @click="openConditions"><AppIcon name="map-pin" :size="15" />上海<AppIcon name="chevron-down" :size="13" /></button><span class="sim-tag">模拟房源</span></div><h1>住得合适，<br />生活才从容。</h1><p class="muted welcome-sub">从工作地出发，找到属于你的生活半径。</p></div>
        <div class="prompt-grid" aria-label="快捷填写需求"><button v-for="prompt in prompts" :key="prompt.label" @click="choose(prompt.text)"><span class="prompt-icon"><AppIcon :name="prompt.icon" :size="17" /></span><span class="prompt-copy"><strong>{{ prompt.label }}</strong><small>{{ prompt.hint }}</small></span></button></div>
      </section>

      <section v-else class="conversation stack">
        <div class="between conversation-heading"><h1>一起，把需求理清楚。</h1><span class="tiny muted">模拟房源数据</span></div>
        <div class="message-list stack" role="log" aria-label="对话消息" aria-live="polite" aria-relevant="additions text"><article v-for="(message, messageIndex) in store.messages" :key="message.id" class="message" :class="message.role"><div v-if="message.role === 'assistant'" class="assistant-caption"><AppIcon name="sparkles" :size="17" /><strong>RentPilot</strong></div><div class="bubble">{{ message.text }}</div><div class="message-actions"><button v-if="message.role === 'assistant'" class="icon-button" aria-label="复制这条回复" @click="copy(message.text)"><AppIcon name="copy" :size="15" /></button><button v-else class="icon-button" aria-label="再次编辑这条需求" @click="choose(message.text)"><AppIcon name="pencil" :size="14" /></button></div><div v-if="message.result" class="recommendations"><div v-if="messageIndex === store.messages.length - 1 && message.result.commute?.state === 'NEEDS_MODE'" class="choice-chips commute-choices"><button v-for="mode in (['WALKING','CYCLING','TRANSIT','DRIVING'] as TravelMode[])" :key="mode" :disabled="store.loading" @click="sendChoice(`通勤方式选择${modeLabels[mode]}，其他条件不变`)">{{ modeLabels[mode] }}</button></div><div v-if="messageIndex === store.messages.length - 1 && message.result.workplaceCandidates?.length" class="workplace-choices"><button v-for="place in message.result.workplaceCandidates" :key="place.id" :disabled="store.loading" @click="chooseWorkplace(place)"><strong>{{ place.name }}</strong><span>{{ place.address }}</span></button></div><div v-if="messageIndex === store.messages.length - 1 && message.result.searchToken" class="closure-actions"><button v-if="message.result.hasMore" :disabled="store.loading" @click="continueSearch(message.result.searchToken)">继续核验</button><template v-if="message.result.status === 'NO_MATCH'"><button v-for="option in (message.result.adjustments || []).slice(0,2)" :key="option.id" :disabled="store.loading" @click="previewAdjustment(option,message.result.searchToken,message.id)">{{ option.label }}</button></template></div><div v-if="messageIndex === store.messages.length - 1 && message.result.status === 'MATCHES' && message.result.searchToken" class="save-plan"><template v-if="member.loggedIn"><button class="primary" type="button" :disabled="plans.busy || store.loading || savedTokens.has(message.result.searchToken)" @click="saveAsPlan(message.result)">{{ savedTokens.has(message.result.searchToken) ? '已保存到我的方案' : plans.busy ? '正在保存…' : '保存为方案' }}</button><RouterLink to="/plans" class="text-action">查看方案</RouterLink></template><RouterLink v-else to="/account" class="secondary">登录后保存为方案</RouterLink></div><div v-if="message.result.commute" class="signature-bar" role="status"><div class="signature-track"><i :style="{ width: `${Math.min(100, Math.round(message.result.commute.checkedCount / Math.max(1, message.result.commute.candidateCount) * 100))}%` }"></i></div><p>已核验 {{ message.result.commute.checkedCount }} / {{ message.result.commute.candidateCount }} 个候选房源{{ message.result.commute.candidateCount > message.result.commute.checkedCount ? ` · ${message.result.commute.candidateCount - message.result.commute.checkedCount} 个未核验` : ' · 全部完成' }}</p></div><p v-for="limit in visibleLimitations[messageIndex]" :key="limit" class="tiny muted">{{ limit }}</p><p v-if="messageIndex === store.messages.length - 1 && message.result.sortRule" class="tiny muted">{{ message.result.sortRule.label }}<span v-if="message.result.sortRule.partial"> · 仅覆盖已核验房源</span></p><template v-for="rec in message.result.recommendations" :key="rec.listing.id"><CatalogCard :home="rec.listing" /><p v-if="rec.commute && !rec.insight" class="commute-result">至工作地：{{ modeLabels[rec.commute.mode] }}约 {{ rec.commute.minutes }} 分钟 · {{ (rec.commute.distanceMeters / 1000).toFixed(1) }} 公里 <span class="tiny muted">高德估算 · 从小区位置出发</span></p><RecommendationDetails v-if="rec.insight" :insight="rec.insight" :reasons="rec.reasons" :listing-id="rec.listing.id" :search-token="message.result.searchToken" :active="messageIndex === store.messages.length - 1 && !store.loading" :context-key="`${store.contextVersion}:${message.id}:${message.result.searchToken || ''}`" /><p v-else class="tiny muted">{{ rec.reasons.join(' · ') }}</p></template></div></article></div>
        <p v-if="store.loading" role="status">正在检索房源… <button type="button" class="text-action" @click="store.cancel()">取消</button></p>
        <p v-if="store.error" role="alert">{{ store.error }} <button v-if="store.failed" type="button" class="text-action" @click="store.sendMessage(store.failed, true)">重新发送</button></p>
      </section>
    </main>
    <div class="chat-footer">
      <button v-if="awayFromLatest && store.messages.length" class="latest-message" @click="latest">回到最新消息<AppIcon name="chevron-down" :size="15" /></button>
      <form class="composer" @submit.prevent="send">
        <div v-if="reference" class="reference-preview"><img :src="reference.url" alt="本地参考图" /><div><strong>{{ reference.name }}</strong><p>仅本地预览，尚未接入图片理解</p></div><button type="button" class="icon-button" aria-label="移除参考图片" @click="removeReference"><AppIcon name="x" :size="17" /></button></div>
        <textarea ref="textarea" v-model="input" aria-label="找房需求" placeholder="说说你的预算、工作地和生活期待…" rows="1" maxlength="2000" @input="resize" @keydown="keydown"></textarea>
        <div class="composer-tools"><input ref="fileInput" type="file" accept="image/*" hidden @change="attachment" /><button type="button" class="icon-button" aria-label="上传参考图片" @click="fileInput?.click()"><AppIcon name="image" :size="20" /></button><button type="button" class="composer-condition" aria-label="编辑找房条件" @click="openConditions"><AppIcon name="sliders" :size="15" />找房条件</button><span v-if="input.length > 1750" class="tiny muted">{{ input.length }}/2000</span><button type="submit" class="send-button" :disabled="!input.trim() || store.loading" aria-label="发送消息"><AppIcon name="arrow-up" :size="21" /></button></div>
      </form>
    </div>
    <AppSheet v-model="adjustmentOpen" title="调整上限"><div v-if="pendingAdjustment" class="stack"><div class="adjust-summary"><p v-for="change in pendingAdjustment.option.changes" :key="change.field"><span>{{ change.field === 'maxRent' ? '月租上限' : '通勤上限' }}</span><strong>{{ change.from }} → {{ pendingAdjustment.to }} {{ change.field === 'maxRent' ? '元' : '分钟' }}</strong></p></div><label class="adjust-field"><span>{{ pendingAdjustment.option.changes[0]?.field === 'maxRent' ? '改成多少元？' : '改成多少分钟？' }}</span><input v-model.number="pendingAdjustment.to" type="number" min="1" :max="adjustmentLimit" inputmode="numeric" /></label><p class="tiny muted">这是你手动改的上限，确认后按新值重新筛选{{ pendingAdjustment.option.previewKind === 'FILTER_ONLY' ? '，通勤尚未核验' : '并重新核验通勤' }}。</p><p class="tiny muted">原值：{{ pendingAdjustment.option.changes[0]?.from }} {{ pendingAdjustment.option.changes[0]?.field === 'maxRent' ? '元' : '分钟' }}</p></div><template #footer><button class="secondary" @click="adjustmentOpen = false">保持原条件</button><button class="primary" :disabled="store.loading || !adjustmentValid" @click="confirmAdjustment">确认并重新查找</button></template></AppSheet>
    <SearchConditionsSheet v-model="conditionsOpen" :conditions="conditions" @apply="applyConditions" />
    <AppSheet v-model="historyOpen" title="历史对话"><div class="stack"><button v-for="conversation in store.conversations" :key="conversation.id" class="history-conversation" @click="reopen(conversation.id)"><AppIcon name="message" /><span><strong>{{ conversation.title }}</strong><small class="muted">{{ new Date(conversation.updatedAt).toLocaleDateString('zh-CN') }} · {{ conversation.messages.filter(m => m.role === 'user').length }} 轮对话</small></span><AppIcon name="chevron-right" :size="17" /></button><div v-if="!store.conversations.length" class="empty-conversations"><AppIcon name="message" :size="30" /><h3>还没有归档对话</h3><p class="small muted">开启新对话后，之前的需求会保存在这里。</p></div></div><template #footer><button class="secondary" @click="historyOpen = false">继续当前对话</button></template></AppSheet>
  </div>
</template>

<style scoped>
.chat-page{height:calc(var(--app-height) - var(--safe-top));padding-bottom:calc(var(--nav-space) + var(--safe-bottom));display:flex;flex-direction:column;overflow:hidden}.app-header{flex-shrink:0}.chat-content{flex:1;min-height:0;overflow-y:auto;overscroll-behavior:contain;scrollbar-width:thin}.welcome{padding:18px var(--gutter) 24px;display:flex;flex-direction:column;gap:22px}.welcome-kicker{display:flex;align-items:center;gap:8px}.sim-tag{font-size:10px;line-height:1.4;color:var(--muted);background:var(--surface);padding:4px 8px;border-radius:999px;letter-spacing:.02em}.city-control{display:flex;align-items:center;gap:6px;min-height:44px;font-size:13px;margin-left:-2px;padding:0 2px}.welcome h1{font-size:30px;font-weight:650;line-height:1.3;letter-spacing:-.045em;margin:10px 0 10px}.welcome-heading>p{font-size:13px}.welcome-sub{line-height:1.7;max-width:292px}.chat-demo-label{display:block;margin-top:5px;font-size:12px}.prompt-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.prompt-grid button{display:flex;flex-direction:column;align-items:flex-start;justify-content:flex-start;gap:9px;min-height:86px;background:#fff;border:1px solid var(--line);border-radius:16px;font-size:12px;padding:12px;text-align:left;white-space:normal}.prompt-icon{display:grid;place-items:center;width:30px;height:30px;border-radius:50%;background:var(--surface);flex-shrink:0}.prompt-copy{display:block;min-width:0}.prompt-copy strong{display:block;font-size:12px;font-weight:600;line-height:1.4}.prompt-copy small{display:block;font-size:10px;color:var(--muted);line-height:1.4;margin-top:2px}.home-discover>.between{margin-bottom:10px}.home-discover h2{font-size:16px}.home-previews{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.preview-photo{position:relative;height:134px;overflow:hidden;border-radius:18px}.preview-photo img{height:100%;width:100%}.preview-photo>span{position:absolute;bottom:8px;left:8px;background:#fffe;border-radius:4px;font-size:12px;padding:2px 6px}.preview-copy{padding:9px 2px 0}.preview-copy p{font-size:13px;font-weight:600}.preview-copy p span{display:block;color:var(--muted);font-size:12px;font-weight:400;margin-top:2px}.preview-copy strong{font-size:18px;display:block;margin-top:3px}.preview-copy small{font-size:12px;font-weight:400;margin-left:4px;color:var(--muted)}.conversation{padding:8px var(--gutter) 24px;gap:24px}.conversation-heading h1{font-size:18px;letter-spacing:0}.conversation-heading>span{white-space:nowrap}.message-list{gap:12px}.message{display:flex;flex-direction:column;align-items:flex-start;min-width:0}.message.user{align-items:flex-end}.bubble{white-space:pre-wrap;overflow-wrap:anywhere;font-size:14px;line-height:1.9;max-width:100%}.user .bubble{background:var(--surface);border-radius:18px 18px 4px 18px;padding:14px 17px;max-width:90%}.assistant-caption{display:flex;align-items:center;gap:7px;font-size:13px;margin-bottom:12px}.assistant-caption strong{font-weight:600}.message-actions{height:32px}.message-actions .icon-button{min-height:32px;height:32px;min-width:44px;color:var(--muted);padding:6px 10px}.quick-replies{border-top:1px solid var(--line);padding-top:12px}.conditions-row{display:flex;justify-content:space-between;align-items:center;width:100%;min-height:50px;margin-bottom:10px}.conditions-row>span{display:flex;gap:10px;align-items:center;font-size:14px}.choice-chips button{font-size:12px}.commute-choices{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}.workplace-choices{display:grid;gap:8px;margin-bottom:12px}.workplace-choices button{display:flex;flex-direction:column;align-items:flex-start;text-align:left;gap:4px;padding:10px 12px;border:1px solid var(--line);border-radius:10px;overflow-wrap:anywhere}.workplace-choices span{font-size:12px;color:var(--muted)}.closure-actions{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:12px}.closure-actions button{min-height:44px;padding:8px 14px;border:1px solid var(--line);border-radius:22px;font-size:13px}.commute-result{font-size:13px;margin:8px 0}.commute-result span{display:block;margin-top:3px}.recommendations{width:100%;min-width:0;border-top:1px solid var(--line);padding-top:18px}.recommendations h2{font-size:17px;max-width:75%}.recommendations>.primary{margin-top:12px}.recommendations>.text-action{width:100%;margin-top:8px}.recommendations .property-card+.property-card{border-top:1px solid var(--line)}.chat-footer{padding:8px var(--gutter) 0;flex-shrink:0;background:#fff;position:relative}.composer{padding:13px 12px 8px;border:1px solid var(--line);border-radius:18px;background:#fff;box-shadow:0 2px 12px rgba(0,0,0,.04)}.composer:focus-within{border-color:#aaa}.composer textarea{border:0;outline:none!important;width:100%;background:transparent;font-size:14px;line-height:1.75;min-height:28px;max-height:120px;display:block;padding:0 4px}.composer textarea::placeholder{color:#717171}.composer-tools{display:flex;align-items:center;gap:4px;margin-top:6px}.composer-tools>.icon-button{margin-left:-3px;color:var(--muted)}.composer-condition{min-height:44px;display:flex;align-items:center;gap:6px;color:var(--muted);font-size:12px;padding:8px}.send-button{width:44px;height:44px;background:var(--ink);color:#fff;border-radius:50%;display:grid;place-items:center;flex-shrink:0;margin-left:auto}.send-button:disabled{background:#e4e4e4;color:#666;opacity:1}.reference-preview{display:flex;align-items:center;gap:10px;padding-bottom:12px;margin-bottom:12px;border-bottom:1px solid var(--line)}.reference-preview img{width:44px;height:48px;border-radius:8px}.reference-preview>div{flex:1;min-width:0}.reference-preview strong{font-size:12px;display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.reference-preview p{font-size:12px;color:var(--muted)}.latest-message{position:static;margin:0 auto 8px;padding:8px 14px;min-height:44px;font-size:12px;display:flex;align-items:center;gap:6px;border:1px solid var(--line);border-radius:30px;background:#fff}.history-conversation{display:flex;align-items:center;text-align:left;gap:14px;padding:12px 0;min-height:68px}.history-conversation>span{flex:1;min-width:0}.history-conversation strong{display:block;font-size:14px;font-weight:500;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.history-conversation small{display:block;margin-top:6px}.empty-conversations{text-align:center;padding:24px 10px}.empty-conversations>svg{margin:0 auto 14px}.empty-conversations p{margin-top:8px}.keyboard-open .chat-page{padding-bottom:10px}.keyboard-open .welcome{padding-top:0}
@media(max-width:359px){.welcome{gap:20px}.welcome h1{font-size:27px}.home-previews{gap:12px}.preview-photo{height:120px}.prompt-grid button{gap:4px;font-size:12px}.prompt-grid button svg{width:14px}.conversation-heading h1{font-size:16px}.chat-footer{padding-top:6px}.composer-condition{font-size:12px}.composer textarea{font-size:13px}}
@media(max-height:720px){.welcome{gap:18px;padding-top:0}.welcome h1{font-size:28px;margin-top:0}.preview-photo{height:110px}.welcome-heading p{font-size:12px}}
.save-plan{display:flex;align-items:center;gap:10px;margin:2px 0 10px;flex-wrap:wrap}.save-plan .secondary{min-height:44px;font-size:13px;padding:0 16px}.save-plan .text-action{min-height:44px;display:flex;align-items:center}
.coverage-note{display:flex;align-items:flex-start;gap:7px;font-size:11px;line-height:1.65;color:var(--muted);background:var(--surface);padding:9px 11px;border-radius:9px;margin:8px 0}.coverage-note svg{flex-shrink:0;margin-top:2px}.save-plan{margin:10px 0 6px}
.adjust-summary{display:grid;gap:6px;font-size:13px}.adjust-summary p{display:flex;justify-content:space-between;gap:12px;margin:0}.adjust-summary span{color:var(--muted)}.adjust-field{display:grid;gap:8px;font-size:12px;margin-top:4px}.adjust-field input{min-height:46px;border:1px solid var(--line);border-radius:12px;padding:0 12px;font-size:17px;font-weight:600;background:#fff}

</style>
