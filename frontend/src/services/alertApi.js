import request from '../utils/request'

export const getAlertList = (params) => {
  return request.get('/alert/list', { params })
}

export const getAlertStatistics = (params) => {
  return request.get('/alert/statistics', { params })
}

export const getAlertDetail = (id) => {
  return request.get(`/alert/${id}`)
}

export const acknowledgeAlert = (id, acknowledgedBy = 'system') => {
  return request.put(`/alert/${id}/acknowledge`, null, { params: { acknowledgedBy } })
}

export const clearAlert = (id, clearedBy = 'system') => {
  return request.put(`/alert/${id}/clear`, null, { params: { clearedBy } })
}

export const batchAcknowledge = (ids, acknowledgedBy = 'system') => {
  return request.put('/alert/batch-acknowledge', ids, { params: { acknowledgedBy } })
}

export const batchClear = (ids, clearedBy = 'system') => {
  return request.put('/alert/batch-clear', ids, { params: { clearedBy } })
}

export const getNotifyConfigList = () => {
  return request.get('/alert/notify-config/list')
}

export const getNotifyConfig = (id) => {
  return request.get(`/alert/notify-config/${id}`)
}

export const saveNotifyConfig = (data) => {
  return request.post('/alert/notify-config', data)
}

export const updateNotifyConfig = (data) => {
  return request.put('/alert/notify-config', data)
}

export const deleteNotifyConfig = (id) => {
  return request.delete(`/alert/notify-config/${id}`)
}
