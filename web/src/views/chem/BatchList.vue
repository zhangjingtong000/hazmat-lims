<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">BATCH SUPERVISION · 先到期先出</div>
        <div class="page-title" style="margin-top: 4px">批次监管</div>
      </div>
      <div class="head-actions">
        <el-button :loading="exporting" @click="doExport">导出</el-button>
        <span class="cell-sub">今日：{{ today }} —— 过期批次已自动标记</span>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>批次为最小监管单元：出库一律先到期先出、同效期按批号先后；移柜时重新校验禁配要求，与柜内化学品冲突即拦截；过期批次自动封存并即时扣减台账。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-select v-model="filters.chemicalId" filterable clearable placeholder="按化学品过滤" style="width: 280px" @change="load(1)">
          <el-option v-for="c in chems" :key="c.id" :label="`${c.code} ${c.name}`" :value="c.id" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="chemicalName" label="化学品" min-width="140" />
        <el-table-column prop="lotNo" label="批号" width="140">
          <template #default="{ row }"><span class="mono">{{ row.lotNo }}</span></template>
        </el-table-column>
        <el-table-column label="余量/入量" width="110" align="right">
          <template #default="{ row }">
            <span :class="{ 'zero-qty': Number(row.qtyLeft) === 0 }">{{ row.qtyLeft }}</span> / {{ row.qtyIn }}
          </template>
        </el-table-column>
        <el-table-column label="效期" width="150">
          <template #default="{ row }">
            <span :class="{ 'exp-danger': isExpired(row) }">{{ row.expiryDate || '—' }}</span>
            <el-tag v-if="isExpired(row)" size="small" type="danger" effect="plain" style="margin-left: 4px">过期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="柜位" width="130">
          <template #default="{ row }">{{ row.cabinetName || row.cabinetId || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="BATCH_STATUS[row.status]?.type" size="small">{{ BATCH_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="inTime" label="入库时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 && hasPerm('hzm:batch:move')" size="small" text type="warning" @click="openMove(row)">移柜</el-button>
            <el-button size="small" text type="primary" @click="$router.push(`/chem/info/${row.chemicalId}`)">台账</el-button>
            <el-button size="small" text type="primary" @click="$router.push(`/print/label/${row.chemicalId}`)">标签</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <el-dialog v-model="moveVisible" title="批次移柜（移入前校验禁配）" width="480px">
      <el-form ref="moveFormRef" :model="moveForm" label-width="100px">
        <el-form-item label="当前柜位">
          <el-input :value="`${moveRow?.cabinetName || moveRow?.cabinetId || '—'}`" disabled />
        </el-form-item>
        <el-form-item label="目标柜位" prop="target" required :rules="R.requiredPick('目标柜位')">
          <el-select v-model="moveForm.target" style="width: 100%">
            <el-option v-for="cb in cabinets.filter(c => c.id !== moveRow?.cabinetId)" :key="cb.id"
              :label="`${cb.code} ${cb.name}（${CABINET_TYPE[cb.type]}${cb.dualLock ? ' · 双锁' : ''}）`" :value="cb.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div class="cell-sub">目标柜与主/次相容组任一冲突即不予移柜（并提示冲突品名）；柜位整改闭环：发现冲突→移柜。</div>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="doMove">执行移柜</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listBatch, moveBatchCabinet, listChemInfo, listCabinet } from '../../api/chem'
import { hasPerm } from '../../store/auth'
import { BATCH_STATUS, CABINET_TYPE } from '../../utils/constants'
import R from '../../utils/validation'
import { exportExcel } from '../../utils/export'

const filters = reactive({ chemicalId: null })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const exporting = ref(false)
const chems = ref([])
const cabinets = ref([])
const moveVisible = ref(false)
const moveRow = ref(null)
const moveForm = reactive({ target: null })
const moveFormRef = ref(null)
const saving = ref(false)
const today = new Date().toISOString().slice(0, 10)

const isExpired = b => b.expiryDate && String(b.expiryDate) < today

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listBatch({
      chemicalId: filters.chemicalId ?? undefined,
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openMove(row) {
  moveRow.value = row
  moveForm.target = null
  moveVisible.value = true
  nextTick(() => moveFormRef.value?.clearValidate())
}

async function doMove() {
  const valid = await moveFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请选择目标柜位')
    return
  }
  saving.value = true
  try {
    await moveBatchCabinet(moveRow.value.id, moveForm.target)
    ElMessage.success('移柜完成（禁配校验通过）')
    moveVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

/** 导出：遵循当前化学品筛选条件 */
async function doExport() {
  exporting.value = true
  try {
    await exportExcel({
      business: '批次台账',
      columns: [
        { prop: 'chemicalName', label: '化学品' },
        { prop: 'lotNo', label: '批号' },
        { prop: 'qtyLeft', label: '余量' },
        { prop: 'qtyIn', label: '入库量' },
        { prop: 'expiryDate', label: '效期' },
        { prop: row => (String(row.expiryDate || '') < today ? '已过期' : ''), label: '过期标记' },
        { prop: row => row.cabinetName || row.cabinetId || '', label: '柜位' },
        { prop: row => BATCH_STATUS[row.status]?.label || row.status, label: '状态' },
        { prop: 'inTime', label: '入库时间' }
      ],
      fetchPage: (p, s) => listBatch({ chemicalId: filters.chemicalId ?? undefined, page: p, size: s })
    })
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  load()
  listChemInfo({ page: 1, size: 200 }).then(d => { chems.value = d.records || [] }).catch(() => {})
  listCabinet().then(d => { cabinets.value = d || [] }).catch(() => {})
})
</script>

<style scoped>
.filter-card { padding: 14px 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { padding: 6px 12px 12px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.exp-danger { color: var(--c-jd); font-weight: 700; }
.zero-qty { color: var(--c-graphite-soft); }
.head-actions { display: flex; gap: 12px; align-items: center; }
</style>
