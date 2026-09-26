<template>
  <div class="home-layout">
    <component
      :is="currentView"
      v-if="currentView"
      :mode="route.query.tab"
      :pending-dish="pendingDish"
      :recipe="currentRecipe"
      @consult-recipe="handleConsultRecipe"
      @clear-pending="clearPendingDish"
      @start-cooking="handleStartCooking"
      @back="handleBack"
    />

    <main v-else class="ck-page home">
      <header class="ck-head">
        <div class="ck-wordmark" aria-label="CookX">Cook<b>X</b></div>
        <button class="ck-icon-btn" type="button" aria-label="查看个人消息" @click="router.push('/profile')"><CkIcon name="bell" :size="24" /><span class="dot"></span></button>
      </header>
      <div class="ck-title">
        <h1>{{ greeting }}{{ userName ? `，${userName}` : '' }}</h1>
        <p>今天想吃点什么？</p>
      </div>

      <button type="button" class="home-sense" aria-label="打开实时厨房" @click="openAiChef"><CkSenseCard /></button>

      <section class="ck-card today">
        <div class="ck-section-title"><span>今日推荐</span><button type="button" class="ck-link" @click="openRecipes">查看全部 <CkIcon name="chevron-right" :size="14" /></button></div>
        <button type="button" class="today__row" @click="openRecipes">
          <img :src="heroDishImage" alt="彩椒西兰花炒鸡胸肉示意图" />
          <span class="today__copy">
            <b>用冰箱食材做一道菜</b>
            <span class="today__meta"><span><CkIcon name="clock" :size="15" />约 15 分钟</span><span><CkIcon name="sparkle" :size="15" />AI 智能推荐</span></span>
          </span>
          <CkIcon name="chevron-right" :size="18" class="today__chev" />
        </button>
      </section>

      <button type="button" :class="['expiry', { 'is-error': inventoryError }]" @click="inventoryError ? fetchInventory() : openInventory()">
        <span class="expiry__icon"><CkIcon :name="inventoryError ? 'refresh' : 'leaf'" :size="22" /></span>
        <span class="expiry__copy">
          <template v-if="inventoryLoading"><b>临期食材提醒</b><small>正在同步冰箱状态…</small></template>
          <template v-else-if="inventoryError"><b>暂时无法读取库存</b><small>点按重新加载</small></template>
          <template v-else-if="!inventory.length"><b>冰箱还是空的</b><small>去识别食材，CookX 才能为你推荐菜谱</small></template>
          <template v-else-if="expiringNames.length"><b>临期食材提醒</b><small><em>{{ expiringNames.join('、') }}</em> 需要尽快食用</small></template>
          <template v-else-if="freshness.status === 'error'"><b>鲜度暂不可用</b><small>进入冰箱重试评估</small></template>
          <template v-else><b>查看评估依据</b><small>未显示临期项不代表食品安全，请核对日期与储存条件</small></template>
        </span>
        <CkIcon name="chevron-right" :size="18" class="expiry__chev" />
      </button>

      <section class="services" aria-label="常用服务">
        <div class="ck-section-title"><span>常用服务</span></div>
        <div class="services__row">
          <button v-for="item in features" :key="item.path" type="button" class="service" @click="router.push(item.path)">
            <span class="service__icon" :style="{ color: item.color, background: item.bg }"><CkIcon :name="item.icon" :size="22" /></span>
            <span class="service__label">{{ item.label }}</span>
          </button>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { getInventoryFreshness } from '../api/freshness.js'
