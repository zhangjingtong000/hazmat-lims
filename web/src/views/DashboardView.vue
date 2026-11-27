<template>
  <div class="page-wrap">
    <!-- 页头：标题 + 口径小注 -->
    <div class="page-header">
      <div>
        <div class="eyebrow">LABORATORY HAZMAT CONTROL · 实验室危化品总控</div>
        <div class="page-title" style="margin-top: 4px">首页概览</div>
      </div>
      <div class="regimen-bar">
        <el-icon><WarningFilled /></el-icon>
        <span>预警口径依《管理办法》第 12 条：效期临近 / 低于安全库存 / 单据待审 / 已批超 7 天未入库，先看数、再处置。</span>
      </div>
    </div>

    <!-- 四类预警：图标色块 + 等宽数字，无装饰边线 -->
    <div class="warn-grid">
      <div v-for="w in warns" :key="w.key" class="warn-tile" @click="w.go">
        <div class="warn-ico">
          <el-icon :size="19"><component :is="w.icon" /></el-icon>
        </div>
        <div class="warn-body">
          <div class="warn-label-row">
            <span class="warn-name">{{ w.label }}</span>
            <span class="warn-badge" :class="Number(w.value) > 0 ? 'hot' : 'calm'">
              {{ Number(w.value) > 0 ? '需处理' : '正常' }}
            </span>
          </div>
          <div class="warn-num-row">
            <span class="warn-num" :class="{ zero: Number(w.value) === 0 }">{{ w.value }}</span>
            <span class="warn-unit">项</span>
          </div>
          <div class="warn-desc">{{ w.desc }}</div>
        </div>
      </div>
    </div>

    <div class="dash-cols">
      <div class="dash-main">
      <!-- 待办列表 -->
      <div class="page-card dash-todo">
        <div class="card-head">
          <div>
            <span class="page-title">待办单据</span>
            <div class="card-sub">依单据推进，勿积压——先审先生效</div>
          </div>
          <el-button size="small" text type="primary" @click="$router.push('/chem/todo')">进入审批中心 →</el-button>
        </div>
        <el-tabs v-model="todoTab">
          <el-tab-pane :label="`待审申购 ${todo.purchaseCount || 0}`" name="p">
            <el-table :data="todo.purchases || []" size="small">
              <el-table-column prop="code" label="单号" width="160">
                <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
              </el-table-column>
              <el-table-column prop="chemicalName" label="化学品" min-width="140" />
              <el-table-column prop="applicantName" label="申请人" width="130" />
              <el-table-column label="操作" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" text type="primary" @click="$router.push(`/chem/purchase?focus=${row.id}`)">处置</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`待签领用 ${todo.requisitionCount || 0}`" name="r">
            <el-table :data="todo.requisitions || []" size="small">
              <el-table-column prop="code" label="单号" width="160">
                <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
              </el-table-column>
              <el-table-column prop="chemicalName" label="化学品" min-width="140" />
              <el-table-column prop="applicantName" label="领用人" width="130" />
              <el-table-column label="操作" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" text type="primary" @click="$router.push(`/chem/req/${row.id}`)">处置</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`待审废弃 ${todo.wasteCount || 0}`" name="w">
            <el-table :data="todo.wastes || []" size="small">
              <el-table-column prop="code" label="单号" width="160">
                <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
              </el-table-column>
              <el-table-column prop="chemicalName" label="化学品" min-width="140" />
              <el-table-column prop="operatorName" label="经办人" width="100" />
              <el-table-column label="操作" width="80" align="right">
                <template #default="{ row }">
                  <el-button size="small" text type="primary" @click="$router.push(`/chem/waste/${row.id}`)">处置</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>

        <div class="page-card mix-card">
          <div class="card-head">
            <div>
              <span class="page-title">批次与处置概况</span>
              <div class="card-sub">批次状态分布 · 废弃物处置构成</div>
            </div>
            <span class="mix-note">数据口径：全部在库批次 / 处置单按类型计数</span>
          </div>
          <div class="mix-charts">
            <div ref="batchRef" class="mix-chart" />
            <div v-if="!wasteEmpty" ref="wasteRef" class="mix-chart" />
            <div v-else class="mix-empty">
              <el-icon :size="20"><Delete /></el-icon>
              <span>暂无处置记录</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：分类概览 + 快捷入口 -->
      <div class="dash-side">
        <div class="page-card ledger-mini">
          <div class="card-head">
            <div>
              <span class="page-title">分类库存概览</span>
              <div class="card-sub">Σ 在库批次 = 台账现存 · 账物守恒</div>
            </div>
          </div>
          <div v-for="row in ledger" :key="row.category" class="ledger-row">
            <GhzTag :category="row.category" :size="26" with-bg />
            <div class="ledger-main">
              <div class="ledger-line1">
                <span class="ledger-name">{{ categoryName(row.category) }}</span>
                <span class="ledger-nums"><b>{{ row.chem_count }}</b> 种 · 现存 <b>{{ row.total_stock }}</b></span>
              </div>
              <div class="ledger-names" :title="row.chem_names">{{ row.chem_names }}</div>
            </div>
          </div>
          <el-empty v-if="!ledger.length" description="暂无台账" :image-size="56" />
        </div>

        <div class="page-card quick-card">
          <div class="card-head">
            <div>
              <span class="page-title">快捷入口</span>
            </div>
          </div>
          <div class="quick-grid">
            <button v-for="q in quicks" :key="q.path" class="quick-tile" @click="$router.push(q.path)">
              <el-icon :size="17"><component :is="q.icon" /></el-icon>
              <span>{{ q.label }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 上手引导：规则反例墙（双对象构造才可触发；只发必然失败的请求） -->
    <RuleWall />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import {
  WarningFilled, Box, ShoppingCart, Van, DataAnalysis, Timer, Odometer, Files, Stamp, Delete
} from '@element-plus/icons-vue'
import GhzTag from '../components/GhzTag.vue'
import RuleWall from '../components/RuleWall.vue'
import { statsDashboard, statsLedger, getTodo, statsScreen } from '../api/chem'
import * as echarts from 'echarts'
import { onBeforeUnmount } from 'vue'
import { CATEGORIES } from '../utils/constants'
import router from '../router'

const dashboard = ref({})
const todo = ref({})
const ledger = ref([])

const warns = computed(() => [
  { key: 'expiry', label: '效期临近', value: dashboard.value.expiryWarn ?? '-', desc: '批次效期在预警期内或已过期，发放按先到期先出', icon: Timer, go: () => router.push('/chem/batch') },
  { key: 'low', label: '低于安全库存', value: dashboard.value.lowStock ?? '-', desc: '现存低于安全下限的在管台账', icon: Odometer, go: () => router.push('/chem/info') },
  { key: 'pending', label: '单据待审', value: dashboard.value.pendingDocs ?? '-', desc: '申购 / 领用 / 废弃三类待办合计', icon: Files, go: () => router.push('/chem/todo') },
  { key: 'overdue', label: '已批超期未入库', value: dashboard.value.overdueStockin ?? '-', desc: '已批超 7 天未入库（已作废除外）', icon: Stamp, go: () => router.push('/chem/purchase?status=2') }
])

const todoTab = ref('p')
const categoryName = c => (CATEGORIES[c] || CATEGORIES[0]).name

const quicks = [
  { label: '新建申购', path: '/chem/purchase', icon: ShoppingCart },
  { label: '入库登记', path: '/chem/stockin', icon: Van },
  { label: '领用出库', path: '/chem/requisition', icon: Box },
  { label: '统计报表', path: '/chem/stats', icon: DataAnalysis }
]

const batchRef = ref(null)
const wasteRef = ref(null)
const wasteEmpty = ref(false)
let batchChart = null
let wasteChart = null

const BATCH_STATUS = { 0: { name: '在库', color: '#15803D' }, 1: { name: '耗尽', color: '#A5ADBB' }, 2: { name: '废弃', color: '#B91C1C' }, 3: { name: '过期封存', color: '#CA8A04' } }
const WASTE_TYPE = { 1: { name: '废液', color: '#D97706' }, 2: { name: '空瓶', color: '#7C3AED' }, 3: { name: '过期整批', color: '#B91C1C' } }

function renderMix(screen) {
  if (batchRef.value) {
    batchChart = echarts.init(batchRef.value)
    batchChart.setOption({
      title: { text: '批次状态', left: 'center', top: 0, textStyle: { fontSize: 12, color: '#7A8494', fontWeight: 600 } },
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, icon: 'circle', itemWidth: 7, itemHeight: 7, itemGap: 10, textStyle: { fontSize: 10, color: '#7A8494' } },
      series: [{
        type: 'pie', radius: ['52%', '74%'], center: ['50%', '52%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: (screen.batchStatusDist || []).map(r => ({
          name: (BATCH_STATUS[r.status] || {}).name || r.status,
          value: Number(r.cnt || 0),
          itemStyle: { color: (BATCH_STATUS[r.status] || {}).color || '#A5ADBB' }
        }))
      }]
    })
  }
  if (wasteRef.value) {
    const rows = (screen.disposalStats || []).map(r => ({
      name: (WASTE_TYPE[r.type] || {}).name || r.type,
      value: Number(r.cnt || 0),
      itemStyle: { color: (WASTE_TYPE[r.type] || {}).color || '#A5ADBB' }
    }))
    // 无处置数据：不渲染环形图，改渲染与左图等高的紧凑空态
    if (!rows.length) {
      wasteEmpty.value = true
      return
    }
    wasteChart = echarts.init(wasteRef.value)
    wasteChart.setOption({
      title: {
        text: '处置构成',
        left: 'center', top: 0,
        textStyle: { fontSize: 12, color: '#7A8494', fontWeight: 600, lineHeight: 18 }
      },
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['52%', '74%'], center: ['50%', '52%'],
        itemStyle: { borderColor: '#fff', borderWidth: 2 },
        label: { show: false },
        data: rows
      }]
    })
  }
}

