<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { explainRecommendation } from '../services/ai'
import type { Insight } from '../services/ai'
import { ApiError } from '../services/http'
const props=defineProps<{insight:Insight; reasons:string[]; listingId:string; searchToken?:string|null; active:boolean; contextKey:string}>()
const open=ref(false), loading=ref(false), error=ref(''), summary=ref(''), expired=ref(false)
let controller:AbortController|null=null, generation=0
function cancel(){generation++;controller?.abort();controller=null;loading.value=false}
function reset(){cancel();summary.value='';error.value='';expired.value=false;open.value=false}
watch(()=>[props.contextKey,props.listingId,props.searchToken,props.insight,props.active],reset)
onBeforeUnmount(cancel)
async function explain(){
 if(!props.active || !props.searchToken || loading.value)return
 cancel();error.value='';const current=++generation;const request=new AbortController();controller=request;loading.value=true
 try {
  const result=await explainRecommendation(props.searchToken,props.listingId,request.signal)
  if(current!==generation || !props.active)return
  const evidence=props.insight.evidence
  if(!result.evidenceIds.every(id=>evidence.some(e=>e.id===id)) || !result.evidenceIds.some(id=>evidence.some(e=>e.id===id && e.status==='MATCHED'))) throw new Error('invalid evidence')
  summary.value=result.summary
 } catch (e) {if(current===generation && !request.signal.aborted){expired.value=e instanceof ApiError && e.code==='AI_SEARCH_EXPIRED';error.value=expired.value?'结果已过期，请重新查找。':'解读暂不可用，请重试。'}}
 finally {if(current===generation){loading.value=false;controller=null}}
}
</script>
<template>
 <div class="detail-wrap">
  <p v-if="reasons.length" class="reasons">{{ reasons.slice(0,3).join(' · ') }}</p>
  <button class="evidence-toggle" type="button" :aria-expanded="open" @click="open=!open">推荐依据与费用 <span aria-hidden="true">{{ open ? '−' : '+' }}</span></button>
  <div v-if="open" class="details">
   <section><h3>推荐依据</h3><ul><li v-for="item in insight.evidence" :key="item.id"><span>{{ item.label }}</span><p>{{ item.text }} <small>{{ item.status === 'UNVERIFIED' ? '待确认' : '已匹配' }} · {{ item.source === 'AMAP' ? '高德估算' : '房源资料' }}</small></p></li></ul></section>
   <section><h3>费用</h3><dl><div><dt>月租</dt><dd>{{ insight.costs.monthlyRent }} 元/月</dd></div><div><dt>物业费</dt><dd>{{ insight.costs.propertyFee === null ? '待确认' : `${insight.costs.propertyFee} 元（周期待确认）` }}</dd></div><div><dt>押金（一次性）</dt><dd>{{ insight.costs.deposit }} 元</dd></div><div><dt>付款方式</dt><dd>{{ insight.costs.paymentMethod || '待确认' }}</dd></div><div><dt>首期租金 + 押金</dt><dd>{{ insight.costs.knownInitialPayment === null ? '待确认' : `${insight.costs.knownInitialPayment} 元` }}</dd></div></dl><p class="note">{{ insight.costs.excludedItems.length ? `未包含：${insight.costs.excludedItems.join('、')}` : '' }}</p></section>
   <section v-if="active && searchToken" class="explanation"><h3>AI 解读</h3><p v-if="summary">{{ summary }}</p><p v-if="error" role="alert">{{ error }}</p><p v-if="loading" role="status">正在生成解读…</p><button v-if="loading" type="button" @click="cancel">取消</button><button v-else-if="!summary && !expired" type="button" @click="explain">{{ error ? '重试 AI 解读' : 'AI 解读' }}</button></section>
  </div>
 </div>
</template>
<style scoped>
.detail-wrap{margin:6px 0 20px;min-width:0}.reasons{font-size:12px;line-height:1.7;color:var(--muted);overflow-wrap:anywhere}.evidence-toggle{width:100%;min-height:44px;display:flex;justify-content:space-between;align-items:center;text-align:left;border-bottom:1px solid var(--line);font-size:13px;font-weight:600}.evidence-toggle span{font-size:20px}.details{display:grid;gap:18px;padding:16px 0;overflow-wrap:anywhere}.details h3{font-size:13px;margin:0 0 9px}.details ul{list-style:none;margin:0;padding:0;display:grid;gap:10px}.details li span,.details dt{font-size:12px;color:var(--muted)}.details li p,.details dd,.explanation p{font-size:13px;line-height:1.6;margin:2px 0 0}.details small{display:block;font-size:11px;color:var(--muted)}dl{margin:0;display:grid;gap:8px}dl div{display:flex;justify-content:space-between;gap:12px}dd{text-align:right}.note{font-size:11px;color:var(--muted);margin-top:8px}.explanation button{min-height:44px;padding:0 16px;border:1px solid var(--line);border-radius:24px;font-size:13px;margin-top:8px}
</style>

