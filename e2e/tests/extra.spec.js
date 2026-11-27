import { test, expect } from '@playwright/test'
import { ADMIN, login, attachWatchers, apiToken, apiList, API_BASE } from './_shared.js'

/* 扩展套件（extra）：申购打印页 / 数据大屏 / 批次移柜 / 效期预警 / 字典页 —— 只读为主，E03 自复原 */

test('E01 申购打印页：真实申购单渲染两联单据与五级签批横排表', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  const token = await apiToken()
  const { records } = await apiList(token, '/chem/purchase/list', { page: 1, size: 10 })
  const req = records[0]
  expect(req, '种子申购单应存在').toBeTruthy()

  await login(page, ADMIN)
  await page.goto(`/#/print/purchase/${req.id}`)
  await expect(page.getByText('高校实验室危险化学品申购单').first()).toBeVisible()

  // 两联单据：第一联审批存根 + 第二联使用凭证，骑缝编号
  await expect(page.locator('.print-sheet')).toHaveCount(2)
  await expect(page.getByText('第一联 · 审批存根')).toBeVisible()
  await expect(page.getByText('第二联 · 使用凭证')).toBeVisible()

  // 签批横排表：表头 5 列 + 申请人行 + 审批链行（≥3 行）
  const signTable = page.locator('.sign-table')
  for (const head of ['环节', '签批人', '意见', '结果', '日期']) {
    await expect(signTable.locator('th', { hasText: head }).first()).toBeVisible()
  }
  // 动态行数：表头 1 行 + 申请人 1 行 + 审批链 chainLen(category) 行
  const chainLen = ({ 0: 1, 1: 2, 2: 3, 3: 3, 4: 4 }[Number(req.category)] ?? 1)
  await expect(signTable.locator('tr')).toHaveCount(2 + chainLen)
  await expect(signTable.locator('tr', { hasText: '申请人' })).toBeVisible()
  await expect(page.locator('.seam-no', { hasText: `No.${req.code}` }).first()).toBeVisible()

  await page.screenshot({ path: 'report/e01-print.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})

test('E02 数据大屏：柜位锚点 + 多类图表渲染，深色监管风格', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  await login(page, ADMIN)
  await page.goto('/#/screen')

  await expect(page.getByRole('heading', { name: '实验室危险化学品全流程监管平台' })).toBeVisible()

  // 四类预警指标卡
  await expect(page.locator('.card')).toHaveCount(4)

  // 柜位实体锚点（真实柜位数据）
  await expect.poll(async () => page.locator('.cab').count(), { timeout: 15000 }).toBeGreaterThanOrEqual(4)

  // 多类图表各自挂载 canvas（折线/环形/雷达 ≥3）
  await expect
    .poll(async () => page.locator('.chart canvas').count(), { timeout: 15000 })
    .toBeGreaterThanOrEqual(3)

  // 元素丰富度：标签墙 + 单据排名 + 柜位占用进度
  await expect.poll(async () => page.locator('.tag').count()).toBeGreaterThanOrEqual(3)
  await expect.poll(async () => page.locator('.rank li').count()).toBeGreaterThanOrEqual(2)
  await expect.poll(async () => page.locator('.occ li').count()).toBeGreaterThanOrEqual(4)

  // 深色监管风格：舞台使用背景素材
  const bg = await page.locator('.stage').evaluate(el => getComputedStyle(el).backgroundImage)
  expect(bg).toContain('screen-bg.jpg')

  await page.screenshot({ path: 'report/e02-screen.png', fullPage: false })
  expect(issues, issues.join('\n')).toEqual([])
})

test('E03 批次移柜：移柜对话框换柜 → 成功提示 + API 柜位变更（复原回原柜位）', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  const token = await apiToken()

  // 动态挑一条在库批次与其当前柜位，目标柜取通用柜 HG-G01
  const { records: batches } = await apiList(token, '/chem/batch/list', { page: 1, size: 50 })
  const batch = batches.find(b => b.status === 0 && Number(b.qtyLeft) > 0)
  expect(batch, '种子在库批次应存在').toBeTruthy()
  const { records: cabinets } = { records: (await apiList(token, '/chem/cabinet')) || [] }
  const target = cabinets.find(c => c.code === 'HG-G01' && c.id !== batch.cabinetId)
      || cabinets.find(c => c.id !== batch.cabinetId)
  expect(target, '应有另一柜位可移').toBeTruthy()

  await login(page, ADMIN)
  await page.goto('/#/chem/batch')
  const row = page.locator('.el-table__row', { hasText: batch.lotNo }).first()
  await row.getByRole('button', { name: '移柜' }).click()

  const dialog = page.locator('.el-dialog', { hasText: '批次移柜' })
  await expect(dialog).toBeVisible()
  await dialog.locator('.el-select').click()
  await page.locator('.el-select-dropdown__item:visible', { hasText: target.code }).click()
  await dialog.getByRole('button', { name: '执行移柜' }).click()

  // 成功提示 + API 侧柜位确已变更
  await expect(page.locator('.el-message', { hasText: '移柜完成' })).toBeVisible()
  const findBatch = async () => {
    const d = await apiList(token, '/chem/batch/list', { page: 1, size: 50 })
    return d.records.find(r => r.id === batch.id)
  }
  await expect
    .poll(async () => (await findBatch())?.cabinetId, { timeout: 10000 })
    .toBe(target.id)

  // 自复原：移回原柜位（RD 组不与原柜 FLAM/ORG 冲突），并确认 API 复位
  const back = await fetch(`${API_BASE}/chem/batch/${batch.id}/cabinet`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ cabinetId: batch.cabinetId })
  })
  const backBody = await back.json()
  expect(backBody.code, `移回原柜位失败: ${backBody.message}`).toBe(0)
  await expect
    .poll(async () => (await findBatch())?.cabinetId, { timeout: 10000 })
    .toBe(batch.cabinetId)

  await page.screenshot({ path: 'report/e03-move.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})

test('E04 效期预警：看板效期临近卡 + 批次列表过期批次危险标记', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  await login(page, ADMIN)

  // 控制台"效期临近"预警卡（依《管理办法》第 12 条四类预警之首）
  await page.goto('/#/dashboard')
  const expiryCard = page.locator('.warn-tile', { hasText: '效期临近' })
  await expect(expiryCard).toBeVisible()

  // 种子含过期批次 LOT-20240101（浓硫酸，2026-06-08 已过期）：批次列表出现红色"过期"标记
  await page.goto('/#/chem/batch')
  const expiredRow = page.locator('.el-table__row', { hasText: 'LOT-20240101' }).first()
  await expect(expiredRow.getByText('过期', { exact: true })).toBeVisible()
  await expect(expiredRow.locator('.exp-danger')).toBeVisible()

  await page.screenshot({ path: 'report/e04-expiry.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})

test('E05 字典页：基础数据字典 Tab 加载 20 条 5 类，按 unit 筛选后行数减少', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  await login(page, ADMIN)
  await page.goto('/#/chem/base')

  await page.getByRole('tab', { name: '字典' }).click()
  const dictRows = page.locator('.dict-table .el-table__row')
  await expect(dictRows.first()).toBeVisible()

  // 全量 20 条，5 个 dictType 均有记录
  await expect(dictRows).toHaveCount(20)
  for (const t of ['unit', 'compat_group', 'waste_method', 'cabinet_type', 'disposal_target']) {
    await expect(page.locator('.dict-table td', { hasText: t }).first()).toBeVisible()
  }

  // 类型筛选：选 unit → 行数减少但仍 > 0
  await page.locator('.dict-toolbar .el-select').click()
  await page.locator('.el-select-dropdown__item:visible', { hasText: /^unit$/ }).click()
  await expect
    .poll(async () => page.locator('.dict-table .el-table__row').count(), { timeout: 10000 })
    .toBeLessThan(20)
  const filtered = await page.locator('.dict-table .el-table__row').count()
  expect(filtered).toBeGreaterThan(0)

  await page.screenshot({ path: 'report/e05-dict.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})
