<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">AUDIT TRAIL · 事事有痕</div>
        <div class="page-title" style="margin-top: 4px">操作日志</div>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>写操作审计：失败的操作同样留痕；调整/改派/封存类动作的参数完整留存——事事有痕、笔笔可查。</span>
    </div>

    <div class="page-card filter-card">
      <div class="filter-row">
        <el-input v-model="keyword" placeholder="模块 / 动作 / 操作人" clearable style="width: 220px" @keyup.enter="load(1)" />
        <el-select v-model="status" placeholder="结果" clearable style="width: 120px">
          <el-option label="成功" value="成功" /><el-option label="失败" value="失败" />
        </el-select>
        <el-button type="primary" @click="load(1)">查询</el-button>
        <el-button @click="reset">重置</el-button>
      </div>
    </div>

    <div class="page-card table-card">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" align="right" />
        <el-table-column prop="operTime" label="时间" width="170" />
        <el-table-column prop="module" label="模块" width="120" />
        <el-table-column prop="action" label="动作" min-width="260" show-overflow-tooltip />
        <el-table-column prop="operator" label="操作人" width="100" />
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === '成功' ? 'success' : 'danger'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination class="pager" layout="total, prev, pager, next, sizes" hide-on-single-page
        :total="total" v-model:current-page="page" v-model:page-size="size" :page-sizes="[10, 20, 50]" @change="load()" />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { listOperLog } from '../../api/system'

const keyword = ref('')
const status = ref('')
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)

async function load(p) {
  if (p) page.value = p
  loading.value = true
  try {
    const data = await listOperLog({
      keyword: keyword.value || '', status: status.value || '',
      page: page.value, size: size.value
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function reset() { keyword.value = ''; status.value = ''; load(1) }

onMounted(() => load())
</script>

<style scoped>
.filter-card { padding: 14px 16px; }
.filter-row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.table-card { padding: 6px 12px 12px; }
.pager { margin-top: 10px; justify-content: flex-end; }
</style>
