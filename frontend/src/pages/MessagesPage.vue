<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {useRoute} from 'vue-router'
import { useMemberStore } from '../stores/member'
import { contactApi, type Conversation } from '../services/contact'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import AppSheet from '../components/AppSheet.vue'
import MemberGate from '../components/MemberGate.vue'
const route=useRoute(),member=useMemberStore(), items=ref<Conversation[]>([]), loading=ref(false), error=ref(''), listingId=ref(''), claimResult=ref(''), claiming=ref(false)
const search=ref('')
const claimOpen=ref(false)
const visible=computed(()=>{const q=search.value.trim().toLowerCase();return q?items.value.filter(item=>`${member.profile?.id===item.ownerId?item.tenantNickname:item.ownerNickname} ${item.listingId}`.toLowerCase().includes(q)):items.value})
let generation=0, loadVersion=0, timer:ReturnType<typeof setInterval>|undefined
function seedClaim(){const id=route.query.claim;listingId.value=typeof id==='string'&&/^SH-MOCK-\d{6}$/.test(id)?id:''}
onMounted(()=>{seedClaim();timer=setInterval(()=>{if(document.visibilityState==='visible')void load()},15000)})
onUnmounted(()=>{if(timer)clearInterval(timer)})
async function claim(){const token=member.token, version=generation, id=listingId.value.trim();if(!token||!id||claiming.value)return;claiming.value=true;claimResult.value='';try{const result=await member.privateCall(t=>contactApi.claim(t,id));if(version===generation&&token===member.token)claimResult.value=result.isMine?'已认领该模拟房源，可用另一个账号发起联系。':'该模拟房源已由其他账号认领。'}catch(e){if(version===generation&&token===member.token)claimResult.value=e instanceof Error?e.message:'认领失败'}finally{if(version===generation&&token===member.token)claiming.value=false}}
async function load(){const token=member.token, version=generation, request=++loadVersion;if(!member.loggedIn)return;loading.value=true;error.value='';try{const result=await member.privateCall(contactApi.list);if(version===generation&&token===member.token&&request===loadVersion)items.value=result}catch(e){if(version===generation&&token===member.token&&request===loadVersion)error.value=e instanceof Error?e.message:'加载失败'}finally{if(version===generation&&token===member.token&&request===loadVersion)loading.value=false}}
watch(()=>member.token,()=>{generation++;loadVersion++;items.value=[];search.value='';seedClaim();claimOpen.value=!!member.token&&!!route.query.claim;claimResult.value='';error.value='';loading.value=false;claiming.value=false;void load()},{immediate:true,flush:'sync'})
watch(()=>route.query.claim,()=>{seedClaim();if(member.loggedIn&&route.query.claim)claimOpen.value=true})
</script>
<template>
  <AppHeader hide-avatar>
    <button v-if="member.loggedIn" class="icon-button" type="button" :disabled="loading" aria-label="刷新对话" @click="load"><AppIcon name="refresh" :size="19"/></button>
    <button v-if="member.loggedIn" class="icon-button more-button" type="button" aria-label="演示工具" @click="claimOpen=true"><span aria-hidden="true">···</span></button>
  </AppHeader>
  <main class="page inbox">
    <MemberGate v-if="!member.loggedIn" title="登录后查看消息" description="与演示发布方的对话会显示在这里。"/>
    <template v-else>
      <div class="inbox-intro"><p>租房沟通</p><h2>关于房子的对话，<br/>从这里开始。</h2></div>
      <label v-if="items.length || search" class="inbox-search"><AppIcon name="search" :size="18"/><input v-model="search" type="search" aria-label="搜索对话" placeholder="搜索联系人或房源编号"/><button v-if="search" type="button" aria-label="清空搜索" @click="search=''">×</button></label>
      <div v-if="error" role="alert" class="load-error"><span>{{ error }}</span><button @click="load">重试</button></div>
      <div v-if="loading&&!items.length" class="loading" role="status">正在加载对话…</div>
      <div v-else-if="!error&&!items.length" class="empty">
        <div class="empty-art" aria-hidden="true"><svg viewBox="0 0 160 118" fill="none"><path d="M35 58 80 22l45 36" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/><path d="M45 52v35a6 6 0 0 0 6 6h58a6 6 0 0 0 6-6V52" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"/><path d="M66 93V70h28v23" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"/><path d="M94 19h30a10 10 0 0 1 10 10v12a10 10 0 0 1-10 10h-8l-8 7v-7H94a10 10 0 0 1-10-10V29a10 10 0 0 1 10-10Z" fill="#fff" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round"/><circle cx="100" cy="35" r="2" fill="currentColor"/><circle cx="109" cy="35" r="2" fill="currentColor"/><circle cx="118" cy="35" r="2" fill="currentColor"/></svg></div>
        <h3>还没有对话</h3><p>遇到喜欢的房源，就和对方聊聊。</p><RouterLink class="browse" to="/listings">去找房<AppIcon name="arrow-right" :size="17"/></RouterLink>
      </div>
      <div v-else-if="!visible.length&&!error" class="no-results"><strong>没有找到对话</strong><p>试试其他联系人或房源编号。</p><button @click="search=''">清除搜索</button></div>
      <section v-else-if="items.length" class="conversations" aria-label="对话列表"><div class="list-heading"><h3>最近对话</h3></div><RouterLink v-for="item in visible" :key="item.id" :to="`/messages/${item.id}`" class="conversation"><div class="avatar" aria-hidden="true">{{ (member.profile?.id===item.ownerId?item.tenantNickname:item.ownerNickname)?.slice(0,1) || '?' }}</div><div class="conversation-copy"><strong>{{ member.profile?.id===item.ownerId?item.tenantNickname:item.ownerNickname }}</strong><p>{{ item.listingId }} <span>·</span> 模拟房源</p></div><AppIcon name="chevron-right" :size="18"/></RouterLink></section>
    </template>
  </main>
  <AppSheet v-model="claimOpen" title="演示发布方"><form id="claim-form" class="claim" @submit.prevent="claim"><p>认领模拟房源后，其他演示账号就可以发起联系。</p><label for="claim-id">房源编号</label><input id="claim-id" v-model="listingId" placeholder="例如 SH-MOCK-000001" required/><p class="claim-note">仅用于演示，不代表真实房东身份。</p><p v-if="claimResult" role="status">{{ claimResult }}</p></form><template #footer><button form="claim-form" class="primary" :disabled="claiming">{{ claiming?'认领中…':'认领模拟房源' }}</button></template></AppSheet>
