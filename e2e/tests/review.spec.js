import { test, expect } from '@playwright/test'
import { ADMIN, API_BASE, attachWatchers, apiToken, apiList } from './_shared.js'

/* 复审专项套件（权限/留痕/规则拦截）——7 条，编号 R01-R07。
 * 账号：admin/admin123；zhang=导师(2) li=安全员(3) wang=学院(4) zhao=校级(5) chen=申请人(6) liu=研究生(7)。
 * 种子台账：盐酸(id 8, cat1) 氰化钾(id 11, cat4 剧毒) 氯化钠(id 12, cat0)。
 */

const ACCOUNTS = {
  admin: ADMIN,
  zhang: { username: 'zhang', password: '123456' },
  li: { username: 'li', password: '123456' },
  wang: { username: 'wang', password: '123456' },
  chen: { username: 'chen', password: '123456' },
  liu: { username: 'liu', password: '123456' }
}

function TAG() {
  return `T${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`
}

/** fetch 包装：返回 {status, body}（业务错误为 HTTP 200 + body.code!=0） */
async function api(token, method, path, body) {
  const resp = await fetch(`${API_BASE}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  let out = null
  try { out = await resp.json() } catch { /* 空响应体 */ }
  return { status: resp.status, body: out }
}

/** 本套件自有登录（不依赖 _shared 中 dashboard 特定文案，只认 regimen-bar） */
async function localLogin(page, account) {
  await page.goto('/#/login')
  await page.getByPlaceholder('用户名').fill(account.username)
  await page.getByPlaceholder('密码').fill(account.password)
  await page.getByRole('button', { name: '登 录' }).click()
  await page.waitForURL(/#\/dashboard/)
  await page.locator('.regimen-bar').first().waitFor()
}

/** 切换账号：清 token → 强制落到登录页 */
async function switchLogin(page, account) {
  await page.evaluate(() => { sessionStorage.clear(); localStorage.clear() })
  await page.goto('/#/login')
  await page.reload()
  await localLogin(page, account)
}

test('R01 越权拦截：申请人 chen 无 hzm:info:add，直建档台账 → HTTP 403', async ({ request }) => {
  const chen = await apiToken(ACCOUNTS.chen)
  const denied = await request.post(`${API_BASE}/chem/info`, {
    headers: { Authorization: `Bearer ${chen}`, 'Content-Type': 'application/json' },
    data: { name: 'R01越权探针', casNo: '9999-99-9', category: 0 }
  })
  expect(denied.status()).toBe(403)
  const body = await denied.json()
  expect(body.code).toBe(403)

  // 只读复核：台账未被创建
  const admin = await apiToken()
  const hit = await apiList(admin, '/chem/info/list', { keyword: 'R01越权探针' })
  expect(hit.records).toHaveLength(0)
})

test('R02 失败留痕：403 拒绝写入操作日志，admin 日志页可检索到"失败"记录', async ({ page, request }) => {
  const issues = []
  attachWatchers(page, issues)
  // 先制造一条新鲜失败留痕（LoginInterceptor 层单独留痕）
  const chen = await apiToken(ACCOUNTS.chen)
  const denied = await request.post(`${API_BASE}/chem/info`, {
    headers: { Authorization: `Bearer ${chen}`, 'Content-Type': 'application/json' },
    data: { name: 'R02留痕探针', casNo: '9999-99-9', category: 0 }
  })
  expect(denied.status()).toBe(403)

  const admin = await apiToken()
  // 前置：日志查询链路可用（表名不匹配缺陷时此处即失败并给出明确指引）
  const precheck = await api(admin, 'GET', '/system/operlog/list?size=10')
  if (precheck.body.code !== 0) {
    throw new Error(`R02 无法验证：操作日志查询接口异常 code=${precheck.body.code}，`
      + `msg=${String(precheck.body.message).slice(0, 140)}——失败留痕链路损坏（需修 sys_oper_log/oper_log 表名不匹配并重启，当前约束禁止）`)
  }

  await localLogin(page, ADMIN)
  await page.goto('/#/system/operlog')
  const rows = page.locator('.el-table__body .el-table__row')
  await rows.first().waitFor()
  await page.getByPlaceholder('模块 / 动作 / 操作人').fill('拒绝 POST /chem/info')
  await page.locator('.filter-card .el-select').click()
  await page.locator('.el-select-dropdown:visible').getByText('失败', { exact: true }).click()
  await page.getByRole('button', { name: '查询' }).click()

  // 日志按 id 倒序：最新一条应即本次拒绝（动作含 URI / 操作人=chen / 结果=失败）
  const firstRow = rows.first()
  await expect(firstRow).toContainText('拒绝 POST /chem/info')
  await expect(firstRow).toContainText('chen')
  await expect(firstRow.locator('.el-tag')).toHaveText('失败')
  await page.screenshot({ path: 'report/r02-operlog.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})

test('R03 按钮级权限：台账列表"封存"按钮按 hasPerm 过滤（chen 无 / admin 有）', async ({ page }) => {
  const issues = []
  attachWatchers(page, issues)
  // chen（applicant）：无 hzm:info:edit → 无封存/解封按钮
  await localLogin(page, ACCOUNTS.chen)
  await page.goto('/#/chem/info')
  const rows = page.locator('.el-table__body .el-table__row')
  await rows.first().waitFor()
  await expect(rows.first().getByRole('button', { name: '详情' })).toBeVisible()
  await expect(page.getByRole('button', { name: '封存' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '解封' })).toHaveCount(0)
  await page.screenshot({ path: 'report/r03-chen-no-seal.png', fullPage: true })

  // admin：角色含全部权限 → 封存按钮可见（只看不点）
  await switchLogin(page, ADMIN)
  await page.goto('/#/chem/info')
  await rows.first().waitFor()
  await expect(page.getByRole('button', { name: '封存' }).first()).toBeVisible()
  await page.screenshot({ path: 'report/r03-admin-seal.png', fullPage: true })
  expect(issues, issues.join('\n')).toEqual([])
})

test('R04 双签拦截：剧毒入库登记人自任第二签 → 4201；无学院审批权限者 → 4202', async ({ request }) => {
  const admin = await apiToken()
  const li = await apiToken(ACCOUNTS.li)
  const rkCode = `RK-TEST-${TAG()}`
  let sid = null
  try {
    const lotNo = `LOT-${TAG()}`
    const created = await request.post(`${API_BASE}/chem/stockin`, {
      headers: { Authorization: `Bearer ${admin}`, 'Content-Type': 'application/json' },
      data: {
        chemicalId: 11, qty: 1, lotNo,
        cabinetId: 5, checkerId: 4, supplier: 'R04 双签拦截探针'
      }
    })
    const cbody = await created.json()
    expect(cbody.code).toBe(0)
    sid = cbody.data.id
    expect(cbody.data.status).toBe(0) // 剧毒仅首签：待第二签
    expect(cbody.data.code).toMatch(/^RK-\d{8}-\d{3}$/) // 服务端自生成单号

    // 登记人（admin, uid=1）自己 countersign：登记人≠复核人 → 4201
    const self = await api(admin, 'POST', `/chem/stockin/${sid}/countersign`, {})
    expect(self.body.code).toBe(4201)
    expect(self.body.message).toContain('双签不得同人')

    // li 持 hzm:stockin:countersign 按钮权限但不持学院审批权限 → 4202
    const liResp = await api(li, 'POST', `/chem/stockin/${sid}/countersign`, {})
    expect(liResp.body.code).toBe(4202)

    // 单据未落账：仍待验收 status=0，无批次产生
    const after = await apiList(admin, '/chem/stockin/list', { keyword: lotNo })
    expect(after.records[0].status).toBe(0)
  } finally {
    if (sid) {
      await api(admin, 'POST', `/chem/stockin/${sid}/reject`, { reason: 'R04 测试清理拒收（未落账）' })
    }
  }
})

test('R05 连续同人拦截：2 级申购链上同一审批人连审两级 → 4203', async ({ request }) => {
  const chen = await apiToken(ACCOUNTS.chen)
  const li = await apiToken(ACCOUNTS.li)
  const admin = await apiToken()
  let apCode = null
  let pid = null
  try {
    // API 建单提交：category=1（普通危化品）→ 链 = 导师 → 安全员，共 2 级。
    // 指定导师=admin（uid=1，种子中唯一同时持两级节点权限的人）：zhang 连审两级会先被
    // 控制器 4105 无权限短路（zhang 不持 audit-safety），无法触达服务层 4203 规则本体。
    const created = await api(chen, 'POST', '/chem/purchase', {
      chemicalName: `R05连续同人探针${TAG()}`, casNo: '7647-01-0',
      category: 1, qty: 1, unit: '瓶', advisorId: 1, purpose: 'R05 复审探针'
    })
    expect(created.body.code).toBe(0)
    pid = created.body.data.id

    const submitted = await api(chen, 'POST', `/chem/purchase/${pid}/submit`)
    expect(submitted.body.code).toBe(0)

    // 第 1 级：被指定的"导师" admin 通过
    const audit1 = await api(admin, 'POST', `/chem/purchase/${pid}/audit`, { approve: true, comment: 'R05 导师级通过' })
    expect(audit1.body.code).toBe(0)
    apCode = created.body.data.code // 服务端自生成单号
    const d1 = await apiList(chen, '/chem/purchase/list', { keyword: apCode })
    expect(d1.records[0].currentSeq).toBe(2)

    // 同一人（admin）连审第 2 级 → 服务层 4203 同一审批人不得连续审批两级
    const audit2 = await api(admin, 'POST', `/chem/purchase/${pid}/audit`, { approve: true, comment: 'R05 连审第二级' })
    expect(audit2.body.code).toBe(4203)
    expect(audit2.body.message).toContain('连续')

    // 单据未被推进：仍停在审批中第 2 级
    const d2 = await apiList(chen, '/chem/purchase/list', { keyword: apCode })
    expect(d2.records[0].currentSeq).toBe(2)
    expect(d2.records[0].status).toBe(1)
  } finally {
    // 清理：li（安全员）合法通过第 2 级 → 已批；chen 作废该单（未入库，无副作用）
    try {
      if (pid) {
        await api(li, 'POST', `/chem/purchase/${pid}/audit`, { approve: true, comment: 'R05 清理：安全员级通过' })
        await api(chen, 'POST', `/chem/purchase/${pid}/void`)
      }
    } catch { /* 清理失败不遮蔽断言 */ }
  }
})

test('R06 导师节点指定制：wang 无导师签权限被拦，节点仅 zhang 可签', async ({ request }) => {
  const chen = await apiToken(ACCOUNTS.chen)
  const wang = await apiToken(ACCOUNTS.wang)
  const zhang = await apiToken(ACCOUNTS.zhang)
  const li = await apiToken(ACCOUNTS.li)
  let rid = null
  try {
    // chen 无关联申购领用 category=1 台账（盐酸 id=8）→ 初始态为"待导师"
    const created = await api(chen, 'POST', '/chem/req', {
      chemicalId: 8, qty: 1, purpose: 'R06 导师节点探针'
    })
    expect(created.body.code).toBe(0)
    rid = created.body.data.id
    expect(created.body.data.status).toBe(0)

    // wang（学院）调导师节点 → 无 hzm:req:advisor 按钮权限：HTTP 403；若放行到服务层则须 4104/4105
    const wangResp = await api(wang, 'POST', `/chem/req/${rid}/advisor`, { approve: true })
    if (wangResp.status === 200) {
      expect([4104, 4105]).toContain(wangResp.body.code)
    } else {
      expect(wangResp.status).toBe(403)
    }
    // 单据未推进：仍待导师
    const still = await api(zhang, 'GET', `/chem/req/${rid}`)
    expect(still.body.data.req.status).toBe(0)

    // 正向对照：固定导师 zhang 通过该节点（0→1）
    const zhangPass = await api(zhang, 'POST', `/chem/req/${rid}/advisor`, { approve: true, comment: 'R06 导师签' })
    expect(zhangPass.body.code).toBe(0)
    const advanced = await api(zhang, 'GET', `/chem/req/${rid}`)
    expect(advanced.body.data.req.status).toBe(1)
  } finally {
    try {
      if (rid) {
        const d = await api(li, 'GET', `/chem/req/${rid}`)
        const st = d.body.data?.req?.status
        if (st === 0) {
          await api(zhang, 'POST', `/chem/req/${rid}/advisor`, { approve: false, comment: 'R06 清理驳回' })
        } else if (st === 1) {
          await api(li, 'POST', `/chem/req/${rid}/check`, { approve: false, comment: 'R06 清理驳回' })
        }
      }
    } catch { /* 清理失败不遮蔽断言 */ }
  }
})

test('R07 归还拒绝留痕：空原因业务拦截 4101，填原因拒绝成功且单据离开待确认', async ({ page, request }) => {
  const issues = []
  attachWatchers(page, issues)
  const admin = await apiToken()
  const chen = await apiToken(ACCOUNTS.chen)
  const li = await apiToken(ACCOUNTS.li)
  let reqId = null, batchId = null, rid = null
  try {
    // ① 建领用：氯化钠（category=0）无关联申购 → 直接待复核
    const created = await api(chen, 'POST', '/chem/req', {
      chemicalId: 12, qty: 1, purpose: 'R07 归还拒绝探针'
    })
    expect(created.body.code).toBe(0)
    reqId = created.body.data.id
    expect(created.body.data.status).toBe(1)

    // ② li 复核双签通过 → FEFO 出库（status=2）
    const checked = await api(li, 'POST', `/chem/req/${reqId}/check`, { approve: true, comment: 'R07 双签出库' })
    expect(checked.body.code).toBe(0)
    const detail = await api(chen, 'GET', `/chem/req/${reqId}`)
    expect(detail.body.data.req.status).toBe(2)
    batchId = detail.body.data.batches[0].batchId

    // ③ chen 提交余量归还 → 待确认归还单
    const ret = await api(chen, 'POST', `/chem/req/${reqId}/return`, { batchId, qty: 1 })
    expect(ret.body.code).toBe(0)
    rid = ret.body.data.id
    const retCode = ret.body.data.code
    expect(ret.body.data.status).toBe(0)

    // ④ li 空原因拒绝 → 业务校验拦截（HTTP 200 + 4101）
    const empty = await api(li, 'POST', `/chem/req/return/${rid}/reject`, { comment: '' })
    expect(empty.status).toBe(200)
    expect(empty.body.code).toBe(4101)
    expect(empty.body.message).toContain('拒绝必须填写原因')

    // ⑤ UI：审批中心"归还确认"页 li 拒绝——前端必填拦截 + 填原因成功
    await localLogin(page, ACCOUNTS.li)
    await page.goto('/#/chem/todo')
    const row = page.locator('.el-table__row', { hasText: retCode })
    await page.locator('.el-tabs__item', { hasText: '归还确认' }).click()
    await row.first().waitFor()
    await row.getByRole('button', { name: '拒绝' }).click()
    const box = page.locator('.el-message-box')
    await box.waitFor()
    await box.getByRole('button', { name: '确定' }).click()
    await expect(box.locator('.el-message-box__errormsg')).toHaveText('拒绝原因必填')
    await expect(box).toBeVisible() // 未被提交
    await box.locator('.el-message-box__input input').fill('R07 批号存疑，拒绝回库待复核')
    await box.getByRole('button', { name: '确定' }).click()
    await expect(box).toHaveCount(0) // 关闭 = 拒绝成功
    await page.screenshot({ path: 'report/r07-return-rejected.png', fullPage: true })

    // ⑥ 单据离开"待确认"：审批中心不再出现该归还单；终态守卫双拦截
    await expect(page.locator('.el-table__row', { hasText: retCode })).toHaveCount(0)
    const todo = await api(admin, 'GET', '/chem/todo')
    expect(todo.body.data.returns.some(r => r.id === rid)).toBe(false)
    const again = await api(li, 'POST', `/chem/req/return/${rid}/reject`, { comment: 'R07 重复拒绝' })
    expect(again.body.code).toBe(4101) // 仅待确认归还单可拒绝 → 已是终态
    const confirm = await api(admin, 'POST', `/chem/req/return/${rid}/confirm`)
    expect(confirm.body.code).toBe(4101) // 仅待确认归还单可确认
    expect(issues, issues.join('\n')).toEqual([])
  } finally {
    // 对账清理：补一笔足额归还并确认回库 → 台账/批次守恒、领用单足额自动关闭
    try {
      if (reqId && batchId) {
        const ret2 = await api(chen, 'POST', `/chem/req/${reqId}/return`, { batchId, qty: 1 })
        if (ret2.body.code === 0) {
          await api(li, 'POST', `/chem/req/return/${ret2.body.data.id}/confirm`)
        }
      }
    } catch { /* 清理失败不遮蔽断言 */ }
  }
})
