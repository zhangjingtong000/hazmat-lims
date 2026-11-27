<template>
  <section class="rule-wall" data-testid="rule-wall">
    <div class="wall-head">
      <div class="wall-head-main">
        <div class="eyebrow">RULE COUNTER-EXAMPLES · 规则反例墙</div>
        <div class="wall-title">系统会拒绝什么</div>
        <div class="wall-sub">
          每条都要先选两个对象（批次×柜位 / 柜位×签批人 / 批次×数量 / 单据×签批棒次），才会发出一次真实请求。
          这些请求必然被拒——不写业务数据，失败会进「系统 → 操作日志」留一条失败留痕，可据此核对拒绝原因。
        </div>
      </div>
      <div class="wall-legend">
        <span class="legend-dot" /> 只发失败请求 · 不产生单据与库存变动
      </div>
    </div>

    <div class="wall-grid">
      <!-- ① 禁配 4301：批次 × 目标柜位 -->
      <article class="wall-card" data-rule="4301">
        <header class="wall-card-head">
          <span class="rule-code">4301</span>
          <span class="rule-name">禁配同柜</span>
          <span class="rule-tag">GB 15603-2022 相容矩阵</span>
        </header>
        <p class="rule-desc">把一桶在库批次移到与它禁配的另一组化学品所在柜，系统按柜内既有批次成对校验后拒绝。</p>
        <div class="rule-obj">
          <label class="obj-field">
            <span class="obj-label">对象 A · 在库批次</span>
            <el-select v-model="c1.batchId" size="small" filterable placeholder="选批次" @change="resetOf(c1)">
              <el-option v-for="b in stockBatches" :key="b.id"
                :label="`${b.lotNo} · ${b.chemicalName} · 余 ${b.qtyLeft}`" :value="b.id" />
            </el-select>
          </label>
          <span class="obj-x">×</span>
          <label class="obj-field">
            <span class="obj-label">对象 B · 目标柜位</span>
            <el-select v-model="c1.cabinetId" size="small" filterable placeholder="选柜位" @change="resetOf(c1)">
              <el-option v-for="c in otherCabinets(c1)" :key="c.id" :label="cabLabel(c)" :value="c.id" />
            </el-select>
          </label>
        </div>
        <div class="rule-verdict" :class="c1Verdict">
          <template v-if="!c1Ready">选齐批次与目标柜位后判定</template>
          <template v-else-if="c1Hit">判定：冲突 —— {{ c1Hit.note }}（柜内已有 {{ c1Hit.mate }}）</template>
          <template v-else>判定：该组合不冲突，换一个柜位或批次</template>
        </div>
        <div class="rule-actions">
          <el-button size="small" type="danger" plain :disabled="!c1CanFire" :loading="c1.loading" @click="fire1">触发反例</el-button>
          <span class="rule-perm" v-if="!canMove">需 hzm:batch:move（li / admin）</span>
        </div>
        <div v-if="c1.result" class="rule-result" :class="c1.result.ok ? 'unexpected' : 'expected'">
          后端返回 <b>{{ c1.result.code }}</b> · {{ c1.result.message }}
          <span class="result-flag">{{ c1.result.ok ? '未按预期拒绝' : '符合预期（已拒绝）' }}</span>
        </div>
      </article>

      <!-- ② 双签同人 4201：柜位 × 签批人 -->
      <article class="wall-card" data-rule="4201">
        <header class="wall-card-head">
          <span class="rule-code">4201</span>
          <span class="rule-name">双签同人</span>
          <span class="rule-tag">五双制度 · 双人复核</span>
        </header>
        <p class="rule-desc">入库登记人与复核人填成同一个人，系统拒绝——"五双"要求收发双方分离。</p>
        <div class="rule-obj">
          <label class="obj-field">
            <span class="obj-label">对象 A · 入库柜位</span>
            <el-select v-model="c2.cabinetId" size="small" filterable placeholder="选柜位" @change="resetOf(c2)">
              <el-option v-for="c in cabinets" :key="c.id" :label="cabLabel(c)" :value="c.id" />
            </el-select>
          </label>
          <span class="obj-x">×</span>
          <label class="obj-field">
            <span class="obj-label">对象 B · 复核签批人</span>
            <el-select v-model="c2.checkerId" size="small" filterable placeholder="选签批人" @change="resetOf(c2)">
              <el-option :label="`本人 · ${myName}`" :value="myId" />
            </el-select>
          </label>
        </div>
        <div class="rule-verdict" :class="c2Verdict">
          <template v-if="!c2Ready">选齐柜位与签批人后判定</template>
          <template v-else-if="c2Same">判定：复核人＝登记人（本人），必然被拒</template>
          <template v-else>判定：复核人不是本人，这一组合不会触发同人拦截</template>
        </div>
        <div class="rule-actions">
          <el-button size="small" type="danger" plain :disabled="!c2CanFire" :loading="c2.loading" @click="fire2">触发反例</el-button>
          <span class="rule-perm" v-if="!canStockIn">需 hzm:stockin:add（li / admin）</span>
        </div>
        <div v-if="c2.result" class="rule-result" :class="c2.result.ok ? 'unexpected' : 'expected'">
          后端返回 <b>{{ c2.result.code }}</b> · {{ c2.result.message }}
          <span class="result-flag">{{ c2.result.ok ? '未按预期拒绝' : '符合预期（已拒绝）' }}</span>
        </div>
      </article>

      <!-- ③ 超量 4501：批次 × 数量 -->
      <article class="wall-card" data-rule="4501">
        <header class="wall-card-head">
          <span class="rule-code">4501</span>
          <span class="rule-name">超量出库</span>
          <span class="rule-tag">数量守卫 · 领用发放同码</span>
        </header>
        <p class="rule-desc">处置量填得比批次余量还多，系统拒绝；领用发放时"余量不足"用的也是 4501 这道数量守卫。</p>
        <div class="rule-obj">
          <label class="obj-field">
            <span class="obj-label">对象 A · 在库批次</span>
            <el-select v-model="c3.batchId" size="small" filterable placeholder="选批次" @change="pickC3">
              <el-option v-for="b in stockBatches" :key="b.id"
                :label="`${b.lotNo} · ${b.chemicalName} · 余 ${b.qtyLeft}`" :value="b.id" />
            </el-select>
          </label>
          <span class="obj-x">×</span>
          <label class="obj-field">
            <span class="obj-label">对象 B · 处置数量</span>
            <el-input v-model.number="c3.qty" size="small" type="number" :min="0" @input="resetOf(c3)">
              <template #suffix><span class="qty-unit">超余量</span></template>
            </el-input>
          </label>
        </div>
        <div class="rule-verdict" :class="c3Verdict">
          <template v-if="!c3Ready">选批次并填数量后判定</template>
          <template v-else-if="c3Over">判定：{{ c3.qty }} &gt; 批次余量 {{ c3Left }}，必然被拒</template>
          <template v-else>判定：{{ c3.qty }} ≤ 批次余量 {{ c3Left }}，不会触发超量拦截</template>
        </div>
        <div class="rule-actions">
          <el-button size="small" type="danger" plain :disabled="!c3CanFire" :loading="c3.loading" @click="fire3">触发反例</el-button>
          <span class="rule-perm" v-if="!canWaste">需 hzm:waste:submit（li / admin）</span>
        </div>
        <div v-if="c3.result" class="rule-result" :class="c3.result.ok ? 'unexpected' : 'expected'">
          后端返回 <b>{{ c3.result.code }}</b> · {{ c3.result.message }}
          <span class="result-flag">{{ c3.result.ok ? '未按预期拒绝' : '符合预期（已拒绝）' }}</span>
        </div>
      </article>

      <!-- ④ 跳级审批 4101：单据 × 签批棒次 -->
      <article class="wall-card" data-rule="4101">
        <header class="wall-card-head">
          <span class="rule-code">4101</span>
          <span class="rule-name">跳级审批</span>
          <span class="rule-tag">流转状态守卫</span>
        </header>
        <p class="rule-desc">越过前面的棒次直接去审一张还没进入审批的单据（草稿/已批/驳回），状态守卫先拦下。</p>
        <div class="rule-obj">
          <label class="obj-field">
            <span class="obj-label">对象 A · 非审批中单据</span>
            <el-select v-model="c4.purchaseId" size="small" filterable placeholder="选单据" @change="resetOf(c4)">
              <el-option v-for="p in idlePurchases" :key="p.id"
                :label="`${p.code} · ${p.chemicalName} · ${p.statusLabel}`" :value="p.id" />
            </el-select>
          </label>
          <span class="obj-x">×</span>
          <label class="obj-field">
            <span class="obj-label">对象 B · 拟越级签批棒次</span>
            <el-select v-model="c4.leg" size="small" placeholder="选棒次" @change="resetOf(c4)">
              <el-option v-for="l in legOptions" :key="l.value" :label="l.label" :value="l.value" />
            </el-select>
          </label>
        </div>
        <div class="rule-verdict" :class="c4Verdict">
          <template v-if="!idlePurchases.length">当前没有可构造的单据（全部处于审批中），本条暂不可用</template>
          <template v-else-if="!c4Ready">选齐单据与棒次后判定</template>
          <template v-else>判定：单据 {{ c4Code }} 不在审批中，审批请求必然被拒</template>
        </div>
        <div class="rule-actions">
          <el-button size="small" type="danger" plain :disabled="!c4CanFire" :loading="c4.loading" @click="fire4">触发反例</el-button>
          <span class="rule-perm">登录即可触发（无静态权限门槛）</span>
        </div>
        <div v-if="c4.result" class="rule-result" :class="c4.result.ok ? 'unexpected' : 'expected'">
          后端返回 <b>{{ c4.result.code }}</b> · {{ c4.result.message }}
          <span class="result-flag">{{ c4.result.ok ? '未按预期拒绝' : '符合预期（已拒绝）' }}</span>
        </div>
      </article>
    </div>

    <div class="wall-foot">
      每条对应后端真实守卫（<span class="mono">BizException</span> 抛出点）；冲突判定在浏览器侧按当前数据实时计算，不写死。
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { auth, hasPerm } from '../store/auth'
import { listCabinet, listBatch, listChemInfo, listCompat, listPurchase } from '../api/chem'
import { PURCHASE_STATUS } from '../utils/constants'

