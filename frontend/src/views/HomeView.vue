<template>
  <div class="home-layout">
    <template v-if="!currentView">
      <section class="dark-stage">
        <div class="dark-inner">
          <header class="home-header">
            <div class="wordmark" aria-label="CookX">
              <span>Cook</span><strong>X</strong>
            </div>
            <button class="notification-button" type="button" aria-label="查看个人消息" @click="router.push('/profile')">
              <el-icon><Bell /></el-icon>
              <span class="notification-dot"></span>
            </button>
          </header>

          <div class="welcome-copy">
            <p>{{ greeting }}{{ userName ? `，${userName}` : '' }}</p>
            <h1>今天想吃点什么？</h1>
          </div>

          <article class="hero-card">
            <img :src="heroDishImage" alt="热气腾腾的鸡肉时蔬料理" class="hero-image" />
            <div class="hero-shade"></div>
            <div class="hero-copy">
              <span class="hero-label">COOKX AI</span>
              <h2>CookX AI 智能推荐</h2>
              <p>根据冰箱现有食材<br />为你生成今天最合适的菜谱</p>
              <button class="hero-button" type="button" @click="openAiChef">
                立即推荐 <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <div class="hero-dots" aria-hidden="true">
              <span class="active"></span><span></span><span></span>
            </div>
          </article>
        </div>
      </section>

      <main class="dashboard-shell">
        <section class="panel fridge-panel">
          <div class="panel-heading">
            <div class="heading-title">
              <span class="heading-icon"><el-icon><Box /></el-icon></span>
              <h2>我的冰箱</h2>
            </div>
            <button class="plain-link" type="button" @click="openInventory">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </button>
          </div>

          <div v-if="inventoryLoading" class="loading-state">正在同步冰箱状态…</div>
          <div v-else-if="inventoryError" class="loading-state error">
            <span>暂时无法读取库存，请稍后重试。</span>
            <button type="button" @click="fetchInventory"><el-icon><Refresh /></el-icon>重新加载</button>
          </div>
          <div v-else-if="inventory.length === 0" class="fridge-empty">
            <span class="empty-box"><el-icon><Box /></el-icon></span>
            <div>
              <strong>冰箱还是空的</strong>
              <p>去识别食材，CookX 才能为你推荐菜谱</p>
              <button class="outline-action" type="button" @click="openInventory">
                去识别食材 <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
          </div>
          <template v-else>
            <div class="metric-grid">
              <div class="metric-card fresh-metric">
                <span class="metric-icon"><el-icon><KnifeFork /></el-icon></span>
                <div><strong>{{ inventoryKindCount }}<small>种</small></strong><p>食材种类</p></div>
              </div>
              <div class="metric-card warning-metric">
                <span class="metric-icon"><el-icon><AlarmClock /></el-icon></span>
                <div><strong>{{ expiringKindCount }}<small>种</small></strong><p>即将过期</p></div>
              </div>
            </div>
            <button class="expiry-row" type="button" @click="openInventory">
              <el-icon><AlarmClock /></el-icon>
              <span v-if="expiringNames.length">建议优先食用：{{ expiringNames.join(' · ') }}</span>
              <span v-else>{{ freshness.status === 'error' ? '鲜度暂不可用，请进入冰箱重试' : '暂无已评估的临期项；未知与过期项请查看冰箱详情' }}</span>
              <el-icon class="row-arrow"><ArrowRight /></el-icon>
            </button>
          </template>
        </section>

        <section class="quick-grid" aria-label="快捷功能">
          <button class="quick-card" type="button" @click="openInventory">
            <span class="quick-icon quick-green"><el-icon><CameraFilled /></el-icon></span>
            <span class="quick-copy"><strong>食材识别</strong><small>AI 识别冰箱食材</small></span>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
          <button class="quick-card" type="button" @click="openAiChef">
            <span class="quick-icon quick-gold"><el-icon><KnifeFork /></el-icon></span>
            <span class="quick-copy"><strong>AI 菜谱</strong><small>根据食材智能推荐</small></span>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
          <button class="quick-card" type="button" @click="openInventory">
            <span class="quick-icon quick-deep"><el-icon><Box /></el-icon></span>
            <span class="quick-copy"><strong>智能库存</strong><small>管理冰箱与保质期</small></span>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
          <button class="quick-card" type="button" @click="openAiChef">
            <span class="quick-icon quick-orange"><el-icon><Bowl /></el-icon></span>
            <span class="quick-copy"><strong>AI 厨房</strong><small>语音指导 · 实时控温</small></span>
            <el-icon class="quick-arrow"><ArrowRight /></el-icon>
          </button>
        </section>

        <div class="sense-insight-grid">
          <section class="sense-card">
            <div class="sense-heading">
              <h2>CookX Sense <span>智能状态</span></h2>
              <span class="connection-state"><i></i>等待连接</span>
            </div>
            <div class="sense-body">
              <div class="temperature-block">
                <span>实时锅温</span>
                <strong>--.-<small>°C</small></strong>
                <p>等待连接</p>
              </div>
              <div class="sense-orbit" aria-hidden="true">
                <span class="orbit-ring"></span>
                <el-icon><Connection /></el-icon>
                <small>未连接</small>
              </div>
              <div class="device-block"><span>测温设备</span><strong>未连接</strong></div>
            </div>
            <button class="sense-button" type="button" @click="openAiChef">
              进入 AI 厨房连接 <el-icon><ArrowRight /></el-icon>
            </button>
          </section>

          <section class="insight-card">
            <div class="insight-copy">
              <span class="insight-kicker">COOKX INSIGHT</span>
              <h2>CookX 今日洞察</h2>
              <template v-if="inventoryLoading">
                <strong>正在分析</strong><p>同步冰箱中的真实食材状态…</p>
              </template>
              <template v-else-if="inventoryError">
                <strong>暂时无法分析</strong><p>库存恢复后将自动生成今日洞察。</p>
              </template>
              <template v-else-if="inventory.length === 0">
                <strong>先添加食材</strong><p>CookX 才能为你生成智能建议。</p>
              </template>
              <template v-else-if="expiringKindCount > 0">
                <strong>建议优先食用</strong><p>冰箱中有 {{ expiringKindCount }} 种食材需要尽快使用。</p>
              </template>
              <template v-else>
                <strong>查看评估依据</strong><p>未显示临期项不代表食品安全；请核对日期、储存条件与数据不足项。</p>
              </template>
              <button class="insight-button" type="button" @click="openInventory">
                查看冰箱详情 <el-icon><ArrowRight /></el-icon>
              </button>
            </div>
            <div class="fridge-illustration" aria-hidden="true">
              <span class="fridge-door top"></span><span class="fridge-door bottom"></span>
              <i class="leaf leaf-one"></i><i class="leaf leaf-two"></i>
            </div>
          </section>
        </div>

        <section class="recipe-section">
          <div class="recipe-heading">
            <div><span>DAILY RECIPE</span><h2>今日推荐菜谱</h2></div>
            <button type="button" @click="openAiChef">换一换 <el-icon><Refresh /></el-icon></button>
          </div>
          <article class="recipe-empty-card">
            <img :src="recipeEmptyImage" alt="新鲜蔬菜食材背景" />
            <div class="recipe-empty-overlay"></div>
            <div class="recipe-empty-copy">
              <span class="chef-mark"><el-icon><KnifeFork /></el-icon></span>
              <div><strong>还没有今日推荐</strong><p>根据冰箱真实食材生成一份吧</p></div>
              <button type="button" @click="openAiChef">去生成菜谱 <el-icon><ArrowRight /></el-icon></button>
            </div>
          </article>
        </section>
      </main>
    </template>

    <template v-else>
      <header class="child-header"><span>Cook</span><strong>X</strong></header>
      <main class="child-body">
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
    </template>
  </div>
