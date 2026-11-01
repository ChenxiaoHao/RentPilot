<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listings, money } from '../data/mock'
import { usePilotStore } from '../stores/pilot'
import { createMapAdapter, type MapAdapter } from '../services/map'
import { queryListings, type ListingQuery } from '../services/listing-query'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import PropertyCard from '../components/PropertyCard.vue'
const route = useRoute(), router = useRouter(), store = usePilotStore()
const district = ref(['张江高科','康桥','唐镇','川沙'].includes(String(route.query.district)) ? String(route.query.district) : 'all')
const budget = ref(Number(route.query.budget) || 0), type = ref(route.query.type === 'two' ? 'two' : 'one')
const metro = ref(['2号线','11号线'].includes(String(route.query.metro)) ? String(route.query.metro) : 'all')
const selected = ref(listings.some(h => h.id === route.query.focus) ? String(route.query.focus) : 'h1'), mapHost = ref<HTMLElement>(), maxCommute = ref(Number(route.query.commute) || 0)
const query = ref(typeof route.query.q === 'string' ? route.query.q : ''), mode = ref<'map'|'list'>(route.query.view === 'list' ? 'list' : 'map')
const sort = ref<ListingQuery['sort']>(['rent-asc','rent-desc','commute'].includes(String(route.query.sort)) ? route.query.sort as ListingQuery['sort'] : 'recommended'), savedOnly = ref(route.query.saved === '1'), expanded = ref(false)
const filtered = computed(() => queryListings(listings,{ query: query.value, district: district.value, budget: budget.value, type: type.value, metro: metro.value, commute: maxCommute.value, savedOnly: savedOnly.value, savedIds: store.savedListings, sort: sort.value }))
const ordered = computed(() => mode.value === 'map' ? [...filtered.value].sort((a,b) => Number(b.id === selected.value)-Number(a.id === selected.value)) : filtered.value)
const selectedHome = computed(() => filtered.value.find(home => home.id === selected.value))
watch(filtered, homes => { if (!homes.some(home => home.id === selected.value)) selected.value = homes[0]?.id ?? '' },{immediate:true,flush:'sync'})
watch([query,district,budget,type,metro,maxCommute,mode,sort,savedOnly,selected], () => {
  router.replace({ query: { q: query.value || undefined, district: district.value !== 'all' ? district.value : undefined,
    budget: budget.value || undefined, type: type.value !== 'one' ? type.value : undefined,
    metro: metro.value !== 'all' ? metro.value : undefined, commute: maxCommute.value || undefined,
    view: mode.value === 'list' ? 'list' : undefined, sort: sort.value !== 'recommended' ? sort.value : undefined,
    saved: savedOnly.value ? '1' : undefined, focus: selected.value !== 'h1' ? selected.value || undefined : undefined } })
})
const positions = [{x:52,y:46},{x:29,y:70},{x:74,y:61},{x:76,y:23},{x:37,y:24},{x:21,y:43}]
function markerPosition(id: string) { return positions[Math.max(0,listings.findIndex(home => home.id === id)) % positions.length]! }
let adapter: MapAdapter|null = null
onMounted(async () => { adapter = createMapAdapter(); if(adapter && mapHost.value) await adapter.mount(mapHost.value) })
onUnmounted(() => adapter?.destroy())
function reset() { district.value='all';budget.value=0;type.value='one';metro.value='all';maxCommute.value=0;query.value='';savedOnly.value=false;selected.value='h1' }
</script>

