<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">SYSTEM ACCOUNTS · 账号与角色</div>
        <div class="page-title" style="margin-top: 4px">用户管理</div>
      </div>
      <el-button v-if="hasPerm('system:user:add')" type="primary" @click="openForm()">+ 新增用户</el-button>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="用户名 / 昵称" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="right" />
        <el-table-column prop="username" label="用户名" min-width="120">
          <template #default="{ row }"><span class="mono">{{ row.username }}</span></template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="170" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">{{ row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('system:user:edit')" size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button v-if="hasPerm('system:user:edit')" size="small" text type="success" @click="openRoles(row)">指派角色</el-button>
            <el-button v-if="hasPerm('system:user:resetPwd')" size="small" text type="warning" @click="doResetPwd(row)">重置密码</el-button>
            <el-button v-if="hasPerm('system:user:remove')" size="small" text type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑用户' : '新增用户'" width="460px">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="用户名" prop="username" :rules="R.requiredLen(30, '用户名').concat(R.format(/^[a-zA-Z][a-zA-Z0-9_]{1,29}$/, '用户名须以字母开头，2~30 位字母/数字/下划线'))">
          <el-input v-model="form.username" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初始密码" prop="password" :rules="R.password()">
          <el-input v-model="form.password" type="password" show-password placeholder="8~64 位，含两类字符" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname" :rules="R.maxLen(50, '昵称')"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="状态" prop="status" :rules="R.requiredPick('状态')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio>
          </el-radio-group>
          <div class="cell-sub">角色指派请使用列表中的"指派角色"（全量覆盖，先清后插）。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolesVisible" :title="`指派角色：${rolesUser && rolesUser.username}`" width="420px">
      <el-checkbox-group v-model="roleIds">
        <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id">{{ r.roleName }}（{{ r.remark }}）</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRoles" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listUser, addUser, editUser, removeUser, resetUserPwd, getUserRoles, assignUserRoles, listRole } from '../../api/system'
import { hasPerm } from '../../store/auth'
import R from '../../utils/validation'

const keyword = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})
const rolesVisible = ref(false)
const rolesUser = ref(null)
const allRoles = ref([])
const roleIds = ref([])
const savingRoles = ref(false)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listUser({ keyword: keyword.value || '', page: page.value, size: size.value })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) Object.assign(form, row)
  else Object.assign(form, { username: '', password: '', nickname: '', status: '0' })
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  // 编辑态只校验昵称/状态（用户名与密码不在本表单变更）
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('用户名与初始密码必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await editUser({ id: form.id, nickname: form.nickname, status: form.status })
      ElMessage.success('用户已更新')
    } else {
      await addUser(form)
      ElMessage.success('用户已新增')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doResetPwd(row) {
  const { value } = await ElMessageBox.prompt('输入新密码（8~64 位，须含大小写字母/数字/符号中至少两类；后端加密存储）', `重置密码：${row.username}`, {
    inputPattern: /^(?=.*[a-z])(?=.*\d).{8,64}$|^(?=.*[A-Z])(?=.*\d).{8,64}$|^(?=.*[^A-Za-z0-9]).{8,64}$/,
    inputErrorMessage: '密码须 8~64 位且至少包含两类字符', type: 'warning'
  })
  await resetUserPwd(row.id, value)
  ElMessage.success('密码已重置')
}

async function openRoles(row) {
  rolesUser.value = row
  if (!allRoles.value.length) allRoles.value = await listRole() || []
  roleIds.value = await getUserRoles(row.id) || []
  rolesVisible.value = true
}

async function saveRoles() {
  savingRoles.value = true
  try {
    await assignUserRoles(rolesUser.value.id, roleIds.value)
    ElMessage.success('角色已指派（全量覆盖）')
    rolesVisible.value = false
  } finally {
    savingRoles.value = false
  }
}

async function doRemove(row) {
  await ElMessageBox.confirm(`确定删除用户 ${row.username}？`, '删除确认', { type: 'error' })
  await removeUser(String(row.id))
  ElMessage.success('已删除')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.filter-card { padding: 14px 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { padding: 6px 12px 12px; }
.pager { margin-top: 10px; justify-content: flex-end; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
