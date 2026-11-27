<template>
  <div class="viewport">
   <div class="stage" :style="{ transform: `scale(${scale})` }">
    <canvas ref="molRef" class="mol-field" />
    <div class="ambient" />

    <header class="hud">
      <button class="hud-back" @click="$router.push('/dashboard')">
        <svg viewBox="0 0 24 24" width="13" height="13"><path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        返回控制台
      </button>
      <div class="hud-title">
        <span class="side-line" />
        <div class="tt">
          <h1 class="t1">实验室危险化学品全流程监管平台</h1>
          <span class="t2">HAZMAT FULL-PROCESS SUPERVISION · GB 15603 / 安全管理条例</span>
        </div>
        <span class="side-line" />
      </div>
      <div class="hud-clock">
        <span class="time">{{ clock }}</span>
        <span class="date">{{ dateText }}</span>
      </div>
    </header>

    <main class="board">
      <!-- 左栏 -->
      <section class="col">
        <div class="panel cards-panel">
          <h4><i class="tab" />风险态势 · 四类预警</h4>
          <div class="cards">
            <div v-for="r in risks" :key="r.key" class="card" :class="{ on: r.value > 0 }" :style="{ '--rc': r.color }">
              <div class="c-top"><span class="c-dot" />{{ r.label }}</div>
              <div class="c-val"><b>{{ display[r.key] }}</b><small>{{ r.unit }}</small></div>
              <div class="c-bar"><i :style="{ width: r.pct + '%' }" /></div>
            </div>
          </div>
        </div>

        <div class="panel trend-panel">
          <h4><i class="tab" />近 7 日单据流转</h4>
          <div ref="trendRef" class="chart" />
        </div>

        <div class="panel grow">
          <h4><i class="tab" />在册化学品 · 库存标签墙</h4>
          <div class="tags">
            <span v-for="t in chemTags" :key="t.id" class="tag"
                  :style="{ color: t.color, borderColor: t.color + '66', fontSize: t.size + 'px' }"
                  :title="`${t.name} · ${t.stock} 瓶 · CAS ${t.cas}`">
              {{ t.name }}<i>{{ t.stock }}</i>
            </span>
            <p v-if="!chemTags.length" class="none">暂无台账</p>
          </div>
        </div>
      </section>

      <!-- 中栏 -->
      <section class="col center">
        <div class="deck-head">
          <h4><i class="tab gold" />实验室存储柜位 · 实时存有</h4>
          <span class="deck-note">{{ cabinets.length }} 柜 · {{ totalBatch }} 批在库 · {{ totalBottles }} 瓶</span>
        </div>
        <div class="deck">
         <div class="deck-grid">
          <div v-for="c in cabinets" :key="c.id" class="cab"
               :class="{ 'is-toxic': c.hasToxic, 'is-warn': c.expiring > 0 }">
            <span class="lock" v-if="c.dual_lock === 1" title="双人双锁">
              <svg viewBox="0 0 24 24" width="11" height="11"><rect x="5" y="11" width="14" height="9" rx="2" fill="currentColor"/><path d="M8 11V8a4 4 0 018 0v3" fill="none" stroke="currentColor" stroke-width="2.2"/></svg>
            </span>
            <span class="warn-dot" v-if="c.expiring > 0" />
            <div class="cab-code">{{ c.code }}</div>
            <div class="cab-name">{{ c.name || CABINET_TYPE[c.type] || '通用柜' }}</div>
            <div class="cab-viz">
              <div class="cab-body">
                <i v-for="(s, i) in c.shelves" :key="i" class="cab-bottle" :style="{ '--cc': s.color }" :title="s.label"><em class="cliquid" /></i>
                <span v-if="c.more" class="cab-more">+{{ c.more }}</span>
                <span v-if="!c.batches" class="cab-none">空柜</span>
              </div>
            </div>
            <div class="cab-meta"><b>{{ c.batches }}</b> 批 · {{ c.bottles }} 瓶</div>
          </div>
          <p v-if="!cabinets.length" class="none">暂无柜位数据</p>
         </div>

         <div class="mol-panel">
           <div class="mol-head">
             <span class="mol-name">{{ moleculeInfo.name }}</span>
             <span class="mol-formula">{{ moleculeInfo.formula }}</span>
           </div>
           <div class="mol-stage"><Molecule3D :molecule="moleculeInfo.mol" /></div>
           <div class="mol-foot">
             <span>{{ moleculeInfo.cas }}</span>
             <span><b>{{ moleculeInfo.stock }}</b> 瓶在库</span>
           </div>
           <ul class="mol-tabs">
             <li v-for="(o, i) in molOptions" :key="o.formula"
                 :class="{ on: i === molIdx }" :title="`${o.name} ${o.formula}`"
                 @click="molIdx = i">{{ o.formula }}</li>
           </ul>
           <div class="mol-hint">点击分子式切换 · 拖拽旋转</div>
         </div>
        </div>

        <div class="duo">
          <div class="panel half">
            <h4><i class="tab" />批次状态构成</h4>
            <div class="stack-wrap">
              <div class="stack">
                <i v-for="s in batchStack" :key="s.label" :style="{ width: s.pct + '%', background: s.color }" :title="`${s.label} ${s.value}`" />
              </div>
              <ul class="stack-legend">
                <li v-for="s in batchStack" :key="s.label">
                  <i :style="{ background: s.color }" />{{ s.label }}<b>{{ s.value }}</b><small>{{ s.pct }}%</small>
                </li>
              </ul>
              <div class="stack-note">批次合计 <b>{{ batchStack.reduce((n, s) => n + s.value, 0) }}</b> 批 · 在库占比 {{ batchStack.find(s => s.label.includes('在库'))?.pct ?? 0 }}%</div>
            </div>
          </div>
          <div class="panel half">
            <h4><i class="tab" />全流程单据量排名</h4>
            <ul class="rank">
              <li v-for="f in funnel" :key="f.name">
                <span class="r-name">{{ f.name }}</span>
                <span class="r-track"><i :style="{ width: f.pct + '%' }" /></span>
                <span class="r-val">{{ f.value }}</span>
              </li>
              <li v-if="!funnel.length" class="none">暂无单据</li>
            </ul>
          </div>
        </div>
      </section>

      <!-- 右栏 -->
      <section class="col">
        <div class="panel pie-panel">
          <h4><i class="tab" />分类分级库存占比</h4>
          <div ref="pieRef" class="chart" />
        </div>

        <div class="panel radar-panel">
          <h4><i class="tab" />实验室安全合规度<em class="hint">实时折算</em></h4>
          <div ref="radarRef" class="chart" />
        </div>

        <div class="panel occ-panel">
          <h4><i class="tab" />柜位占用率</h4>
          <ul class="occ">
            <li v-for="o in occupancy" :key="o.code">
              <span class="o-code">{{ o.code }}</span>
              <span class="o-track"><i :style="{ width: o.pct + '%', background: o.color }" /></span>
              <span class="o-val">{{ o.batches }} 批</span>
            </li>
          </ul>
        </div>

        <div class="panel barrel-panel">
          <h4><i class="tab" />废弃处置去向</h4>
          <div class="barrels">
            <div v-for="b in barrelRows" :key="b.key" class="barrel">
              <span class="blid" />
              <span class="bbody"><em class="bfill" :style="{ height: b.h + '%' }" /><em class="bglow" :style="{ bottom: b.h + '%' }" /></span>
              <span class="bname">{{ b.name }}</span>
              <span class="bnum">{{ b.value }} <small>单</small></span>
            </div>
            <p v-if="!barrelRows.length" class="none">暂无处置记录</p>
          </div>
        </div>
      </section>
    </main>

    <footer class="hud-foot">
      <span class="src">数据实时读取自系统台账与单据</span>
      <span class="law">双人双锁 · 分级审批 · 禁配相容 · 账物守恒 · 全程留痕</span>
      <span class="src">每 60s 自动刷新</span>
    </footer>
   </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import * as echarts from 'echarts'
