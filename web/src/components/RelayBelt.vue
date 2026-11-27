<template>
  <section v-if="visible" class="relay-belt" data-testid="relay-belt" :data-mode="detailMode ? 'doc' : 'page'">
    <!-- 上排：接力轨（页面级五棒 / 单据级步骤条） -->
    <div class="belt-track">
      <div class="belt-kicker">
        <span class="kicker-code">{{ detailMode ? docCode : 'RELAY' }}</span>
        <span class="kicker-name">{{ detailMode ? docTitle + ' · 单据级' : docTitle + ' · 签批接力' }}</span>
      </div>

      <ol class="belt-legs">
        <li
          v-for="(leg, i) in track" :key="leg.key"
          class="belt-leg"
          :class="{
            'is-you': leg.you,
            'is-done': leg.done,
            'is-now': leg.now,
            'is-off': leg.off
          }"
          @click="jump(leg, i)"
        >
          <span class="leg-head">
            <span class="leg-no">{{ detailMode ? ('阶段' + (i + 1)) : ('第' + (i + 1) + '棒') }}</span>
            <span class="leg-role">{{ leg.role }}</span>
            <span v-if="leg.you" class="leg-you">你</span>
          </span>
          <span class="leg-foot">
            <span class="leg-count" :class="{ zero: !detailMode && !leg.count }">
              {{ detailMode ? leg.state : leg.count }}
            </span>
            <span class="leg-unit">{{ detailMode ? '阶段' : '待办' }}</span>
          </span>
        </li>
      </ol>
    </div>

    <!-- 下排：规程（依据 + 你的刻度） -->
    <div class="belt-regimen">
      <el-icon class="regimen-ico"><InfoFilled /></el-icon>
      <span class="regimen-text">{{ regimenText }}</span>
      <span class="belt-scale">
        <template v-if="detailMode">本单在第 {{ docStep }} 步 · 共 {{ docSteps }} 步</template>
        <template v-else-if="isAdmin">管理员 · 全棒可代签</template>
        <template v-else>你 · 第 {{ youLeg }} 棒 / 5 · 剩余 {{ remain }} 棒</template>
      </span>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import { auth } from '../store/auth'
import { listPurchase, listReq, listStockIn, listWaste, reqDetail, wasteDetail, checkDetail } from '../api/chem'

/**
 * 流程带（引导方案 1 + 3 的合并形态）：
 *  - 页面级：按签批棒次分段的接力轨（第 1 棒申请人 → 第 5 棒校级），每段挂实时待办数；
 *  - 单据级：打开具体单据时下钻为该单的阶段步骤条（页面级 → 单据级两层）；
 *  - 双层：带在上（轨），规程在下（依据 + 你排第几棒、还剩几棒）。
 * 只在单据类页面出现（台账/基础数据/系统页不出现）。
 */
const route = useRoute()
const router = useRouter()

/* 单据类页面白名单：列表页走页面级，详情页下钻单据级 */
const DOC_PAGES = {
  '/chem/purchase': { title: '申购单', code: 'AP', regimen: '申购按化学品分类取审批链：普通 1 级到导师、危化品 2 级、易制毒爆 3 级、剧毒 4 级；每一棒签完才流转下一棒。' },
  '/chem/stockin': { title: '入库单', code: 'RK', regimen: '入库须双人签认：登记人与复核人不得同人；剧毒两步签，第二签限学院权限（GB 15603-2022 双人验收）。' },
  '/chem/requisition': { title: '领用单', code: 'LY', regimen: '领用先经导师签、再由安全员复核双签，复核通过即按到期日升序（FEFO）逐批出库。' },
  '/chem/todo': { title: '审批中心', code: 'TODO', regimen: '四类待办各归其棒：待审申购、待签领用、待审废弃、待确认归还；先审先生效，积压即风险。' },
  '/chem/waste': { title: '废弃处置单', code: 'FQ', regimen: '处置单须写清去向与转移凭证；审批层级随分类：普通到学院、危化品及以上到校级，审批通过即销账。' },
  '/chem/check': { title: '盘点单', code: 'PD', regimen: '盘点双人签字（不得同人），四类差异判定后逐行调账；结单后不可再改。' }
}

