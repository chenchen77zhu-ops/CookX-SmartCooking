<template>
  <div class="capture-confirm-page">
    <CkNavBar title="确认食材" back-label="返回冰箱" manual @back="goBack" />
    <header class="confirm-hero">
      <span class="status-pill"><CkIcon name="check" :size="15" :stroke="2.4" />{{ LOCAL_TEST_MODE ? '手动载入示例 · 未识别图片' : 'AI 识别完成' }}</span>
      <h1>识别结果确认</h1>
      <p>请确认识别出的食材及数量，必要时可修改后再加入冰箱</p>
      <dl class="summary-strip">
        <div><dt>待确认</dt><dd class="ck-num">{{ identifiedItems.length }}<small>项</small></dd></div>
        <div><dt>食材种类</dt><dd class="ck-num">{{ uniqueIngredientCount }}<small>种</small></dd></div>
        <div><dt>总数量</dt><dd class="ck-num">{{ totalQuantity }}<small>份</small></dd></div>
        <div v-if="identifiedItems.length"><dt>最短保质期</dt><dd class="ck-num">{{ minShelfLife }}<small>天</small></dd></div>
      </dl>
    </header>

    <main class="confirm-layout">
      <section class="results-panel">
        <div class="ck-section-title">
          <span>识别出的食材 <small>（{{ identifiedItems.length }}项）</small></span>
          <span v-if="identifiedItems.length" class="ready-label">等待确认</span>
        </div>

        <div v-if="identifiedItems.length" class="food-grid">
          <article v-for="(item, index) in identifiedItems" :key="index" class="food-card">
            <div class="food-card-head">
              <div class="food-image">
                <span class="image-placeholder"><CkIcon name="leaf" :size="22" /></span>
                <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="displayName(item.name)" loading="lazy" @error="hideBrokenImage" />
              </div>
              <div class="freshness-copy"><b>{{ displayName(item.name) }}</b><span>新鲜度 <el-tag :type="getFreshnessType(item.freshness)" size="small">{{ item.freshness || '数据不足' }}</el-tag></span></div>
              <button type="button" class="delete-button" aria-label="删除该识别结果" :disabled="saving || pendingWrite" @click="removeItem(index)"><CkIcon name="trash" :size="16" />删除</button>
            </div>
            <div class="food-card-body">
              <InventoryFields v-model="identifiedItems[index]" :disabled="saving || pendingWrite" />
              <FreshnessCard v-if="item.freshness_detail" :detail="item.freshness_detail" status="success" />
              <p v-else class="freshness-note">识别结果仅供确认名称，鲜度数据不足，入库后重新评估。</p>
            </div>
          </article>
        </div>

        <section v-else class="empty-state">
          <span><CkIcon name="image" :size="26" /></span>
          <h2>未检测到可确认的食材</h2>
          <p>请重新选择图片进行识别</p>
          <button type="button" @click="recognizeAgain"><CkIcon name="refresh" :size="16" />重新识别</button>
        </section>
      </section>

      <section class="tips-card">
        <div class="ck-section-title"><span>识别小贴士</span></div>
        <ul>
          <li>请确认食材名称和数量</li>
          <li>可修改数量、存储方式和保质期</li>
          <li>确认后会加入我的冰箱</li>
          <li>如识别有误，可删除后重新识别</li>
        </ul>
      </section>
    </main>

    <InventoryWriteStatus :user="currentUserId" :message="saveError" :pending="pendingWrite" :disabled="saving" @released="pendingWrite = false; saveError = ''" />
    <section class="action-bar">
      <button type="button" class="secondary-action" @click="recognizeAgain"><CkIcon name="refresh" :size="18" />重新识别</button>
      <button type="button" class="primary-action" :disabled="identifiedItems.length === 0 || saving" @click="confirmSave">
        <span>确认加入冰箱（{{ identifiedItems.length }}项）<small>保存当前确认的食材信息</small></span>
      </button>
    </section>
  </div>
</template>