import http from '../../api/request'
import { statsDashboard, statsScreen, listBatch, listChemInfo, listPurchase, listStockIn, listReq, listWaste, listCheck } from '../../api/chem'
import Molecule3D from '../../components/Molecule3D.vue'
import { CATEGORIES, BATCH_STATUS, WASTE_TYPE } from '../../utils/constants'

const CABINET_TYPE = { 1: '酸柜', 2: '碱柜', 3: '有机柜', 4: '氧化剂柜', 5: '剧毒专柜', 6: '通用柜' }
const BATCH_COLOR = { 0: '#34D9A8', 1: '#7C8FA6', 2: '#FF5A5F', 3: '#FFA23A' }
const AXIS = { color: '#8FA6C4', fontSize: 11 }
const TIP = { backgroundColor: 'rgba(6,18,34,.94)', borderColor: 'rgba(90,200,250,.35)', textStyle: { color: '#E6F1FF', fontSize: 12 } }

/* ── 三维分子库（真实物质结构，与台账名称关键词匹配） ── */
const hexC = Array.from({ length: 6 }, (_, i) => ({ el: 'C', x: Math.cos(i * Math.PI / 3) * 1.4, y: Math.sin(i * Math.PI / 3) * 1.4, z: 0 }))
const hexH = Array.from({ length: 6 }, (_, i) => ({ el: 'H', x: Math.cos(i * Math.PI / 3) * 2.45, y: Math.sin(i * Math.PI / 3) * 2.45, z: 0 }))
const MOL_LIB = [
  {
    keys: ['苯', '甲苯', '二甲苯'], name: '苯环骨架', formula: 'C6H6', scale: 0.72,
    atoms: [...hexC, ...hexH],
    bonds: [...Array.from({ length: 6 }, (_, i) => [i, (i + 1) % 6]), ...Array.from({ length: 6 }, (_, i) => [i, i + 6])]
  },
  {
    keys: ['硫酸'], name: '硫酸', formula: 'H2SO4', scale: 0.86,
    atoms: [
      { el: 'S', x: 0, y: 0, z: 0 },
      { el: 'O', x: 1.5, y: 0.75, z: 0.2 }, { el: 'O', x: -1.5, y: 0.75, z: -0.2 },
      { el: 'O', x: 0.2, y: -1.15, z: 1.35 }, { el: 'O', x: -0.2, y: -1.15, z: -1.35 },
      { el: 'H', x: 2.35, y: 1.2, z: 0.35 }, { el: 'H', x: -2.35, y: 1.2, z: -0.35 }
    ],
    bonds: [[0, 1], [0, 2], [0, 3], [0, 4], [1, 5], [2, 6]]
  },
  {
    keys: ['丙酮'], name: '丙酮', formula: 'C3H6O', scale: 0.9,
    atoms: [
      { el: 'C', x: -1.75, y: -0.35, z: 0 }, { el: 'C', x: 0, y: 0.1, z: 0 }, { el: 'C', x: 1.75, y: -0.35, z: 0 },
      { el: 'O', x: 0, y: 1.5, z: 0.1 },
      { el: 'H', x: -2.35, y: 0.35, z: 0.55 }, { el: 'H', x: -2.2, y: -1.15, z: -0.4 },
      { el: 'H', x: 2.35, y: 0.35, z: -0.55 }, { el: 'H', x: 2.2, y: -1.15, z: 0.4 }
    ],
    bonds: [[0, 1], [1, 2], [1, 3], [0, 4], [0, 5], [2, 6], [2, 7]]
  },
  {
    keys: ['乙醇', '酒精'], name: '乙醇', formula: 'C2H5OH', scale: 0.9,
    atoms: [
      { el: 'C', x: -1.45, y: -0.3, z: 0 }, { el: 'C', x: 0, y: 0.25, z: 0 },
      { el: 'O', x: 1.5, y: -0.3, z: 0 }, { el: 'H', x: 2.3, y: 0.3, z: 0.15 },
      { el: 'H', x: -2.05, y: 0.4, z: 0.5 }, { el: 'H', x: -1.9, y: -1.1, z: -0.4 },
      { el: 'H', x: 0.2, y: 1.15, z: 0.35 }, { el: 'H', x: 0.15, y: -0.35, z: 0.95 }
    ],
    bonds: [[0, 1], [1, 2], [2, 3], [0, 4], [0, 5], [1, 6], [1, 7]]
  },
  {
    keys: ['氯化钠', '氯化钾', '食盐'], name: '氯化钠', formula: 'NaCl', scale: 1.0,
    atoms: [
      { el: 'Na', x: -1.0, y: 0.15, z: 0 }, { el: 'Cl', x: 1.0, y: -0.15, z: 0 }
    ],
    bonds: [[0, 1]]
  },
  {
    keys: ['氢氧化钠', '烧碱', '氢氧化钾'], name: '氢氧化钠', formula: 'NaOH', scale: 1.0,
    atoms: [
      { el: 'Na', x: 1.85, y: 0.45, z: 0 }, { el: 'O', x: 0, y: -0.25, z: 0 }, { el: 'H', x: -0.95, y: 0.4, z: 0 }
    ],
    bonds: [[1, 0], [1, 2]]
  }
]

