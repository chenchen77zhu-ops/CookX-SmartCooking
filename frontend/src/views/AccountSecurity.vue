<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>账号与安全</h1><p>管理当前账号资料与账户状态。</p></div></header>
    <main class="subpage-content"><section class="subpage-card account-card">
      <div class="account-summary"><span><el-icon><User /></el-icon></span><div><h2>{{ user.nickname || user.username || '未设置昵称' }}</h2><p>{{ maskedPhone }}</p></div></div>
      <button type="button" class="account-row" @click="router.push({ path: '/profile', query: { edit: '1' } })"><span><el-icon><EditPen /></el-icon></span><div><b>编辑个人资料</b><small>修改昵称、手机号和头像</small></div><el-icon><ArrowRight /></el-icon></button>
      <div class="account-note"><el-icon><Lock /></el-icon><p><b>密码管理</b><span>当前项目尚未提供修改密码接口，因此未展示虚假操作入口。</span></p></div>
      <button type="button" class="delete-account" @click="deleteAccount"><el-icon><Delete /></el-icon>注销账户</button>
    </section><ServerSettings/></main>
  </div>
</template>

<script setup>
import ServerSettings from '../components/ServerSettings.vue'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Delete, EditPen, Lock, User } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { clearSession } from '../services/authSession'
import { authApi } from '@/api/auth'
import '@/assets/profile-pages.css'

const router = useRouter()
const user = ref(JSON.parse(localStorage.getItem('user') || '{}'))
const maskedPhone = computed(() => {
  const phone = String(user.value.phone || '')
  return /^\d{11}$/.test(phone) ? `${phone.slice(0, 3)}****${phone.slice(-4)}` : (phone || '未设置手机号')
})
const deleteAccount = () => {
  ElMessageBox.confirm('注销后个人账号、库存和对话记录将删除，家庭共享记录由家庭保留。请先移交家庭管理员权限。', '风险提示', { confirmButtonText: '确认注销', type: 'danger' }).then(async () => {
    try {
      const response = await authApi.deleteAccount(user.value.id)
      if(response.data.status !== 'success') throw new Error('注销失败')
      clearSession()
      ElMessage.success('账户已注销')
      router.push('/login')
    } catch (error) { ElMessage.error('注销失败') }
  }).catch(() => {})
}
</script>

<style scoped>
.account-card { display: grid; gap: 13px; }
.account-summary { display: flex; align-items: center; gap: 13px; padding-bottom: 17px; border-bottom: var(--cookx-border); }
.account-summary > span { display: grid; width: 50px; height: 50px; border-radius: 16px; background: #eaf2ec; color: var(--cookx-primary); font-size: 22px; place-items: center; }
.account-summary h2 { margin: 0 0 5px; font-size: 18px; }
.account-summary p { margin: 0; color: var(--cookx-text-secondary); font-size: 11px; }
.account-row { display: flex; align-items: center; width: 100%; min-height: 64px; padding: 8px 0; border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.account-row > span { display: grid; width: 40px; height: 40px; margin-right: 11px; border-radius: 13px; background: #fff1e7; color: var(--cookx-accent); place-items: center; }
.account-row > div { display: flex; flex: 1; flex-direction: column; gap: 4px; }
.account-row b { font-size: 13px; }
.account-row small { color: var(--cookx-text-secondary); font-size: 10px; }
.account-note { display: flex; gap: 11px; padding: 14px; border-radius: 14px; background: #f7f7f2; color: var(--cookx-text-secondary); }
.account-note > .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--cookx-primary); }
.account-note p { margin: 0; font-size: 10px; line-height: 1.6; }
.account-note b, .account-note span { display: block; }
.account-note b { color: var(--cookx-text); font-size: 12px; }
.delete-account { min-height: 44px; border: 1px solid rgba(216,74,58,.16); border-radius: 14px; background: #fff6f4; color: var(--cookx-danger); font-weight: 700; cursor: pointer; }
</style>
