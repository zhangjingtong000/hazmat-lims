<template>
  <div class="print-wrap" v-loading="loading">
    <div class="print-ops no-print">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="doPrint">打印（两联骑缝）</el-button>
      <span class="cell-sub">本页由申购单据与审批记录生成，供线下归档。</span>
    </div>

    <template v-if="req">
      <!-- 第一联：审批存根 -->
      <div class="print-sheet" style="margin-bottom: 22px">
        <div class="sheet-head">
          <div class="sheet-brand">
            <GhzTag :category="req.category" :size="34" />
            <b>高校实验室危险化学品申购单</b>
          </div>
          <div class="copy-tag copy-a">第一联 · 审批存根<span class="seam-no">骑缝 No.{{ req.code }}-1</span></div>
        </div>
        <table class="info-table">
          <tr>
            <th>申购单号</th><td class="mono">{{ req.code }}</td>
            <th>申请日期</th><td>{{ fmt(req.createTime) }}</td>
            <th>分类</th><td>{{ categoryOf(req.category).key }} · {{ categoryOf(req.category).name }}</td>
          </tr>
          <tr>
            <th>化学品名称</th><td>{{ req.chemicalName }}</td>
            <th>CAS 号</th><td class="mono">{{ req.casNo || '—' }}</td>
            <th>规格/单位</th><td>{{ req.spec || '—' }} / {{ req.unit || '—' }}</td>
          </tr>
          <tr>
            <th>申购数量</th><td>{{ req.qty }} {{ req.unit }}</td>
            <th>已入库</th><td>{{ req.inQty || 0 }}</td>
            <th>单据状态</th><td>{{ PURCHASE_STATUS[req.status]?.label }}</td>
          </tr>
          <tr>
            <th>用途</th><td colspan="3">{{ req.purpose || '—' }}</td>
            <th>实验项目</th><td>{{ req.experiment || '—' }}</td>
          </tr>
        </table>

        <!-- 五级签批横表：申请人 + 四级审批共 5 行 -->
        <div class="sign-title">签批栏（申请人 + 四级审批，共 5 行）</div>
        <table class="sign-table">
          <tr>
            <th style="width: 130px">环节</th><th>签批人</th><th>意见</th><th style="width: 90px">结果</th><th style="width: 120px">日期</th>
          </tr>
          <tr v-for="r in signRows" :key="r.seq">
            <td>{{ r.name }}</td>
            <td>{{ r.person || '' }}</td>
            <td class="comment-cell">{{ r.comment || '' }}</td>
            <td>{{ r.result }}</td>
            <td>{{ r.time || '' }}</td>
          </tr>
        </table>
        <div class="sheet-foot">
          <span>本联随单据归档留存（逐级签批，一签一录）</span>
          <span>制单时间：{{ fmt(req.createTime) }}</span>
        </div>
      </div>

      <!-- 第二联：使用凭证 -->
      <div class="print-sheet">
        <div class="sheet-head">
          <div class="sheet-brand">
            <GhzTag :category="req.category" :size="34" />
            <b>高校实验室危险化学品申购单（使用凭证）</b>
          </div>
          <div class="copy-tag copy-b">第二联 · 使用凭证<span class="seam-no">骑缝 No.{{ req.code }}-2</span></div>
        </div>
        <table class="info-table">
          <tr>
            <th>申购单号</th><td class="mono">{{ req.code }}</td>
            <th>化学品名称</th><td>{{ req.chemicalName }}</td>
            <th>数量</th><td>{{ req.qty }} {{ req.unit }}</td>
          </tr>
          <tr>
            <th>领用关联</th><td colspan="5">入库后凭本联办理领用（先批后领、存量双轨）；剧毒/易制毒/易制爆按五双制度执行。</td>
          </tr>
        </table>
        <div class="sheet-foot">
          <span>使用人签字：＿＿＿＿＿＿</span>
          <span>复核（双签）：＿＿＿＿＿＿</span>
          <span>日期：＿＿＿＿＿＿</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import GhzTag from '../../components/GhzTag.vue'
import { purchaseDetail } from '../../api/chem'
import { categoryOf, PURCHASE_STATUS, AUDIT_NODES } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const req = ref(null)
const records = ref([])

const fmt = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''

/** 五级签批横表：申请人 + 四级审批共 5 行（文案统一"四级审批"，G-9） */
const signRows = computed(() => {
  const rows = [{ seq: 0, name: '申请人', person: req.value?.applicantName || req.value?.applicantId || '', comment: '申购提交', result: '—', time: fmt(req.value?.createTime) }]
  const chain = AUDIT_NODES.slice(0, chainLen(req.value?.category))
  chain.forEach(n => {
    const rec = (records.value || []).find(r => r.seq === n.seq)
    rows.push({
      seq: n.seq, name: `${n.seq}. ${n.nodeName}`,
      person: rec ? (rec.approverId || '') : '',
      comment: rec?.comment || '',
      result: rec ? (rec.result === 1 ? '通过' : '驳回') : '',
      time: rec ? fmt(rec.createTime) : ''
    })
  })
  return rows
})

const chainLen = c => ({ 0: 1, 1: 2, 2: 3, 3: 3, 4: 4 }[Number(c)] ?? 1)

function doPrint() { window.print() }

onMounted(async () => {
  try {
    const vo = await purchaseDetail(route.params.id)
    req.value = vo.req
    records.value = vo.records || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.print-wrap { padding: 20px 24px; background: var(--c-page); min-height: 100vh; }
.print-ops { display: flex; gap: 10px; align-items: center; max-width: 900px; margin: 0 auto 14px; }
.sheet-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.sheet-brand { display: flex; gap: 10px; align-items: center; font-size: 1.05rem; }
.copy-tag { border: 1px solid #444; padding: 4px 10px; font-size: 0.78rem; text-align: center; }
.copy-a { background: #FEF3C7; }
.copy-b { background: #FEE2E2; }
.seam-no { display: block; font-family: var(--f-mono); font-size: 0.68rem; color: #555; }
.info-table th { width: 96px; background: var(--c-line-faint); font-weight: 600; }
.info-table td { text-align: left; }
.sign-title { font-weight: 700; margin: 14px 0 6px; font-size: 0.88rem; }
.sign-table th { background: var(--c-line-faint); }
.sign-table td { height: 34px; }
.comment-cell { text-align: left; }
.sheet-foot { display: flex; justify-content: space-between; margin-top: 12px; font-size: 0.74rem; color: #555; }
.mono { font-family: var(--f-mono); }
.cell-sub { font-size: 0.74rem; color: var(--c-graphite-soft); }
</style>