const molRef = ref(null)
const trendRef = ref(null), pieRef = ref(null), radarRef = ref(null)
const scale = ref(1)
const dashboard = ref({})
const screen = ref({})
const cabinets = ref([])
const batchRows = ref([])
const chemRows = ref([])
const docs = ref({ purchase: [], stockin: [], req: [], waste: [], check: [] })
const clock = ref('--:--:--')
const dateText = ref('')
const display = reactive({ expiryWarn: 0, lowStock: 0, pendingDocs: 0, overdueStockin: 0 })

let trendChart = null, pieChart = null, radarChart = null
let firstPaint = true
let lastSig = ''
let timer = null, clockTimer = null, molRaf = null

function fit() { scale.value = Math.min(window.innerWidth / 1920, window.innerHeight / 1080) }

const risks = computed(() => {
  const base = [
    { key: 'expiryWarn', label: '效期临近', unit: '批', value: Number(dashboard.value.expiryWarn || 0), color: '#FFA23A' },
    { key: 'lowStock', label: '低于安全库存', unit: '种', value: Number(dashboard.value.lowStock || 0), color: '#E8B33A' },
    { key: 'pendingDocs', label: '单据待审', unit: '单', value: Number(dashboard.value.pendingDocs || 0), color: '#5AC8FA' },
    { key: 'overdueStockin', label: '超期未入库', unit: '单', value: Number(dashboard.value.overdueStockin || 0), color: '#FF5A5F' }
  ]
  const max = Math.max(1, ...base.map(b => b.value))
  return base.map(b => ({ ...b, pct: Math.round(b.value / max * 100) }))
})

function dayKey(v) { return v ? String(v).replace('T', ' ').slice(0, 10) : '' }

