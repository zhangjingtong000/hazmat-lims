<template>
  <div class="print-wrap" v-loading="loading">
    <div class="print-ops no-print">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="doPrint">打印（一页 6 枚标签）</el-button>
      <span class="cell-sub">标签内容取自化学品档案与在库批次，随批次打印张贴于柜面。</span>
    </div>

    <template v-if="info">
      <div class="print-sheet label-sheet">
        <div class="label-grid-head">
          <span>GHS 警示标签 · {{ info.name }}（CAS {{ info.casNo || '—' }}）</span>
          <span class="mono">{{ info.code }}</span>
        </div>
        <div class="label-grid">
          <div v-for="n in 6" :key="n" class="ghs-label">
            <div class="label-top">
              <GhzTag :category="info.category" :size="58" />
              <div class="label-title">
                <b class="label-name">{{ info.name }}</b>
                <div class="mono label-cas">CAS {{ info.casNo || '—' }}</div>
                <div class="label-word">{{ signalWord }}</div>
              </div>
              <div class="label-cat" :style="{ borderColor: categoryOf(info.category).color, color: categoryOf(info.category).color }">
                {{ categoryOf(info.category).key }}
              </div>
            </div>
            <div class="label-line"><span class="lbl">象形图</span><b class="mono">{{ info.ghzPictogram || '—' }}</b><span class="lbl">规格</span>{{ info.spec || '—' }}</div>
            <div class="label-line"><span class="lbl">编码</span><span class="mono">{{ info.code }}</span><span class="lbl">柜位</span>{{ cabinetName }}</div>
            <div v-if="info.firstAid" class="label-line label-aid">急救：{{ info.firstAid }}</div>
          </div>
        </div>
        <div class="label-grid-foot">
          <span>标签随批次张贴于柜面；剧毒/易制毒/易制爆按五双制度补挂双人领用记录。</span>
          <span>打印时间：{{ now }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import GhzTag from '../../components/GhzTag.vue'
import { chemInfoDetail } from '../../api/chem'
import { categoryOf } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const info = ref(null)
const batches = ref([])

const now = new Date().toLocaleString('zh-CN', { hour12: false })

/** GHS 警示词：剧毒/易制毒/易制爆→"危险"，危化品→"警告"，普通→"注意" */
const signalWord = computed(() => {
  const c = Number(info.value?.category)
  return c >= 2 ? '危险' : c === 1 ? '警告' : '注意'
})

/** 柜位以在库批次实际柜位为准（禁配以批次维度校验，同口径） */
const cabinetName = computed(() => (batches.value || []).find(b => b.cabinetName)?.cabinetName || '—')

function doPrint() { window.print() }

onMounted(async () => {
  try {
    const vo = await chemInfoDetail(route.params.id)
    info.value = vo.info
    batches.value = vo.batches || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.print-wrap { padding: 20px 24px; background: var(--c-page); min-height: 100vh; }
.print-ops { display: flex; gap: 10px; align-items: center; max-width: 900px; margin: 0 auto 14px; }
.label-sheet { padding: 24px 28px; }
.label-grid-head { display: flex; justify-content: space-between; align-items: center; font-weight: 700; font-size: 0.9rem; margin-bottom: 10px; }
.label-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.ghs-label { border: 1.5px solid #222; border-radius: 4px; padding: 8px 10px; break-inside: avoid; }
.label-top { display: flex; align-items: center; gap: 8px; border-bottom: 1px dashed #999; padding-bottom: 6px; margin-bottom: 6px; }
.label-title { flex: 1; min-width: 0; }
.label-name { font-size: 0.95rem; display: block; }
.label-cas { font-size: 0.72rem; color: #333; }
.label-word { display: inline-block; margin-top: 2px; background: #111; color: #fff; font-size: 0.7rem; font-weight: 700; padding: 1px 8px; border-radius: 2px; }
.label-cat { flex: none; font-weight: 800; font-family: var(--f-mono); font-size: 0.78rem; border: 1.5px solid; border-radius: 3px; padding: 2px 6px; }
.label-line { font-size: 0.74rem; line-height: 1.7; color: #111; }
.label-line .lbl { display: inline-block; width: 44px; color: #555; }
.label-aid { color: #B91C1C; }
.label-grid-foot { display: flex; justify-content: space-between; margin-top: 10px; font-size: 0.72rem; color: #555; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
