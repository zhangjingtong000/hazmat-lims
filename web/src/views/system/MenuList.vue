<template>
  <div class="page-wrap">
    <div class="page-header">
      <div>
        <div class="eyebrow">PERMISSION TREE · 按钮级权限</div>
        <div class="page-title" style="margin-top: 4px">菜单权限</div>
      </div>
    </div>

    <div class="regimen-bar">
      <el-icon><InfoFilled /></el-icon>
      <span>菜单树为三级按钮权限树，覆盖业务动作与管理动作的按钮级授权；菜单与权限项由初始化配置统一维护，此处只读查看。</span>
    </div>

    <div class="page-card table-card">
      <el-table :data="tree" v-loading="loading" row-key="id" default-expand-all>
        <el-table-column prop="menuName" label="菜单名称" min-width="200" />
        <el-table-column prop="path" label="路径" min-width="150">
          <template #default="{ row }"><span class="mono">{{ row.path || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="perms" label="权限标识" min-width="220">
          <template #default="{ row }">
            <el-tag v-if="row.perms" size="small" effect="plain" class="perm-tag">{{ row.perms }}</el-tag>
            <span v-else class="cell-sub">—</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.menuType === 'B' ? 'warning' : row.menuType === 'M' ? 'info' : 'success'">
              {{ { M: '目录', C: '菜单', B: '按钮' }[row.menuType] || row.menuType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="下级" width="80" align="center">
          <template #default="{ row }">{{ (row.children || []).length || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { InfoFilled } from '@element-plus/icons-vue'
import { listMenuTree } from '../../api/system'

const tree = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    tree.value = (await listMenuTree()) || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.table-card { padding: 6px 12px 12px; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
.perm-tag { font-family: var(--f-mono); }
</style>
