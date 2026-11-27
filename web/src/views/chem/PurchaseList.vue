<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">PURCHASE APPROVAL CHAIN · 分级审批</div>
        <div class="page-title" style="margin-top: 4px">申购管理</div>
      </div>
      <el-button v-if="hasPerm('hzm:purchase:submit')" type="primary" @click="openCreate">+ 新建申购</el-button>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>申购走分级审批链：按分类取前 N 级（导师→安全员→学院→校级）；已批后超 7 天未入库预警、可由申请人作废（部分入库除外）。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="filters.keyword" placeholder="单号 / 化学品名 / CAS" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-select v-model="filters.status" placeholder="状态" clearable style="width: 140px">
          <el-option v-for="(s, k) in PURCHASE_STATUS" :key="k" :label="s.label" :value="Number(k)" />
        </el-select>
        <el-checkbox v-model="filters.mine" label="只看本人" @change="load(1)" />
        <el-checkbox v-model="filters.todo" label="审批中" @change="load(1)" />
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe @row-click="openDetail">
        <el-table-column prop="code" label="单号" width="150">
          <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
        </el-table-column>
        <el-table-column prop="chemicalName" label="化学品" min-width="130" />
        <el-table-column prop="casNo" label="CAS" width="108">
          <template #default="{ row }"><span class="mono cell-sub">{{ row.casNo || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="分类" width="126">
          <template #default="{ row }">
            <GhzTag :category="row.category" :size="22" with-bg />
            <span style="margin-left: 5px">{{ categoryOf(row.category).name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ row.qty }} {{ row.unit }}</template>
        </el-table-column>
        <el-table-column prop="applicantName" label="申请人" width="104">
          <template #default="{ row }">{{ shortName(row.applicantName) }}</template>
        </el-table-column>
        <el-table-column prop="advisorName" label="导师" width="104">
          <template #default="{ row }">{{ shortName(row.advisorName) }}</template>
        </el-table-column>
        <el-table-column label="进度" width="130">
          <template #default="{ row }">
            <span v-if="row.status === 1">第 {{ row.currentSeq }} 级 / 共 {{ chainLen(row.category) }} 级</span>
            <span v-else class="cell-sub">{{ chainLen(row.category) }} 级链</span>
          </template>
        </el-table-column>
        <el-table-column label="已入" width="60" align="right">
          <template #default="{ row }">{{ row.inQty || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="PURCHASE_STATUS[row.status]?.type" size="small">{{ PURCHASE_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="openDetail(row)">详情</el-button>
            <el-button v-if="hasPerm('hzm:purchase:submit') && row.status === 0 && isMine(row)" size="small" text type="warning" @click.stop="doSubmit(row)">提交</el-button>
            <el-button v-if="canVoid(row)" size="small" text type="danger" @click.stop="doVoid(row)">作废</el-button>
            <el-button size="small" text @click.stop="$router.push(`/print/purchase/${row.id}`)">打印</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 新建申购对话框 -->
    <el-dialog v-model="createVisible" title="新建申购单（AP）" width="620px" top="6vh">
      <el-form ref="formRef" :model="createForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="化学品名" prop="chemicalName" :rules="R.requiredLen(100, '化学品名')"><el-input v-model="createForm.chemicalName" /></el-form-item>
          <el-form-item label="CAS 号" prop="casNo" :rules="R.casNo()">
            <el-input v-model="createForm.casNo" placeholder="可空，格式如 67-64-1" @blur="preCheckCas" />
          </el-form-item>
          <el-form-item label="分类" prop="category" :rules="R.requiredPick('分类')">
            <el-select v-model="createForm.category" style="width: 100%">
              <el-option v-for="(c, k) in CATEGORIES" :key="k" :label="`${c.key} · ${c.name}`" :value="Number(k)" />
            </el-select>
            <div class="cell-sub">分类决定审批级数：{{ chainDesc(createForm.category) }}</div>
          </el-form-item>
          <el-form-item label="导师" prop="advisorId" :rules="R.requiredPick('导师')">
            <el-select v-model="createForm.advisorId" filterable style="width: 100%" placeholder="提交时指定导师（导师节点指定制）">
              <el-option v-for="u in users" :key="u.id" :label="`${u.nickname || u.username}（#${u.id}）`" :value="u.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="规格" prop="spec" :rules="R.maxLen(50, '规格')"><el-input v-model="createForm.spec" /></el-form-item>
          <el-form-item label="单位" prop="unit" :rules="R.maxLen(16, '单位')"><el-input v-model="createForm.unit" placeholder="如 瓶" /></el-form-item>
          <el-form-item label="申购数量" prop="qty" :rules="R.positive('申购数量')">
            <el-input-number v-model="createForm.qty" :min="0.01" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="用途" prop="purpose" :rules="R.maxLen(200, '用途')"><el-input v-model="createForm.purpose" placeholder="如 本科有机实验" /></el-form-item>
          <el-form-item label="实验项目" prop="experiment" class="span2" :rules="R.maxLen(100, '实验项目')"><el-input v-model="createForm.experiment" /></el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button :loading="saving" @click="saveDoc(0)">暂存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="saveDoc(1)">直接提交审批</el-button>
      </template>
    </el-dialog>

    <!-- 详情 + 步骤条审批面板 -->
    <el-dialog v-model="detailVisible" title="申购单详情 / 逐级审批" width="760px" top="5vh">
      <template v-if="detail">
        <el-descriptions :column="3" size="small" border class="detail-desc">
          <el-descriptions-item label="单号"><span class="doc-code">{{ detail.req.code }}</span></el-descriptions-item>
          <el-descriptions-item label="化学品">{{ detail.req.chemicalName }}</el-descriptions-item>
          <el-descriptions-item label="CAS"><span class="mono">{{ detail.req.casNo || '—' }}</span></el-descriptions-item>
          <el-descriptions-item label="分类">{{ categoryOf(detail.req.category).name }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ detail.req.qty }} {{ detail.req.unit }}{{ detail.req.spec ? '（' + detail.req.spec + '）' : '' }}</el-descriptions-item>
          <el-descriptions-item label="已入库">{{ detail.req.inQty || 0 }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ detail.req.applicantName || detail.req.applicantId }}</el-descriptions-item>
          <el-descriptions-item label="导师">{{ detail.req.advisorName || detail.req.advisorId }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag size="small" :type="PURCHASE_STATUS[detail.req.status]?.type">{{ PURCHASE_STATUS[detail.req.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="用途" :span="2">{{ detail.req.purpose || '—' }}</el-descriptions-item>
          <el-descriptions-item label="实验">{{ detail.req.experiment || '—' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 步骤条审批面板（差异化签名） -->
        <div class="steps-panel">
          <el-steps :active="stepActive" align-center finish-status="success" :process-status="detail.req.status === 3 ? 'error' : 'process'">
            <el-step v-for="n in chainFor(detail.req.category)" :key="n.seq"
              :title="n.nodeName" :description="stepDesc(n.seq)" />
          </el-steps>
        </div>

        <el-table :data="detail.records" size="small" class="rec-table">
          <el-table-column prop="seq" label="级次" width="60" />
          <el-table-column prop="nodeName" label="节点" width="110" />
          <el-table-column prop="comment" label="审批意见" min-width="180" show-overflow-tooltip />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '通过' : '驳回' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>

        <div v-if="canAuditNow" class="audit-bar">
          <el-input v-model="auditComment" type="textarea" :rows="2" placeholder="审批意见（驳回必填；通过可填）" />
          <div class="audit-btns">
            <el-button type="success" :loading="auditing" @click="doAudit(true)">通过（推进至下一级）</el-button>
            <el-button type="danger" :loading="auditing" @click="doAudit(false)">驳回（终态，意见必填）</el-button>
          </div>
          <div class="cell-sub">先审先生效；同一单据不会被重复审批，相邻两级不得为同一人。</div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import GhzTag from '../../components/GhzTag.vue'
import {
  listPurchase, savePurchase, submitPurchase, purchaseDetail, auditPurchase, voidPurchase, casCheck
} from '../../api/chem'
import { userOptions } from '../../api/system'
import { auth, hasPerm, hasAnyPurchaseAuditPerm } from '../../store/auth'
import { CATEGORIES, categoryOf, PURCHASE_STATUS, AUDIT_NODES } from '../../utils/constants'
import R from '../../utils/validation'

const route = useRoute()
const filters = reactive({ keyword: '', status: route.query.status ? Number(route.query.status) : null, mine: false, todo: false })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const users = ref([])
const createVisible = ref(false)
const saving = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const auditComment = ref('')
const auditing = ref(false)
const formRef = ref(null)
const createForm = reactive({})

const chainLen = c => chainFor(c).length
const chainFor = c => AUDIT_NODES.slice(0, chainLenFor(c))
const chainLenFor = c => ({ 0: 1, 1: 2, 2: 3, 3: 3, 4: 4 }[Number(c)] ?? 1)
/** 表头已标明角色，去掉昵称尾部冗余的（角色）后缀，避免人名列过宽 */
const shortName = n => { if (!n) return ''; const s = String(n).replace(/（[^）]*）$/, ''); return s || String(n) }
const chainDesc = c => `前 ${chainLenFor(c)} 级：` + chainFor(c).map(n => n.nodeName.replace('审批', '')).join('→')

const stepActive = computed(() => {
  const req = detail.value?.req
  if (!req) return 0
  if (req.status === 2 || req.status === 4) return chainLen(req.category)
  if (req.status === 1) return (req.currentSeq || 1) - 1
  return 0
})
const stepDesc = seq => {
  const req = detail.value?.req
  if (!req) return ''
  const rec = (detail.value.records || []).find(r => r.seq === seq)
  if (rec) return rec.result === 1 ? '已通过' : '已驳回'
  if (req.status === 1 && req.currentSeq === seq) return '待审批'
  return ''
}

const canAuditNow = computed(() => {
  const req = detail.value?.req
  if (!req || req.status !== 1) return false
  return hasAnyPurchaseAuditPerm()
})
const isMine = row => Number(row.applicantId) === Number(auth.user?.id)
const canVoid = row => hasPerm('hzm:purchase:submit') && isMine(row) && [0, 2].includes(row.status) && !(row.inQty > 0)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listPurchase({
      keyword: filters.keyword || '',
      status: filters.status ?? undefined,
      mine: filters.mine, todo: filters.todo,
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
    if (route.query.focus && rows.value.some(r => String(r.id) === String(route.query.focus))) {
      openDetailById(route.query.focus)
    }
  } finally {
    loading.value = false
  }
}

function reset() {
  filters.keyword = ''
  filters.status = null
  filters.mine = false
  filters.todo = false
  load(1)
}

function openCreate() {
  Object.keys(createForm).forEach(k => delete createForm[k])
  Object.assign(createForm, { chemicalName: '', casNo: '', category: 0, spec: '', unit: '', qty: 1, purpose: '', experiment: '', advisorId: null })
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function preCheckCas() {
  if (!createForm.casNo) return
  const hit = await casCheck(createForm.casNo).catch(() => null)
  if (hit) ElMessage.info(`CAS 命中台账 ${hit.code} ${hit.name}——申购后可直接存量领用（先批后领、存量双轨）`)
}

async function saveDoc(status) {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('化学品名、数量与导师为必填')
    return
  }
  saving.value = true
  try {
    const saved = await savePurchase({ ...createForm, status })
    if (status === 1) {
      ElMessage.success('申购已提交，进入分级审批链')
    } else {
      ElMessage.success(`草稿已暂存（${saved?.code || 'AP-…'}），可在列表提交`)
    }
    createVisible.value = false
    load(1)
  } finally {
    saving.value = false
  }
}

async function doSubmit(row) {
  await ElMessageBox.confirm('提交后进入审批链（草稿态暂存有效），确定提交？', '提交申购', { type: 'info' })
  await submitPurchase(row.id)
  ElMessage.success('已提交审批')
  load()
}

async function doVoid(row) {
  await ElMessageBox.confirm('作废为终态（已批未入库可作废；部分入库不可）。确定作废该申购单？', '作废申购', { type: 'warning' })
  await voidPurchase(row.id)
  ElMessage.success('申购单已作废（状态 5，预警口径已排除）')
  load()
}

async function openDetail(row) { openDetailById(row.id) }
async function openDetailById(id) {
  detail.value = await purchaseDetail(id)
  auditComment.value = ''
  detailVisible.value = true
}

async function doAudit(approve) {
  if (!approve && !auditComment.value.trim()) {
    ElMessage.warning('驳回必须填写审批意见')
    return
  }
  auditing.value = true
  try {
    await auditPurchase(detail.value.req.id, { approve, comment: auditComment.value })
    ElMessage.success(approve ? '已按当前节点通过，单据推进' : '已驳回（终态），意见已留痕')
    detailVisible.value = false
    load()
  } finally {
    auditing.value = false
  }
}

onMounted(async () => {
  load()
  // 导师下拉：有 system:user:list 权限则拉取（无权限静默降级为后端守卫）
  userOptions('hzm:purchase:audit-advisor').then(d => { users.value = d || [] }).catch(() => {})
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
.form-grid .span2 { grid-column: span 2; }
.detail-desc { margin-bottom: 14px; }
.steps-panel { background: var(--c-safe-bg); border-radius: var(--r); padding: 16px 10px 8px; margin-bottom: 12px; }
/* 步骤条统一主色橙（is-success 的绿色完成态收回到主色） */
.steps-panel :deep(.el-step__head.is-finish) { color: var(--c-safe); border-color: var(--c-safe); }
.steps-panel :deep(.el-step__head.is-finish .el-step__line) { background-color: var(--c-safe-line); }
.steps-panel :deep(.el-step__head.is-process) { color: var(--c-safe); border-color: var(--c-safe); }
.rec-table { margin-bottom: 12px; }
.audit-bar { border-top: 2px dashed var(--c-line); padding-top: 12px; }
.audit-btns { display: flex; gap: 10px; margin-top: 8px; }
</style>
