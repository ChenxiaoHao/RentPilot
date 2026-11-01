import {apiRequest,ApiError} from './http'
import type {CatalogQuery} from './catalog-types'

export interface MapBounds {minLongitude:number;minLatitude:number;maxLongitude:number;maxLatitude:number}
export interface MapListing {
  id:string;longitude:number;latitude:number;name:string;district:string;town:string;community:string;address:string
  rentalType:'ENTIRE'|'SHARED';layout:string;bedrooms:number;rent:number;area:number;nearestMetro:string
  metroLines:string[];metroWalkMinutes:number|null;dataKind:'SIMULATED';coordinateSystem:'GCJ-02';coordinateScope:'COMMUNITY'
}
export interface MapCatalog {bounds:MapBounds;items:MapListing[];total:number;returned:number;limit:number;truncated:boolean;dataKind:'SIMULATED';coordinateSystem:'GCJ-02';coordinateScope:'COMMUNITY';source:{id:string;rowCount:number;dataKind:'SIMULATED'}}
const record=(v:unknown):v is Record<string,unknown>=>!!v&&typeof v==='object'&&!Array.isArray(v)
const finite=(v:unknown):v is number=>typeof v==='number'&&Number.isFinite(v)
function marker(v:unknown):v is MapListing {
  if(!record(v)||typeof v.id!=='string'||!/^SH-MOCK-\d{6}$/.test(v.id))return false
  if(!finite(v.longitude)||v.longitude < -180||v.longitude > 180||!finite(v.latitude)||v.latitude < -90||v.latitude > 90)return false
  if(!['name','district','town','community','address','layout','nearestMetro'].every(k=>typeof v[k]==='string'))return false
  return ['ENTIRE','SHARED'].includes(String(v.rentalType))&&Number.isInteger(v.bedrooms)&&finite(v.rent)&&v.rent>0&&finite(v.area)&&v.area>0&&Array.isArray(v.metroLines)&&v.metroLines.every(x=>typeof x==='string')&&(v.metroWalkMinutes===null||finite(v.metroWalkMinutes)&&v.metroWalkMinutes>=0)&&v.dataKind==='SIMULATED'&&v.coordinateSystem==='GCJ-02'&&v.coordinateScope==='COMMUNITY'
}
export async function fetchMapListings(bounds:MapBounds,filters:CatalogQuery={},limit=6,signal?:AbortSignal,center?:{longitude:number;latitude:number}):Promise<MapCatalog>{
  if(!Object.values(bounds).every(finite)||bounds.minLongitude>bounds.maxLongitude||bounds.minLatitude>bounds.maxLatitude||bounds.minLongitude < -180||bounds.maxLongitude > 180||bounds.minLatitude < -90||bounds.maxLatitude > 90||!Number.isInteger(limit)||limit<1||limit>500)throw new ApiError('地图范围无效，请重试')
  const params=new URLSearchParams({minLongitude:String(Math.floor(bounds.minLongitude*1e6)/1e6),minLatitude:String(Math.floor(bounds.minLatitude*1e6)/1e6),maxLongitude:String(Math.ceil(bounds.maxLongitude*1e6)/1e6),maxLatitude:String(Math.ceil(bounds.maxLatitude*1e6)/1e6),limit:String(limit)})
  if(center){if(!finite(center.longitude)||!finite(center.latitude)||center.longitude < -180||center.longitude > 180||center.latitude < -90||center.latitude > 90)throw new ApiError('地图中心点无效，请重试');params.set('centerLongitude',center.longitude.toFixed(6));params.set('centerLatitude',center.latitude.toFixed(6))}
  for(const [key,value]of Object.entries(filters))if(value!==undefined&&value!=='')params.set(key,String(value))
  const data=await apiRequest<unknown>('/listings/map?'+params,{signal})
  if(!record(data)||!Array.isArray(data.items)||!data.items.every(marker)||new Set(data.items.map(x=>x.id)).size!==data.items.length||!Number.isInteger(data.total)||Number(data.total)<0||data.returned!==data.items.length||data.limit!==limit||data.items.length!==Math.min(Number(data.total),limit)||data.truncated!==(Number(data.total)>limit)||data.dataKind!=='SIMULATED'||data.coordinateSystem!=='GCJ-02'||data.coordinateScope!=='COMMUNITY'||!record(data.source)||data.source.dataKind!=='SIMULATED'||!record(data.bounds))throw new ApiError('服务返回的地图房源数据不完整，请重试')
  return data as unknown as MapCatalog
}
