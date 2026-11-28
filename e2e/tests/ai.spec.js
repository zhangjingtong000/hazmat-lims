import { test, expect } from '@playwright/test'
import { ADMIN, login, attachWatchers } from './_shared.js'

/* 防御性 AI 层套件（ai）：智能查询动线 —— 无 key 环境验证缓存回放/降级链 */

test('E-AI01 智能查询：种子问题命中缓存回放并返回正确台账结果', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  await login(page, ADMIN)

  // 顶栏入口 → 弹窗
  await page.getByTestId('ai-entry').click()
  await expect(page.getByRole('dialog', { name: '智能查询' })).toBeVisible()

  // 输入种子问题（种子数据：氰化钾现存 1 瓶，HG-J01 剧毒专柜）
  await page.getByTestId('ai-input').fill('氰化钾还有多少库存？')
  await page.getByTestId('ai-submit').click()

  // 结果：缓存回放徽标 + 摘要 + 台账行
  const dialog = page.getByRole('dialog', { name: '智能查询' })
  await expect(dialog.getByTestId('ai-source')).toHaveText('缓存回放', { timeout: 30000 })
  await expect(dialog.getByTestId('ai-summary')).toContainText('氰化钾')
  await expect(dialog.getByTestId('ai-summary')).toContainText('HG-J01')
  await expect(dialog.getByRole('cell', { name: 'HG-JD-2026-0011' })).toBeVisible()
  await expect(dialog.getByRole('cell', { name: '丙酮' })).toHaveCount(0)

  expect(issues, issues.join('; ')).toHaveLength(0)
})