/**
 * 规则反例墙（引导方案 6）：必须先构造两个对象才可触发。
 * 四类守卫：禁配 4301（批次×柜位）、双签同人 4201（柜位×签批人）、
 * 超量 4501（批次×数量）、跳级审批 4101（单据×棒次）。
 * 请求走独立 axios 实例：不弹全局 toast、不触发 401 跳转，结果只在本卡展示。
 */
const probe = axios.create({ timeout: 15000 })
probe.interceptors.request.use(cfg => {
  const token = sessionStorage.getItem('token')
  if (token) cfg.headers.Authorization = 'Bearer ' + token
  return cfg
})

const cabinets = ref([])
const batches = ref([])
const chems = ref([])
const rules = ref([])
const purchases = ref([])

const myId = computed(() => Number(auth.user?.id || 0))
const myName = computed(() => auth.user?.nickname || auth.user?.username || '当前账号')
const canMove = computed(() => hasPerm('hzm:batch:move'))
const canStockIn = computed(() => hasPerm('hzm:stockin:add'))
const canWaste = computed(() => hasPerm('hzm:waste:submit'))

const stockBatches = computed(() => batches.value.filter(b => Number(b.status) === 0 && Number(b.qtyLeft) > 0))
const idlePurchases = computed(() => purchases.value.filter(p => Number(p.status) !== 1))
const legOptions = [
  { value: 2, label: '第 2 棒 · 导师' },
  { value: 3, label: '第 3 棒 · 安全员' },
  { value: 4, label: '第 4 棒 · 学院' },
  { value: 5, label: '第 5 棒 · 校级' }
]

