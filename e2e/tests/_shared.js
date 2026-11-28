/* 共享辅助：hazmat-lims E2E（extra.spec.js / accept.spec.js / review.spec.js 共同引用） */
const ADMIN = { username: 'admin', password: 'admin123' }
const LI = { username: 'li', password: '123456' }
const API_BASE = process.env.BASE_URL || 'http://localhost:8080'

async function login(page, account) {
  await page.goto('/#/login')
  await page.getByPlaceholder('用户名').fill(account.username)
  await page.getByPlaceholder('密码').fill(account.password)
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL(/#\/dashboard/)
  await page.getByText('效期临近').first().waitFor()
}

function attachWatchers(page, issues) {
  page.on('console', msg => {
    if (msg.type() === 'error') issues.push(`console: ${msg.text()}`)
  })
  page.on('pageerror', err => issues.push(`pageerror: ${err.message}`))
  page.on('requestfailed', req => {
    const url = req.url()
    if (!url.includes('favicon')) issues.push(`requestfailed: ${url}`)
  })
  page.on('response', resp => {
    if (resp.status() >= 400) issues.push(`http${resp.status()}: ${resp.url()}`)
  })
}

async function apiToken(account = ADMIN) {
  const resp = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(account)
  })
  const body = await resp.json()
  if (body.code !== 0) throw new Error(`登录失败: ${body.message}`)
  return body.data.token
}

/** GET list 包装：返回 data.records（后端 TableData{total,records}） */
async function apiList(token, path, params = {}) {
  const qs = new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)])).toString()
  const resp = await fetch(`${API_BASE}${path}${qs ? `?${qs}` : ''}`, {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await resp.json()
  if (body.code !== 0) throw new Error(`GET ${path} 失败: ${body.message}`)
  return body.data
}

/* ═══ accept.spec.js 扩展区（只追加，不改动上方既有定义/导出） ═══ */
const ZHANG = { username: 'zhang', password: '123456' }
const WANG = { username: 'wang', password: '123456' }
const CHEN = { username: 'chen', password: '123456' }
const LIU = { username: 'liu', password: '123456' }

const TAG = () => `T${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`

/** 通用 JSON 请求；allowBizError=true 时 4xxx（HTTP 200 + code!=0）不抛只返回 body */
async function apiSend(token, method, path, body, { allowBizError = false } = {}) {
  const resp = await fetch(`${API_BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const json = await resp.json().catch(() => ({}))
  if (!allowBizError && json.code !== 0) throw new Error(`${method} ${path} 失败: ${json.message}`)
  return json
}

/** 换账号 UI 登录：先清 token 再进登录页（否则 guard 会把 /#/login 重定向回 dashboard） */
async function loginFresh(page, account) {
  await page.goto('/')
  await page.evaluate(() => { sessionStorage.clear(); localStorage.clear() })
  await login(page, account)
}

/** 便捷取 records：list 接口返回 {total,records} */
async function records(token, path, params = {}) {
  const data = await apiList(token, path, params)
  return data.records || []
}

export { ADMIN, LI, API_BASE, login, attachWatchers, apiToken, apiList,
  ZHANG, WANG, CHEN, LIU, TAG, apiSend, loginFresh, records }