<template>
  <div class="map-page" :class="{'list-mode':mode === 'list', 'expanded-list':expanded}">
    <AppHeader title="地图找房" hide-avatar><button class="icon-button mode-button" :aria-label="mode === 'map' ? '列表模式' : '地图模式'" @click="mode = mode === 'map' ? 'list' : 'map'"><AppIcon :name="mode === 'map' ? 'list' : 'map'" :size="20" /></button></AppHeader>
    <div class="map-heading"><h1>在上海，找个家。</h1><span class="tiny muted">{{ mode === 'map' ? '看位置，也看生活' : '把喜欢的留在清单里' }}</span></div>
    <div class="map-search"><AppIcon name="search" :size="18" /><input v-model="query" type="search" aria-label="搜索区域、小区或地铁" placeholder="区域、小区或地铁站" /><button v-if="query" class="icon-button" aria-label="清空搜索" @click="query='' "><AppIcon name="x" :size="17" /></button><button class="icon-button" :class="{chosen:savedOnly}" :aria-pressed="savedOnly" aria-label="只看收藏房源" @click="savedOnly = !savedOnly"><AppIcon name="heart" :size="18" :fill="savedOnly ? 'currentColor' : 'none'" /></button></div>
    <div class="map-filters">
      <label :class="{active:district !== 'all'}"><select v-model="district" aria-label="区域筛选"><option value="all">区域</option><option v-for="area in ['张江高科','康桥','唐镇','川沙']" :key="area">{{ area }}</option></select></label>
      <label :class="{active:budget>0}"><select v-model.number="budget" aria-label="租金预算"><option :value="0">租金</option><option :value="3000">¥3,000 以下</option><option :value="4200">¥4,200 以下</option><option :value="5000">¥5,000 以下</option><option :value="6000">¥6,000 以下</option><option v-if="budget && ![3000,4200,5000,6000].includes(budget)" :value="budget">{{ money(budget) }} 以下</option></select></label>
      <label :class="{active:type !== 'one'}"><select v-model="type" aria-label="户型筛选"><option value="one">一居</option><option value="two">二居</option></select></label>
      <label :class="{active:metro !== 'all'}"><select v-model="metro" aria-label="地铁线路"><option value="all">地铁</option><option value="2号线">2 号线</option><option value="11号线">11 号线</option></select></label>
    </div>
    <section v-show="mode === 'map'" class="map-canvas" aria-label="模拟房源点位示意图">
      <div ref="mapHost" class="map-host"></div>
      <svg class="map-art" viewBox="0 0 402 380" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><rect width="402" height="380" fill="#f1f2f1"/><path d="M320-20 300 120 330 220 300 400" fill="none" stroke="#d9dddc" stroke-width="32"/><path d="M-20 50 420 130M-20 210 420 260M70-20 100 410M245-20 210 410M-20 340 420 350" fill="none" stroke="white" stroke-width="12"/><path d="m-20 140 420 50M150-20l15 420M-20 300l420-100" fill="none" stroke="white" stroke-width="6"/><path d="M-20 50 420 130M-20 210 420 260M70-20 100 410M245-20 210 410" fill="none" stroke="#e0e2df" stroke-width="1"/><rect x="115" y="105" width="36" height="65" rx="7" fill="#e1e5e0"/><rect x="250" y="275" width="35" height="55" rx="6" fill="#e1e5e0"/><text x="115" y="34" fill="#878b87" font-size="11">张江路</text><text x="155" y="310" fill="#878b87" font-size="11">祖冲之路</text><text x="333" y="173" fill="#878b87" font-size="11">张江</text></svg>
      <button v-for="home in filtered" :key="home.id" class="price-pin" :class="{selected:selected === home.id}" :style="{left:`${markerPosition(home.id).x}%`,top:`${markerPosition(home.id).y}%`}" :aria-label="`${home.name} ${money(home.price)}`" :aria-pressed="selected === home.id" @click="selected=home.id">{{ money(home.price) }}</button>
      <span class="map-notice">示意地图 · 非实时点位</span><button class="icon-button map-reset" aria-label="重置地图筛选" @click="reset"><AppIcon name="locate" /></button>
    </section>
    <section class="map-sheet">
      <button v-if="mode === 'map'" class="sheet-expander" :aria-label="expanded ? '收起房源列表' : '展开房源列表'" :aria-expanded="expanded" @click="expanded = !expanded"><span></span></button>
      <div class="between map-result-heading"><h2>{{ filtered.length }} 套模拟房源</h2><label><span class="sr-only">房源排序</span><select v-model="sort" aria-label="房源排序"><option value="recommended">推荐排序</option><option value="rent-asc">租金从低到高</option><option value="rent-desc">租金从高到低</option><option value="commute">通勤最短</option></select></label></div>
      <div class="map-current"><p v-if="mode === 'map'" role="status" class="selection-status">{{ selectedHome ? `当前选中：${selectedHome.name}` : '没有符合当前条件的房源' }}</p><span v-if="maxCommute" class="tiny muted">通勤 ≤{{ maxCommute }} 分钟</span><button v-if="budget || district !== 'all' || metro !== 'all' || type !== 'one' || savedOnly" class="reset-filters" @click="reset">重置筛选</button></div>
      <div v-if="filtered.length" class="map-homes"><PropertyCard v-for="home in ordered" :key="home.id" :home="home" :compact="mode === 'map'" /></div>
      <div v-else class="map-empty"><AppIcon name="search" :size="27" /><h3>没有找到合适的房源</h3><p class="small muted">试着换个关键词，或少选一项条件。</p><RouterLink :to="{path:'/empty',query:{...route.query,budget,commute:maxCommute,district,type,metro}}" class="primary">查看条件调整建议</RouterLink><button class="text-action muted" @click="reset">重置筛选</button></div>
    </section>
  </div>
</template>

