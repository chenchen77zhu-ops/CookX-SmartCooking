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
.profile-container { position: relative; z-index: 1; width: min(100%, var(--ck-page-max)); min-height: 100vh; margin: 0 auto; padding: var(--sat) calc(var(--ck-gutter) + var(--sar)) 24px calc(var(--ck-gutter) + var(--sal)); color: var(--ck-text); }
button { font: inherit; }
.profile-topbar { display: flex; align-items: center; justify-content: space-between; min-height: 64px; }
.profile-wordmark span, .profile-wordmark i { display: none; }
.profile-wordmark b { font-size: 30px; font-weight: 700; letter-spacing: -0.4px; }
.notification-bell { display: grid; place-items: center; width: 40px; height: 40px; padding: 0; border: 1px solid var(--ck-glass-border); border-radius: 50%; background: rgba(20, 22, 21, 0.5); color: var(--ck-text); font-size: 20px; }
.notification-bell :deep(.el-badge__content) { border: 0; }

.user-hero { display: grid; grid-template-columns: auto minmax(0, 1fr); grid-template-rows: auto auto; align-items: center; column-gap: 16px; row-gap: 14px; margin-top: 8px; padding: 18px 16px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-xl); background: radial-gradient(120% 100% at 100% 0%, rgba(255, 138, 61, 0.22), transparent 60%), var(--ck-glass); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.avatar-section { position: relative; padding: 0; border: 0; border-radius: 50%; background: none; }
.avatar-section :deep(.el-avatar) { width: 72px !important; height: 72px !important; border: 2px solid rgba(255, 255, 255, 0.2); background: var(--ck-fill-strong); color: var(--ck-text-2); font-size: 30px; }
.avatar-edit-badge { position: absolute; right: -2px; bottom: -2px; display: grid; place-items: center; width: 26px; height: 26px; border: 2px solid #1E2220; border-radius: 50%; background: var(--ck-heat-deep); color: #fff; font-size: 12px; }
.user-info { display: flex; flex-direction: column; min-width: 0; }
.user-eyebrow { color: #FFB27F; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.user-info h1 { overflow: hidden; font-size: 24px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.user-info p { color: var(--ck-text-2); font-size: 13px; }
.user-info small { color: var(--ck-text-3); font-size: 12px; }
.edit-profile-button { grid-column: 1 / -1; display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 42px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 14px; font-weight: 600; }

.profile-content { display: flex; flex-direction: column; gap: 12px; margin-top: 12px; }
.stats-card { display: grid; grid-template-columns: repeat(auto-fit, minmax(0, 1fr)); padding: 14px 4px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.stats-card article { display: flex; flex-direction: column; align-items: center; min-width: 0; padding: 0 6px; text-align: center; }
.stats-card article + article { border-left: 1px solid var(--ck-hairline); }
.stats-card strong { font-family: var(--ck-font-display); font-size: 28px; font-weight: 300; line-height: 1.15; }
.stats-card span { color: var(--ck-text-2); font-size: 12.5px; }
.stats-card small { display: none; }

.sense-card { display: flex; flex-direction: column; gap: 12px; padding: 16px; border: 1px solid rgba(255, 138, 61, 0.25); border-radius: var(--ck-radius-lg); background: linear-gradient(150deg, rgba(120, 52, 18, 0.55), rgba(30, 34, 32, 0.75)); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.sense-copy { display: flex; align-items: center; gap: 12px; }
.sense-icon { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 14px; background: rgba(255, 138, 61, 0.2); color: #FFB27F; font-size: 20px; }
.sense-copy span:not(.sense-icon) { color: #FFB27F; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.sense-copy h2 { font-size: 17px; font-weight: 600; }
.sense-copy p { color: var(--ck-text-2); font-size: 12.5px; }
.sense-status { display: none; }
.sense-card > button { display: inline-flex; align-items: center; justify-content: center; gap: 4px; min-height: 42px; border: 0; border-radius: 999px; background: var(--ck-cream); color: var(--ck-cream-text); font-size: 14px; font-weight: 700; }

.function-card { padding: 4px 0; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); overflow: hidden; -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.section-title { display: none; }
.function-row { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 60px; padding: 10px 16px; border: 0; background: none; color: var(--ck-text); text-align: left; }
.function-row + .function-row { border-top: 1px solid var(--ck-hairline); }
.function-row:active { background: rgba(255, 255, 255, 0.04); }
.row-icon { display: grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; border-radius: 11px; background: rgba(127, 180, 255, 0.14); color: #9CC5FF; font-size: 18px; }
.row-icon.warm { background: var(--ck-heat-soft); color: #FFB27F; }
.row-copy { display: flex; flex-direction: column; min-width: 0; flex: 1 1 auto; }
.row-copy b { font-size: 15px; font-weight: 500; }
.row-copy small { color: var(--ck-text-3); font-size: 12px; }
.row-badge { display: grid; place-items: center; min-width: 20px; height: 20px; padding: 0 6px; border-radius: 10px; background: var(--ck-danger); color: #fff; font-size: 11px; font-weight: 700; }
.row-arrow { color: var(--ck-text-3); font-size: 14px; }

.logout-button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 50px; border: 1px solid rgba(255, 107, 91, 0.25); border-radius: var(--ck-radius-lg); background: rgba(255, 107, 91, 0.08); color: #FF9A8E; font-size: 15px; font-weight: 600; }
.profile-brand-note { margin: 6px 0 0; color: var(--ck-text-3); font-size: 12px; text-align: center; letter-spacing: 2px; }

.notification-content { color: var(--ck-text); }
.message-list { display: flex; flex-direction: column; gap: 10px; padding-top: 6px; }
.message-item { padding: 14px; border: 1px solid var(--ck-glass-border); border-radius: 16px; background: var(--ck-fill); }
.message-item.is-read { opacity: 0.6; }
.message-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.message-title { font-size: 15px; font-weight: 600; }
.message-tag { flex: 0 0 auto; padding: 2px 8px; border-radius: 999px; font-size: 11px; }
.tag-expire { background: var(--ck-warn-soft); color: var(--ck-warn); }
.tag-system { background: rgba(127, 180, 255, 0.15); color: #9CC5FF; }
.message-content { margin-top: 6px; color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }
.message-footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-top: 8px; }
.message-time { color: var(--ck-text-3); font-size: 12px; }
.empty-message { padding: 30px 0; }
.avatar-upload-section { display: flex; flex-direction: column; align-items: flex-start; }
.loading-state { position: relative; z-index: 1; }
</style>
