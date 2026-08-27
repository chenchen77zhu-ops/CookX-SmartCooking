<template>
  <div class="profile-container" v-if="user">
    <!-- 用户信息头部 -->
    <div class="user-header" @click="openEdit">
      <div class="avatar-section">
        <el-avatar :size="80" :src="getFullAvatarUrl(user.avatar)" />
        <div class="edit-tip">点击修改</div>
      </div>
      <div class="user-info">
        <h2 class="nickname">{{ user.nickname || '未设置昵称' }}</h2>
        <p class="username">@{{ user.username }}</p>
      </div>
      <!-- 添加消息通知图标 -->
      <div class="notification-bell" @click.stop="openNotificationDrawer">
        <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger">
          <el-icon class="bell-icon"><Bell /></el-icon>
        </el-badge>
      </div>
      <el-icon class="arrow-right"><ArrowRight /></el-icon>
    </div>

    <div class="menu-list">
      <div class="menu-item" @click="openEdit">
        <span class="menu-label">个人信息</span>
        <el-icon class="menu-arrow"><ArrowRight /></el-icon>
      </div>

      <div class="menu-item danger" @click="handleLogout">
        <span class="menu-label">退出登录</span>
        <el-icon class="menu-arrow"><SwitchButton /></el-icon>
      </div>

      <div class="menu-item danger" @click="handleDeleteAccount">
        <span class="menu-label">注销账户</span>
        <el-icon class="menu-arrow"><Delete /></el-icon>
      </div>
    </div>

    <!-- 消息通知抽屉 -->
    <el-drawer
      v-model="showNotificationDrawer"
      title="消息中心"
      direction="btt"
      size="80%"
      :with-header="true"
      class="notification-drawer"
    >
      <div class="notification-content">
        <!-- 分类标签 -->
        <el-tabs v-model="activeTab" @tab-click="handleTabClick" class="notification-tabs">
          <el-tab-pane label="全部" name="all">
            <div class="message-list">
              <div v-for="msg in filteredMessages" :key="msg.id" class="message-item" :class="{ 'is-read': msg.isRead }">
                <div class="message-header">
                  <span class="message-title">{{ msg.title }}</span>
                  <span class="message-tag" :class="msg.type === 'expire' ? 'tag-expire' : 'tag-system'">
                    {{ msg.type === 'expire' ? '食材临期' : '系统通知' }}
                  </span>
                </div>
                <div class="message-content">{{ msg.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ msg.time }}</span>
                  <!-- ✅ 修改点：确保没有 text 属性，并添加 mark-read-btn 类 -->
                  <el-button
                    v-if="!msg.isRead"
                    type="primary"
                    size="small"
                    class="mark-read-btn"
                    @click="markAsRead(msg.id)"
                  >
                    标记已读
                  </el-button>
                </div>
              </div>
              <div v-if="filteredMessages.length === 0" class="empty-message">
                <el-empty description="暂无消息" :image-size="80" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="食材临期" name="expire">
            <div class="message-list">
              <div v-for="msg in expireMessages" :key="msg.id" class="message-item" :class="{ 'is-read': msg.isRead }">
                <div class="message-header">
                  <span class="message-title">{{ msg.title }}</span>
                  <span class="message-tag tag-expire">食材临期</span>
                </div>
                <div class="message-content">{{ msg.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ msg.time }}</span>
                  <el-button v-if="!msg.isRead" text type="primary" size="small" @click="markAsRead(msg.id)">标记已读</el-button>
                </div>
              </div>
              <div v-if="expireMessages.length === 0" class="empty-message">
                <el-empty description="暂无食材临期消息" :image-size="80" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="系统通知" name="system">
            <div class="message-list">
              <div v-for="msg in systemMessages" :key="msg.id" class="message-item" :class="{ 'is-read': msg.isRead }">
                <div class="message-header">
                  <span class="message-title">{{ msg.title }}</span>
                  <span class="message-tag tag-system">系统通知</span>
                </div>
                <div class="message-content">{{ msg.content }}</div>
                <div class="message-footer">
                  <span class="message-time">{{ msg.time }}</span>
                  <el-button v-if="!msg.isRead" text type="primary" size="small" @click="markAsRead(msg.id)">标记已读</el-button>
                </div>
              </div>
              <div v-if="systemMessages.length === 0" class="empty-message">
                <el-empty description="暂无系统通知" :image-size="80" />
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog
      v-model="showEditDialog"
      title="编辑个人信息"
      width="90%"
      :close-on-click-modal="false"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="80px">
        <el-form-item label="头像">
          <div class="avatar-upload-section">
            <el-avatar :size="100" :src="getFullAvatarUrl(editForm.avatar)" />
            <el-upload
              class="avatar-uploader"
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleAvatarChange"
              accept="image/*"
            >
              <el-button type="primary" size="small" style="margin-top: 10px">
                更换图片
              </el-button>
            </el-upload>
          </div>
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" clearable />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" maxlength="11" clearable />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdateProfile">
          保存修改
        </el-button>
      </template>
    </el-dialog>
  </div>

  <div v-else class="loading-state" style="padding: 50px; text-align: center;">
    <el-skeleton :rows="5" animated />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, SwitchButton, Delete, Bell } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'
