import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/http'
import { getLifesim, saveLifesim, type LifesimAnswers, type LifesimView } from '../services/lifesim'
import { useMemberStore } from './member'

export const useLifesimStore = defineStore('lifesim', () => {
  const member = useMemberStore()
  const view = ref<LifesimView | null>(null)
  const loading = ref(false)
  const saving = ref(false)
  const error = ref('')
  let generation = 0

  function clear() {
    generation++
    view.value = null
    loading.value = false
    saving.value = false
    error.value = ''
  }
  function fail(cause: unknown, fallback: string) {
    if (cause instanceof ApiError && cause.status === 401) {
      member.clear()
      error.value = '登录已失效，请重新登录'
      return
    }
    error.value = cause instanceof Error && cause.message ? cause.message : fallback
  }
  async function load(planId: string) {
    if (!member.token) { clear(); return null }
    const token = member.token, version = ++generation
    loading.value = true
    error.value = ''
    try {
      const result = await getLifesim(token, planId)
      if (token === member.token && version === generation) view.value = result
      return result
    } catch (cause) {
      if (token === member.token && version === generation) fail(cause, '生活账本加载失败，请重试')
      return null
    } finally {
      if (version === generation) loading.value = false
    }
  }
  async function save(planId: string, answers: LifesimAnswers) {
    if (!member.token) throw new ApiError('请先登录', 401)
    saving.value = true
    error.value = ''
    try {
      const result = await saveLifesim(member.token, planId, answers)
      view.value = result
      return result
    } catch (cause) {
      fail(cause, '生活账本保存失败，请重试')
      throw cause
    } finally {
      saving.value = false
    }
  }

  watch(() => member.token, () => { clear() }, { flush: 'sync' })
  return { view, loading, saving, error, load, save, clear }
})