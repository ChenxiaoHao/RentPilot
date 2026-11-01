<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import AppSheet from './AppSheet.vue'
import AppIcon from './AppIcon.vue'
import {memberApi,shanghaiDate,slotLabels,type Viewing,type ViewingSlot} from '../services/member'
import type {CatalogListing} from '../services/catalog-types'
import {catalogTitle} from '../services/catalog-photos'
import {useMemberStore} from '../stores/member'
const props=defineProps<{modelValue:boolean;home:CatalogListing;existing?:Viewing}>()
const emit=defineEmits<{'update:modelValue':[boolean];saved:[Viewing]}>()
const member=useMemberStore(),date=ref(shanghaiDate(1)),slot=ref<ViewingSlot>('AFTERNOON'),error=ref(''),busy=ref(false)
let owner=''
const open=computed({get:()=>props.modelValue,set:(v:boolean)=>emit('update:modelValue',v)})
watch(()=>props.modelValue,value=>{if(value){owner=member.token;date.value=props.existing?.date||shanghaiDate(1);slot.value=props.existing?.slot||'AFTERNOON';error.value=''}},{immediate:true})
watch(()=>member.token,()=>{open.value=false;busy.value=false;error.value='';owner=''})
async function submit(){const captured=owner;if(busy.value)return;if(!captured||captured!==member.token){open.value=false;return}busy.value=true;error.value='';try{const result=await member.privateCall(token=>{if(token!==captured)throw Error('账户已变化，请重新打开');return memberApi.plan(token,props.home.id,date.value,slot.value,props.existing?.id)});if(member.token!==captured)return;await member.refresh().catch(()=>{});if(member.token!==captured)return;emit('saved',result);open.value=false}catch(cause){if(member.token===captured)error.value=cause instanceof Error?cause.message:'保存失败，请重试'}finally{if(member.token===captured)busy.value=false}}
</script>
<template><AppSheet v-model="open" :title="existing?'修改看房时间':'安排看房'"><form id="viewing-form" class="rental-form" @submit.prevent="submit"><p class="viewing-for-home">{{ catalogTitle(home) }}</p><label class="form-field">看房日期<input v-model="date" type="date" aria-label="看房日期" required :min="shanghaiDate()" :max="shanghaiDate(60)" :disabled="busy"/></label><label class="form-field">看房时段<select v-model="slot" aria-label="看房时段" :disabled="busy"><option v-for="(label,key) in slotLabels" :key="key" :value="key">{{ label }}</option></select></label><p class="small muted">保存个人看房安排，不会联系房东或经纪人，也不代表预约已确认。</p><p v-if="error" class="form-error" role="alert">{{ error }}</p></form><template #footer><button form="viewing-form" type="submit" class="primary" :disabled="busy||!date">{{ busy?'正在保存…':'保存计划' }}<AppIcon name="check" :size="17"/></button></template></AppSheet></template>
<style scoped>.viewing-for-home{font-size:17px;font-weight:600;line-height:1.7}</style>