function cabLabel(c) {
  return c.code ? `${c.code} · ${c.name || ''}` : (c.name || ('柜位 #' + c.id))
}
function groupsOf(c) {
  return [c?.compatGroup, c?.compatGroup2].filter(Boolean)
}
const ruleMap = computed(() => {
  const m = {}
  for (const r of rules.value) {
    if (r?.groupA && r?.groupB) m[`${r.groupA}|${r.groupB}`] = r
  }
  return m
})
function matesOf(cabinetId) {
  const ids = new Set(batches.value
    .filter(b => Number(b.status) === 0 && Number(b.cabinetId) === Number(cabinetId))
    .map(b => Number(b.chemicalId)))
  return chems.value.filter(c => ids.has(Number(c.id)))
}
function conflictOf(cabinetId, chem) {
  if (!cabinetId || !chem) return null
  for (const mate of matesOf(cabinetId)) {
    if (Number(mate.id) === Number(chem.id)) continue
    for (const mg of groupsOf(mate)) {
      for (const cg of groupsOf(chem)) {
        const hit = ruleMap.value[`${mg}|${cg}`] || ruleMap.value[`${cg}|${mg}`]
        if (hit) return { note: hit.note || `${mg} × ${cg}`, mate: mate.name }
      }
    }
  }
  return null
}
function resetOf(card) { card.result = null }
function otherCabinets(card) {
  const b = stockBatches.value.find(x => x.id === card.batchId)
  return cabinets.value.filter(c => !b || Number(c.id) !== Number(b.cabinetId))
}