const DETAIL_PAGES = [
  {
    re: /^\/chem\/req\/(\d+)$/, kind: 'req', code: 'LY', title: '领用单',
    stages: [
      { name: '登记', basis: 'DB11/T 1191.2 §5.1 使用登记' },
      { name: '导师签', basis: 'DB11/T 1191.2 §5.2 导师确认' },
      { name: '复核双签', basis: 'GB 15603-2022 §6.2 双人复核' },
      { name: '出库', basis: '五双制度 · 先到期先出' },
      { name: '归还/核销', basis: 'DB11/T 1191.2 §5.3 使用与归还' }
    ]
  },
  {
    re: /^\/chem\/waste\/(\d+)$/, kind: 'waste', code: 'FQ', title: '废弃处置单',
    stages: [
      { name: '建单', basis: '危化品条例 · 处置去向登记' },
      { name: '审批', basis: 'DB11/T 1191.2 §5.3 分级审批' },
      { name: '销账', basis: 'GB 15603-2022 §6.3 账物核销' }
    ]
  },
  {
    re: /^\/chem\/check\/(\d+)$/, kind: 'check', code: 'PD', title: '盘点单',
    stages: [
      { name: '建单', basis: '账物守恒 · 快照判定' },
      { name: '录入差异', basis: '双盘点人不得同人' },
      { name: '结单', basis: '四类差异逐行调账' }
    ]
  }
]

const LEGS = [
  { key: 'applicant', role: '申请人', basis: 'DB11/T 1191.2 §5.1 申购与领用登记' },
  { key: 'advisor', role: '导师', basis: 'DB11/T 1191.2 §5.2 导师确认' },
  { key: 'safety', role: '安全员', basis: 'GB 15603-2022 §6.2 双人验收与复核' },
  { key: 'college', role: '学院', basis: 'DB11/T 1191.2 §5.3 分级审批' },
  { key: 'campus', role: '校级', basis: '剧毒等高风险品校级监管终审' }
]

const ROLE_LEG = {
  teacher: 1, student: 1, applicant: 1,
  advisor: 2, safety: 3, college: 4, campus: 5
}

const detailMatch = computed(() => {
  for (const d of DETAIL_PAGES) {
    const m = route.path.match(d.re)
    if (m) return { ...d, id: Number(m[1]) }
  }
  return null
})
const pageMeta = computed(() => DOC_PAGES[route.path] || null)
const visible = computed(() => !!pageMeta.value || !!detailMatch.value)
const detailMode = computed(() => !!detailMatch.value)
const docTitle = computed(() => detailMatch.value?.title || pageMeta.value?.title || '')
const docCode = computed(() => {
  if (!detailMode.value) return 'RELAY'
  return detailDoc.value?.code || detailMatch.value?.code || ''
})

/* ── 实时计数（禁止写死；失败静默，不阻断业务页） ── */
const counts = ref({ draft: 0, seq1: 0, seq2: 0, seq3: 0, seq4: 0, reqAdv: 0, reqChk: 0, stockinPending: 0, wastePending: 0 })
const counted = ref(false)

async function loadCounts() {
  const one = { page: 1, size: 1 }
  const [draftR, pendingR, advR, chkR, inR, wasteR] = await Promise.all([
    listPurchase({ ...one, status: 0, mine: true }).catch(() => null),
    listPurchase({ page: 1, size: 200, status: 1 }).catch(() => null),
    listReq({ ...one, status: 0 }).catch(() => null),
    listReq({ ...one, status: 1 }).catch(() => null),
    listStockIn({ ...one, status: 0 }).catch(() => null),
    listWaste({ ...one, status: 0 }).catch(() => null)
  ])
  const seq = { 1: 0, 2: 0, 3: 0, 4: 0 }
  for (const r of pendingR?.records || []) {
    const s = Number(r.currentSeq)
    if (seq[s] != null) seq[s] += 1
  }
  counts.value = {
    draft: Number(draftR?.total || 0),
    seq1: seq[1], seq2: seq[2], seq3: seq[3], seq4: seq[4],
    reqAdv: Number(advR?.total || 0),
    reqChk: Number(chkR?.total || 0),
    stockinPending: Number(inR?.total || 0),
    wastePending: Number(wasteR?.total || 0)
  }
  counted.value = true
}

/* ── 当前角色在第几棒 ── */
const myRole = computed(() => auth.roles[0] || '')
const isAdmin = computed(() => auth.roles.includes('admin'))
const youLeg = computed(() => ROLE_LEG[myRole.value] || 1)
const remain = computed(() => Math.max(0, 5 - youLeg.value))

