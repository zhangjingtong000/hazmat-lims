<template>
  <el-dialog v-model="visible" title="智能查询" width="720px" top="8vh" @closed="onClosed">
    <div class="eyebrow">INTELLIGENT QUERY · 自然语言查台账 / 法规检索</div>
    <div class="ask-row">
      <el-input
        v-model="question" placeholder="例如：氰化钾还有多少库存？/ 剧毒品怎么存放？"
        clearable :disabled="loading" data-testid="ai-input"
        @keyup.enter="doAsk">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
      <el-button type="primary" :loading="loading" data-testid="ai-submit" @click="doAsk">查询</el-button>
    </div>
    <div class="ai-note">三级降级：缓存回放 → 在线解析 → 关键词检索，任一环节都能出正确结果；AI 只翻译意图，数据一律来自数据库查询。</div>

    <div v-if="loading" class="ai-loading" v-loading="loading" element-loading-text="解析中…" />

    <template v-else-if="result">
      <div class="ai-summary">
        <el-tag size="small" effect="plain" :type="sourceTag.type" data-testid="ai-source">{{ sourceTag.label }}</el-tag>
        <span class="summary-text" data-testid="ai-summary">{{ result.summary }}</span>
      </div>

      <!-- 数据意图：复用台账列表列定义 -->
      <template v-if="result.intent === 'list'">
        <el-table :data="result.rows || []" stripe size="small" empty-text="无匹配记录">
          <el-table-column prop="code" label="编码" width="158">
            <template #default="{ row }"><span class="doc-code">{{ row.code }}</span></template>
          </el-table-column>
          <el-table-column prop="name" label="名称" min-width="120" />
          <el-table-column prop="casNo" label="CAS 号" width="110">
            <template #default="{ row }"><span class="mono">{{ row.casNo }}</span></template>
          </el-table-column>
          <el-table-column label="分类" width="90">
            <template #default="{ row }">
              <span class="cat-name" :class="'cat-' + categoryOf(row.category).key.toLowerCase()">{{ categoryOf(row.category).name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="stockQty" label="现存" width="80" align="right">
            <template #default="{ row }"><span class="metric-num">{{ row.stockQty }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <el-tag :type="row.status === 0 ? 'success' : row.status === 1 ? 'warning' : 'info'" size="small">
                {{ INFO_STATUS[row.status] }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <!-- 统计意图：分类分布聚合表 -->
      <template v-else-if="result.intent === 'stats' && (result.rows || []).length">
        <el-table :data="result.rows" stripe size="small">
          <el-table-column label="分类" width="110">
            <template #default="{ row }">
              <span class="cat-name" :class="'cat-' + categoryOf(Number(row.category)).key.toLowerCase()">{{ categoryOf(Number(row.category)).name }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="chem_count" label="台账数" width="90" align="right" />
          <el-table-column prop="total_stock" label="Σ现存" width="90" align="right">
            <template #default="{ row }"><span class="metric-num">{{ row.total_stock }}</span></template>
          </el-table-column>
          <el-table-column prop="chem_names" label="品名" min-width="240" show-overflow-tooltip />
        </el-table>
      </template>

      <!-- 法规意图：条文卡片 -->
      <template v-else-if="result.intent === 'regs'">
        <div v-if="(result.regs || []).length" class="reg-list">
          <div v-for="reg in result.regs" :key="reg.law + reg.no" class="reg-card">
            <div class="reg-head">
              <span class="reg-law">{{ reg.law === '条例' ? '《危险化学品安全管理条例》' : 'GB 15603-2022《危险化学品仓库储存通则》' }}</span>
              <span class="reg-no doc-code">{{ reg.no }}</span>
            </div>
            <div class="reg-text">{{ reg.text }}</div>
          </div>
        </div>
        <el-empty v-else description="语料中未检索到相关条文" :image-size="60" />
      </template>

      <el-empty v-if="!hasContent" description="无匹配结果" :image-size="60" />
    </template>

    <el-empty v-else-if="!loading && asked" description="本次未获得结果，请换个说法再试" :image-size="60" />
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { aiAsk } from '../api/chem'
import { categoryOf, INFO_STATUS } from '../utils/constants'

const props = defineProps({ modelValue: Boolean })
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

const question = ref('')
const loading = ref(false)
const asked = ref(false)
const result = ref(null)

const SOURCE_TAGS = {
  ai: { label: '智能解析', type: 'warning' },
  cache: { label: '缓存回放', type: 'success' },
  keyword: { label: '关键词匹配', type: 'info' }
}
const sourceTag = computed(() => SOURCE_TAGS[result.value?.source] || SOURCE_TAGS.keyword)
const hasContent = computed(() => (result.value?.rows || []).length || (result.value?.regs || []).length)

async function doAsk() {
  const q = question.value.trim()
  if (!q) return
  loading.value = true
  asked.value = true
  result.value = null
  try {
    result.value = await aiAsk(q)
  } catch (e) {
    result.value = null
  } finally {
    loading.value = false
  }
}

function onClosed() {
  question.value = ''
  result.value = null
  asked.value = false
}
</script>

<style scoped>
.ask-row { display: flex; gap: 8px; margin-top: 10px; }
.ask-row .el-input { flex: 1; }
.ai-note {
  font-size: 0.74rem;
  color: var(--c-graphite-faint);
  line-height: 1.6;
  margin: 8px 0 2px;
  border-left: 3px solid var(--c-safe-line);
  padding-left: 8px;
}
.ai-loading { min-height: 120px; }
.ai-summary {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: var(--c-safe-bg);
  border: 1px solid var(--c-safe-line);
  border-radius: var(--r-sm);
  margin: 10px 0;
}
.summary-text { font-size: 0.86rem; line-height: 1.65; color: var(--c-graphite); }
.mono { font-family: var(--f-mono); }
.cat-name { font-weight: 600; font-size: 0.8rem; }
.reg-list { display: flex; flex-direction: column; gap: 8px; }
.reg-card {
  border: 1px solid var(--c-line);
  border-radius: var(--r-sm);
  padding: 10px 12px;
  background: #fff;
}
.reg-head { display: flex; justify-content: space-between; align-items: baseline; gap: 10px; margin-bottom: 4px; }
.reg-law { font-weight: 700; font-size: 0.8rem; color: var(--c-graphite-deep); }
.reg-no { flex: none; }
.reg-text { font-size: 0.82rem; line-height: 1.7; color: var(--c-graphite); white-space: pre-wrap; }
</style>
