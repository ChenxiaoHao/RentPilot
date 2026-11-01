<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { loadAmap, type AMapInstance } from '../services/amap-sdk'
import AppIcon from './AppIcon.vue'
const props=defineProps<{longitude:number|null;latitude:number|null;locationStatus:string;community:string}>()
const valid=computed(()=>props.locationStatus!=='UNVERIFIED'&&typeof props.longitude==='number'&&Number.isFinite(props.longitude)&&Math.abs(props.longitude)<=180&&typeof props.latitude==='number'&&Number.isFinite(props.latitude)&&Math.abs(props.latitude)<=90)
const host=ref<HTMLElement>(),map=shallowRef<AMapInstance>(),state=ref<'loading'|'ready'|'error'>('loading')
let generation=0,disposed=false
function clear(){map.value?.destroy();map.value=undefined}
async function start(){const version=++generation;clear();if(!valid.value)return;state.value='loading';try{await nextTick();const sdk=await loadAmap();if(disposed||version!==generation||!host.value)return;const position=[props.longitude!,props.latitude!];const instance=new sdk.Map(host.value,{viewMode:'2D',zoom:15,center:position,resizeEnable:true,dragEnable:true,touchZoom:true,scrollWheel:false,keyboardEnable:false});map.value=instance;const label=document.createElement('div');label.className='listing-map-marker';label.textContent=props.community;new sdk.Marker({map:instance,position,content:label,anchor:'bottom-center'});state.value='ready'}catch{if(!disposed&&version===generation)state.value='error'}}
function zoom(delta:number){if(map.value)map.value.setZoom(Math.max(3,Math.min(20,map.value.getZoom()+delta)))}
function recenter(){map.value?.setZoomAndCenter(15,[props.longitude!,props.latitude!])}
watch(()=>[props.longitude,props.latitude,props.locationStatus,props.community],()=>void start())
onMounted(()=>void start());onBeforeUnmount(()=>{disposed=true;++generation;clear()})
</script>
<template><div class="listing-map"><template v-if="valid"><div ref="host" class="listing-map-host" aria-label="房源位置地图"></div><div v-if="state==='ready'" class="listing-map-controls"><button aria-label="放大地图" @click="zoom(1)"><AppIcon name="plus" :size="18"/></button><button aria-label="缩小地图" @click="zoom(-1)"><AppIcon name="minus" :size="18"/></button><button aria-label="回到房源" @click="recenter"><AppIcon name="map-pin" :size="18"/></button></div><div v-if="state!=='ready'" class="listing-map-status" role="status"><span>{{ state==='loading'?'地图加载中…':'地图暂时无法加载' }}</span><button v-if="state==='error'" @click="start">重试</button></div><span v-if="state==='ready'" class="listing-map-note">模拟房源 · 小区位置参考</span></template><div v-else class="listing-map-status">位置待核验</div></div></template>
<style scoped>
.listing-map{position:relative;height:260px;overflow:hidden;border-radius:12px;background:#f2f2f2;margin:14px 0}.listing-map-host{position:absolute;inset:0}.listing-map-controls{position:absolute;right:10px;top:10px;z-index:10;display:flex;flex-direction:column;background:white;border-radius:10px;overflow:hidden}.listing-map-controls button{width:44px;height:44px;display:grid;place-items:center}.listing-map-status{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:12px;font-size:13px;color:#555}.listing-map-status button{text-decoration:underline;min-height:44px}.listing-map-note{position:absolute;left:10px;bottom:25px;z-index:8;padding:4px 7px;background:#fff;font-size:11px;border-radius:4px;pointer-events:none}
</style>
<style>.listing-map-marker{background:#171717;color:white;border:2px solid white;border-radius:16px;padding:7px 10px;font-size:12px;white-space:nowrap}</style>
