import { reactive } from 'vue'
import { login as apiLogin, me as apiMe, changePassword as apiPwd, logout as apiLogout } from '../api/auth'

/**
 * 会话仓库：token 持久化 sessionStorage，用户档案（roles/perms/menus）内存保存。
 * 菜单树来自后端 /auth/me 的 menus（按账号角色过滤后的结果），前端不再另抄一份硬编码导航。
 * 按钮级权限判定 hasPerm() 与 25 键种子标识一一对应。
 */
export const auth = reactive({
  token: sessionStorage.getItem('token') || '',
  user: null,
  roles: [],
  perms: [],
  menus: [],
  loaded: false
})

export function hasPerm(key) {
  if (!key) return true
  if (auth.roles.includes('admin')) return true
  return auth.perms.includes(key)
}

/** 申购审批按钮：任一审批节点权限即展示面板，精确守卫由后端 4105 承担 */
export function hasAnyPurchaseAuditPerm() {
  return auth.roles.includes('admin')
    || ['advisor', 'safety', 'college', 'campus'].some(k => auth.perms.includes(`hzm:purchase:audit-${k}`))
}

export async function login(username, password) {
  const data = await apiLogin({ username, password })
  auth.token = data.token
  auth.user = data.user
  auth.roles = data.roles || []
  auth.perms = data.perms || []
  auth.menus = data.menus || []
  auth.loaded = true
  sessionStorage.setItem('token', data.token)
  return data
}

export async function loadProfile() {
  if (!auth.token) return null
  try {
    const data = await apiMe()
    auth.user = data.user
    auth.roles = data.roles || []
    auth.perms = data.perms || []
    auth.menus = data.menus || []
    auth.loaded = true
    return data
  } catch (e) {
    return null
  }
}

/** 自助改密：后端会吊销该账号已签发的全部令牌，本地会话必须同步作废，否则下一个请求就 401 */
export async function changePassword(oldPassword, newPassword) {
  await apiPwd({ oldPassword, newPassword })
  clearSession()
}

function clearSession() {
  auth.token = ''
  auth.user = null
  auth.roles = []
  auth.perms = []
  auth.menus = []
  auth.loaded = false
  sessionStorage.removeItem('token')
}

/** 登出：先注销服务端这一枚令牌（失败不阻断，本地照样清），再清本地会话 */
export async function logout() {
  try {
    await apiLogout()
  } catch (e) {
    // 令牌已过期/网络异常时后端本就没有有效会话，本地清理即可
  }
  clearSession()
}
