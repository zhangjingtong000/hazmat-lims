<template>
  <div class="page-wrap" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="eyebrow">REQUISITION DOC · 领用与归还</div>
        <div class="page-title" style="margin-top: 4px">领用单详情</div>
      </div>
      <el-button @click="$router.push('/chem/requisition')">← 返回领用列表</el-button>
    </div>

    <template v-if="req">
      <!-- 单头 -->
      <div class="page-card head-card">
        <div class="head-row">
          <div>
            <div class="head-title">
              <span class="doc-code" style="font-size: 1.2rem">{{ req.code }}</span>
              <el-tag :type="REQ_STATUS[req.status]?.type" style="margin-left: 10px">{{ REQ_STATUS[req.status]?.label }}</el-tag>
              <span v-if="req.status === 4 && req.returnQty >= req.qty" class="cell-sub" style="margin-left: 8px">归还完毕关闭</span>
              <span v-else-if="req.status === 4" class="cell-sub" style="margin-left: 8px">核销关闭</span>
            </div>
            <div class="head-sub">
              {{ req.chemicalName }} · 领用 {{ req.qty }} · 已还 {{ req.returnQty || 0 }}
              <template v-if="req.reqId"> · 关联申购 #{{ req.reqId }}</template>
              <template v-else> · 存量领用</template>
            </div>
            <div class="head-meta">
              <span>领用人：{{ req.applicantName || req.applicantId }}</span>
              <span>归还时限：{{ req.expectReturn || '—' }}</span>
              <span>出库时间：{{ req.outTime || '—' }}</span>
            </div>
            <div class="head-purpose">用途：{{ req.purpose || '—' }}<template v-if="req.experiment"> ｜ 实验：{{ req.experiment }}</template></div>
          </div>
          <div class="head-actions">
            <el-button @click="$router.push(`/print/req/${req.id}`)">打印领用单</el-button>
            <el-button v-if="req.status === 0 && hasPerm('hzm:req:advisor')" type="warning" @click="sign('advisor')">导师签</el-button>
            <el-button v-if="req.status === 1 && hasPerm('hzm:req:check')" type="success" @click="sign('check')">复核双签</el-button>
            <el-button v-if="req.status === 2 && isMine" type="primary" @click="openReturn">余量归还</el-button>
            <el-button v-if="req.status === 2 && isMine" type="warning" plain @click="doClose">用尽核销</el-button>
          </div>
        </div>
        <el-alert v-if="req.status === 2 && overdue" type="error" :closable="false" show-icon
          title="已超归还时限——依《危化品使用登记规程》第 8 条，余量应立即归还或核销" />
      </div>

      <!-- 出库分配明细 -->
      <div class="page-card sec-card">
        <div class="card-head">
          <span class="page-title">出库分配明细（按效期升序逐批扣减）</span>
          <span class="cell-sub">改派：原批回加→新批扣减（等量换批，台账现存不变）</span>
        </div>
        <el-table :data="allocRows" size="small" stripe>
          <el-table-column prop="seqNo" label="序" width="56" />
          <el-table-column label="批次" min-width="150">
            <template #default="{ row }"><span class="mono">{{ batchInfo(row.batchId)?.lotNo || ('批次 #' + row.batchId) }}</span></template>
          </el-table-column>
          <el-table-column label="效期" width="110">
            <template #default="{ row }">{{ batchInfo(row.batchId)?.expiryDate || '—' }}</template>
          </el-table-column>
          <el-table-column label="柜位" width="110">
            <template #default="{ row }">{{ batchInfo(row.batchId)?.cabinetName || batchInfo(row.batchId)?.cabinetId || '—' }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="分配量" width="90" align="right" />
          <el-table-column label="操作" width="140">
            <template #default="{ row }">
              <el-button v-if="req.status === 2 && hasPerm('hzm:req:dispatch')"
                size="small" text type="warning" @click="openDispatch(row)">改派批次</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!allocRows.length" description="尚未出库（复核通过后生成出库分配明细）" :image-size="60" />
      </div>

      <!-- 归还记录（本单据） -->
      <div class="page-card sec-card">
        <div class="card-head">
          <span class="page-title">余量归还记录</span>
          <span class="cell-sub">归还回原批次；确认人限安全员/学院；累计归还达到领用量即自动关单</span>
        </div>
        <el-table :data="returns" size="small" stripe>
          <el-table-column prop="code" label="归还单号" width="150"><template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template></el-table-column>
          <el-table-column label="批次" min-width="140">
            <template #default="{ row }"><span class="mono">{{ batchInfo(row.batchId)?.lotNo || ('#' + row.batchId) }}</span></template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="80" align="right" />
          <el-table-column label="申请人/确认人" width="130">
            <template #default="{ row }">
              <span>{{ row.applicantName || '—' }}</span>
              <span v-if="row.confirmerName" class="cell-sub"> / {{ row.confirmerName }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="comment" label="说明/拒因" min-width="140" show-overflow-tooltip />
          <el-table-column label="状态" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 2 ? 'danger' : 'warning'">{{ RETURN_STATUS[row.status] }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="确认操作" width="170">
            <template #default="{ row }">
              <template v-if="row.status === 0 && !isMine">
                <el-button v-if="hasPerm('hzm:return:confirm')" size="small" text type="success" @click="doConfirmReturn(row)">确认回库</el-button>
                <el-button v-if="hasPerm('hzm:return:reject')" size="small" text type="danger" @click="doRejectReturn(row)">拒绝</el-button>
              </template>
              <span v-else class="cell-sub">{{ row.status === 0 ? '待确认' : '已办结' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- 归还对话框 -->
    <el-dialog v-model="returnVisible" title="余量归还（回原批次）" width="460px">
      <el-form ref="returnFormRef" :model="returnForm" label-width="100px">
        <el-form-item label="归还批次" prop="batchId" :rules="R.requiredPick('归还批次')">
          <el-select v-model="returnForm.batchId" style="width: 100%">
            <el-option v-for="a in allocRows" :key="a.batchId"
              :label="`${batchInfo(a.batchId)?.lotNo || ('#' + a.batchId)}（分配 ${a.qty}）`" :value="a.batchId" />
          </el-select>
        </el-form-item>
        <el-form-item label="归还数量" prop="qty" :rules="R.positive('归还数量')">
          <el-input-number v-model="returnForm.qty" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="说明" prop="comment" :rules="R.maxLen(200, '说明')">
          <el-input v-model="returnForm.comment" placeholder="如 实验余量按规归还" />
        </el-form-item>
      </el-form>
      <div class="cell-sub">累计归还（含本次）不得超过领用量。</div>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveReturn">提交归还</el-button>
      </template>
    </el-dialog>

    <!-- 改派对话框 -->
    <el-dialog v-model="dispatchVisible" title="批次改派（等量换批）" width="520px">
      <el-form ref="dispatchFormRef" :model="dispatchForm" label-width="100px">
        <el-form-item label="原批次">
          <el-input :value="`${batchInfo(dispatchForm.originalBatchId)?.lotNo || ''}（分配 ${dispatchForm.qty}）`" disabled />
        </el-form-item>
        <el-form-item label="目标批次" prop="newBatchId" :rules="R.requiredPick('目标批次')">
          <el-select v-model="dispatchForm.newBatchId" style="width: 100%">
            <el-option v-for="b in alternativeBatches(dispatchForm.originalBatchId)" :key="b.id"
              :label="`${b.lotNo}（余 ${b.qtyLeft} · 效期 ${b.expiryDate || '—'} · ${b.cabinetName || ''}）`" :value="b.id" />
          </el-select>
          <div class="cell-sub">余量不足则整单退回不出库；台账现存等量不动。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dispatchVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDispatch">执行改派</el-button>
      </template>
    </el-dialog>

    <!-- 导师签/复核签对话框 -->
    <el-dialog v-model="signVisible" :title="signType === 'advisor' ? '导师签（仅指定导师，回避申请人）' : '复核双签（复核人不得为申请人）'" width="480px">
      <el-input v-model="signComment" type="textarea" :rows="3" placeholder="意见（驳回必填；通过可填）" />
      <div class="cell-sub" style="margin-top: 6px">
        {{ signType === 'advisor' ? '仅限单据指定的导师本人签批，且须持导师身份。' : '通过后按先到期先出逐批扣减出库，余量不足即拒绝。' }}
      </div>
      <template #footer>
        <el-button type="danger" :loading="saving" @click="doSign(false)">驳回（终态）</el-button>
        <el-button type="success" :loading="saving" @click="doSign(true)">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  reqDetail, advisorReq, checkReq, dispatchReq, returnReq, confirmReturn, rejectReturn, listBatch, closeReq, listReqReturns
} from '../../api/chem'
import { auth, hasPerm } from '../../store/auth'
import { REQ_STATUS, RETURN_STATUS } from '../../utils/constants'
import R from '../../utils/validation'

const route = useRoute()
const loading = ref(true)
const req = ref(null)
const allocs = ref([])
const chemBatches = ref([])
const returns = ref([])
const saving = ref(false)
const returnFormRef = ref(null)
const dispatchFormRef = ref(null)

const returnVisible = ref(false)
const returnForm = reactive({ batchId: null, qty: 1, comment: '' })
const dispatchVisible = ref(false)
const dispatchForm = reactive({ originalBatchId: null, newBatchId: null, qty: 0 })
const signVisible = ref(false)
const signType = ref('advisor')
const signComment = ref('')

const isMine = computed(() => Number(req.value?.applicantId) === Number(auth.user?.id))
const overdue = computed(() => req.value?.status === 2 && req.value?.expectReturn
  && String(req.value.expectReturn) < new Date().toISOString().slice(0, 10))
const allocRows = computed(() => allocs.value)
const batchInfo = id => chemBatches.value.find(b => b.id === id)
const alternativeBatches = orig => chemBatches.value.filter(b => b.id !== orig && b.status === 0 && Number(b.qtyLeft) >= dispatchForm.qty)

async function load() {
  loading.value = true
  try {
    const vo = await reqDetail(route.params.id)
    req.value = vo.req
    allocs.value = vo.batches || []
    if (vo.req?.chemicalId) {
      const data = await listBatch({ chemicalId: vo.req.chemicalId, page: 1, size: 100 }).catch(() => null)
      chemBatches.value = data?.records || []
    }
    returns.value = await listReqReturns(route.params.id).catch(() => [])
  } finally {
    loading.value = false
  }
}

function openReturn() {
  returnForm.batchId = allocRows.value[0]?.batchId || null
  returnForm.qty = 1
  returnForm.comment = ''
  returnVisible.value = true
  nextTick(() => returnFormRef.value?.clearValidate())
}

async function saveReturn() {
  const valid = await returnFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('批次与数量必填')
    return
  }
  saving.value = true
  try {
    await returnReq(req.value.id, { batchId: returnForm.batchId, qty: returnForm.qty })
    ElMessage.success('归还单已提交（GH），待安全员/学院确认回库')
    returnVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function openDispatch(row) {
  dispatchForm.originalBatchId = row.batchId
  dispatchForm.newBatchId = null
  dispatchForm.qty = Number(row.qty)
  dispatchVisible.value = true
  nextTick(() => dispatchFormRef.value?.clearValidate())
}

async function saveDispatch() {
  const valid = await dispatchFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('请选择目标批次')
    return
  }
  saving.value = true
  try {
    await dispatchReq(req.value.id, { originalBatchId: dispatchForm.originalBatchId, newBatchId: dispatchForm.newBatchId })
    ElMessage.success('已按等量换批执行改派（原批回加、新批扣减）')
    dispatchVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

function sign(type) {
  signType.value = type
  signComment.value = ''
  signVisible.value = true
}

async function doSign(approve) {
  if (!approve && !signComment.value.trim()) {
    ElMessage.warning('驳回必须填写意见（终态，重新领用走新单）')
    return
  }
  saving.value = true
  try {
    const body = { approve, comment: signComment.value }
    if (signType.value === 'advisor') {
      await advisorReq(req.value.id, body)
      ElMessage.success(approve ? '导师签通过，进入复核双签' : '导师签驳回（终态）')
    } else {
      await checkReq(req.value.id, body)
      ElMessage.success(approve ? '已按五双制度双签出库：出库分配明细已生成' : '复核驳回（终态）')
    }
    signVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doConfirmReturn(row) {
  await ElMessageBox.confirm('确认回库：校验领用单已出库→超量守卫→原批次回加（原子）。确认？', '归还确认', { type: 'info' })
  await confirmReturn(row.id)
  row.status = 1
  ElMessage.success('余量已确认回库，账物守恒校验通过')
  load()
}

async function doRejectReturn(row) {
  const { value } = await ElMessageBox.prompt('拒绝原因必填（N-3 留痕）', '归还拒绝', {
    inputPattern: /.+/, inputErrorMessage: '拒绝原因必填', type: 'warning'
  })
  await rejectReturn(row.id, value)
  row.status = 2
  ElMessage.success('归还已拒绝（原因留痕）')
}

async function doClose() {
  const { value } = await ElMessageBox.prompt(
    '核销说明必填：材料实验中用尽、Σ归还<领用量时核销关闭（2→4），否则单据永挂与超期预警误报。',
    '用尽核销', { inputPattern: /.+/, inputErrorMessage: '核销说明必填', type: 'warning' }
  )
  await closeReq(req.value.id, value)
  ElMessage.success('领用单已核销关闭（超期预警口径已排除）')
  load()
}

onMounted(load)
</script>

<style scoped>
.head-card { padding: 18px 22px; }
.head-row { display: flex; justify-content: space-between; gap: 16px; }
.head-title { display: flex; align-items: center; }
.head-sub { color: var(--c-graphite); margin-top: 6px; font-size: 0.92rem; }
.head-meta { display: flex; gap: 20px; color: var(--c-graphite-soft); font-size: 0.8rem; margin-top: 6px; flex-wrap: wrap; }
.head-purpose { margin-top: 8px; font-size: 0.86rem; color: var(--c-graphite); }
.head-actions { flex: none; display: flex; flex-direction: column; gap: 8px; align-items: stretch; }
.sec-card { padding: 14px 18px; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
