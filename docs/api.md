# 接口文档（手写清单）

> 项目：hazmat-lims / hazmat-management ｜ 基线版本：v1.0.0 ｜ 生成方式：按控制器源码逐条核对（非 OpenAPI 自动生成）
> 维护约定：**改接口必须同步改本文件**（清单与代码同源是硬要求；下一版计划引入 springdoc 自动生成以消除漂移风险）。

## 0. 通用约定

- **Base URL**：`http://<host>:8080`（默认端口 8080）
- **认证**：除 `/auth/login`、`/health` 外，所有接口需带 `Authorization: Bearer <token>`；
  令牌由登录接口返回，默认 12 小时有效（`app.token-hours`）。
- **响应包裹**：业务接口统一返回 `{"code":0,"message":"success","data":{...}}`
  - `code=0` 成功；非 0 为业务错误（**HTTP 仍为 200**，按 `code` 判定）；
  - 例外：`/health` 直接返回裸 JSON（探针友好）；鉴权失败由拦截器返回 **HTTP 401/403**（body 为 `{code,message}`）；
    限流返回 **HTTP 429**（`code=4290`）。
- **分页**：`page`（1 起）+ `size`；返回 `{"total":N,"records":[...]}`。
- **时间格式**：`yyyy-MM-dd HH:mm:ss`；日期字段 `yyyy-MM-dd`；服务端统一 `Asia/Shanghai` 口径。
- **错误码**：`4001` 参数校验失败｜`41xx` 状态/守卫类｜`42xx` 双签与审批人约束｜`43xx` 禁配｜
  `44xx` 数量对账｜`45xx` 台账状态｜`46xx` 废弃批次｜`4290` 限流｜`4291` 登录锁定｜`500` 系统异常。
  完整释义见各接口说明与 `GlobalExceptionHandler` / 业务代码中的抛出点。
- **追踪**：所有响应带 `X-Trace-Id`（8 位短码），排障时据此检索日志。
- **权限**：标注 `perm:` 的接口需对应权限键（管理员 `admin` 角色放行全部）；
  未标注者仅需登录。

## 1. 认证与会话

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/auth/login` | 登录；body `{username,password}`；返回 `{token,user,roles,perms,menus}`；失败 5 次/10 分钟锁定（4291） | 公开 |
| GET | `/auth/me` | 当前用户档案（含菜单树与权限键） | 登录 |
| POST | `/auth/password` | 自助改密；body `{oldPassword,newPassword}`；新口令 8~64 位且至少两类字符 | 登录 |

```bash
curl -s -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}'
# → {"code":0,"data":{"token":"eyJ...","user":{...},"perms":["hzm:info:add",...]}}
```

## 2. 运维/探针

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/health` | 健康检查（裸 JSON）：`{status,version,time,checks:{db,regs,ai}}` | 公开 |

## 3. 化学品台账 `/chem/info`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/info/list` | 分页列表；`keyword`（编码/名称/CAS）、`category`、`status`、`page`、`size` | 登录 |
| GET | `/chem/info/{id}` | 档案聚合：台账 + 在库批次（FEFO 序） | 登录 |
| GET | `/chem/info/cas-check` | CAS 查重预检；`casNo`；未命中返回 `data=null` | 登录 |
| POST | `/chem/info` | 建档；CAS 命中则挂既有台账并返回既有记录 | `hzm:info:add` |
| PUT | `/chem/info` | 维护；已有批次时禁改分类（4107） | `hzm:info:edit` |
| POST | `/chem/info/{id}/status` | 封存/解封；body `{status:0|1}`；封存即停领用（4504） | `hzm:info:edit` |
| DELETE | `/chem/info/{ids}` | 逻辑删除（多 ID 逗号分隔）；三条件守卫（已清空/无在库批次/无未完成单据） | `hzm:info:remove` |

建档请求体（字段级约束与前端 rules 一致）：

```jsonc
{
  "name": "丙酮",              // 必填，≤100
  "alias": "",                 // ≤100
  "casNo": "67-64-1",          // 必填，格式 \d{2,7}-\d{2}-\d
  "category": 1,               // 必填，0..4（0普通/1普通危化/2易制毒/3易制爆/4剧毒）
  "compatGroup": "FLAM",       // 必填，≤8
  "compatGroup2": null,        // ≤8
  "spec": "AR 500mL/瓶",       // ≤50
  "unit": "瓶",                // ≤16
  "minStock": 2,               // ≥0
  "expiryWarnDays": 30,        // 1..365
  "maxOnce": null,             // ≥0（分类=4 时业务必填，安全员维护）
  "cabinetId": null,
  "ghzPictogram": "",          // ≤50
  "firstAid": ""               // ≤200
}
```

