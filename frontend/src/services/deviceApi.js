import request from '../utils/request'

export const getDeviceList = (params) => {
  return request.get('/device/list', { params })
}

export const getDeviceById = (id) => {
  return request.get(`/device/${id}`)
}

export const createDevice = (data) => {
  return request.post('/device', data)
}

export const updateDevice = (data) => {
  return request.put('/device', data)
}

export const deleteDevice = (id) => {
  return request.delete(`/device/${id}`)
}

export const controlDevice = (deviceId, action, params) => {
  return request.put(`/device/${deviceId}/control`, { action, params })
}

export const getOnlineDevices = () => {
  return request.get('/device/online')
}

export const exportDevices = () => {
  return request.get('/device/export', {
    responseType: 'blob'
  })
}

export const importDevices = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/device/import', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

export const downloadImportTemplate = () => {
  return request.get('/device/import/template', {
    responseType: 'blob'
  })
}

export const startSimulator = (config) => {
  return request.post('/device/simulator/start', config)
}

export const stopSimulator = (deviceId) => {
  return request.post(`/device/simulator/stop/${deviceId}`)
}

export const getSimulatorStatus = (deviceId) => {
  return request.get(`/device/simulator/status/${deviceId}`)
}

export const getAllSimulatorStatus = () => {
  return request.get('/device/simulator/status')
}

export const getSimulatorConfig = (deviceId) => {
  return request.get(`/device/simulator/config/${deviceId}`)
}

export default {
  getDeviceList,
  getDeviceById,
  createDevice,
  updateDevice,
  deleteDevice,
  controlDevice,
  getOnlineDevices,
  exportDevices,
  importDevices,
  downloadImportTemplate,
  startSimulator,
  stopSimulator,
  getSimulatorStatus,
  getAllSimulatorStatus,
  getSimulatorConfig
}
