<script setup lang="ts">
import AppIcon from './AppIcon.vue'
defineProps<{ items: { label: string; value: string | number; unit?: string; hint?: string; icon?: string }[] }>()
</script>
<template><div class="metric-grid"><div v-for="item in items" :key="item.label" class="metric-card"><div class="between muted small"><span>{{ item.label }}</span><AppIcon v-if="item.icon" :name="item.icon" :size="16" /></div><div class="metric-value">{{ item.value }}<span v-if="item.unit">{{ item.unit }}</span></div><p v-if="item.hint" class="muted tiny">{{ item.hint }}</p></div></div></template>