const onResize = () => { batchChart?.resize(); wasteChart?.resize() }

onMounted(async () => {
  dashboard.value = await statsDashboard()
  todo.value = await getTodo()
  ledger.value = await statsLedger()
  renderMix(await statsScreen().catch(() => ({})))
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  batchChart?.dispose()
  wasteChart?.dispose()
})
</script>

<style scoped>
/* ── 预警砖：图标色块 + 等宽数字，无边线装饰 ── */
.warn-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.warn-tile {
  display: flex; align-items: center; gap: 14px;
  background: #fff; border: 1px solid var(--c-line);
  border-radius: var(--r-lg);
  padding: 16px 16px;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s;
  position: relative;
}
.warn-tile:hover { border-color: var(--c-safe); box-shadow: var(--shadow-2); }
.warn-ico {
  flex: none; width: 42px; height: 42px; border-radius: var(--r);
  display: flex; align-items: center; justify-content: center;
}
.warn-ico { background: #F3F4F6; color: #4B5563; }
.warn-body { flex: 1; min-width: 0; }
.warn-num-row { display: flex; align-items: baseline; gap: 6px; }
.warn-num { font-family: var(--f-mono); font-size: 2rem; font-weight: 800; color: var(--c-graphite-deep); line-height: 1.15; letter-spacing: -0.02em; }
.warn-num.zero { color: var(--c-graphite-faint); font-size: 1.5rem; font-weight: 700; }
.warn-unit { font-size: 0.7rem; color: var(--c-graphite-faint); }
.warn-label-row { display: flex; align-items: center; gap: 8px; }
.warn-name { font-weight: 600; color: var(--c-graphite-soft); font-size: 0.78rem; }
.warn-badge {
  font-size: 0.62rem; font-weight: 700; letter-spacing: 0.06em;
  padding: 1.5px 7px; border-radius: 999px;
}
.warn-badge.hot { background: var(--c-safe); color: #fff; }
.warn-badge.calm { background: #EEF0F3; color: var(--c-graphite-faint); }
.warn-desc { font-size: 0.72rem; color: var(--c-graphite-soft); margin-top: 3px; line-height: 1.5; }

/* ── 主栏/侧栏 ── */
.dash-cols { display: grid; grid-template-columns: 1.9fr 1fr; gap: 12px; align-items: start; }
.dash-main { display: flex; flex-direction: column; gap: 12px; }
.mix-note { font-size: 0.72rem; color: var(--c-graphite-faint); }
.dash-todo { padding: 16px 18px; }
.dash-side { display: flex; flex-direction: column; gap: 12px; }
.ledger-mini { padding: 16px 18px; }

/* 分类概览：菱标 + 两行结构（行1 名称+数字，行2 品名小字截断） */
.ledger-row { display: flex; align-items: center; gap: 12px; padding: 9px 2px; border-bottom: 1px solid var(--c-line-faint); }
.ledger-row:last-child { border-bottom: none; }
.ledger-main { flex: 1; min-width: 0; }
.ledger-line1 { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; }
.ledger-name { font-weight: 700; color: var(--c-graphite); font-size: 0.88rem; white-space: nowrap; }
.ledger-nums { font-size: 0.78rem; color: var(--c-graphite-soft); white-space: nowrap; }
.ledger-nums b { font-family: var(--f-mono); color: var(--c-graphite-deep); font-weight: 700; }
.ledger-names {
  font-size: 0.72rem; color: var(--c-graphite-faint);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 100%;
}

/* ── 批次/处置小图 ── */
.mix-card { padding: 16px 18px; }
.mix-charts { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.mix-chart { height: 150px; }
/* 处置构成空态：与左环形图等高的紧凑占位 */
.mix-empty {
  height: 150px;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px;
  color: var(--c-graphite-faint); font-size: 0.78rem;
}
.mix-empty .el-icon { color: var(--c-graphite-faint); }

/* ── 快捷入口：统一等大瓷贴 ── */
.quick-card { padding: 16px 18px; }
.quick-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.quick-tile {
  display: flex; align-items: center; justify-content: center; gap: 8px;
  height: 44px;
  background: #fff;
  border: 1px solid var(--c-line);
  border-radius: var(--r);
  color: var(--c-graphite);
  font-size: 0.86rem; font-weight: 600;
  font-family: var(--f-body);
  cursor: pointer;
  transition: all 0.15s;
}
.quick-tile:hover { border-color: var(--c-safe); color: var(--c-safe-deep); background: var(--c-safe-bg); }

@media (max-width: 1100px) {
  .warn-grid { grid-template-columns: repeat(2, 1fr); }
  .dash-cols { grid-template-columns: 1fr; }
}
</style>