<script setup>
import {LOCAL_TEST_MODE} from '../config/buildMode.js'
import FreshnessCard from '../components/FreshnessCard.vue'
import { loadDraft, saveDraft, clearDraft, readUserId } from '../services/recognitionDraft.js'
import InventoryFields from '../components/InventoryFields.vue'
import InventoryWriteStatus from '../components/InventoryWriteStatus.vue'
import { serializeItem, inventoryErrorMessage } from '../services/inventoryFields.js'
import { saveInventory, hasPendingWrite } from '../api/inventoryWrites.js'
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import CkIcon from '@/components/ck/CkIcon.vue'
import CkNavBar from '@/components/ck/CkNavBar.vue'
import { ElMessage } from 'element-plus'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'
import beefImage from '@/assets/images/ingredients/beef.webp'
import broccoliImage from '@/assets/images/ingredients/broccoli.webp'
import carrotImage from '@/assets/images/ingredients/carrot.webp'
import chickenImage from '@/assets/images/ingredients/chicken.webp'
import chiliImage from '@/assets/images/ingredients/chili.webp'
import eggImage from '@/assets/images/ingredients/egg.webp'
import garlicImage from '@/assets/images/ingredients/garlic.webp'
import kimchiImage from '@/assets/images/ingredients/kimchi.webp'
import leekImage from '@/assets/images/ingredients/leek.webp'
import lettuceImage from '@/assets/images/ingredients/lettuce.webp'
import milkImage from '@/assets/images/ingredients/milk.webp'
import onionImage from '@/assets/images/ingredients/onion.webp'
import potatoImage from '@/assets/images/ingredients/potato.webp'
import riceImage from '@/assets/images/ingredients/rice.webp'
import tomatoImage from '@/assets/images/ingredients/tomato.webp'
import cabbageImage from '@/assets/images/ingredients/cabbage.webp'
import spinachImage from '@/assets/images/ingredients/spinach.webp'
import tofuImage from '@/assets/images/ingredients/tofu.webp'
import cilantroImage from '@/assets/images/ingredients/cilantro.webp'
import greenChiliImage from '@/assets/images/ingredients/green-chili.webp'
import gingerImage from '@/assets/images/ingredients/ginger.webp'
import doubanjiangImage from '@/assets/images/ingredients/doubanjiang.webp'

const router = useRouter()
const identifiedItems = ref([])
const saving = ref(false), saveError = ref(''), pendingWrite = ref(false)
const currentUserId = ref(null)

const foodConfig = {
  beef: { cn: '牛肉', category: 'meat', image: beefImage },
  carrot: { cn: '胡萝卜', category: 'vegetable', image: carrotImage },
  chicken: { cn: '鸡肉', category: 'meat', image: chickenImage },
  chili: { cn: '辣椒', category: 'vegetable', image: chiliImage },
  egg: { cn: '鸡蛋', category: 'dairy', image: eggImage },
  garlic: { cn: '大蒜', category: 'vegetable', image: garlicImage },
  kimchi: { cn: '泡菜', category: 'other', image: kimchiImage },
  leek: { cn: '韭菜', category: 'vegetable', image: leekImage },
  onion: { cn: '洋葱', category: 'vegetable', image: onionImage },
  potato: { cn: '土豆', category: 'vegetable', image: potatoImage },
  tomato: { cn: '西红柿', category: 'vegetable', image: tomatoImage },
  lettuce: { cn: '生菜', category: 'vegetable', image: lettuceImage },
  broccoli: { cn: '西兰花', category: 'vegetable', image: broccoliImage },
  rice: { cn: '大米', category: 'staple', image: riceImage },
  milk: { cn: '牛奶', category: 'dairy', image: milkImage },
  cabbage: { cn: '白菜', category: 'vegetable', image: cabbageImage },
  spinach: { cn: '菠菜', category: 'vegetable', image: spinachImage },
  tofu: { cn: '豆腐', category: 'other', image: tofuImage },
  cilantro: { cn: '香菜', category: 'vegetable', image: cilantroImage },
  green_chili: { cn: '青辣椒', category: 'vegetable', image: greenChiliImage },
  ginger: { cn: '生姜', category: 'vegetable', image: gingerImage },
  doubanjiang: { cn: '豆瓣酱', category: 'condiment', image: doubanjiangImage }
}

const nameAliases = {
  牛肉: 'beef', 牛排: 'beef',
  胡萝卜: 'carrot',
  鸡肉: 'chicken', 鸡胸肉: 'chicken',
  辣椒: 'chili', 红辣椒: 'chili', 青辣椒: 'green_chili', 青椒: 'green_chili',
  鸡蛋: 'egg', 蛋: 'egg',
  大蒜: 'garlic', 蒜: 'garlic', 蒜头: 'garlic',
  泡菜: 'kimchi', 韩国泡菜: 'kimchi', 韩式泡菜: 'kimchi', 辣白菜: 'kimchi',
  韭菜: 'leek', 大葱: 'leek',
  洋葱: 'onion', 红洋葱: 'onion', 紫洋葱: 'onion',
  土豆: 'potato', 马铃薯: 'potato',
  番茄: 'tomato', 西红柿: 'tomato', 小番茄: 'tomato', 圣女果: 'tomato',
  生菜: 'lettuce', 西兰花: 'broccoli',
  白菜: 'cabbage', 大白菜: 'cabbage', 菠菜: 'spinach',
  豆腐: 'tofu', 香菜: 'cilantro',
  姜: 'ginger', 生姜: 'ginger', 豆瓣酱: 'doubanjiang',
  大米: 'rice', 米: 'rice',
  牛奶: 'milk', 纯牛奶: 'milk'
}

