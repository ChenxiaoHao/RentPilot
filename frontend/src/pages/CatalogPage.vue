<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, onActivated, onDeactivated, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import CatalogCard from '../components/CatalogCard.vue'
import CatalogLocationFilters from '../components/CatalogLocationFilters.vue'
import { fetchCatalog, fetchCatalogFilters, CatalogError } from '../services/catalog'
import type { CatalogFilters, CatalogListing, CatalogQuery, CatalogSort } from '../services/catalog-types'

import { money } from '../data/mock'

defineOptions({name:'CatalogPage'})
const route = useRoute(), router = useRouter()
const text = (key: string) => typeof route.query[key] === 'string' ? route.query[key] as string : ''
const search = ref(text('q'))
const query = computed<CatalogQuery>(() => ({
  q: text('q') || undefined, district: text('district') || undefined, town: text('town') || undefined, nearestMetro: text('nearestMetro') || undefined,
  rentalType: (text('rentalType') || undefined) as CatalogQuery['rentalType'],
  bedrooms: text('bedrooms') ? Number(text('bedrooms')) : undefined,
  maxRent: text('maxRent') ? Number(text('maxRent')) : undefined,
  maxMetroWalkMinutes: text('maxMetroWalkMinutes') ? Number(text('maxMetroWalkMinutes')) : undefined,
  metroLine: text('metroLine') || undefined, sort: (text('sort') || undefined) as CatalogSort | undefined,
}))
const homes = ref<CatalogListing[]>([]), total = ref(0), page = ref(-1)
const loading = ref(false), loadingMore = ref(false), error = ref(''), moreError = ref('')
const options = ref<CatalogFilters>(), filterError = ref(false), filtering = ref(false)

const hasFilters = computed(() => Object.entries(query.value).some(([key, value]) => key !== 'sort' && value !== undefined))
const count = computed(() => `${total.value.toLocaleString('zh-CN')} 套模拟房源`)
let lastQuery = ''
let requestId = 0, searchTimer: ReturnType<typeof setTimeout> | undefined
let listController: AbortController | undefined
const filterController = new AbortController()

function updateFilters(patch: Record<string, string | undefined>) {
  if(route.path!=='/listings')return
  clearTimeout(searchTimer)
  const values: Record<string, string | undefined> = { ...route.query as Record<string, string>, q: search.value.trim() || undefined, ...patch }
  for (const key of Object.keys(values)) if (!values[key]) delete values[key]
  void router.replace({ path: '/listings', query: values })
}
function change(key: string, event: Event) { updateFilters({ [key]: (event.target as HTMLSelectElement).value || undefined }) }
function clear() { clearTimeout(searchTimer); search.value = ''; void router.replace('/listings') }
watch(search, () => { clearTimeout(searchTimer); if(route.path!=='/listings')return; searchTimer = setTimeout(() => updateFilters({ q: search.value.trim() || undefined }), 300) })
watch(() => text('q'), value => { if(route.path==='/listings') search.value = value })

async function load(append = false) {
  const id = ++requestId
  listController?.abort()
  listController = new AbortController()
  const nextPage = append ? page.value + 1 : 0
  const snapshot = { ...query.value }
  error.value = ''; moreError.value = ''
  loading.value = !append; loadingMore.value = append
  if (!append) { homes.value = []; page.value = -1; total.value = 0 }
  try {
    const result = await fetchCatalog(snapshot, nextPage, listController.signal)
    if (id !== requestId) return
    if (append && result.items.some(item => homes.value.some(previous => previous.id === item.id))) throw new CatalogError('房源分页数据有变化，请重新筛选')
    homes.value = append ? [...homes.value, ...result.items] : result.items
    page.value = result.page; total.value = result.total
  } catch (cause) {
    if (id !== requestId || (cause instanceof DOMException && cause.name === 'AbortError')) return
    const message = cause instanceof Error ? cause.message : '房源服务暂不可用'
    if (append) moreError.value = message
    else error.value = message
  } finally {
    if (id === requestId) { loading.value = false; loadingMore.value = false }
  }
}
async function loadFilters() {
  filterError.value = false; filtering.value = true
  try { options.value = await fetchCatalogFilters(filterController.signal) }
  catch { if (!filterController.signal.aborted) filterError.value = true }
  finally { filtering.value = false }
}
watch(() => JSON.stringify(query.value), key => { if(route.path==='/listings'&&(key!==lastQuery||page.value<0)){lastQuery=key;void load()} }, { immediate: true })
onDeactivated(()=>clearTimeout(searchTimer))
onActivated(()=>{if(route.path==='/listings'){search.value=text('q');if(page.value<0&&!loading.value)void load()}})
onMounted(loadFilters)
onBeforeUnmount(() => { ++requestId; clearTimeout(searchTimer); listController?.abort();  filterController.abort() })
</script>

