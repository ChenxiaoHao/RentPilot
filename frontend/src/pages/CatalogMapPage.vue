<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { loadAmap } from '../services/amap-sdk'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import AppSheet from '../components/AppSheet.vue'
import CatalogLocationFilters from '../components/CatalogLocationFilters.vue'
import { fetchMapListings, type MapBounds, type MapListing } from '../services/map-catalog'
import { fetchCatalogFilters } from '../services/catalog'
import { layoutMapLabels, type MapLabel } from '../services/map-label-layout'
import { catalogPhoto, catalogTitle, metroCaption } from '../services/catalog-photos'
import type { CatalogFilters, CatalogQuery } from '../services/catalog-types'
import { money } from '../data/mock'

type Point={getLng:()=>number;getLat:()=>number}
type MapInstance={on:(event:string,callback:()=>void)=>void;getCenter:()=>Point;getZoom:()=>number;setZoom:(value:number)=>void;getBounds:()=>{getSouthWest:()=>Point;getNorthEast:()=>Point};lngLatToContainer:(position:number[])=>{getX:()=>number;getY:()=>number};destroy:()=>void}
type SDK={Map:new(host:HTMLElement,options:object)=>MapInstance}
const route=useRoute(),router=useRouter(),host=ref<HTMLElement>(),surface=ref<HTMLElement>(),instance=shallowRef<MapInstance>()
const options=ref<CatalogFilters>(),rows=ref<MapListing[]>([]),labels=ref<MapLabel[]>([]),selected=ref(''),busy=ref(false),error=ref(''),mapError=ref(''),filterOpen=ref(false)
let controller:AbortController|undefined,request=0,timer:ReturnType<typeof setTimeout>|undefined,closeTimer:ReturnType<typeof setTimeout>|undefined,frame=0,disposed=false
const filterText=(key:string)=>typeof route.query[key]==='string'?route.query[key] as string:''
const filters=computed<CatalogQuery>(()=>({q:filterText('q')||undefined,district:filterText('district')||undefined,town:filterText('town')||undefined,nearestMetro:filterText('nearestMetro')||undefined,rentalType:(filterText('rentalType')||undefined) as CatalogQuery['rentalType'],bedrooms:filterText('bedrooms')?Number(filterText('bedrooms')):undefined,maxRent:filterText('maxRent')?Number(filterText('maxRent')):undefined,maxMetroWalkMinutes:filterText('maxMetroWalkMinutes')?Number(filterText('maxMetroWalkMinutes')):undefined,metroLine:filterText('metroLine')||undefined}))
const inherited=computed(()=>[filters.value.q?`搜索：${filters.value.q}`:'',filters.value.bedrooms?`${filters.value.bedrooms} 室户型`:'',filters.value.maxMetroWalkMinutes?`地铁步行 ≤${filters.value.maxMetroWalkMinutes} 分钟`:'',filters.value.metroLine||''].filter(Boolean))
const areaLabel=computed(()=>['上海',filters.value.district,filters.value.town].filter(Boolean).join(' · '))
const selectedHome=computed(()=>rows.value.find(row=>row.id===selected.value))
const popupStyle=computed(()=>{
  const label=labels.value.find(item=>item.id===selected.value),container=surface.value
  if(!label||!container)return {}
  const w=Math.min(260,container.clientWidth-24), h=132
  let left:number,top:number
  if(label.x+50+w<=container.clientWidth-12){left=label.x+50;top=label.y-h/2}
  else if(label.x-50-w>=12){left=label.x-50-w;top=label.y-h/2}
  else{left=label.x-w/2;top=label.y-h-28>=12?label.y-h-28:label.y+28}
  left=Math.max(12,Math.min(container.clientWidth-w-12,left))
  top=Math.max(12,Math.min(container.clientHeight-h-12,top))
  return {left:`${left}px`,top:`${top}px`,width:`${w}px`}
})
function clearClose(){clearTimeout(closeTimer)}
function closeLater(){clearClose();closeTimer=setTimeout(()=>{selected.value=''},220)}
function hoverHome(event:PointerEvent,id:string){if(event.pointerType==='mouse'){clearClose();selected.value=id}}
function leaveHome(event:PointerEvent){if(event.pointerType==='mouse')closeLater()}
function toggleHome(id:string){clearClose();selected.value=id}
function dismiss(){clearClose();selected.value=''}
function zoom(delta:number){if(instance.value)instance.value.setZoom(Math.max(3,Math.min(20,instance.value.getZoom()+delta)))}
function change(key:string,event:Event){const value=(event.target as HTMLSelectElement).value;if(value===filterText(key))return;invalidate();void router.replace({path:'/find-map',query:{...route.query,[key]:value||undefined}})}
function updateLocation(patch:Record<string,string|undefined>){invalidate();void router.replace({path:'/find-map',query:{...route.query,...patch}})}
function clearFilters(){invalidate();void router.replace('/find-map')}
function invalidate(){++request;controller?.abort();rows.value=[];labels.value=[];dismiss();error.value='';busy.value=!!instance.value}
function bounds():MapBounds{const box=instance.value!.getBounds(),sw=box.getSouthWest(),ne=box.getNorthEast();return {minLongitude:Math.max(-180,sw.getLng()),minLatitude:Math.max(-90,sw.getLat()),maxLongitude:Math.min(180,ne.getLng()),maxLatitude:Math.min(90,ne.getLat())}}
function project(){
  if(!instance.value||!surface.value)return
  const anchors=rows.value.flatMap(row=>{try{const p=instance.value!.lngLatToContainer([row.longitude,row.latitude]);const x=p.getX(),y=p.getY();return Number.isFinite(x)&&Number.isFinite(y)?[{id:row.id,x,y}]:[]}catch{return []}})
  labels.value=layoutMapLabels(anchors,surface.value.clientWidth,surface.value.clientHeight)
}
function scheduleProject(){cancelAnimationFrame(frame);frame=requestAnimationFrame(project)}
async function load(){
  if(!instance.value)return
  const id=++request;controller?.abort();controller=new AbortController();busy.value=true;error.value=''
  try{const center=instance.value.getCenter();const result=await fetchMapListings(bounds(),filters.value,6,controller.signal,{longitude:center.getLng(),latitude:center.getLat()});if(id!==request)return;rows.value=result.items;if(!rows.value.some(row=>row.id===selected.value))dismiss();await nextTick();project()}
  catch(e){if(id!==request||(e instanceof DOMException&&e.name==='AbortError'))return;rows.value=[];labels.value=[];error.value=e instanceof Error?e.message:'房源暂时加载不了'}
  finally{if(id===request)busy.value=false}
}
function schedule(){++request;controller?.abort();busy.value=!!instance.value;clearTimeout(timer);dismiss();scheduleProject();timer=setTimeout(()=>void load(),250)}
async function start(){mapError.value='';try{const loaded=await loadAmap() as unknown as SDK;if(disposed||!host.value)return;instance.value=new loaded.Map(host.value,{viewMode:'2D',zoom:11,center:[121.48,31.23],resizeEnable:true});instance.value.on('complete',schedule);instance.value.on('moveend',schedule);instance.value.on('zoomend',schedule);instance.value.on('resize',schedule);instance.value.on('mapmove',scheduleProject);instance.value.on('click',dismiss);await nextTick();schedule()}catch(e){mapError.value=e instanceof Error?e.message:'地图暂时不可用'}}
watch(()=>JSON.stringify(filters.value),schedule)
onMounted(()=>{void start();void fetchCatalogFilters().then(v=>{options.value=v}).catch(()=>{})})
onBeforeUnmount(()=>{disposed=true;++request;clearTimeout(timer);clearClose();cancelAnimationFrame(frame);controller?.abort();instance.value?.destroy()})
</script>

