<template>
  <div class="app-layout">
    <!-- 顶部导航栏 -->
    <header class="app-header">
      <div class="header-content">
        <span class="app-title">🍳 SmartCooking</span>
      </div>
    </header>

    <!-- 中间内容滚动区域 -->
    <main class="app-body">
      <!-- 这里的 :is 会根据 currentView 的变化自动切换组件 -->
      <component
        :is="currentView"
        :pending-dish="pendingDish"
        :recipe="currentRecipe"
        @consult-recipe="handleConsultRecipe"
        @clear-pending="clearPendingDish"
        @start-cooking="handleStartCooking"
        @back="handleBack"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, shallowRef, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

// 1. 导入子组件 (确保路径正确)
import ManageFridge from './ManageFridge.vue'
import AiChef from './AiChef.vue'
import CookingMode from './CookingMode.vue'

const route = useRoute()
const componentMap = {
  'Manage': ManageFridge,
  'AiChef': AiChef
}
const getTarget = () => {
  const tab = route.query.tab // 拿到 URL 里的参数
  return componentMap[tab] || ManageFridge // 找不到就默认显示冰箱
}
// 2. 业务状态变量
const currentRecipe = ref(null)
const pendingDish = ref('')

/**
 * ✅ 核心修复逻辑：强力同步函数
 * 负责根据 URL 参数决定当前显示哪个组件
 */
const getActiveComponent = () => {
  const tab = route.query.tab
  if (tab === 'AiChef') return AiChef
  return ManageFridge // 默认显示冰箱管理
}

// 3. ✅ 定义唯一的 currentView 变量，并立即初始化
const currentView = shallowRef(getTarget())

// 4. 页面初始化
onMounted(() => {
  // 此时不需要再调用多余的函数，因为定义时已经初始化过了
})

watch(() => route.query.tab, () => {
  currentView.value = getTarget()
})

// --- 子组件事件监听器 ---

const handleConsultRecipe = (dishName) => {
  pendingDish.value = dishName
  currentView.value = AiChef
}

const clearPendingDish = () => {
  pendingDish.value = ''
}

const handleStartCooking = (recipe) => {
  currentRecipe.value = recipe
  currentView.value = CookingMode
}

const handleBack = () => {
  if (currentView.value === CookingMode) {
    currentView.value = AiChef
  } else {
    currentView.value = ManageFridge
  }
}
</script>

<style scoped>
/* 保持你原来的样式代码不变 */
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-page);
}
.app-header {
  height: 56px;
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 12px rgba(46, 125, 50, 0.15);
  position: relative;
  z-index: 10;
}
.app-title {
  font-size: 20px;
  font-weight: 600;
  color: #fff;
  letter-spacing: 1px;
}
.app-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--bg-page);
}
</style>