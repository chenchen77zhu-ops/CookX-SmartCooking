<template>
  <div v-if="user" class="profile-container">
    <header class="profile-hero">
      <div class="profile-hero-inner">
        <div class="profile-topbar">
          <div class="profile-wordmark"><span>Cook<strong>X</strong></span><i></i><b>我的</b></div>
          <button type="button" class="notification-bell" aria-label="打开消息中心" @click="openNotificationDrawer">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" type="danger"><el-icon><Bell /></el-icon></el-badge>
          </button>
        </div>

        <div class="user-hero">
          <button type="button" class="avatar-section" aria-label="编辑个人资料" @click="openEdit">
            <PrivateAvatar :size="88" :path="user.avatar" />
            <span class="avatar-edit-badge"><el-icon><EditPen /></el-icon></span>
          </button>
          <div class="user-info">
            <span class="user-eyebrow">COOKX MEMBER</span>
            <h1>{{ displayName }}</h1>
            <p v-if="user.username">@{{ user.username }}</p>
            <small>让烹饪更简单，让生活更美味</small>
          </div>
          <button type="button" class="edit-profile-button" @click="openEdit"><el-icon><EditPen /></el-icon>编辑资料</button>
        </div>
      </div>
    </header>

    <main class="profile-content">
      <section v-if="profileStats.length" class="stats-card">
        <article v-for="stat in profileStats" :key="stat.label"><strong>{{ stat.value }}</strong><span>{{ stat.label }}</span><small>{{ stat.description }}</small></article>
      </section>

      <section class="sense-card">
        <div class="sense-copy">
          <span class="sense-icon"><el-icon><Connection /></el-icon></span>
          <div><span>COOKX SENSE</span><h2>CookX Sense</h2><p>{{ savedTemperatureDevice ? '已保存测温设备，连接状态请在 AI 厨房查看' : '尚未连接测温设备' }}</p></div>
        </div>
        <div class="sense-status"><i></i><span>进入 AI 厨房连接设备</span></div>
        <button type="button" @click="openAiChef">进入 AI 厨房<el-icon><ArrowRight /></el-icon></button>
      </section>

      <section class="function-card">
        <div class="section-title"><div><span>账户服务</span><h2>功能入口</h2></div><small>只展示当前可用功能</small></div>
        <button type="button" class="function-row" @click="openPage('/favorites')">
          <span class="row-icon"><el-icon><Star /></el-icon></span><span class="row-copy"><b>我的收藏</b><small>查看我收藏的菜谱</small></span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="function-row" @click="openPage('/cooking-history')">
          <span class="row-icon warm"><el-icon><Clock /></el-icon></span><span class="row-copy"><b>烹饪记录</b><small>查看真实 AI 菜谱生成记录</small></span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="function-row" @click="openPage('/preferences')">
          <span class="row-icon"><el-icon><Setting /></el-icon></span><span class="row-copy"><b>偏好设置</b><small>口味、辣度与食材禁忌</small></span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="function-row" @click="openPage('/account-security')">
          <span class="row-icon"><el-icon><Lock /></el-icon></span><span class="row-copy"><b>账号与安全</b><small>账号资料与注销管理</small></span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="function-row" @click="openNotificationDrawer">
          <span class="row-icon warm"><el-icon><Bell /></el-icon></span><span class="row-copy"><b>消息中心</b><small>查看食材临期与系统通知</small></span><span v-if="unreadCount" class="row-badge">{{ unreadCount }}</span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
        <button type="button" class="function-row" @click="openPage('/about')">
          <span class="row-icon"><el-icon><InfoFilled /></el-icon></span><span class="row-copy"><b>关于 CookX</b><small>产品介绍、版本与隐私说明</small></span><el-icon class="row-arrow"><ArrowRight /></el-icon>
        </button>
      </section>

      <button type="button" class="logout-button" @click="handleLogout"><el-icon><SwitchButton /></el-icon>退出登录</button>
      <p class="profile-brand-note">CookX · 感知每一度 · 智烹每一步</p>
    </main>

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
            <PrivateAvatar :size="100" :path="editForm.avatar" />
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
import PrivateAvatar from '../components/PrivateAvatar.vue'
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowRight, Bell, Clock, Connection, EditPen, InfoFilled, Lock, Setting, Star, SwitchButton, UserFilled } from '@element-plus/icons-vue'
import { authApi } from '@/api/auth'
import axios from 'axios'
import { logoutSession } from '../services/authSession'
import { API_BASE_URL } from '@/config/backend'

