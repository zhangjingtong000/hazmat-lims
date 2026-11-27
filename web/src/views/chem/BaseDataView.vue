<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">MASTER DATA · 监管底座</div>
        <div class="page-title" style="margin-top: 4px">基础数据</div>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>基础数据为监管底座：柜位按 GB 15603 分类存放（剧毒专柜双人双锁，柜内有剧毒批次时不可改类型/取消双锁）；禁配规则成对维护、成对删除，不可与自身互配。</span>
    </div>

    <div class="page-card tab-card">
      <el-tabs v-model="tab">
        <!-- 柜位 -->
        <el-tab-pane label="存放柜位" name="cabinet">
          <div class="tab-toolbar">
            <el-button v-if="hasPerm('hzm:cabinet:maint')" type="primary" size="small" @click="openCabinetForm()">+ 新增柜位</el-button>
          </div>
          <el-table :data="cabinets" v-loading="loadingCab" stripe size="small">
            <el-table-column prop="code" label="编码" width="110"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
            <el-table-column prop="name" label="名称" min-width="130" />
            <el-table-column label="类型" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.type) === 5 ? 'danger' : 'info'">{{ CABINET_TYPE[row.type] }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="双锁" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.dualLock" size="small" type="warning" effect="plain">双人双锁</el-tag>
                <span v-else class="cell-sub">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="location" label="位置" min-width="130" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="hasPerm('hzm:cabinet:maint')" size="small" text type="primary" @click="openCabinetForm(row)">维护</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 禁配规则 -->
        <el-tab-pane label="禁配规则" name="compat">
          <div class="tab-toolbar">
            <el-button v-if="hasPerm('hzm:compat:maint')" type="primary" size="small" @click="compatVisible = true">+ 成对新增禁配</el-button>
            <span class="cell-sub">规则成对写入（A→B 与 B→A 各一条）；同组不可自配；禁配校验按批次实际柜位执行。</span>
          </div>
          <el-table :data="compats" v-loading="loadingCompat" stripe size="small">
            <el-table-column prop="groupA" label="组 A" width="110"><template #default="{ row }"><b class="mono">{{ row.groupA }}</b></template></el-table-column>
            <el-table-column label="×" width="50" align="center"><template #default>禁配</template></el-table-column>
            <el-table-column prop="groupB" label="组 B" width="110"><template #default="{ row }"><b class="mono">{{ row.groupB }}</b></template></el-table-column>
            <el-table-column prop="note" label="说明" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="hasPerm('hzm:compat:maint')" size="small" text type="danger" @click="doRemoveCompat(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 字典（DictController /system/dict，停用不删改） -->
        <el-tab-pane label="字典" name="dict">
          <div class="dict-toolbar">
            <el-select v-model="dictTypeFilter" clearable placeholder="全部类型" style="width: 180px" @change="loadDicts">
              <el-option v-for="t in DICT_TYPES" :key="t" :label="t" :value="t" />
            </el-select>
            <el-button v-if="hasPerm('hzm:compat:maint')" type="primary" size="small" @click="dictVisible = true">+ 新增字典项</el-button>
          </div>
          <el-table :data="dicts" size="small" class="dict-table" v-loading="loadingDict" stripe>
            <el-table-column prop="dictType" label="dictType" width="160" />
            <el-table-column prop="itemValue" label="itemValue" width="140" />
            <el-table-column prop="itemName" label="itemName" min-width="170" />
            <el-table-column prop="orderNum" label="排序" width="70" align="center" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.status === 0 ? 'success' : 'info'" size="small">{{ row.status === 0 ? '启用' : '停用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="hasPerm('hzm:compat:maint')" label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button size="small" text :type="row.status === 0 ? 'warning' : 'success'" @click="toggleDict(row)">
                  {{ row.status === 0 ? '停用' : '启用' }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="cell-sub" style="margin-top: 8px">停用不删改：停用项退出新建表单，历史单据按原值展示。</div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 柜位表单 -->
    <el-dialog v-model="cabinetVisible" :title="cabinetForm.id ? '维护柜位' : '新增柜位'" width="480px">
      <el-form ref="cabinetFormRef" :model="cabinetForm" label-width="100px">
        <el-form-item label="编码" prop="code" :rules="R.requiredLen(32, '柜位编码').concat(R.format(/^[A-Za-z0-9-]+$/, '柜位编码仅含字母/数字/连字符'))">
          <el-input v-model="cabinetForm.code" :disabled="!!cabinetForm.id" />
        </el-form-item>
        <el-form-item label="名称" prop="name" :rules="R.maxLen(100, '柜位名称')"><el-input v-model="cabinetForm.name" /></el-form-item>
        <el-form-item label="类型" prop="type" :rules="R.requiredPick('柜位类型')">
          <el-select v-model="cabinetForm.type" style="width: 100%">
            <el-option v-for="(t, k) in CABINET_TYPE" :key="k" :label="t" :value="Number(k)" />
          </el-select>
        </el-form-item>
        <el-form-item label="双人双锁">
          <el-switch v-model="cabinetForm.dualLock" :active-value="1" :inactive-value="0" />
          <span class="cell-sub" style="margin-left: 8px">柜内有在库剧毒批次时不可改类型/取消双锁</span>
        </el-form-item>
        <el-form-item label="位置" prop="location" :rules="R.maxLen(100, '位置')"><el-input v-model="cabinetForm.location" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cabinetVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCabinet">保存</el-button>
      </template>
    </el-dialog>

    <!-- 禁配成对新增 -->
    <el-dialog v-model="compatVisible" title="成对新增禁配规则" width="440px">
      <el-form ref="compatFormRef" :model="compatForm" label-width="90px">
        <el-form-item label="组 A" prop="groupA" :rules="R.requiredLen(8, '组 A')">
          <el-select v-model="compatForm.groupA" filterable allow-create style="width: 100%">
            <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="组 B" prop="groupB" :rules="R.requiredLen(8, '组 B').concat(R.notEqual(() => compatForm.groupA, '组 B'))">
          <el-select v-model="compatForm.groupB" filterable allow-create style="width: 100%">
            <el-option v-for="g in COMPAT_GROUPS" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" prop="note" :rules="R.maxLen(200, '说明')"><el-input v-model="compatForm.note" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="compatVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveCompat">成对写入</el-button>
      </template>
    </el-dialog>

    <!-- 字典项新增 -->
    <el-dialog v-model="dictVisible" title="新增字典项" width="440px">
      <el-form ref="dictFormRef" :model="dictForm" label-width="100px">
        <el-form-item label="dictType" prop="dictType" :rules="R.requiredPick('字典类型')">
          <el-select v-model="dictForm.dictType" filterable allow-create style="width: 100%">
            <el-option v-for="t in DICT_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="itemName" prop="itemName" :rules="R.requiredLen(50, '字典名称')"><el-input v-model="dictForm.itemName" /></el-form-item>
        <el-form-item label="itemValue" prop="itemValue" :rules="R.maxLen(50, '字典值')"><el-input v-model="dictForm.itemValue" /></el-form-item>
        <el-form-item label="排序" prop="orderNum" :rules="R.nonNegative('排序')">
          <el-input-number v-model="dictForm.orderNum" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dictVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveDict">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listCabinet, addCabinet, editCabinet, listCompat, addCompat, removeCompat } from '../../api/chem'
import { listDict, addDict, setDictStatus } from '../../api/system'
import { hasPerm } from '../../store/auth'
import { CABINET_TYPE, COMPAT_GROUPS } from '../../utils/constants'
import R from '../../utils/validation'

const tab = ref('cabinet')
const cabinets = ref([])
const compats = ref([])
const loadingCab = ref(false)
const loadingCompat = ref(false)
const saving = ref(false)
const cabinetVisible = ref(false)
const compatVisible = ref(false)
const cabinetFormRef = ref(null)
const compatFormRef = ref(null)
const dictFormRef = ref(null)
const cabinetForm = reactive({})
const compatForm = reactive({ groupA: '', groupB: '', note: '' })

const DICT_TYPES = ['unit', 'compat_group', 'waste_method', 'cabinet_type', 'disposal_target']
const dicts = ref([])
const loadingDict = ref(false)
const dictTypeFilter = ref('')
const dictVisible = ref(false)
const dictForm = reactive({ dictType: '', itemName: '', itemValue: '', orderNum: 0 })

async function loadDicts() {
  loadingDict.value = true
  try {
    const data = await listDict({ dictType: dictTypeFilter.value || undefined, page: 1, size: 100 })
    dicts.value = data.records || []
  } finally {
    loadingDict.value = false
  }
}

async function saveDict() {
  const valid = await dictFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('dictType 与 itemName 必填')
    return
  }
  saving.value = true
  try {
    await addDict({ ...dictForm })
    ElMessage.success('字典项已新增')
    dictVisible.value = false
    loadDicts()
  } finally {
    saving.value = false
  }
}