import axios from 'axios'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'

const router = useRouter()
const showEditDialog = ref(false)
const updating = ref(false)
const editFormRef = ref(null)
const showNotificationDrawer = ref(false)
const activeTab = ref('all')

const user = ref(null)

const messages = ref([])

// 计算未读消息数量
const unreadCount = computed(() => {
  return messages.value.filter(msg => !msg.isRead).length
})

// 筛选消息
const filteredMessages = computed(() => {
  if (activeTab.value === 'all') return messages.value
  if (activeTab.value === 'expire') return expireMessages.value
  if (activeTab.value === 'system') return systemMessages.value
  return messages.value
})

const expireMessages = computed(() => {
  return messages.value.filter(msg => msg.type === 'expire')
})

const systemMessages = computed(() => {
  return messages.value.filter(msg => msg.type === 'system')
})

const markAsRead = async (messageId) => {
  try {
    const res = await axios.post(`${API_BASE_URL}/notifications/read`, null, {
      params: {
        user_id: user.value.id,
        msg_id: messageId
      }
    })
    if (res.data.status === 'success') {
      const msg = messages.value.find(m => m.id === messageId)
      if (msg) msg.isRead = true
      ElMessage.success('已标记已读')
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const openNotificationDrawer = () => {
  showNotificationDrawer.value = true
  fetchNotifications()
}

// 切换标签页
const handleTabClick = (tab) => {
  activeTab.value = tab.paneName
}

const editForm = reactive({
  nickname: '',
  phone: '',
  avatar: ''
})

const editRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [{ pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }]
}

const getFullAvatarUrl = (path) => {
  if (!path) return 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
  if (path.startsWith('http')) return path
  return resolveBackendUrl(path)
}

onMounted(async () => {
  await fetchFreshUserInfo()
  await fetchNotifications()
})

const fetchNotifications = async () => {
  if (!user.value?.id) return
  try {
    const res = await axios.get(`${API_BASE_URL}/notifications`, {
      params: { user_id: user.value.id }
    })
    messages.value = res.data
  } catch (e) {
    console.error("加载消息失败", e)
  }
}

const fetchFreshUserInfo = async () => {
  const localUser = JSON.parse(localStorage.getItem('user') || '{}')
  if (!localUser.id) {
    router.push('/login')
    return
  }

  try {
    const res = await authApi.getUserInfo(localUser.id)
    if (res.data.status === 'success') {
      user.value = res.data.user
      localStorage.setItem('user', JSON.stringify(res.data.user))
    } else {
      ElMessage.error('用户信息已失效，请重新登录')
      handleLogoutDirectly()
    }
  } catch (error) {
    console.error('获取用户信息失败:', error)
    handleLogoutDirectly()
  }
}

const openEdit = () => {
  editForm.nickname = user.value.nickname
  editForm.phone = user.value.phone
  editForm.avatar = user.value.avatar
  showEditDialog.value = true
}

const handleUpdateProfile = async () => {
  if (!editFormRef.value) return
  await editFormRef.value.validate(async (valid) => {
    if (valid) {
      updating.value = true
      try {
        const response = await authApi.updateUserInfo(user.value.id, {
          nickname: editForm.nickname,
          phone: editForm.phone,
          avatar: editForm.avatar
        })

        if (response.data.status === 'success') {
          ElMessage.success('更新成功')
          user.value = response.data.user
          localStorage.setItem('user', JSON.stringify(response.data.user))
          showEditDialog.value = false
        }
      } catch (error) {
        ElMessage.error('更新失败，请稍后重试')
      } finally {
        updating.value = false
      }
    }
  })
}

const handleAvatarChange = async (file) => {
  try {
    const response = await authApi.uploadAvatar(file.raw)
    if (response.data.status === 'success') {
      editForm.avatar = response.data.avatar_url
      ElMessage.success('头像预览已更新，请点击下方保存')
    }
  } catch (error) {
    ElMessage.error('上传失败')
  }
}

const handleLogoutDirectly = () => {
  localStorage.removeItem('user')
  router.push('/login')
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    .then(() => handleLogoutDirectly())
}

const handleDeleteAccount = () => {
  ElMessageBox.confirm('警告：注销后所有冰箱数据和对话记录将永久删除！', '风险提示', {
    confirmButtonText: '确认注销',
    type: 'danger'
  }).then(async () => {
    try {
      await authApi.deleteAccount(user.value.id)
      ElMessage.success('账户已彻底注销')
      handleLogoutDirectly()
    } catch (e) { ElMessage.error('注销失败') }
  })
}
</script>

<style scoped>
.profile-container {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: env(safe-area-inset-bottom);
}

.user-header {
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%);
  padding: 40px 20px 30px;
  display: flex;
  align-items: center;
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.user-header::before {
  content: '';
  position: absolute;
  top: -50px;
  right: -50px;
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
  border-radius: 50%;
}

.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-right: 20px;
  position: relative;
  z-index: 1;
}

:deep(.el-avatar) {
  border: 4px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transition: all 0.3s ease;
}

.user-header:hover :deep(.el-avatar) {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.edit-tip {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.85);
  margin-top: 8px;
  font-weight: 500;
}

.user-info {
  flex: 1;
  color: #fff;
  position: relative;
  z-index: 1;
}

.nickname {
  font-size: 24px;
  margin-bottom: 8px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.username {
  font-size: 14px;
  opacity: 0.9;
  font-weight: 400;
}

.notification-bell {
  margin-right: 12px;
  cursor: pointer;
  position: relative;
  z-index: 2;
}

.bell-icon {
  font-size: 24px;
  color: #fff;
  opacity: 0.9;
  transition: opacity 0.2s;
}

.notification-bell:hover .bell-icon {
  opacity: 1;
}

.arrow-right {
  color: #fff;
  font-size: 20px;
  opacity: 0.8;
}

.menu-list {
  margin-top: 16px;
  background: #fff;
  border-radius: 16px;
  margin-left: 16px;
  margin-right: 16px;
  overflow: hidden;
  box-shadow: var(--shadow-light);
}

.menu-item {
  padding: 20px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
  transition: all 0.2s ease;
  position: relative;
}

.menu-item::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 50%;
  transform: translateY(-50%) scaleX(0);
  width: 4px;
  height: 60%;
  background: var(--primary-green);
  border-radius: 2px;
  transition: transform 0.3s ease;
}

.menu-item:hover::before {
  transform: translateY(-50%) scaleX(1);
}

.menu-item:hover {
  background: #F9FAFB;
}

.menu-item:active {
  background: #F3F4F6;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item.danger .menu-label {
  color: var(--danger-red);
}

.menu-label {
  font-size: 16px;
  color: var(--text-body);
  font-weight: 500;
  padding-left: 20px;
}

.menu-arrow {
  color: var(--text-secondary);
  font-size: 18px;
}

/* 消息抽屉样式 */
.notification-drawer :deep(.el-drawer__header) {
  padding: 20px 20px 0;
  margin-bottom: 0;
  border-bottom: 1px solid #f0f0f0;
}

.notification-drawer :deep(.el-drawer__body) {
  padding: 0;
}

.notification-content {
  padding: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.notification-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.notification-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
  background: #fff;
}

.notification-tabs :deep(.el-tabs__nav-wrap) {
  padding: 12px 0;
}

.notification-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding: 0 16px;
}

