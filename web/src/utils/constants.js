/**
 * 全局枚举字典（口径对齐 §2 表结构与 §4 错误码表）
 */

/** 化学品分类：0普通/1危化品/2易制毒/3易制爆/4剧毒 —— 缩写与分类色对齐 GHS 规范 */
export const CATEGORIES = {
  0: { key: 'PT', name: '普通化学品', color: '#16A34A', light: '#DCFCE7' },
  1: { key: 'WH', name: '危险化学品', color: '#CA8A04', light: '#FEF9C3' },
  2: { key: 'YZ', name: '易制毒', color: '#7C3AED', light: '#EDE9FE' },
  3: { key: 'YB', name: '易制爆', color: '#EA580C', light: '#FFEDD5' },
  4: { key: 'JD', name: '剧毒品', color: '#B91C1C', light: '#FEE2E2' }
}

export const categoryOf = c => CATEGORIES[c] || CATEGORIES[0]

/** 台账状态：0在管/1封存/2清空 */
export const INFO_STATUS = { 0: '在管', 1: '封存', 2: '清空' }

/** 批次状态：0在库/1耗尽/2废弃/3过期封存 */
export const BATCH_STATUS = {
  0: { label: '在库', type: 'success' },
  1: { label: '耗尽', type: 'info' },
  2: { label: '废弃', type: 'danger' },
  3: { label: '过期封存', type: 'warning' }
}

/** 申购单状态：0草稿/1审批中/2已批/3驳回/4已入库关闭/5已作废 */
export const PURCHASE_STATUS = {
  0: { label: '草稿', type: 'info' },
  1: { label: '审批中', type: 'warning' },
  2: { label: '已批', type: 'success' },
  3: { label: '驳回', type: 'danger' },
  4: { label: '已入库关闭', type: '' },
  5: { label: '已作废', type: 'danger' }
}

/** 入库单状态：0待验收/1已入库/2已拒收 */
export const STOCKIN_STATUS = {
  0: { label: '待验收', type: 'warning' },
  1: { label: '已入库', type: 'success' },
  2: { label: '已拒收', type: 'danger' }
}

/** 领用单状态：0待导师/1待复核/2已出库/3驳回/4归还完毕(含核销)关闭 */
export const REQ_STATUS = {
  0: { label: '待导师签', type: 'warning' },
  1: { label: '待复核', type: 'warning' },
  2: { label: '已出库', type: 'success' },
  3: { label: '驳回', type: 'danger' },
  4: { label: '已关闭', type: 'info' }
}

/** 废弃处置单：type 1废液/2空瓶/3过期整批 */
export const WASTE_TYPE = { 1: '废液', 2: '空瓶', 3: '过期整批' }
export const WASTE_STATUS = {
  0: { label: '待审批', type: 'warning' },
  1: { label: '已处置', type: 'success' },
  2: { label: '已驳回', type: 'danger' }
}

/** 盘点单 result：1平/2不平 */
export const CHECK_RESULT = { 0: { label: '盘点中', type: 'warning' }, 1: { label: '平', type: 'success' }, 2: { label: '不平', type: 'danger' } }

/** 柜位类型：1酸/2碱/3有机/4氧化剂/5剧毒专柜/6通用 */
export const CABINET_TYPE = { 1: '酸品柜', 2: '碱品柜', 3: '有机柜', 4: '氧化剂柜', 5: '剧毒专柜', 6: '通用柜' }

/** 归还单状态：0待确认/1已回库/2已拒绝 */
export const RETURN_STATUS = { 0: '待确认', 1: '已回库', 2: '已拒绝' }

/** 申购审批链节点（与 ApprovalChainService 对齐） */
export const AUDIT_NODES = [
  { seq: 1, nodeKey: 'advisor', nodeName: '导师审批', perm: 'hzm:purchase:audit-advisor' },
  { seq: 2, nodeKey: 'safety', nodeName: '安全员审批', perm: 'hzm:purchase:audit-safety' },
  { seq: 3, nodeKey: 'college', nodeName: '学院审批', perm: 'hzm:purchase:audit-college' },
  { seq: 4, nodeKey: 'campus', nodeName: '校级审批', perm: 'hzm:purchase:audit-campus' }
]

/** GHS 象形图代码选项（ghz_pictogram 常用集） */
export const PICTOGRAMS = [
  { value: 'FLAM', label: 'FLAM 易燃' },
  { value: 'OXI', label: 'OXI 氧化' },
  { value: 'ACID', label: 'ACID 腐蚀' },
  { value: 'TOXIC', label: 'TOXIC 剧毒' },
  { value: 'HARM', label: 'HARM 有害' },
  { value: 'IRRIT', label: 'IRRIT 刺激' },
  { value: 'EXPLOS', label: 'EXPLOS 爆炸' },
  { value: 'GENERAL', label: 'GENERAL 一般' }
]

/** 相容组选项（§7 种子：ORG/FLAM/AC/AK/OX/RD/GEN + YZ/YB 场景） */
export const COMPAT_GROUPS = ['ORG', 'FLAM', 'AC', 'AK', 'OX', 'RD', 'GEN']

/** 分页辅助 */
export const pager = (page, size) => ({ page, size })
