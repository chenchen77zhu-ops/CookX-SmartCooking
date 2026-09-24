<template>
  <div class="capture-confirm-page">
    <header class="confirm-hero">
      <button class="back-button" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon><span>返回冰箱</span>
      </button>
      <div class="brand-lockup"><span>Cook</span><strong>X</strong></div>
      <div class="hero-copy">
        <span class="status-pill"><el-icon><CircleCheckFilled /></el-icon>{{ LOCAL_TEST_MODE ? '手动载入示例 · 未识别图片' : 'AI 识别完成' }}</span>
        <h1>识别结果确认</h1>
        <p>请确认识别出的食材及数量，必要时可修改后再加入冰箱</p>
      </div>
    </header>

    <main class="confirm-layout">
      <section class="results-panel">
        <div class="section-heading">
          <div><span class="heading-icon"><el-icon><KnifeFork /></el-icon></span><h2>识别出的食材 <small>（{{ identifiedItems.length }}项）</small></h2></div>
          <span v-if="identifiedItems.length" class="ready-label">等待确认</span>
        </div>

        <div v-if="identifiedItems.length" class="food-grid">
          <article v-for="(item, index) in identifiedItems" :key="index" class="food-card">
            <div class="food-image">
              <span class="image-placeholder"><el-icon><KnifeFork /></el-icon></span>
              <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="displayName(item.name)" loading="lazy" @error="hideBrokenImage" />
              <span class="selected-mark"><el-icon><CircleCheckFilled /></el-icon></span>
            </div>

            <div class="food-card-body">
              <InventoryFields v-model="identifiedItems[index]" :disabled="saving || pendingWrite" />
              <FreshnessCard v-if="item.freshness_detail" :detail="item.freshness_detail" status="success" />
              <p v-else>识别结果仅供确认名称，鲜度数据不足，入库后重新评估。</p>
              <div class="card-footer">
                <div class="freshness-copy"><span>新鲜度</span><el-tag :type="getFreshnessType(item.freshness)" size="small">{{ item.freshness || '数据不足' }}</el-tag></div>
                <button type="button" class="delete-button" aria-label="删除该识别结果" :disabled="saving || pendingWrite" @click="removeItem(index)"><el-icon><Delete /></el-icon>删除</button>
              </div>
            </div>
          </article>
        </div>

        <section v-else class="empty-state">
          <span><el-icon><Picture /></el-icon></span>
          <h2>未检测到可确认的食材</h2>
          <p>请重新选择图片进行识别</p>
          <button type="button" @click="recognizeAgain"><el-icon><RefreshRight /></el-icon>重新识别</button>
        </section>
      </section>

      <aside class="summary-column">
        <section class="summary-card">
          <div class="side-title"><el-icon><Tickets /></el-icon><h2>识别摘要</h2></div>
          <dl>
            <div><dt>待确认食材</dt><dd>{{ identifiedItems.length }}<small>项</small></dd></div>
            <div><dt>食材种类</dt><dd>{{ uniqueIngredientCount }}<small>种</small></dd></div>
            <div><dt>总数量</dt><dd>{{ totalQuantity }}<small>份</small></dd></div>
            <div v-if="identifiedItems.length"><dt>最短保质期</dt><dd>{{ minShelfLife }}<small>天</small></dd></div>
          </dl>
        </section>

        <section class="tips-card">
          <div class="side-title"><el-icon><InfoFilled /></el-icon><h2>识别小贴士</h2></div>
          <ul>
            <li>请确认食材名称和数量</li>
            <li>可修改数量、存储方式和保质期</li>
            <li>确认后会加入我的冰箱</li>
            <li>如识别有误，可删除后重新识别</li>
          </ul>
        </section>
      </aside>
    </main>

    <InventoryWriteStatus :user="currentUserId" :message="saveError" :pending="pendingWrite" :disabled="saving" @released="pendingWrite = false; saveError = ''" />
    <section class="action-bar">
      <button type="button" class="secondary-action" @click="recognizeAgain"><el-icon><RefreshRight /></el-icon>重新识别</button>
      <button type="button" class="primary-action" :disabled="identifiedItems.length === 0 || saving" @click="confirmSave">
        <el-icon><CircleCheckFilled /></el-icon>
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
import { ArrowLeft, Calendar, CircleCheckFilled, Delete, InfoFilled, KnifeFork, Picture, RefreshRight, Tickets } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'
import beefImage from '@/assets/images/ingredients/beef.png'
import broccoliImage from '@/assets/images/ingredients/broccoli.png'
import carrotImage from '@/assets/images/ingredients/carrot.png'
import chickenImage from '@/assets/images/ingredients/chicken.png'
import chiliImage from '@/assets/images/ingredients/chili.png'
import eggImage from '@/assets/images/ingredients/egg.png'
import garlicImage from '@/assets/images/ingredients/garlic.png'
import kimchiImage from '@/assets/images/ingredients/kimchi.png'
import leekImage from '@/assets/images/ingredients/leek.png'
import lettuceImage from '@/assets/images/ingredients/lettuce.png'
import milkImage from '@/assets/images/ingredients/milk.png'
import onionImage from '@/assets/images/ingredients/onion.png'
import potatoImage from '@/assets/images/ingredients/potato.png'
import riceImage from '@/assets/images/ingredients/rice.png'
import tomatoImage from '@/assets/images/ingredients/tomato.png'
import cabbageImage from '@/assets/images/ingredients/cabbage.png'
import spinachImage from '@/assets/images/ingredients/spinach.png'
import tofuImage from '@/assets/images/ingredients/tofu.png'
import cilantroImage from '@/assets/images/ingredients/cilantro.png'
import greenChiliImage from '@/assets/images/ingredients/green-chili.png'
import gingerImage from '@/assets/images/ingredients/ginger.png'
import doubanjiangImage from '@/assets/images/ingredients/doubanjiang.png'

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
.capture-confirm-page {
  min-height: 100vh;
  padding: 0 16px calc(104px + env(safe-area-inset-bottom));
  background:
    radial-gradient(circle at 8% 0, rgba(233, 162, 59, .08), transparent 24%),
    var(--cookx-bg);
  color: var(--cookx-text);
}