import { createFreshnessLoader, emptyFreshness } from '../services/inventoryFreshness.js'
import { readUserId } from '../services/recognitionDraft.js'
import { computed, onMounted, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import CkIcon from '@/components/ck/CkIcon.vue'
import CkSenseCard from '@/components/ck/CkSenseCard.vue'
import { API_BASE_URL } from '@/config/backend'
import heroDishImage from '@/assets/images/home-hero-dish.webp'
import ManageFridge from './ManageFridge.vue'
import AiChef from './AiChef.vue'
import CookingMode from './CookingMode.vue'

const route = useRoute()
const router = useRouter()
const componentMap = { Manage: ManageFridge, AiChef, Recipes: AiChef }
const getTarget = () => componentMap[route.query.tab] || null

const currentView = shallowRef(getTarget())
const currentRecipe = ref(null)
const pendingDish = ref(typeof route.query.dish === 'string' ? route.query.dish : '')
watch(() => route.query.dish, dish => { if (typeof dish === 'string' && dish) pendingDish.value = dish })
const inventory = ref([])
const inventoryLoading = ref(false)
const inventoryError = ref(false)
const freshness = ref(emptyFreshness())
const freshnessLoader = createFreshnessLoader(getInventoryFreshness, value => { freshness.value = value })
let inventoryVersion = 0
let lastUserId = null
const readUser = () => { try { return JSON.parse(localStorage.getItem('user') || '{}') || {} } catch { return {} } }
const userInfo = ref(readUser())
const userName = computed(() => userInfo.value.nickname || userInfo.value.username || '')

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})



const inventoryKindCount = computed(() => new Set(
  inventory.value.map(item => String(item.name || '').trim().toLowerCase()).filter(Boolean)
).size)
const expiringKinds = computed(() => {
  const kinds = new Map()
  inventory.value.forEach(item => {
    const key = String(item.name || '').trim().toLowerCase()
    const detail = freshness.value.items[item.id]
    if (key && detail && !detail.expired && (detail.critical || detail.expiring_soon) && !kinds.has(key)) kinds.set(key, item)
  })
  return [...kinds.values()]
})
const expiringKindCount = computed(() => freshness.value.status === 'success' ? expiringKinds.value.length : '—')
const expiringNames = computed(() => expiringKinds.value.slice(0, 3).map(item => item.name))

const fetchInventory = async () => {
  const userId = readUserId(), version = ++inventoryVersion
  freshnessLoader.reset()
  if (userId !== lastUserId) inventory.value = []
  lastUserId = userId
  if (!userId) { inventoryLoading.value = false; return }
  inventoryLoading.value = true; inventoryError.value = false
  try {
    const response = await axios.get(`${API_BASE_URL}/inventory`, { params: { user_id: userId }, timeout:15000 })
    if (version !== inventoryVersion || readUserId() !== userId) return
    if (!Array.isArray(response.data)) throw new Error('库存响应异常')
    inventory.value = response.data
    freshnessLoader.load(userId, inventory.value)
  } catch { if (version === inventoryVersion) inventoryError.value = true }
  finally { if (version === inventoryVersion) inventoryLoading.value = false }
}
const syncHomeUser = () => { userInfo.value = readUser(); if (readUserId() !== lastUserId && !currentView.value) fetchInventory() }
onBeforeUnmount(() => { inventoryVersion++; freshnessLoader.dispose(); window.removeEventListener('storage', syncHomeUser); document.removeEventListener('visibilitychange', syncHomeUser) })

const openInventory = () => router.push({ path: '/home', query: { tab: 'Manage' } })
const openAiChef = () => router.push({ path: '/home', query: { tab: 'AiChef' } })

onMounted(() => {
  window.addEventListener('storage', syncHomeUser)
  document.addEventListener('visibilitychange', syncHomeUser)
  if (!currentView.value) fetchInventory()
})

watch(() => route.query.tab, () => {
  currentView.value = getTarget()
  if (!currentView.value) fetchInventory()
})

const handleConsultRecipe = (dishName) => {
  pendingDish.value = dishName
  router.push({ path: '/home', query: { tab: 'Recipes' } })
}
const clearPendingDish = () => { pendingDish.value = '' }
const handleStartCooking = (recipe) => {
  currentRecipe.value = recipe
  currentView.value = CookingMode
}
const handleBack = () => {
  if (currentView.value === CookingMode) currentView.value = AiChef
  else router.push({ path: '/home', query: { tab: 'Manage' } })
}

const openRecipes = () => router.push({ path: '/home', query: { tab: 'Recipes' } })