const track = computed(() => {
  if (detailMode.value) {
    const stages = detailMatch.value.stages
    const reached = detailDoc.value?.reached ?? 0
    return stages.map((st, i) => ({
      key: 's' + i,
      role: st.name,
      state: i < reached ? '已过' : (i === reached ? '当前' : '未到'),
      done: i < reached,
      now: i === reached,
      off: i > reached,
      you: i === reached && detailDoc.value?.youAre,
      tip: st.basis
    }))
  }
  const c = counts.value
  const nums = [c.draft, c.seq1 + c.reqAdv, c.seq2 + c.reqChk, c.seq3 + c.stockinPending + c.wastePending, c.seq4]
  return LEGS.map((leg, i) => ({
    key: leg.key,
    role: leg.role,
    count: counted.value ? nums[i] : '·',
    you: !isAdmin.value && youLeg.value === i + 1,
    now: !isAdmin.value && youLeg.value === i + 1,
    done: false,
    off: false,
    tip: leg.basis
  }))
})

/* ── 单据级下钻数据 ── */
const detailDoc = ref(null)

async function loadDetail() {
  const d = detailMatch.value
  if (!d) { detailDoc.value = null; return }
  try {
    if (d.kind === 'req') {
      const vo = await reqDetail(d.id)
      const st = Number(vo?.req?.status)
      const reached = st === 0 ? 1 : st === 1 ? 2 : st === 2 ? 3 : st === 4 ? 5 : 1
      detailDoc.value = {
        code: vo?.req?.code || ('领用单 #' + d.id),
        statusText: ['待导师签', '待复核', '已出库', '驳回', '已关闭'][st] || '—',
        reached: st === 3 ? 1 : reached,
        youAre: false
      }
    } else if (d.kind === 'waste') {
      const vo = await wasteDetail(d.id)
      const st = Number(vo?.waste?.status)
      detailDoc.value = {
        code: vo?.waste?.code || ('处置单 #' + d.id),
        statusText: ['待审批', '已处置', '已驳回'][st] || '—',
        reached: st === 2 ? 1 : st === 1 ? 3 : 1,
        youAre: false
      }
    } else {
      const vo = await checkDetail(d.id)
      const c = vo?.check || vo?.checkOrder || {}
      const result = Number(c.result)
      detailDoc.value = {
        code: c.code || ('盘点单 #' + d.id),
        statusText: result === 0 ? '盘点中' : (result === 1 ? '已平' : '不平'),
        reached: result === 0 ? 1 : 3,
        youAre: false
      }
    }
  } catch {
    detailDoc.value = { code: d.code + ' #' + d.id, statusText: '—', reached: 0, youAre: false }
  }
}

/* ── 下排规程文案 ── */
const regimenText = computed(() => {
  if (detailMode.value) {
    const d = detailMatch.value
    const st = detailDoc.value?.statusText || '—'
    const idx = Math.max(0, Math.min(d.stages.length - 1, (detailDoc.value?.reached || 1) - 1))
    const stage = d.stages[idx]
    return `当前状态「${st}」｜${stage.name} · 依据 ${stage.basis}：${nextHint(d)}`
  }
  const meta = pageMeta.value
  const leg = LEGS[Math.max(0, youLeg.value - 1)]
  if (isAdmin.value) {
    return `${meta.regimen}｜管理员可按任一棒代签：第 1 棒起依次为 ${LEGS.map(l => l.role).join(' → ')}（依据 DB11/T 1191.2 与 GB 15603-2022）。`
  }
  return `${meta.regimen}｜你这一棒：第 ${youLeg.value} 棒 · ${leg.role} · 依据 ${leg.basis}。`
})

function nextHint(d) {
  if (d.kind === 'req') {
    return '导师签只能由持导师签权限者签、且不得是申请人本人；复核双签通过即出库，签不齐单据停在原棒。'
  }
  if (d.kind === 'waste') {
    return '处置人去审批本人的处置单会被拒；审批通过即从批次与台账同时销账。'
  }
  return '双盘点人不得同人；差异行须逐行调账，结单后不可再改。'
}

const docSteps = computed(() => detailMatch.value?.stages.length || 0)
const docStep = computed(() => {
  const reached = detailDoc.value?.reached || 0
  return Math.max(1, Math.min(docSteps.value, reached <= 0 ? 1 : (reached >= docSteps.value ? docSteps.value : reached + 1)))
})