<template>
  <div class="catalog-map" @keydown.esc="dismiss">
    <AppHeader title="地图找房" back hide-avatar heading><RouterLink :to="{path:'/listings',query:route.query}" class="icon-button" aria-label="列表找房"><AppIcon name="list"/></RouterLink></AppHeader>
    <main class="map-content">
      <div class="map-toolbar"><span><AppIcon name="map-pin" :size="15"/>{{ areaLabel }}</span><button class="map-filter-button" @click="filterOpen=true"><AppIcon name="sliders" :size="16"/>筛选</button></div>
      <div ref="surface" class="map-surface">
        <div ref="host" class="map-instance" aria-label="高德地图"></div>
        <div class="map-overlay">
          <svg class="map-leaders" aria-hidden="true"><template v-for="label in labels" :key="label.id"><line :x1="label.anchorX" :y1="label.anchorY" :x2="label.x" :y2="label.y"/><circle :cx="label.anchorX" :cy="label.anchorY" r="2.5"/></template></svg>
          <button v-for="label in labels" :key="label.id" class="map-price-pin" :class="{active:selected===label.id}" :style="{left:`${label.x}px`,top:`${label.y}px`}" data-testid="map-pin" :aria-label="`${rows.find(row=>row.id===label.id)?.community} ${money(rows.find(row=>row.id===label.id)?.rent || 0)} 模拟房源`" :aria-pressed="selected===label.id" @pointerenter="hoverHome($event,label.id)" @pointerleave="leaveHome" @focus="selected=label.id" @click.stop="toggleHome(label.id)">{{ money(rows.find(row=>row.id===label.id)?.rent || 0) }}</button>
          <Transition name="map-pop"><article v-if="selectedHome" class="map-preview" :style="popupStyle" data-testid="map-preview" @pointerenter="clearClose" @pointerleave="leaveHome">
            <RouterLink :to="`/listing/${selectedHome.id}`" class="preview-link" aria-label="查看房源详情"><img :src="catalogPhoto(selectedHome)" alt="室内示意，非房源实拍"/><div class="preview-info"><span class="preview-caption">模拟房源 · 室内示意</span><strong>{{ catalogTitle(selectedHome) }}</strong><span>{{ selectedHome.area }}㎡ · {{ selectedHome.district }}</span><span>{{ metroCaption(selectedHome) }}</span><b>{{ money(selectedHome.rent) }}<small> /月</small></b></div><AppIcon name="chevron-right" :size="15"/></RouterLink>
            <button class="preview-close" aria-label="关闭房源信息" @click.stop="dismiss"><AppIcon name="x" :size="15"/></button>
          </article></Transition>
        </div>
        <div class="map-crosshair" aria-hidden="true"></div>
        <div v-if="instance" class="map-zoom"><button aria-label="放大地图" @click="zoom(1)"><AppIcon name="plus" :size="19"/></button><button aria-label="缩小地图" @click="zoom(-1)"><AppIcon name="minus" :size="19"/></button></div>
        <div v-if="busy || error || (!rows.length&&!mapError)" class="map-status" role="status"><span v-if="busy">正在寻找附近房源…</span><span v-else-if="error">{{ error }} <button @click="load">重试</button></span><span v-else>附近没有符合条件的房源，试试移动地图。</span></div>
        <p v-if="rows.length" class="map-coordinate-note">标签错开显示 · 小区位置参考</p>
        <div v-if="mapError" class="map-error" role="alert"><p>{{ mapError }}</p><button class="secondary" @click="start">重新加载</button></div>
      </div>
    </main>
    <AppSheet v-model="filterOpen" title="地图筛选"><div class="map-filter-row"><select aria-label="地图最高月租" :value="filters.maxRent||''" @change="change('maxRent',$event)"><option value="">租金不限</option><option v-for="n in [1500,2000,2500,3000,4000,5000,7000,10000]" :key="n" :value="n">{{ money(n) }}以内</option></select><select aria-label="地图出租方式" :value="filters.rentalType||''" @change="change('rentalType',$event)"><option value="">整租 / 合租</option><option value="ENTIRE">整租</option><option value="SHARED">合租</option></select></div><div class="map-location"><CatalogLocationFilters :query="filters" :options="options" @change="updateLocation"/></div><p v-if="inherited.length" class="inherited-filter">{{ inherited.join(' · ') }}</p><template #footer><div class="map-filter-actions"><button class="secondary" @click="clearFilters">重置</button><button class="primary" @click="filterOpen=false">查看房源</button></div></template></AppSheet>
  </div>