const trend = computed(() => {
  const days = []
  const today = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today.getTime() - i * 864e5)
    days.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`)
  }
  const bucket = rows => {
    const m = {}
    for (const r of rows) {
      const k = dayKey(r.createTime || r.create_time)
      if (k) m[k] = (m[k] || 0) + 1
    }
    return days.map(d => m[d] || 0)
  }
  return { days: days.map(d => d.slice(5)), inbound: bucket(docs.value.stockin), outbound: bucket(docs.value.req) }
})

const chemTags = computed(() => {
  const rows = chemRows.value.map(c => ({
    id: c.id, name: c.name, stock: Number(c.stockQty ?? c.stock_qty ?? 0),
    cas: c.casNo || c.cas_no || '—',
    color: CATEGORIES[c.category]?.color || '#5AC8FA'
  }))
  const max = Math.max(1, ...rows.map(r => r.stock))
  return rows.sort((a, b) => b.stock - a.stock).slice(0, 26)
    .map(r => ({ ...r, size: Math.round(14 + (r.stock / max) * 11) }))
})

const batchStack = computed(() => {
  const bs = screen.value.batchStatusDist || []
  const total = Math.max(1, bs.reduce((n, r) => n + Number(r.cnt || 0), 0))
  return bs.map(r => ({
    label: BATCH_STATUS[r.status]?.label || `状态${r.status}`,
    value: Number(r.cnt || 0),
    color: BATCH_COLOR[r.status] || '#7C8FA6',
    pct: Math.round(Number(r.cnt || 0) / total * 100)
  }))
})

const funnel = computed(() => {
  const rows = [
    { name: '申购单', value: docs.value.purchase.length },
    { name: '入库单', value: docs.value.stockin.length },
    { name: '领用单', value: docs.value.req.length },
    { name: '盘点单', value: docs.value.check.length },
    { name: '废弃单', value: docs.value.waste.length }
  ].sort((a, b) => b.value - a.value)
  const max = Math.max(1, ...rows.map(r => r.value))
  return rows.map(r => ({ ...r, pct: Math.round(r.value / max * 100) }))
})

const radarVals = computed(() => {
  const inStock = batchRows.value.filter(b => Number(b.status ?? 0) === 0)
  const totalB = Math.max(1, batchRows.value.length)
  const soon = new Date(Date.now() + 30 * 864e5)
  const nearCnt = inStock.filter(b => {
    const e = b.expiryDate || b.expiry_date
    return e && new Date(e) <= soon
  }).length
  const cabs = cabinets.value
  const occupied = cabs.filter(c => c.batches > 0).length
  const locked = cabs.filter(c => c.dual_lock === 1).length
  const chemWithBatch = new Set(inStock.map(b => Number(b.chemicalId ?? b.chemical_id))).size
  const doneWaste = docs.value.waste.filter(w => Number(w.status) >= 2).length
  const pct = (a, b) => Math.round(Math.min(1, b ? a / b : 0) * 100)
  return [
    pct(inStock.length, totalB), pct(occupied, Math.max(1, cabs.length)), pct(locked, Math.max(1, cabs.length)),
    pct(Math.max(0, inStock.length - nearCnt), Math.max(1, inStock.length)),
    pct(doneWaste, Math.max(1, docs.value.waste.length)), pct(chemWithBatch, Math.max(1, chemRows.value.length))
  ]
})

const occupancy = computed(() => {
  const max = Math.max(1, ...cabinets.value.map(c => c.batches))
  return cabinets.value.map(c => ({
    code: c.code, batches: c.batches, pct: Math.round(c.batches / max * 100),
    color: c.hasToxic ? '#FF5A5F' : (c.expiring > 0 ? '#FFA23A' : '#5AC8FA')
  }))
})

const barrelRows = computed(() => {
  const ds = screen.value.disposalStats || []
  const maxCnt = Math.max(1, ...ds.map(r => Number(r.cnt || 0)))
  return ds.map(r => ({
    key: r.type, name: WASTE_TYPE[r.type]?.label || `类型${r.type}`,
    value: Number(r.cnt || 0), h: Math.round(10 + (Number(r.cnt || 0) / maxCnt) * 70)
  }))
})

/* 可切换分子列表：分子库中在台账有物的物质（台账名 + 库存），按库存降序 */
const molIdx = ref(0)
const molOptions = computed(() => {
  const rows = chemRows.value.map(c => ({
    name: c.name || '', cas: c.casNo || c.cas_no || 'CAS —',
    stock: Number(c.stockQty ?? c.stock_qty ?? 0)
  }))
  return MOL_LIB.map(m => {
    const hit = rows.filter(r => m.keys.some(k => r.name.includes(k))).sort((a, b) => b.stock - a.stock)[0]
    return hit ? { name: hit.name, formula: m.formula, cas: hit.cas, stock: hit.stock, mol: m } : null
  }).filter(Boolean).sort((a, b) => b.stock - a.stock)
})
const moleculeInfo = computed(() => molOptions.value[molIdx.value] || molOptions.value[0]
  || { name: '苯环骨架', formula: 'C6H6', cas: 'CAS 71-43-2', stock: 0, mol: MOL_LIB[0] })

const totalBatch = computed(() => batchRows.value.filter(b => Number(b.status ?? 0) === 0).length)
const totalBottles = computed(() => cabinets.value.reduce((s, c) => s + c.bottles, 0))

function rollTo(key, target) {
  const from = Number(display[key] || 0), t = Number(target || 0)
  if (from === t) return
  const t0 = performance.now()
  const step = now => {
    const p = Math.min(1, (now - t0) / 420)
    display[key] = Math.round(from + (t - from) * (1 - Math.pow(1 - p, 3)))
    if (p < 1) requestAnimationFrame(step)
  }
  requestAnimationFrame(step)
}

function buildCabinets(cabs, batches, chemMap) {
  const soon = new Date(Date.now() + 30 * 864e5)
  return cabs.map(c => {
    const mine = batches.filter(b => Number(b.cabinetId ?? b.cabinet_id) === Number(c.id) && Number(b.status ?? 0) === 0)
    const catOf = b => Number(chemMap.get(Number(b.chemicalId ?? b.chemical_id))?.category ?? 0)
    const shelves = mine.slice(0, 4).map(b => {
      const qty = Number(b.qty_left ?? b.qtyLeft ?? 0)
      const e = b.expiryDate || b.expiry_date
      const near = e && new Date(e) <= soon
      return { color: near ? '#FFA23A' : (CATEGORIES[catOf(b)]?.color || '#5AC8FA'), label: `${b.lotNo || b.lot_no || ''} · ${qty} 瓶${near ? ' · 临期' : ''}` }
    })
    return {
      id: c.id, code: c.code, name: c.name, type: c.type, dual_lock: c.dual_lock,
      hasToxic: mine.some(b => catOf(b) === 1),
      expiring: mine.filter(b => {
        const e = b.expiryDate || b.expiry_date
        return e && new Date(e) <= soon
      }).length,
      batches: mine.length, more: Math.max(0, mine.length - 4),
      bottles: mine.reduce((s, b) => s + Number(b.qty_left ?? b.qtyLeft ?? 0), 0),
      shelves
    }
  })
}

function render() {
  const animate = firstPaint
  firstPaint = false

  if (trendRef.value) {
    if (!trendChart) trendChart = echarts.init(trendRef.value)
    const c1 = trendChart
    c1.setOption({
      animation: animate,
      tooltip: { ...TIP, trigger: 'axis' },
      legend: { top: 0, right: 0, itemWidth: 12, itemHeight: 6, textStyle: { color: '#8FA6C4', fontSize: 11 } },
      grid: { left: 32, right: 12, top: 26, bottom: 20 },
      xAxis: { type: 'category', data: trend.value.days, axisLabel: AXIS, axisLine: { lineStyle: { color: 'rgba(90,200,250,.25)' } } },
      yAxis: { type: 'value', minInterval: 1, max: v => Math.max(3, v.max), splitLine: { lineStyle: { color: 'rgba(90,200,250,.10)' } }, axisLabel: AXIS },
      series: [
        {
          name: '入库', type: 'line', smooth: true, symbolSize: 6, data: trend.value.inbound,
          lineStyle: { width: 2, color: '#34D9A8' }, itemStyle: { color: '#34D9A8' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(52,217,168,.42)' }, { offset: 1, color: 'rgba(52,217,168,0)' }]) }
        },
        {
          name: '领用', type: 'line', smooth: true, symbolSize: 6, data: trend.value.outbound,
          lineStyle: { width: 2, color: '#E8B33A' }, itemStyle: { color: '#E8B33A' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(232,179,58,.4)' }, { offset: 1, color: 'rgba(232,179,58,0)' }]) }
        }
      ]
    }, { notMerge: true })
  }

  const catDist = screen.value.categoryDist || []
  if (pieRef.value) {
    if (!pieChart) pieChart = echarts.init(pieRef.value)
    const c3 = pieChart
    const total = catDist.reduce((n, r) => n + Number(r.chem_count || 0), 0)
    c3.setOption({
      tooltip: { ...TIP, trigger: 'item', formatter: p => `${p.name}<br/>台账 ${p.value} 种 (${p.percent}%)` },
      legend: { bottom: 0, icon: 'circle', itemWidth: 9, itemHeight: 9, textStyle: { color: '#8FA6C4', fontSize: 11 } },
      graphic: [
        { type: 'text', left: 'center', top: '34%', style: { text: String(total), fill: '#8FD3F4', fontSize: 26, fontWeight: 700, fontFamily: 'Consolas, monospace' } },
        { type: 'text', left: 'center', top: '46%', style: { text: '种在册', fill: '#8FA6C4', fontSize: 11 } }
      ],
      series: [{
        type: 'pie', radius: ['46%', '68%'], center: ['50%', '40%'],
        itemStyle: { borderColor: 'rgba(4,10,20,.8)', borderWidth: 2 },
        label: { show: false }, emphasis: { scale: true, scaleSize: 6 },
        data: catDist.map(r => ({
          name: CATEGORIES[r.category]?.name || `分类${r.category}`,
          value: Number(r.chem_count || 0),
          itemStyle: { color: CATEGORIES[r.category]?.color || '#5AC8FA' }
        }))
      }]
    }, { notMerge: true })
  }

  if (radarRef.value) {
    if (!radarChart) radarChart = echarts.init(radarRef.value)
    const c4 = radarChart
    c4.setOption({
      animation: animate,
      tooltip: { ...TIP },
      radar: {
        center: ['50%', '52%'], radius: '74%',
        indicator: [
          { name: '在库率', max: 100 }, { name: '柜位利用', max: 100 }, { name: '双锁覆盖', max: 100 },
          { name: '效期健康', max: 100 }, { name: '处置完结', max: 100 }, { name: '台账动销', max: 100 }
        ],
        axisName: { color: '#BFE7FF', fontSize: 11 },
        splitLine: { lineStyle: { color: 'rgba(90,200,250,.18)' } },
        axisLine: { lineStyle: { color: 'rgba(90,200,250,.2)' } },
        splitArea: { areaStyle: { color: ['rgba(90,200,250,.04)', 'rgba(90,200,250,.08)'] } }
      },
      series: [{
        type: 'radar', symbolSize: 4,
        lineStyle: { color: '#5AC8FA', width: 2 }, itemStyle: { color: '#8FD3F4' },
        areaStyle: { color: 'rgba(90,200,250,.28)' },
        data: [{ value: radarVals.value, name: '合规度' }]
      }]
    }, { notMerge: true })
  }
}

const CPK = { C: '#8C8C8C', H: '#EDEDED', O: '#FF4D4D', S: '#E8D24A', N: '#5A7BFF', Na: '#A55CF2' }
const R_ATOM = { C: 11, H: 6.5, O: 10, S: 13, N: 10, Na: 13 }
const MOLECULES = [
  {
    cx: 0.10, cy: 0.30, unit: 36, spin: 0.00016,
    atoms: Array.from({ length: 6 }, (_, i) => ({ el: 'C', x: Math.cos(i * Math.PI / 3) * 1.4, y: Math.sin(i * Math.PI / 3) * 1.4, z: 0 }))
      .concat(Array.from({ length: 6 }, (_, i) => ({ el: 'H', x: Math.cos(i * Math.PI / 3) * 2.5, y: Math.sin(i * Math.PI / 3) * 2.5, z: 0 }))),
    bonds: Array.from({ length: 6 }, (_, i) => [i, (i + 1) % 6]).concat(Array.from({ length: 6 }, (_, i) => [i, i + 6]))
  },
  {
    cx: 0.88, cy: 0.24, unit: 32, spin: -0.00013,
    atoms: [
      { el: 'C', x: -1.5, y: 0.2, z: 0 }, { el: 'C', x: 0, y: -0.2, z: 0.2 }, { el: 'O', x: 1.4, y: 0.3, z: 0 },
      { el: 'H', x: 2.2, y: -0.2, z: 0.2 }, { el: 'H', x: -2.1, y: -0.3, z: 0.4 }, { el: 'H', x: -1.8, y: 0.9, z: -0.3 }
    ],
    bonds: [[0, 1], [1, 2], [2, 3], [0, 4], [0, 5]]
  },
  {
    cx: 0.80, cy: 0.80, unit: 30, spin: 0.00011,
    atoms: [{ el: 'S', x: 0, y: 0, z: 0 }, { el: 'O', x: 1.5, y: 0.6, z: 0 }, { el: 'O', x: -1.5, y: 0.6, z: 0 }, { el: 'O', x: 0, y: -1.0, z: 1.1 }],
    bonds: [[0, 1], [0, 2], [0, 3]]
  },
  {
    cx: 0.16, cy: 0.82, unit: 28, spin: -0.00010,
    atoms: [{ el: 'O', x: 0, y: 0, z: 0 }, { el: 'H', x: 0.9, y: 0.6, z: 0 }, { el: 'H', x: -0.9, y: 0.6, z: 0 }, { el: 'Na', x: 2.6, y: -0.8, z: 0.4 }],
    bonds: [[0, 1], [0, 2]]
  }
]

function initMolecular() {
  const cv = molRef.value
  if (!cv) return
  const ctx = cv.getContext('2d')
  let W = 0, H = 0
  const resize = () => { W = cv.width = cv.clientWidth; H = cv.height = cv.clientHeight }
  resize()
  window.addEventListener('resize', resize)
  const project = (a, ang, unit) => {
    const s = Math.sin(ang), c = Math.cos(ang)
    const X = a.x * c - a.z * s, Z = a.x * s + a.z * c
    const f = 6.2, k = f / (f - Z * 0.5)
    return { X: X * unit * k, Y: -a.y * unit * k, k }
  }
  const frame = now => {
    ctx.clearRect(0, 0, W, H)
    for (const m of MOLECULES) {
      const bx = m.cx * W, by = m.cy * H
      const drift = Math.sin(now * 0.00013 + m.cx * 10) * 14
      const ang = now * m.spin
      const pts = m.atoms.map(a => project(a, ang, m.unit))
      ctx.lineWidth = 3
      ctx.strokeStyle = 'rgba(150, 200, 235, 0.28)'
      for (const [i, j] of m.bonds) {
        ctx.beginPath()
        ctx.moveTo(bx + pts[i].X, by + pts[i].Y + drift)
        ctx.lineTo(bx + pts[j].X, by + pts[j].Y + drift)
        ctx.stroke()
      }
      m.atoms.forEach((a, i) => {
        const p = pts[i]
        const r = (R_ATOM[a.el] || 9) * p.k * 1.15
        const x = bx + p.X, y = by + p.Y + drift
        const g = ctx.createRadialGradient(x - r * 0.35, y - r * 0.4, r * 0.15, x, y, r)
        const col = CPK[a.el] || '#8C8C8C'
        g.addColorStop(0, col + 'FF'); g.addColorStop(0.55, col + 'B0'); g.addColorStop(1, col + '38')
        ctx.fillStyle = g
        ctx.beginPath(); ctx.arc(x, y, r, 0, 6.2832); ctx.fill()
        ctx.strokeStyle = col + '70'; ctx.lineWidth = 1; ctx.stroke()
      })
    }
    molRaf = requestAnimationFrame(frame)
  }
  molRaf = requestAnimationFrame(frame)
}

async function load() {
  const [dash, scr, cabs, batches, chems, pur, stk, req, wst, chk] = await Promise.all([
    statsDashboard(), statsScreen(), http.get('/chem/cabinet'),
    listBatch({ page: 1, size: 500 }), listChemInfo({ page: 1, size: 500 }),
    listPurchase({ page: 1, size: 200 }), listStockIn({ page: 1, size: 200 }),
    listReq({ page: 1, size: 200 }), listWaste({ page: 1, size: 200 }),
    listCheck({ page: 1, size: 200 })
  ])
  dashboard.value = dash
  screen.value = scr
  batchRows.value = batches?.records || []
  chemRows.value = chems?.records || []
  docs.value = {
    purchase: pur?.records || [], stockin: stk?.records || [], req: req?.records || [],
    waste: wst?.records || [], check: chk?.records || []
  }
  const chemMap = new Map(chemRows.value.map(c => [Number(c.id), c]))
  cabinets.value = buildCabinets(cabs || [], batchRows.value, chemMap)
  risks.value.forEach(r => rollTo(r.key, r.value))

  const sig = JSON.stringify([dash, scr.categoryDist, scr.batchStatusDist, scr.disposalStats,
    batchRows.value.length, chemRows.value.length,
    cabinets.value.map(c => [c.code, c.batches, c.bottles])])
  if (sig === lastSig && !firstPaint) return   // 数据未变：不重绘
  lastSig = sig
  render()
}

function tickClock() {
  const d = new Date()
  clock.value = d.toLocaleTimeString('zh-CN', { hour12: false })
  dateText.value = d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' })
}

function onResize() { trendChart?.resize(); pieChart?.resize(); radarChart?.resize() }

onMounted(async () => {
  fit()
  window.addEventListener('resize', fit)
  window.addEventListener('resize', onResize)
  tickClock()
  initMolecular()
  await load()
  timer = setInterval(load, 60000)
  clockTimer = setInterval(tickClock, 1000)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', fit)
  window.removeEventListener('resize', onResize)
  clearInterval(timer)
  clearInterval(clockTimer)
  cancelAnimationFrame(molRaf)
  trendChart?.dispose(); pieChart?.dispose(); radarChart?.dispose()
})
</script>

<style scoped>
.viewport { position: fixed; inset: 0; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #03080F; }
.stage {
  position: relative; flex: none; width: 1920px; height: 1080px;
  transform-origin: center center; padding: 22px 24px 14px;
  display: flex; flex-direction: column; gap: 12px;
  color: #C7DAEC;
  background: url(/screen-bg.jpg) center / 100% 100% no-repeat, #03080F;
}
.stage::after {
  content: ''; position: absolute; left: 0; right: 0; height: 220px; top: -240px; z-index: 1;
  background: linear-gradient(180deg, transparent, rgba(125, 227, 255, 0.055), rgba(125, 227, 255, 0.02), transparent);
  animation: scanline 11s linear infinite; pointer-events: none;
}
.mol-field { position: absolute; inset: 0; width: 1920px; height: 1080px; pointer-events: none; opacity: 0.5; }
.ambient { position: absolute; inset: 0; pointer-events: none; background: linear-gradient(180deg, transparent 62%, rgba(3, 8, 15, 0.55)); }

.hud { position: relative; z-index: 3; height: 74px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; }
.hud-back {
  justify-self: start; display: inline-flex; align-items: center; gap: 6px; padding: 7px 16px; cursor: pointer;
  font-size: 13px; letter-spacing: 0.08em; color: #8FD3F4;
  background: rgba(9, 26, 42, 0.6); border: 1px solid rgba(90, 200, 250, 0.28);
  transition: background 0.2s, border-color 0.2s;
}
.hud-back:hover { background: rgba(90, 200, 250, 0.12); border-color: rgba(90, 200, 250, 0.55); }
.hud-title { display: flex; align-items: center; gap: 22px; }
.side-line { position: relative; width: 150px; height: 1px; background: linear-gradient(90deg, transparent, rgba(90, 200, 250, 0.65)); }
.side-line::after { content: ''; position: absolute; right: -4px; top: -2.5px; width: 6px; height: 6px; transform: rotate(45deg); background: rgba(125, 227, 255, 0.75); }
.hud-title .side-line:last-child { background: linear-gradient(270deg, transparent, rgba(90, 200, 250, 0.65)); }
.hud-title .side-line:last-child::after { right: auto; left: -4px; }
.tt { display: flex; flex-direction: column; align-items: center; gap: 5px; }
.hud-title .t1 {
  margin: 0; font-size: 33px; font-weight: 700; letter-spacing: 0.14em; color: #EAF6FF;
  text-shadow: 0 0 22px rgba(125, 227, 255, 0.55), 0 0 54px rgba(90, 200, 250, 0.3), 0 2px 0 rgba(0, 0, 0, 0.45);
}
.hud-title .t2 { font-size: 11px; letter-spacing: 0.34em; color: rgba(143, 211, 244, 0.5); }
.hud-clock { justify-self: end; display: flex; flex-direction: column; align-items: flex-end; gap: 2px; }
.hud-clock .time { font-family: var(--f-mono); font-size: 31px; color: #8FD3F4; text-shadow: 0 0 16px rgba(125, 227, 255, 0.65); }
.hud-clock .date { font-size: 12px; letter-spacing: 0.12em; color: rgba(143, 166, 196, 0.8); }

.board { position: relative; z-index: 3; flex: 1; display: grid; grid-template-columns: 430px 972px 430px; gap: 18px; min-height: 0; }
.col { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.panel {
  position: relative; display: flex; flex-direction: column; padding: 12px 16px 14px;
  background: linear-gradient(180deg, rgba(11, 34, 56, 0.34), rgba(4, 12, 22, 0.30));
  backdrop-filter: blur(13px) saturate(1.22);
  border: 1px solid rgba(90, 200, 250, 0.17);
  box-shadow: inset 0 1px 0 rgba(180, 230, 255, 0.10), inset 0 0 46px rgba(60, 150, 220, 0.07), 0 10px 32px rgba(0, 0, 0, 0.38);
}
.panel::before, .panel::after { content: ''; position: absolute; width: 18px; height: 18px; pointer-events: none; }
.panel::before { top: -1px; left: -1px; border-top: 2px solid rgba(125, 227, 255, 0.62); border-left: 2px solid rgba(125, 227, 255, 0.62); }
.panel::after { bottom: -1px; right: -1px; border-bottom: 2px solid rgba(125, 227, 255, 0.36); border-right: 2px solid rgba(125, 227, 255, 0.36); }
.panel.grow { flex: 1; }
.panel h4 { display: flex; align-items: center; gap: 8px; margin: 0 0 8px; font-size: 13.5px; font-weight: 600; letter-spacing: 0.1em; color: #A9D8F0; }
.panel h4 .tab { width: 3px; height: 12px; background: linear-gradient(180deg, #5AC8FA, transparent); }
.panel h4 .tab.gold { background: linear-gradient(180deg, #E8B33A, transparent); }
.panel h4 .hint { margin-left: auto; font-style: normal; font-size: 10.5px; color: rgba(143, 166, 196, 0.7); letter-spacing: 0; }
.chart { flex: 1; min-height: 0; height: 100%; }
.none { margin: auto; color: rgba(143, 166, 196, 0.6); font-size: 12.5px; }

.cards-panel { height: 214px; flex: none; }
.trend-panel { height: 198px; flex: none; }
.pie-panel { height: 248px; flex: none; }
.radar-panel { height: 268px; flex: none; }
.cards { flex: 1; display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr; gap: 8px; }
.card {
  position: relative; display: flex; flex-direction: column; justify-content: center; gap: 4px;
  padding: 8px 12px; border: 1px solid rgba(90, 200, 250, 0.16);
  background: linear-gradient(135deg, rgba(12, 38, 62, 0.5), rgba(4, 12, 22, 0.34));
}
.card.on { border-color: color-mix(in srgb, var(--rc) 55%, transparent); box-shadow: inset 0 0 22px color-mix(in srgb, var(--rc) 12%, transparent); }
.c-top { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #A9C2DC; }
.c-dot { width: 6px; height: 6px; border-radius: 50%; background: rgba(143, 166, 196, 0.45); }
.card.on .c-dot { background: var(--rc); box-shadow: 0 0 9px var(--rc); animation: ghspulse 1.8s ease-in-out infinite; }
.c-val { display: flex; align-items: baseline; gap: 4px; }
.c-val b { font-family: var(--f-mono); font-size: 27px; line-height: 1; color: rgba(143, 166, 196, 0.6); }
.card.on .c-val b { color: var(--rc); text-shadow: 0 0 16px color-mix(in srgb, var(--rc) 60%, transparent); }
.c-val small { font-size: 11px; color: rgba(143, 166, 196, 0.7); }
.c-bar { height: 3px; background: rgba(90, 200, 250, 0.12); overflow: hidden; }
.c-bar i { display: block; height: 100%; background: linear-gradient(90deg, transparent, var(--rc)); transition: width 0.7s ease; }

.tags { flex: 1; display: flex; flex-wrap: wrap; align-content: space-evenly; justify-content: center; gap: 9px 12px; overflow: hidden; }
.tag {
  display: inline-flex; align-items: baseline; gap: 5px; padding: 3px 10px;
  border: 1px solid; border-radius: 2px; line-height: 1.35;
  background: rgba(6, 18, 30, 0.45); white-space: nowrap;
}
.tag i { font-style: normal; font-family: var(--f-mono); font-size: 11px; opacity: 0.75; }

.deck-head { height: 28px; display: flex; align-items: center; justify-content: space-between; }
.deck-head h4 { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 14.5px; letter-spacing: 0.1em; color: #A9D8F0; }
.deck-note { font-size: 12px; color: rgba(143, 166, 196, 0.88); font-family: var(--f-mono); }
.deck {
  position: relative; flex: 1;
  display: grid; grid-template-columns: 1fr 296px; gap: 14px;
  padding: 8px 12px 16px;
}
.deck-grid { display: grid; grid-template-columns: repeat(3, 1fr); grid-template-rows: 1fr 1fr; gap: 12px; }
.mol-panel {
  position: relative; display: flex; flex-direction: column;
  background: linear-gradient(180deg, rgba(10, 30, 50, 0.34), rgba(4, 12, 22, 0.3));
  border: 1px solid rgba(90, 200, 250, 0.2);
  box-shadow: inset 0 0 44px rgba(60, 150, 220, 0.09), 0 12px 30px rgba(0, 0, 0, 0.42);
}
.mol-head {
  display: flex; align-items: baseline; justify-content: space-between; gap: 8px;
  padding: 9px 12px 0;
}
.mol-name { font-size: 14px; font-weight: 600; color: #BFE7FF; letter-spacing: 0.06em; }
.mol-formula { font-family: var(--f-mono); font-size: 12px; color: #E8B33A; }
.mol-stage { flex: 1; min-height: 0; }
.mol-foot {
  display: flex; align-items: center; justify-content: space-between;
  padding: 0 12px 6px; font-family: var(--f-mono); font-size: 11.5px; color: rgba(143, 166, 196, 0.9);
}
.mol-foot b { color: #8FD3F4; font-size: 14px; }
.mol-tabs {
  list-style: none; margin: 0; padding: 0 10px 6px;
  display: flex; flex-wrap: wrap; gap: 5px;
}
.mol-tabs li {
  cursor: pointer; padding: 2px 8px;
  font-family: var(--f-mono); font-size: 11px; letter-spacing: 0.02em;
  color: rgba(143, 166, 196, 0.85);
  border: 1px solid rgba(90, 200, 250, 0.22);
  background: rgba(6, 18, 30, 0.5);
  transition: all 0.18s;
}
.mol-tabs li:hover { color: #BFE7FF; border-color: rgba(90, 200, 250, 0.5); }
.mol-tabs li.on {
  color: #06131F; background: linear-gradient(180deg, #8FD3F4, #5AC8FA);
  border-color: #8FD3F4; font-weight: 600;
  box-shadow: 0 0 14px rgba(90, 200, 250, 0.5);
}
.mol-hint {
  padding: 0 12px 9px; font-size: 10.5px; letter-spacing: 0.1em;
  color: rgba(125, 227, 255, 0.55);
}
.deck::before {
  content: ''; position: absolute; left: -6%; right: -6%; bottom: -10px; height: 38%;
  background-image:
    linear-gradient(rgba(90, 200, 250, 0.14) 1px, transparent 1px),
    linear-gradient(90deg, rgba(90, 200, 250, 0.14) 1px, transparent 1px);
  background-size: 52px 32px;
  transform: perspective(300px) rotateX(72deg); transform-origin: 50% 100%;
  mask-image: linear-gradient(to top, rgba(0, 0, 0, 0.85), transparent 82%);
  pointer-events: none;
}
.cab {
  position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 7px;
  padding: 10px 12px 9px; transform: rotateX(2.4deg); transform-origin: 50% 100%;
  background: linear-gradient(180deg, rgba(16, 44, 68, 0.46), rgba(5, 14, 24, 0.52));
  backdrop-filter: blur(7px) saturate(1.15);
  border: 1px solid rgba(90, 200, 250, 0.22);
  box-shadow: inset 0 1px 0 rgba(200, 240, 255, 0.14), inset 0 -24px 38px rgba(3, 10, 18, 0.5), 0 16px 34px rgba(0, 0, 0, 0.46), 0 0 42px rgba(60, 150, 220, 0.10);
  transition: transform 0.22s, border-color 0.22s, box-shadow 0.22s;
}
.cab:hover { transform: rotateX(0deg) translateY(-5px); }
.cab.is-toxic { border-color: rgba(255, 90, 95, 0.55); }
.cab.is-warn { border-color: rgba(255, 162, 58, 0.5); }
.cab .lock { position: absolute; top: 8px; right: 9px; color: #E8B33A; }
.cab .warn-dot { position: absolute; top: 10px; left: 10px; width: 7px; height: 7px; border-radius: 50%; background: #FFA23A; box-shadow: 0 0 10px #FFA23A; animation: ghspulse 1.5s ease-in-out infinite; }
.cab-code { font-family: var(--f-mono); font-size: 14px; letter-spacing: 0.1em; color: #8FD3F4; }
.cab-name { font-size: 12px; color: #A9C2DC; }
.cab-viz { flex: 1; width: 100%; display: flex; align-items: center; justify-content: center; }
.cab-body {
  display: flex; align-items: flex-end; justify-content: center; gap: 10px; height: 126px; width: 78%;
  padding: 10px 12px 12px; background: rgba(3, 10, 18, 0.6);
  border: 1px solid rgba(90, 200, 250, 0.16);
}
.cab-bottle { position: relative; width: 34px; height: 66px; flex: none; border: 1px solid rgba(180, 215, 240, 0.34); border-radius: 3px 3px 7px 7px; background: rgba(8, 20, 32, 0.55); overflow: hidden; }
.cab-bottle .cliquid { position: absolute; left: 0; right: 0; bottom: 0; height: 64%; background: var(--cc); opacity: 0.82; box-shadow: 0 0 9px color-mix(in srgb, var(--cc) 55%, transparent); }
.cab-bottle .cliquid::after { content: ''; position: absolute; top: 0; left: 0; right: 0; height: 1.5px; background: rgba(255, 255, 255, 0.5); }
.cab-bottle::after { content: ''; position: absolute; left: 3px; top: 4px; bottom: 4px; width: 3px; background: linear-gradient(180deg, rgba(255, 255, 255, 0.38), rgba(255, 255, 255, 0.05)); border-radius: 2px; }
.cab-more { align-self: center; font-family: var(--f-mono); font-size: 12px; color: rgba(143, 166, 196, 0.8); }
.cab-none { align-self: center; font-size: 12px; color: rgba(143, 166, 196, 0.45); letter-spacing: 0.1em; }
.cab-meta { font-size: 11.5px; color: rgba(143, 166, 196, 0.92); font-family: var(--f-mono); }
.cab-meta b { color: #C7DAEC; }

.duo { height: 252px; flex: none; display: grid; grid-template-columns: 1fr 1fr; gap: 18px; }
.panel.half { height: 100%; }
.stack-wrap { flex: 1; display: flex; flex-direction: column; justify-content: center; gap: 16px; }
.stack-note { font-size: 11.5px; color: rgba(143, 166, 196, 0.8); font-family: var(--f-mono); }
.stack-note b { color: #EAF6FF; }

/* 单据量排名条 */
.rank { flex: 1; list-style: none; margin: 0; padding: 2px 0; display: flex; flex-direction: column; justify-content: space-evenly; }
.rank li { display: flex; align-items: center; gap: 10px; }
.rank .r-name { width: 56px; flex: none; font-size: 12px; color: #C7DAEC; }
.rank .r-track { flex: 1; height: 14px; background: rgba(90, 200, 250, 0.10); overflow: hidden; }
.rank .r-track i {
  display: block; height: 100%;
  background: linear-gradient(90deg, rgba(90, 200, 250, 0.25), #5AC8FA, #E8B33A);
  box-shadow: 0 0 14px rgba(90, 200, 250, 0.45);
  transition: width 0.7s ease;
}
.rank .r-val { width: 34px; text-align: right; font-family: var(--f-mono); font-size: 13px; color: #EAF6FF; }
.stack { display: flex; height: 22px; border: 1px solid rgba(90, 200, 250, 0.16); overflow: hidden; }
.stack i { height: 100%; transition: width 0.7s ease; }
.stack-legend { list-style: none; margin: 0; padding: 0; display: grid; grid-template-columns: 1fr 1fr; gap: 5px 12px; }
.stack-legend li { display: flex; align-items: center; gap: 6px; font-size: 12px; color: #A9C2DC; }
.stack-legend i { width: 9px; height: 9px; border-radius: 2px; }
.stack-legend b { margin-left: auto; font-family: var(--f-mono); color: #EAF6FF; }
.stack-legend small { font-family: var(--f-mono); font-size: 10.5px; color: rgba(143, 166, 196, 0.7); width: 34px; text-align: right; }

.occ-panel { height: 168px; flex: none; }
.occ { flex: 1; list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; justify-content: space-evenly; }
.occ li { display: flex; align-items: center; gap: 9px; }
.o-code { width: 62px; flex: none; font-family: var(--f-mono); font-size: 11.5px; color: #8FD3F4; }
.o-track { flex: 1; height: 8px; background: rgba(90, 200, 250, 0.10); overflow: hidden; }
.o-track i { display: block; height: 100%; transition: width 0.7s ease; }
.o-val { width: 46px; text-align: right; font-family: var(--f-mono); font-size: 11.5px; color: rgba(143, 166, 196, 0.9); }

.barrel-panel { flex: 1; min-height: 150px; }
.barrels { flex: 1; display: flex; align-items: flex-end; justify-content: space-around; padding: 4px 4px 8px; }
.barrel { display: flex; flex-direction: column; align-items: center; gap: 5px; }
.blid { width: 62px; height: 7px; background: rgba(180, 215, 240, 0.32); border-radius: 3px 3px 0 0; }
.bbody { position: relative; width: 56px; height: 82px; border: 1px solid rgba(180, 215, 240, 0.28); border-radius: 4px 4px 8px 8px; background: rgba(8, 20, 32, 0.55); overflow: hidden; }
.bfill { position: absolute; left: 0; right: 0; bottom: 0; background: linear-gradient(180deg, rgba(255, 162, 58, 0.8), rgba(255, 162, 58, 0.32)); transition: height 0.6s ease; }
.bglow { position: absolute; left: 0; right: 0; height: 2px; background: rgba(255, 214, 150, 0.8); transition: bottom 0.6s ease; }
.bname { font-size: 12px; color: #A9C2DC; }
.bnum { font-family: var(--f-mono); font-size: 17px; color: #FFA23A; text-shadow: 0 0 14px rgba(255, 162, 58, 0.6); }
.bnum small { font-size: 10.5px; color: rgba(143, 166, 196, 0.7); }

.hud-foot {
  position: relative; z-index: 3; height: 28px;
  display: flex; justify-content: space-between; align-items: center; gap: 16px;
  border-top: 1px solid rgba(90, 200, 250, 0.12);
  font-size: 12px; color: rgba(143, 166, 196, 0.75); letter-spacing: 0.05em;
}
.hud-foot .law { color: rgba(143, 211, 244, 0.62); letter-spacing: 0.18em; }
.hud-foot .src { font-family: var(--f-mono); font-size: 11.5px; }

@keyframes ghspulse { 0%, 100% { opacity: 0.62; } 50% { opacity: 1; } }
@keyframes scanline { 0% { top: -240px; } 100% { top: 1120px; } }
@media (prefers-reduced-motion: reduce) {
  .card.on .c-dot, .cab .warn-dot, .stage::after { animation: none; }
}
</style>
