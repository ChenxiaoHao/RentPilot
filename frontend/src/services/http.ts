import { Capacitor, CapacitorHttp } from '@capacitor/core'

const configured = import.meta.env.VITE_API_BASE_URL?.trim()
const base = (configured || (Capacitor.isNativePlatform() ? 'http://10.0.2.2:8080/api' : '/api')).replace(/\/+$/, '')
export class ApiError extends Error {
  constructor(message: string, public status?: number, public code?: string) { super(message); this.name = 'ApiError' }
}
export interface ApiOptions { method?: 'GET'|'POST'|'PUT'|'PATCH'|'DELETE'; data?: unknown; token?: string; signal?: AbortSignal; timeout?: number }
export async function apiRequest<T>(path: string, options: ApiOptions = {}): Promise<T> {
  const { signal, token, data: body, method = 'GET', timeout = 12000 } = options
  const controller = new AbortController(), abort = () => controller.abort()
  if (signal?.aborted) controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  const timer = setTimeout(abort, timeout)
  const headers: Record<string,string> = { Accept: 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  try {
    let status: number, data: unknown
    if (Capacitor.isNativePlatform()) {
      if (controller.signal.aborted) throw new DOMException('Aborted','AbortError')
      let cancel: () => void = () => {}
      const cancelled = new Promise<never>((_, reject) => { cancel = () => reject(new DOMException('Aborted','AbortError')); controller.signal.addEventListener('abort',cancel,{once:true}) })
      const nativeOptions = { url: base+path, headers, responseType: 'json' as const, connectTimeout:10000, readTimeout:timeout }
      const response = await Promise.race([method === 'GET' ? CapacitorHttp.get(nativeOptions) : CapacitorHttp.request({...nativeOptions,method,data:body}),cancelled]).finally(()=>controller.signal.removeEventListener('abort',cancel))
      if(controller.signal.aborted)throw new DOMException('Aborted','AbortError')
      status=response.status;data=response.data
    } else {
      const response=await fetch(base+path,{method,headers,signal:controller.signal,body:body===undefined?undefined:JSON.stringify(body)})
      status=response.status
      if(status!==204){const text=await response.text();try{data=JSON.parse(text)}catch{throw new ApiError('服务响应格式异常，请稍后重试',status)}}
    }
    if(status<200 || status>=300){
      const error=data && typeof data==='object'?data as Record<string,unknown>:{}
      const message=typeof error.message==='string' && error.message.length<200?error.message:status===401?'请重新登录':status===404?'未找到这条记录':'服务暂不可用，请稍后重试'
      throw new ApiError(message,status,typeof error.code==='string'?error.code:undefined)
    }
    if(status===204)return undefined as T
    if(!data || typeof data!=='object')throw new ApiError('服务响应格式异常，请稍后重试')
    return data as T
  } catch(error) {
    if(signal?.aborted)throw new DOMException('Aborted','AbortError')
    if(error instanceof ApiError)throw error
    throw new ApiError(controller.signal.aborted?'加载超时，请重试':'无法连接服务，请检查网络或稍后重试')
  } finally {clearTimeout(timer);signal?.removeEventListener('abort',abort)}
}
