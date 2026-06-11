import request from '../utils/request'

export const getRuleList = (params) => {
  return request.get('/rule/list', { params })
}

export const getRuleById = (id) => {
  return request.get(`/rule/${id}`)
}

export const createRule = (data) => {
  return request.post('/rule', data)
}

export const updateRule = (data) => {
  return request.put('/rule', data)
}

export const deleteRule = (id) => {
  return request.delete(`/rule/${id}`)
}

export const enableRule = (id) => {
  return request.put(`/rule/${id}/enable`)
}

export const disableRule = (id) => {
  return request.put(`/rule/${id}/disable`)
}

export const testRule = (data) => {
  return request.post('/rule/test', data)
}

export default {
  getRuleList,
  getRuleById,
  createRule,
  updateRule,
  deleteRule,
  enableRule,
  disableRule,
  testRule
}