## 4. 申购与审批链 `/chem/purchase`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/purchase/list` | `keyword`、`status`、`mine`、`todo`、分页 | 登录 |
| GET | `/chem/purchase/{id}` | 详情：单头 + 审批链记录 | 登录 |
| POST | `/chem/purchase` | 保存/提交本体；`status=0` 草稿、`status=1` 直接提交（提交必填 `advisorId`） | `hzm:purchase:submit` |
| POST | `/chem/purchase/{id}/submit` | 草稿提交（仅本人、仅草稿） | `hzm:purchase:submit` |
| POST | `/chem/purchase/{id}/audit` | 逐级审批；body `{approve:true|false,comment}`；驳回必填意见；节点权限动态校验（4105）、连续同人 4203 | 登录+节点权限 |
| POST | `/chem/purchase/{id}/void` | 作废（草稿或已批未入库，仅本人） | `hzm:purchase:submit` |

审批链长度由分类决定：`0→1 级(导师)`、`1→2 级(+安全员)`、`2/3→3 级(+学院)`、`4→4 级(+校级)`。

## 5. 入库双签 `/chem/stockin`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/stockin/list` | `keyword`（单号/批号）、`status`、分页 | 登录 |
| POST | `/chem/stockin` | 提交入库（普通=一步双签落账；剧毒=首签待验收）；见下方请求体 | `hzm:stockin:add` |
| POST | `/chem/stockin/{id}/countersign` | 剧毒第二签（须学院权限）；登记人=复核人 → 4201 | `hzm:stockin:countersign` |
| POST | `/chem/stockin/{id}/reject` | 拒收（终态）；body `{reason}` 必填 | `hzm:stockin:reject` |

```jsonc
{
  "reqId": null,               // 可空：关联已批申购（累计入库不得超申购量 4402）
  "chemicalId": 12,            // 与 draft 二选一
  "lotNo": "LOT-20260901-01",  // 必填 ≤32，同化学品唯一
  "qty": 10,                   // 必填 >0
  "expiryDate": "2027-03-01",  // 必填（前端必填；服务端可空以兼容历史脚本）
  "cabinetId": 6,              // 必填
  "supplier": "验收测试供应商",  // ≤100
  "permitNo": null,            // ≤50（易制毒/易制爆备案号）
  "checkerId": 4,              // 必填，复核人；不得为接收人本人
  "draft": {                   // chemicalId 为空时必填：新化学品建档草稿
    "name": "氯化钠", "casNo": "7647-14-5", "category": 0,
    "compatGroup": "GEN", "compatGroup2": null, "spec": ""
  }
}
```

## 6. 领用与归还 `/chem/req`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/req/list` | `status`、`mine`、`todo`、分页 | 登录 |
| GET | `/chem/req/{id}` | 详情：单头 + FEFO 出库分配明细（`batches`） | 登录 |
| POST | `/chem/req` | 提交领用；body 见下；起点分支（分类≥1 且非关联 → 待导师签） | `hzm:req:submit` |
| POST | `/chem/req/{id}/advisor` | 导师签（仅单据指定导师本人） | `hzm:req:advisor` |
| POST | `/chem/req/{id}/check` | 复核双签：通过即 FEFO 出库（候选批次只含未过期且有余量者） | `hzm:req:check` |
| PUT | `/chem/req/{id}/dispatch` | 批次改派（等量换批）；body `{originalBatchId,newBatchId}` | `hzm:req:dispatch` |
| POST | `/chem/req/{id}/return` | 余量归还；body `{batchId,qty}`；累计不得超领用量 | 登录（仅本人） |
| GET | `/chem/req/{id}/returns` | 本单归还记录 | 登录 |
| POST | `/chem/req/return/{rid}/confirm` | 确认回库（批次回加 + 台账同步，原子） | `hzm:return:confirm` |
| POST | `/chem/req/return/{rid}/reject` | 拒绝归还；body `{comment}` 必填（留痕） | `hzm:return:reject` |
| POST | `/chem/req/{id}/close` | 用尽核销关闭（2→4）；body `{comment}` 必填 | 登录（仅本人） |

