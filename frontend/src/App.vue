<template>
  <div class="app-container">
    <!-- 1. 路由展示区域 -->
    <router-view v-slot="{ Component }">
      <transition name="fade" mode="out-in">
        <component :is="Component" :key="$route.fullPath" />
      </transition>
    </router-view>

    <!-- 2. 底部导航栏 -->
    <nav v-if="showBottomNav" class="bottom-nav">
      <!-- 冰箱管理 -->
      <div
        class="nav-item"
        :class="{ active: activeTab === 'Manage' }"
        @click="switchTab('Manage')"
      >
        <div class="nav-icon-wrapper">
          <el-icon class="nav-icon"><Box /></el-icon>
        </div>
        <span class="nav-text">冰箱管理</span>
      </div>

      <!-- AI 厨房 -->
      <div
        class="nav-item"
        :class="{ active: activeTab === 'AiChef' }"
        @click="switchTab('AiChef')"
      >
        <div class="nav-icon-wrapper">
          <el-icon class="nav-icon"><Bowl /></el-icon>
        </div>
        <span class="nav-text">AI 厨房</span>
      </div>

      <!-- 我的 -->
      <div
        class="nav-item"
        :class="{ active: activeTab === 'Profile' }"
        @click="switchTab('Profile')"
      >
        <div class="nav-icon-wrapper">
          <el-icon class="nav-icon"><User /></el-icon>
        </div>
        <span class="nav-text">我的</span>
      </div>
    </nav>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Box, Bowl, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

// 2. 控制导航栏显示
const showBottomNav = computed(() => {
  const hideNavRoutes = ['Login', 'Register']
  return !hideNavRoutes.includes(route.name)
})

// 3. ✅ 核心修改：统一通过路由状态决定 activeTab
const activeTab = computed(() => {
  if (route.path === '/profile') return 'Profile'
  if (route.path.includes('/home')) {
    // 如果 URL 里有 tab 参数，就用参数，否则默认 Manage
    return route.query.tab || 'Manage'
  }
  return 'Manage'
})

// 4. ✅ 核心修改：切换逻辑
const switchTab = (tab) => {
  activeTab.value = tab; // 同步底部高亮状态

  if (tab === 'Profile') {
    router.push('/profile');
  } else {
    // ✅ 关键点：统一跳转到 /home，并带上 tab 参数
    // 这样 URL 会变成 /home?tab=Manage 或 /home?tab=AiChef
    router.push({ path: '/home', query: { tab: tab } });
  }
};
</script>

<style>
/* 全局基础样式 */
body {
  margin: 0;
  background-color: var(--bg-page);
  font-family: 'PingFang SC', 'Noto Sans SC', 'Roboto', 'Inter', sans-serif;
}

.app-container {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 70px; /* 为底部导航留出空间 */
}

/* 底部导航样式 */
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 70px;
  background: #fff;
  display: flex;
  border-top: 1px solid var(--border-light);
  padding-bottom: env(safe-area-inset-bottom);
  box-shadow: 0 -2px 12px rgba(46, 125, 50, 0.08);
  z-index: 1000;
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  padding: 8px 0;
}

.nav-icon-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 12px;
  transition: all 0.3s ease;
  margin-bottom: 2px;
}

.nav-icon {
  font-size: 26px;
  color: var(--text-secondary);
  transition: all 0.3s ease;
}

.nav-text {
  font-size: 12px;
  color: var(--text-secondary);
  font-weight: 500;
  transition: all 0.3s ease;
}

.nav-item.active .nav-icon-wrapper {
  background: linear-gradient(135deg, var(--super-light-green) 0%, rgba(232, 245, 233, 0.5) 100%);
}

.nav-item.active .nav-icon {
  color: var(--primary-green);
  transform: scale(1.1);
}

.nav-item.active .nav-text {
  color: var(--primary-green);
  font-weight: 600;
}

.nav-item:active .nav-icon-wrapper {
  transform: scale(0.95);
}

/* 页面切换动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
