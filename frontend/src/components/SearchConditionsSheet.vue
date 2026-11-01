<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import AppSheet from './AppSheet.vue'
import { fetchCatalogFilters } from '../services/catalog'
import type { CatalogFilters } from '../services/catalog-types'
import type { SearchConditions } from '../services/search-conditions'
import { buildConditions, validateBudget } from '../services/search-conditions'
const props=defineProps<{modelValue:boolean; conditions:SearchConditions}>()
const emit=defineEmits<{ 'update:modelValue':[boolean]; apply:[string] }>()
const filters=ref<CatalogFilters|null>(null), loading=ref(false), failed=ref(false), attempted=ref(false)
const towns=computed(()=>filters.value?.towns?.filter(t=>t.district===props.conditions.district) || [])
async function load(){loading.value=true;failed.value=false;try{filters.value=await fetchCatalogFilters()}catch{failed.value=true}finally{loading.value=false}}
watch(()=>props.modelValue,open=>{if(open&&!filters.value&&!loading.value) void load(); if(!open) attempted.value=false})
watch(()=>props.conditions.district,()=>{if(!towns.value.some(t=>t.value===props.conditions.town)) props.conditions.town=''})
watch(()=>props.conditions.rental,value=>{if(value!=='ENTIRE') props.conditions.rooms=''})
const budgetError=computed(()=>validateBudget(props.conditions.minRent,props.conditions.maxRent))
function apply(){attempted.value=true;if(!props.conditions.address.trim()||!props.conditions.mode||budgetError.value)return;emit('apply',buildConditions(props.conditions));emit('update:modelValue',false);attempted.value=false}
</script>
<template>
<AppSheet :model-value="modelValue" title="找房条件" @update:model-value="emit('update:modelValue',$event)">
 <div class="conditions-form">
  <section><h3>工作地点 <span class="city-hint">上海 · 区和街镇可选</span></h3><div class="fields"><label>区<select v-model="conditions.district" aria-label="工作地区"><option value="">不限</option><option v-for="d in filters?.districts" :key="d.value" :value="d.value">{{d.value}}</option></select></label><label>街道 / 镇<select v-model="conditions.town" aria-label="工作街镇" :disabled="!conditions.district"><option value="">不限</option><option v-for="t in towns" :key="t.value" :value="t.value">{{t.value}}</option></select></label></div><p v-if="loading" class="hint">正在加载区域目录…</p><p v-if="failed" class="hint">区域目录暂不可用，仍可填写地址。<button class="retry" @click="load">重试</button></p><label>具体地址<input v-model="conditions.address" aria-label="工作地点" placeholder="企业、园区或道路门牌" maxlength="100" /></label><p v-if="attempted&&!conditions.address.trim()" class="error">请填写工作地点</p></section>
  <section><div class="budget-heading"><h3>月租预算</h3><button class="clear-budget" @click="conditions.minRent='';conditions.maxRent=''">清空租金</button></div><div class="fields"><label>最低租金<input v-model.trim="conditions.minRent" inputmode="decimal" aria-label="最低租金" placeholder="不限" /></label><label>最高租金<input v-model.trim="conditions.maxRent" inputmode="decimal" aria-label="最高租金" placeholder="不限" /></label></div><p v-if="attempted&&budgetError" class="error">{{budgetError}}</p><div class="chips"><button v-for="range in [{label:'1000以内',min:'',max:'1000'},{label:'1000–3000',min:'1000',max:'3000'},{label:'3000–5000',min:'3000',max:'5000'}]" :key="range.label" type="button" :aria-pressed="conditions.minRent===range.min && conditions.maxRent===range.max" @click="conditions.minRent=range.min;conditions.maxRent=range.max">{{range.label}}</button></div></section>
  <section><h3>租赁方式</h3><div class="chips"><button v-for="option in [{label:'不限',value:'ANY'},{label:'整租',value:'ENTIRE'},{label:'合租',value:'SHARED'}] as const" :key="option.value" :aria-pressed="conditions.rental===option.value" @click="conditions.rental=option.value">{{option.label}}</button></div><label v-if="conditions.rental==='ENTIRE'">居室<select v-model="conditions.rooms" aria-label="居室"><option value="">不限</option><option v-for="n in 4" :key="n" :value="String(n)">{{n}}居</option></select></label></section>
  <section><h3>通勤方式</h3><div class="chips"><button v-for="option in [{label:'步行',value:'WALKING'},{label:'骑行',value:'CYCLING'},{label:'公共交通',value:'TRANSIT'},{label:'驾车',value:'DRIVING'}] as const" :key="option.value" :aria-pressed="conditions.mode===option.value" @click="conditions.mode=option.value">{{option.label}}</button></div><p v-if="attempted&&!conditions.mode" class="error">请选择通勤方式</p></section>
  <section><h3>单程通勤时间 · {{conditions.minutes}} 分钟</h3><div class="chips"><button v-for="n in [15,30,45,60]" :key="n" :aria-pressed="conditions.minutes===n" @click="conditions.minutes=n">{{n}} 分钟</button></div></section>
 </div><template #footer><button class="primary" @click="apply">填入对话</button></template>
</AppSheet>
</template>
<style scoped>
.conditions-form{display:grid;gap:16px;min-width:0}.conditions-form section{min-width:0}.conditions-form h3{font-size:15px;margin:0 0 8px}.city-hint{font-size:12px;font-weight:400;color:var(--muted);margin-left:6px}.budget-heading{display:flex;align-items:center;justify-content:space-between}.budget-heading h3{margin:0}.clear-budget{min-height:44px;padding:0 4px;font-size:12px;color:var(--muted)}.hint{font-size:12px;color:var(--muted);margin:0 0 6px}.fields{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.conditions-form label{display:block;min-width:0;font-size:13px;margin:6px 0}.conditions-form input,.conditions-form select{display:block;box-sizing:border-box;width:100%;min-width:0;height:44px;padding:0 10px;border:1px solid var(--line);border-radius:10px;background:white;color:var(--ink);font-size:14px}.chips{display:flex;flex-wrap:wrap;gap:8px}.chips button{min-height:44px;padding:8px 14px;border:1px solid var(--line);border-radius:24px;font-size:13px}.chips button[aria-pressed=true]{background:var(--ink);color:white;border-color:var(--ink)}.error{color:#a12b2b;font-size:13px}.retry{min-height:44px;text-decoration:underline;padding:0 8px}
</style>
