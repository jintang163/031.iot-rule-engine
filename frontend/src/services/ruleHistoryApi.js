import request from '../utils/request'

export const getRuleHistoryList = (ruleId, params) => {
  return request.get(`/rule-history/${ruleId}/list`, { params })
}

export const getRuleHistorySnapshot = (historyId) => {
  return request.get(`/rule-history/${historyId}/snapshot`)
}

export const isRuleHistoryEnabled = () => {
  return request.get('/rule-history/enabled')
}

export default {
  getRuleHistoryList,
  getRuleHistorySnapshot,
  isRuleHistoryEnabled
}
