<template>
  <div class="register-container">
    <div class="register-card">
      <!-- LOGO 区域 -->
      <div class="logo-section">
        <img class="logo-icon" :src="cookxMark" alt="" />
        <h1 class="register-title">Cook<strong>X</strong></h1>
        <p class="register-subtitle">感知每一度 · 智烹每一步</p>
      </div>

      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0px">
        <el-form-item prop="invitation_code"><el-input v-model="registerForm.invitation_code" placeholder="请输入内测邀请码" /></el-form-item>
        <!-- 昵称 -->
        <el-form-item prop="nickname">
          <el-input
            v-model="registerForm.nickname"
            placeholder="请输入昵称"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>

        <!-- 密码 -->
        <el-form-item prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码（8-64 位）"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <!-- 确认密码 -->
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            :loading="loading"
            @click="handleRegister"
            class="register-button"
          >
            立即注册
          </el-button>
        </el-form-item>

        <div class="login-link">
          已有账号？
          <router-link to="/login" class="link">立即登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import cookxMark from '@/assets/brand/cookx-mark.svg'
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
// ✅ 核心修复：导入用到的图标
import { User, Lock } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'

const router = useRouter()
const registerFormRef = ref(null)
const loading = ref(false)

const registerForm = reactive({
  invitation_code: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

// 自定义验证器：检查两次密码是否一致
const validateConfirmPassword = (rule, value, callback) => {
  if (value !== registerForm.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const registerRules = {
  invitation_code: [{required:true,message:'请输入内测邀请码',trigger:'blur'}],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度在 8-64 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}

const handleRegister = async () => {
  if (!registerFormRef.value) return

  try {
    const valid = await registerFormRef.value.validate()
    if (!valid) return

    loading.value = true
    // ✅ 核心修复：发送注册请求，phone 和 sms_code 传空字符串
    // 注意：这里建议传一个对象，以匹配后端 Body 接收模式
    const response = await authApi.register({
        invitation_code: registerForm.invitation_code,
        nickname: registerForm.nickname,
        phone: '',
        password: registerForm.password,
        sms_code: ''
    })

    if (response.data.status === 'success') {
      ElMessage.success('注册成功，欢迎加入！')
      router.push('/login')
    } else {
      ElMessage.error(response.data.message || '注册失败')
    }
  } catch (error) {
    if (error !== false) {
      console.error('注册错误:', error)
      ElMessage.error('网络错误，请稍后重试')
    }
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-container { position: relative; z-index: 1; display: flex; flex-direction: column; justify-content: flex-end; min-height: 100vh; min-height: 100dvh; padding: calc(var(--sat) + 24px) calc(var(--ck-gutter) + var(--sar)) calc(24px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); }
.register-card { width: min(100%, 440px); margin: 0 auto; }
.logo-section { margin-bottom: 28px; color: var(--ck-text); text-shadow: 0 2px 20px rgba(0, 0, 0, 0.4); }
.logo-icon { display: block; width: 64px; height: 64px; margin-bottom: 18px; padding: 10px; border: 1px solid var(--ck-glass-border); border-radius: 20px; background: rgba(20, 22, 21, 0.55); -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
.register-title { font-family: var(--ck-font-display); font-size: 44px; font-weight: 700; letter-spacing: -1.4px; line-height: 1; }
.register-title strong { color: var(--ck-heat); font-weight: inherit; }
.register-subtitle { margin-top: 10px; color: var(--ck-text-2); font-size: 15px; letter-spacing: 2px; }
.register-card :deep(.el-form) { padding: 20px 16px 8px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-xl); background: rgba(24, 27, 26, 0.72); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.register-card :deep(.el-form-item) { margin-bottom: 16px; }
.register-card :deep(.el-input__wrapper) { min-height: 50px; padding: 0 14px; border-radius: 16px !important; }
.register-card :deep(.el-input__inner) { font-size: 16px; }
.register-button { width: 100%; min-height: 52px !important; font-size: 16px !important; }
.register-link, .login-link { padding: 4px 0 12px; color: var(--ck-text-2); font-size: 14px; text-align: center; }
.link { color: var(--ck-heat); font-weight: 600; text-decoration: none; }
</style>
