import request from '../utils/request'

export const getRecommendations = async () => {
  return request.get('/recommendation/list')
}

export const applyRecommendation = async (recommendation) => {
  return request.post('/recommendation/apply', recommendation)
}

export default {
  getRecommendations,
  applyRecommendation
}
