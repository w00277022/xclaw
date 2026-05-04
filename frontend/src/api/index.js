import axios from 'axios'

const api = axios.create({ baseURL: '/api' })

// XClaw Instance API
export const xclawApi = {
  create: (data) => api.post('/xclaw', data),
  list: () => api.get('/xclaw'),
  get: (id) => api.get(`/xclaw/${id}`),
  start: (id) => api.post(`/xclaw/${id}/start`),
  stop: (id) => api.post(`/xclaw/${id}/stop`),
  delete: (id) => api.delete(`/xclaw/${id}`),
  sync: (id) => api.post(`/xclaw/${id}/sync`),
}

// Chat API
export const chatApi = {
  history: (instanceId) => api.get(`/chat/${instanceId}/history`),
}

export default api
