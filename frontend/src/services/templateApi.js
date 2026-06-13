import request from '../utils/request'

export const getTemplateList = async (params) => {
  const res = await request.get('/template/list', { params })
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

export const getTemplateById = (id) => {
  return request.get(`/template/${id}`)
}

export const createTemplate = (data) => {
  return request.post('/template', data)
}

export const updateTemplate = (data) => {
  return request.put('/template', data)
}

export const deleteTemplate = (id) => {
  return request.delete(`/template/${id}`)
}

export const applyTemplate = (data) => {
  return request.post('/template/apply', data)
}

export const enableTemplate = (id) => {
  return request.put(`/template/${id}/enable`)
}

export const disableTemplate = (id) => {
  return request.put(`/template/${id}/disable`)
}

export const reviewTemplate = (id, reviewStatus, reviewerId, remark) => {
  return request.put(`/template/${id}/review`, null, {
    params: { reviewStatus, reviewerId, remark }
  })
}

export const getTemplatesByCategory = (category) => {
  return request.get(`/template/category/${category}`)
}

export const saveRuleAsTemplate = (ruleId, templateName, templateDescription, authorName, teamId, authorId) => {
  return request.post('/template/save-from-rule', null, {
    params: { ruleId, templateName, templateDescription, authorName, teamId, authorId }
  })
}

export default {
  getTemplateList,
  getTemplateById,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  applyTemplate,
  enableTemplate,
  disableTemplate,
  reviewTemplate,
  getTemplatesByCategory,
  saveRuleAsTemplate
}