const router = useRouter()
const route = useRoute()
const showEditDialog = ref(false)
const updating = ref(false)
const editFormRef = ref(null)
const showNotificationDrawer = ref(false)
const activeTab = ref('all')
const recipeHistoryCount = ref(0)
const historyLoaded = ref(false)
const notificationsLoaded = ref(false)
const savedTemperatureDevice = ref(localStorage.getItem('temperatureDeviceAddress') || '')

const user = ref(null)

const messages = ref([])

// 计算未读消息数量
const unreadCount = computed(() => {
  return messages.value.filter(msg => !msg.isRead).length
})

const displayName = computed(() => user.value?.nickname || user.value?.username || '未设置昵称')
const usageDays = computed(() => {
  if (!user.value?.created_at) return null
  const createdAt = new Date(String(user.value.created_at).replace(' ', 'T'))
  if (Number.isNaN(createdAt.getTime())) return null
  return Math.max(1, Math.floor((Date.now() - createdAt.getTime()) / 86400000) + 1)
})
const profileStats = computed(() => [
  historyLoaded.value ? { label: '菜谱记录', value: recipeHistoryCount.value, description: '真实 AI 菜谱' } : null,
  notificationsLoaded.value ? { label: '未读消息', value: unreadCount.value, description: '待查看提醒' } : null,
  usageDays.value !== null ? { label: '使用天数', value: usageDays.value, description: '自注册起' } : null
].filter(Boolean))

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

onMounted(async () => {
  await fetchFreshUserInfo()
  if (!user.value?.id) return
  await Promise.all([fetchNotifications(), fetchRecipeStats()])
  if (route.query.edit === '1') openEdit()
})

const fetchNotifications = async () => {
  if (!user.value?.id) return
  try {
    const res = await axios.get(`${API_BASE_URL}/notifications`, {
      params: { user_id: user.value.id }
    })
    messages.value = Array.isArray(res.data) ? res.data : []
    notificationsLoaded.value = true
  } catch (e) {
    console.error("加载消息失败", e)
  }
}

