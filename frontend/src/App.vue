<template>
  <div :class="['app-container', { 'has-tabbar': showBottomNav }]">
    <CookXSplash :visible="showNativeSplash" />
    <CkBackdrop :variant="backdrop" />
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <component :is="Component" :key="routeKey" />
      </transition>
    </router-view>

    <nav v-if="showBottomNav" class="tabbar" aria-label="主导航">
      <div class="tabbar__inner">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          :class="['tabbar__item', { active: activeTab === tab.id }]"
          :aria-current="activeTab === tab.id ? 'page' : undefined"
          @click="switchTab(tab.id)"
        >
          <span class="tabbar__icon"><CkIcon :name="tab.icon" :size="23" :stroke="activeTab === tab.id ? 2.1 : 1.7" /></span>
          <span class="tabbar__label">{{ tab.label }}</span>
        </button>
      </div>
    </nav>
  </div>
</template>

<script setup>
import {initializeTemperatureDevice} from './services/temperatureDevice.js'
onMounted(()=>initializeTemperatureDevice().catch(()=>{}))
import {initializeNotifications} from './services/cookingNotifications.js'
onMounted(initializeNotifications)
import { suspendCookingStores } from './services/cookingStore.js'
import { readUserId } from './services/recognitionDraft.js'
import { watch, computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Capacitor } from '@capacitor/core'
import CkIcon from '@/components/ck/CkIcon.vue'
import CkBackdrop from '@/components/ck/CkBackdrop.vue'
import { uiState } from './services/uiState.js'
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

// 首页的冰箱 / 厨房标签共用一个 HomeView 实例，切换时保留待咨询菜名等状态
const routeKey = computed(() => (route.name === 'Home' ? 'home:' : route.fullPath) + authRevision.value)

const tabs = [
  { id: 'Home', label: '首页', icon: 'home' },
  { id: 'Manage', label: '冰箱', icon: 'fridge' },
  { id: 'AiChef', label: '厨房', icon: 'pot' },
  { id: 'Recipes', label: '菜谱', icon: 'book' },
  { id: 'Profile', label: '我的', icon: 'user' }
]

// 只有一级页面显示底栏；二级页面和沉浸式烹饪隐藏底栏
const TAB_ROUTES = ['Home', 'Profile', 'Recipes']
const showBottomNav = computed(() => TAB_ROUTES.includes(route.name) && !uiState.immersive)

const activeTab = computed(() => {
  if (route.name === 'Profile') return 'Profile'
  if (route.name === 'Recipes') return 'Recipes'
  if (route.query.tab === 'Manage') return 'Manage'
  if (route.query.tab === 'AiChef') return 'AiChef'
  return 'Home'
})

const backdrop = computed(() => {
  if (route.name === 'Login' || route.name === 'Register') return 'kitchen'
  if (route.name === 'Home') return route.query.tab === 'Manage' ? 'fresh' : 'kitchen'
  if (route.name === 'CaptureConfirm') return 'fresh'
  return 'warm'
})

const switchTab = (tab) => {
  if (tab === activeTab.value) { window.scrollTo({ top: 0, behavior: 'smooth' }); return }
  if (tab === 'Profile') router.push('/profile')
  else if (tab === 'Recipes') router.push('/recipes')
  else if (tab === 'Home') router.push('/home')
  else router.push({ path: '/home', query: { tab } })
}
</script>

<style>
.app-container {
  position: relative;
  min-height: 100vh;
  min-height: 100dvh;
}
.app-container.has-tabbar { padding-bottom: calc(var(--ck-tabbar-height) + var(--sab) + 8px); }

.tabbar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 1000;
  padding: 0 var(--sar) var(--sab) var(--sal);
  background: var(--ck-tabbar-bg);
  border-top: 1px solid rgba(0, 0, 0, 0.06);
  -webkit-backdrop-filter: saturate(180%) blur(24px);
  backdrop-filter: saturate(180%) blur(24px);
}
.tabbar__inner {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  width: min(100%, var(--ck-page-max));
  height: var(--ck-tabbar-height);
  margin: 0 auto;
  padding: 0 6px;
}
.tabbar__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--ck-tabbar-text);
}
.tabbar__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 30px;
  border-radius: 999px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}
.tabbar__label { font-size: 11px; font-weight: 500; line-height: 1.1; white-space: nowrap; }
.tabbar__item.active { color: #E8641F; }
.tabbar__item.active .tabbar__icon { background: rgba(232, 100, 31, 0.13); }
.tabbar__item.active .tabbar__label { font-weight: 700; }
.tabbar__item:active .tabbar__icon { transform: scale(0.92); }

.page-enter-active, .page-leave-active { transition: opacity 0.16s ease; }
.page-enter-from, .page-leave-to { opacity: 0; }
</style>
