<template>
  <div class="login-container">
    <div class="login-card">
      <!-- LOGO 区域 -->
      <div class="logo-section">
        <div class="logo-icon">🍳</div>
        <h1 class="login-title">CookX</h1>
        <p class="login-subtitle">感知每一度 · 智烹每一步</p>
      </div>

      <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" label-width="0px">
        <el-form-item prop="nickname">
          <!-- 注意：前缀图标要用冒号绑定组件变量 -->
          <el-input
            v-model="loginForm.nickname"
            placeholder="请输入昵称"
            :prefix-icon="User"
            clearable
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            show-password
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button 
            type="primary" 
            :loading="loading"
            @click="handleLogin"
            size="large"
            class="login-button"
          >
            登录
          </el-button>
        </el-form-item>

        <div class="register-link">
          还没有账号？
          <router-link to="/register" class="link">立即注册</router-link>
        </div>
      </el-form><ServerSettings/>
    </div>
  </div>
</template>

<script setup>
import ServerSettings from '../components/ServerSettings.vue'
import { saveSession, clearSession } from '../services/authSession'
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// ✅ 核心修复：必须导入用到的图标组件
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const loginFormRef = ref(null)
const loading = ref(false)

const loginForm = reactive({
  nickname: '',
  password: ''
})

const loginRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少 6 位', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    const valid = await loginFormRef.value.validate()
    if (!valid) return

    loading.value = true

    // 1. 清理旧缓存
    clearSession()

    // 2. 调用登录接口
    // 请确保 authApi.login 内部实现是 api.post('/login', { username, password })
    const response = await authApi.login(loginForm.nickname, loginForm.password)

    if (response.data.status === 'success') {
      // 3. 存储用户信息
      const userData = response.data.user
      saveSession(response.data)

      ElMessage.success(`欢迎回来，${userData.nickname || userData.username}！`)

      // 4. 跳转
      // 此时路由守卫 beforeEach 会检测到 localStorage 有 user，从而允许通过
      router.push('/home')
    } else {
      ElMessage.error(response.data.message || '登录失败，请检查账号密码')
    }
  } catch (error) {
    if (error !== false) { // 排除表单校验失败的 catch
      console.error('登录异常:', error)
      const errorMsg = error.response?.data?.message || '网络连接超时'
      ElMessage.error(errorMsg)
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--cookx-bg);
  position: relative;
  overflow: hidden;
}

/* 装饰性背景元素 */
.login-container::before {
  content: '';
  position: absolute;
  top: -100px;
  right: -100px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(216, 107, 53, 0.10) 0%, transparent 70%);
  border-radius: 50%;
}

.login-container::after {
  content: '';
  position: absolute;
  bottom: -150px;
  left: -150px;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(23, 63, 53, 0.08) 0%, transparent 70%);
  border-radius: 50%;
}

.login-card {
  background: var(--cookx-surface);
  /* 调小内边距：上下从48px减到30px，左右从40px减到24px */
  padding: 30px 24px;
  border: var(--cookx-border);
  border-radius: var(--cookx-radius-large);
  box-shadow: var(--cookx-shadow);
  width: 88%;      /* ✅ 关键：宽度占屏幕88%，不撑满 */
  max-width: 350px; /* ✅ 关键：最大宽度从420px缩小到350px */
  position: relative;
  z-index: 1;
  animation: slideUp 0.5s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.logo-section {
  text-align: center;
  margin-bottom: 24px; /* 间距缩小 */
}

.logo-icon {
  font-size: 48px; /* 图标从64px减小到48px */
  margin-bottom: 10px;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-10px);
  }
}

.login-title {
  color: var(--cookx-primary);
  margin-bottom: 4px;
  font-size: 24px; /* 标题从32px减小到24px */
  font-weight: 600;
}

.login-subtitle {
  color: var(--cookx-text-secondary);
  font-size: 13px; /* 副标题缩小 */
}

:deep(.el-input__wrapper) {
  padding: 8px 12px; /* 输入框高度略微压缩 */
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--cookx-success) inset !important;
  background-color: #fff;
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--cookx-primary) inset !important;
  background-color: #fff;
}

:deep(.el-input__inner) {
  font-size: 15px;
  color: var(--cookx-text);
}

:deep(.el-form-item) {
  margin-bottom: 18px; /* 间距从24px减小到18px */
}

.login-button {
  width: 100%;
  height: 44px; /* 按钮高度从50px减小到44px */
  font-size: 15px;
  letter-spacing: 1px;
}

.login-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(23, 63, 53, 0.22);
}

.login-button:active {
  transform: scale(0.98);
}

.register-link {
  margin-top: 16px; /* 底部链接间距缩小 */
  font-size: 13px;
}

.link {
  color: var(--cookx-primary);
  text-decoration: none;
  margin-left: 5px;
  font-weight: 600;
  transition: color 0.3s ease;
}

.link:hover {
  color: var(--cookx-accent);
  text-decoration: underline;
}

:deep(.el-input__prefix) {
  color: #9CA3AF;
}

:deep(.el-input__prefix-inner) {
  font-size: 18px;
}
</style>
