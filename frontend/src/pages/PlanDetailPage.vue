<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppHeader from '../components/AppHeader.vue'
import AppIcon from '../components/AppIcon.vue'
import MemberGate from '../components/MemberGate.vue'
import { usePlansStore } from '../stores/plans'
import { useMemberStore } from '../stores/member'
import type { PlanSnapshot } from '../services/plans'

const route = useRoute()
const router = useRouter()
const store = usePlansStore()
const member = useMemberStore()
const notice = ref('')
const confirmDelete = ref(false)
const planId = String(route.params.id || '')
const snapshot = computed(() => store.detail?.snapshot ?? null)
const detail = computed(() => store.detail)
const modeLabel: Record<string, string> = { WALKING: '步行', CYCLING: '骑行', TRANSIT: '公共交通', DRIVING: '驾车' }
onMounted(() => { if (member.loggedIn) void store.open(planId) })
function money(value: number | null | undefined) { return value == null ? '未知' : `¥${Math.round(value).toLocaleString('zh-CN')}` }
function captured(value: string) { return new Date(value).toLocaleString('zh-CN', { year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }
function criteriaText(snapshot: PlanSnapshot) {
  const c = snapshot.criteria
  const parts: string[] = []
  if (typeof c.maxRent === 'number') parts.push(`预算 ${money(c.maxRent)}/月`)
  if (Array.isArray(c.bedrooms)) { if (c.bedrooms.length) parts.push(`${c.bedrooms.join('、')} 居`) }
  else if (typeof c.bedrooms === 'number') parts.push(`${c.bedrooms} 居`)
  for (const key of ['district', 'town', 'rentalType'] as const) if (typeof c[key] === 'string' && c[key]) parts.push(String(c[key]))
  const commute = snapshot.commute
  if (commute) parts.push(`${modeLabel[commute.mode] ?? commute.mode}通勤${commute.maxMinutes == null ? '' : ` ≤ ${commute.maxMinutes} 分钟`}`)
  return parts.length ? parts.join(' · ') : '按已保存的条件检索'
}
async function restore(versionId: string, versionNo: number) {
  try { await store.activate(planId, versionId); notice.value = `已恢复第 ${versionNo} 版` } catch { /* store surfaces the error */ }
}
async function remove() {
  if (!confirmDelete.value) { confirmDelete.value = true; return }
  try { await store.remove(planId); await router.push('/plans') } catch { confirmDelete.value = false }
}
</script>

<template>
  <AppHeader title="方案详情" back />
  <main v-if="!member.loggedIn && !member.loading" class="page stack"><MemberGate title="登录，查看你的居住方案" /></main>
  <main v-else-if="store.loading && !detail" class="page stack"><p class="rental-loading" role="status">正在加载方案…</p></main>
  <main v-else-if="store.error && !detail" class="page empty-fallback"><AppIcon name="help" :size="34" /><h1>无法打开这个方案</h1><p class="muted">{{ store.error }}</p><RouterLink to="/plans" class="primary">返回我的方案<AppIcon name="arrow-right" /></RouterLink></main>
  <main v-else-if="detail && snapshot" class="page detail-page stack">
    <p class="snapshot-label">历史快照 · 保存于 {{ captured(snapshot.capturedAt) }} · 不会自动更新</p>
    <section class="detail-intro"><div class="between"><h1>{{ detail.plan.title }}</h1><span class="badge">{{ detail.plan.coverage === 'PARTIAL' ? '部分核验' : '已核验' }}</span></div><p class="small muted">{{ criteriaText(snapshot) }}</p></section>

    <div v-if="snapshot.commute" class="signature-bar"><div class="signature-track"><i :style="{ width: `${Math.min(100, Math.round(snapshot.commute.checkedCount / Math.max(1, snapshot.commute.candidateCount) * 100))}%` }"></i></div><p>已核验 {{ snapshot.commute.checkedCount }} / {{ snapshot.commute.candidateCount }} 个候选房源 · 未核验 {{ snapshot.commute.unverifiedCount }}</p></div>
    <section v-if="snapshot.commute" class="fact-block">
      <h2>通勤核验范围</h2>
      <dl class="ledger"><div><dt>工作地点</dt><dd>{{ snapshot.commute.workplace?.name || '未记录' }}</dd></div><div><dt>方式</dt><dd>{{ modeLabel[snapshot.commute.mode] || snapshot.commute.mode }}</dd></div><div><dt>已核验候选</dt><dd>{{ snapshot.commute.checkedCount }} / {{ snapshot.commute.candidateCount }}（未核验 {{ snapshot.commute.unverifiedCount }}）</dd></div></dl>
      <p class="note">{{ snapshot.commute.coverage === 'PARTIAL' ? '只核验了部分候选房源，未核验不代表不符合。' : '当前候选范围内已完成核验。' }}路线为小区参考坐标到工作地点的预计值，不保证门到门或高峰耗时。</p>
    </section>

    <section class="detail-homes stack"><h2>{{ snapshot.recommendations.length }} 套房源</h2>
      <article v-for="home in snapshot.recommendations" :key="home.listing.id" class="home-card">
        <div class="between"><h3>{{ home.listing.community || home.listing.name }}</h3><strong>{{ money(home.listing.rent) }}<small>/月</small></strong></div>
        <p class="tiny muted">{{ home.listing.district }} · {{ home.listing.town }} · {{ home.listing.layout || `${home.listing.bedrooms} 居` }} · {{ home.listing.area }}㎡</p>
        <p class="home-commute">{{ home.commute ? `预计${modeLabel[home.commute.mode] || home.commute.mode} ${home.commute.minutes} 分钟 · ${(home.commute.distanceMeters / 1000).toFixed(1)} 公里` : '通勤未核验' }}</p>
        <ul v-if="home.reasons.length" class="reasons"><li v-for="reason in home.reasons" :key="reason">{{ reason }}</li></ul>
        <dl v-if="home.insight" class="ledger compact"><div><dt>押金</dt><dd>{{ money(home.insight.costs.deposit) }}</dd></div><div><dt>付款方式</dt><dd>{{ home.insight.costs.paymentMethod || '未知' }}</dd></div><div><dt>物业费</dt><dd>{{ home.insight.costs.propertyFee == null ? '未知（周期未注明）' : `${money(home.insight.costs.propertyFee)} · 周期未知` }}</dd></div><div><dt>首期租金 + 押金</dt><dd>{{ money(home.insight.costs.knownInitialPayment) }}</dd></div></dl>
        <RouterLink :to="`/listing/${home.listing.id}`" class="text-action">查看房源资料<AppIcon name="chevron-right" :size="15" /></RouterLink>
      </article>
    </section>

    <section class="fact-block"><h2>数据来源与限制</h2><p class="small muted">数据集 {{ snapshot.source.id }}，共 {{ snapshot.source.rowCount }} 条模拟房源。快照保留原始校验值，便于核对。</p><ul class="limitations"><li v-for="limit in snapshot.limitations" :key="limit">{{ limit }}</li></ul></section>

    <section class="fact-block"><h2>历史版本 <span class="tiny muted">共 {{ detail.versions.length }} 个</span></h2><ul class="version-list"><li v-for="version in detail.versions" :key="version.id"><div><strong>第 {{ version.versionNo }} 版</strong><span class="tiny muted">{{ captured(version.capturedAt) }} · {{ version.homeCount }} 套 · {{ version.coverage === 'PARTIAL' ? '部分核验' : '已核验' }}</span></div><button v-if="version.id !== detail.plan.versionId" class="text-action" :disabled="store.busy" @click="restore(version.id, version.versionNo)">恢复</button><span v-else class="badge">当前</span></li></ul></section>

    <p v-if="notice" class="notice" role="status">{{ notice }}</p>
    <RouterLink :to="`/lifesim/${detail.plan.id}`" class="lifesim-entry"><AppIcon name="sun" :size="20" /><span><strong>看看你的一天</strong><small>按已核验的通勤，排一份时间账本</small></span><AppIcon name="chevron-right" :size="18" /></RouterLink>
    <div class="action-bar detail-actions"><RouterLink :to="`/compare?left=${detail.plan.id}`" class="secondary">与另一方案对比</RouterLink><button class="secondary danger" :disabled="store.busy" @click="remove">{{ confirmDelete ? '确认删除方案' : '删除方案' }}</button></div>
  </main>
</template>

<style scoped>
.detail-page{padding-top:10px;gap:24px}.snapshot-label{font-size:12px;background:var(--surface);padding:10px 14px;border-radius:10px;line-height:1.7}.detail-intro h1{margin:10px 0 8px;font-size:26px;line-height:1.5;overflow-wrap:anywhere}.fact-block{display:grid;gap:12px}.fact-block h2,.detail-homes h2{font-size:18px}.ledger{margin:0;padding:14px 16px;background:var(--surface);border-radius:14px;display:grid;gap:9px}.ledger div{display:flex;justify-content:space-between;gap:16px;font-size:13px}.ledger dt{color:var(--muted)}.ledger dd{text-align:right;margin:0}.ledger.compact{padding:12px 0;background:transparent;border-top:1px solid var(--line);border-radius:0;margin-top:10px}.note,.small{font-size:12px;line-height:1.75}.note{color:var(--muted)}.home-card{border:1px solid var(--line);border-radius:16px;padding:16px;display:grid;gap:8px}.home-card h3{font-size:15px;line-height:1.5}.home-card strong{font-size:19px;font-weight:600;white-space:nowrap}.home-card strong small{font-size:11px;color:var(--muted);font-weight:400}.home-commute{font-size:13px}.reasons{list-style:none;margin:0;padding:0;display:grid;gap:4px}.reasons li{font-size:12px;color:var(--muted);line-height:1.6}.limitations{margin:0;padding-left:18px;display:grid;gap:6px;font-size:12px;color:var(--muted);line-height:1.7}.version-list{list-style:none;margin:0;padding:0;display:grid}.version-list li{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 0;border-bottom:1px solid var(--line)}.version-list li>div{display:grid;gap:3px;min-width:0}.version-list strong{font-size:13px;font-weight:500}.version-list .text-action{min-height:44px}.notice{font-size:13px;background:var(--surface);padding:10px 14px;border-radius:10px}.detail-actions{gap:10px}.detail-actions .secondary{flex:1;min-height:46px;font-size:13px}.detail-actions .danger{color:#8a4a4a}.empty-fallback{align-items:flex-start}
.lifesim-entry{display:flex;align-items:center;gap:12px;padding:16px;border:1px solid var(--line);border-radius:16px;background:var(--surface)}.lifesim-entry>span{flex:1;display:grid;gap:3px}.lifesim-entry strong{font-size:14px;font-weight:500}.lifesim-entry small{font-size:11px;color:var(--muted)}.lifesim-entry>svg:last-child{color:var(--muted)}


</style>