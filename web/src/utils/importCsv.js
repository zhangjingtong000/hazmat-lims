/**
 * 数据导入（CSV，零新依赖）：模板下载 + 逐行校验 + 失败明细回显。
 *
 * 为什么是 CSV 而不是 xlsx 导入：`xlsx@0.18.5` 有高危原型污染告警（GHSA-4r6h-8v6p-xvw6），
 * 触发路径正是 `XLSX.read` 解析外来工作簿；本项目在导出侧只用它的**写**能力，导入侧一律走
 * CSV 文本解析（不引入解析风险，故不额外引第三方解析库）。
 *
 * 纪律：
 *  ① 模板即契约——列顺序/必填/取值范围写在模板表头与示例行里；
 *  ② 逐行校验（前端先拦格式，服务端再兜底：错误码 4001 原样回显到失败明细）；
 *  ③ 失败不中断——继续导入后续行，最后汇总"成功 N 条 / 失败 M 条"并列出失败原因；
 *  ④ 串行提交（低并发、可审计），避免瞬时打爆服务端（写接口另有 300/min 限流）。
 */
import { ElMessage } from 'element-plus'

/** 生成带 BOM 的 CSV 文本（Excel 打开不乱码）。 */
export function buildCsv(headers, rows, { bom = true } = {}) {
  const esc = v => {
    const s = v === null || v === undefined ? '' : String(v)
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const lines = [headers.map(esc).join(','), ...rows.map(r => r.map(esc).join(','))]
  return (bom ? '\ufeff' : '') + lines.join('\r\n') + '\r\n'
}

/** 触发浏览器下载文本文件（文件名含业务名与日期）。 */
export function downloadText(business, text, ext = 'csv') {
  const d = new Date()
  const pad = n => String(n).padStart(2, '0')
  const name = `${business}_模板_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}.${ext}`
  const blob = new Blob([text], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  a.click()
  URL.revokeObjectURL(url)
  return name
}

/**
 * 极简 CSV 解析：支持双引号包裹（含逗号/换行/转义双引号）、\r\n 与 \n，忽略空行与 # 注释行。
 * @returns {string[][]} 行数组（含表头行）
 */
export function parseCsv(text) {
  const src = String(text || '').replace(/^\ufeff/, '')
  const rows = []
  let row = []
  let field = ''
  let inQuotes = false
  for (let i = 0; i < src.length; i++) {
    const ch = src[i]
    if (inQuotes) {
      if (ch === '"') {
        if (src[i + 1] === '"') { field += '"'; i++ } else { inQuotes = false }
      } else {
        field += ch
      }
      continue
    }
    if (ch === '"') { inQuotes = true; continue }
    if (ch === ',') { row.push(field); field = ''; continue }
    if (ch === '\n') { row.push(field); rows.push(row); row = []; field = ''; continue }
    if (ch === '\r') continue
    field += ch
  }
  if (field.length || row.length) { row.push(field); rows.push(row) }
  return rows.filter(r => r.some(c => String(c).trim() !== '') && !String(r[0] || '').trim().startsWith('#'))
}

/** 读文件为文本（FileReader 包装）。 */
export function readFileText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file, 'UTF-8')
  })
}

/**
 * 逐行导入：调用方给出 columns（key/label/required/validate/transform）与 submit(row)。
 * @returns {{success:number, failed:Array<{line:number, values:Object, reason:string}>}}
 */
export async function importRows({ rows, columns, submit, onProgress }) {
  const failed = []
  let success = 0
  const header = rows[0] || []
  const body = rows.slice(1)
  for (let i = 0; i < body.length; i++) {
    const lineNo = i + 2 // 含表头，1 起
    const values = {}
    header.forEach((h, idx) => {
      const col = columns.find(c => c.label === String(h).trim() || c.key === String(h).trim())
      if (col) values[col.key] = String(body[i][idx] ?? '').trim()
    })
    // 列名全不匹配 → 模板不对，直接终止（避免"全部行都失败"的噪声）
    if (i === 0 && Object.keys(values).length === 0) {
      throw new Error('表头与模板不匹配，请先下载模板（列名须一致）')
    }
    const reason = validateRow(values, columns)
    if (reason) {
      failed.push({ line: lineNo, values, reason })
      continue
    }
    try {
      const payload = {}
      columns.forEach(c => { payload[c.key] = c.transform ? c.transform(values[c.key]) : values[c.key] })
      await submit(payload)
      success += 1
    } catch (e) {
      failed.push({ line: lineNo, values, reason: e?.message || '服务端拒绝（未返回原因）' })
    }
    if (onProgress) onProgress(success + failed.length, body.length)
  }
  return { success, failed, total: body.length }
}

function validateRow(values, columns) {
  for (const col of columns) {
    const value = values[col.key]
    if (col.required && (value === undefined || value === '')) {
      return `${col.label}不能为空`
    }
    if (col.validate && value !== undefined && value !== '') {
      const msg = col.validate(value)
      if (msg) return msg
    }
  }
  return ''
}

/** 导入结果提示（成功/失败汇总）。 */
export function summarize(result) {
  if (result.failed.length === 0) {
    ElMessage.success(`导入完成：成功 ${result.success} 条（共 ${result.total} 行）`)
  } else {
    ElMessage.warning(`导入完成：成功 ${result.success} 条、失败 ${result.failed.length} 条（详见失败明细）`)
  }
}
