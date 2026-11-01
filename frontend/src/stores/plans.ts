import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { ApiError } from '../services/http'
import {
  listPlans, getPlan, savePlan, activateVersion, deletePlan, comparePlans,
  type PlanSummary, type PlanDetail, type PlanComparison, type SavePlanInput,
} from '../services/plans'
import { useMemberStore } from './member'

export const usePlansStore = defineStore('plans', () => {
  const member = useMemberStore()
  const plans = ref<PlanSummary[]>([])
  const detail = ref<PlanDetail | null>(null)
  const comparison = ref<PlanComparison | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  let generation = 0

  const count = computed(() => plans.value.length)
  const hasEnoughToCompare = computed(() => plans.value.length >= 2)

  function clear() {
    generation++
    plans.value = []
    detail.value = null
    comparison.value = null
    loading.value = false
    busy.value = false
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
  async function load(force = false) {
    if (!member.token) { clear(); return }
    if (!force && plans.value.length) return
    const current = member.token, version = ++generation
    loading.value = true
    error.value = ''
    try {
      const result = await listPlans(current)
      if (current === member.token && version === generation) plans.value = result
    } catch (cause) {
      if (current === member.token && version === generation) fail(cause, '方案加载失败，请重试')
    } finally {
      if (version === generation) loading.value = false
    }
  }
  async function open(id: string) {
    if (!member.token) { clear(); return null }
    const current = member.token, version = ++generation
    loading.value = true
    error.value = ''
    try {
      const result = await getPlan(current, id)
      if (current === member.token && version === generation) detail.value = result
      return result
    } catch (cause) {
      if (current === member.token && version === generation) fail(cause, '方案加载失败，请重试')
      return null
    } finally {
      if (version === generation) loading.value = false
    }
  }
  async function save(input: SavePlanInput) {
    if (!member.token) throw new ApiError('请先登录后再保存方案', 401)
    busy.value = true
    error.value = ''
    try {
      const result = await savePlan(member.token, input)
      detail.value = result
      plans.value = [result.plan, ...plans.value.filter(plan => plan.id !== result.plan.id)]
        .sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
      return result
    } catch (cause) {
      fail(cause, '方案保存失败，请重试')
      throw cause
    } finally {
      busy.value = false
    }
  }
  async function activate(planId: string, versionId: string) {
    if (!member.token) throw new ApiError('请先登录', 401)
    busy.value = true
    try {
      const result = await activateVersion(member.token, planId, versionId)
      detail.value = result
      plans.value = plans.value.map(plan => plan.id === result.plan.id ? result.plan : plan)
      return result
    } catch (cause) {
      fail(cause, '版本恢复失败，请重试')
      throw cause
    } finally {
      busy.value = false
    }
  }
  async function remove(id: string) {
    if (!member.token) throw new ApiError('请先登录', 401)
    busy.value = true
    try {
      await deletePlan(member.token, id)
      plans.value = plans.value.filter(plan => plan.id !== id)
      if (detail.value?.plan.id === id) detail.value = null
      if (comparison.value?.left.planId === id || comparison.value?.right.planId === id) comparison.value = null
    } catch (cause) {
      fail(cause, '方案删除失败，请重试')
      throw cause
    } finally {
      busy.value = false
    }
  }
  async function compare(left: string, right: string) {
    if (!member.token) throw new ApiError('请先登录', 401)
    const current = member.token
    busy.value = true
    error.value = ''
    try {
      const result = await comparePlans(current, left, right)
      if (current === member.token) comparison.value = result
      return result
    } catch (cause) {
      if (current === member.token) fail(cause, '方案对比失败，请重试')
      throw cause
    } finally {
      busy.value = false
    }
  }

  watch(() => member.token, () => { clear() }, { flush: 'sync' })
  return { plans, detail, comparison, loading, busy, error, count, hasEnoughToCompare, load, open, save, activate, remove, compare, clear }
})