<template>
  <AppHeader hide-avatar><RouterLink :to="{path:'/find-map',query:route.query}" class="icon-button" aria-label="地图找房"><AppIcon name="map" :size="20"/></RouterLink></AppHeader>
  <main class="page catalog-page">
    <div class="catalog-title"><div><h1>上海租房</h1><p v-if="!loading && !error" data-testid="catalog-total" role="status">{{ count }}</p><p v-else class="muted">{{ loading ? '正在找合适的房源…' : '稍后再试，也许就好了' }}</p></div><span class="catalog-city"><AppIcon name="map-pin" :size="14" />上海</span></div>
    <form class="catalog-search" role="search" @submit.prevent="updateFilters({ q: search.trim() || undefined })"><AppIcon name="search" :size="18" /><input v-model="search" type="search" aria-label="搜索房源" placeholder="搜索小区、区域或街道" maxlength="120" enterkeyhint="search" /><button v-if="search" type="button" class="icon-button" aria-label="清空搜索" @click="search='';updateFilters({q:undefined})"><AppIcon name="x" :size="17" /></button></form>
    <CatalogLocationFilters :query="query" :options="options" @change="updateFilters" />
    <div class="catalog-controls">
      <label><select :value="query.maxRent || ''" aria-label="最高月租" @change="change('maxRent',$event)"><option value="">租金不限</option><option v-for="rent in [1500,2000,2500,3000,4000,5000,7000,10000]" :key="rent" :value="rent">{{ money(rent) }}以内</option></select><AppIcon name="chevron-down" :size="13" /></label>
      <label><select :value="query.bedrooms || ''" aria-label="卧室数" @change="change('bedrooms',$event)"><option value="">户型不限</option><option v-for="rooms in [1,2,3,4]" :key="rooms" :value="rooms">{{ rooms }} 室户型</option></select><AppIcon name="chevron-down" :size="13" /></label>
      <label><select :value="query.sort || ''" aria-label="房源排序" @change="change('sort',$event)"><option value="">默认排序</option><option value="rentAsc">租金从低到高</option><option value="rentDesc">租金从高到低</option><option value="areaDesc">面积从大到小</option><option value="metroWalkAsc">地铁步行优先</option></select><AppIcon name="chevron-down" :size="13" /></label>
    </div>
    <div class="catalog-secondary-controls"><label><select :value="query.rentalType || ''" aria-label="出租方式" @change="change('rentalType',$event)"><option value="">整租 / 合租</option><option value="ENTIRE">整租</option><option value="SHARED">合租单间</option></select></label><label><select :value="query.maxMetroWalkMinutes || ''" aria-label="地铁步行时间" @change="change('maxMetroWalkMinutes',$event)"><option value="">地铁步行不限</option><option value="10">地铁步行10分钟内</option><option value="15">地铁步行15分钟内</option><option value="20">地铁步行20分钟内</option><option value="30">地铁步行30分钟内</option></select></label><button v-if="hasFilters" class="text-action" aria-label="重置筛选" @click="clear">重置<AppIcon name="x" :size="13" /></button></div>
    <p v-if="query.rentalType === 'SHARED' && query.bedrooms" class="catalog-note">{{ query.bedrooms }} 室指合租房的整套户型，出租的是单间。</p>
    <button v-if="filterError" class="text-action muted" :disabled="filtering" @click="loadFilters">区域选项加载失败，点此重试<AppIcon name="refresh" :size="14" /></button>
    <section v-if="loading" class="catalog-loading" aria-label="正在加载房源" aria-busy="true"><div v-for="i in 4" :key="i" class="catalog-skeleton"><span></span><div><i></i><i></i><i></i><i></i></div></div></section>
    <section v-else-if="error" class="catalog-state" role="alert"><AppIcon name="wifi" :size="30" /><h2>房源暂时加载不了</h2><p>{{ error }}</p><button class="primary" aria-label="重试加载房源" @click="load()">重新加载<AppIcon name="refresh" :size="16" /></button></section>
    <section v-else-if="!homes.length" class="catalog-state"><AppIcon name="search" :size="30" /><h2>暂时没有符合条件的房源</h2><p>试着调整区域、预算或户型，看看其他选择。</p><button class="secondary" aria-label="清除筛选条件" @click="clear">清除筛选条件</button></section>
    <section v-else class="catalog-results" aria-label="房源结果" :aria-busy="loadingMore">
      <CatalogCard v-for="home in homes" :key="home.id" :home="home" />
      <div class="catalog-pagination"><p v-if="moreError" class="small muted" role="alert">{{ moreError }}</p><button v-if="homes.length < total" class="secondary" :disabled="loadingMore" :aria-label="moreError ? '重试加载更多房源' : '加载更多房源'" @click="load(true)">{{ loadingMore ? '正在加载…' : moreError ? '重试加载更多' : '加载更多' }}<AppIcon v-if="!loadingMore" name="chevron-down" :size="16" /></button><p v-else class="tiny muted">已显示全部 {{ homes.length }} 套房源</p><span v-if="homes.length < total" class="tiny muted">已显示 {{ homes.length }} / {{ total }} 套</span></div>
    </section>
  </main>

