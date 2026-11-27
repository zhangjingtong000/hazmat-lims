<template>
  <div class="page-wrap" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="eyebrow">CHEMICAL PROFILE · 台账档案</div>
        <div class="page-title" style="margin-top: 4px">化学品档案详情</div>
      </div>
      <div>
        <el-button @click="$router.push('/chem/info')">← 返回台账</el-button>
        <el-button type="primary" plain @click="$router.push(`/print/label/${route.params.id}`)">打印 GHS 标签</el-button>
      </div>
    </div>

    <div v-if="info" class="page-card profile-head">
      <div class="profile-ghz">
        <GhzTag :category="info.category" :size="72" with-bg />
      </div>
      <div class="profile-main">
        <div class="profile-title">
          <span class="page-title" style="font-size: 1.35rem">{{ info.name }}</span>
          <span v-if="info.alias" class="profile-alias">（{{ info.alias }}）</span>
          <span class="cat-name" :class="'cat-' + categoryOf(info.category).key.toLowerCase()">
            {{ categoryOf(info.category).key }} · {{ categoryOf(info.category).name }}
          </span>
          <el-tag :type="info.status === 0 ? 'success' : info.status === 1 ? 'warning' : 'info'" style="margin-left: 6px">
            {{ INFO_STATUS[info.status] }}
          </el-tag>
        </div>
        <div class="profile-code">
          <span class="doc-code" style="font-size: 1.05rem">{{ info.code }}</span>
          <span class="mono cas">CAS {{ info.casNo }}</span>
        </div>
        <el-alert v-if="info.firstAid" type="error" :closable="false" show-icon class="first-aid"
          :title="`应急处置要点（档案随取随用）：${info.firstAid}`" />
        <div class="profile-meta">
          <span>规格：{{ info.spec || '—' }} / {{ info.unit || '—' }}</span>
          <span>主相容组 <b class="mono">{{ info.compatGroup }}</b>
            <template v-if="info.compatGroup2">／次组 <b class="mono">{{ info.compatGroup2 }}</b></template>
          </span>
          <span>现存 <b class="stock-num">{{ info.stockQty }}</b> {{ info.unit }}（下限 {{ info.minStock || 0 }}）</span>
          <span>效期预警 {{ info.expiryWarnDays }} 天</span>
          <span v-if="info.maxOnce">剧毒单次上限 {{ info.maxOnce }}</span>
          <span>象形图 <b class="mono">{{ info.ghzPictogram || '—' }}</b></span>
        </div>
      </div>
    </div>

    <!-- 批次表（FEFO 序） -->
    <div class="page-card sec-card">
      <div class="card-head">
        <span class="page-title">在库批次（按效期先到期先出）</span>
        <span class="cell-sub">在库批次余量合计 = 台账现存，账物守恒</span>
      </div>
      <el-table :data="batches" size="small" stripe>
        <el-table-column prop="lotNo" label="批号" min-width="140">
          <template #default="{ row }"><span class="mono">{{ row.lotNo }}</span></template>
        </el-table-column>
        <el-table-column label="余量/入量" width="130" align="right">
          <template #default="{ row }">{{ row.qtyLeft }} / {{ row.qtyIn }}</template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="效期" width="110">
          <template #default="{ row }">
            <span :class="{ 'exp-danger': isExpired(row), 'exp-warn': isNearExpiry(row) }">{{ row.expiryDate || '—' }}</span>
            <el-tag v-if="isExpired(row)" size="small" type="danger" effect="plain" style="margin-left: 4px">已过期</el-tag>
            <el-tag v-else-if="isNearExpiry(row)" size="small" type="warning" effect="plain" style="margin-left: 4px">临期</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cabinetName" label="柜位" min-width="110">
          <template #default="{ row }">{{ row.cabinetName || row.cabinetId || '—' }}</template>
        </el-table-column>
        <el-table-column label="批次状态" width="100">
          <template #default="{ row }">
            <el-tag :type="BATCH_STATUS[row.status]?.type" size="small">{{ BATCH_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inTime" label="入库时间" width="170">
          <template #default="{ row }"><span class="cell-sub">{{ row.inTime }}</span></template>
        </el-table-column>
        <el-table-column label="入库单溯源" width="110">
          <template #default="{ row }">
            <el-link v-if="row.stockInId" type="primary" @click="$router.push('/chem/stockin')">RK-#{{ row.stockInId }}</el-link>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!batches.length && !loading" description="暂无在库批次——库存来自入库单落账" :image-size="70" />
    </div>

    <!-- GHS 标签预览（打印模板数据源：象形图 + 急救要点） -->
    <template v-if="info">
      <div class="page-card sec-card">
        <div class="card-head"><span class="page-title">GHS 警示标签预览</span></div>
      <div class="label-preview">
        <div class="label-box">
          <div class="label-row">
            <GhzTag :category="info.category" :size="54" with-bg />
            <div class="label-name">
              <b>{{ info.name }}</b>
              <div class="mono">{{ info.casNo }}</div>
            </div>
          </div>
          <div class="label-line">象形图：{{ info.ghzPictogram || '—' }} ｜ 规格：{{ info.spec || '—' }}</div>
          <div class="label-line label-aid" v-if="info.firstAid">急救：{{ info.firstAid }}</div>
        </div>
        <div class="cell-sub" style="max-width: 320px">
          标签随批次打印张贴于柜面；象形图与急救要点进打印模板——"应急有据"落点。
        </div>
      </div>
    </div>

    <!-- 流转记录（操作日志，按本单据/化学品关联） -->
    <div class="page-card sec-card">
      <div class="card-head"><span class="page-title">流转记录（写操作留痕，失败亦记）</span></div>
      <el-table :data="logs" size="small" stripe>
        <el-table-column prop="operTime" label="时间" width="170" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" min-width="220" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column prop="status" label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '成功' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!logs.length && !loading" description="暂无留痕记录" :image-size="60" />
    </div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import GhzTag from '../../components/GhzTag.vue'
import { chemInfoDetail } from '../../api/chem'
import { listOperLog } from '../../api/system'
import { categoryOf, INFO_STATUS, BATCH_STATUS } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const info = ref(null)
const batches = ref([])
const logs = ref([])

const isExpired = b => b.expiryDate && String(b.expiryDate) < today()
const isNearExpiry = b => {
  if (!b.expiryDate || isExpired(b)) return false
  const days = Number(info.value?.expiryWarnDays || 30)
  const diff = (new Date(b.expiryDate) - new Date(today())) / 86400000
  return diff <= days
}
const today = () => new Date().toISOString().slice(0, 10)

onMounted(async () => {
  try {
    const vo = await chemInfoDetail(route.params.id)
    info.value = vo.info
    batches.value = vo.batches || []
  } finally {
    loading.value = false
  }
  // 流转记录：按化学品名匹配留痕（oper_log 无 biz 外键，前端按关键词过滤）
  if (info.value) {
    const data = await listOperLog({ keyword: info.value.name, page: 1, size: 20 }).catch(() => null)
    logs.value = data?.records || []
  }
})
</script>

<style scoped>
.profile-head { display: flex; gap: 22px; padding: 18px 22px; }
.profile-ghz { flex: none; padding-top: 4px; }
.profile-main { flex: 1; min-width: 0; }
.profile-title { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.profile-alias { color: var(--c-graphite-soft); }
.cat-name { font-weight: 600; font-size: 0.86rem; color: var(--c-graphite); }
.profile-code { display: flex; gap: 16px; align-items: baseline; margin: 6px 0; }
.cas { color: var(--c-graphite-soft); }
.first-aid { margin: 8px 0; }
.profile-meta { display: flex; flex-wrap: wrap; gap: 8px 22px; color: var(--c-graphite); font-size: 0.86rem; margin-top: 6px; }
.stock-num { color: var(--c-safe-deep); font-size: 1.05rem; }
.mono { font-family: var(--f-mono); }
.sec-card { padding: 14px 18px; }
.exp-danger { color: var(--c-jd); font-weight: 700; }
.exp-warn { color: var(--c-yb); font-weight: 600; }
.label-preview { display: flex; gap: 26px; align-items: center; flex-wrap: wrap; }
.label-box {
  width: 380px; border: 2px solid var(--c-jd); border-radius: 6px; padding: 12px 14px;
  background: #fff;
}
.label-row { display: flex; gap: 12px; align-items: center; margin-bottom: 8px; }
.label-name b { font-size: 1.05rem; }
.label-line { font-size: 0.8rem; color: var(--c-graphite); line-height: 1.6; }
.label-aid { border-top: 1px dashed var(--c-line); margin-top: 6px; padding-top: 6px; color: var(--c-jd); }
</style>
