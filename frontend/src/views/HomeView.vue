<template>
  <div class="home-layout">
    <component
      :is="currentView"
      v-if="currentView"
      :pending-dish="pendingDish"
      :recipe="currentRecipe"
      @consult-recipe="handleConsultRecipe"
      @clear-pending="clearPendingDish"
      @start-cooking="handleStartCooking"
      @back="handleBack"
    />

    <main v-else class="ck-page home">
      <header class="home-top">
        <div class="wordmark" aria-label="CookX">Cook<strong>X</strong></div>
        <button class="icon-btn" type="button" aria-label="查看个人消息" @click="router.push('/profile')">
          <CkIcon name="bell" :size="22" />
          <span class="icon-btn__dot"></span>
        </button>
      </header>

      <section class="hero">
        <div class="hero-head">
          <div>
            <h1>实时厨房</h1>
            <p>{{ greeting }}{{ userName ? `，${userName}` : '' }}</p>
          </div>
          <button type="button" class="sense-pill" @click="openAiChef">
            <span :class="['ck-dot', { 'is-on': live.connected.value }]"></span>
            <span><b>CookX Sense</b><small>{{ senseText }}</small></span>
          </button>
        </div>

        <div class="hero-temp">
          <span class="hero-temp__label">当前锅温</span>
          <div class="hero-temp__value ck-num">
            <span :class="{ 'is-empty': liveTemperature === null }">{{ liveTemperature === null ? '--' : Math.round(liveTemperature) }}</span><sup v-if="liveTemperature !== null">°C</sup>
          </div>
          <button type="button" class="stage-pill" @click="openAiChef">
            <CkIcon :name="stage ? 'flame' : 'sensor'" :size="18" />
            <span><b>{{ stagePill.title }}</b><small>{{ stagePill.text }}</small></span>
          </button>
        </div>
      </section>

      <section class="ck-glass card stage-card" aria-label="烹饪阶段">
        <div class="ck-section-title">
          <span>{{ activeSession ? `正在烹饪 · ${activeSession.recipe.dish_name || '当前菜谱'}` : '烹饪阶段' }}</span>
          <small v-if="activeSession">第 {{ activeSession.stepIndex + 1 }} / {{ activeSession.recipe.steps.length }} 步</small>
        </div>
        <ol class="stepper">
          <li v-for="(item, index) in stepperItems" :key="index" :class="{ done: index < stepperCurrent, current: index === stepperCurrent }">
            <i></i><span>{{ item }}</span>
          </li>
        </ol>
      </section>

      <section class="ck-glass card" aria-label="温度趋势">
        <div class="ck-section-title"><span>温度趋势</span><button type="button" class="ck-link" @click="openAiChef">详情 <CkIcon name="chevron-right" :size="14" /></button></div>
        <CkTempChart :points="live.history.value" :band="[150, 190]" :height="132" />
      </section>

      <button type="button" class="ck-cream suggestion" @click="suggestion.action()">
        <span class="suggestion__icon"><CkIcon :name="suggestion.icon" :size="22" /></span>
        <span class="suggestion__body">
          <small>CookX 建议</small>
          <b>{{ suggestion.title }}</b>
          <em v-if="suggestion.text">{{ suggestion.text }}</em>
        </span>
        <CkIcon name="chevron-right" :size="18" class="suggestion__chev" />
      </button>

      <section class="ck-glass card fridge-card" aria-label="我的冰箱">
        <div class="ck-section-title">
          <span>我的冰箱</span>
          <button class="ck-link" type="button" @click="openInventory">查看全部 <CkIcon name="chevron-right" :size="14" /></button>
        </div>
        <div v-if="inventoryLoading" class="muted-line">正在同步冰箱状态…</div>
        <div v-else-if="inventoryError" class="muted-line is-error">
          <span>暂时无法读取库存，请稍后重试。</span>
          <button type="button" class="ck-btn ck-btn--ghost" @click="fetchInventory"><CkIcon name="refresh" :size="16" />重新加载</button>
        </div>
        <template v-else>
          <div class="fridge-stats">
            <div><strong class="ck-num">{{ inventoryKindCount }}</strong><span>种食材</span></div>
            <div><strong class="ck-num is-warn">{{ expiringKindCount }}</strong><span>即将过期</span></div>
            <button type="button" class="fridge-capture" @click="openInventory">
              <CkIcon name="camera" :size="20" /><span>{{ inventory.length ? '添加食材' : '去识别食材' }}</span>
            </button>
          </div>
          <button v-if="expiringNames.length" type="button" class="expiry-line" @click="openInventory">
            <CkIcon name="clock" :size="16" /><span>建议优先食用：{{ expiringNames.join(' · ') }}</span><CkIcon name="chevron-right" :size="14" />
          </button>
        </template>
      </section>

      <section class="ck-glass card" aria-label="更多功能">
        <div class="ck-section-title"><span>一起做饭</span><small>个人数据确认后才分享</small></div>
        <div class="feature-grid">
          <button v-for="item in features" :key="item.path" type="button" class="feature" @click="router.push(item.path)">
            <span class="feature__icon" :style="{ color: item.color, background: item.bg }"><CkIcon :name="item.icon" :size="22" /></span>
            <span class="feature__label">{{ item.label }}</span>
          </button>
        </div>
      </section>

      <section class="recipe-card" aria-label="今日推荐">
        <img :src="heroDishImage" alt="热气腾腾的鸡肉时蔬料理" />
        <div class="recipe-card__shade"></div>
        <div class="recipe-card__body">
          <small>今日推荐</small>
          <b>用冰箱里的食材做一道菜</b>
          <span>AI 会根据真实库存与偏好生成菜谱</span>
          <button type="button" class="ck-btn ck-btn--heat" @click="openAiChef">立即推荐 <CkIcon name="chevron-right" :size="16" /></button>
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
import CkTempChart from '@/components/ck/CkTempChart.vue'
import { useLiveTemperature, heatStage, HEAT_STAGES } from '@/services/useLiveTemperature.js'
import { getCookingStore } from '@/services/cookingStore.js'
import { API_BASE_URL } from '@/config/backend'
import heroDishImage from '@/assets/images/home-hero-dish.webp'
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
  if (!currentView.value) { fetchInventory(); activeSession.value = readSession() }
})