const features = [
  { label: '家庭冰箱', path: '/household', icon: 'users', color: '#2E9A5C', bg: 'rgba(46,154,92,.13)' },
  { label: '共同采购', path: '/shopping', icon: 'cart', color: '#EE7A2B', bg: 'rgba(238,122,43,.13)' },
  { label: '菜谱复刻', path: '/recipes', icon: 'book', color: '#3F7BE0', bg: 'rgba(63,123,224,.12)' },
  { label: '一起晒菜', path: '/community', icon: 'image', color: '#D9467A', bg: 'rgba(217,70,122,.12)' },
  { label: '剩菜改造', path: '/leftovers', icon: 'recycle', color: '#2E9A5C', bg: 'rgba(46,154,92,.13)' },
  { label: '厨艺成长', path: '/growth', icon: 'chart', color: '#7A5CD6', bg: 'rgba(122,92,214,.12)' },
  { label: '七日菜单', path: '/menus', icon: 'calendar', color: '#D08A00', bg: 'rgba(208,138,0,.13)' },
  { label: '偏好学习', path: '/learning', icon: 'sparkle', color: '#3F7BE0', bg: 'rgba(63,123,224,.12)' }
]
const consumptionChanged=event=>{if(event.detail?.user===readUserId())fetchInventory()}
onMounted(()=>window.addEventListener('cookx:inventory-changed',consumptionChanged))
onBeforeUnmount(()=>window.removeEventListener('cookx:inventory-changed',consumptionChanged))
</script>

<style scoped>
.home-layout { position: relative; z-index: 1; min-height: 100vh; }
.home { display: flex; flex-direction: column; gap: 14px; padding-bottom: 20px; }
.ck-title { margin: 2px 0 4px; }
.home-sense { display: block; width: 100%; padding: 0; border: 0; background: none; text-align: left; border-radius: 26px; box-shadow: 0 18px 40px rgba(30, 20, 10, 0.18); }
.home-sense:active { transform: scale(0.99); }

.today { padding: 16px; }
.today .ck-section-title { margin-bottom: 12px; }
.today__row { display: flex; align-items: center; gap: 14px; width: 100%; padding: 0; border: 0; background: none; color: var(--ck-text); text-align: left; }
.today__row img { width: 108px; height: 76px; flex: 0 0 108px; border-radius: 14px; object-fit: cover; object-position: 70% 50%; }
.today__copy { display: flex; flex-direction: column; gap: 8px; min-width: 0; flex: 1 1 auto; }
.today__copy b { font-size: 16px; font-weight: 700; }
.today__meta { display: flex; flex-wrap: wrap; gap: 14px; color: var(--ck-text-2); font-size: 13px; }
.today__meta span { display: inline-flex; align-items: center; gap: 4px; }
.today__chev { color: var(--ck-text-3); }

.expiry { display: flex; align-items: center; gap: 14px; width: 100%; min-height: 76px; padding: 14px 16px; border: 1px solid rgba(46, 139, 87, 0.12); border-radius: var(--ck-radius-lg); background: color-mix(in srgb, var(--ck-fresh) 9%, var(--ck-surface)); color: var(--ck-text); text-align: left; }
.expiry__icon { display: grid; place-items: center; width: 46px; height: 46px; flex: 0 0 46px; border-radius: 14px; background: var(--ck-fresh-soft); color: var(--ck-fresh); }
.expiry__copy { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1 1 auto; }
.expiry__copy b { font-size: 16px; font-weight: 700; }
.expiry__copy small { overflow: hidden; color: var(--ck-text-2); font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.expiry__copy em { color: var(--ck-text); font-style: normal; font-weight: 600; }
.expiry__chev { color: var(--ck-fresh); }
.expiry.is-error { border-color: var(--ck-danger-soft); background: color-mix(in srgb, var(--ck-danger) 7%, var(--ck-surface)); }
.expiry.is-error .expiry__icon { background: var(--ck-danger-soft); color: var(--ck-danger); }

.services .ck-section-title { margin-bottom: 10px; }
.services__row { display: flex; gap: 6px; margin: 0 calc(-1 * var(--ck-gutter)); padding: 0 var(--ck-gutter) 4px; overflow-x: auto; scrollbar-width: none; scroll-snap-type: x proximity; }
.services__row::-webkit-scrollbar { display: none; }
.service { display: flex; flex: 0 0 74px; flex-direction: column; align-items: center; gap: 7px; padding: 4px 0; border: 0; background: none; color: var(--ck-text); scroll-snap-align: start; }
.service__icon { display: grid; place-items: center; width: 52px; height: 52px; border-radius: 17px; }
.service__label { font-size: 12.5px; white-space: nowrap; }
.service:active .service__icon { transform: scale(0.94); }
</style>
