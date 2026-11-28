import http from './request'

/* ═══ 用户 SysUserController（/system/user） ═══ */
export const listUser = params => http.get('/system/user/list', { params })
export const addUser = data => http.post('/system/user', data)
export const editUser = data => http.put('/system/user', data)
export const removeUser = ids => http.delete(`/system/user/${ids}`)
export const resetUserPwd = (id, password) => http.put(`/system/user/${id}/password`, { password })
export const getUserRoles = id => http.get(`/system/user/${id}/roles`)
export const userOptions = perm => http.get('/system/user/options', { params: { perm } })
export const assignUserRoles = (id, roleIds) => http.put(`/system/user/${id}/roles`, { roleIds })

/* ═══ 角色 SysRoleController（/system/role） ═══ */
export const listRole = () => http.get('/system/role/list')
export const addRole = data => http.post('/system/role', data)
export const editRole = data => http.put('/system/role', data)
export const removeRole = ids => http.delete(`/system/role/${ids}`)

/* ═══ 菜单 SysMenuController（/system/menu，只读） ═══ */
export const listMenuTree = () => http.get('/system/menu/list')

/* ═══ 操作日志 OperLogController（/system/operlog） ═══ */
export const listOperLog = params => http.get('/system/operlog/list', { params })

/* ═══ 公告 NoticeController（/biz/notice） ═══ */
export const listNotice = params => http.get('/biz/notice/list', { params })
export const addNotice = data => http.post('/biz/notice', data)
export const editNotice = data => http.put('/biz/notice', data)
export const removeNotice = ids => http.delete(`/biz/notice/${ids}`)

/* ═══ 字典 DictController（/system/dict） ═══ */
export const listDict = params => http.get('/system/dict/list', { params })
export const addDict = data => http.post('/system/dict', data)
export const editDict = data => http.put('/system/dict', data)
export const setDictStatus = (id, status) => http.post(`/system/dict/${id}/status`, { status })

/* ═══ 站内信 NotifyController（/system/notify，主动通知） ═══ */
export const listNotify = params => http.get('/system/notify/list', { params })
export const notifyUnread = () => http.get('/system/notify/unread-count')
export const readNotify = id => http.post(`/system/notify/${id}/read`)
export const readAllNotify = () => http.post('/system/notify/read-all')
