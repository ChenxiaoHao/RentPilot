<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps<{ modelValue: boolean; title: string; description?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const dialog = ref<HTMLDialogElement>()
const id = useId()
let opener: HTMLElement | null = null
let previousOverflow = ''
let locked = false
function release() {
  if (!locked) return
  document.body.style.overflow = previousOverflow
  locked = false
  if (opener?.isConnected) opener.focus({ preventScroll: true })
}
function close() { emit('update:modelValue', false) }
function closed() { release(); close() }
watch(() => props.modelValue, async open => {
  await nextTick()
  if (open && !dialog.value?.open) {
    opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
    previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    locked = true
    dialog.value?.showModal()
  } else if (!open && dialog.value?.open) {
    dialog.value.close()
    release()
  }
}, { immediate: true })
onBeforeUnmount(() => { dialog.value?.close(); release() })
</script>

<template>
  <Teleport to="body">
    <dialog ref="dialog" class="app-sheet" :aria-labelledby="`${id}-title`" :aria-describedby="description ? `${id}-description` : undefined" @cancel.prevent="close" @close="closed" @click.self="close">
      <section class="sheet-panel">
        <div class="sheet-handle" aria-hidden="true"></div>
        <header class="sheet-header">
          <h2 :id="`${id}-title`">{{ title }}</h2>
          <button class="icon-button" :aria-label="`关闭${title}`" autofocus @click="close"><AppIcon name="x" /></button>
        </header>
        <div class="sheet-body"><p v-if="description" :id="`${id}-description`" class="sheet-description">{{ description }}</p><slot /></div>
        <footer v-if="$slots.footer" class="sheet-footer"><slot name="footer" /></footer>
      </section>
    </dialog>
  </Teleport>
</template>

<style scoped>
.app-sheet{position:fixed;inset:0;width:100%;height:100%;max-width:none;max-height:none;margin:0;padding:0;border:0;background:transparent;color:var(--ink);overflow:hidden}
.app-sheet[open]{display:flex;align-items:flex-end;justify-content:center}
.app-sheet::backdrop{background:#0006}
.sheet-panel{display:flex;flex-direction:column;width:min(100%,640px);max-height:calc(var(--app-height,100dvh) - max(var(--safe-top),24px));background:#fff;border-radius:26px 26px 0 0;padding:10px var(--gutter) max(var(--safe-bottom),20px);animation:sheet-in .18s ease-out;box-shadow:0 -8px 40px #0000000c;min-width:0}
.sheet-handle{width:34px;height:4px;flex-shrink:0;background:#d9d4d0;border-radius:4px;margin:0 auto 8px}
.sheet-header{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-shrink:0;padding-bottom:12px}
.sheet-header h2{font-size:20px;line-height:1.5}
.sheet-body{overflow-y:auto;overscroll-behavior:contain;min-height:0;font-size:14px;line-height:1.8;padding:0 0 8px}
.sheet-description{color:var(--muted);font-size:13px;margin-bottom:18px}
.sheet-footer{padding-top:16px;display:flex;flex-direction:column;gap:10px;flex-shrink:0;border-top:1px solid var(--line);margin-top:12px}
@keyframes sheet-in{from{opacity:0;transform:translateY(16px)}to{opacity:1;transform:translateY(0)}}
</style>