const categoryAliases = {
  vegetable: 'vegetable', vegetables: 'vegetable', 蔬菜: 'vegetable',
  meat: 'meat', meats: 'meat', 肉类: 'meat',
  dairy: 'dairy', egg: 'dairy', 蛋奶: 'dairy', 蛋类: 'dairy', 奶类: 'dairy',
  staple: 'staple', grain: 'staple', 主食: 'staple', 谷物: 'staple',
  condiment: 'condiment', seasoning: 'condiment', 调料: 'condiment', 调味品: 'condiment',
  other: 'other', 其他: 'other'
}

const normalizeName = (name) => {
  const value = String(name || '').trim()
  return nameAliases[value] || value.toLowerCase()
}

const getFoodInfo = (name) => foodConfig[normalizeName(name)] || { cn: String(name || '未知'), category: 'other', image: '' }
const displayName = (name) => getFoodInfo(name).cn
const getItemCategory = (item) => categoryAliases[String(item.category || '').trim().toLowerCase()] || getFoodInfo(item.name).category
const categoryName = (category) => ({ vegetable: '蔬菜', meat: '肉类', dairy: '蛋奶', staple: '主食', condiment: '调料', other: '其他' }[category] || '其他')

const getItemImage = (item) => {
  const sourceImage = item.image_url || item.image || item.thumbnail
  return sourceImage ? resolveBackendUrl(sourceImage) : getFoodInfo(item.name).image
}

const hideBrokenImage = (event) => { event.currentTarget.style.display = 'none' }

const uniqueIngredientCount = computed(() => new Set(
  identifiedItems.value.map(item => normalizeName(item.name)).filter(Boolean)
).size)

const totalQuantity = computed(() => identifiedItems.value.reduce((total, item) => {
  const quantity = Number(item.quantity)
  return total + (Number.isFinite(quantity) ? quantity : 0)
}, 0))

const minShelfLife = computed(() => {
  const days = identifiedItems.value.map(item => Number(item.shelf_life)).filter(value => Number.isFinite(value) && value > 0)
  return days.length ? Math.min(...days) : '--'
})

const getFreshnessType = (freshness) => {
  if (freshness === '新鲜') return 'success'
  if (freshness === '较新鲜') return 'warning'
  if (freshness === '一般') return 'danger'
  return 'info'
}

const removeItem = (index) => {
  identifiedItems.value.splice(index, 1)
  ElMessage.success('已删除')
}

const goBack = () => { router.back() }
const recognizeAgain = () => { router.push({ path: '/home', query: { tab: 'Manage' } }) }

const confirmSave = async () => {
  if (saving.value || !identifiedItems.value.length) return
  const userId = currentUserId.value
  if (!userId || readUserId() !== userId) return router.push('/login')
  saving.value = true; saveError.value = ''
  try {
    const payload = hasPendingWrite(userId) ? null : identifiedItems.value.map(item => serializeItem(item))
    await saveInventory(userId, payload, null, true)
    if (readUserId() !== userId) return
    pendingWrite.value = false
    clearDraft(userId)
    ElMessage.success('已重新读取库存并确认保存')
    router.push('/home?tab=Manage')
  } catch (error) {
    saveError.value = inventoryErrorMessage(error)
    pendingWrite.value = hasPendingWrite(userId)
  } finally { saving.value = false }
}

function syncDraftUser() {
  const user = readUserId()
  if (user !== currentUserId.value) {
    currentUserId.value = user
    identifiedItems.value = user ? loadDraft(user) : []
    pendingWrite.value = user ? hasPendingWrite(user) : false
    saveError.value = ''
  }
}
const userChanged = event => { if (!event.key || event.key === 'user') syncDraftUser() }
watch(identifiedItems, items => {
  if (!saving.value && currentUserId.value === readUserId() && currentUserId.value) {
    try { saveDraft(currentUserId.value, items) } catch { saveError.value = '草稿无法保存在本机，请保持此页打开' }
  }
}, {deep:true})
onMounted(() => {
  syncDraftUser()
  window.addEventListener('storage', userChanged)
  document.addEventListener('visibilitychange', syncDraftUser)
})
onBeforeUnmount(() => {
  window.removeEventListener('storage', userChanged)
  document.removeEventListener('visibilitychange', syncDraftUser)
})
</script>