const handleConsultRecipe = (dishName) => {
  pendingDish.value = dishName
  router.push({ path: '/home', query: { tab: 'AiChef' } })
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

// ---- 实时厨房 ----
const live = useLiveTemperature()
const liveTemperature = computed(() => live.temperature.value)
const stage = computed(() => heatStage(liveTemperature.value))
const senseText = computed(() => {
  if (!live.supported) return '需 Android App'
  if (live.connected.value) return liveTemperature.value === null ? '已连接 · 等待数据' : '已连接'
  return '未连接'
})
const STAGE_HINTS = {
  preheat: ['预热中', '锅还没热，保持中火'],
  rising: ['升温中', '接近煎香区，保持火力'],
  sear: ['煎香区', '温度合适，可以下食材'],
  cook: ['爆炒区', '高温，注意快速翻炒'],
  hot: ['温度过高', '建议调小火力，避免油冒烟']
}
const stagePill = computed(() => {
  if (stage.value) { const [title, text] = STAGE_HINTS[stage.value.id]; return { title, text } }
  if (!live.supported) return { title: '未检测到测温设备', text: '在 Android App 中连接 CookX Sense' }
  return { title: live.connected.value ? '等待温度数据' : '连接 CookX Sense', text: '进入厨房扫描与连接设备' }
})

const readSession = () => { const uid = readUserId(); if (!uid) return null; const state = getCookingStore(uid).engine.state; return state?.status === 'active' && state.recipe?.steps?.length ? state : null }
const activeSession = ref(readSession())
const stepperItems = computed(() => {
  const session = activeSession.value
  if (!session) return HEAT_STAGES.slice(0, 4).map(item => item.label).concat('完成')
  const steps = session.recipe.steps
  const from = Math.max(0, Math.min(session.stepIndex - 2, steps.length - 5))
  return steps.slice(from, from + 5).map((step, i) => { const text = String(step.title || step.text || step || `第 ${from + i + 1} 步`); return text.length > 5 ? text.slice(0, 4) + '…' : text })
})
const stepperCurrent = computed(() => {
  const session = activeSession.value
  if (session) { const from = Math.max(0, Math.min(session.stepIndex - 2, session.recipe.steps.length - 5)); return session.stepIndex - from }
  return stage.value ? Math.min(stage.value.index, 3) : -1
})

const suggestion = computed(() => {
  if (stage.value) {
    const [title, text] = STAGE_HINTS[stage.value.id]
    return { icon: 'flame', title: `${title}：${text}`, text: '提示仅根据锅面温度给出，请结合实际火候判断', action: openAiChef }
  }
  if (activeSession.value) {
    const s = activeSession.value
    return { icon: 'pot', title: `继续烹饪「${s.recipe.dish_name || '当前菜谱'}」`, text: `进行到第 ${s.stepIndex + 1} / ${s.recipe.steps.length} 步`, action: openAiChef }
  }
  if (inventoryLoading.value) return { icon: 'leaf', title: '正在分析冰箱', text: '同步冰箱中的真实食材状态…', action: openInventory }
  if (inventoryError.value) return { icon: 'refresh', title: '暂时无法分析', text: '库存恢复后将自动生成建议', action: fetchInventory }
  if (!inventory.value.length) return { icon: 'camera', title: '先添加食材', text: '拍一张冰箱照片，CookX 才能为你生成建议', action: openInventory }
  if (expiringNames.value.length) return { icon: 'chef', title: `${expiringNames.value.join('、')} 需要尽快使用`, text: '让 AI 用它们做一道菜', action: openAiChef }
  return { icon: 'shield', title: '查看评估依据', text: '未显示临期项不代表食品安全；请核对日期与储存条件', action: openInventory }
})

const features = [
  { label: '家庭冰箱', path: '/household', icon: 'users', color: '#3DD68C', bg: 'rgba(61,214,140,.14)' },
  { label: '共同采购', path: '/shopping', icon: 'cart', color: '#FF9F5A', bg: 'rgba(255,159,90,.14)' },
  { label: '菜谱复刻', path: '/recipes', icon: 'book', color: '#7FB4FF', bg: 'rgba(127,180,255,.14)' },
  { label: '一起晒菜', path: '/community', icon: 'image', color: '#FF8FB1', bg: 'rgba(255,143,177,.14)' },
  { label: '剩菜改造', path: '/leftovers', icon: 'recycle', color: '#3DD68C', bg: 'rgba(61,214,140,.14)' },
  { label: '厨艺成长', path: '/growth', icon: 'chart', color: '#B69CFF', bg: 'rgba(182,156,255,.14)' },
  { label: '七日菜单', path: '/menus', icon: 'calendar', color: '#FFC15A', bg: 'rgba(255,193,90,.14)' },
  { label: '偏好学习', path: '/learning', icon: 'sparkle', color: '#7FB4FF', bg: 'rgba(127,180,255,.14)' }
]
const consumptionChanged=event=>{if(event.detail?.user===readUserId())fetchInventory()}
onMounted(()=>window.addEventListener('cookx:inventory-changed',consumptionChanged))
onBeforeUnmount(()=>window.removeEventListener('cookx:inventory-changed',consumptionChanged))
</script>

<style scoped>
.home-layout { position: relative; z-index: 1; min-height: 100vh; }
.home { padding-top: var(--sat); }
.home-top { display: flex; align-items: center; justify-content: space-between; height: 56px; }
.wordmark { color: var(--ck-text); font-family: var(--ck-font-display); font-size: 28px; font-weight: 700; letter-spacing: -0.8px; }
.wordmark strong { color: var(--ck-heat); font-weight: inherit; }
.icon-btn { position: relative; display: grid; place-items: center; width: 40px; height: 40px; padding: 0; border: 1px solid var(--ck-glass-border); border-radius: 50%; background: rgba(20, 22, 21, 0.45); color: var(--ck-text); -webkit-backdrop-filter: blur(12px); backdrop-filter: blur(12px); }
.icon-btn__dot { position: absolute; top: 8px; right: 9px; width: 7px; height: 7px; border-radius: 50%; background: var(--ck-heat); }

.hero { padding: 6px 0 22px; }
.hero-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.hero-head h1 { font-size: 30px; font-weight: 700; letter-spacing: -0.4px; line-height: 1.2; }
.hero-head p { margin-top: 2px; color: var(--ck-text-2); font-size: 14px; }
.sense-pill { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 6px 14px 6px 12px; border: 1px solid var(--ck-glass-border); border-radius: 16px; background: rgba(20, 22, 21, 0.5); color: var(--ck-text); text-align: left; -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
.sense-pill span:last-child { display: flex; flex-direction: column; line-height: 1.25; }
.sense-pill b { font-size: 12.5px; font-weight: 600; }
.sense-pill small { color: var(--ck-text-2); font-size: 11.5px; }

.hero-temp { display: flex; flex-direction: column; align-items: center; padding-top: clamp(18px, 6vh, 56px); text-align: center; text-shadow: 0 2px 24px rgba(0, 0, 0, 0.35); }
.hero-temp__label { color: var(--ck-text-2); font-size: 15px; }
.hero-temp__value { display: flex; align-items: flex-start; margin-top: -2px; color: #fff; font-size: clamp(88px, 27vw, 120px); font-weight: 200; line-height: 1; }
.hero-temp__value .is-empty { color: rgba(255, 255, 255, 0.55); font-size: 0.72em; letter-spacing: 0.08em; }
.hero-temp__value sup { margin: 0.18em 0 0 4px; font-size: 0.3em; font-weight: 300; }
.stage-pill { display: flex; align-items: center; gap: 10px; margin-top: 16px; padding: 10px 20px 10px 16px; border: 1px solid rgba(255, 138, 61, 0.35); border-radius: 22px; background: rgba(120, 52, 18, 0.42); color: var(--ck-text); text-align: left; -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px); }
.stage-pill > .ck-icon { color: #FF9F5A; }
.stage-pill span { display: flex; flex-direction: column; line-height: 1.35; }
.stage-pill b { font-size: 15px; font-weight: 600; }
.stage-pill small { color: rgba(246, 243, 238, 0.75); font-size: 12.5px; }

.card { margin-bottom: 12px; padding: 16px; }
.stepper { position: relative; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin: 4px 0 0; padding: 0; list-style: none; }
.stepper li { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--ck-text-3); font-size: 12px; text-align: center; }
.stepper li::before { content: ''; position: absolute; top: 8px; right: 50%; left: -50%; height: 2px; background: rgba(255, 255, 255, 0.14); }
.stepper li:first-child::before { display: none; }
.stepper li.done::before, .stepper li.current::before { background: var(--ck-heat); }
.stepper i { position: relative; z-index: 1; width: 18px; height: 18px; border: 2px solid rgba(255, 255, 255, 0.28); border-radius: 50%; background: #262A28; }
.stepper li.done i { border-color: var(--ck-heat); background: var(--ck-heat); }
.stepper li.current i { border-color: var(--ck-heat); background: #fff; box-shadow: 0 0 0 5px rgba(255, 138, 61, 0.25); }
.stepper li.current, .stepper li.done { color: var(--ck-text); }
.stepper li.current span { font-weight: 600; }
.stepper span { max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.suggestion { display: flex; align-items: center; gap: 14px; width: 100%; margin: 4px 0 16px; padding: 16px; border: 0; text-align: left; box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35); }
.suggestion__icon { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 14px; background: #FFE3CC; color: #D2561A; }
.suggestion__body { display: flex; flex-direction: column; min-width: 0; flex: 1 1 auto; gap: 2px; }
.suggestion__body small { color: var(--ck-cream-text-2); font-size: 12px; }
.suggestion__body b { font-size: 16px; font-weight: 700; line-height: 1.4; }
.suggestion__body em { color: var(--ck-cream-text-2); font-size: 12.5px; font-style: normal; line-height: 1.5; }
.suggestion__chev { color: var(--ck-cream-text-2); }

.muted-line { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding: 8px 0; color: var(--ck-text-3); font-size: 13px; }
.muted-line.is-error { color: #FFB5AB; }
.muted-line .ck-btn { min-height: 38px; font-size: 13px; }
.fridge-stats { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) auto; align-items: center; gap: 12px; }
.fridge-stats div { display: flex; flex-direction: column; }
.fridge-stats strong { font-size: 32px; font-weight: 300; line-height: 1.1; }
.fridge-stats strong.is-warn { color: var(--ck-warn); }
.fridge-stats span { color: var(--ck-text-3); font-size: 12px; }
.fridge-capture { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; min-width: 84px; min-height: 64px; padding: 8px 10px; border: 1px solid rgba(255, 138, 61, 0.3); border-radius: 16px; background: var(--ck-heat-soft); color: #FFB27F; font-size: 12px; font-weight: 600; }
.expiry-line { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; margin-top: 12px; padding: 10px 12px; border: 0; border-radius: 12px; background: var(--ck-warn-soft); color: #FFD89A; font-size: 13px; text-align: left; }
.expiry-line span { flex: 1 1 auto; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.feature-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); row-gap: 16px; }
.feature { display: flex; flex-direction: column; align-items: center; gap: 7px; min-width: 0; padding: 2px 0; border: 0; background: none; color: var(--ck-text); }
.feature__icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 16px; }
.feature__label { max-width: 100%; overflow: hidden; font-size: 12.5px; text-overflow: ellipsis; white-space: nowrap; }
.feature:active .feature__icon { transform: scale(0.94); }

.recipe-card { position: relative; min-height: 190px; margin-bottom: 12px; overflow: hidden; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); }
.recipe-card > img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: 70% 50%; }
.recipe-card__shade { position: absolute; inset: 0; background: linear-gradient(90deg, rgba(10, 12, 11, 0.94) 0%, rgba(10, 12, 11, 0.7) 50%, rgba(10, 12, 11, 0.05) 100%); }
.recipe-card__body { position: relative; display: flex; flex-direction: column; align-items: flex-start; gap: 4px; max-width: 74%; padding: 20px 18px; }
.recipe-card__body small { color: #FFB27F; font-size: 12px; font-weight: 600; }
.recipe-card__body b { font-size: 18px; line-height: 1.35; }
.recipe-card__body span { color: var(--ck-text-2); font-size: 12.5px; }
.recipe-card__body .ck-btn { min-height: 40px; margin-top: 10px; padding: 0 16px; font-size: 14px; }

@media (max-width: 360px) {
  .hero-head h1 { font-size: 26px; }
  .sense-pill { padding: 6px 10px; }
  .feature__icon { width: 44px; height: 44px; }
  .feature__label { font-size: 11.5px; }
}
</style>
