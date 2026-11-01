<script setup lang="ts">
import { ref, watch } from 'vue'
import { fetchMetroLineStations } from '../services/catalog'
import type { CatalogFilters, CatalogQuery } from '../services/catalog-types'
const props = defineProps<{ query: CatalogQuery; options?: CatalogFilters }>()
const emit = defineEmits<{ change: [patch: Record<string, string | undefined>] }>()
const stations = ref<string[]>([]), loading = ref(false), failed = ref(false)
let generation = 0
async function load() {
  const line = props.query.metroLine, id = ++generation
  stations.value = []; failed.value = false
  if (!line) return
  loading.value = true
  try { const result = await fetchMetroLineStations(line); if (id === generation) { if (result.status !== 'OK' && result.status !== 'ok' && result.status !== 'SUCCESS') throw new Error(); stations.value = result.stations } }
  catch { if (id === generation) failed.value = true }
  finally { if (id === generation) loading.value = false }
}
watch(() => props.query.metroLine, load, { immediate: true })
const value = (event: Event) => (event.target as HTMLSelectElement).value || undefined
</script>
<template>
  <div class="location-filters">
    <select aria-label="行政区" :value="query.district || ''" :disabled="!options" @change="emit('change', { district: value($event), town: undefined })"><option value="">全部区域</option><option v-for="area in options?.districts" :key="area.value" :value="area.value">{{ area.value }}</option></select>
    <select aria-label="街道镇" :value="query.town || ''" :disabled="!query.district || !options" @change="emit('change', { town: value($event) })"><option value="">全部街镇</option><option v-for="town in (options?.towns || []).filter(t => t.district === query.district)" :key="town.value" :value="town.value">{{ town.value }}</option></select>
    <select aria-label="地铁线路" :value="query.metroLine || ''" :disabled="!options" @change="emit('change', { metroLine: value($event), nearestMetro: undefined })"><option value="">全部线路</option><option v-for="line in options?.metroLines" :key="line" :value="line">{{ line }}</option></select>
    <select aria-label="地铁站点" :value="query.nearestMetro || ''" :disabled="!query.metroLine || loading || failed" @change="emit('change', { nearestMetro: value($event) })"><option value="">{{ loading ? '站点加载中' : '全部站点' }}</option><option v-for="station in stations" :key="station" :value="station">{{ station }}</option></select>
    <button v-if="failed" type="button" class="retry" @click="load">站点加载失败，重试</button>
  </div>
</template>
<style scoped>
.location-filters{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin-top:10px}.location-filters select{min-width:0;width:100%;min-height:44px;padding:8px 4px;border:0;background:var(--surface);font-size:13px;color:inherit}.retry{grid-column:1/-1;text-align:left;border:0;background:none;padding:4px 0;font-size:12px;color:#555}@media(max-width:359px){.location-filters{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