<style scoped>
.capture-confirm-page { position: relative; z-index: 1; width: min(100%, var(--ck-page-max)); min-height: 100vh; margin: 0 auto; padding: 0 calc(var(--ck-gutter) + var(--sar)) calc(110px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); color: var(--ck-text); }
button { font: inherit; }
.confirm-hero { padding: 16px 0 6px; }
.status-pill { display: inline-flex; align-items: center; gap: 5px; height: 28px; padding: 0 12px; border-radius: 999px; background: var(--ck-fresh-soft); color: var(--ck-fresh); font-size: 12.5px; font-weight: 600; }
.confirm-hero h1 { margin: 12px 0 4px; font-size: 28px; font-weight: 700; letter-spacing: -0.4px; }
.confirm-hero p { color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }
.summary-strip { display: grid; grid-template-columns: repeat(auto-fit, minmax(0, 1fr)); gap: 4px; margin: 16px 0 0; padding: 14px 6px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.summary-strip div { display: flex; flex-direction: column-reverse; align-items: center; min-width: 0; }
.summary-strip div + div { border-left: 1px solid var(--ck-hairline); }
.summary-strip dt { color: var(--ck-text-3); font-size: 11.5px; white-space: nowrap; }
.summary-strip dd { margin: 0; font-size: 26px; font-weight: 300; }
.summary-strip small { margin-left: 2px; color: var(--ck-text-3); font-size: 12px; }
.confirm-layout { display: flex; flex-direction: column; gap: 12px; margin-top: 16px; }
.ready-label { padding: 3px 10px; border-radius: 999px; background: var(--ck-warn-soft); color: var(--ck-warn); font-size: 12px; font-weight: 600; }
.ck-section-title small { color: var(--ck-text-3); font-size: 13px; font-weight: 400; }
.food-grid { display: grid; gap: 12px; }
.food-card { padding: 14px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.food-card-head { display: flex; align-items: center; gap: 12px; }
.food-image { position: relative; display: grid; place-items: center; width: 56px; height: 56px; flex: 0 0 56px; overflow: hidden; border-radius: 16px; background: var(--ck-fill-strong); color: #4D8B69; }
.food-image img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.freshness-copy { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1 1 auto; }
.freshness-copy b { overflow: hidden; font-size: 16px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.freshness-copy span { display: flex; align-items: center; gap: 6px; color: var(--ck-text-3); font-size: 12px; }
.delete-button { display: inline-flex; align-items: center; gap: 4px; min-height: 36px; padding: 0 12px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill); color: var(--ck-danger-text); font-size: 13px; }
.delete-button:disabled { opacity: 0.45; }
.freshness-note { color: var(--ck-text-3); font-size: 12px; line-height: 1.6; }
.empty-state { display: flex; flex-direction: column; align-items: center; padding: 30px 20px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); text-align: center; }
.empty-state > span { display: grid; place-items: center; width: 60px; height: 60px; border-radius: 20px; background: var(--ck-heat-soft); color: var(--ck-heat); }
.empty-state h2 { margin: 14px 0 4px; font-size: 18px; font-weight: 600; }
.empty-state p { color: var(--ck-text-3); font-size: 13px; }
.empty-state button { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; margin-top: 14px; padding: 0 20px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: #fff; font-weight: 600; }
.tips-card { padding: 16px; border: 1px solid var(--ck-glass-border); border-radius: var(--ck-radius-lg); background: var(--ck-glass); }
.tips-card ul { display: grid; gap: 6px; padding-left: 18px; color: var(--ck-text-2); font-size: 13px; }
.action-bar { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 10px; padding: 12px calc(var(--ck-gutter) + var(--sar)) calc(12px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); border-top: 1px solid var(--ck-hairline); background: var(--ck-nav-bg); -webkit-backdrop-filter: blur(20px); backdrop-filter: blur(20px); }
.secondary-action, .primary-action { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 54px; border-radius: 999px; font-weight: 600; }
.secondary-action { padding: 0 18px; border: 1px solid var(--ck-glass-border); background: var(--ck-fill-strong); color: var(--ck-text); }
.primary-action { border: 0; background: var(--ck-heat-deep); color: #fff; }
.primary-action span { display: flex; flex-direction: column; line-height: 1.25; font-size: 15px; }
.primary-action small { color: var(--ck-hairline); font-size: 11px; font-weight: 400; }
.primary-action:disabled { opacity: 0.45; }
</style>