async function toggleDict(row) {
  await ElMessageBox.confirm(
    row.status === 0 ? '停用后该项退出新建表单，历史单据仍按原值展示。' : '启用后该项恢复进入新建表单。',
    '字典停用/启用', { type: 'warning' }
  )
  await setDictStatus(row.id, row.status === 0 ? '1' : '0')
  ElMessage.success(row.status === 0 ? '已停用（停用不删改）' : '已启用')
  loadDicts()
}

async function loadCabinets() {
  loadingCab.value = true
  try { cabinets.value = (await listCabinet()) || [] } finally { loadingCab.value = false }
}
async function loadCompats() {
  loadingCompat.value = true
  try { compats.value = (await listCompat()) || [] } finally { loadingCompat.value = false }
}

function openCabinetForm(row) {
  Object.keys(cabinetForm).forEach(k => delete cabinetForm[k])
  if (row) Object.assign(cabinetForm, row)
  else Object.assign(cabinetForm, { code: '', name: '', type: 6, dualLock: 0, location: '' })
  cabinetVisible.value = true
  nextTick(() => cabinetFormRef.value?.clearValidate())
}

async function saveCabinet() {
  const valid = await cabinetFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('柜位编码必填')
    return
  }
  saving.value = true
  try {
    if (cabinetForm.id) {
      await editCabinet(cabinetForm)
      ElMessage.success('柜位已维护（剧毒在柜守卫已校验）')
    } else {
      await addCabinet(cabinetForm)
      ElMessage.success('柜位已新增（默认通用柜可后改）')
    }
    cabinetVisible.value = false
    loadCabinets()
  } finally {
    saving.value = false
  }
}

async function saveCompat() {
  const valid = await compatFormRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('组 A / 组 B 必填，且不得为同一组')
    return
  }
  if (compatForm.groupA === compatForm.groupB) {
    ElMessage.warning('同组不得自配')
    return
  }
  saving.value = true
  try {
    await addCompat({ ...compatForm })
    ElMessage.success('禁配对已双向写入')
    compatVisible.value = false
    loadCompats()
  } finally {
    saving.value = false
  }
}

async function doRemoveCompat(row) {
  await ElMessageBox.confirm(`删除禁配对 ${row.groupA} × ${row.groupB}（双向删）？`, '删除确认', { type: 'warning' })
  await removeCompat(row.groupA, row.groupB)
  ElMessage.success('禁配对已双向删除')
  loadCompats()
}

onMounted(() => {
  loadCabinets()
  loadCompats()
  loadDicts()
})
</script>

<style scoped>
.tab-card { padding: 6px 16px 16px; }
.tab-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.dict-toolbar { display: flex; gap: 10px; align-items: center; }
.dict-table { margin-top: 12px; }
</style>
