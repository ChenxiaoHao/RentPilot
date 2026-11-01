import { apiRequest } from './http'
export interface Message { id:string; senderId:string; content:string; createdAt:string }
export interface Conversation { id:string; listingId:string; tenantId:string; ownerId:string; ownerNickname:string; tenantNickname:string; messages?:Message[] }
export interface DemoOwner { claimed:boolean; ownerId:string|null; ownerNickname:string|null; isMine:boolean }
export const contactApi={
 owner:(token:string,id:string)=>apiRequest<DemoOwner>(`/demo/listings/${encodeURIComponent(id)}/owner`,{token}),
 claim:(token:string,id:string)=>apiRequest<DemoOwner>(`/demo/listings/${encodeURIComponent(id)}/claim`,{method:'POST',token}),
 start:(token:string,listingId:string)=>apiRequest<Conversation>('/conversations',{method:'POST',token,data:{listingId}}),
 list:(token:string)=>apiRequest<Conversation[]>('/conversations',{token}),
 get:(token:string,id:string)=>apiRequest<Conversation>(`/conversations/${encodeURIComponent(id)}`,{token}),
 send:(token:string,id:string,content:string)=>apiRequest<Message>(`/conversations/${encodeURIComponent(id)}/messages`,{method:'POST',token,data:{content}}),
}
