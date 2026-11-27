<template>
  <div class="page-wrap" v-loading="loading">
    <div class="page-header">
      <div>
        <div class="eyebrow">WASTE DISPOSAL DOC · 去向可追</div>
        <div class="page-title" style="margin-top: 4px">废弃处置单详情</div>
      </div>
      <el-button @click="$router.push('/chem/waste')">← 返回废弃处置</el-button>
    </div>

    <template v-if="waste">
      <div class="page-card head-card">
        <div class="head-row">
          <div>
            <div class="head-title">
              <span class="doc-code" style="font-size: 1.2rem">{{ waste.code }}</span>
              <el-tag :type="WASTE_STATUS[waste.status]?.type" style="margin-left: 10px">{{ WASTE_STATUS[waste.status]?.label }}</el-tag>
              <el-tag size="small" type="warning" style="margin-left: 6px">{{ WASTE_TYPE[waste.type] || waste.type }}</el-tag>
            </div>
            <div class="head-sub">
              {{ waste.chemicalName }} · 批次 <span class="mono">#{{ waste.batchId }}</span>
              · 数量 {{ waste.qty ?? '整批' }}
            </div>
            <div class="head-meta">
              <span>经办人：{{ waste.operatorName || waste.operatorId }}</span>
              <span>处置方式：{{ waste.method || '—' }}</span>
              <span>处置去向：{{ waste.disposalTarget || '—' }}</span>
              <span>转移凭证号：{{ waste.voucherNo || '—' }}</span>
              <span>完成时间：{{ waste.finishTime || '—' }}</span>
            </div>
          </div>
          <div class="head-actions">
            <el-button @click="$router.push(`/print/waste/${waste.id}`)">打印处置单</el-button>
            <el-button v-if="waste.status === 0 && hasPerm('hzm:waste:audit')" type="success" @click="auditVisible = true">审批处置单</el-button>
          </div>
        </div>
      </div>

      <!-- 审批记录 -->
      <div class="page-card sec-card">
        <div class="card-head">
          <span class="page-title">审批记录（biz_type=2，与申购链同轴单级）</span>
          <span class="cell-sub">approver≠operator 回避（⑦-8）；层级：普通/危化→学院，易制毒/易制爆/剧毒→校级</span>
        </div>
        <el-table :data="records" size="small" stripe>
          <el-table-column prop="seq" label="级次" width="60" />
          <el-table-column prop="nodeName" label="节点" width="120" />
          <el-table-column prop="comment" label="意见" min-width="200" show-overflow-tooltip />
          <el-table-column label="结果" width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '通过' : '驳回' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间" width="170" />
        </el-table>
        <el-empty v-if="!records.length" description="尚无审批记录" :image-size="60" />
      </div>

      <!-- 审批对话框 -->
      <el-dialog v-model="auditVisible" title="审批处置单" width="520px">
        <el-form ref="auditFormRef" :model="auditForm" label-width="110px">
          <el-form-item label="处置去向" prop="disposalTarget" :rules="R.maxLen(120, '处置去向')">
            <el-input v-model="auditForm.disposalTarget" placeholder="完成时必填（如 委托 XX 固废处理中心）" />
          </el-form-item>
          <el-form-item v-if="needVoucher" label="转移凭证号" prop="voucherNo" :rules="R.maxLen(50, '转移凭证号')">
            <el-input v-model="auditForm.voucherNo" placeholder="剧毒/易制毒/易制爆完成时必填（N-10）" />
          </el-form-item>
          <el-form-item label="审批意见" prop="comment" :rules="R.maxLen(500, '审批意见')">
            <el-input v-model="auditForm.comment" type="textarea" :rows="2" placeholder="驳回必填" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button type="danger" :loading="saving" @click="doAudit(false)">驳回</el-button>
          <el-button type="success" :loading="saving" @click="doAudit(true)">通过并销账</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { wasteDetail, auditWaste } from '../../api/chem'
import { hasPerm } from '../../store/auth'
import { WASTE_TYPE, WASTE_STATUS } from '../../utils/constants'
import R from '../../utils/validation'

const route = useRoute()
const loading = ref(true)
const waste = ref(null)
const records = ref([])
const auditVisible = ref(false)
const saving = ref(false)
const auditFormRef = ref(null)
const auditForm = reactive({ disposalTarget: '', voucherNo: '', comment: '' })

/** 剧毒(4)/易制毒(2)/易制爆(3) 完成时 voucher_no 必填——分类信息由列表/名称缺失时按提示兜底 */
const needVoucher = computed(() => {
  // waste 详情行不含 category，按凭证必要性提示全量展示（服务层精确校验）
  return waste.value?.status === 0
})

async function load() {
  loading.value = true
  try {
    const vo = await wasteDetail(route.params.id)
    waste.value = vo.waste
    records.value = vo.records || []
  } finally {
    loading.value = false
  }
}

async function doAudit(approve) {
  // 通过时必填处置去向 → 走声明式校验；驳回时校验意见长度
  const valid = await auditFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning(approve ? '完成处置必须填写处置去向' : '请检查审批意见')
    return
  }
  if (approve && !auditForm.disposalTarget.trim()) {
    ElMessage.warning('完成处置必须填写处置去向')
    return
  }
  if (!approve && !auditForm.comment.trim()) {
    ElMessage.warning('驳回必须填写意见')
    return
  }
  saving.value = true
  try {
    await auditWaste(waste.value.id, {
      approve,
      comment: auditForm.comment,
      disposalTarget: approve ? auditForm.disposalTarget : null,
      voucherNo: approve ? auditForm.voucherNo || null : null
    })
    ElMessage.success(approve ? '已审批通过并销账：去向/凭证已登记留痕' : '处置单已驳回')
    auditVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
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
</style>