.message-list {
  padding: 8px 0 20px;
}

.message-item {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  border: 1px solid #f0f0f0;
  transition: all 0.3s;
  position: relative;
}

.message-item:not(.is-read)::after {
  content: '';
  position: absolute;
  top: 18px;
  left: 6px;
  width: 6px;
  height: 6px;
  background: #f56c6c;
  border-radius: 50%;
}

.message-item:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transform: translateY(-1px);
}

.message-item.is-read {
  opacity: 0.6;
  background: #fcfcfc;
  box-shadow: none;
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
  gap: 8px;
}

.message-title {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.message-tag {
  font-size: 11px;
  padding: 2px 10px;
  border-radius: 20px;
  font-weight: 500;
}

.tag-expire {
  background: #fee9e6;
  color: #c24536;
}

.tag-system {
  background: #e8f4f8;
  color: #2c7da0;
}

.message-content {
  font-size: 14px;
  color: #5b6b82;
  line-height: 1.5;
  margin-bottom: 12px;
}

.message-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.mark-read-btn {
  color: #ffffff !important;
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%) !important;
  border: none !important;
  padding: 4px 12px !important;
  height: 28px !important;
  font-size: 12px !important;
  border-radius: 6px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(46, 125, 50, 0.2);
  cursor: pointer;
  transition: all 0.3s;
}

