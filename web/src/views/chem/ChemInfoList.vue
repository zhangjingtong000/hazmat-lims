<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">CHEMICAL LEDGER · GB 15603 建档</div>
        <div class="page-title" style="margin-top: 4px">化学品台账</div>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('hzm:info:add')" type="primary" @click="openForm()">+ 建档</el-button>
        <el-button v-if="hasPerm('hzm:info:add')" @click="openImport">批量导入</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>台账依 GB 15603 建档：类别按最高风险认定，是审批链的主轴；已有批次的台账不可再改类别；CAS 重复时自动挂靠既有台账，不另建新档。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="编码 / 名称 / CAS" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-select v-model="filters.category" placeholder="分类" clearable style="width: 150px">
          <el-option v-for="(c, k) in CATEGORIES" :key="k" :label="`${c.key} · ${c.name}`" :value="Number(k)" />
        </el-select>
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 130px">
          <el-option label="在管" :value="0" /><el-option label="封存" :value="1" /><el-option label="清空" :value="2" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="编码" width="160">
          <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column label="GHS" width="56" align="center">
          <template #default="{ row }"><GhzTag :category="row.category" :size="24" with-bg /></template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="130">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/chem/info/${row.id}`)">{{ row.name }}</el-link>
            <div v-if="row.alias" class="cell-sub">{{ row.alias }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="casNo" label="CAS 号" width="120">
          <template #default="{ row }"><span class="mono">{{ row.casNo }}</span></template>
        </el-table-column>
        <el-table-column label="分类" width="96">
          <template #default="{ row }">
            <span class="cat-name" :class="'cat-' + categoryOf(row.category).key.toLowerCase()">{{ categoryOf(row.category).name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="相容组" width="110">
          <template #default="{ row }">
            <span class="mono">{{ row.compatGroup }}</span>
            <span v-if="row.compatGroup2" class="mono"> / {{ row.compatGroup2 }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" width="90" />
        <el-table-column label="现存/下限" width="120" align="right">
          <template #default="{ row }">
            <span :class="{ 'low-stock': Number(row.stockQty) <= Number(row.minStock || 0) }">{{ row.stockQty }}</span>
            <span class="cell-sub"> / {{ row.minStock || 0 }} {{ row.unit }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="84">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'info'" size="small">
              {{ INFO_STATUS[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/chem/info/${row.id}`)">详情</el-button>
            <el-button size="small" text @click="$router.push(`/print/label/${row.id}`)">打印</el-button>
            <el-button v-if="hasPerm('hzm:info:edit')" size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button
              v-if="hasPerm('hzm:info:edit') && row.status !== 2"
              size="small" text :type="row.status === 1 ? 'success' : 'warning'"
              @click="toggleSeal(row)">
              {{ row.status === 1 ? '解封' : '封存' }}
            </el-button>
            <span v-if="hasPerm('hzm:info:remove')" class="op-divider" aria-hidden="true"></span>
            <el-button v-if="hasPerm('hzm:info:remove')" size="small" text type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size"
        :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 建档/编辑对话框 -->
    <el-dialog v-model="formVisible" :title="form.id ? '维护台账' : '新建化学品档案'" width="640px" top="6vh">
      <el-form ref="formRef" :model="form" label-width="110px">
        <div class="form-grid">
          <el-form-item label="品名" prop="name" :rules="R.requiredLen(100, '品名')"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="别名" prop="alias" :rules="R.maxLen(100, '别名')"><el-input v-model="form.alias" /></el-form-item>
          <el-form-item label="CAS 号" prop="casNo" :rules="R.requiredCasNo()">
            <el-input v-model="form.casNo" placeholder="如 67-64-1" :disabled="!!form.id">
              <template #append><el-button @click="doCasCheck">查重</el-button></template>
            </el-input>
          </el-form-item>
          <el-form-item label="分类" prop="category" :rules="R.requiredPick('分类').concat(R.range(0, 4, '分类'))">
            <el-select v-model="form.category" :disabled="hasBatch" style="width: 100%">
              <el-option v-for="(c, k) in CATEGORIES" :key="k" :label="`${c.key} · ${c.name}`" :value="Number(k)" />
            </el-select>
            <div v-if="hasBatch" class="cell-sub" style="color: var(--c-jd)">已有批次，类别为审批链主轴，不可修改</div>
          </el-form-item>
          <el-form-item label="主相容组" prop="compatGroup" :rules="R.requiredLen(8, '主相容组')">
            <el-select v-model="form.compatGroup" filterable allow-create default-first-option style="width: 100%">
              <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
          <el-form-item label="次相容组" prop="compatGroup2" :rules="R.maxLen(8, '次相容组')">
            <el-select v-model="form.compatGroup2" clearable filterable allow-create style="width: 100%" placeholder="可空">
              <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格" prop="spec" :rules="R.maxLen(50, '规格')"><el-input v-model="form.spec" placeholder="如 AR 500mL/瓶" /></el-form-item>
          <el-form-item label="计量单位" prop="unit" :rules="R.maxLen(16, '计量单位')"><el-input v-model="form.unit" placeholder="如 瓶" /></el-form-item>
          <el-form-item label="安全库存下限" prop="minStock" :rules="R.nonNegative('安全库存下限')">
            <el-input-number v-model="form.minStock" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="效期预警天数" prop="expiryWarnDays" :rules="R.requiredNumber('效期预警天数').concat(R.range(1, 365, '效期预警天数'))">
            <el-input-number v-model="form.expiryWarnDays" :min="1" :max="365" style="width: 100%" />
          </el-form-item>
          <el-form-item label="剧毒单次上限" prop="maxOnce" v-if="Number(form.category) === 4" :rules="R.nonNegative('剧毒单次上限')">
            <el-input-number v-model="form.maxOnce" :min="0" :precision="2" style="width: 100%" placeholder="仅剧毒必填，安全员维护" />
          </el-form-item>
          <el-form-item label="默认柜位" prop="cabinetId">
            <el-select v-model="form.cabinetId" clearable style="width: 100%">
              <el-option v-for="cb in cabinets" :key="cb.id" :label="`${cb.code} ${cb.name}`" :value="cb.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="GHS 象形图" prop="ghzPictogram">
            <el-select v-model="form.ghzPictogram" clearable style="width: 100%" placeholder="象形图代码">
              <el-option v-for="p in PICTOGRAMS" :key="p.value" :label="p.label" :value="p.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="应急处置要点" prop="firstAid" class="span2" :rules="R.maxLen(200, '应急处置要点')">
            <el-input v-model="form.firstAid" type="textarea" :rows="2" maxlength="200" placeholder="如：皮肤接触立即用大量清水冲洗 15 分钟以上" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入（模板 + 逐行校验 + 失败明细） -->
    <el-dialog v-model="importVisible" title="批量导入台账（CSV 模板）" width="760px" top="6vh">
      <el-steps :active="importResult ? 3 : (importRowsData.length ? 2 : 1)" simple class="import-steps">
        <el-step title="下载模板" />
        <el-step title="选择文件" />
        <el-step title="逐行校验导入" />
        <el-step title="失败明细" />
      </el-steps>
      <div class="import-bar">
        <el-button @click="downloadTemplate">下载模板</el-button>
        <input ref="fileInput" type="file" accept=".csv,text/csv" class="import-file" @change="onPickFile" />
        <el-button type="primary" :disabled="!importRowsData.length" :loading="importing" @click="doImport">
          开始导入{{ importRowsData.length ? `（${importRowsData.length - 1} 行）` : '' }}
        </el-button>
      </div>
      <div class="cell-sub">
        模板列：{{ IMPORT_COLUMNS.map(c => c.label).join(' / ') }}；分类取值 0~4（0 普通化学品、1 普通危化品、2 易制毒、3 易制爆、4 剧毒）；
        CAS 命中既有台账时按规则挂靠、不重复建档（计入成功）。导入串行执行，失败行不阻断后续行。
      </div>

      <el-alert v-if="importResult" class="import-summary" :closable="false" show-icon
        :type="importResult.failed.length ? 'warning' : 'success'"
        :title="`导入完成：成功 ${importResult.success} 条 / 失败 ${importResult.failed.length} 条 / 共 ${importResult.total} 行`" />

      <el-table v-if="importResult && importResult.failed.length" :data="importResult.failed" size="small" stripe max-height="260">
        <el-table-column prop="line" label="行号" width="70" />
        <el-table-column label="品名" width="130"><template #default="{ row }">{{ row.values.name || '—' }}</template></el-table-column>
        <el-table-column label="CAS" width="120"><template #default="{ row }">{{ row.values.casNo || '—' }}</template></el-table-column>
        <el-table-column prop="reason" label="失败原因" min-width="260" show-overflow-tooltip />
      </el-table>
      <el-empty v-else-if="importResult" description="全部成功，无失败行" :image-size="60" />
      <el-empty v-else description="尚未导入：先下载模板，填好后选择文件" :image-size="60" />

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, computed, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import GhzTag from '../../components/GhzTag.vue'
import { listChemInfo, addChemInfo, editChemInfo, removeChemInfo, casCheck, listCabinet, setChemInfoStatus } from '../../api/chem'
import { hasPerm } from '../../store/auth'
import { CATEGORIES, categoryOf, INFO_STATUS, COMPAT_GROUPS, PICTOGRAMS } from '../../utils/constants'
import R from '../../utils/validation'
import { exportExcel } from '../../utils/export'
import { buildCsv, downloadText, parseCsv, readFileText, importRows, summarize } from '../../utils/importCsv'

const filters = reactive({ keyword: '', category: null, status: null })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const exporting = ref(false)
const cabinets = ref([])
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})

/* ═══ 批量导入状态 ═══ */
const importVisible = ref(false)
const importing = ref(false)
const importRowsData = ref([])
const importResult = ref(null)
const fileInput = ref(null)

/** 模板列即导入契约（顺序、必填、取值范围与后端 Validators 一致） */
const IMPORT_COLUMNS = [
  { key: 'name', label: '品名', required: true, validate: v => (v.length > 100 ? '品名不能超过 100 个字符' : '') },
  { key: 'alias', label: '别名' },
  { key: 'casNo', label: 'CAS号', required: true, validate: v => (/^\d{2,7}-\d{2}-\d$/.test(v) ? '' : 'CAS 号格式如 67-64-1') },
  { key: 'category', label: '分类', required: true, validate: v => (/^[0-4]$/.test(v) ? '' : '分类须为 0~4 的整数'), transform: v => Number(v) },
  { key: 'compatGroup', label: '主相容组', required: true, validate: v => (v.length > 8 ? '主相容组不能超过 8 个字符' : '') },
  { key: 'compatGroup2', label: '次相容组', validate: v => (v.length > 8 ? '次相容组不能超过 8 个字符' : '') },
  { key: 'spec', label: '规格', validate: v => (v.length > 50 ? '规格不能超过 50 个字符' : '') },
  { key: 'unit', label: '单位', validate: v => (v.length > 16 ? '单位不能超过 16 个字符' : '') },
  { key: 'minStock', label: '安全库存下限', validate: v => (/^\d+(\.\d+)?$/.test(v) ? '' : '安全库存下限须为非负数字'), transform: v => Number(v) },
  { key: 'expiryWarnDays', label: '效期预警天数', validate: v => (/^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 365 ? '' : '效期预警天数须为 1~365'), transform: v => Number(v) },
  { key: 'firstAid', label: '应急处置要点', validate: v => (v.length > 200 ? '应急处置要点不能超过 200 个字符' : '') }
]

function openImport() {
  importRowsData.value = []
  importResult.value = null
  importVisible.value = true
  nextTick(() => { if (fileInput.value) fileInput.value.value = '' })
}

function downloadTemplate() {
  const headers = IMPORT_COLUMNS.map(c => c.label)
  const samples = [
    ['示例-丙酮（可删除本行）', '二甲基酮', '67-64-1', 1, 'FLAM', '', 'AR 500mL/瓶', '瓶', 2, 30, '皮肤接触立即用大量清水冲洗 15 分钟以上'],
    ['示例-氯化钠', '', '7647-14-5', 0, 'GEN', '', 'AR 500g/瓶', '瓶', 5, 180, '常规化学品，按实验室通则处置']
  ]
  const name = downloadText('化学品台账导入', buildCsv(headers, samples))
  ElMessage.success(`模板已下载：${name}（示例行请删除后再导入）`)
}

async function onPickFile(evt) {
  const file = evt.target.files?.[0]
  if (!file) return
  importResult.value = null
  try {
    const text = await readFileText(file)
    importRowsData.value = parseCsv(text)
    if (importRowsData.value.length < 2) {
      importRowsData.value = []
      ElMessage.warning('文件为空或只有表头，请检查模板内容')
      return
    }
    ElMessage.info(`已读取 ${importRowsData.value.length - 1} 行，点击"开始导入"执行逐行校验`)
  } catch (e) {
    ElMessage.error(e.message || '文件解析失败')
  }
}

async function doImport() {
  importing.value = true
  try {
    const result = await importRows({
      rows: importRowsData.value,
      columns: IMPORT_COLUMNS,
      submit: payload => addChemInfo(payload)
    })
    importResult.value = result
    summarize(result)
    load(1)
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}
/** 当前编辑对象是否已有批次（禁改类别 4107 的前端提示） */
const hasBatch = computed(() => form.id && Number(form.stockQty) > 0)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listChemInfo({
      keyword: filters.keyword || '',
      category: filters.category ?? undefined,
      status: filters.status ?? undefined,
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.category = null
  filters.status = null
  load(1)
}

function openForm(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) {
    Object.assign(form, row)
  } else {
    Object.assign(form, {
      name: '', alias: '', casNo: '', category: 0, compatGroup: 'GEN', compatGroup2: '',
      spec: '', unit: '', minStock: 0, expiryWarnDays: 30, maxOnce: null,
      cabinetId: null, ghzPictogram: '', firstAid: ''
    })
  }
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

/** 导出：按当前筛选条件分页拉取后生成 Excel（文件名含业务名与日期） */
async function doExport() {
  exporting.value = true
  try {
    await exportExcel({
      business: '化学品台账',
      columns: [
        { prop: 'code', label: '编码' },
        { prop: 'name', label: '品名' },
        { prop: 'alias', label: '别名' },
        { prop: 'casNo', label: 'CAS 号' },
        { prop: row => categoryOf(row.category).name, label: '分类' },
        { prop: 'compatGroup', label: '主相容组' },
        { prop: 'compatGroup2', label: '次相容组' },
        { prop: 'spec', label: '规格' },
        { prop: 'unit', label: '单位' },
        { prop: 'stockQty', label: '现存' },
        { prop: 'minStock', label: '安全库存下限' },
        { prop: row => INFO_STATUS[row.status] ?? row.status, label: '状态' },
        { prop: 'expiryWarnDays', label: '效期预警天数' },
        { prop: 'createTime', label: '建档时间' }
      ],
      fetchPage: (p, s) => listChemInfo({
        keyword: filters.keyword || '',
        category: filters.category ?? undefined,
        status: filters.status ?? undefined,
        page: p, size: s
      })
    })
  } finally {
    exporting.value = false
  }
}

async function doCasCheck() {
  if (!form.casNo) {
    ElMessage.warning('请先填写 CAS 号')
    return
  }
  const hit = await casCheck(form.casNo)
  if (hit) {
    ElMessage.warning(`CAS 已在档：${hit.code} ${hit.name}——提交入库将挂既有台账，不建新档`)
  } else {
    ElMessage.success('CAS 未命中，可新建档案')
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('品名与 CAS 号必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await editChemInfo(form)
      ElMessage.success('台账已按 GB 15603 维护更新')
    } else {
      const created = await addChemInfo(form)
      ElMessage.success(created && created.code ? `已建档启用：${created.code}` : '建档完成，台账启用')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleSeal(row) {
  const to = row.status === 1 ? 0 : 1
  await ElMessageBox.confirm(
    to === 1 ? '封存后该台账将停止领用，确定封存？' : '解封后恢复领用，确定？',
    '封存/解封确认', { type: 'warning' }
  )
  await setChemInfoStatus(row.id, to)
  ElMessage.success(to === 1 ? '台账已封存（封存=停止领用）' : '台账已解封恢复在管')
  load()
}

async function doRemove(row) {
  await ElMessageBox.confirm(
    '删除为逻辑删除，须同时满足：①已清空 ②无在库批次 ③无未完成单据引用。确定删除？',
    '删除确认', { type: 'error' }
  )
  await removeChemInfo(String(row.id))
  ElMessage.success('已按三条件守卫执行逻辑删除')
  load()
}

onMounted(async () => {
  load()
  listCabinet().then(d => { cabinets.value = d || [] }).catch(() => {})
})
</script>

<style scoped>
.filter-card { padding: 14px 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { padding: 6px 12px 12px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.mono { font-family: var(--f-mono); }
.cat-name { font-weight: 600; font-size: 0.8rem; color: var(--c-graphite); }
.op-divider { display: inline-block; width: 1px; height: 12px; background: var(--c-line-strong); vertical-align: -2px; margin: 0 6px 0 2px; }
.low-stock { color: var(--c-jd); font-weight: 700; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; column-gap: 8px; }
.form-grid .span2 { grid-column: span 2; }
.head-actions { display: flex; gap: 8px; align-items: center; }
.import-steps { margin-bottom: 12px; }
.import-bar { display: flex; gap: 10px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.import-file { font-size: 0.8rem; }
.import-summary { margin: 10px 0; }
</style>
