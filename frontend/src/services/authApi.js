import request from '../utils/request'

export const login = (data) => {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export const getCurrentUser = () => {
  return request({
    url: '/auth/me',
    method: 'get'
  })
}

export const logout = () => {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

export const setToken = (token) => {
  localStorage.setItem('iot_token', token)
}

export const getToken = () => {
  return localStorage.getItem('iot_token')
}

export const removeToken = () => {
  localStorage.removeItem('iot_token')
  localStorage.removeItem('iot_user')
}

export const setUser = (user) => {
  localStorage.setItem('iot_user', JSON.stringify(user))
}

export const getUser = () => {
  const u = localStorage.getItem('iot_user')
  return u ? JSON.parse(u) : null
}
