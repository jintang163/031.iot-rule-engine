import request from '../utils/request'

export const getActionLogList = (params) => {
  return request.get('/action-log/list', { params })
}

export const getStatistics = () => {
  return request.get('/action-log/statistics')
}

export default {
  getActionLogList,
  getStatistics
}