button { font: inherit; }

.confirm-hero {
  position: relative;
  width: min(100%, var(--cookx-page-max));
  margin: 0 auto;
  padding: calc(18px + env(safe-area-inset-top)) 0 28px;
  text-align: center;
}

.back-button {
  position: absolute;
  top: calc(18px + env(safe-area-inset-top));
  left: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 40px;
  padding: 0 10px;
  border: 0;
  border-radius: 12px;
  background: transparent;
  color: var(--cookx-primary-dark);
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
}

.back-button:hover { background: rgba(23, 63, 53, .06); }
.brand-lockup { font-size: 25px; font-weight: 780; letter-spacing: -.8px; }
.brand-lockup strong { color: var(--cookx-accent); }
.hero-copy { margin-top: 22px; }
.status-pill { display: inline-flex; align-items: center; gap: 5px; padding: 6px 10px; border-radius: 999px; background: #eaf3e8; color: var(--cookx-success); font-size: 10px; font-weight: 680; }
.hero-copy h1 { margin: 10px 0 7px; font-size: clamp(26px, 4vw, 37px); line-height: 1.15; letter-spacing: -1px; }
.hero-copy p { margin: 0; color: var(--cookx-text-secondary); font-size: 12px; line-height: 1.6; }

.confirm-layout {
  display: grid;
  width: min(100%, var(--cookx-page-max));
  margin: 0 auto;
  gap: 16px;
}

.results-panel,
.summary-card,
.tips-card {
  border: var(--cookx-border);
  border-radius: 22px;
  background: rgba(255, 255, 255, .90);
  box-shadow: var(--cookx-shadow);
}

.results-panel { min-width: 0; padding: 16px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 15px; }
.section-heading > div { display: flex; align-items: center; gap: 9px; }
.heading-icon { display: grid; width: 34px; height: 34px; border-radius: 11px; background: #eaf3e8; color: var(--cookx-success); place-items: center; }
.section-heading h2 { margin: 0; font-size: 17px; }
.section-heading h2 small { color: var(--cookx-text-secondary); font-size: 11px; font-weight: 500; }
.ready-label { padding: 5px 9px; border-radius: 999px; background: #f0f5ed; color: var(--cookx-success); font-size: 9px; }

.food-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.food-card { min-width: 0; overflow: hidden; border: 1px solid rgba(23, 63, 53, .09); border-radius: 18px; background: #fff; box-shadow: 0 8px 24px rgba(28, 48, 40, .06); }
.food-image { position: relative; display: grid; height: 132px; overflow: hidden; background: linear-gradient(145deg, #f8f5ed, #eeeae0); color: rgba(23, 63, 53, .35); font-size: 30px; place-items: center; }
.food-image img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.image-placeholder { display: grid; width: 50px; height: 50px; border: 1px solid rgba(23, 63, 53, .08); border-radius: 16px; background: rgba(255, 255, 255, .62); place-items: center; }
.selected-mark { position: absolute; z-index: 2; top: 9px; left: 9px; display: grid; width: 23px; height: 23px; border: 2px solid #fff; border-radius: 50%; background: #fff; color: var(--cookx-success); font-size: 22px; place-items: center; }
.food-card-body { padding: 12px; }
.name-line { display: flex; align-items: center; gap: 8px; }
.name-input { min-width: 0; }
.name-input :deep(.el-input__wrapper) { padding: 0; box-shadow: none !important; background: transparent; }
.name-input :deep(.el-input__inner) { height: 30px; color: var(--cookx-text); font-size: 15px; font-weight: 700; }
.category-chip { flex: 0 0 auto; padding: 4px 7px; border-radius: 999px; background: #eaf3e8; color: var(--cookx-success); font-size: 8px; font-weight: 650; }
.category-chip.category-meat { background: #fff0ee; color: #c9574d; }
.category-chip.category-dairy { background: #edf6fb; color: #397ca2; }
.category-chip.category-staple { background: #fff3e4; color: #bd702f; }
.category-chip.category-condiment { background: #fff3e4; color: #bd702f; }
.category-chip.category-other { background: #f0f2ef; color: var(--cookx-text-secondary); }

.control-block { margin-top: 13px; }
.control-block > label { display: block; margin-bottom: 6px; color: var(--cookx-text-secondary); font-size: 9px; }
.quantity-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px; }
.unit-chip { display: grid; min-width: 42px; border: var(--cookx-border); border-radius: 11px; background: #faf9f5; color: var(--cookx-text-secondary); font-size: 11px; place-items: center; }
.control-block :deep(.el-input-number) { width: 100%; }
.control-block :deep(.el-input-number .el-input__wrapper) { min-height: 38px; border-radius: 11px; box-shadow: 0 0 0 1px rgba(23, 63, 53, .10) inset; }
.storage-row :deep(.el-radio-group) { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); width: 100%; }
.storage-row :deep(.el-radio-button__inner) { width: 100%; min-height: 36px; border-color: rgba(23, 63, 53, .10); background: #faf9f5; color: var(--cookx-text-secondary); box-shadow: none; }
.storage-row :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { border-color: var(--cookx-primary); background: var(--cookx-primary); color: #fff; box-shadow: -1px 0 0 0 var(--cookx-primary); }
.shelf-life-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding-top: 11px; border-top: var(--cookx-border); }
.shelf-life-row > label { display: inline-flex; align-items: center; gap: 5px; margin: 0; }
.shelf-life-row > div { display: flex; align-items: center; gap: 5px; color: var(--cookx-text-secondary); font-size: 10px; }
.shelf-life-row :deep(.el-input-number) { width: 94px; }
.card-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 12px; padding-top: 10px; border-top: var(--cookx-border); }
.freshness-copy { display: flex; align-items: center; gap: 6px; color: var(--cookx-text-secondary); font-size: 9px; }
.delete-button { display: inline-flex; align-items: center; gap: 4px; min-height: 32px; padding: 0 8px; border: 0; border-radius: 9px; background: #fff3f1; color: var(--cookx-danger); font-size: 10px; cursor: pointer; }

.summary-column { display: grid; gap: 14px; align-content: start; }
.summary-card,
.tips-card { padding: 18px; }
.side-title { display: flex; align-items: center; gap: 8px; color: var(--cookx-primary); }
.side-title > .el-icon { font-size: 19px; }
.side-title h2 { margin: 0; color: var(--cookx-text); font-size: 16px; }
.summary-card dl { margin: 16px 0 0; }
.summary-card dl > div { display: flex; align-items: center; justify-content: space-between; min-height: 49px; border-bottom: var(--cookx-border); }
.summary-card dl > div:last-child { border-bottom: 0; }
.summary-card dt { color: var(--cookx-text-secondary); font-size: 11px; }
.summary-card dd { margin: 0; color: var(--cookx-primary); font-size: 22px; font-weight: 760; }
.summary-card dd small { margin-left: 3px; font-size: 9px; font-weight: 550; }
.tips-card { background: linear-gradient(145deg, #fff, #f2f7ef); }
.tips-card ul { margin: 15px 0 0; padding-left: 17px; color: var(--cookx-text-secondary); font-size: 10px; line-height: 2; }

.empty-state { padding: 52px 18px; text-align: center; }
.empty-state > span { display: grid; width: 58px; height: 58px; margin: 0 auto 14px; border-radius: 18px; background: #e8efe8; color: var(--cookx-primary); font-size: 25px; place-items: center; }
.empty-state h2 { margin: 0; font-size: 17px; }
.empty-state p { margin: 7px 0 16px; color: var(--cookx-text-secondary); font-size: 11px; }
.empty-state button { display: inline-flex; align-items: center; gap: 5px; min-height: 44px; padding: 0 15px; border: 0; border-radius: 13px; background: var(--cookx-primary); color: #fff; cursor: pointer; }

.action-bar { display: grid; width: min(100%, var(--cookx-page-max)); margin: 17px auto 0; gap: 10px; }
.action-bar button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 52px; border-radius: 15px; font-weight: 680; cursor: pointer; }
.secondary-action { border: var(--cookx-border); background: #fff; color: var(--cookx-primary); }
.primary-action { border: 0; background: var(--cookx-primary); color: #fff; box-shadow: 0 10px 24px rgba(23, 63, 53, .20); }
.primary-action span { display: flex; flex-direction: column; }
.primary-action small { margin-top: 2px; color: rgba(255, 255, 255, .66); font-size: 8px; font-weight: 450; }
.primary-action:disabled { opacity: .45; cursor: not-allowed; box-shadow: none; }

@media (min-width: 760px) {
  .capture-confirm-page { padding-right: 24px; padding-left: 24px; }
  .confirm-layout { grid-template-columns: minmax(0, 1fr) 260px; }
  .results-panel { padding: 20px; }
  .food-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
  .food-image { height: 155px; }
  .action-bar { grid-template-columns: 220px minmax(0, 1fr); }
}

@media (max-width: 759px) {
  .food-grid { grid-template-columns: minmax(0, 1fr); }
}

@media (max-width: 380px) {
  .capture-confirm-page { padding-right: 11px; padding-left: 11px; }
  .back-button span { display: none; }
  .brand-lockup { font-size: 22px; }
  .food-grid { gap: 9px; }
  .food-card-body { padding: 10px; }
  .food-image { height: 112px; }
  .shelf-life-row { align-items: flex-start; flex-direction: column; }
  .shelf-life-row > div { width: 100%; }
  .shelf-life-row :deep(.el-input-number) { width: 100%; }
}

@media (max-width: 320px) {
  .food-grid { grid-template-columns: 1fr; }
}
</style>
