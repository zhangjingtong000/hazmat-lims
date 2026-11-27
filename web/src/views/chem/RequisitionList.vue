<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">REQUISITION & RETURN · 先批后领</div>
        <div class="page-title" style="margin-top: 4px">领用与归还</div>
      </div>
      <el-button v-if="hasPerm('hzm:req:submit')" type="primary" @click="openCreate">+ 新建领用</el-button>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>领用执行"先批后领、存量双轨"：复核双签通过即出库，过期批次不予发放；非关联领用且危化品及以上须导师先签；用尽/余量按规核销归还。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(s, k) in REQ_STATUS" :key="k" :label="s.label" :value="Number(k)" />
        </el-select>
        <el-checkbox v-model="filters.mine" label="只看本人" @change="load(1)" />
        <el-checkbox v-model="filters.todo" label="待签单" @change="load(1)" />
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="单号" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/chem/req/${row.id}`)"><span class="doc-code">{{ row.code }}</span></el-link>
          </template>
        </el-table-column>
        <el-table-column prop="chemicalName" label="化学品" min-width="130" />
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">{{ row.qty }}</template>
        </el-table-column>
        <el-table-column prop="applicantName" label="领用人" width="104">
          <template #default="{ row }">{{ shortName(row.applicantName) }}</template>
        </el-table-column>
        <el-table-column label="关联申购" width="150">
          <template #default="{ row }">
            <span v-if="row.reqId" class="mono">AP-关联 #{{ row.reqId }}</span>
            <span v-else class="cell-sub">存量领用</span>
          </template>
        </el-table-column>
        <el-table-column label="已还/量" width="90" align="right">
          <template #default="{ row }">{{ row.returnQty || 0 }} / {{ row.qty }}</template>
        </el-table-column>
        <el-table-column prop="expectReturn" label="归还时限" width="105">
          <template #default="{ row }">
            <span :class="{ 'exp-danger': overdue(row) }">{{ row.expectReturn || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="REQ_STATUS[row.status]?.type" size="small">{{ REQ_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/chem/req/${row.id}`)">详情</el-button>
            <el-button size="small" text @click="$router.push(`/print/req/${row.id}`)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 新建领用 -->
    <el-dialog v-model="createVisible" title="新建领用单（LY）" width="600px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="化学品" prop="chemicalId" :rules="R.requiredPick('化学品')">
          <el-select v-model="form.chemicalId" filterable style="width: 100%" @change="onChemChange">
            <el-option v-for="c in chems" :key="c.id"
              :label="`${c.code} ${c.name}（${categoryOf(c.category).name} · 现存 ${c.stockQty}${c.status === 1 ? ' · 已封存' : ''}）`"
              :value="c.id" :disabled="c.status !== 0" />
          </el-select>
          <div class="cell-sub">台账封存不可领用；出库自动按先到期先出分配批次，过期批次拦截。</div>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="数量" prop="qty" :rules="R.positive('数量')"><el-input-number v-model="form.qty" :min="0.01" :precision="2" style="width: 100%" /></el-form-item>
          <el-form-item label="归还时限" prop="expectReturn">
            <el-date-picker v-model="form.expectReturn" type="date" value-format="YYYY-MM-DD" style="width: 100%" placeholder="可空；超期进看板预警" />
          </el-form-item>
        </div>
        <el-form-item label="关联申购单">
          <el-select v-model="form.reqId" clearable filterable style="width: 100%" placeholder="可空：关联已批申购（不得超领）">
            <el-option v-for="p in approvedPurchases.filter(p => p.casNo === selectedChem?.casNo)"
              :key="p.id" :label="`${p.code} ${p.chemicalName}（CAS 一致 · 剩余可领 ${remain(p)}）`" :value="p.id" />
          </el-select>
          <div class="cell-sub">非关联且分类≥危化品时须导师先签（起点分支 N-5）。</div>
        </el-form-item>
        <el-form-item label="用途" prop="purpose" :rules="R.requiredLen(200, '用途')"><el-input v-model="form.purpose" /></el-form-item>
        <el-form-item label="实验项目" prop="experiment" :rules="R.maxLen(100, '实验项目')"><el-input v-model="form.experiment" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交领用</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listReq, submitReq, listChemInfo, listPurchase } from '../../api/chem'
import { hasPerm } from '../../store/auth'
import { REQ_STATUS, categoryOf } from '../../utils/constants'
import R from '../../utils/validation'

const filters = reactive({ status: null, mine: false, todo: false })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const chems = ref([])
const approvedPurchases = ref([])
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})

const selectedChem = computed(() => chems.value.find(c => c.id === form.chemicalId))
/** 表头已标明角色，去掉昵称尾部冗余的（角色）后缀，避免人名列过宽 */
const shortName = n => { if (!n) return ''; const s = String(n).replace(/（[^）]*）$/, ''); return s || String(n) }
const remain = p => Math.max(0, Number(p.qty) - Number(p.inQty || 0))
const overdue = row => row.status === 2 && row.expectReturn && String(row.expectReturn) < new Date().toISOString().slice(0, 10)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listReq({
      status: filters.status ?? undefined, mine: filters.mine, todo: filters.todo,
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.keys(form).forEach(k => delete form[k])
  Object.assign(form, { chemicalId: null, qty: 1, expectReturn: '', reqId: null, purpose: '', experiment: '' })
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function onChemChange() { form.reqId = null }

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('化学品、数量与用途必填')
    return
  }
  saving.value = true
  try {
    const saved = await submitReq({
      chemicalId: form.chemicalId, qty: form.qty, reqId: form.reqId || null,
      expectReturn: form.expectReturn || null, purpose: form.purpose, experiment: form.experiment
    })
    const start = saved?.status === 0 ? '待导师签（非关联且危化品及以上）' : '待复核（关联已批申购或普通品）'
    ElMessage.success(`领用单已提交（${saved?.code || 'LY'}）：${start}`)
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  listChemInfo({ page: 1, size: 200 }).then(d => { chems.value = d.records || [] }).catch(() => {})
  listPurchase({ status: 2, page: 1, size: 100 }).then(d => { approvedPurchases.value = d.records || [] }).catch(() => {})
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
.form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 8px; }
</style>
