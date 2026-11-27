import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const http = axios.create({
  baseURL: '',
  timeout: 10000
})

http.interceptors.request.use(cfg => {
  const token = sessionStorage.getItem('token')
  if (token) {
    cfg.headers.Authorization = 'Bearer ' + token
  }
  return cfg
})

http.interceptors.response.use(
  resp => {
    if (resp.config.responseType === 'blob') {
      return resp.data
    }
    const body = resp.data
    if (body.code !== 0) {
      ElMessage.error(body.message || '操作失败')
      return Promise.reject(body)
    }
    return body.data
  },
  err => {
    const status = err.response?.status
    if (status === 401) {
      sessionStorage.removeItem('token')
      if (router.currentRoute.value.path !== '/login') {
        router.replace('/login')
      }
    }
    ElMessage.error(err.response?.data?.message || '网络错误')
    return Promise.reject(err)
  }
)

export default http
