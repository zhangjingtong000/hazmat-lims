/**
 * 生成 README 用截图（登录页 / 看板 / 禁配墙 / 台账 / 禁配规则 / 大屏）。
 * 前置：后端已在 BASE_URL 启动，且静态目录里是已构建的前端产物。
 * 用法：cd e2e && BASE_URL=http://localhost:8080 node scripts/screenshots.js
 */
const path = require('path')
const fs = require('fs')
const { chromium } = require('@playwright/test')

const BASE = process.env.BASE_URL || 'http://localhost:8080'
const ADMIN_USER = process.env.SHOT_USER || 'admin'
const ADMIN_PASS = process.env.SHOT_PASS || 'admin123'
const OUT = path.resolve(__dirname, '..', '..', 'docs', 'screenshots')

async function main() {
  fs.mkdirSync(OUT, { recursive: true })
  const browser = await chromium.launch()
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'zh-CN' })
  const page = await context.newPage()

  // 1) 登录页（等入场动画结束，否则表单卡片还是透明的）
  await page.goto(BASE + '/#/login', { waitUntil: 'networkidle' })
  await page.waitForSelector('input[placeholder="请输入用户名"]')
  await page.waitForTimeout(2000)
  await page.screenshot({ path: path.join(OUT, '01-login.png') })
  console.log('saved 01-login.png')

  // 2) 看板
  await page.fill('input[placeholder="请输入用户名"]', ADMIN_USER)
  await page.fill('input[placeholder="请输入密码"]', ADMIN_PASS)
  await page.click('button[type="submit"]')
  await page.waitForURL(/dashboard/, { timeout: 30000 })
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(2500)
  await page.screenshot({ path: path.join(OUT, '02-dashboard.png') })
  console.log('saved 02-dashboard.png')

  // 3) 禁配墙（看板下半部分的"系统会拒绝什么"）
  const wall = page.locator('[data-testid="rule-wall"]')
  await wall.scrollIntoViewIfNeeded()
  await page.waitForTimeout(600)
  await page.screenshot({ path: path.join(OUT, '03-rule-wall.png') })
  console.log('saved 03-rule-wall.png')

  // 4) 化学品台账
  await page.goto(BASE + '/#/chem/info', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1500)
  await page.screenshot({ path: path.join(OUT, '04-ledger.png') })
  console.log('saved 04-ledger.png')

  // 5) 基础数据 → 禁配规则（成对存储的规则表）
  await page.goto(BASE + '/#/chem/base', { waitUntil: 'networkidle' })
  await page.waitForTimeout(1200)
  const compatTab = page.getByRole('tab', { name: '禁配规则' })
  if (await compatTab.count()) {
    await compatTab.first().click()
    await page.waitForTimeout(800)
  }
  await page.screenshot({ path: path.join(OUT, '05-compat-rules.png') })
  console.log('saved 05-compat-rules.png')

  // 6) 大屏（等 Three.js 分子场与图表稳定）
  await page.goto(BASE + '/#/screen', { waitUntil: 'networkidle' })
  await page.waitForTimeout(7000)
  await page.screenshot({ path: path.join(OUT, '06-screen.png') })
  console.log('saved 06-screen.png')

  await browser.close()
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
