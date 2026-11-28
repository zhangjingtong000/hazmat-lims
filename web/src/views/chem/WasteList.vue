<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">WASTE DISPOSAL · 去向可追</div>
        <div class="page-title" style="margin-top: 4px">废弃处置</div>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('hzm:waste:submit')" type="primary" @click="openCreate">+ 新建处置单</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>废弃处置一单一化学品一批次（混合废液拆单）；废液/空瓶处置即库存销账，过期整批仅核销过期封存批次；剧毒/易制毒/易制爆处置完成须填转移凭证号——去向可追。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="单号" clearable style="width: 180px" @keyup.enter="load(1)" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="待审批" :value="0" /><el-option label="已处置" :value="1" /><el-option label="已驳回" :value="2" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="单号" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/chem/waste/${row.id}`)"><span class="doc-code">{{ row.code }}</span></el-link>
          </template>
        </el-table-column>
        <el-table-column prop="chemicalName" label="化学品" min-width="130" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="Number(row.type) === 3 ? 'danger' : 'warning'">{{ WASTE_TYPE[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="batchId" label="批次" width="110">
          <template #default="{ row }"><span class="mono">#{{ row.batchId }}</span></template>
        </el-table-column>
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.qty ?? '整批' }}</template>
        </el-table-column>
        <el-table-column prop="method" label="方式" min-width="120" show-overflow-tooltip />
        <el-table-column prop="disposalTarget" label="处置去向" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.disposalTarget || '—' }}</template>
        </el-table-column>
        <el-table-column prop="voucherNo" label="凭证号" width="120">
          <template #default="{ row }"><span class="mono cell-sub">{{ row.voucherNo || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="operatorName" label="经办人" width="90" />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="WASTE_STATUS[row.status]?.type" size="small">{{ WASTE_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/chem/waste/${row.id}`)">详情</el-button>
            <el-button size="small" text type="primary" @click="$router.push(`/print/waste/${row.id}`)">打印处置单</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 新建处置单 -->
    <el-dialog v-model="createVisible" title="新建废弃物处置单（FQ）" width="560px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="化学品" prop="chemicalId" :rules="R.requiredPick('化学品')">
          <el-select v-model="form.chemicalId" filterable style="width: 100%" @change="onChemChange">
            <el-option v-for="c in chems" :key="c.id" :label="`${c.code} ${c.name}（${categoryOf(c.category).name}）`" :value="c.id" />
          </el-select>
          <div class="cell-sub">一张处置单一种化学品一个批次（错批守卫 4603）。</div>
        </el-form-item>
        <el-form-item label="批次" prop="batchId" :rules="R.requiredPick('批次')">
          <el-select v-model="form.batchId" style="width: 100%">
            <el-option v-for="b in chemBatches" :key="b.id"
              :label="`${b.lotNo}（余 ${b.qtyLeft} · ${BATCH_STATUS[b.status]?.label}）`"
              :value="b.id" :disabled="typeThree && Number(b.status) !== 3" />
          </el-select>
          <div class="cell-sub">{{ typeThree ? '过期整批仅可选择"过期封存"批次。' : '废液/空瓶须为在库批次且 qty≤批次余量（处置=库存销账）。' }}</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="类型" prop="type" :rules="R.requiredPick('处置类型')">
            <el-select v-model="form.type" style="width: 100%">
              <el-option label="废液" :value="1" /><el-option label="空瓶" :value="2" /><el-option label="过期整批" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="数量" v-if="!typeThree" prop="qty" :rules="R.positive('数量')">
            <el-input-number v-model="form.qty" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="处置方式" prop="method" :rules="R.maxLen(120, '处置方式')">
          <el-input v-model="form.method" placeholder="如 委托有资质单位焚烧" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交处置单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { addWaste, listWaste, listChemInfo, listBatch } from '../../api/chem'
import { hasPerm } from '../../store/auth'
import { WASTE_TYPE, WASTE_STATUS, BATCH_STATUS, categoryOf } from '../../utils/constants'
import R from '../../utils/validation'
import { exportExcel } from '../../utils/export'

const filters = reactive({ keyword: '', status: null })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const exporting = ref(false)
const chems = ref([])
const chemBatches = ref([])
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})

const typeThree = computed(() => Number(form.type) === 3)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listWaste({
      keyword: filters.keyword || '', status: filters.status ?? undefined,
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function reset() { filters.keyword = ''; filters.status = null; load(1) }

function openCreate() {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, { chemicalId: null, batchId: null, type: 1, qty: 1, method: '' })
  chemBatches.value = []
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function onChemChange() {
  form.batchId = null
  chemBatches.value = []
  if (form.chemicalId) {
    const data = await listBatch({ chemicalId: form.chemicalId, page: 1, size: 100 }).catch(() => null)
    chemBatches.value = data?.records || []
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('化学品与批次均强制')
    return
  }
  if (!typeThree.value && !form.qty) {
    ElMessage.warning('废液/空瓶处置数量必填')
    return
  }
  saving.value = true
  try {
    const saved = await addWaste({
      chemicalId: form.chemicalId, batchId: form.batchId, type: Number(form.type),
      qty: typeThree.value ? null : form.qty, method: form.method
    })
    const catKey = categoryOf(selectedCategory()).key
    ElMessage.success(`处置单已建（${saved?.code || 'FQ'}），进入${['JD', 'YZ', 'YB'].includes(catKey) ? '校级' : '学院'}单级审批`)
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

const selectedCategory = () => chems.value.find(c => c.id === form.chemicalId)?.category ?? 0

/** 导出：遵循当前筛选条件（keyword/status） */
async function doExport() {
  exporting.value = true
  try {
    await exportExcel({
      business: '废弃处置单',
      columns: [
        { prop: 'code', label: '单号' },
        { prop: 'chemicalName', label: '化学品' },
        { prop: row => WASTE_TYPE[row.type] || row.type, label: '类型' },
        { prop: 'batchId', label: '批次ID' },
        { prop: row => (row.qty ?? '整批'), label: '数量' },
        { prop: 'method', label: '处置方式' },
        { prop: 'disposalTarget', label: '处置去向' },
        { prop: 'voucherNo', label: '转移凭证号' },
        { prop: 'operatorName', label: '经办人' },
        { prop: row => WASTE_STATUS[row.status]?.label || row.status, label: '状态' },
        { prop: 'createTime', label: '创建时间' },
        { prop: 'finishTime', label: '完成时间' }
      ],
      fetchPage: (p, s) => listWaste({ keyword: filters.keyword || '', status: filters.status ?? undefined, page: p, size: s })
    })
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  load()
  listChemInfo({ page: 1, size: 200 }).then(d => { chems.value = d.records || [] }).catch(() => {})
})
</script>

<style scoped>
.filter-card { padding: 14px 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { padding: 6px 12px 12px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 8px; }
.head-actions { display: flex; gap: 8px; align-items: center; }
</style>
