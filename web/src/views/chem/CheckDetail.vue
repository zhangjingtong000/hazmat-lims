<template>
  <div class="page-wrap" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="eyebrow">STOCK-TAKING DOC · 双人制盘点</div>
        <div class="page-title" style="margin-top: 4px">盘点单详情</div>
      </div>
      <el-button @click="$router.push('/chem/check')">← 返回盘点列表</el-button>
    </div>

    <template v-if="check">
      <div class="page-card head-card">
        <div class="head-row">
          <div>
            <div class="head-title">
              <span class="doc-code" style="font-size: 1.2rem">{{ check.code }}</span>
              <el-tag :type="CHECK_RESULT[check.result]?.type || 'warning'" style="margin-left: 10px">
                {{ CHECK_RESULT[check.result]?.label || '盘点中' }}
              </el-tag>
            </div>
            <div class="head-sub">
              范围：{{ check.scopeCategory != null ? categoryOf(check.scopeCategory).name : '全部在库' }}
              · 盘点人一 {{ check.checkerOneName || check.checkerOneId }} / 盘点人二 {{ check.checkerTwoName || check.checkerTwoId }}
            </div>
            <div class="head-meta">
              <span>差异数：{{ check.diffCount ?? '—' }}</span>
              <span>结单时间：{{ check.checkedTime || '—' }}</span>
              <span>备注：{{ check.remark || '—' }}</span>
            </div>
          </div>
          <div class="head-actions">
            <el-button v-if="check.result === 0 && canFinish" type="warning" @click="finishVisible = true">录入实物量 / 结单</el-button>
          </div>
        </div>
        <el-alert v-if="check.result === 0" type="warning" :closable="false" show-icon
          title="盘点中：结单须由盘点人执行且双人不同；差异记录不改库存，调账走增量修正。" />
      </div>

      <!-- 差异明细 -->
      <div class="page-card sec-card">
        <div class="card-head">
          <span class="page-title">差异明细（chem_check_diff）</span>
          <span class="cell-sub">调账=增量修正：qty_left=qty_left+diff（≥0、批次在库守卫），台账同步±</span>
        </div>
        <el-table :data="diffs" size="small" stripe>
          <el-table-column label="批次" min-width="140">
            <template #default="{ row }"><span class="mono">{{ batchLabel(row.batchId) }}</span></template>
          </el-table-column>
          <el-table-column prop="bookQty" label="账面量" width="90" align="right" />
          <el-table-column prop="actualQty" label="实物量" width="90" align="right" />
          <el-table-column label="差异" width="90" align="right">
            <template #default="{ row }">
              <span :class="Number(row.diffQty) > 0 ? 'diff-pos' : Number(row.diffQty) < 0 ? 'exp-danger' : ''">
                {{ Number(row.diffQty) > 0 ? '+' : '' }}{{ row.diffQty }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          <el-table-column prop="adjustTime" label="调账时间" width="170">
            <template #default="{ row }">{{ row.adjustTime || '—' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button v-if="check.result === 2 && !row.adjustTime && hasPerm('hzm:check:adjust')"
                size="small" text type="primary" @click="doAdjust(row)">调账</el-button>
              <span v-else-if="row.adjustTime" class="cell-sub">已调账</span>
              <span v-else-if="check.result === 2" class="cell-sub">无调账权限</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!diffs.length" description="暂无差异记录（结单后生成）" :image-size="60" />
      </div>

      <!-- 录入实物量 / 结单 -->
      <el-dialog v-model="finishVisible" title="录入实物量并结单（不改库存）" width="720px" top="5vh">
        <el-table :data="scopeBatches" size="small" max-height="380">
          <el-table-column prop="chemicalName" label="化学品" min-width="120" />
          <el-table-column prop="lotNo" label="批号" width="130"><template #default="{ row }"><span class="mono">{{ row.lotNo }}</span></template></el-table-column>
          <el-table-column prop="qtyLeft" label="账面量" width="80" align="right" />
          <el-table-column label="实物量" width="140">
            <template #default="{ row }">
              <el-input-number v-model="finishForm[row.id]" :min="0" :precision="2" size="small" style="width: 120px" />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="130">
            <template #default="{ row }"><el-input v-model="finishRemark[row.id]" size="small" placeholder="差异备注" /></template>
          </el-table-column>
        </el-table>
        <div class="cell-sub" style="margin-top: 6px">
          book/diff 由服务层快照计算，前端仅报 batchId/actualQty/remark；平的批次可保持与账面一致。
        </div>
        <template #footer>
          <el-button @click="finishVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="doFinish">结单</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { checkDetail, finishCheck, adjustCheck, listBatch, listChemInfo } from '../../api/chem'
import { auth, hasPerm } from '../../store/auth'
import { CHECK_RESULT, categoryOf } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const check = ref(null)
const diffs = ref([])
const scopeBatches = ref([])
const chemMap = ref({})
const finishVisible = ref(false)
const finishForm = reactive({})
const finishRemark = reactive({})
const saving = ref(false)

const canFinish = computed(() => hasPerm('hzm:check:do')
  && [check.value?.checkerOneId, check.value?.checkerTwoId].includes(auth.user?.id))
const batchLabel = id => {
  const b = scopeBatches.value.find(x => x.id === id)
  return b ? b.lotNo : ('批次 #' + id)
}

async function load() {
  loading.value = true
  try {
    const vo = await checkDetail(route.params.id)
    check.value = vo.check
    diffs.value = vo.diffs || []
  } finally {
    loading.value = false
  }
}

async function doAdjust(row) {
  await ElMessageBox.confirm(
    `调账为增量修正（${Number(row.diffQty) > 0 ? '盘盈回加' : '盘亏条件扣减'}，台账同步±）。确定调账该差异行？`,
    '差异调账', { type: 'warning' }
  )
  await adjustCheck(check.value.id, row.id)
  ElMessage.success('调账完成（批次与台账同步修正，留痕含 check_id/diff）')
  load()
}

onMounted(async () => {
  await load()
  if (check.value) openFinishPreload()
})

async function openFinishPreload() {
  // 预加载范围批次（差异行批次号翻译 + 结单数据源）
  const all = await listBatch({ page: 1, size: 300 }).catch(() => ({ records: [] }))
  const chems = await listChemInfo({ page: 1, size: 300 }).catch(() => ({ records: [] }))
  ;(chems.records || []).forEach(c => { chemMap.value[c.id] = c.category })
  scopeBatches.value = (all.records || []).filter(b =>
    check.value.scopeCategory == null || Number(chemMap.value[b.chemicalId]) === Number(check.value.scopeCategory))
}

async function doFinish() {
  const payload = scopeBatches.value.map(b => ({
    batchId: b.id,
    actualQty: finishForm[b.id] ?? Number(b.qtyLeft),
    remark: finishRemark[b.id] || null
  }))
  saving.value = true
  try {
    await finishCheck(check.value.id, payload)
    ElMessage.success('已结单：差异已记录（不改库存），不平差异请逐行调账')
    finishVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

// 结单对话框打开时初始化实物量输入（默认=账面量）
watch(finishVisible, v => {
  if (v) scopeBatches.value.forEach(b => {
    if (finishForm[b.id] == null) finishForm[b.id] = Number(b.qtyLeft)
  })
})
</script>

<style scoped>
.head-card { padding: 18px 22px; }
.head-row { display: flex; justify-content: space-between; gap: 16px; }
.head-title { display: flex; align-items: center; }
.head-sub { color: var(--c-graphite); margin-top: 6px; }
.head-meta { display: flex; gap: 20px; color: var(--c-graphite-soft); font-size: 0.8rem; margin-top: 6px; flex-wrap: wrap; }
.head-actions { flex: none; }
.sec-card { padding: 14px 18px; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.exp-danger { color: var(--c-jd); font-weight: 700; }
.diff-pos { color: var(--c-pt); font-weight: 700; }
</style>