const c1 = reactive({ batchId: null, cabinetId: null, loading: false, result: null })
const c1Batch = computed(() => stockBatches.value.find(b => b.id === c1.batchId))
const c1Chem = computed(() => chems.value.find(c => Number(c.id) === Number(c1Batch.value?.chemicalId)))
const c1Ready = computed(() => !!c1Batch.value && !!c1.cabinetId)
const c1Hit = computed(() => (c1Ready.value ? conflictOf(c1.cabinetId, c1Chem.value) : null))
const c1Verdict = computed(() => (c1Hit.value ? 'conflict' : (c1Ready.value ? 'no' : 'idle')))
const c1CanFire = computed(() => c1Ready.value && !!c1Hit.value && canMove.value)

const c2 = reactive({ cabinetId: null, checkerId: null, loading: false, result: null })
const c2Ready = computed(() => !!c2.cabinetId && !!c2.checkerId)
const c2Same = computed(() => c2Ready.value && Number(c2.checkerId) === myId.value)
const c2Verdict = computed(() => (c2Same.value ? 'conflict' : (c2Ready.value ? 'no' : 'idle')))
const c2CanFire = computed(() => c2Same.value && chems.value.length > 0 && canStockIn.value)

const c3 = reactive({ batchId: null, qty: null, loading: false, result: null })
const c3Batch = computed(() => stockBatches.value.find(b => b.id === c3.batchId))
const c3Left = computed(() => Number(c3Batch.value?.qtyLeft || 0))
const c3Ready = computed(() => !!c3Batch.value && c3.qty != null && Number(c3.qty) > 0)
const c3Over = computed(() => c3Ready.value && Number(c3.qty) > c3Left.value)
const c3Verdict = computed(() => (c3Over.value ? 'conflict' : (c3Ready.value ? 'no' : 'idle')))
const c3CanFire = computed(() => c3Over.value && canWaste.value)
function pickC3() {
  if (c3Batch.value) c3.qty = c3Left.value + 1
  c3.result = null
}

const c4 = reactive({ purchaseId: null, leg: null, loading: false, result: null })
const c4Code = computed(() => idlePurchases.value.find(p => p.id === c4.purchaseId)?.code || '—')
const c4Ready = computed(() => !!idlePurchases.value.find(p => p.id === c4.purchaseId) && !!c4.leg)
const c4Verdict = computed(() => (!idlePurchases.value.length ? 'off' : (c4Ready.value ? 'conflict' : 'idle')))
const c4CanFire = computed(() => c4Ready.value)

async function fire1() {
  c1.loading = true
  try {
    c1.result = await fire('put', `/chem/batch/${c1.batchId}/cabinet`, { cabinetId: c1.cabinetId })
  } finally { c1.loading = false }
}
async function fire2() {
  const chem = chems.value[0]
  c2.loading = true
  try {
    c2.result = await fire('post', '/chem/stockin', {
      chemicalId: chem?.id,
      qty: 1,
      lotNo: 'ONB-' + Date.now().toString().slice(-8),
      cabinetId: c2.cabinetId,
      checkerId: c2.checkerId,
      supplier: '上手引导反例（不落库）'
    })
  } finally { c2.loading = false }
}
async function fire3() {
  const b = c3Batch.value
  c3.loading = true
  try {
    c3.result = await fire('post', '/chem/waste', {
      chemicalId: b?.chemicalId,
      batchId: b?.id,
      type: 1,
      qty: Number(c3.qty),
      method: '上手引导反例（不落库）'
    })
  } finally { c3.loading = false }
}
async function fire4() {
  c4.loading = true
  try {
    c4.result = await fire('post', `/chem/purchase/${c4.purchaseId}/audit`, {
      approve: true,
      comment: '【上手引导·反例】跳级审批（不落库）'
    })
  } finally { c4.loading = false }
}

/* 统一探测请求：200+业务码 与 4xx 都归一到 {ok, code, message} */
async function fire(method, url, data) {
  try {
    const resp = await probe({ method, url, data })
    const body = resp?.data || {}
    const code = body.code == null ? 0 : body.code
    return { ok: code === 0, code, message: body.message || '（无消息）' }
  } catch (e) {
    const r = e.response
    if (r?.data) {
      const code = r.data.code == null ? r.status : r.data.code
      return { ok: code === 0, code, message: r.data.message || `HTTP ${r.status}` }
    }
    return { ok: false, code: -1, message: e.message || '请求失败' }
  }
}