.mark-read-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
  color: #ffffff !important;
}

.message-footer .el-button {
  padding: 0;
  height: auto;
  font-size: 12px;
}

.message-footer :deep(.el-button.is-text),
.message-footer :deep(.el-button.is-link) {
  padding: 4px 8px;
  height: auto;
  line-height: 1.4;
  font-size: 13px;
  margin: 0;
  display: inline-flex;
  align-items: center;
}

.message-time {
  font-size: 12px;
  color: #99a6bb;
  flex-shrink: 0;
}

.empty-message {
  padding: 40px 0;
  text-align: center;
}

/* 头像上传区域样式 */
.avatar-upload-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
  padding: 20px 0;
}

.avatar-uploader {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.avatar-uploader .el-upload {
  border-radius: 12px;
}

:deep(.el-dialog) {
  border-radius: 20px !important;
}

:deep(.el-dialog__header) {
  padding: 24px 20px;
  border-bottom: 1px solid var(--border-light);
}

:deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: var(--text-title);
}

:deep(.el-dialog__body) {
  padding: 24px 20px;
}

:deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--text-title);
}

:deep(.el-button--primary) {
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%);
}

/* 动画效果 */
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.menu-item {
  animation: fadeIn 0.4s ease forwards;
}

.menu-item:nth-child(1) {
  animation-delay: 0.1s;
}

.menu-item:nth-child(2) {
  animation-delay: 0.2s;
}

.menu-item:nth-child(3) {
  animation-delay: 0.3s;
}
</style>
