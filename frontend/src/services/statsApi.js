import request from '../utils/request'

export const getStatsOverview = (params) => {
  return request.get('/rule-stats/overview', { params })
}

export const exportStatsCsv = (params) => {
  return request.get('/rule-stats/export/csv', {
    params,
    responseType: 'blob'
  })
}

export const exportStatsJson = (params) => {
  return request.get('/rule-stats/export/json', {
    params,
    responseType: 'blob'
  })
}

export const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

export default {
  getStatsOverview,
  exportStatsCsv,
  exportStatsJson,
  downloadBlob
}
