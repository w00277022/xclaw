import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/api' })

// JWT token injection
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('xclaw_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 401 auto-redirect to login
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('xclaw_token')
      localStorage.removeItem('xclaw_user')
      if (window.location.pathname !== '/login') {
        ElMessage.warning('登录已过期，请重新登录')
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

// Auth API
export const authApi = {
  login: (username, password) => api.post('/auth/login', { username, password }),
  register: (username, password, displayName) => api.post('/auth/register', { username, password, displayName }),
  me: () => api.get('/auth/me'),
}

// XClaw Instance API
export const xclawApi = {
  create: (data) => api.post('/xclaw', data),
  list: () => api.get('/xclaw'),
  get: (id) => api.get(`/xclaw/${id}`),
  start: (id) => api.post(`/xclaw/${id}/start`),
  stop: (id) => api.post(`/xclaw/${id}/stop`),
  delete: (id) => api.delete(`/xclaw/${id}`),
  sync: (id) => api.post(`/xclaw/${id}/sync`),
  allowedTypes: () => api.get('/xclaw/allowed-types'),
}

// Chat API
export const chatApi = {
  history: (instanceId, sessionKey) => {
    const params = sessionKey ? { sessionKey } : {}
    return api.get(`/chat/${instanceId}/history`, { params })
  },
  deleteMessage: (messageId) => api.delete(`/chat/${messageId}`),
  upload: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/chat/upload', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}

// Approval API
export const approvalApi = {
  list: () => api.get('/approvals'),
  approve: (id) => api.post(`/approvals/${id}/approve`),
  reject: (id, reason) => api.post(`/approvals/${id}/reject`, { reason }),
}

// User API (admin)
export const userApi = {
  list: () => api.get('/users'),
  create: (data) => api.post('/users', data),
  delete: (id) => api.delete(`/users/${id}`),
  updateRole: (id, role) => api.put(`/users/${id}/role`, { role }),
  update: (id, data) => api.put(`/users/${id}`, data),
}

// Helper: get current user from localStorage
export function getCurrentUser() {
  try {
    return JSON.parse(localStorage.getItem('xclaw_user') || 'null')
  } catch { return null }
}

// Helper: is admin
export function isAdmin() {
  const user = getCurrentUser()
  return user?.role === 'ADMIN'
}

export default api
