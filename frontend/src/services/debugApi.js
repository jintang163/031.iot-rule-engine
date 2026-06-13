import request from '../utils/request'

export const startDebugSession = (data) => {
  return request.post('/debug/start', data)
}

export const getDebugStatus = (sessionId) => {
  return request.get(`/debug/${sessionId}/status`)
}

export const debugStepNext = (sessionId) => {
  return request.post(`/debug/${sessionId}/step`)
}

export const debugResume = (sessionId) => {
  return request.post(`/debug/${sessionId}/resume`)
}

export const debugPause = (sessionId) => {
  return request.post(`/debug/${sessionId}/pause`)
}

export const debugStop = (sessionId) => {
  return request.post(`/debug/${sessionId}/stop`)
}

export const sandboxTest = (data) => {
  return request.post('/rule/sandbox-test', data)
}

export default {
  startDebugSession,
  getDebugStatus,
  debugStepNext,
  debugResume,
  debugPause,
  debugStop,
  sandboxTest
}