```jsonc
// POST /chem/req
{ "chemicalId": 12, "qty": 1, "purpose": "本科实验（必填 ≤200）",
  "expectReturn": "2026-10-01", "reqId": null, "experiment": "≤100" }
```

## 7. 批次 `/chem/batch`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/batch/list` | `chemicalId` 可空（LEFT JOIN 补化学品名/柜位名）、分页 | 登录 |
| PUT | `/chem/batch/{id}/cabinet` | 移柜；body `{cabinetId}`；移入前禁配校验（4301）、条件更新（柜位/状态变化即 4101） | `hzm:batch:move` |

## 8. 废弃处置 `/chem/waste`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/waste/list` | `keyword`（单号）、`status`、分页 | 登录 |
| GET | `/chem/waste/{id}` | 详情：单头（含化学品/批次/审批人）+ 审批记录 | 登录 |
| POST | `/chem/waste` | 建单；body `{chemicalId,batchId,type(1废液/2空瓶/3过期整批),qty,method}`；错批守卫 4603 | `hzm:waste:submit` |
| POST | `/chem/waste/{id}/audit` | 审批；body `{approve,comment,disposalTarget,voucherNo}`；通过时去向必填，分类 2/3/4 凭证号必填 | `hzm:waste:audit` |

## 9. 账物盘点 `/chem/check`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/check/list` | `result`（0盘点中/1平/2不平）、分页 | 登录 |
| GET | `/chem/check/{id}` | 详情：单头 + 差异明细 | 登录 |
| POST | `/chem/check` | 制单；body `{scopeCategory?,checkerTwoId}`；双人制（两人不同） | `hzm:check:do` |
| POST | `/chem/check/{id}/finish` | 结单（只记差异不改库存）；body 差异行数组 `[{batchId,actualQty,remark?}]` | `hzm:check:do` |
| POST | `/chem/check/{id}/adjust` | 调账（增量修正 + 台账同步）；body `{diffId}` | `hzm:check:adjust` |

## 10. 柜位与禁配

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/cabinet` | 柜位全量列表（含双锁标记） | 登录 |
| POST | `/chem/cabinet` | 新增柜位；body `{code,name,type,dualLock,location}`；编码必填 | `hzm:cabinet:maint` |
| PUT | `/chem/cabinet` | 维护；柜内有在库剧毒批次时禁改类型/取消双锁 | `hzm:cabinet:maint` |
| GET | `/chem/compat` | 禁配规则全量（成对存储） | 登录 |
| POST | `/chem/compat` | 成对新增；body `{groupA,groupB,note}`；同组自配拒绝 | `hzm:compat:maint` |
| DELETE | `/chem/compat` | 成对删除；query `groupA`、`groupB` | `hzm:compat:maint` |

## 11. 待办与统计

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/chem/todo` | 审批中心聚合：`purchases/reqs/wastes/returns` 四类待办 | 登录 |
| GET | `/chem/stats/dashboard` | 看板：四类预警 + 分类概览 | 登录 |
| GET | `/chem/stats/ledger` | 分类台账汇总 | 登录 |
| GET | `/chem/stats/screen` | 数据大屏聚合（柜位/批次/单据/分类/处置） | 登录 |

## 12. 智能查询 `/ai`

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| POST | `/ai/ask` | body `{question}`；三级降级链（缓存回放→LLM→关键词检索）；无 key/超时静默降级，不报错 | `hzm:ai:ask` |

## 13. 系统管理

