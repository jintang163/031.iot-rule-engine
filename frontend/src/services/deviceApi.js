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

export default {
  getDeviceList,
  getDeviceById,
  createDevice,
  updateDevice,
  deleteDevice,
  controlDevice,
  getOnlineDevices
}
