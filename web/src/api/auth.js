import http from './request'

export const login = data => http.post('/auth/login', data)
export const me = () => http.get('/auth/me')
export const changePassword = data => http.post('/auth/password', data)
export const logout = () => http.post('/auth/logout')
