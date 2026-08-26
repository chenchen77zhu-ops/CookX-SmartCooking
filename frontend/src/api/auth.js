import axios from 'axios'

// ?? axios ??
export const BASE_URL = ''

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { "Content-Type": "application/json" }
})

export default api;

// ?????? API
export const authApi = {
  // 1. ????
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/upload-avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 2. ???????
  sendSmsCode(phone) {
    return api.post('/send-sms-code', null, { params: { phone } })
  },

  // 3. User registration
  register(data) {
    return api.post('/register', data)
  },

  // 4. User login
  login(username, password) {
    return api.post('/login', { username, password })
  },

  // 5. ??????
  getUserInfo(userId) {
    return api.get(`/user/${userId}`)
  },

  // 6. ??????
  updateUserInfo(userId, data) {
    return api.put(`/user/${userId}`, null, { params: data })
  },

  // 7. ????
  deleteAccount(userId) {
    return api.delete(`/user/${userId}`)
  }
}
