<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">STOCK-IN DUAL SIGN · 五双制度</div>
        <div class="page-title" style="margin-top: 4px">入库双签</div>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('hzm:stockin:add')" type="primary" @click="openCreate">+ 入库登记</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>入库执行"五双"制度：收发双方双签（复核人不得与接收人为同一人，至少一方持会签权）；剧毒品为两步独立签——首签后单据停留"待验收"，待持学院权限者会签方落账。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="单号 / 批号" clearable style="width: 200px" @keyup.enter="load(1)" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px">
          <el-option label="待验收" :value="0" /><el-option label="已入库" :value="1" /><el-option label="已拒收" :value="2" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="单号" width="150">
          <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="chemicalName" label="化学品" min-width="130" />
        <el-table-column prop="lotNo" label="批号" width="130">
          <template #default="{ row }"><span class="mono">{{ row.lotNo }}</span></template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ row.qty }}</template>
        </el-table-column>
        <el-table-column prop="expiryDate" label="效期" width="105" />
        <el-table-column label="柜位" width="110">
          <template #default="{ row }">{{ cabinetName(row.cabinetId) }}</template>
        </el-table-column>
        <el-table-column prop="supplier" label="供应商" min-width="110" show-overflow-tooltip />
        <el-table-column prop="permitNo" label="公安备案号" width="120">
          <template #default="{ row }"><span class="mono cell-sub">{{ row.permitNo || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="receiverName" label="接收人" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="STOCKIN_STATUS[row.status]?.type" size="small">{{ STOCKIN_STATUS[row.status]?.label }}</el-tag>
            <div v-if="row.status === 0 && row.rejectReason" class="cell-sub" style="color: var(--c-jd)">{{ row.rejectReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 && hasPerm('hzm:stockin:countersign')" size="small" text type="success" @click="doCountersign(row)">第二签会签</el-button>
            <el-button v-if="row.status === 0 && hasPerm('hzm:stockin:reject')" size="small" text type="danger" @click="doReject(row)">拒收</el-button>
            <span v-if="row.status === 0" class="cell-sub">剧毒/待验收单：仅验收类动作</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 新建入库表单：一步双签 / 剧毒提示待第二签 -->
    <el-dialog v-model="createVisible" title="入库登记（RK · 五双双签）" width="680px" top="5vh">
      <el-form ref="formRef" :model="form" label-width="120px">
        <el-form-item label="台账来源" prop="mode" :rules="R.requiredPick('台账来源')">
          <el-radio-group v-model="form.mode">
            <el-radio value="existing">挂既有台账</el-radio>
            <el-radio value="draft">新化学品建档（CAS 查重）</el-radio>
          </el-radio-group>
        </el-form-item>
        <template v-if="form.mode === 'existing'">
          <el-form-item label="化学品" prop="chemicalId" :rules="R.requiredPick('化学品')">
            <el-select v-model="form.chemicalId" filterable style="width: 100%" placeholder="选择台账化学品">
              <el-option v-for="c in chems" :key="c.id"
                :label="`${c.code} ${c.name}（${categoryOf(c.category).name} · 现存 ${c.stockQty}）`" :value="c.id" />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <div class="form-grid">
            <el-form-item label="品名" prop="draft.name" :rules="R.requiredLen(100, '品名')"><el-input v-model="form.draft.name" /></el-form-item>
            <el-form-item label="CAS 号" prop="draft.casNo" :rules="R.requiredCasNo()">
              <el-input v-model="form.draft.casNo" placeholder="命中将挂既有台账" />
            </el-form-item>
            <el-form-item label="分类" prop="draft.category" :rules="R.requiredPick('分类')">
              <el-select v-model="form.draft.category" style="width: 100%">
                <el-option v-for="(c, k) in CATEGORIES" :key="k" :label="`${c.key} · ${c.name}`" :value="Number(k)" />
              </el-select>
            </el-form-item>
            <el-form-item label="主相容组" prop="draft.compatGroup" :rules="R.maxLen(8, '主相容组')">
              <el-select v-model="form.draft.compatGroup" filterable allow-create style="width: 100%">
                <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="次相容组" prop="draft.compatGroup2" :rules="R.maxLen(8, '次相容组')">
              <el-select v-model="form.draft.compatGroup2" clearable filterable allow-create style="width: 100%">
                <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
              </el-select>
            </el-form-item>
            <el-form-item label="规格" prop="draft.spec" :rules="R.maxLen(50, '规格')"><el-input v-model="form.draft.spec" /></el-form-item>
          </div>
        </template>

        <el-divider content-position="left">批号与存放（落账时校验禁配，冲突将拦截）</el-divider>
        <div class="form-grid">
          <el-form-item label="关联申购单">
            <el-select v-model="form.reqId" clearable filterable style="width: 100%" placeholder="可空：关联已批申购（累计入库不得超申购量）">
              <el-option v-for="p in approvedPurchases" :key="p.id"
                :label="`${p.code} ${p.chemicalName}（已入 ${p.inQty || 0}/申购 ${p.qty}）`" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="批号" prop="lotNo" :rules="R.requiredLen(32, '批号')"><el-input v-model="form.lotNo" placeholder="同化学品唯一" /></el-form-item>
          <el-form-item label="数量" prop="qty" :rules="R.positive('数量')"><el-input-number v-model="form.qty" :min="0.01" :precision="2" style="width: 100%" /></el-form-item>
          <el-form-item label="失效日期" prop="expiryDate" :rules="R.requiredPick('失效日期')">
            <el-date-picker v-model="form.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="存放柜位" prop="cabinetId" :rules="R.requiredPick('存放柜位')">
            <el-select v-model="form.cabinetId" style="width: 100%">
              <el-option v-for="cb in cabinets" :key="cb.id" :label="`${cb.code} ${cb.name}（${CABINET_TYPE[cb.type]}${cb.dualLock ? ' · 双锁' : ''}）`" :value="cb.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="供应商" prop="supplier" :rules="R.maxLen(100, '供应商')"><el-input v-model="form.supplier" /></el-form-item>
          <el-form-item label="公安备案号" prop="permitNo" :rules="R.maxLen(50, '公安备案号')">
            <el-input v-model="form.permitNo" placeholder="易制毒/易制爆备案号（可空）" />
          </el-form-item>
          <el-form-item label="复核人" prop="checkerId" :rules="R.requiredPick('复核人')">
            <el-select v-model="form.checkerId" filterable style="width: 100%">
              <el-option v-for="u in users.filter(u => u.id !== auth.user?.id)" :key="u.id" :label="`${u.nickname || u.username}（#${u.id}）`" :value="u.id" />
            </el-select>
            <div v-if="isJD" class="cell-sub" style="color: var(--c-jd)">
              剧毒两步签：首签后单据停留"待验收"，复核人登记留待第二签会签（须持学院权限）。
            </div>
            <div v-else class="cell-sub">一步双签：提交即落账建批次；复核人不得与接收人（本人）为同一人。</div>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">提交{{ isJD ? '（剧毒首签）' : '（一步双签落账）' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listStockIn, addStockIn, countersignStockIn, rejectStockIn, listChemInfo, listCabinet, listPurchase } from '../../api/chem'
import { userOptions } from '../../api/system'
import { auth, hasPerm } from '../../store/auth'
import { CATEGORIES, categoryOf, COMPAT_GROUPS, STOCKIN_STATUS, CABINET_TYPE } from '../../utils/constants'
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
const cabinets = ref([])
const users = ref([])
const approvedPurchases = ref([])
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})

