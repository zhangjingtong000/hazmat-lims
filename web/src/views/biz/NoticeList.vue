<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">NOTICES · 信息公告</div>
        <div class="page-title" style="margin-top: 4px">信息公告</div>
      </div>
      <el-button v-if="hasPerm('biz:notice:add')" type="primary" @click="openForm()">+ 发布公告</el-button>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="公告标题" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-button type="primary" @click="load(1)">查询</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="right" />
        <el-table-column prop="title" label="标题" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="viewRow = row">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === '0' ? 'success' : 'info'" size="small">{{ row.status === '0' ? '正常' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="发布人" width="100" />
        <el-table-column prop="createTime" label="发布时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="hasPerm('biz:notice:edit')" size="small" text type="primary" @click="openForm(row)">编辑</el-button>
            <el-button v-if="hasPerm('biz:notice:remove')" size="small" text type="danger" @click="doRemove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑公告' : '发布公告'" width="560px">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="标题" prop="title" :rules="R.requiredLen(120, '标题')"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="内容" prop="content" :rules="R.requiredLen(2000, '内容')"><el-input v-model="form.content" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="状态" prop="status" :rules="R.requiredPick('状态')">
          <el-radio-group v-model="form.status">
            <el-radio value="0">正常</el-radio><el-radio value="1">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" :title="viewRow?.title || '公告'" width="560px">
      <div class="notice-body">{{ viewRow?.content }}</div>
      <div class="cell-sub" style="margin-top: 10px">{{ viewRow?.createBy }} · {{ viewRow?.createTime }}</div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listNotice, addNotice, editNotice, removeNotice } from '../../api/system'
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
const viewRow = ref(null)
const viewVisible = computed({
  get: () => !!viewRow.value,
  set: v => { if (!v) viewRow.value = null }
})

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listNotice({ keyword: keyword.value || '', page: page.value, size: size.value })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function openForm(row) {
  Object.keys(form).forEach(k => delete form[k])
  if (row) Object.assign(form, row)
  else Object.assign(form, { title: '', content: '', status: '0' })
  formVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    ElMessage.warning('标题与内容必填')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await editNotice(form)
      ElMessage.success('公告已更新')
    } else {
      await addNotice(form)
      ElMessage.success('公告已发布')
    }
    formVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function doRemove(row) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」？`, '删除确认', { type: 'error' })
  await removeNotice(String(row.id))
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
.notice-body { white-space: pre-wrap; color: var(--c-graphite); line-height: 1.8; }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