function jump(leg, i) {
  if (detailMode.value) return
  const meta = pageMeta.value
  if (i === 0) return router.push('/chem/purchase')
  if (meta && meta.code === 'TODO') return
  router.push('/chem/todo')
}

let timer = null
onMounted(async () => {
  await Promise.all([loadCounts(), loadDetail()])
  timer = setInterval(loadCounts, 60000)
})
watch(() => route.fullPath, async () => {
  counted.value = false
  await Promise.all([loadCounts(), loadDetail()])
})
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.relay-belt {
  background: #fff;
  border: 1px solid var(--c-line);
  border-left: 3px solid var(--c-safe);
  border-radius: var(--r-lg);
  margin-bottom: 14px;
  overflow: hidden;
}
.belt-track {
  display: flex;
  align-items: stretch;
  gap: 14px;
  padding: 8px 12px 7px;
}
.belt-kicker {
  flex: none;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 104px;
  padding-right: 12px;
  border-right: 1px dashed var(--c-line);
}
.kicker-code {
  font-family: var(--f-mono);
  font-size: 0.62rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--c-safe);
}
.kicker-name {
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--c-graphite-deep);
  margin-top: 2px;
  white-space: nowrap;
}
.belt-legs {
  flex: 1;
  display: flex;
  align-items: stretch;
  gap: 6px;
  min-width: 0;
  margin: 0;
  padding: 0;
  list-style: none;
}
.belt-leg {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
  padding: 5px 8px 5px 10px;
  border-radius: var(--r-sm);
  background: #FAFBFC;
  border: 1px solid transparent;
  border-left: 2px solid var(--c-line-strong);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}
.belt-leg:hover { background: var(--c-safe-bg); border-left-color: var(--c-safe); }
.belt-leg.is-now {
  background: var(--c-safe-bg);
  border-color: var(--c-safe-line);
  border-left-color: var(--c-safe);
}
.belt-leg.is-you { box-shadow: inset 0 0 0 1px var(--c-safe-line); }
.belt-leg.is-done { border-left-color: var(--c-ok); background: #F7FBF8; }
.belt-leg.is-off { opacity: 0.5; }
.leg-head { display: flex; align-items: center; gap: 5px; min-width: 0; }
.leg-no {
  flex: none;
  font-family: var(--f-mono);
  font-size: 0.6rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: var(--c-safe-deep);
  background: var(--c-safe-bg);
  border: 1px solid var(--c-safe-line);
  border-radius: 3px;
  padding: 0 4px;
  line-height: 1.5;
}
.belt-leg.is-now .leg-no { background: var(--c-safe); }
.belt-leg.is-done .leg-no { background: var(--c-ok); }
.leg-role {
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--c-graphite-deep);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.leg-you {
  flex: none;
  font-size: 0.58rem;
  font-weight: 700;
  color: #fff;
  background: var(--c-safe);
  border-radius: 999px;
  padding: 0 5px;
  line-height: 1.5;
}
.leg-foot { display: flex; align-items: baseline; gap: 3px; }
.leg-count {
  font-family: var(--f-mono);
  font-size: 0.92rem;
  font-weight: 800;
  color: var(--c-graphite-deep);
  line-height: 1.1;
}
.leg-count.zero { color: var(--c-graphite-faint); }
.leg-unit { font-size: 0.56rem; color: var(--c-graphite-faint); letter-spacing: 0.04em; }
.belt-regimen {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 5px 12px;
  background: var(--c-safe-bg);
  border-top: 1px solid var(--c-safe-line);
  font-size: 0.72rem;
  color: var(--c-graphite);
  line-height: 1.45;
}
.regimen-ico { color: var(--c-safe); flex: none; font-size: 0.85rem; }
.regimen-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.belt-scale {
  flex: none;
  font-family: var(--f-mono);
  font-size: 0.66rem;
  font-weight: 700;
  color: var(--c-safe-deep);
  letter-spacing: 0.02em;
  white-space: nowrap;
}
@media (max-width: 1280px) {
  .belt-track { gap: 8px; padding: 7px 9px 6px; }
  .belt-kicker { min-width: 84px; padding-right: 8px; }
  .leg-role { font-size: 0.7rem; }
  .belt-leg { padding: 4px 6px 4px 8px; }
  .belt-regimen { font-size: 0.68rem; }
}
</style>