</template>

<script setup>
import { getInventoryFreshness } from '../api/freshness.js'
import { createFreshnessLoader, emptyFreshness } from '../services/inventoryFreshness.js'
import { readUserId } from '../services/recognitionDraft.js'
import { computed, onMounted, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import {
  AlarmClock,
  ArrowRight,
  Bell,
  Bowl,
  Box,
  CameraFilled,
  Connection,
  KnifeFork,
  Refresh
} from '@element-plus/icons-vue'
import { API_BASE_URL } from '@/config/backend'
import heroDishImage from '@/assets/images/home-hero-dish.png'
import recipeEmptyImage from '@/assets/images/home-recipe-empty.png'
import ManageFridge from './ManageFridge.vue'
import AiChef from './AiChef.vue'
import CookingMode from './CookingMode.vue'

const route = useRoute()
const router = useRouter()
const componentMap = { Manage: ManageFridge, AiChef }
const getTarget = () => componentMap[route.query.tab] || null

const currentView = shallowRef(getTarget())
const currentRecipe = ref(null)
const pendingDish = ref('')
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
  currentView.value = AiChef
}
const clearPendingDish = () => { pendingDish.value = '' }
const handleStartCooking = (recipe) => {
  currentRecipe.value = recipe
  currentView.value = CookingMode
}
const handleBack = () => {
  currentView.value = currentView.value === CookingMode ? AiChef : ManageFridge
}
const consumptionChanged=event=>{if(event.detail?.user===readUserId())fetchInventory()}
onMounted(()=>window.addEventListener('cookx:inventory-changed',consumptionChanged))
onBeforeUnmount(()=>window.removeEventListener('cookx:inventory-changed',consumptionChanged))
</script>

