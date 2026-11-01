import { apiRequest } from './http'
import { isListing } from './catalog'
import type { CatalogListing } from './catalog-types'

export interface MemberProfile { id:string;username:string;nickname:string;createdAt:string;favoritesCount:number;historyCount:number;plannedCount:number }
export interface Session { token:string;expiresAt:string;profile:MemberProfile }
export interface OwnedListing {listing:CatalogListing;recordedAt:string}
export type ViewingStatus='PLANNED'|'CANCELLED'|'COMPLETED'
export type ViewingSlot='MORNING'|'AFTERNOON'|'EVENING'
export interface Viewing {id:string;listing:CatalogListing;date:string;slot:ViewingSlot;status:ViewingStatus;updatedAt:string}
export interface MemberPage<T>{items:T[];page:number;size:number;total:number;totalPages:number}
export const slotLabels:Record<ViewingSlot,string>={MORNING:'上午 10:00',AFTERNOON:'下午 14:00',EVENING:'晚上 18:30'}
export const statusLabels:Record<ViewingStatus,string>={PLANNED:'待看',CANCELLED:'已取消',COMPLETED:'已看'}
const validProfile=(p:MemberProfile)=>p && typeof p.id==='string' && typeof p.username==='string' && typeof p.nickname==='string' && [p.favoritesCount,p.historyCount,p.plannedCount].every(n=>Number.isInteger(n)&&n>=0)
function validPage<T>(data:MemberPage<T>,validate:(item:T)=>boolean){if(!data || !Array.isArray(data.items)||!data.items.every(validate)||![data.page,data.total,data.totalPages].every(n=>Number.isInteger(n)&&n>=0)||!Number.isInteger(data.size)||data.size<1||data.totalPages!==Math.ceil(data.total/data.size))throw Error('个人数据格式异常，请重试');return data}
export const memberApi={
  async authenticate(mode:'login'|'register',username:string,password:string,nickname?:string){const s=await apiRequest<Session>(`/auth/${mode}`,{method:'POST',data:{username,password,nickname}});if(!s||!/^[A-Za-z0-9_-]{43}$/.test(s.token)||!validProfile(s.profile)||!Number.isFinite(Date.parse(s.expiresAt)))throw Error('登录响应异常，请重试');return s},
  async profile(token:string){const p=await apiRequest<MemberProfile>('/me',{token});if(!validProfile(p))throw Error('账户信息格式异常');return p},
  async ids(token:string){const ids=await apiRequest<string[]>('/me/favorite-ids',{token});if(!Array.isArray(ids)||!ids.every(id=>/^SH-MOCK-\d{6}$/.test(id)))throw Error('收藏信息格式异常');return ids},
  logout:(token:string)=>apiRequest<void>('/auth/logout',{method:'POST',token}),
  rename:(token:string,nickname:string)=>apiRequest<MemberProfile>('/me',{method:'PATCH',token,data:{nickname}}),
  favorite:(token:string,id:string,saved:boolean)=>apiRequest<void>(`/me/favorites/${encodeURIComponent(id)}`,{method:saved?'PUT':'DELETE',token}),
  history:(token:string,id:string)=>apiRequest<void>(`/me/history/${encodeURIComponent(id)}`,{method:'PUT',token}),
  clearHistory:(token:string)=>apiRequest<void>('/me/history',{method:'DELETE',token}),
  async collection(token:string,kind:'favorites'|'history',page=0){return validPage(await apiRequest<MemberPage<OwnedListing>>(`/me/${kind}?page=${page}&size=20`,{token}),entry=>!!entry&&isListing(entry.listing)&&typeof entry.recordedAt==='string')},
  async viewings(token:string,status:ViewingStatus,page=0){return validPage(await apiRequest<MemberPage<Viewing>>(`/me/viewings?status=${status}&page=${page}&size=20`,{token}),v=>!!v&&typeof v.id==='string'&&isListing(v.listing)&&v.slot in slotLabels&&v.status in statusLabels&&/^\d{4}-\d{2}-\d{2}$/.test(v.date))},
  plan:(token:string,listingId:string,date:string,slot:ViewingSlot,id?:string)=>apiRequest<Viewing>(id?`/me/viewings/${id}`:'/me/viewings',{method:id?'PUT':'POST',token,data:{listingId,date,slot}}),
  status:(token:string,id:string,status:ViewingStatus)=>apiRequest<Viewing>(`/me/viewings/${id}/status`,{method:'PATCH',token,data:{status}}),
}
export function shanghaiDate(offset=0){const parts=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit'}).formatToParts(new Date());const get=(type:string)=>parts.find(p=>p.type===type)!.value;const date=new Date(`${get('year')}-${get('month')}-${get('day')}T12:00:00Z`);date.setUTCDate(date.getUTCDate()+offset);return date.toISOString().slice(0,10)}
export function safeRedirect(value:unknown){return typeof value==='string' && /^\/(chat|find-map|listings|listing\/SH-MOCK-\d{6}|favorites|viewings|profile|recent|messages(?:\/[A-Za-z0-9-]{1,64})?)(\?[^#]*)?$/.test(value)?value:'/chat'}
