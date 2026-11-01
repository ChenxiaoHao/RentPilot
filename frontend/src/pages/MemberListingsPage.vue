<script setup lang="ts">
import {computed,onBeforeUnmount,ref,watch} from 'vue'
import {useRoute} from 'vue-router'
import {useMemberStore} from '../stores/member'
import {memberApi,type OwnedListing} from '../services/member'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import AppSheet from '../components/AppSheet.vue'
import MemberGate from '../components/MemberGate.vue'
import CatalogCard from '../components/CatalogCard.vue'
const route=useRoute(),member=useMemberStore(),entries=ref<OwnedListing[]>([]),total=ref(0),page=ref(-1),busy=ref(false),error=ref(''),clearOpen=ref(false),clearing=ref(false)
const kind=computed(()=>route.path==='/recent'?'history':'favorites'),title=computed(()=>kind.value==='history'?'最近浏览':'我的收藏')
let request=0,clearOwner=''
function openClear(){clearOwner=member.token;error.value='';clearOpen.value=true}
async function load(append=false){const id=++request;busy.value=true;error.value='';if(!append){entries.value=[];total.value=0;page.value=-1}try{await member.restore();if(!member.loggedIn)return;const data=await member.privateCall(token=>memberApi.collection(token,kind.value,append?page.value+1:0));if(id!==request)return;entries.value=append?[...entries.value,...data.items]:data.items;page.value=data.page;total.value=data.total}catch(cause){if(id===request)error.value=cause instanceof Error?cause.message:'加载失败，请重试'}finally{if(id===request)busy.value=false}}
async function clearHistory(){const owner=clearOwner;if(!owner||owner!==member.token){clearOpen.value=false;return}clearing.value=true;error.value='';try{await member.privateCall(token=>{if(token!==owner)throw Error('账户已变化，请重新打开');return memberApi.clearHistory(token)});if(member.token!==owner)return;clearOpen.value=false;await member.refresh();await load()}catch(cause){if(member.token===owner)error.value=cause instanceof Error?cause.message:'清除失败，请重试'}finally{if(member.token===owner)clearing.value=false}}
watch(()=>[member.token,kind.value],()=>{clearOpen.value=false;clearing.value=false;clearOwner='';void load()},{immediate:true})
watch(()=>member.favoriteIds.join(','),()=>{if(kind.value==='favorites'&&entries.value.length)void load()})
onBeforeUnmount(()=>++request)
</script>
<template><AppHeader :title="title" :back="kind==='history'" hide-avatar><button v-if="kind==='history'&&total>0" class="text-action" @click="openClear">清空</button></AppHeader><main class="page member-list-page"><div class="rental-page-title"><h1>{{ title }}</h1><span v-if="member.loggedIn&&!busy&&!error" class="small muted">{{ total }} 套</span></div><MemberGate v-if="!member.loggedIn&&!member.loading" :title="kind==='history'?'登录后查看浏览记录':'登录后查看收藏'"/><p v-else-if="busy&&!entries.length" class="rental-loading" role="status">正在加载…</p><section v-else-if="error&&!entries.length" class="rental-state" role="alert"><h2>暂时加载不了</h2><p>{{ error }}</p><button class="secondary" @click="load()">重试</button></section><section v-else-if="!entries.length" class="rental-state"><AppIcon :name="kind==='history'?'history':'heart'" :size="32"/><h2>{{ kind==='history'?'还没有浏览记录':'还没有收藏的房源' }}</h2><p>{{ kind==='history'?'登录后打开房源详情，会自动保留最近浏览记录。':'遇到喜欢的房子，点一下爱心。' }}</p><RouterLink to="/listings" class="primary">去看看房源<AppIcon name="arrow-right"/></RouterLink></section><section v-else><CatalogCard v-for="entry in entries" :key="entry.listing.id" :home="entry.listing" :recorded-at="entry.recordedAt"/><p v-if="error" class="form-error" role="alert">{{ error }}</p><button v-if="entries.length<total" class="secondary rental-load-more" :disabled="busy" @click="load(true)">{{ busy?'正在加载…':'加载更多' }}</button></section></main><AppSheet v-model="clearOpen" title="清空浏览记录"><p>仅清空当前账户的浏览记录，收藏和看房计划会保留。</p><p v-if="error" class="form-error" role="alert">{{ error }}</p><template #footer><button class="primary" :disabled="clearing" @click="clearHistory">{{ clearing?'正在清空…':'确认清空' }}</button></template></AppSheet></template>
