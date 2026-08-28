<template>
  <div class="profile-subpage">
    <header class="subpage-hero"><div class="subpage-hero-inner"><div class="subpage-topbar"><button class="subpage-back" type="button" @click="router.back()"><el-icon><ArrowLeft /></el-icon></button><span class="subpage-brand">Cook<strong>X</strong></span></div><h1>我的收藏</h1><p>收纳喜欢的菜谱，随时回来查看。</p></div></header>
    <main class="subpage-content"><section class="subpage-card subpage-empty"><span class="subpage-empty-icon"><el-icon><Star /></el-icon></span><h2>还没有收藏菜谱</h2><p>遇到喜欢的菜谱，可以先收藏起来。当前项目尚未接入收藏数据。</p><button class="subpage-primary-button" type="button" @click="router.push({ path: '/home', query: { tab: 'AiChef' } })">去发现菜谱<el-icon><ArrowRight /></el-icon></button></section></main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Star } from '@element-plus/icons-vue'
import '@/assets/profile-pages.css'
const router = useRouter()
</script>