</template>

<style scoped>
.catalog-page{padding-top:8px}.catalog-title{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:4px 0 22px}.catalog-title h1{font-size:27px;font-weight:600;letter-spacing:-.04em;line-height:1.4}.catalog-title p{font-size:13px;color:var(--muted);margin-top:8px;min-height:20px}.catalog-city{display:flex;align-items:center;gap:4px;font-size:12px;color:var(--muted)}.catalog-search{display:flex;align-items:center;gap:10px;background:var(--surface);border-radius:15px;min-height:48px;padding:0 12px}.catalog-search>svg{color:var(--muted);flex-shrink:0}.catalog-search input{min-width:0;width:100%;padding:12px 0;font-size:14px;outline:0;border:0;background:transparent;min-height:48px}.catalog-search .icon-button{margin-right:-8px}.catalog-controls{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin-top:10px}.catalog-controls label{position:relative;min-width:0;display:flex;align-items:center}.catalog-controls select{width:100%;min-width:0;appearance:none;padding:10px 16px 10px 0;font-size:13px;background:transparent;min-height:44px;border:0;outline-offset:2px;text-overflow:ellipsis}.catalog-controls label>svg{position:absolute;right:0;pointer-events:none;color:var(--muted)}.catalog-secondary-controls{display:flex;align-items:center;gap:10px;padding:4px 0 12px;border-bottom:1px solid var(--line);min-width:0}.catalog-secondary-controls label{min-width:0;max-width:47%}.catalog-secondary-controls select{font-size:12px;background:var(--surface);min-height:44px;border:0;border-radius:10px;padding:8px;max-width:100%}.catalog-secondary-controls .text-action{font-size:12px;margin-left:auto;flex-shrink:0}.catalog-note{font-size:12px;color:var(--muted);padding-top:10px}.catalog-results{min-height:280px}.catalog-row{display:grid;grid-template-columns:34% minmax(0,1fr);gap:15px;width:100%;text-align:left;padding:20px 0;border-bottom:1px solid var(--line)}.catalog-photo{display:block;position:relative;align-self:start;border-radius:13px;overflow:hidden;aspect-ratio:4/5;background:var(--surface)}.catalog-photo img{display:block;width:100%;height:100%;object-fit:cover}.catalog-photo>span{position:absolute;left:7px;bottom:7px;padding:3px 6px;background:#fff;border-radius:4px;font-size:11px;color:#555}.catalog-info{display:flex;flex-direction:column;gap:5px;min-width:0}.catalog-home-name{font-size:15px;font-weight:600;line-height:1.6;margin-bottom:3px}.catalog-meta,.catalog-metro{font-size:13px;color:var(--muted);line-height:1.65;overflow-wrap:anywhere}.catalog-metro{color:#535353;margin-top:2px}.catalog-rent{font-size:23px;font-weight:600;letter-spacing:-.035em;margin-top:auto;padding-top:9px;line-height:1.3}.catalog-rent small{font-size:12px;font-weight:400;color:var(--muted);margin-left:5px;letter-spacing:0}.catalog-pagination{display:flex;align-items:center;flex-direction:column;gap:12px;padding:26px 0 8px}.catalog-pagination>.secondary{width:100%;scroll-margin-block-end:calc(var(--nav-space) + var(--safe-bottom));scroll-margin-block-start:16px}.catalog-state{display:flex;flex-direction:column;align-items:center;text-align:center;gap:16px;padding:60px 12px;min-height:340px}.catalog-state>svg{color:#777}.catalog-state h2{font-size:19px;line-height:1.6}.catalog-state p{font-size:13px;color:var(--muted);max-width:280px}.catalog-state button{margin-top:6px;min-width:170px}.catalog-skeleton{display:grid;grid-template-columns:34% 1fr;gap:16px;padding:20px 0;border-bottom:1px solid var(--line)}.catalog-skeleton>span{aspect-ratio:4/5;border-radius:13px;background:var(--surface)}.catalog-skeleton>div{padding-top:5px;display:flex;flex-direction:column;gap:14px}.catalog-skeleton i{display:block;height:12px;width:78%;background:var(--surface);border-radius:4px}.catalog-skeleton i:first-child{height:18px;width:94%}.catalog-skeleton i:last-child{height:23px;width:50%;margin-top:auto}@media(max-width:359px){.catalog-row{gap:12px}.catalog-home-name{font-size:14px}.catalog-rent{font-size:21px}.catalog-controls{gap:7px}.catalog-controls select{font-size:12px}.catalog-secondary-controls{gap:7px}.catalog-secondary-controls select{padding:8px 5px;font-size:12px}.catalog-facts{font-size:12px}.catalog-facts>div{gap:10px}.catalog-detail figure img{height:190px}}
</style>

