<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">STOCK-TAKING · 双人制</div>
        <div class="page-title" style="margin-top: 4px">账物盘点</div>
      </div>
      <div class="head-actions">
        <el-button v-if="hasPerm('hzm:check:do')" type="primary" @click="openCreate">+ 新建盘点单</el-button>
        <el-button :loading="exporting" @click="doExport">导出</el-button>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>账物盘点实行双人制（两名盘点人不得为同一人）：结单只记差异、不改库存；调账按差值增减修正、台账同步，不支持直接"设为实物量"。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-select v-model="filters.result" placeholder="盘点结果" clearable style="width: 150px">
          <el-option label="盘点中" :value="0" /><el-option label="平" :value="1" /><el-option label="不平" :value="2" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="单号" width="150">
          <template #default="{ row }">
            <el-link type="primary" @click="$router.push(`/chem/check/${row.id}`)"><span class="doc-code">{{ row.code }}</span></el-link>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="140">
          <template #default="{ row }">
            <template v-if="row.scopeCategory != null">
              <GhzTag :category="row.scopeCategory" :size="20" with-bg />
              <span style="margin-left: 4px">{{ categoryOf(row.scopeCategory).name }}</span>
            </template>
            <span v-else>全部在库</span>
          </template>
        </el-table-column>
        <el-table-column prop="checkerOneName" label="盘点人一" width="100" />
        <el-table-column prop="checkerTwoName" label="盘点人二" width="100" />
        <el-table-column prop="diffCount" label="差异数" width="80" align="right">
          <template #default="{ row }">
            <span :class="{ 'exp-danger': row.diffCount > 0 }">{{ row.diffCount ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="96">
          <template #default="{ row }">
            <el-tag :type="CHECK_RESULT[row.result]?.type" size="small">{{ CHECK_RESULT[row.result]?.label || '盘点中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="checkedTime" label="结单时间" width="170">
          <template #default="{ row }">{{ row.checkedTime || '—' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="$router.push(`/chem/check/${row.id}`)">明细/处置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <!-- 新建盘点单（双人制） -->
    <el-dialog v-model="createVisible" title="新建盘点单（PD · 双人制）" width="480px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <el-form-item label="盘点范围" prop="scopeCategory" :rules="R.range(0, 4, '盘点范围分类')">
          <el-select v-model="form.scopeCategory" clearable style="width: 100%" placeholder="可空=全部在库">
            <el-option v-for="(c, k) in CATEGORIES" :key="k" :label="`${c.key} · ${c.name}`" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点人二" prop="checkerTwoId" :rules="R.requiredPick('盘点人二')">
          <el-select v-model="form.checkerTwoId" filterable style="width: 100%">
            <el-option v-for="u in users.filter(u => u.id !== auth.user?.id)" :key="u.id"
              :label="`${u.nickname || u.username}（#${u.id}）`" :value="u.id" />
          </el-select>
          <div class="cell-sub">盘点人一为本人；两人不得相同（服务层守卫）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">制单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import GhzTag from '../../components/GhzTag.vue'
import { addCheck, listCheck } from '../../api/chem'
import { userOptions } from '../../api/system'
import { auth, hasPerm } from '../../store/auth'
import { CATEGORIES, CHECK_RESULT, categoryOf } from '../../utils/constants'
import R from '../../utils/validation'
import { exportExcel } from '../../utils/export'

const router = useRouter()
const filters = reactive({ result: null })
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const exporting = ref(false)
const users = ref([])
const createVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({ scopeCategory: null, checkerTwoId: null })

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listCheck({ result: filters.result ?? undefined, page: page.value, size: size.value })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.scopeCategory = null
  form.checkerTwoId = null
  createVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('盘点人二必填（双人制）')
    return
  }
  saving.value = true
  try {
    const saved = await addCheck({ scopeCategory: form.scopeCategory ?? null, checkerTwoId: form.checkerTwoId })
    ElMessage.success(`盘点单已制（${saved?.code || 'PD'}），请前往明细页录入实物量并结单`)
    createVisible.value = false
    if (saved?.id) router.push(`/chem/check/${saved.id}`)
    else load(1)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  load()
  userOptions('hzm:check:do').then(d => { users.value = d || [] }).catch(() => {})
})

/** D1 导出：遵循当前结果筛选条件 */
async function doExport() {
  exporting.value = true
  try {
    await exportExcel({
      business: '账物盘点单',
      columns: [
        { prop: 'code', label: '单号' },
        { prop: row => (row.scopeCategory != null ? categoryOf(row.scopeCategory).name : '全部在库'), label: '盘点范围' },
        { prop: 'checkerOneName', label: '盘点人一' },
        { prop: 'checkerTwoName', label: '盘点人二' },
        { prop: row => row.diffCount ?? '', label: '差异数' },
        { prop: row => CHECK_RESULT[row.result]?.label || '盘点中', label: '结果' },
        { prop: 'checkedTime', label: '结单时间' },
        { prop: 'remark', label: '备注' }
      ],
      fetchPage: (p, s) => listCheck({ result: filters.result ?? undefined, page: p, size: s })
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
.doc-code { font-family: var(--f-mono); color: var(--c-safe-deep); font-weight: 600; }
.exp-danger { color: var(--c-jd); font-weight: 700; }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.head-actions { display: flex; gap: 8px; align-items: center; }
</style>
