<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">RBAC ROLES · 审批链节点权限</div>
        <div class="page-title" style="margin-top: 4px">角色管理</div>
      </div>
      <el-button v-if="hasPerm('system:role:add')" type="primary" @click="openForm()">+ 新增角色</el-button>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>角色承载审批链节点权限（导师/安全员/学院/校级）与按钮级权限；成员关系在用户管理中逐人指派。</span>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="right" />
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="角色键" width="130">
          <template #default="{ row }"><span class="mono">{{ row.roleKey }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">{{ row.status === '0' ? '正常' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('system:role:edit')" size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button v-if="hasPerm('system:role:remove')" size="small" text type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑角色' : '新增角色'" width="460px">
      <el-form ref="formRef" :model="form" label-width="90px">
        <el-form-item label="角色名称" prop="roleName" :rules="R.requiredLen(50, '角色名称')"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="角色键" prop="roleKey" :rules="R.requiredLen(30, '角色键').concat(R.format(/^[a-zA-Z][a-zA-Z0-9_-]*$/, '角色键须以字母开头，仅含字母/数字/下划线/连字符'))">
          <el-input v-model="form.roleKey" placeholder="如 advisor / safety / college" />
        </el-form-item>
        <el-form-item label="状态" prop="status" :rules="R.requiredPick('状态')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio><el-radio value="1">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark" :rules="R.maxLen(200, '备注')"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import { listRole, addRole, editRole, removeRole } from '../../api/system'
import { hasPerm } from '../../store/auth'
import R from '../../utils/validation'

const rows = ref([])
const loading = ref(false)
const formVisible = ref(false)
const saving = ref(false)
const formRef = ref(null)
const form = reactive({})

async function load() {
  loading.value = true
  try {
    rows.value = (await listRole()) || []
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) Object.assign(form, row)
  else Object.assign(form, { roleName: '', roleKey: '', status: '0', remark: '' })
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('角色名称与角色键必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await editRole(form)
      ElMessage.success('角色已更新')
    } else {
      await addRole(form)
      ElMessage.success('角色已新增')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doRemove(row) {
  await ElMessageBox.confirm(`确定删除角色 ${row.roleName}？`, '删除确认', { type: 'error' })
  await removeRole(String(row.id))
  ElMessage.success('已删除')
  load()
}

onMounted(() => load())
</script>

<style scoped>
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.table-card { padding: 6px 12px 12px; }
</style>