const fetchRecipeStats = async () => {
  if (!user.value?.id) return
  try {
    const res = await axios.get(`${API_BASE_URL}/chat-history`, {
      params: { user_id: user.value.id }
    })
    const history = Array.isArray(res.data) ? res.data : []
    recipeHistoryCount.value = history.filter(item => item?.recipe?.steps?.length).length
    historyLoaded.value = true
  } catch (error) {
    console.error('加载菜谱记录失败', error)
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
      user.value = { ...localUser, ...res.data.user }
      localStorage.setItem('user', JSON.stringify(user.value))
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

const openAiChef = () => {
  router.push({ path: '/home', query: { tab: 'AiChef' } })
}

const openPage = (path) => router.push(path)

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

const handleLogoutDirectly = async () => {
  try { await logoutSession(); router.push('/login') }
  catch { ElMessage.error('退出未确认，请恢复网络后重试') }
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', { type: 'warning' })
    .then(() => handleLogoutDirectly())
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

/* CookX 我的 */
.profile-container {
  min-height: calc(100vh - 70px);
  padding: 0 0 calc(98px + env(safe-area-inset-bottom));
  overflow-x: hidden;
  background: var(--cookx-bg);
  color: var(--cookx-text);
}
.profile-hero {
  color: #fff;
  background: radial-gradient(circle at 88% 10%, rgba(77,139,105,.2), transparent 31%), linear-gradient(145deg, #092a22, var(--cookx-primary-dark));
}
.profile-hero-inner, .profile-content { width: min(var(--cookx-settings-max), 100%); margin: 0 auto; box-sizing: border-box; }
.profile-hero-inner { padding: 22px 20px 42px; }
.profile-topbar, .profile-wordmark, .user-hero, .sense-copy, .sense-status, .section-title,
.function-row, .row-copy, .logout-button { display: flex; align-items: center; }
.profile-topbar { justify-content: space-between; }
.profile-wordmark { gap: 13px; }
.profile-wordmark > span { font-size: 29px; font-weight: 800; letter-spacing: -.8px; }
.profile-wordmark strong { color: var(--cookx-accent); }
.profile-wordmark > i { width: 1px; height: 25px; background: rgba(255,255,255,.22); }
.profile-wordmark > b { font-size: 18px; }
.notification-bell { display: grid; width: 43px; height: 43px; padding: 0; border: 1px solid rgba(255,255,255,.16); border-radius: 14px; background: rgba(255,255,255,.06); color: #fff; font-size: 20px; cursor: pointer; place-items: center; }
.user-hero { gap: 20px; margin-top: 34px; }
.avatar-section { position: relative; flex: 0 0 auto; padding: 0; border: 0; background: transparent; cursor: pointer; }
.avatar-section :deep(.el-avatar) { border: 3px solid rgba(255,255,255,.28); background: rgba(255,255,255,.12); color: #fff; font-size: 36px; box-shadow: 0 12px 30px rgba(0,0,0,.2); }
.avatar-section > .avatar-edit-badge { position: absolute; right: -2px; bottom: 2px; display: grid; width: 29px; height: 29px; border: 3px solid var(--cookx-primary-dark); border-radius: 10px; background: var(--cookx-accent); color: #fff; font-size: 13px; place-items: center; }
.user-info { flex: 1; min-width: 0; }
.user-eyebrow { color: var(--cookx-gold); font-size: 9px; font-weight: 800; letter-spacing: 1.3px; }
.user-info h1 { overflow: hidden; margin: 6px 0 4px; font-size: clamp(24px, 5vw, 34px); text-overflow: ellipsis; white-space: nowrap; }
.user-info p { margin: 0 0 9px; color: rgba(255,255,255,.6); font-size: 12px; }
.user-info small { color: rgba(255,255,255,.78); font-size: 12px; }
.edit-profile-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 42px; padding: 0 15px; border: 1px solid rgba(255,255,255,.2); border-radius: 14px; background: rgba(255,255,255,.07); color: #fff; font: inherit; font-size: 12px; font-weight: 650; cursor: pointer; }
.profile-content { position: relative; z-index: 2; padding: 0 18px; transform: translateY(-20px); }
.stats-card { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); border: var(--cookx-border); border-radius: var(--cookx-radius-large); background: var(--cookx-surface); box-shadow: var(--cookx-shadow); }
.stats-card article { position: relative; padding: 20px 16px; text-align: center; }
.stats-card article + article::before { position: absolute; top: 23%; bottom: 23%; left: 0; width: 1px; background: rgba(23,63,53,.08); content: ''; }
.stats-card strong { display: block; color: var(--cookx-primary-dark); font-size: 28px; line-height: 1; }
.stats-card span { display: block; margin-top: 8px; font-size: 12px; font-weight: 700; }
.stats-card small { display: block; margin-top: 3px; color: var(--cookx-text-secondary); font-size: 9px; }
.sense-card { display: grid; grid-template-columns: 1fr auto; gap: 15px 22px; margin-top: 17px; padding: 21px; border: 1px solid rgba(255,255,255,.12); border-radius: var(--cookx-radius-large); background: linear-gradient(145deg, var(--cookx-primary-dark), var(--cookx-primary)); color: #fff; box-shadow: 0 12px 30px rgba(16,46,39,.16); }
.sense-copy { gap: 14px; }
.sense-icon { display: grid; flex: 0 0 48px; width: 48px; height: 48px; border-radius: 15px; background: rgba(255,255,255,.1); color: var(--cookx-gold); font-size: 23px; place-items: center; }
.sense-copy > div > span { color: var(--cookx-gold); font-size: 9px; font-weight: 800; letter-spacing: 1.1px; }
.sense-copy h2 { margin: 3px 0 4px; font-size: 19px; }
.sense-copy p { margin: 0; color: rgba(255,255,255,.67); font-size: 10px; line-height: 1.5; }
.sense-card > button { grid-row: span 2; align-self: center; display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 43px; padding: 0 16px; border: 1px solid rgba(255,255,255,.18); border-radius: 14px; background: rgba(255,255,255,.09); color: #fff; font-weight: 700; cursor: pointer; }
.sense-status { gap: 7px; grid-column: 1; color: rgba(255,255,255,.72); font-size: 10px; }
.sense-status i { width: 7px; height: 7px; border-radius: 50%; background: #c3cac6; }
.function-card { margin-top: 17px; padding: 20px; border: var(--cookx-border); border-radius: var(--cookx-radius-large); background: var(--cookx-surface); box-shadow: var(--cookx-shadow); }
.section-title { justify-content: space-between; gap: 12px; margin-bottom: 12px; }
.section-title > div > span { color: var(--cookx-accent); font-size: 9px; font-weight: 800; letter-spacing: 1.1px; }
.section-title h2 { margin: 3px 0 0; font-size: 19px; }
.section-title > small { color: var(--cookx-text-secondary); font-size: 10px; }
.function-row { width: 100%; min-height: 68px; padding: 9px 5px; border: 0; border-top: 1px solid rgba(23,63,53,.07); background: transparent; color: inherit; font: inherit; text-align: left; cursor: pointer; }
.row-icon { display: grid; flex: 0 0 41px; width: 41px; height: 41px; margin-right: 13px; border-radius: 13px; background: #eaf2ec; color: var(--cookx-primary); font-size: 18px; place-items: center; }
.row-icon.warm { background: #fff2e8; color: var(--cookx-accent); }
.row-icon.danger { background: #fff0ee; color: var(--cookx-danger); }
.row-copy { flex: 1; align-items: flex-start; flex-direction: column; gap: 4px; min-width: 0; }
.row-copy b { font-size: 14px; }
.row-copy small { overflow: hidden; width: 100%; color: var(--cookx-text-secondary); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.row-badge { min-width: 18px; margin-right: 8px; padding: 3px 6px; border-radius: 999px; background: var(--cookx-accent); color: #fff; font-size: 9px; text-align: center; }
.row-arrow { color: #a2aaa5; }
.danger-row .row-copy b { color: var(--cookx-danger); }
.logout-button { justify-content: center; gap: 7px; width: 100%; min-height: 47px; margin-top: 16px; border: 1px solid rgba(216,74,58,.14); border-radius: 15px; background: rgba(255,255,255,.7); color: var(--cookx-danger); font: inherit; font-size: 12px; font-weight: 700; cursor: pointer; }
.profile-brand-note { margin: 18px 0 0; color: #9ba39e; font-size: 9px; letter-spacing: .6px; text-align: center; }
.loading-state { min-height: calc(100vh - 70px); box-sizing: border-box; background: var(--cookx-bg); }

.notification-drawer :deep(.el-drawer) { border-radius: 24px 24px 0 0; background: var(--cookx-bg); }
.notification-tabs :deep(.el-tabs__active-bar) { background: var(--cookx-primary); }
.notification-tabs :deep(.el-tabs__item.is-active) { color: var(--cookx-primary); }
.notification-tabs :deep(.el-tabs__item:hover) { color: var(--cookx-primary); }

@media (max-width: 767px) {
  .profile-hero-inner { padding: 17px var(--cookx-page-gutter-mobile) 38px; }
  .profile-wordmark > span { font-size: 25px; }
  .profile-wordmark > b { font-size: 15px; }
  .user-hero { align-items: flex-start; gap: 15px; margin-top: 27px; }
  .avatar-section :deep(.el-avatar) { width: 74px !important; height: 74px !important; font-size: 29px; }
  .user-info h1 { font-size: 24px; }
  .user-info small { display: block; max-width: 210px; line-height: 1.5; }
  .edit-profile-button { min-width: 44px; width: 44px; padding: 0; }
  .edit-profile-button .el-icon { font-size: 17px; }
  .edit-profile-button { font-size: 0; }
  .profile-content { padding: 0 var(--cookx-page-gutter-mobile); }
  .stats-card article { padding: 17px 8px; }
  .stats-card strong { font-size: 24px; }
  .sense-card { grid-template-columns: 1fr; padding: 18px; }
  .sense-card > button { grid-row: auto; width: 100%; }
  .sense-status { grid-column: auto; }
  .function-card { padding: 17px 14px; }
  .section-title > small { display: none; }
  .function-row { min-height: 66px; }
}

@media (max-width: 390px) {
  .profile-wordmark > i { display: none; }
  .user-hero { flex-wrap: wrap; }
  .edit-profile-button { position: absolute; right: 15px; bottom: 37px; }
  .stats-card { grid-template-columns: repeat(auto-fit, minmax(90px, 1fr)); }
  .stats-card article { padding: 16px 5px; }
  .stats-card small { display: none; }
}
</style>
