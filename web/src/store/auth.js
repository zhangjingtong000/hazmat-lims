import { reactive } from 'vue'
import { login as apiLogin, me as apiMe, changePassword as apiPwd } from '../api/auth'

/**
 * 会话仓库：token 持久化 localStorage，用户档案（roles/perms）内存保存。
 * 按钮级权限判定 hasPerm() 与 §5 25 键种子标识一一对应。
 */
export const auth = reactive({
  token: sessionStorage.getItem('token') || '',
  user: null,
  roles: [],
  perms: [],
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
    auth.loaded = true
    return data
  } catch (e) {
    return null
  }
}

export async function changePassword(oldPassword, newPassword) {
  await apiPwd({ oldPassword, newPassword })
}

export function logout() {
  auth.token = ''
  auth.user = null
  auth.roles = []
  auth.perms = []
  auth.loaded = false
  sessionStorage.removeItem('token')
}
