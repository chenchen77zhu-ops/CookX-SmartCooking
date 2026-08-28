<template>
  <div class="register-container">
    <div class="register-card">
      <!-- LOGO 区域 -->
      <div class="logo-section">
        <div class="logo-icon">🍳</div>
        <h1 class="register-title">CookX</h1>
        <p class="register-subtitle">感知每一度 · 智烹每一步</p>
      </div>

      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="0px">
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
            placeholder="请输入密码（6-20 位）"
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
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '密码长度在 6-20 个字符', trigger: 'blur' }
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
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: var(--cookx-bg);
  position: relative;
}

.register-card {
  background: var(--cookx-surface);
  /* ✅ 压缩内边距，使卡片更紧凑 */
  padding: 30px 24px;
  border: var(--cookx-border);
  border-radius: var(--cookx-radius-large);
  box-shadow: var(--cookx-shadow);
  /* ✅ 设置宽度百分比及最大宽度，不撑满全屏 */
  width: 88%;
  max-width: 350px;
  z-index: 1;
  animation: slideUp 0.5s ease;
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.logo-section {
  text-align: center;
  margin-bottom: 24px;
}

.logo-icon {
  font-size: 48px;
  margin-bottom: 10px;
}

.register-title {
  color: var(--cookx-primary);
  margin-bottom: 4px;
  font-size: 24px;
  font-weight: 600;
  text-align: center;
}

.register-subtitle {
  color: var(--cookx-text-secondary);
  font-size: 13px;
  text-align: center;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

:deep(.el-input__wrapper) {
  border-radius: var(--cookx-radius-input) !important;
  background-color: var(--cookx-surface);
  padding: 8px 12px;
}

.register-button {
  width: 100%;
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: var(--cookx-radius-button) !important;
  background: var(--cookx-primary);
  border: none;
  margin-top: 10px;
}

.login-link {
  text-align: center;
  margin-top: 20px;
  color: #666;
  font-size: 13px;
}

.link {
  color: var(--cookx-primary);
  text-decoration: none;
  font-weight: 600;
}
</style>
