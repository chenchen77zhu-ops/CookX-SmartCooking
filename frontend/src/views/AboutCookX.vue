<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>关于 CookX</h1><p>AI 智能烹饪助手</p></div></header>
    <main class="subpage-content"><section class="subpage-card about-card"><div class="about-mark">Cook<strong>X</strong></div><h2>感知每一度 · 智烹每一步</h2><p>CookX 围绕食材识别、智能库存、AI 菜谱、语音烹饪指导与实时温度感知，帮助用户更从容地完成每一餐。</p><dl><div><dt>当前版本</dt><dd>v{{ packageInfo.version }}</dd></div><div><dt>隐私说明</dt><dd>用户数据仅用于 CookX 当前功能。请妥善保管账号信息和服务密钥。</dd></div><div><dt>技术支持</dt><dd>当前项目尚未配置独立反馈渠道。</dd></div></dl></section></main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import packageInfo from '../../package.json'
import '@/assets/profile-pages.css'
const router = useRouter()
</script>

<style scoped>
.about-card { padding: 28px 18px 8px; text-align: center; }
.about-mark { display: inline-block; color: var(--ck-text); font-family: var(--ck-font-display); font-size: 40px; font-weight: 700; letter-spacing: -1.2px; }
.about-mark strong { color: var(--ck-heat); }
.about-card h2 { margin: 12px 0 10px; color: var(--ck-text-2); font-size: 15px; font-weight: 500; letter-spacing: 2px; }
.about-card > p { max-width: 520px; margin: 0 auto; color: var(--ck-text-2); font-size: 13.5px; line-height: 1.8; }
dl { margin: 22px 0 0; text-align: left; }
dl > div { padding: 14px 0; border-top: 1px solid var(--ck-hairline); }
dt { margin-bottom: 4px; color: var(--ck-text); font-size: 14px; font-weight: 600; }
dd { margin: 0; color: var(--ck-text-3); font-size: 13px; line-height: 1.65; }
</style>