| 方法 | 路径 | 说明 | 权限 |
|---|---|---|---|
| GET | `/system/user/list` | 用户分页（口令字段置空返回） | `system:user:list` |
| POST | `/system/user` | 新增；口令 8~64 位且至少两类字符；用户名唯一 | `system:user:add` |
| PUT | `/system/user` | 编辑（昵称/状态） | `system:user:edit` |
| DELETE | `/system/user/{ids}` | 删除（多 ID 逗号分隔） | `system:user:remove` |
| GET | `/system/user/options` | 人员下拉；`perm` 可按权限键过滤；无权限时返回 403（前端静默降级） | 登录 |
| GET | `/system/user/{id}/roles` | 用户已指派角色 ID | `system:user:list` |
| PUT | `/system/user/{id}/roles` | 角色全量覆盖；body `{roleIds:[...]}` | `system:user:edit` |
| PUT | `/system/user/{id}/password` | 重置口令（同口令策略） | `system:user:resetPwd` |
| GET | `/system/role/list` | 角色列表 | `system:role:list` |
| POST/PUT/DELETE | `/system/role`、`/system/role/{ids}` | 角色增改删 | `system:role:add/edit/remove` |
| GET | `/system/menu/list` | 菜单树 | `system:menu:list` |
| GET | `/system/operlog/list` | 操作日志分页（含失败与 403 留痕） | `system:operlog:list` |
| GET | `/system/dict/list`、`/system/dict/by-type` | 字典分页 / 按类型取启用项 | 登录 |
| POST/PUT | `/system/dict` | 字典增/改（停用不删改） | `hzm:compat:maint`（同基础数据维护位） |
| POST | `/system/dict/{id}/status` | 停用/启用；body `{status:"0"|"1"}` | 同上 |
| GET | `/biz/notice/list` | 公告分页 | `biz:notice:list` |
| POST/PUT/DELETE | `/biz/notice`、`/biz/notice/{ids}` | 公告增改删 | `biz:notice:add/edit/remove` |
| GET | `/system/notify/list` | 我的站内信分页（含全员广播），`page`/`size` | 登录 |
| GET | `/system/notify/unread-count` | 未读数（顶栏徽标轮询） | 登录 |
| POST | `/system/notify/{id}/read` | 标记已读 | 登录 |
| POST | `/system/notify/read-all` | 全部标记已读 | 登录 |

## 14. 字段校验矩阵（前端 rules ↔ 后端 Validators）

| 表单 | 必填（前端 + 后端） | 长度/范围/格式 |
|---|---|---|
| 台账建档 | 品名、CAS 号、分类、主相容组 | 品名≤100；CAS `\d{2,7}-\d{2}-\d`；分类 0..4；相容组≤8；规格≤50；单位≤16；下限≥0；预警天数 1..365；处置要点≤200 |
| 申购 | 化学品名、分类、导师、数量 | 品名≤100；CAS 同上；数量>0；规格≤50；单位≤16；用途≤200；实验项目≤100 |
| 入库 | 批号、数量、柜位、效期（前端）、复核人；新档草稿另需品名/CAS/分类 | 批号≤32；数量>0；供应商≤100；备案号≤50 |
| 领用 | 化学品、数量、用途 | 数量>0；用途≤200；实验项目≤100 |
| 归还 | 归还批次、归还数量 | 数量>0；说明≤200 |
| 废弃 | 化学品、批次、类型、数量（非过期整批） | 类型 1..3；数量>0；方式≤120；去向≤120；凭证号≤50 |
| 盘点 | 盘点人二 | 范围分类 0..4；实物量≥0；差异说明≤200 |
| 柜位/禁配/字典 | 柜位编码、类型；组 A、组 B；dictType、itemName | 编码≤32 且字母数字连字符；相容组≤8；说明≤200；名称≤50；字典值≤50 |
| 用户/角色 | 用户名、初始口令、状态；角色名称、角色键 | 用户名 2~30 字母数字下划线；口令 8~64 且两类字符；角色键≤30 且 `^[a-zA-Z][\w-]*$` |
| 公告/站内信 | 公告标题、内容 | 标题≤120；内容≤2000 |
| 改密 | 旧密码、新密码 | 新口令同用户口令策略 |

> 前端规则工厂：`web/src/utils/validation.js`（`R.required/requiredPick/positive/range/maxLen/casNo/password/...`）；
> 后端对应实现：`server/.../common/Validators.java`（错误码统一 4001）。两侧文案保持一致，便于排障。

## 15. 客户端能力（无新增接口）

- **Excel 导出**：纯前端生成（SheetJS 写能力），数据来自各列表 `GET */list` 分页接口，
  故无"导出接口"；导出遵循当前筛选条件，最多 5000 行。
- **CSV 导入**：模板下载与文件解析均在浏览器端完成（`web/src/utils/importCsv.js`，零依赖），
  逐行校验通过后**复用 `POST /chem/info`** 提交，故无"导入接口"；
  失败行明细在前端汇总展示，服务端仅以 4001/业务码原样回传原因。
- **站内信**：路由 `/system/notify/*` 见第 13 节；未读数由顶栏每 30s 轮询（`GET /system/notify/unread-count`）。
