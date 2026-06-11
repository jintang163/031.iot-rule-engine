import request from '../utils/request'

export const getRuleList = async (params) => {
  const res = await request.get('/rule/list', { params })
  if (res && res.records !== undefined) {
    return res
  }
  const records = res?.records || res?.list || []
  const total = res?.total || 0
  const current = res?.current || res?.pageNum || 1
  const size = res?.size || res?.pageSize || 10
  const pages = res?.pages || (total > 0 ? Math.ceil(total / size) : 0)
  return { records, total, current, size, pages }
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
