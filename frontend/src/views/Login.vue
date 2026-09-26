<template>
  <div class="login-container ck-dark">
    <div class="login-card">
      <!-- LOGO 区域 -->
      <div class="logo-section">
        <img class="logo-icon" :src="cookxMark" alt="" />
        <h1 class="login-title">Cook<strong>X</strong></h1>
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
import cookxMark from '@/assets/brand/cookx-mark.svg'
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
.login-container { position: relative; z-index: 1; display: flex; flex-direction: column; justify-content: flex-end; min-height: 100vh; min-height: 100dvh; padding: calc(var(--sat) + 24px) calc(var(--ck-gutter) + var(--sar)) calc(24px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); }
.login-card { width: min(100%, 440px); margin: 0 auto; }
.logo-section { margin-bottom: 28px; color: var(--ck-text); text-shadow: 0 2px 20px rgba(0, 0, 0, 0.4); }
.logo-icon { display: block; width: 64px; height: 64px; margin-bottom: 18px; padding: 10px; border: 1px solid var(--ck-glass-border); border-radius: 20px; background: rgba(20, 22, 21, 0.55); -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
.login-title { font-family: var(--ck-font-display); font-size: 44px; font-weight: 700; letter-spacing: -1.4px; line-height: 1; }
.login-title strong { color: var(--ck-heat); font-weight: inherit; }
.login-subtitle { margin-top: 10px; color: var(--ck-text-2); font-size: 15px; letter-spacing: 2px; }
.login-card :deep(.el-form) { padding: 20px 16px 8px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-xl); background: rgba(24, 27, 26, 0.72); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.login-card :deep(.el-form-item) { margin-bottom: 16px; }
.login-card :deep(.el-input__wrapper) { min-height: 50px; padding: 0 14px; border-radius: 16px !important; }
.login-card :deep(.el-input__inner) { font-size: 16px; }
.login-button { width: 100%; min-height: 52px !important; font-size: 16px !important; }
.register-link, .login-link { padding: 4px 0 12px; color: var(--ck-text-2); font-size: 14px; text-align: center; }
.link { color: var(--ck-heat); font-weight: 600; text-decoration: none; }
</style>
