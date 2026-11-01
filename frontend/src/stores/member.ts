import { computed,ref } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/http'
import { memberApi,type MemberProfile,type Session } from '../services/member'

export const AUTH_STORAGE='rentpilot-account-v1'
function readSession():Session|null{try{const value=JSON.parse(localStorage.getItem(AUTH_STORAGE)||'null');return value&&typeof value.token==='string'&&/^[A-Za-z0-9_-]{43}$/.test(value.token)&&value.profile&&typeof value.profile.id==='string'?value:null}catch{return null}}
export const useMemberStore=defineStore('member',()=>{
  const initial=readSession(),token=ref(initial?.token||''),profile=ref<MemberProfile|null>(initial?.profile||null),expiresAt=ref(initial?.expiresAt||'')
  const favoriteIds=ref<string[]>([]),favoritesReady=ref(!token.value),loading=ref(false),error=ref(''),busyIds=ref(new Set<string>())
  let generation=0,restoring:Promise<void>|undefined,profileRequest=0
  const loggedIn=computed(()=>!!token.value&&!!profile.value)
  function persist(){try{if(token.value)localStorage.setItem(AUTH_STORAGE,JSON.stringify({token:token.value,profile:profile.value,expiresAt:expiresAt.value}));else localStorage.removeItem(AUTH_STORAGE)}catch{error.value='登录信息无法保存到本机，关闭后可能需要重新登录'}}
  function clear(){++generation;++profileRequest;loading.value=false;restoring=undefined;token.value='';profile.value=null;expiresAt.value='';favoriteIds.value=[];favoritesReady.value=true;busyIds.value=new Set();error.value='';persist()}
  function rejectExpired(cause:unknown,captured:string):never{if(cause instanceof ApiError&&cause.status===401&&captured===token.value){clear();error.value='登录已失效，请重新登录'}throw cause}
  async function privateCall<T>(action:(value:string)=>Promise<T>){const captured=token.value;if(!captured)throw new ApiError('请先登录',401);try{return await action(captured)}catch(cause){return rejectExpired(cause,captured)}}
  async function refresh(){const current=token.value,id=++profileRequest;if(!current)return;try{const p=await memberApi.profile(current);if(current===token.value&&id===profileRequest){profile.value=p;persist()}}catch(cause){rejectExpired(cause,current)}}
  async function restore(){if(restoring)return restoring;if(!token.value || (favoritesReady.value&&profile.value&&!error.value))return;const current=token.value,version=generation;loading.value=true;error.value='';restoring=(async()=>{try{const [p,ids]=await Promise.all([memberApi.profile(current),memberApi.ids(current)]);if(current===token.value&&version===generation){profile.value=p;favoriteIds.value=ids;favoritesReady.value=true;persist()}}catch(cause){if(current===token.value&&version===generation){if(cause instanceof ApiError&&cause.status===401){clear();error.value='登录已失效，请重新登录'}else error.value=cause instanceof Error?cause.message:'账户暂不可用'}}finally{if(current===token.value&&version===generation){loading.value=false;restoring=undefined}}})();return restoring}
  async function authenticate(mode:'login'|'register',username:string,password:string,nickname?:string){const session=await memberApi.authenticate(mode,username,password,nickname);clear();token.value=session.token;profile.value=session.profile;expiresAt.value=session.expiresAt;favoritesReady.value=false;persist();await restore()}
  async function toggleFavorite(id:string){if(busyIds.value.has(id))return;if(token.value&&!favoritesReady.value)throw new ApiError('收藏状态正在加载，请稍候');const current=token.value,version=generation,wasSaved=favoriteIds.value.includes(id);busyIds.value.add(id);try{await privateCall(t=>memberApi.favorite(t,id,!wasSaved));if(current===token.value&&version===generation){favoriteIds.value=wasSaved?favoriteIds.value.filter(v=>v!==id):[...favoriteIds.value,id];await refresh().catch(()=>{})}}finally{if(version===generation)busyIds.value.delete(id)}}
  async function recordHistory(id:string){if(!token.value)return;await privateCall(t=>memberApi.history(t,id));await refresh()}
  async function rename(nickname:string){const current=token.value;const p=await privateCall(t=>memberApi.rename(t,nickname));if(current===token.value){profile.value=p;persist()}}
  async function logout(){const current=token.value;await privateCall(t=>memberApi.logout(t));if(current===token.value)clear()}
  function storageChanged(event:StorageEvent){if(event.key!==AUTH_STORAGE)return;const next=readSession();if((!next&&!token.value)||(next?.token===token.value&&next?.profile.id===profile.value?.id))return;++generation;++profileRequest;loading.value=false;error.value='';token.value=next?.token||'';profile.value=next?.profile||null;expiresAt.value=next?.expiresAt||'';favoriteIds.value=[];favoritesReady.value=!next;busyIds.value=new Set();restoring=undefined;void restore()}
  return{token,profile,loggedIn,favoriteIds,favoritesReady,busyIds,loading,error,restore,refresh,authenticate,toggleFavorite,recordHistory,rename,logout,privateCall,clear,storageChanged}
})