</template>

<style scoped>
.catalog-map{height:calc(var(--app-height) - var(--safe-top));display:flex;flex-direction:column;padding-bottom:calc(88px + var(--safe-bottom))}.map-content{display:flex;flex-direction:column;min-height:0;flex:1}.map-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:3px var(--gutter) 12px;font-size:13px}.map-toolbar span{display:flex;align-items:center;gap:6px;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.map-filter-button{display:flex;align-items:center;gap:6px;min-height:44px;padding:0 14px;background:var(--surface);border-radius:12px;flex-shrink:0}.map-surface{position:relative;flex:1;min-height:140px;background:#f2f2f2;overflow:hidden}.map-instance,.map-overlay{position:absolute;inset:0}.map-overlay{z-index:9;pointer-events:none}.map-leaders{position:absolute;width:100%;height:100%;overflow:visible}.map-leaders line{stroke:#292929;stroke-width:1;opacity:.65}.map-leaders circle{fill:#292929;stroke:#fff;stroke-width:1}.map-price-pin{position:absolute;transform:translate(-50%,-50%);pointer-events:auto;border:1px solid #dedede;background:#fff;color:#161616;border-radius:24px;min-width:76px;min-height:44px;padding:8px 11px;font-size:13px;font-weight:650;white-space:nowrap;transition:background .16s,color .16s,transform .16s}.map-price-pin.active,.map-price-pin:hover{background:#161616;color:white;transform:translate(-50%,-50%) scale(1.04);z-index:2}.map-preview{position:absolute;z-index:4;background:white;border-radius:16px;box-shadow:0 6px 24px #0003;pointer-events:auto;min-height:124px;padding:9px}.preview-link{display:flex;align-items:center;gap:10px;min-height:106px}.preview-link>img{width:76px;height:104px;object-fit:cover;border-radius:10px;flex-shrink:0}.preview-info{display:flex;flex-direction:column;align-items:flex-start;gap:4px;min-width:0;flex:1}.preview-info strong{font-size:13px;line-height:1.4;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden}.preview-info span{font-size:11px;color:#666;white-space:nowrap;max-width:100%;overflow:hidden;text-overflow:ellipsis}.preview-info .preview-caption{font-size:10px}.preview-info b{font-size:16px;white-space:nowrap}.preview-info small{font-size:11px;font-weight:400;color:#777}.preview-link>svg{flex-shrink:0;color:#888}.preview-close{position:absolute;right:2px;top:2px;display:grid;place-items:center;width:32px;height:32px;border-radius:50%;background:white}.map-pop-enter-active,.map-pop-leave-active{transition:opacity .16s,transform .16s}.map-pop-enter-from,.map-pop-leave-to{opacity:0;transform:translateY(5px)}.map-crosshair{position:absolute;left:50%;top:50%;width:24px;height:24px;transform:translate(-50%,-50%);pointer-events:none;z-index:8}.map-crosshair:before,.map-crosshair:after{content:'';position:absolute;background:#222;box-shadow:0 0 2px white}.map-crosshair:before{width:24px;height:2px;top:11px}.map-crosshair:after{height:24px;width:2px;left:11px}.map-zoom{position:absolute;right:12px;bottom:34px;display:flex;flex-direction:column;z-index:10;background:white;border-radius:12px;box-shadow:0 2px 10px #0002}.map-zoom button{width:44px;height:44px;display:grid;place-items:center}.map-zoom button+button{border-top:1px solid var(--line)}.map-coordinate-note{position:absolute;bottom:26px;left:12px;z-index:8;font-size:11px;color:#555;background:#fffe;padding:4px 7px;border-radius:5px;pointer-events:none}.map-status{position:absolute;z-index:11;top:12px;left:50%;transform:translateX(-50%);max-width:calc(100% - 24px);background:#fff;border-radius:12px;padding:10px 14px;font-size:12px;box-shadow:0 2px 10px #0002;text-align:center}.map-status button{text-decoration:underline}.map-error{position:absolute;z-index:12;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:12px;padding:20px;text-align:center;font-size:13px}.map-filter-row{display:flex;gap:8px;padding:0 var(--gutter) 12px}.map-filter-row select{background:var(--surface);border:0;border-radius:10px;min-height:44px;padding:0 9px;font-size:12px;min-width:0;flex:1}.map-location{padding:0 var(--gutter) 8px}.map-location .location-filters{margin-top:0}.inherited-filter{padding:8px var(--gutter);font-size:12px;color:var(--muted)}.map-filter-actions{display:flex;gap:10px}.map-filter-actions .secondary{width:35%}.map-filter-actions .primary{flex:1}
@media(max-width:359px){.preview-link>img{width:68px;height:94px}.preview-link{min-height:96px}.map-preview{min-height:114px}}
@media(prefers-reduced-motion:reduce){.map-price-pin,.map-pop-enter-active,.map-pop-leave-active{transition:none}}
</style>