<style scoped>
.home-layout { min-height: calc(100vh - 70px); background: var(--cookx-bg); color: var(--cookx-text); }
button { font: inherit; }
.dark-stage { overflow: hidden; padding: 20px 16px 34px; background: radial-gradient(circle at 82% 5%, rgba(77,139,105,.2), transparent 30%), linear-gradient(145deg, #092a22 0%, var(--cookx-primary-dark) 60%, #0b352a 100%); }
.dark-inner, .dashboard-shell { width: min(100%, var(--cookx-page-max)); margin: 0 auto; }
.home-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 23px; }
.wordmark, .child-header { color: #fff; font-size: 30px; font-weight: 760; letter-spacing: -.8px; }
.wordmark strong, .child-header strong { color: var(--cookx-accent); font-weight: inherit; }
.notification-button { position: relative; display: grid; width: 42px; height: 42px; padding: 0; border: 1px solid rgba(255,255,255,.16); border-radius: 14px; background: rgba(255,255,255,.06); color: #fff; cursor: pointer; place-items: center; }
.notification-button .el-icon { font-size: 21px; }
.notification-dot { position: absolute; top: 7px; right: 7px; width: 7px; height: 7px; border: 2px solid var(--cookx-primary-dark); border-radius: 50%; background: var(--cookx-accent); }
.welcome-copy { margin-bottom: 22px; color: #fff; }
.welcome-copy p { margin: 0 0 3px; font-size: 25px; font-weight: 700; letter-spacing: -.4px; }
.welcome-copy h1 { margin: 0; color: rgba(255,255,255,.82); font-size: 16px; font-weight: 450; }
.hero-card { position: relative; min-height: 260px; overflow: hidden; border: 1px solid rgba(255,255,255,.18); border-radius: 24px; background: #0b362b; box-shadow: 0 18px 40px rgba(0,0,0,.22); }
.hero-image { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: center; }
.hero-shade { position: absolute; inset: 0; background: linear-gradient(90deg, rgba(5,32,25,.98) 0%, rgba(5,32,25,.82) 42%, rgba(5,32,25,.12) 77%); }
.hero-copy { position: relative; z-index: 2; width: 60%; padding: 26px 22px 38px; color: #fff; }
.hero-label { color: var(--cookx-gold); font-size: 10px; font-weight: 750; letter-spacing: 1.4px; }
.hero-copy h2 { margin: 7px 0 12px; font-size: clamp(21px,5.8vw,28px); line-height: 1.2; }
.hero-copy p { margin: 0 0 22px; color: rgba(255,255,255,.78); font-size: 13px; line-height: 1.65; }
.hero-button { display: inline-flex; align-items: center; gap: 7px; min-height: 44px; padding: 0 18px; border: 0; border-radius: 15px; background: linear-gradient(135deg, #ef8b39, var(--cookx-accent)); color: #fff; font-weight: 700; box-shadow: 0 9px 22px rgba(216,107,53,.26); cursor: pointer; }
.hero-dots { position: absolute; z-index: 2; bottom: 13px; left: 50%; display: flex; gap: 7px; transform: translateX(-50%); }
.hero-dots span { width: 7px; height: 7px; border-radius: 50%; background: rgba(255,255,255,.35); }
.hero-dots .active { background: #fff; }
.dashboard-shell { position: relative; z-index: 3; padding: 18px 16px 34px; }
.panel, .quick-card, .insight-card, .recipe-section { border: var(--cookx-border); background: var(--cookx-surface); box-shadow: var(--cookx-shadow); }
.panel { margin-bottom: 16px; padding: 18px; border-radius: 20px; }
.panel-heading, .heading-title, .metric-card, .expiry-row, .quick-card, .sense-heading, .sense-body, .recipe-heading, .recipe-empty-copy { display: flex; align-items: center; }
.panel-heading { justify-content: space-between; margin-bottom: 15px; }
.heading-title { gap: 9px; }
.heading-title h2, .sense-heading h2, .insight-copy h2, .recipe-heading h2 { margin: 0; font-size: 18px; }
.heading-icon { display: grid; width: 30px; height: 30px; border-radius: 9px; background: #eef4ef; color: var(--cookx-primary); place-items: center; }
.plain-link, .recipe-heading button { display: inline-flex; align-items: center; gap: 3px; padding: 7px 0; border: 0; background: none; color: var(--cookx-text-secondary); font-size: 12px; cursor: pointer; }
.loading-state { padding: 22px 0; color: var(--cookx-text-secondary); font-size: 13px; text-align: center; }
.loading-state.error { color: var(--cookx-danger); }
.loading-state.error button { display: inline-flex; align-items: center; justify-content: center; gap: 5px; min-height: 40px; margin: 12px auto 0; padding: 0 15px; border: var(--cookx-border); border-radius: var(--cookx-radius-button); background: #fff; color: var(--cookx-primary); font: inherit; font-size: 12px; font-weight: 650; cursor: pointer; }
.fridge-empty { display: flex; align-items: center; gap: 14px; padding: 13px; border-radius: 15px; background: #faf9f5; }
.empty-box { display: grid; flex: 0 0 48px; width: 48px; height: 48px; border-radius: 13px; background: #e8eeea; color: var(--cookx-primary); font-size: 21px; place-items: center; }
.fridge-empty strong { font-size: 14px; }
.fridge-empty p { margin: 4px 0 7px; color: var(--cookx-text-secondary); font-size: 11px; }
.outline-action, .insight-button { display: inline-flex; align-items: center; gap: 4px; min-height: 44px; padding: 0 13px; border: 1px solid rgba(23,63,53,.32); border-radius: 12px; background: transparent; color: var(--cookx-primary); font-size: 11px; font-weight: 650; cursor: pointer; }
.metric-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; }
.metric-card { min-width: 0; gap: 12px; padding: 15px 12px; border: 1px solid rgba(23,63,53,.07); border-radius: 15px; background: #fff; box-shadow: 0 5px 18px rgba(28,48,40,.05); }
.metric-icon { display: grid; flex: 0 0 44px; width: 44px; height: 44px; border-radius: 13px; font-size: 22px; place-items: center; }
.fresh-metric .metric-icon { background: #e9f5e8; color: #279744; }
.warning-metric .metric-icon { background: #fff0ed; color: var(--cookx-danger); }
.metric-card strong { font-size: 27px; line-height: 1; }
.metric-card strong small { margin-left: 3px; font-size: 11px; }
.metric-card p { margin: 5px 0 0; color: var(--cookx-text-secondary); font-size: 11px; }
.expiry-row { width: 100%; gap: 8px; margin-top: 12px; padding: 12px 4px 0; border: 0; border-top: var(--cookx-border); background: none; color: var(--cookx-text); font-size: 11px; text-align: left; cursor: pointer; }
.expiry-row > .el-icon:first-child { color: var(--cookx-danger); }
.expiry-row span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.row-arrow { margin-left: auto; }
.quick-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); gap: 10px; margin-bottom: 16px; }
.quick-card { position: relative; min-width: 0; min-height: 98px; gap: 11px; padding: 14px 12px; border-radius: 17px; text-align: left; cursor: pointer; transition: transform .2s ease, box-shadow .2s ease; }
.quick-card:hover { transform: translateY(-2px); box-shadow: var(--cookx-shadow-hover); }
.quick-card:active { transform: scale(.98); }
.quick-icon { display: grid; flex: 0 0 42px; width: 42px; height: 42px; border-radius: 13px; color: #fff; font-size: 20px; box-shadow: 0 6px 14px rgba(28,48,40,.12); place-items: center; }
.quick-green { background: linear-gradient(145deg,#69cf75,#36ad51); }
.quick-gold { background: linear-gradient(145deg,#f5b14e,#e9862f); }
.quick-deep { background: linear-gradient(145deg,#2e9c7b,var(--cookx-primary)); }
.quick-orange { background: linear-gradient(145deg,#ff8a53,#e85c38); }
.quick-copy { display: flex; min-width: 0; flex-direction: column; }
.quick-copy strong { font-size: 14px; }
.quick-copy small { margin-top: 5px; color: var(--cookx-text-secondary); font-size: 10px; line-height: 1.35; }
.quick-arrow { flex: 0 0 auto; margin-left: auto; color: var(--cookx-text-secondary); font-size: 13px; }
.sense-insight-grid { display: grid; gap: 16px; margin-bottom: 16px; }
.sense-card { padding: 18px; border: 1px solid rgba(255,255,255,.12); border-radius: 20px; background: radial-gradient(circle at 55% 65%,rgba(68,139,107,.22),transparent 29%), linear-gradient(145deg,#07352a,#03271f); color: #fff; box-shadow: 0 13px 28px rgba(7,49,39,.18); }
.sense-heading { justify-content: space-between; gap: 8px; padding-bottom: 12px; border-bottom: 1px solid rgba(255,255,255,.1); }
.sense-heading h2 span { font-weight: 500; }
.connection-state { display: inline-flex; align-items: center; gap: 5px; color: var(--cookx-gold); font-size: 10px; white-space: nowrap; }
.connection-state i { width: 7px; height: 7px; border-radius: 50%; background: var(--cookx-gold); }
.sense-body { justify-content: space-between; gap: 10px; min-height: 116px; }
.temperature-block span, .device-block span { color: rgba(255,255,255,.58); font-size: 10px; }
.temperature-block strong { display: block; margin: 5px 0; font-size: 29px; }
.temperature-block strong small { font-size: 15px; }
.temperature-block p { margin: 0; color: rgba(255,255,255,.72); font-size: 10px; }
.sense-orbit { display: grid; position: relative; flex: 0 0 80px; width: 80px; height: 80px; border-radius: 50%; background: rgba(255,255,255,.05); place-items: center; }
.orbit-ring { position: absolute; inset: 7px; border: 5px solid rgba(255,255,255,.09); border-top-color: var(--cookx-gold); border-radius: 50%; transform: rotate(30deg); }
.sense-orbit .el-icon { z-index: 1; font-size: 24px; }
.sense-orbit small { position: absolute; bottom: 9px; color: rgba(255,255,255,.55); font-size: 8px; }
.device-block { text-align: right; }
.device-block strong { display: block; margin-top: 6px; font-size: 13px; }
.sense-button { display: flex; align-items: center; justify-content: center; gap: 5px; width: 100%; min-height: 39px; border: 1px solid rgba(255,255,255,.46); border-radius: 14px; background: rgba(255,255,255,.03); color: #fff; font-size: 12px; font-weight: 650; cursor: pointer; }
.insight-card { position: relative; min-height: 210px; overflow: hidden; border-radius: 20px; background: linear-gradient(135deg,#f5fbf3,#e2f2e2); }
.insight-copy { position: relative; z-index: 2; width: 68%; padding: 20px; }
.insight-kicker, .recipe-heading span { color: var(--cookx-success); font-size: 9px; font-weight: 750; letter-spacing: 1.2px; }
.insight-copy h2 { margin: 4px 0 18px; }
.insight-copy strong { font-size: 14px; }
.insight-copy p { margin: 5px 0 12px; color: var(--cookx-text-secondary); font-size: 11px; line-height: 1.5; }
.fridge-illustration { position: absolute; right: 16px; bottom: 20px; width: 72px; height: 126px; border-radius: 16px; background: linear-gradient(145deg,#d5f0d3,#8ed08d); box-shadow: 0 12px 25px rgba(77,139,105,.18); }
.fridge-illustration::before { content:''; position:absolute; top:12px; left:9px; width:4px; height:23px; border-radius:3px; background:rgba(23,63,53,.25); }
.fridge-door { position:absolute; left:0; right:0; border-bottom:1px solid rgba(23,63,53,.14); }
.fridge-door.top { top:47px; }.fridge-door.bottom { top:49px; }
.leaf { position:absolute; width:22px; height:10px; border-radius:100% 0 100% 0; background:#5fb264; transform:rotate(-38deg); }
.leaf-one { right:-14px; bottom:10px; }.leaf-two { right:-5px; bottom:28px; transform:rotate(-70deg) scale(.75); }
.recipe-section { padding: 18px; border-radius: 20px; }
.recipe-heading { justify-content: space-between; margin-bottom: 14px; }
.recipe-heading h2 { margin-top: 3px; }
.recipe-empty-card { position: relative; min-height: 190px; overflow: hidden; border-radius: 16px; }
.recipe-empty-card > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.recipe-empty-overlay { position: absolute; inset: 0; background: rgba(247,245,239,.68); backdrop-filter: blur(1px); }
.recipe-empty-copy { position: relative; z-index: 2; justify-content: center; gap: 12px; min-height: 190px; padding: 20px; text-align: left; }
.chef-mark { display: grid; flex: 0 0 44px; width: 44px; height: 44px; border-radius: 13px; background: rgba(255,255,255,.85); color: var(--cookx-primary); font-size: 20px; place-items: center; }
.recipe-empty-copy strong { font-size: 14px; }.recipe-empty-copy p { margin: 5px 0 0; color: var(--cookx-text-secondary); font-size: 10px; }
.recipe-empty-copy button { display: inline-flex; align-items: center; gap: 4px; min-height: 44px; margin-left: 8px; padding: 0 15px; border: 0; border-radius: 13px; background: var(--cookx-primary); color: #fff; font-size: 11px; font-weight: 650; cursor: pointer; }
.child-header { display: flex; align-items: center; justify-content: center; height: 56px; background: linear-gradient(135deg,var(--cookx-primary-dark),var(--cookx-primary)); box-shadow: 0 8px 24px rgba(16,46,39,.16); font-size: 20px; }
.child-body { height: calc(100vh - 126px); overflow-y: auto; padding: 16px; background: var(--cookx-bg); }

@media (min-width: 720px) {
  .dark-stage { padding: 28px 24px 42px; }
  .dashboard-shell { padding: 22px 24px 42px; }
  .hero-card { min-height: 310px; }
  .hero-copy { padding: 42px 36px; }
  .sense-insight-grid { grid-template-columns: repeat(2,minmax(0,1fr)); }
  .quick-card { min-height: 110px; }
}

@media (max-width: 390px) {
  .dark-stage, .dashboard-shell { padding-right: var(--cookx-page-gutter-mobile); padding-left: var(--cookx-page-gutter-mobile); }
  .hero-card { min-height: 250px; }
  .hero-copy { width: 64%; padding-right: 8px; padding-left: 17px; }
  .hero-copy h2 { font-size: 20px; }
  .metric-card { gap: 8px; padding: 13px 9px; }
  .metric-icon { flex-basis: 39px; width: 39px; height: 39px; }
  .metric-card strong { font-size: 24px; }
  .quick-card { gap: 8px; padding: 12px 10px; }
  .quick-icon { flex-basis: 38px; width: 38px; height: 38px; }
  .recipe-empty-copy { flex-wrap: wrap; text-align: center; }
  .recipe-empty-copy button { width: 100%; margin-left: 0; justify-content: center; }
}
</style>