</template>
<style scoped>
.inbox{padding-top:12px}.more-button span{font-size:24px;line-height:1;letter-spacing:1px}.inbox-intro{padding:16px 0 28px;border-bottom:1px solid var(--line)}.inbox-intro>p{font-size:12px;color:var(--muted);margin-bottom:13px}.inbox-intro h2{font-size:23px;font-weight:600;line-height:1.48;letter-spacing:-.035em}.inbox-search{display:flex;align-items:center;gap:10px;background:var(--surface);border-radius:13px;margin:22px 0 0;padding:0 14px;min-height:48px;color:#777}.inbox-search input{flex:1;min-width:0;background:transparent;border:0;outline:none;font-size:14px;min-height:48px;color:var(--ink)}.inbox-search button{min-width:36px;min-height:44px;font-size:22px}.empty{display:flex;align-items:center;flex-direction:column;text-align:center;padding:32px 10px 28px}.empty-art{display:grid;place-items:center;width:160px;height:132px;border-radius:26px;background:#f6f6f6;color:#333;margin-bottom:22px}.empty-art svg{width:132px;height:98px}.empty h3{font-size:17px;font-weight:600}.empty p{font-size:13px;color:var(--muted);line-height:1.7;margin:8px 0 21px}.browse{display:inline-flex;align-items:center;justify-content:center;gap:22px;background:var(--ink);color:#fff;border-radius:999px;min-height:48px;padding:0 24px;font-size:14px;font-weight:600}.conversations{padding-top:22px}.list-heading h3{font-size:14px;font-weight:600}.conversation{display:flex;align-items:center;gap:14px;min-height:82px;border-bottom:1px solid var(--line)}.avatar{width:48px;height:48px;display:grid;place-items:center;flex:none;background:#f1f1f1;border-radius:50%;font-size:17px;font-weight:600}.conversation-copy{flex:1;min-width:0}.conversation-copy strong{display:block;font-size:15px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.conversation-copy p{font-size:12px;color:var(--muted);margin-top:7px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.conversation-copy p span{padding:0 3px}.conversation>svg{color:#888;flex:none}.conversation:focus-visible,.browse:focus-visible{outline:2px solid #222;outline-offset:3px}.loading,.no-results,.load-error{font-size:13px;color:var(--muted);padding:28px 0}.no-results strong{display:block;color:var(--ink);font-size:15px}.no-results p{margin:8px 0 12px}.no-results button,.load-error button{text-decoration:underline;min-height:44px}.load-error{display:flex;justify-content:space-between;align-items:center;gap:12px}.claim{display:flex;flex-direction:column;gap:12px}.claim p{font-size:13px;line-height:1.7;color:var(--muted)}.claim label{font-size:13px;font-weight:600}.claim input{width:100%;min-height:48px;border:1px solid var(--line);border-radius:12px;padding:12px;font-size:14px}.claim .claim-note{font-size:12px}@media(max-height:700px){.inbox-intro{padding-bottom:18px}.empty{padding-top:22px}.empty-art{height:105px;margin-bottom:15px}.empty p{margin-bottom:14px}}@media(prefers-reduced-motion:reduce){*{scroll-behavior:auto}}
</style>