<style scoped>
.map-page{height:calc(var(--app-height) - var(--safe-top));display:flex;flex-direction:column;overflow:hidden;padding-bottom:calc(92px + var(--safe-bottom));isolation:isolate}.map-page>.app-header{flex-shrink:0}.map-heading{padding:4px var(--gutter) 16px;flex-shrink:0}.map-heading h1{font-size:25px}.map-heading>span{display:none}.map-search{display:flex;align-items:center;gap:10px;margin:0 var(--gutter);padding:2px 6px 2px 14px;border-radius:14px;background:var(--surface);flex-shrink:0}.map-search>svg{color:var(--muted);flex-shrink:0}.map-search input{width:100%;min-width:0;min-height:44px;border:0;outline:none;background:transparent;font-size:13px}.map-search input::-webkit-search-cancel-button{display:none}.map-search input::placeholder{color:#707070}.map-search .icon-button{min-width:38px;padding:8px}.map-search .chosen{color:var(--ink)}.map-filters{display:flex;gap:8px;padding:8px var(--gutter) 12px;overflow-x:auto;scrollbar-width:none;flex-shrink:0}.map-filters label{flex:1;min-width:62px}.map-filters select{width:100%;min-height:44px;max-width:140px;border:0;border-radius:8px;background:#fff;font-size:13px;padding:8px 2px}.map-filters .active select{font-weight:600;background:var(--surface)}.map-canvas{position:relative;flex:1;min-height:110px;background:#f1f2f1;overflow:hidden}.map-art,.map-host{position:absolute;inset:0;width:100%;height:100%}.map-host{z-index:1}.price-pin{position:absolute;z-index:2;transform:translate(-50%,-50%);border-radius:24px;background:#fff;min-height:44px;padding:8px 12px;font-size:12px;font-weight:600;border:1px solid #ddd;white-space:nowrap}.price-pin:after{content:'';position:absolute;left:calc(50% - 4px);bottom:-5px;border-left:4px solid transparent;border-right:4px solid transparent;border-top:6px solid #fff}.price-pin.selected{background:var(--ink);color:#fff;z-index:3}.price-pin.selected:after{border-top-color:var(--ink)}.map-notice{position:absolute;bottom:18px;left:var(--gutter);font-size:12px;color:#606760;background:#f8f9f8;padding:4px 7px;border-radius:5px}.map-reset{position:absolute;bottom:14px;right:16px;background:#fff;z-index:5;border:1px solid #ddd}.map-sheet{position:relative;z-index:6;border-radius:24px 24px 0 0;background:#fff;padding:0 var(--gutter);margin-top:-12px;max-height:42%;min-height:168px;display:flex;flex-direction:column;flex-shrink:1}.sheet-expander{width:100%;min-height:30px;flex-shrink:0;display:grid;place-items:center}.sheet-expander span{width:32px;height:4px;border-radius:4px;background:#d3d3d3}.map-result-heading{flex-shrink:0;gap:6px}.map-result-heading h2{font-size:16px}.map-result-heading select{font-size:13px;min-height:44px;border:0;background:white;color:var(--muted);width:102px;padding:8px 0}.map-current{display:flex;align-items:center;gap:8px;flex-shrink:0;flex-wrap:wrap}.selection-status{font-size:13px;line-height:1.6;color:var(--muted);flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.reset-filters{margin-left:auto;font-size:12px;min-height:32px;color:var(--muted);padding:4px 0}.map-homes{overflow-y:auto;min-height:0;overscroll-behavior:contain;padding-bottom:8px;scrollbar-width:thin}.map-homes>.property-card+.property-card{border-top:1px solid var(--line)}.map-empty{text-align:center;padding:20px 0;overflow-y:auto;min-height:0}.map-empty>svg{margin:0 auto 12px}.map-empty h3{font-size:16px}.map-empty p{margin:8px 0 18px}.map-empty .primary{min-height:46px;font-size:13px}.expanded-list .map-sheet{max-height:64%}.list-mode .map-sheet{max-height:none;flex:1;min-height:0;border-radius:0;margin-top:0;padding-top:0}.list-mode .map-current:empty{display:none}.list-mode .map-homes{flex:1}.list-mode .map-empty{padding-top:50px}.keyboard-open .map-page{padding-bottom:10px}.keyboard-open .map-canvas{display:none}.keyboard-open .map-sheet{flex:1;min-height:0;max-height:none;margin-top:0;border-radius:0}.keyboard-open .sheet-expander{display:none}
@media(max-height:700px){.map-heading{display:none}.map-sheet{max-height:44%}.map-canvas{min-height:105px}.map-empty{padding-top:8px}.map-empty>svg,.map-empty p{display:none}.map-empty h3{margin-bottom:12px}.map-empty .primary{min-height:44px}}
</style>
