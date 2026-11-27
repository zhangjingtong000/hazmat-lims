<template>
  <div class="print-wrap" v-loading="loading">
    <div class="print-ops no-print">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="doPrint">打印（联次竖排左置）</el-button>
      <span class="cell-sub">本页由领用单与出库明细生成，供领用签收留档。</span>
    </div>

    <template v-if="req">
      <!-- 联次竖排左置：存根联在上、领用联在下，联次标记竖排在左侧 -->
      <div class="print-sheet req-sheet">
        <div class="sheet-head">
          <div class="sheet-brand">
            <GhzTag v-if="chem" :category="chem.category" :size="34" />
            <b>实验室危险化学品领用单</b>
          </div>
        </div>

        <div class="copies-row">
          <div class="seam-col">
            <div class="seam-item">存<br>根<br>联<br><span class="seam-no">No.{{ req.code }}-1</span></div>
            <div class="seam-item use-copy">领<br>用<br>联<br><span class="seam-no">No.{{ req.code }}-2</span></div>
          </div>

          <div class="copies-body">
            <table class="info-table">
              <tr>
                <th>领用单号</th><td class="mono">{{ req.code }}</td>
                <th>申请日期</th><td>{{ fmt(req.createTime) }}</td>
                <th>状态</th><td>{{ REQ_STATUS[req.status]?.label }}</td>
              </tr>
              <tr>
                <th>化学品</th><td>{{ req.chemicalName }}</td>
                <th>领用数量</th><td>{{ req.qty }}</td>
                <th>已归还</th><td>{{ req.returnQty || 0 }}</td>
              </tr>
              <tr>
                <th>领用人</th><td>{{ req.applicantName || req.applicantId }}</td>
                <th>归还时限</th><td>{{ req.expectReturn || '—' }}</td>
                <th>出库时间</th><td>{{ fmt(req.outTime) || '—' }}</td>
              </tr>
              <tr>
                <th>用途</th><td colspan="5">{{ req.purpose || '—' }}<span v-if="req.experiment">（实验：{{ req.experiment }}）</span></td>
              </tr>
            </table>

            <!-- FEFO 分配明细（出库联次核心） -->
            <div class="sign-title">出库分配明细（复核通过即出库，先到期先出）</div>
            <table class="sign-table">
              <tr><th style="width: 50px">序</th><th>批次</th><th style="width: 90px">效期</th><th style="width: 110px">柜位</th><th style="width: 90px">分配量</th></tr>
              <tr v-for="a in allocs" :key="a.id">
                <td>{{ a.seqNo }}</td>
                <td class="mono">{{ batchLabel(a.batchId) }}</td>
                <td>{{ batchInfo(a.batchId)?.expiryDate || '—' }}</td>
                <td>{{ batchInfo(a.batchId)?.cabinetName || '—' }}</td>
                <td>{{ a.qty }}</td>
              </tr>
              <tr v-if="!allocs.length"><td colspan="5" class="cell-sub">尚未出库（无分配明细）</td></tr>
            </table>

            <div class="sheet-foot">
              <span>领用人签字：＿＿＿＿＿＿</span>
              <span>复核（双签）：＿＿＿＿＿＿</span>
              <span>导师签（如需）：＿＿＿＿＿＿</span>
            </div>
            <div class="return-line">
              余量归还：凭本联办理 GH 归还单（回原批次，超量 4602 拦截）；用尽核销须填说明（⑦-2）。
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import GhzTag from '../../components/GhzTag.vue'
import { reqDetail, listBatch, listChemInfo } from '../../api/chem'
import { REQ_STATUS } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const req = ref(null)
const allocs = ref([])
const chemBatches = ref([])
const chem = ref(null)

const fmt = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''
const batchInfo = id => chemBatches.value.find(b => b.id === id)
const batchLabel = id => batchInfo(id)?.lotNo || ('批次 #' + id)

function doPrint() { window.print() }

onMounted(async () => {
  try {
    const vo = await reqDetail(route.params.id)
    req.value = vo.req
    allocs.value = vo.batches || []
    if (vo.req?.chemicalId) {
      const [b, c] = await Promise.all([
        listBatch({ chemicalId: vo.req.chemicalId, page: 1, size: 100 }).catch(() => ({ records: [] })),
        listChemInfo({ page: 1, size: 200 }).catch(() => ({ records: [] }))
      ])
      chemBatches.value = b.records || []
      chem.value = (c.records || []).find(x => x.id === vo.req.chemicalId)
    }
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.print-wrap { padding: 20px 24px; background: var(--c-page); min-height: 100vh; }
.print-ops { display: flex; gap: 10px; align-items: center; max-width: 900px; margin: 0 auto 14px; }
.sheet-head { display: flex; justify-content: space-between; margin-bottom: 12px; }
.sheet-brand { display: flex; gap: 10px; align-items: center; font-size: 1.05rem; }
/* 联次竖排左置（差异化版式：与申购单"横表+骑缝联次"错开） */
.copies-row { display: flex; gap: 12px; }
.seam-col { display: flex; flex-direction: column; gap: 10px; flex: none; width: 46px; }
.seam-item {
  flex: 1;
  border: 1px dashed #444;
  border-radius: 4px;
  padding: 8px 4px;
  font-size: 0.8rem;
  font-weight: 700;
  text-align: center;
  line-height: 1.4;
  background: #FEF3C7;
}
.seam-item.use-copy { background: #DCFCE7; }
.seam-no { display: block; font-family: var(--f-mono); font-size: 0.56rem; font-weight: 400; color: #555; }
.copies-body { flex: 1; min-width: 0; }
.info-table th { width: 84px; background: var(--c-line-faint); font-weight: 600; }
.info-table td { text-align: left; }
.sign-title { font-weight: 700; margin: 12px 0 6px; font-size: 0.86rem; }
.sign-table th { background: var(--c-line-faint); }
.sign-table td { height: 30px; }
.sheet-foot { display: flex; justify-content: space-between; gap: 8px; margin-top: 12px; font-size: 0.76rem; color: #333; flex-wrap: wrap; }
.return-line { margin-top: 8px; border-top: 1px dashed #999; padding-top: 6px; font-size: 0.74rem; color: #555; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
