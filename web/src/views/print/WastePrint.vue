<template>
  <div class="print-wrap" v-loading="loading">
    <div class="print-ops no-print">
      <el-button @click="$router.back()">返回</el-button>
      <el-button type="primary" @click="doPrint">打印（两联骑缝）</el-button>
      <span class="cell-sub">本页由废弃物处置单及审批记录生成，供转移交接留档。</span>
    </div>

    <template v-if="wd">
      <!-- 第一联：处置存根 -->
      <div class="print-sheet" style="margin-bottom: 22px">
        <div class="sheet-head">
          <div class="sheet-brand">
            <GhzTag :category="wd.category" :size="34" />
            <b>实验室废弃物处置单（存根联）</b>
          </div>
          <div class="copy-tag copy-a">第一联 · 处置存根<span class="seam-no">骑缝 No.{{ wd.code }}-1</span></div>
        </div>
        <table class="info-table">
          <tr>
            <th>处置单号</th><td class="mono">{{ wd.code }}</td>
            <th>建单时间</th><td>{{ fmt(wd.createTime) }}</td>
            <th>单据状态</th><td>{{ WASTE_STATUS[wd.status]?.label || wd.status }}</td>
          </tr>
          <tr>
            <th>化学品</th><td>{{ wd.chemicalName || '—' }}</td>
            <th>台账编码</th><td class="mono">{{ wd.chemicalCode || '—' }}</td>
            <th>分类</th><td>{{ categoryOf(wd.category).key }} · {{ categoryOf(wd.category).name }}</td>
          </tr>
          <tr>
            <th>CAS 号</th><td class="mono">{{ wd.casNo || '—' }}</td>
            <th>批次号</th><td class="mono">#{{ wd.batchId }}{{ wd.lotNo ? ' / ' + wd.lotNo : '' }}</td>
            <th>规格/单位</th><td>{{ wd.spec || '—' }} / {{ wd.unit || '—' }}</td>
          </tr>
          <tr>
            <th>处置类型</th><td>{{ WASTE_TYPE[wd.type] || wd.type }}</td>
            <th>处置数量</th><td>{{ wd.qty ?? '整批' }}{{ wd.unit ? ' ' + wd.unit : '' }}</td>
            <th>处置方式</th><td>{{ wd.method || '—' }}</td>
          </tr>
          <tr>
            <th>处置去向</th><td>{{ wd.disposalTarget || '—' }}</td>
            <th>转移凭证号</th><td class="mono">{{ wd.voucherNo || '—' }}</td>
            <th>完成时间</th><td>{{ fmt(wd.finishTime) || '—' }}</td>
          </tr>
        </table>

        <!-- 签批栏：经办 + 审批（approval_record biz_type=2，单级同轴） -->
        <div class="sign-title">签批栏（处置经办 + 审批，⑦-8 回避：处置人不得自审）</div>
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
          <span>本联随处置单归档留存（去向可追：disposal_target / voucher_no 完成时必填留痕）</span>
          <span>制单时间：{{ fmt(wd.createTime) }}</span>
        </div>
      </div>

      <!-- 第二联：处置凭证 -->
      <div class="print-sheet">
        <div class="sheet-head">
          <div class="sheet-brand">
            <GhzTag :category="wd.category" :size="34" />
            <b>实验室废弃物处置单（处置凭证）</b>
          </div>
          <div class="copy-tag copy-b">第二联 · 处置凭证<span class="seam-no">骑缝 No.{{ wd.code }}-2</span></div>
        </div>
        <table class="info-table">
          <tr>
            <th>处置单号</th><td class="mono">{{ wd.code }}</td>
            <th>化学品</th><td>{{ wd.chemicalName || '—' }}</td>
            <th>数量</th><td>{{ wd.qty ?? '整批' }}{{ wd.unit ? ' ' + wd.unit : '' }}</td>
          </tr>
          <tr>
            <th>处置去向</th><td>{{ wd.disposalTarget || '—' }}</td>
            <th>转移凭证号</th><td class="mono">{{ wd.voucherNo || '—' }}</td>
            <th>类型</th><td>{{ WASTE_TYPE[wd.type] || wd.type }}</td>
          </tr>
          <tr>
            <th>规程引用</th><td colspan="5">剧毒/易制毒/易制爆处置按"五双"制度双人交割，转移凭证随本联归档（教育部实验室安全规范：去向可追）。</td>
          </tr>
        </table>
        <div class="sheet-foot">
          <span>移交人签字：＿＿＿＿＿＿</span>
          <span>接收方签章：＿＿＿＿＿＿</span>
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
import { wasteDetail } from '../../api/chem'
import { categoryOf, WASTE_TYPE, WASTE_STATUS } from '../../utils/constants'

const route = useRoute()
const loading = ref(true)
const wd = ref(null)
const records = ref([])

const fmt = t => t ? String(t).replace('T', ' ').slice(0, 16) : ''

/** 签批栏：处置经办 + 审批记录（approval_record biz_type=2 单级） */
const signRows = computed(() => {
  const rows = [{
    seq: 0, name: '处置经办',
    person: wd.value?.operatorName || wd.value?.operatorId || '',
    comment: `建单（${WASTE_TYPE[wd.value?.type] || wd.value?.type}）`,
    result: '—', time: fmt(wd.value?.createTime)
  }]
  const rec = (records.value || [])[0]
  rows.push({
    seq: 1, name: rec?.nodeName || '审批',
    person: rec ? (rec.approverId || wd.value?.approverName || '') : (wd.value?.approverName || ''),
    comment: rec?.comment || '',
    result: rec ? (rec.result === 1 ? '通过' : '驳回') : '',
    time: rec ? fmt(rec.createTime) : ''
  })
  return rows
})

function doPrint() { window.print() }

onMounted(async () => {
  try {
    const vo = await wasteDetail(route.params.id)
    wd.value = vo.waste
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
.copy-b { background: #D1FAE5; }
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
