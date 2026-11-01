import { Capacitor } from '@capacitor/core'
export type AMapInstance={getZoom:()=>number;setZoom:(value:number)=>void;setZoomAndCenter:(zoom:number,position:number[])=>void;destroy:()=>void}
export type AMapSDK={Map:new(host:HTMLElement,options:any)=>any;Marker:new(options:any)=>any}
const global=window as unknown as {AMap?:AMapSDK;_AMapSecurityConfig?:{serviceHost:string}}
let pending:Promise<AMapSDK>|undefined
export function loadAmap():Promise<AMapSDK>{
 if(global.AMap)return Promise.resolve(global.AMap)
 if(pending)return pending
 pending=new Promise<AMapSDK>((resolve,reject)=>{
  const key=import.meta.env.VITE_AMAP_WEB_KEY?.trim();if(!key){reject(Error('地图暂未配置'));return}
  const api=import.meta.env.VITE_API_BASE_URL?.trim();let origin=location.origin
  if(Capacitor.isNativePlatform()&&api){try{const candidate=new URL(api);if(candidate.protocol==='https:')origin=candidate.origin}catch{reject(Error('地图服务地址无效'));return}}
  global._AMapSecurityConfig={serviceHost:origin+'/_AMapService'}
  const script=document.createElement('script');let done=false
  const finish=(error?:Error)=>{if(done)return;done=true;clearTimeout(timer);script.onload=null;script.onerror=null;if(error||!global.AMap){script.remove();reject(error||Error('地图服务暂不可用'))}else resolve(global.AMap)}
  const timer=setTimeout(()=>finish(Error('地图加载超时')),15000)
  script.src='https://webapi.amap.com/maps?v=2.0&key='+encodeURIComponent(key);script.onload=()=>finish();script.onerror=()=>finish(Error('地图加载失败'));document.head.append(script)
 }).catch(error=>{pending=undefined;throw error})
 return pending
}
