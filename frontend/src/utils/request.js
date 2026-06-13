import axios from 'axios'
import { message } from 'antd'
import { getToken, removeToken } from '../services/authApi'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

request.interceptors.request.use(
  (config) => {
    config.headers['Content-Type'] = 'application/json'
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && res.code !== undefined) {
      if (res.code === 200) {
        const data = res.data
        if (data && data.records !== undefined && data.total !== undefined) {
          return {
            records: data.records,
            total: data.total,
            current: data.current || 1,
            size: data.size || data.pageSize || 10,
            pages: data.pages || Math.ceil(data.total / (data.size || data.pageSize || 10))
          }
        }
        return data
      } else {
        message.error(res.msg || '请求失败')
        return Promise.reject(new Error(res.msg || '请求失败'))
      }
    }
    return res
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      const res = error.response.data
      const errorMsg = (res && res.msg) || error.message || '网络错误'
      if (status === 401) {
        message.error('登录已过期，请重新登录')
        removeToken()
        if (window.location.pathname !== '/login') {
          window.location.href = '/login'
        }
      } else if (status === 403) {
        message.error(errorMsg || '没有操作权限')
      } else {
        message.error(errorMsg)
      }
    } else if (error.request) {
      message.error('服务器无响应，请稍后重试')
    } else {
      message.error(error.message || '请求错误')
    }
    return Promise.reject(error)
  }
)

export default request
