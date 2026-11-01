<script setup lang="ts">
import type { Listing } from '../data/mock'
import { money } from '../data/mock'
import { usePilotStore } from '../stores/pilot'
import AppIcon from './AppIcon.vue'
defineProps<{ home: Listing; compact?: boolean }>()
const store = usePilotStore()
function save(id: string) {
  store.toggleSaved(id)
}
</script>
<template><article class="property-card" :class="{ compact }"><RouterLink :to="`/listing/${home.id}`" class="property-main"><div class="property-photo"><img :src="home.image" :alt="`${home.name} 设计示意图`" loading="lazy" /><span class="property-mock">模拟房源</span></div><div class="property-info"><h3>{{ home.name }}</h3><p class="muted tiny">{{ home.area }}㎡ · 整租一居 · {{ home.district }}</p><p class="muted tiny">{{ home.metro }} · 通勤约 {{ home.commute }} 分钟</p><div class="tags"><span v-for="tag in home.tags" :key="tag">{{ tag }}</span></div><strong>{{ money(home.price) }}<small class="muted"> /月</small></strong></div></RouterLink><button class="save-home icon-button" :aria-label="store.savedListings.includes(home.id) ? '取消收藏房源' : '收藏房源'" :aria-pressed="store.savedListings.includes(home.id)" @click="save(home.id)"><AppIcon name="heart" :size="18" :fill="store.savedListings.includes(home.id) ? 'currentColor' : 'none'" /></button></article></template>