onMounted(async () => {
  const [cab, bat, chem, rule, pur] = await Promise.all([
    listCabinet().catch(() => []),
    listBatch({ page: 1, size: 200 }).catch(() => null),
    listChemInfo({ page: 1, size: 200 }).catch(() => null),
    listCompat().catch(() => []),
    listPurchase({ page: 1, size: 100 }).catch(() => null)
  ])
  cabinets.value = cab || []
  batches.value = bat?.records || []
  chems.value = chem?.records || []
  rules.value = rule || []
  purchases.value = (pur?.records || []).map(p => ({
    ...p,
    statusLabel: PURCHASE_STATUS[p.status]?.label || '—'
  }))
})
</script>

<style scoped>
.rule-wall {
  margin-top: 14px;
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--r-lg);
  padding: 16px 18px 14px;
}
.wall-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 12px; }
.wall-head-main { min-width: 0; }
.wall-title { font-size: 1.06rem; font-weight: 800; color: var(--c-graphite-deep); margin-top: 2px; }
.wall-sub { font-size: 0.74rem; line-height: 1.6; color: var(--c-graphite-soft); margin-top: 4px; max-width: 900px; }
.wall-legend {
  flex: none; display: inline-flex; align-items: center; gap: 6px;
  font-size: 0.66rem; font-weight: 700; color: var(--c-safe-deep);
  background: var(--c-safe-bg); border: 1px solid var(--c-safe-line);
  border-radius: 999px; padding: 3px 10px; white-space: nowrap;
}
.legend-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--c-safe); }
.wall-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.wall-card {
  border: 1px solid var(--c-line);
  border-top: 2px solid var(--c-graphite-deep);
  border-radius: var(--r);
  background: #FCFCFB;
  padding: 11px 12px 10px;
}
.wall-card[data-rule="4301"], .wall-card[data-rule="4501"] { border-top-color: var(--c-safe); }
.wall-card-head { display: flex; align-items: center; gap: 7px; min-width: 0; }
.rule-code {
  font-family: var(--f-mono); font-size: 0.68rem; font-weight: 800;
  color: #fff; background: var(--c-danger); border-radius: 3px; padding: 1px 6px;
}
.rule-name { font-size: 0.9rem; font-weight: 800; color: var(--c-graphite-deep); white-space: nowrap; }
.rule-tag { font-size: 0.62rem; color: var(--c-graphite-faint); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rule-desc { font-size: 0.72rem; line-height: 1.55; color: var(--c-graphite-soft); margin: 6px 0 8px; min-height: 32px; }
.rule-obj { display: flex; align-items: flex-end; gap: 6px; }
.obj-field { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.obj-label { font-size: 0.64rem; font-weight: 700; color: var(--c-graphite-faint); letter-spacing: 0.02em; }
.obj-x { flex: none; font-size: 0.8rem; color: var(--c-graphite-faint); padding-bottom: 6px; }
.qty-unit { font-size: 0.6rem; color: var(--c-graphite-faint); }
.rule-verdict {
  margin-top: 7px; font-size: 0.68rem; line-height: 1.5;
  color: var(--c-graphite-soft); background: #F5F6F8; border-radius: var(--r-sm);
  padding: 4px 7px; min-height: 24px;
}
.rule-verdict.conflict { color: var(--c-danger); background: var(--c-danger-bg); font-weight: 600; }
.rule-verdict.no { color: var(--c-graphite-soft); }
.rule-verdict.off { color: var(--c-graphite-faint); }
.rule-actions { display: flex; align-items: center; gap: 8px; margin-top: 8px; }
.rule-perm { font-size: 0.62rem; color: var(--c-graphite-faint); }
.rule-result {
  margin-top: 7px; font-size: 0.7rem; line-height: 1.5; border-radius: var(--r-sm);
  padding: 5px 8px; border-left: 3px solid var(--c-ok); background: #F6FBF7; color: var(--c-graphite);
}
.rule-result.unexpected { border-left-color: var(--c-danger); background: var(--c-danger-bg); }
.rule-result b { font-family: var(--f-mono); }
.result-flag { display: block; font-size: 0.62rem; font-weight: 700; color: var(--c-ok); margin-top: 2px; }
.rule-result.unexpected .result-flag { color: var(--c-danger); }
.wall-foot { margin-top: 10px; font-size: 0.66rem; color: var(--c-graphite-faint); }
.mono { font-family: var(--f-mono); }
@media (max-width: 1100px) {
  .wall-grid { grid-template-columns: 1fr; }
  .wall-head { flex-direction: column; gap: 8px; }
}
</style>
