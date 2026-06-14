import request from '../utils/request'

export const getVersionList = async (ruleId, params = {}) => {
  const res = await request.get(`/rule/version/list/${ruleId}`, { params })
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

export const getVersionById = (id) => {
  return request.get(`/rule/version/${id}`)
}

export const getVersionByNumber = (ruleId, version) => {
  return request.get(`/rule/version/rule/${ruleId}/v/${version}`)
}

export const compareVersions = (ruleId, fromVersion, toVersion) => {
  return request.get(`/rule/version/compare/${ruleId}`, {
    params: { fromVersion, toVersion }
  })
}

export const compareWithCurrent = (ruleId, version) => {
  return request.get(`/rule/version/compare-with-current/${ruleId}`, {
    params: { version }
  })
}

export const rollbackVersion = (data) => {
  return request.post('/rule/version/rollback', data)
}

export const updateVersionComment = (versionId, comment) => {
  return request.put(`/rule/version/${versionId}/comment`, { comment })
}

export const getLatestVersions = (ruleId, limit = 5) => {
  return request.get(`/rule/version/latest/${ruleId}`, {
    params: { limit }
  })
}

export default {
  getVersionList,
  getVersionById,
  getVersionByNumber,
  compareVersions,
  compareWithCurrent,
  rollbackVersion,
  updateVersionComment,
  getLatestVersions
}
