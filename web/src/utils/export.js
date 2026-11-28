/**
 * 数据导出（Excel）：与其他导出同源做法——前端 SheetJS（xlsx，已在 package.json）在浏览器侧
 * 生成 .xlsx，后端只提供分页 list 接口，不新增后端依赖、不占服务器内存。
 *
 * 关键约定：
 *  1) **遵循当前筛选条件**：列定义与 fetchPage 由调用页传入，fetchPage(page, size) 复用列表查询；
 *  2) **分页拉取**：按 pageSize=100 逐页拉取（大表不会一次性进内存，也不会把服务端打爆）；
 *  3) **文件名含业务名与日期**：`化学品台账_20260917.xlsx`；
 *  4) 上限保护：默认最多 5000 行，超出时提示并在导出结果中标注"截断"。
 */
import * as XLSX from 'xlsx'
import { ElMessage } from 'element-plus'

export const EXPORT_PAGE_SIZE = 100
export const EXPORT_MAX_ROWS = 5000

/** 业务名 + yyyyMMdd 文件名（Windows 安全字符）。 */
export function exportFileName(business) {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  return `${business}_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}.xlsx`
}

/**
 * 分页拉全量数据。
 * @param {(page:number,size:number)=>Promise<{records:Array,total:number}>} fetchPage
 */
export async function fetchAllPages(fetchPage, { pageSize = EXPORT_PAGE_SIZE, maxRows = EXPORT_MAX_ROWS } = {}) {
  const rows = []
  let total = Infinity
  let page = 1
  while (rows.length < Math.min(total, maxRows)) {
    const data = await fetchPage(page, pageSize)
    const records = data?.records || []
    total = Number(data?.total ?? records.length)
    if (!records.length) break
    rows.push(...records)
    if (records.length < pageSize) break
    page += 1
  }
  const truncated = total > rows.length
  return { rows: rows.slice(0, maxRows), total, truncated }
}

/**
 * 导出 Excel。
 * @param {object} opts
 * @param {string} opts.business 业务名（文件名/工作表名）
 * @param {Array<{prop:string,label:string,format?:Function}>} opts.columns 列定义（遵循列表展示口径）
 * @param {Function} opts.fetchPage 分页取数（复用当前筛选条件）
 * @param {Function} [opts.onCount] 导出成功回调（提示行数）
 */
export async function exportExcel({ business, columns, fetchPage }) {
  const { rows, total, truncated } = await fetchAllPages(fetchPage)
  if (!rows.length) {
    ElMessage.warning('当前筛选条件下没有可导出的数据')
    return { rows: 0, total }
  }
  const header = columns.map(c => c.label)
  const body = rows.map(row => columns.map(c => {
    const raw = typeof c.prop === 'function' ? c.prop(row) : getByPath(row, c.prop)
    return c.format ? c.format(raw, row) : (raw === null || raw === undefined ? '' : raw)
  }))
  const sheet = XLSX.utils.aoa_to_sheet([header, ...body])
  sheet['!cols'] = header.map((h, i) => ({
    wch: Math.min(40, Math.max(10, h.length * 2,
      ...body.slice(0, 200).map(r => String(r[i] ?? '').length + 2)))
  }))
  const book = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(book, sheet, business.slice(0, 28) || 'Sheet1')
  XLSX.writeFile(book, exportFileName(business))
  const suffix = truncated ? `（已达导出上限 ${EXPORT_MAX_ROWS} 行，可先缩小筛选范围）` : ''
  ElMessage.success(`已导出 ${rows.length} 行${suffix}｜总数 ${total}`)
  return { rows: rows.length, total, truncated }
}

function getByPath(obj, path) {
  return String(path).split('.').reduce((acc, key) => (acc == null ? acc : acc[key]), obj)
}
