<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">APPROVAL CENTER · 先审先生效</div>
        <div class="page-title" style="margin-top: 4px">审批中心</div>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>审批中心按角色聚合四类待办（先审先生效，同一单据不会重复进入待办）；"我的已办"回看本人签批结论与经手的领用签核；前端按按钮权限过滤动作，后端对身份与角色再做校验兜底。</span>
    </div>

    <div class="page-card todo-card">
      <el-tabs v-model="tab">
        <el-tab-pane>
          <template #label><span class="tab-label">待审申购 <el-badge :value="data.purchaseCount || 0" type="warning" /></span></template>
          <el-table :data="data.purchases || []" v-loading="loading" stripe>
            <el-table-column prop="code" label="单号" width="150"><template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="chemicalName" label="化学品" min-width="130" />
            <el-table-column label="分类" width="90">
              <template #default="{ row }"><GhzTag :category="row.category" :size="22" with-bg /></template>
            </el-table-column>
            <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ row.qty }} {{ row.unit }}</template></el-table-column>
            <el-table-column prop="applicantName" label="申请人" width="104">
              <template #default="{ row }">{{ shortName(row.applicantName) }}</template>
            </el-table-column>
            <el-table-column label="当前级" width="110">
              <template #default="{ row }">第 {{ row.currentSeq }} 级 / {{ chainLen(row.category) }} 级</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="goPurchase(row)">详情/审批</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane>
          <template #label><span class="tab-label">待签领用 <el-badge :value="data.requisitionCount || 0" type="warning" /></span></template>
          <el-table :data="data.requisitions || []" v-loading="loading" stripe>
            <el-table-column prop="code" label="单号" width="150"><template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="chemicalName" label="化学品" min-width="130" />
            <el-table-column label="数量" width="80" align="right"><template #default="{ row }">{{ row.qty }}</template></el-table-column>
            <el-table-column prop="applicantName" label="领用人" width="104" />
            <el-table-column label="节点" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 0 ? 'warning' : 'success'">{{ row.status === 0 ? '待导师签' : '待复核' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="$router.push(`/chem/req/${row.id}`)">详情/签批</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane>
          <template #label><span class="tab-label">待审废弃 <el-badge :value="data.wasteCount || 0" type="warning" /></span></template>
          <el-table :data="data.wastes || []" v-loading="loading" stripe>
            <el-table-column prop="code" label="单号" width="150"><template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="chemicalName" label="化学品" min-width="130" />
            <el-table-column label="类型" width="100">
              <template #default="{ row }">{{ WASTE_TYPE[row.type] || row.type }}</template>
            </el-table-column>
            <el-table-column prop="operatorName" label="经办人" width="104" />
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="$router.push(`/chem/waste/${row.id}`)">详情/审批</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane>
          <template #label><span class="tab-label">归还确认 <el-badge :value="data.returnCount || 0" type="warning" /></span></template>
          <el-table :data="data.returns || []" v-loading="loading" stripe>
            <el-table-column prop="code" label="单号" width="170"><template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="chemicalName" label="化学品" min-width="130" />
            <el-table-column label="归还量" width="90" align="right"><template #default="{ row }">{{ row.qty }}</template></el-table-column>
            <el-table-column prop="applicantName" label="归还人" width="104" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text type="success" @click="doConfirm(row)">确认回库</el-button>
                <el-button size="small" text type="danger" @click="doReject(row)">拒绝</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane>
          <template #label><span class="tab-label">我的已办</span></template>
          <div class="done-sub">近 20 条本人签批结论（申购四级审批 + 领用导师/复核双签 + 废弃审批），按单号可回看签批明细。</div>
          <el-table :data="data.done || []" v-loading="loading" stripe>
            <el-table-column prop="code" label="单号" width="160">
              <template #default="{ row }">
                <el-link type="primary" @click="goDone(row)"><span class="doc-code">{{ row.code }}</span></el-link>
              </template>
            </el-table-column>
            <el-table-column prop="bizLabel" label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.bizType === 1 ? 'primary' : row.bizType === 2 ? 'danger' : 'success'">{{ row.bizLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="chemName" label="化学品" min-width="140" show-overflow-tooltip />
            <el-table-column prop="nodeName" label="签批节点" width="110" />
            <el-table-column label="结论" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="row.result === 1 ? 'success' : 'danger'">{{ row.result === 1 ? '通过' : '驳回' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="comment" label="批注" min-width="160" show-overflow-tooltip>
              <template #default="{ row }">{{ row.comment || '—' }}</template>
            </el-table-column>
            <el-table-column label="签批时间" width="170">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { InfoFilled } from '@element-plus/icons-vue'
import GhzTag from '../../components/GhzTag.vue'
import { getTodo, confirmReturn, rejectReturn } from '../../api/chem'
import { WASTE_TYPE } from '../../utils/constants'

const router = useRouter()
const data = ref({})
const loading = ref(false)
const tab = ref('0')

const chainLen = c => ({ 0: 1, 1: 2, 2: 3, 3: 3, 4: 4 }[Number(c)] ?? 1)
const goPurchase = row => router.push(`/chem/purchase?focus=${row.id}`)
/** 表头已标明角色，去掉昵称尾部冗余的（角色）后缀，避免人名列过宽 */
const shortName = n => { if (!n) return ''; const s = String(n).replace(/（[^）]*）$/, ''); return s || String(n) }

/** 已办跳转：1申购 → 申购列表 focus；2废弃 → 处置详情；3领用 → 领用详情 */
function goDone(row) {
  if (row.bizType === 1) router.push(`/chem/purchase?focus=${row.bizId}`)
  else if (row.bizType === 2) router.push(`/chem/waste/${row.bizId}`)
  else router.push(`/chem/req/${row.bizId}`)
}

async function load() {
  loading.value = true
  try {
    data.value = await getTodo()
  } finally {
    loading.value = false
  }
}

async function doConfirm(row) {
  await ElMessageBox.confirm(`确认该归还单 ${row.code} 已按原批次回库？足额归还将自动关单。`, '归还确认', { type: 'warning' })
  await confirmReturn(row.id)
  ElMessage.success('已确认回库（回原批次，守恒落账）')
  load()
}

async function doReject(row) {
  const { value } = await ElMessageBox.prompt('请填写拒绝原因（必填）', '归还拒绝', { inputPattern: /\S/, inputErrorMessage: '拒绝原因必填' })
  await rejectReturn(row.id, value)
  ElMessage.success('已拒绝并留痕')
  load()
}

onMounted(load)
</script>

<style scoped>
.todo-card { padding: 6px 16px 16px; }
.tab-label { display: inline-flex; align-items: center; gap: 6px; }
.doc-code { font-family: var(--f-mono); color: var(--c-safe-deep); font-weight: 600; }
.done-sub { color: var(--c-graphite-soft); font-size: 0.78rem; margin: 2px 0 10px; }
</style>
