<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from './AppIcon.vue'
import AppSheet from './AppSheet.vue'
withDefaults(defineProps<{ title?: string; back?: boolean; avatar?: string; hideAvatar?: boolean; heading?: boolean }>(), { title: 'RentPilot', back: false })
const router = useRouter()
const about = ref(false)
function goBack() { if (window.history.state?.back) router.back(); else router.push('/listings') }
</script>
<template>
  <header class="app-header">
    <div class="row header-title"><button v-if="back" class="icon-button back-button" aria-label="返回" @click="goBack"><AppIcon name="arrow-left" /></button><svg v-else-if="title === 'RentPilot'" class="brand-mark" width="27" height="30" viewBox="0 0 27 30" fill="none" aria-hidden="true"><path d="M3 13 13.5 4 24 13v12H3V13Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="m10 20 8-8-2 9-3-3-3 2Z" fill="currentColor"/></svg><h1 v-if="heading" class="header-heading">{{ title }}</h1><span v-else :class="{ brand: title === 'RentPilot' }">{{ title }}</span></div>
    <div class="row header-actions"><slot /><button class="demo-indicator" aria-label="演示说明" @click="about = true">演示</button><RouterLink v-if="!back && !hideAvatar" to="/profile" class="avatar" aria-label="个人中心"><AppIcon name="user" :size="23" /></RouterLink></div>
  </header>
  <AppSheet v-model="about" title="关于这个演示"><div class="stack"><p>RentPilot 帮你一起找到合适的租住选择。</p><p class="muted">当前查询的房源为模拟数据，图片为室内示意，不代表真实在租。</p><p class="muted">地图由高德提供，房源坐标仅供小区位置参考。联系功能用于演示账号之间协商。</p><p class="muted">AI 找房使用模拟房源数据；居住方案为功能演示，原有本机记录继续保留。</p></div><template #footer><button class="primary" @click="about = false">知道了</button></template></AppSheet>
</template>
