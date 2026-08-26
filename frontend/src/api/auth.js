import axios from 'axios'

// 创建 axios 实例
export const BASE_URL = 'https://thermal-armful-surfer.ngrok-free.dev';

const api = axios.create({
  baseURL: `${BASE_URL}/api`,
  timeout: 30000, // ✅ 调大超时时间
  headers: {
    "ngrok-skip-browser-warning": "69420", // ✅ 绕过拦截
    "Content-Type": "application/json"
  }
})

export default api;

// 用户认证相关 API
export const authApi = {
  // 1. 上传头像
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/upload-avatar', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 2. 发送短信验证码
  sendSmsCode(phone) {
    return api.post('/send-sms-code', { phone })
  },

  // 3. 用户注册
  register(nickname, phone, password, sms_code) {
    return api.post('register', { nickname, phone, password, sms_code })
  },

  // 4. 用户登录
  login(username, password) {
    // ✅ 确保这里写的是字符串 '/login'，且后面没有多余的单词
    return api.post('login', { username, password })
  },

  // 5. 获取用户信息
  getUserInfo(userId) {
    return api.get(`/user/${userId}`)
  },

  // 6. 更新用户信息
  updateUserInfo(userId, data) {
    return api.put(`/user/${userId}`, data)
  },

  // 7. 注销账户
  deleteAccount(userId) {
    return api.delete(`/user/${userId}`)
  }
}