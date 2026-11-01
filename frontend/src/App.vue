<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Capacitor, type PluginListenerHandle } from '@capacitor/core'
import { App as NativeApp } from '@capacitor/app'
import { Keyboard } from '@capacitor/keyboard'
import { usePilotStore } from './stores/pilot'
import AppIcon from './components/AppIcon.vue'
import {useMemberStore} from './stores/member'

const store = usePilotStore()
const member=useMemberStore()
void member.restore()
store.hydrate()
watch(() => [store.weights, store.versions, store.activeVersionId, store.activePlanId, store.savedListings, store.savedPlans, store.messages, store.conversations, store.viewings, store.nickname, store.scene, store.notifications], () => store.persist(), { deep: true })
const route = useRoute(), router = useRouter()
const keyboardOpen = ref(false)
const tabs = [{ id: 'chat', path: '/chat', label: 'AI', icon: 'sparkles' }, { id: 'map', path: '/listings', label: '找房', icon: 'search' }, { id: 'messages', path: '/messages', label: '联系', icon: 'message' }, { id: 'profile', path: '/profile', label: '我的', icon: 'user' }]
const handles: PluginListenerHandle[] = []
let lastBack = 0
let baselineHeight = window.innerHeight
function updateViewport() {
  const viewport = window.visualViewport
  const height = viewport?.height ?? window.innerHeight
  const isInput = document.activeElement?.matches('input,textarea,[contenteditable]')
  keyboardOpen.value = Boolean(isInput && baselineHeight - height > 120)
  document.documentElement.style.setProperty('--app-height', `${height}px`)
  if (!isInput) baselineHeight = Math.max(baselineHeight, window.innerHeight)
}
onMounted(async () => {
  window.addEventListener('storage',member.storageChanged)
  window.visualViewport?.addEventListener('resize', updateViewport)
  window.addEventListener('resize', updateViewport)
  document.addEventListener('focusout', updateViewport)
  updateViewport()
  if (Capacitor.isNativePlatform()) {
    handles.push(await NativeApp.addListener('backButton', ({ canGoBack }) => {
      if (keyboardOpen.value) { Keyboard.hide(); return }
      const dialogs = document.querySelectorAll<HTMLDialogElement>('dialog[open]')
      if (dialogs.length) { dialogs[dialogs.length - 1]?.close(); return }
      if (canGoBack && window.history.state?.back) router.back()
      else if (route.path !== '/chat') router.replace('/chat')
      else {
        const now = Date.now()
        if (now - lastBack < 2000) NativeApp.exitApp()
        else { lastBack = now; store.notify('再按一次返回键退出') }
      }
    }))
    handles.push(await Keyboard.addListener('keyboardDidShow', () => { keyboardOpen.value = true }))
    handles.push(await Keyboard.addListener('keyboardDidHide', () => { keyboardOpen.value = false }))
  }
})
onUnmounted(() => {
  window.removeEventListener('storage',member.storageChanged)
  window.visualViewport?.removeEventListener('resize', updateViewport)
  window.removeEventListener('resize', updateViewport)
  document.removeEventListener('focusout', updateViewport)
  handles.forEach(h => h.remove())
})
</script>
<template><div class="app-shell" :class="{ 'keyboard-open': keyboardOpen }"><RouterView v-slot="{ Component }"><KeepAlive include="CatalogPage"><component :is="Component" /></KeepAlive></RouterView><nav v-if="!route.meta.hideNav" class="bottom-nav" aria-label="主导航"><RouterLink v-for="tab in tabs" :key="tab.id" :to="tab.id === 'map' && route.path === '/listings' ? route.fullPath : tab.path" :class="{ active: route.meta.tab === tab.id }" :aria-current="route.meta.tab === tab.id ? 'page' : undefined"><AppIcon :name="tab.icon" :size="21" /><span>{{ tab.label }}</span></RouterLink></nav><Transition name="route-fade"><div v-if="store.toast" class="toast" role="status">{{ store.toast }}</div></Transition></div></template>
