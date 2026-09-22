<template>
  <div class="app-container">
    <CookXSplash :visible="showNativeSplash" />
    <!-- 1. 路由展示区域 -->
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" :key="$route.fullPath + authRevision" />
      </transition>
    </router-view>

    <!-- 2. 底部导航栏 -->
    <nav v-if="showBottomNav" class="bottom-nav" aria-label="主导航">
      <div class="bottom-nav__inner">
        <!-- 首页 Dashboard -->
        <button
          type="button"
          class="nav-item"
          :class="{ active: activeTab === 'Home' }"
          @click="switchTab('Home')"
        >
          <div class="nav-icon-wrapper">
            <el-icon class="nav-icon"><House /></el-icon>
          </div>
          <span class="nav-text">首页</span>
        </button>

        <!-- 冰箱管理 -->
        <button
          type="button"
          class="nav-item"
          :class="{ active: activeTab === 'Manage' }"
          @click="switchTab('Manage')"
        >
          <div class="nav-icon-wrapper">
            <el-icon class="nav-icon"><Box /></el-icon>
          </div>
          <span class="nav-text">冰箱</span>
        </button>

        <!-- AI 厨房 -->
        <button
          type="button"
          class="nav-item"
          :class="{ active: activeTab === 'AiChef' }"
          @click="switchTab('AiChef')"
        >
          <div class="nav-icon-wrapper">
            <el-icon class="nav-icon"><Bowl /></el-icon>
          </div>
          <span class="nav-text">AI 厨房</span>
        </button>

        <!-- 我的 -->
        <button
          type="button"
          class="nav-item"
          :class="{ active: activeTab === 'Profile' }"
          @click="switchTab('Profile')"
        >
          <div class="nav-icon-wrapper">
            <el-icon class="nav-icon"><User /></el-icon>
          </div>
          <span class="nav-text">我的</span>
        </button>
      </div>
    </nav>
  </div>
</template>

<script setup>
import {initializeNotifications} from './services/cookingNotifications.js'
onMounted(initializeNotifications)
import { suspendCookingStores } from './services/cookingStore.js'
import { readUserId } from './services/recognitionDraft.js'
import { watch, computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import { House, Box, Bowl, User } from '@element-plus/icons-vue'
import CookXSplash from '@/components/CookXSplash.vue'

const router = useRouter()
const route = useRoute()
const showNativeSplash = ref(Capacitor.isNativePlatform())
let splashTimer = null
const authRevision=ref(0)
let cookingUser=readUserId()
const syncCookingUser=()=>{const next=readUserId();if(next!==cookingUser){suspendCookingStores();authRevision.value++;window.dispatchEvent(new CustomEvent('cookx:user-changed',{detail:{previous:cookingUser,current:next}}));cookingUser=next}}
watch(()=>route.fullPath,syncCookingUser)
onMounted(()=>window.addEventListener('storage',syncCookingUser))
onBeforeUnmount(()=>window.removeEventListener('storage',syncCookingUser))

onMounted(() => {
  if (showNativeSplash.value) splashTimer = window.setTimeout(() => { showNativeSplash.value = false }, 850)
})

onBeforeUnmount(() => {
  if (splashTimer) window.clearTimeout(splashTimer)
})

// 2. 控制导航栏显示
const showBottomNav = computed(() => {
  const hideNavRoutes = ['Login', 'Register']
  return !hideNavRoutes.includes(route.name)
})

// 3. ✅ 核心修改：统一通过路由状态决定 activeTab
const activeTab = computed(() => {
  if (route.path === '/profile' || route.meta.navTab === 'Profile') return 'Profile'
  if (route.path.includes('/home')) {
    if (route.query.tab === 'Manage') return 'Manage'
    if (route.query.tab === 'AiChef') return 'AiChef'
    return 'Home'
  }
  return 'Home'
})

// 4. ✅ 核心修改：切换逻辑
const switchTab = (tab) => {
  if (tab === 'Profile') {
    router.push('/profile');
  } else if (tab === 'Home') {
    router.push('/home');
  } else {
    // ✅ 关键点：统一跳转到 /home，并带上 tab 参数
    // 这样 URL 会变成 /home?tab=Manage 或 /home?tab=AiChef
    router.push({ path: '/home', query: { tab: tab } });
  }
};
</script>

<style>
:root {
  --cookx-bottom-nav-height: 72px;
}

body {
  margin: 0;
  background-color: var(--cookx-bg);
  font-family: 'PingFang SC', 'Noto Sans SC', 'Roboto', 'Inter', sans-serif;
}

.app-container {
  min-height: 100vh;
  box-sizing: border-box;
  background: var(--cookx-bg);
  padding-bottom: calc(var(--cookx-bottom-nav-height) + env(safe-area-inset-bottom));
}

.bottom-nav {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  min-height: var(--cookx-bottom-nav-height);
  padding-bottom: env(safe-area-inset-bottom);
  border-top: var(--cookx-border, 1px solid rgba(23, 63, 53, 0.08));
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 -6px 24px rgba(28, 48, 40, 0.07);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  z-index: 1000;
}

.bottom-nav__inner {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  width: min(100%, 760px);
  min-height: var(--cookx-bottom-nav-height);
  margin: 0 auto;
}

.nav-item {
  min-width: 0;
  min-height: 44px;
  padding: 7px 4px 6px;
  border: 0;
  background: transparent;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  font: inherit;
  transition: color 0.2s ease, background-color 0.2s ease;
  -webkit-tap-highlight-color: transparent;
}

.nav-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 32px;
  margin-bottom: 3px;
  border-radius: 12px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.nav-icon {
  font-size: 23px;
  color: var(--cookx-text-secondary, #737a75);
  transition: color 0.2s ease, transform 0.2s ease;
}

.nav-text {
  color: var(--cookx-text-secondary, #737a75);
  font-size: 12px;
  font-weight: 500;
  line-height: 1.2;
  white-space: nowrap;
  transition: color 0.2s ease;
}

.nav-item.active .nav-icon-wrapper {
  background: rgba(23, 63, 53, 0.1);
}

.nav-item.active .nav-icon {
  color: var(--cookx-primary, #173f35);
  transform: translateY(-1px);
}

.nav-item.active .nav-text {
  color: var(--cookx-primary, #173f35);
  font-weight: 600;
}

.nav-item:active .nav-icon-wrapper {
  transform: scale(0.95);
}

@media (hover: hover) {
  .nav-item:hover .nav-icon-wrapper {
    background: rgba(23, 63, 53, 0.06);
  }

  .nav-item:hover .nav-icon,
  .nav-item:hover .nav-text {
    color: var(--cookx-primary, #173f35);
  }
}

@media (max-width: 430px) {
  :root {
    --cookx-bottom-nav-height: 68px;
  }

  .nav-item {
    padding-inline: 2px;
  }

  .nav-text {
    font-size: 11px;
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
