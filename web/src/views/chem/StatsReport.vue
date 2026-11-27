<template>
  <div>
    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>统计报表按分类分组汇总台账数与现存总量；仅学院/校级监督角色可查看，越权访问将被拒绝。</span>
    </div>

    <div class="stats-grid">
      <div class="page-card chart-card">
        <div class="card-head"><span class="sec-title">分类库存汇总表</span></div>
        <el-table :data="ledger" size="small" stripe show-summary :summary-method="summaryMethod">
          <el-table-column label="分类" width="170">
            <template #default="{ row }">
              <GhzTag :category="row.category" :size="22" with-bg />
              <span style="margin-left: 6px">{{ categoryName(row.category) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chem_count" label="台账数" width="100" align="right" />
          <el-table-column prop="total_stock" label="Σ现存" align="right" />
        </el-table>
      </div>
      <div class="page-card chart-card">
        <div class="card-head"><span class="sec-title">分类现存分布</span></div>
        <div ref="pieRef" class="chart-box" />
      </div>
    </div>
    <div class="page-card chart-card" style="margin-top: 14px">
      <div class="card-head"><span class="sec-title">台账数与现存双轴对比</span></div>
      <div ref="barRef" class="chart-box wide" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import * as echarts from 'echarts'
import { InfoFilled } from '@element-plus/icons-vue'
import GhzTag from '../../components/GhzTag.vue'
import { statsLedger } from '../../api/chem'
import { CATEGORIES } from '../../utils/constants'

const ledger = ref([])
const pieRef = ref(null)
const barRef = ref(null)
let pieChart = null
let barChart = null

const categoryName = c => (CATEGORIES[c] || CATEGORIES[0]).name

const summaryMethod = () => {
  const totalStock = ledger.value.reduce((s, r) => s + Number(r.total_stock || 0), 0)
  const totalCount = ledger.value.reduce((s, r) => s + Number(r.chem_count || 0), 0)
  return ['合计', String(totalCount), String(totalStock)]
}

const AXIS_TEXT = { color: '#7A8494', fontSize: 11 }
const SPLIT = { lineStyle: { color: '#EEF0F3' } }

function renderCharts() {
  if (!pieRef.value || !barRef.value) return
  const cats = ledger.value.map(r => categoryName(r.category))
  const colors = ledger.value.map(r => (CATEGORIES[r.category] || CATEGORIES[0]).color)

  pieChart = echarts.init(pieRef.value)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11, color: '#7A8494' } },
    series: [{
      type: 'pie', radius: ['46%', '66%'], center: ['50%', '44%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { formatter: '{b}  {c}', color: '#3D4756', fontSize: 11 },
      labelLine: { length: 12, length2: 10, lineStyle: { color: '#D2D7DF' } },
      data: ledger.value.map((r, i) => ({ name: categoryName(r.category), value: Number(r.total_stock || 0), itemStyle: { color: colors[i] } }))
    }]
  })

  barChart = echarts.init(barRef.value)
  barChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['台账数', 'Σ现存'], right: 8, top: 0, icon: 'rect', itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 11, color: '#7A8494' }, itemGap: 26 },
    grid: { left: 44, right: 52, bottom: 34, top: 34, containLabel: false },
    xAxis: {
      type: 'category', data: cats,
      axisLine: { lineStyle: { color: '#D2D7DF' } },
      axisTick: { show: false },
      axisLabel: { ...AXIS_TEXT }
    },
    yAxis: [
      { type: 'value', name: '台账数', nameTextStyle: { ...AXIS_TEXT, align: 'left' }, axisLabel: { ...AXIS_TEXT }, splitLine: SPLIT },
      { type: 'value', name: 'Σ现存', nameTextStyle: { ...AXIS_TEXT, align: 'right' }, axisLabel: { ...AXIS_TEXT }, splitLine: { show: false } }
    ],
    series: [
      { name: '台账数', type: 'bar', barWidth: '34%', data: ledger.value.map(r => Number(r.chem_count || 0)), itemStyle: { color: '#E5901D', borderRadius: [2, 2, 0, 0] } },
      { name: 'Σ现存', type: 'line', yAxisIndex: 1, data: ledger.value.map(r => Number(r.total_stock || 0)), symbol: 'circle', symbolSize: 6, itemStyle: { color: '#3D4756' }, lineStyle: { width: 2, color: '#3D4756' }, smooth: false }
    ]
  })
}

const onResize = () => { pieChart?.resize(); barChart?.resize() }

onMounted(async () => {
  ledger.value = (await statsLedger()) || []
  renderCharts()
  window.addEventListener('resize', onResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.stats-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.chart-card { padding: 14px 16px; }
.card-head { margin-bottom: 8px; }
.sec-title { font-weight: 700; color: var(--c-graphite-deep); }
.chart-box { height: 300px; }
.chart-box.wide { height: 320px; }
@media (max-width: 1100px) { .stats-grid { grid-template-columns: 1fr; } }
</style>