const isJD = computed(() => {
  if (form.mode === 'draft') return Number(form.draft.category) === 4
  const chem = chems.value.find(c => c.id === form.chemicalId)
  return chem ? Number(chem.category) === 4 : false
})
const cabinetName = id => cabinets.value.find(c => c.id === id)?.name || ('#' + id)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listStockIn({
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
  Object.assign(form, {
    mode: 'existing', chemicalId: null,
    draft: { name: '', casNo: '', category: 0, compatGroup: 'GEN', compatGroup2: '', spec: '' },
    reqId: null, lotNo: '', qty: 1, expiryDate: '', cabinetId: null, supplier: '', permitNo: '', checkerId: null
  })
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('批号/数量/柜位/效期/复核人均必填（复核人=一步双签登记）')
    return
  }
  saving.value = true
  try {
    const payload = {
      reqId: form.reqId || null, chemicalId: form.mode === 'existing' ? form.chemicalId : null,
      lotNo: form.lotNo, qty: form.qty, expiryDate: form.expiryDate, cabinetId: form.cabinetId,
      supplier: form.supplier, permitNo: form.permitNo || null, checkerId: form.checkerId
    }
    if (form.mode === 'draft') payload.draft = { ...form.draft }
    const saved = await addStockIn(payload)
    if (isJD.value) {
      ElMessage.warning(`剧毒首签完成（${saved?.code || 'RK'}）：单据停留待验收，待持学院权限者第二签会签后落账`)
    } else {
      ElMessage.success(`已按五双制度双签入库落账（${saved?.code || 'RK'}）`)
    }
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

async function doCountersign(row) {
  await ElMessageBox.confirm(
    '剧毒第二签会签：以本人身份登记复核（复核人不得为接收人，且须持学院权限）。确认会签？',
    '第二签会签', { type: 'warning' }
  )
  await countersignStockIn(row.id, {})
  ElMessage.success('第二签完成，剧毒品已落账入柜（双人双锁）')
  load()
}

async function doReject(row) {
  const { value } = await ElMessageBox.prompt('拒收原因必填（终态：不建批次不回写申购）', '拒收入库单', {
    inputPattern: /.+/, inputErrorMessage: '拒收原因必填', type: 'warning'
  })
  await rejectStockIn(row.id, value)
  ElMessage.success('已拒收（终态，拒因已留痕）')
  load()
}

onMounted(async () => {
  load()
  listChemInfo({ page: 1, size: 200 }).then(d => { chems.value = d.records || [] }).catch(() => {})
  listCabinet().then(d => { cabinets.value = d || [] }).catch(() => {})
  listPurchase({ status: 2, page: 1, size: 100 }).then(d => { approvedPurchases.value = d.records || [] }).catch(() => {})
  userOptions('hzm:stockin:countersign').then(d => { users.value = d || [] }).catch(() => {})
})

/** D1 导出：遵循当前关键词/状态筛选条件 */
async function doExport() {
  exporting.value = true
  try {
    await exportExcel({
      business: '入库双签单',
      columns: [
        { prop: 'code', label: '单号' },
        { prop: 'chemicalName', label: '化学品' },
        { prop: 'lotNo', label: '批号' },
        { prop: 'qty', label: '数量' },
        { prop: 'expiryDate', label: '效期' },
        { prop: row => cabinetName(row.cabinetId), label: '柜位' },
        { prop: 'supplier', label: '供应商' },
        { prop: 'permitNo', label: '公安备案号' },
        { prop: 'receiverName', label: '接收人' },
        { prop: row => STOCKIN_STATUS[row.status]?.label || row.status, label: '状态' },
        { prop: 'rejectReason', label: '拒收原因' },
        { prop: 'inTime', label: '入库时间' },
        { prop: 'createTime', label: '创建时间' }
      ],
      fetchPage: (p, s) => listStockIn({ keyword: filters.keyword || '', status: filters.status ?? undefined, page: p, size: s })
    })
  } finally {
    exporting.value = false
  }
}
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
