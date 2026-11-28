import http from './request'

/* ═══ 化学品台账 ChemInfoController（/chem/info） ═══ */
export const listChemInfo = params => http.get('/chem/info/list', { params })
export const addChemInfo = data => http.post('/chem/info', data)
export const editChemInfo = data => http.put('/chem/info', data)
export const removeChemInfo = ids => http.delete(`/chem/info/${ids}`)
export const chemInfoDetail = id => http.get(`/chem/info/${id}`)
export const casCheck = casNo => http.get('/chem/info/cas-check', { params: { casNo } })
export const setChemInfoStatus = (id, status) => http.post(`/chem/info/${id}/status`, { status })

/* ═══ 申购 PurchaseController（/chem/purchase） ═══ */
export const savePurchase = data => http.post('/chem/purchase', data)
export const submitPurchase = id => http.post(`/chem/purchase/${id}/submit`)
export const listPurchase = params => http.get('/chem/purchase/list', { params })
export const purchaseDetail = id => http.get(`/chem/purchase/${id}`)
export const auditPurchase = (id, data) => http.post(`/chem/purchase/${id}/audit`, data)
export const voidPurchase = id => http.post(`/chem/purchase/${id}/void`)

/* ═══ 入库 StockInController（/chem/stockin） ═══ */
export const listStockIn = params => http.get('/chem/stockin/list', { params })
export const addStockIn = data => http.post('/chem/stockin', data)
export const countersignStockIn = (id, data) => http.post(`/chem/stockin/${id}/countersign`, data || {})
export const rejectStockIn = (id, reason) => http.post(`/chem/stockin/${id}/reject`, { reason })

/* ═══ 领用/归还 RequisitionController（/chem/req） ═══ */
export const submitReq = data => http.post('/chem/req', data)
export const listReq = params => http.get('/chem/req/list', { params })
export const reqDetail = id => http.get(`/chem/req/${id}`)
export const advisorReq = (id, data) => http.post(`/chem/req/${id}/advisor`, data)
export const checkReq = (id, data) => http.post(`/chem/req/${id}/check`, data)
export const dispatchReq = (id, data) => http.put(`/chem/req/${id}/dispatch`, data)
export const returnReq = (id, data) => http.post(`/chem/req/${id}/return`, data)
export const listReqReturns = id => http.get(`/chem/req/${id}/returns`)
export const confirmReturn = rid => http.post(`/chem/req/return/${rid}/confirm`)
export const rejectReturn = (rid, comment) => http.post(`/chem/req/return/${rid}/reject`, { comment })
/** 核销关闭（POST /chem/req/{id}/close——服务层已实现；注意核验后端控制器是否暴露） */
export const closeReq = (id, comment) => http.post(`/chem/req/${id}/close`, { comment })

/* ═══ 批次 BatchController（/chem/batch） ═══ */
export const listBatch = params => http.get('/chem/batch/list', { params })
export const moveBatchCabinet = (id, cabinetId) => http.put(`/chem/batch/${id}/cabinet`, { cabinetId })

/* ═══ 待办 TodoController（/chem/todo） ═══ */
export const getTodo = () => http.get('/chem/todo')

/* ═══ 废弃 WasteController（/chem/waste） ═══ */
export const addWaste = data => http.post('/chem/waste', data)
export const listWaste = params => http.get('/chem/waste/list', { params })
export const wasteDetail = id => http.get(`/chem/waste/${id}`)
export const auditWaste = (id, data) => http.post(`/chem/waste/${id}/audit`, data)

/* ═══ 盘点 CheckController（/chem/check） ═══ */
export const addCheck = data => http.post('/chem/check', data)
export const finishCheck = (id, diffs) => http.post(`/chem/check/${id}/finish`, diffs)
export const adjustCheck = (id, diffId) => http.post(`/chem/check/${id}/adjust`, { diffId })
export const listCheck = params => http.get('/chem/check/list', { params })
export const checkDetail = id => http.get(`/chem/check/${id}`)

/* ═══ 柜位 CabinetController（/chem/cabinet） ═══ */
export const listCabinet = () => http.get('/chem/cabinet')
export const addCabinet = data => http.post('/chem/cabinet', data)
export const editCabinet = data => http.put('/chem/cabinet', data)

/* ═══ 禁配 CompatController（/chem/compat） ═══ */
export const listCompat = () => http.get('/chem/compat')
export const addCompat = data => http.post('/chem/compat', data)
export const removeCompat = (groupA, groupB) => http.delete('/chem/compat', { params: { groupA, groupB } })

/* ═══ 统计 StatsController（/chem/stats） ═══ */
export const statsDashboard = () => http.get('/chem/stats/dashboard')
export const statsLedger = () => http.get('/chem/stats/ledger')
export const statsScreen = () => http.get('/chem/stats/screen')

/* ═══ 智能查询 AiAskController（/ai/ask，防御性 AI 层） ═══ */
export const aiAsk = question => http.post('/ai/ask', { question }, { timeout: 60000 })
