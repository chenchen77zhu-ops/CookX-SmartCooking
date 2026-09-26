<template>
  <div v-loading="inventoryLoading" :class="['manage-container', `is-${isItems ? 'items' : 'overview'}`]">
    <!-- ============ 冰箱总览（图二右） ============ -->
    <template v-if="!isItems">
      <header class="ck-head">
        <div class="ck-wordmark">Cook<b>X</b></div>
        <button v-if="LOCAL_TEST_MODE" class="ck-btn ck-btn--ghost" @click="loadRecognitionExample(router)">载入识别示例</button>
        <el-upload v-else
          :action="`${API_BASE_URL}/analyze-fridge`"
          :before-upload="beforeRecognitionUpload"
          :on-progress="handleRecognitionProgress"
          :on-success="handleUploadSuccess"
          :on-error="handleRecognitionError"
          :disabled="isRecognizing"
          :show-file-list="false"
          accept="image/*"
          class="recognize-fab"
        >
          <span class="recognize-button"><CkIcon name="camera" :size="20" />识别食材</span>
        </el-upload>
      </header>
      <div class="ck-title"><h1>冰箱</h1><p>食材新鲜，生活更健康</p></div>

      <section class="fridge-card">
        <div class="fridge-card__copy">
          <h2>我的冰箱</h2>
          <button type="button" class="fridge-card__more" @click="openItems()">查看详情 <CkIcon name="chevron-right" :size="14" /></button>
          <div class="fridge-stats">
            <div><CkIcon name="leaf" :size="22" /><strong class="ck-num">{{ inventoryKindCount }}<small>种</small></strong><span>食材种类</span></div>
            <div><CkIcon name="bag" :size="22" /><strong class="ck-num">{{ totalQuantity }}<small>件</small></strong><span>库存计数</span></div>
            <div><CkIcon name="clock" :size="22" class="is-warn" /><strong class="ck-num">{{ expiringCount }}<small>件</small></strong><span>即将过期</span></div>
          </div>
        </div>
        <img class="fridge-art" :src="fridgeIllustration" alt="" />
        <button class="add-food-button" type="button" @click="showAddDialog = true"><CkIcon name="plus" :size="18" :stroke="2.2" /><span>添加食材</span></button>
      </section>

      <el-alert v-if="inventoryError" title="库存加载失败，当前显示上次库存；鲜度需重新评估" type="error" :closable="false" class="gap-alert" />
      <el-alert v-if="freshness.status === 'error'" :title="freshness.error" type="warning" :closable="false" class="gap-alert" />

      <section v-if="!inventory.length && !inventoryLoading && !inventoryError" class="ck-card empty-card">
        <span class="empty-card__icon"><CkIcon name="fridge" :size="26" /></span>
        <h2>冰箱还是空的</h2>
        <p>拍一张冰箱照片，或手动添加食材，CookX 就能开始为你规划下一餐。</p>
      </section>

      <template v-else>
        <section class="ck-card expiry-card">
          <div class="ck-section-title"><span>临期食材提醒</span><button type="button" class="ck-link" @click="openItems('expiring')">查看全部 <CkIcon name="chevron-right" :size="14" /></button></div>
          <div v-if="expiringPreview.length" class="expiry-list">
            <button v-for="item in expiringPreview.slice(0, 2)" :key="item.id" type="button" @click="openItems(); focusItem(item)">
              <span class="mini-food-visual">
                <CkIcon name="leaf" :size="18" />
                <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="getFoodInfo(item.name).cn" @error="hideBrokenImage" />
              </span>
              <span class="expiry-copy"><strong>{{ getFoodInfo(item.name).cn }}</strong><small>{{ expiryStatus(item).description }}</small></span>
              <span :class="['expiry-days', expiryStatus(item).className]">{{ expiryStatus(item).label }}</span>
            </button>
          </div>
          <div v-else class="expiry-empty"><CkIcon name="shield" :size="18" /><span>{{ freshness.status === 'success' ? '未发现已评估的临期项，未知项请补充信息' : '等待鲜度评估' }}</span></div>
        </section>

        <section class="ck-card category-card">
          <div class="ck-section-title"><span>食材分类</span><button type="button" class="ck-link" @click="openItems()">查看全部 <CkIcon name="chevron-right" :size="14" /></button></div>
          <div class="category-grid">
            <button v-for="category in overviewCategories" :key="category.id" type="button" :class="['category-tile', `tone-${category.id}`]" @click="openItems(category.id)">
              <img v-if="category.image" :src="category.image" alt="" />
              <b>{{ category.name }}</b><small>{{ category.count }} 种</small>
            </button>
          </div>
        </section>
      </template>

      <MultiObjectiveRecommendations :inventory-revision="inventoryRevision" :inventory-ready="inventoryReady" :user-id="currentUserId" @manage-inventory="showAddDialog = true" />
    </template>

    <!-- ============ 全部食材 ============ -->
    <template v-else>
      <CkNavBar title="全部食材" back-label="返回冰箱" back-to="/home?tab=Manage">
        <template #right><button class="nav-add" type="button" aria-label="添加食材" @click="showAddDialog = true"><CkIcon name="plus" :size="20" :stroke="2.2" /></button></template>
      </CkNavBar>
      <el-alert v-if="inventoryError" title="库存加载失败，当前显示上次库存；鲜度需重新评估" type="error" :closable="false" class="gap-alert" />
      <el-alert v-if="freshness.status === 'error'" :title="freshness.error" type="warning" :closable="false" class="gap-alert" />
      <section class="inventory-toolbar">
        <el-input v-model="searchKeyword" placeholder="搜索食材名称" :prefix-icon="Search" clearable class="search-input" />
        <el-button class="refresh-btn" :loading="inventoryLoading" @click="fetchInventory()">刷新库存与鲜度</el-button>
      </section>
      <nav class="category-tabs" aria-label="食材分类">
        <button v-for="category in visibleCategories" :key="category.id" type="button" :class="{ active: activeCategory === category.id }" @click="activeCategory = category.id">
          {{ category.name }} <span>{{ category.count }}</span>
        </button>
        <button type="button" :class="{ active: activeCategory === 'expiring' }" @click="activeCategory = 'expiring'">临期 <span>{{ expiringItems.length }}</span></button>
      </nav>
      <p class="last-updated">最后更新：{{ lastUpdatedText }}</p>

      <div v-if="shownInventory.length" class="food-grid">
        <article v-for="item in shownInventory" :key="item.id" :class="['food-card', { open: openedId === item.id }]">
          <button type="button" class="food-card-top" :aria-expanded="openedId === item.id" @click="openedId = openedId === item.id ? null : item.id">
            <span class="food-visual">
              <span class="food-image-placeholder"><CkIcon name="leaf" :size="22" /></span>
              <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="getFoodInfo(item.name).cn" loading="lazy" @error="hideBrokenImage" />
            </span>
            <span class="food-card-body">
              <span class="food-title-row"><h3>{{ getFoodInfo(item.name).cn }}</h3><span :class="['cat-tag', `category-${getItemCategory(item)}`]">{{ categoryName(getItemCategory(item)) }}</span></span>
              <span class="food-meta">{{ getItemMeasureText(item) }}<template v-if="item.storage_type"> · {{ item.storage_type }}</template> · 添加于 {{ formatDate(item.add_time) }}</span>
            </span>
            <span :class="['expiry-days', expiryStatus(item).className]">{{ expiryStatus(item).label }}</span>
          </button>
          <div v-show="openedId === item.id" class="food-detail">
            <FreshnessCard :detail="freshness.items[item.id]" :status="freshness.status" :evaluated-at="freshness.evaluatedAt" />
            <div class="card-actions"><button type="button" aria-label="编辑食材" @click.stop="editItem(item)"><CkIcon name="edit" :size="16" />编辑</button><button type="button" aria-label="删除食材" class="danger" @click.stop="removeItem(item.id)"><CkIcon name="trash" :size="16" />删除</button></div>
          </div>
        </article>
      </div>

      <section v-else-if="inventoryError" class="inventory-empty inventory-error ck-card">
        <span><CkIcon name="info" :size="26" /></span><h2>库存加载失败</h2>
        <p>请检查网络连接后重新加载，已有库存数据不会因此被清空。</p>
        <div class="empty-actions"><button type="button" @click="fetchInventory()"><CkIcon name="refresh" :size="16" />重新加载</button></div>
      </section>
      <section v-else-if="!inventoryLoading" class="inventory-empty ck-card">
        <span><CkIcon name="fridge" :size="26" /></span><h2>{{ inventory.length ? '没有匹配的食材' : '冰箱还是空的' }}</h2>
        <p>{{ inventory.length ? '换个关键词或分类看看吧' : '添加一些食材，CookX 就能开始为你规划下一餐' }}</p>
        <div v-if="!inventory.length" class="empty-actions"><button type="button" @click="showAddDialog = true"><CkIcon name="plus" :size="16" />添加食材</button></div>
      </section>

      <section v-if="inventory.length > 0" class="recommend-section ck-card">
        <div class="ck-section-title"><span>{{ LOCAL_TEST_MODE ? '本地菜谱示例' : 'AI 生成灵感' }}</span><small>{{ LOCAL_TEST_MODE ? '固定演练内容，未调用大模型' : '由大模型生成新的菜谱方案' }}</small></div>
        <div class="recipe-container" v-loading="recLoading">
          <div v-for="(rec, index) in quickRecipes" :key="index" class="recipe-row-card">
            <span class="rec-icon"><CkIcon name="chef" :size="18" /></span>
            <span class="rec-dish-name">{{ rec?.dish_name || '构思中...' }}</span>
            <button type="button" class="rec-go-btn" @click="goToChef(rec.dish_name)">咨询教程<CkIcon name="chevron-right" :size="14" /></button>
          </div>
        </div>
      </section>
    </template>

    <div v-if="recognitionStatus !== 'idle'" class="recognition-overlay" role="status" aria-live="polite">
      <section class="recognition-card">
        <div :class="['recognition-visual', recognitionStatus]">
          <CkIcon v-if="recognitionStatus === 'success'" name="check" :size="30" :stroke="2.4" />
          <CkIcon v-else-if="recognitionStatus === 'error'" name="info" :size="30" />
          <CkIcon v-else name="camera" :size="30" />
          <span v-if="isRecognizing" class="scan-ring"></span>
        </div>
        <h2>{{ recognitionTitle }}</h2>
        <p>{{ recognitionMessage }}</p>
        <div v-if="recognitionStatus === 'uploading'" class="upload-progress">
          <div><span>正在上传图片</span><strong>{{ uploadPercent }}%</strong></div>
          <div class="progress-track"><i :style="{ width: `${uploadPercent}%` }"></i></div>
        </div>
        <div v-else-if="recognitionStatus === 'analyzing'" class="analysis-progress"><i></i></div>
        <strong v-if="isRecognizing" class="elapsed-time">已等待 {{ recognitionElapsedSeconds }} 秒</strong>
        <small v-if="isRecognizing">请不要关闭页面或重复选择图片</small>
        <button v-if="recognitionStatus === 'error'" type="button" @click="resetRecognitionState">返回重试</button>
      </section>
    </div>

    <el-dialog v-model="showAddDialog" title="添加食材" width="92%" center :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
       <InventoryFields v-model="newItem" :disabled="saving || pendingWrite" />
       <InventoryWriteStatus :user="currentUserId" :message="saveError" :pending="pendingWrite" :disabled="saving" @released="pendingWrite = false; saveError = ''" />
       <template #footer>
         <el-button :disabled="saving" @click="showAddDialog = false">取消</el-button>
         <el-button type="primary" :loading="saving" @click="saveNewItem">确认添加</el-button>
       </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="修改信息" width="92%" center :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving">
       <InventoryFields v-model="editingItem" editing :original="editingOriginal" :disabled="saving || pendingWrite" />
       <InventoryWriteStatus :user="currentUserId" :message="saveError" :pending="pendingWrite" :disabled="saving" @released="pendingWrite = false; saveError = ''" />
       <template #footer>
         <el-button :disabled="saving" @click="editDialogVisible = false">取消</el-button>
         <el-button type="primary" :loading="saving" @click="saveEdit">保存修改</el-button>
       </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {loadRecognitionExample} from '../localtest/helpers.js'
import {LOCAL_TEST_MODE} from '../config/buildMode.js'
import fridgeIllustration from '@/assets/illustrations/fridge.webp'
import { saveDraft, recognitionItem } from '../services/recognitionDraft.js'
import InventoryFields from '../components/InventoryFields.vue'
import InventoryWriteStatus from '../components/InventoryWriteStatus.vue'
import { blankItem, inventoryEditForm, serializeItem, inventoryErrorMessage } from '../services/inventoryFields.js'
import { deleteInventory, saveInventory, hasPendingWrite } from '../api/inventoryWrites.js'
import FreshnessCard from '../components/FreshnessCard.vue'
import { getInventoryFreshness } from '../api/freshness.js'
import { createFreshnessLoader, emptyFreshness, freshnessStatus } from '../services/inventoryFreshness.js'
import { calculateDaysUntilExpiry } from '../services/inventoryExpiry.js'
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { Search } from '@element-plus/icons-vue'
import CkIcon from '@/components/ck/CkIcon.vue'
import CkNavBar from '@/components/ck/CkNavBar.vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'
import MultiObjectiveRecommendations from '@/components/MultiObjectiveRecommendations.vue'
import tomatoImage from '@/assets/images/ingredients/tomato.webp'
import potatoImage from '@/assets/images/ingredients/potato.webp'
import carrotImage from '@/assets/images/ingredients/carrot.webp'
import onionImage from '@/assets/images/ingredients/onion.webp'
import eggImage from '@/assets/images/ingredients/egg.webp'
import chickenImage from '@/assets/images/ingredients/chicken.webp'
import lettuceImage from '@/assets/images/ingredients/lettuce.webp'
import broccoliImage from '@/assets/images/ingredients/broccoli.webp'
import riceImage from '@/assets/images/ingredients/rice.webp'
import milkImage from '@/assets/images/ingredients/milk.webp'
import beefImage from '@/assets/images/ingredients/beef.webp'
import chiliImage from '@/assets/images/ingredients/chili.webp'
import garlicImage from '@/assets/images/ingredients/garlic.webp'
import kimchiImage from '@/assets/images/ingredients/kimchi.webp'
import leekImage from '@/assets/images/ingredients/leek.webp'
import cabbageImage from '@/assets/images/ingredients/cabbage.webp'
import spinachImage from '@/assets/images/ingredients/spinach.webp'
import tofuImage from '@/assets/images/ingredients/tofu.webp'
import cilantroImage from '@/assets/images/ingredients/cilantro.webp'
import greenChiliImage from '@/assets/images/ingredients/green-chili.webp'
import gingerImage from '@/assets/images/ingredients/ginger.webp'
import doubanjiangImage from '@/assets/images/ingredients/doubanjiang.webp'

const router = useRouter()
const inventory = ref([])
const freshness = ref(emptyFreshness())
const freshnessLoader = createFreshnessLoader(getInventoryFreshness, value => { freshness.value = value })
const searchKeyword = ref('')
const activeCategory = ref('all')
const editDialogVisible = ref(false)
const editingItem = ref({})
const editingOriginal = ref({})
const saving = ref(false)
const saveError = ref('')
const pendingWrite = ref(false)
const quickRecipes = ref([])
const recLoading = ref(false)
const showAddDialog = ref(false)
const lastUpdatedAt = ref(null)
const recognitionStatus = ref('idle')
const uploadPercent = ref(0)
const recognitionElapsedSeconds = ref(0)
const recognizedItemCount = ref(0)
const currentUserId = ref(null)
const inventoryLoading = ref(true)
const inventoryError = ref(false)
const inventoryRevision = ref(0)
const inventoryReady = ref(false)
let uploadUserId = null
let quickVersion = 0
let recognitionTimer = null
let recognitionSuccessTimer = null
let userWaitTimer = null
let userWaitAttempts = 0
let inventoryRequestVersion = 0
const emit = defineEmits(['consult-recipe', 'clear-pending'])
const props = defineProps({ mode: { type: String, default: 'overview' } })
const route = useRoute()
const isItems = computed(() => props.mode === 'items')
const openedId = ref(null)
if (isItems.value && typeof route.query.category === 'string') activeCategory.value = route.query.category
const openItems = (category) => router.push({ path: '/fridge', query: category ? { category } : {} })
const focusItem = item => { openedId.value = item.id }
const shownInventory = computed(() => activeCategory.value === 'expiring'
  ? expiringItems.value.filter(item => !searchKeyword.value || String(getFoodInfo(item.name).cn).includes(searchKeyword.value))
  : filteredInventory.value)

const readStoredUserId = () => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}').id || null
  } catch (error) {
    console.warn('[INVENTORY] invalid cached user data')
    return null
  }
}

const syncCurrentUserId = () => {
  const id = readStoredUserId()
  console.log('[INVENTORY] current user_id:', id || 'not ready')
  currentUserId.value = id
  return id
}

const waitForCurrentUser = () => {
  if (syncCurrentUserId()) return
  inventoryLoading.value = true
  userWaitAttempts += 1
  if (userWaitAttempts < 20) {
    userWaitTimer = setTimeout(waitForCurrentUser, 100)
  } else {
    router.push('/login')
  }
}

const handleStoredUserChange = (event) => {
  if (event.key === 'user') syncCurrentUserId()
}

const handleVisibilityChange = () => {
  if (document.visibilityState === 'visible') syncCurrentUserId()
}

const isRecognizing = computed(() => ['uploading', 'analyzing'].includes(recognitionStatus.value))

const recognitionTitle = computed(() => {
  if (recognitionStatus.value === 'uploading') return '正在上传图片'
  if (recognitionStatus.value === 'analyzing') return 'CookX 正在识别食材'
  if (recognitionStatus.value === 'success') return '识别完成'
  return '识别失败'
})

const recognitionMessage = computed(() => {
  if (recognitionStatus.value === 'success') return `发现 ${recognizedItemCount.value} 项食材，即将进入确认页`
  if (recognitionStatus.value === 'error') return '请检查网络后重新尝试'
  if (recognitionStatus.value === 'uploading') return '正在安全上传图片，请稍候…'
  if (recognitionElapsedSeconds.value >= 30) return '仍在识别中，请保持网络连接'
  if (recognitionElapsedSeconds.value >= 15) return '图片中的食材较多，本次识别需要一点时间'
  if (recognitionElapsedSeconds.value >= 9) return '正在整理识别结果…'
  if (recognitionElapsedSeconds.value >= 4) return 'AI 正在定位图片中的食材…'
  return 'AI 正在分析图片，请稍候…'
})

const clearRecognitionTimer = () => {
  if (recognitionTimer) {
    clearInterval(recognitionTimer)
    recognitionTimer = null
  }
}

const resetRecognitionState = () => {
  clearRecognitionTimer()
  recognitionStatus.value = 'idle'
  uploadPercent.value = 0
  recognitionElapsedSeconds.value = 0
  recognizedItemCount.value = 0
}

const beforeRecognitionUpload = (file) => {
  uploadUserId = currentUserId.value
  if (!uploadUserId) return false
  if (isRecognizing.value) return false
  console.log('CookX upload file', {
    name: file?.name,
    type: file?.type,
    size: file?.size
  })
  resetRecognitionState()
  recognitionStatus.value = 'uploading'
  recognitionTimer = setInterval(() => { recognitionElapsedSeconds.value += 1 }, 1000)
  return true
}

const handleRecognitionProgress = (event) => {
  const percent = Math.min(100, Math.max(0, Math.round(Number(event?.percent) || 0)))
  uploadPercent.value = percent
  if (percent >= 100) recognitionStatus.value = 'analyzing'
}

const handleRecognitionError = (error) => {
  clearRecognitionTimer()
  recognitionStatus.value = 'error'
  console.error('CookX analyze-fridge upload rejected', {
    message: error?.message,
    code: error?.code,
    status: error?.status
  })
  ElMessage.error('识别失败，请检查网络后重新尝试')
}

const filteredInventory = computed(() => {
  const list = inventory.value.filter(item => {
    const info = getFoodInfo(item.name);
    const matchesSearch = !searchKeyword.value ||
                         info.cn.includes(searchKeyword.value) ||
                         item.name.toLowerCase().includes(searchKeyword.value.toLowerCase());

    const matchesCategory = activeCategory.value === 'all' ||
                           getItemCategory(item) === activeCategory.value;

    return matchesSearch && matchesCategory;
  });
  return list.map(item => {
    return {
      ...item,
      daysUntilExpiry: calculateDaysUntilExpiry(item) // 调用你定义的计算天数函数
    };
  });
});

const newItem = ref(blankItem())
const editItem = item => {
  editingOriginal.value = { ...item }
  editingItem.value = { ...inventoryEditForm(item), name: getFoodInfo(item.name).cn }
  saveError.value = ''
  editDialogVisible.value = true
}

// 分类筛选
const categories = [
  { id: 'all', name: '全部' },
  { id: 'vegetable', name: '蔬菜' },
  { id: 'meat', name: '肉类' },
  { id: 'fruit', name: '水果' },
  { id: 'dairy', name: '蛋奶' },
  { id: 'staple', name: '主食' },
  { id: 'condiment', name: '调料' },
  { id: 'other', name: '其他' }
]

const foodConfig = {
  // 肉类
  'beef': { emoji: '🥩', cn: '牛肉', category: 'meat' },
  'pork': { emoji: '🥩', cn: '猪肉', category: 'meat' },
  'chicken': { emoji: '🍗', cn: '鸡肉', category: 'meat' },
  'duck': { emoji: '🦆', cn: '鸭肉', category: 'meat' },
  'mutton': { emoji: '🐑', cn: '羊肉', category: 'meat' },
  'ribs': { emoji: '🍖', cn: '排骨', category: 'meat' },
  'ham': { emoji: '🥓', cn: '火腿', category: 'meat' },
  'sausage': { emoji: '🌭', cn: '香肠', category: 'meat' },
  'beef_tendon': { emoji: '🥩', cn: '牛筋', category: 'meat' },
  'pig_liver': { emoji: '🫀', cn: '猪肝', category: 'meat' },

  // 蔬菜
  'cabbage': { emoji: '🥬', cn: '白菜', category: 'vegetable' },
  'cilantro': { emoji: '🌿', cn: '香菜', category: 'vegetable' },
  'carrot': { emoji: '🥕', cn: '胡萝卜', category: 'vegetable' },
  'chili': { emoji: '🌶️', cn: '辣椒', category: 'vegetable' },
  'garlic': { emoji: '🧄', cn: '大蒜', category: 'vegetable' },
  'leek': { emoji: '🌿', cn: '韭菜', category: 'vegetable' },
  'onion': { emoji: '🧅', cn: '洋葱', category: 'vegetable' },
  'potato': { emoji: '🥔', cn: '土豆', category: 'vegetable' },
  'tomato': { emoji: '🍅', cn: '西红柿', category: 'vegetable' },
  'cucumber': { emoji: '🥒', cn: '黄瓜', category: 'vegetable' },
  'spinach': { emoji: '🥬', cn: '菠菜', category: 'vegetable' },
  'lettuce': { emoji: '🥗', cn: '生菜', category: 'vegetable' },
  'eggplant': { emoji: '🍆', cn: '茄子', category: 'vegetable' },
  'pumpkin': { emoji: '🎃', cn: '南瓜', category: 'vegetable' },
  'mushroom': { emoji: '🍄', cn: '蘑菇', category: 'vegetable' },
  'ginger': { emoji: '🫚', cn: '生姜', category: 'vegetable' },
  'green_chili': { emoji: '🌶️', cn: '青辣椒', category: 'vegetable' },
  'broccoli': { emoji: '🥦', cn: '西兰花', category: 'vegetable' },
  'green_pepper': { emoji: '🌶️', cn: '青椒', category: 'vegetable' },
  'chive': { emoji: '🌿', cn: '韭菜', category: 'vegetable' },
  'cauliflower': { emoji: '🥦', cn: '花菜', category: 'vegetable' },
  'celery': { emoji: '🌿', cn: '芹菜', category: 'vegetable' },
  'radish': { emoji: '🥕', cn: '白萝卜', category: 'vegetable' },
  'pea': { emoji: '🫛', cn: '豌豆', category: 'vegetable' },
  'corn': { emoji: '🌽', cn: '玉米', category: 'vegetable' },
  'asparagus': { emoji: '🌿', cn: '芦笋', category: 'vegetable' },

  // 水果
  'apple': { emoji: '🍎', cn: '苹果', category: 'fruit' },
  'banana': { emoji: '🍌', cn: '香蕉', category: 'fruit' },
  'orange': { emoji: '🍊', cn: '橙子', category: 'fruit' },
  'grape': { emoji: '🍇', cn: '葡萄', category: 'fruit' },
  'watermelon': { emoji: '🍉', cn: '西瓜', category: 'fruit' },
  'strawberry': { emoji: '🍓', cn: '草莓', category: 'fruit' },
  'mango': { emoji: '🥭', cn: '芒果', category: 'fruit' },
  'pineapple': { emoji: '🍍', cn: '菠萝', category: 'fruit' },
  'peach': { emoji: '🍑', cn: '桃子', category: 'fruit' },
  'pear': { emoji: '🍐', cn: '梨子', category: 'fruit' },
  'lemon': { emoji: '🍋', cn: '柠檬', category: 'fruit' },
  'cherry': { emoji: '🍒', cn: '樱桃', category: 'fruit' },
  'kiwi': { emoji: '🥝', cn: '猕猴桃', category: 'fruit' },
  'blueberry': { emoji: '🫐', cn: '蓝莓', category: 'fruit' },
  'coconut': { emoji: '🥥', cn: '椰子', category: 'fruit' },
  'durian': { emoji: '🥮', cn: '榴莲', category: 'fruit' },
  'avocado': { emoji: '🥑', cn: '牛油果', category: 'fruit' },

  // 其他
  'egg': { emoji: '🥚', cn: '鸡蛋', category: 'dairy' },
  'milk': { emoji: '🥛', cn: '牛奶', category: 'dairy' },
  'yogurt': { emoji: '🥛', cn: '酸奶', category: 'dairy' },
  'cheese': { emoji: '🧀', cn: '奶酪', category: 'dairy' },
  'tofu': { emoji: '🧈', cn: '豆腐', category: 'other' },
  'rice': { emoji: '🍚', cn: '大米', category: 'staple' },
  'noodle': { emoji: '🍜', cn: '面条', category: 'staple' },
  'flour': { emoji: '🍚', cn: '面粉', category: 'staple' },
  'mantou': { emoji: '🍞', cn: '馒头', category: 'staple' },
  'salt': { emoji: '🧂', cn: '盐', category: 'condiment' },
  'sugar': { emoji: '🧂', cn: '糖', category: 'condiment' },
  'soy_sauce': { emoji: '🧂', cn: '酱油', category: 'condiment' },
  'vinegar': { emoji: '🧂', cn: '醋', category: 'condiment' },
  'cooking_wine': { emoji: '🧂', cn: '料酒', category: 'condiment' },
  'cooking_oil': { emoji: '🧂', cn: '食用油', category: 'condiment' },
  'chili_sauce': { emoji: '🧂', cn: '辣椒酱', category: 'condiment' },
  'kimchi': { emoji: '🥬', cn: '泡菜', category: 'other' },
  'doubanjiang': { emoji: '🧂', cn: '豆瓣酱', category: 'condiment' }
}

// 中文到英文的反向映射
const cnToEnMap = {
  // 肉类
  '牛肉': 'beef',
  '牛排': 'beef',
  '猪肉': 'pork',
  '鸡肉': 'chicken',
  '鸡胸肉': 'chicken',
  '鸭肉': 'duck',
  '羊肉': 'mutton',
  '排骨': 'ribs',
  '火腿': 'ham',
  '香肠': 'sausage',
  '牛筋': 'beef_tendon',
  '猪肝': 'pig_liver',

  // 蔬菜
  '白菜': 'cabbage',
  '大白菜': 'cabbage',
  '香菜': 'cilantro',
  '胡萝卜': 'carrot',
  '辣椒': 'chili',
  '红辣椒': 'chili',
  '青辣椒': 'green_chili',
  '青椒': 'green_chili',
  '大蒜': 'garlic',
  '蒜': 'garlic',
  '蒜头': 'garlic',
  '韭菜': 'leek',
  '大葱': 'leek',
  '洋葱': 'onion',
  '土豆': 'potato',
  '马铃薯': 'potato',
  '番茄': 'tomato',
  '西红柿': 'tomato',
  '小番茄': 'tomato',
  '圣女果': 'tomato',
  '黄瓜': 'cucumber',
  '菠菜': 'spinach',
  '生菜': 'lettuce',
  '茄子': 'eggplant',
  '南瓜': 'pumpkin',
  '蘑菇': 'mushroom',
  '生姜': 'ginger',
  '姜': 'ginger',
  '西兰花': 'broccoli',
  '花菜': 'cauliflower',
  '芹菜': 'celery',
  '白萝卜': 'radish',
  '豌豆': 'pea',
  '玉米': 'corn',
  '芦笋': 'asparagus',

  // 水果
  '苹果': 'apple',
  '香蕉': 'banana',
  '橙子': 'orange',
  '葡萄': 'grape',
  '西瓜': 'watermelon',
  '草莓': 'strawberry',
  '芒果': 'mango',
  '菠萝': 'pineapple',
  '桃子': 'peach',
  '梨子': 'pear',
  '柠檬': 'lemon',
  '樱桃': 'cherry',
  '猕猴桃': 'kiwi',
  '蓝莓': 'blueberry',
  '椰子': 'coconut',
  '榴莲': 'durian',
  '牛油果': 'avocado',

  // 其他
  '鸡蛋': 'egg',
  '蛋': 'egg',
  '鸭蛋': 'egg',
  '牛奶': 'milk',
  '纯牛奶': 'milk',
  '酸奶': 'yogurt',
  '奶酪': 'cheese',
  '豆腐': 'tofu',
  '大米': 'rice',
  '米': 'rice',
  '面条': 'noodle',
  '挂面': 'noodle',
  '面粉': 'flour',
  '馒头': 'mantou',
  '盐': 'salt',
  '食盐': 'salt',
  '糖': 'sugar',
  '白糖': 'sugar',
  '酱油': 'soy_sauce',
  '醋': 'vinegar',
  '料酒': 'cooking_wine',
  '食用油': 'cooking_oil',
  '辣椒酱': 'chili_sauce',
  '泡菜': 'kimchi',
  '韩国泡菜': 'kimchi',
  '辣白菜': 'kimchi',
  '韩式泡菜': 'kimchi',
  '豆瓣酱': 'doubanjiang',
  '红洋葱': 'onion',
  '紫洋葱': 'onion'
}

// 将中文转换为英文的函数
const convertCnToEn = (input) => {
  if (!input) return '';
  const trimmed = input.trim();
  // 如果已经是英文，直接返回小写
  if (/^[a-zA-Z]+$/.test(trimmed)) {
    return trimmed.toLowerCase();
  }
  // 如果是中文，查找映射表
  return cnToEnMap[trimmed] || trimmed.toLowerCase();
}

const getFoodInfo = (nameFromBackend) => {
  if (!nameFromBackend) return { emoji: '🍱', cn: '未知', category: 'other' };

  const enKey = convertCnToEn(nameFromBackend);

  if (foodConfig[enKey]) {
    return foodConfig[enKey];
  }
  return {
    emoji: '🍱',
    cn: nameFromBackend,
    category: 'other'
  };
}

// 计算剩余保质期天数


const categoryAliases = {
  vegetable: 'vegetable', vegetables: 'vegetable', '蔬菜': 'vegetable',
  meat: 'meat', meats: 'meat', '肉类': 'meat', '肉': 'meat',
  fruit: 'fruit', fruits: 'fruit', '水果': 'fruit',
  dairy: 'dairy', egg: 'dairy', '蛋奶': 'dairy', '蛋类': 'dairy', '奶类': 'dairy',
  staple: 'staple', grain: 'staple', '主食': 'staple', '谷物': 'staple',
  condiment: 'condiment', seasoning: 'condiment', '调料': 'condiment', '调味品': 'condiment',
  other: 'other', '其他': 'other'
}

const getItemCategory = (item) => {
  const backendCategory = String(item?.category || '').trim().toLowerCase()
  return categoryAliases[backendCategory] || getFoodInfo(item?.name).category
}

const ingredientImages = {
  beef: beefImage,
  tomato: tomatoImage,
  potato: potatoImage,
  carrot: carrotImage,
  onion: onionImage,
  egg: eggImage,
  chicken: chickenImage,
  chili: chiliImage,
  green_pepper: greenChiliImage,
  green_chili: greenChiliImage,
  garlic: garlicImage,
  kimchi: kimchiImage,
  leek: leekImage,
  chive: leekImage,
  lettuce: lettuceImage,
  broccoli: broccoliImage,
  rice: riceImage,
  milk: milkImage,
  cabbage: cabbageImage,
  spinach: spinachImage,
  tofu: tofuImage,
  cilantro: cilantroImage,
  ginger: gingerImage,
  doubanjiang: doubanjiangImage
}

const formatMeasure = (value, unit) => {
  if (typeof value === 'string' && /[a-zA-Z\u4e00-\u9fa5]/.test(value.trim())) return value.trim()
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0) return ''
  return `${Number.isInteger(number) ? number : Number(number.toFixed(1))}${unit}`
}

const getItemMeasureText = (item) => {
  const quantity = Number(item?.quantity)
  const quantityText = `${Number.isFinite(quantity) ? quantity : 0} ${item?.unit || item?.quantity_unit || '库存计数'}`

  const actualWeight = item?.weight_g ?? item?.grams
  if (actualWeight != null) return `${quantityText} · ${formatMeasure(actualWeight, 'g')}`
  const actualVolume = item?.volume_ml ?? item?.ml
  if (actualVolume != null) return `${quantityText} · ${formatMeasure(actualVolume, 'ml')}`
  if (item?.weight != null) return `${quantityText} · ${formatMeasure(item.weight, item.weight_unit || item.unit || '单位未知')}`
  if (item?.volume != null) return `${quantityText} · ${formatMeasure(item.volume, item.volume_unit || item.unit || '单位未知')}`
  if (item?.amount != null && item?.unit) return formatMeasure(item.amount, ` ${item.unit}`)
  if (item?.unit && item.unit !== '份') return `${Number.isFinite(quantity) ? quantity : 0} ${item.unit}`

  return quantityText
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return `${date.getMonth() + 1}-${date.getDate()}`;
}

const inventoryKindCount = computed(() => new Set(
  inventory.value.map(item => String(item.name || '').trim().toLowerCase()).filter(Boolean)
).size)

const totalQuantity = computed(() => inventory.value.reduce((total, item) => {
  const quantity = Number(item.quantity)
  return total + (Number.isFinite(quantity) ? quantity : 0)
}, 0))

const expiringItems = computed(() => inventory.value.filter(item => {
  const detail = freshness.value.items[item.id]
  return detail?.expired || detail?.critical || detail?.expiring_soon
}))
const countKinds = predicate => freshness.value.status === 'success'
  ? new Set(inventory.value.filter(item => predicate(freshness.value.items[item.id])).map(item => item.name)).size : '—'
const expiringCount = computed(() => countKinds(d => d && !d.expired && (d.critical || d.expiring_soon)))
const expiredCount = computed(() => countKinds(d => d?.expired))

const expiringPreview = computed(() => expiringItems.value.slice(0, 3))

const visibleCategories = computed(() => categories.map(category => ({
  ...category,
  count: category.id === 'all'
    ? inventory.value.length
    : inventory.value.filter(item => getItemCategory(item) === category.id).length
})).filter(category => category.id === 'all' || category.count > 0))

const CATEGORY_IMAGES = { vegetable: broccoliImage, meat: beefImage, dairy: eggImage, staple: riceImage, fruit: tomatoImage, condiment: garlicImage, other: tofuImage }
// 总览只展示 4 个分类：优先有库存的分类
const overviewCategories = computed(() => {
  const counts = categories.filter(c => c.id !== 'all').map(c => ({ ...c, count: new Set(inventory.value.filter(item => getItemCategory(item) === c.id).map(item => getFoodInfo(item.name).cn)).size, image: CATEGORY_IMAGES[c.id] }))
  return [...counts].sort((a, b) => b.count - a.count).slice(0, 4)
})
const lastUpdatedText = computed(() => {
  if (!lastUpdatedAt.value) return '等待同步'
  return lastUpdatedAt.value.toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  })
})

const categoryName = (category) => categories.find(item => item.id === category)?.name || '其他'

const expiryStatus = item => freshnessStatus(freshness.value.items[item.id])

const getItemImage = (item) => {
  const imageUrl = item.image_url || item.image || item.thumbnail
  if (imageUrl) return resolveBackendUrl(imageUrl)
  return ingredientImages[convertCnToEn(item.name)] || ''
}

const hideBrokenImage = (event) => { event.currentTarget.style.display = 'none' }

const fetchInventory = async (requestedUserId = currentUserId.value) => {
  if (!requestedUserId) {
    inventoryLoading.value = true
    return
  }
  freshnessLoader.reset()
  inventoryReady.value = false
  inventoryRevision.value++
  quickVersion++
  quickRecipes.value = []
  recLoading.value = false
  const requestVersion = ++inventoryRequestVersion
  inventoryLoading.value = true
  inventoryError.value = false
  console.log(`[INVENTORY] request start user_id=${requestedUserId} version=${requestVersion}`)
  try {
    const res = await axios.get(`${API_BASE_URL}/inventory`,
    {
      params: { user_id: requestedUserId }, timeout: 15000
    });
    if (requestVersion !== inventoryRequestVersion || requestedUserId !== currentUserId.value) {
      console.log(`[INVENTORY] discard stale response version=${requestVersion} currentVersion=${inventoryRequestVersion}`)
      return
    }
    if (!Array.isArray(res.data)) throw new Error('库存响应异常')
    const receivedInventory = res.data
    console.log(`[INVENTORY] response user_id=${requestedUserId} version=${requestVersion} status=${res.status} count=${receivedInventory.length}`)
    inventory.value = receivedInventory.map(item => ({
      ...item,
      daysUntilExpiry: calculateDaysUntilExpiry(item)
    }));
    inventoryReady.value = true
    lastUpdatedAt.value = new Date();
    freshnessLoader.load(requestedUserId, inventory.value);
    if (inventory.value.length > 0 && isItems.value) fetchQuickRecipes();
  } catch (error) {
    if (requestVersion !== inventoryRequestVersion || requestedUserId !== currentUserId.value) return
    console.error("同步失败", error);
    inventoryError.value = true
  } finally {
    if (requestVersion === inventoryRequestVersion && requestedUserId === currentUserId.value) {
      inventoryLoading.value = false
    }
  }
};

const goToChef = (dishName) => {
  if (!dishName) return;
  if (isItems.value) { router.push({ path: '/home', query: { tab: 'Recipes', dish: dishName } }); return }
  // 向父组件 (App.vue) 发送事件，通知它切换页面并传递菜名
  emit('consult-recipe', dishName);
}

const fetchQuickRecipes = async () => {
  const userId = currentUserId.value
  if (!userId || inventory.value.length === 0) return;
  const version = ++quickVersion
  recLoading.value = true;

  try {
    const res = await axios.get(`${API_BASE_URL}/recommend-recipe`, {
      params: {
        user_id: userId,
        user_prompt: "根据库存推荐1个中文菜名。只需JSON格式: {\"dish_name\":\"菜名\",\"used_main\":\"主要食材\"}",
        save_history: false
      }, timeout: 15000
    });

    if (version !== quickVersion || userId !== currentUserId.value) return
    if (res.data.status === 'success' && res.data.recipe) {
      quickRecipes.value = [res.data.recipe];
    }
  } catch (e) {
    console.error("首页推荐获取失败:", e);
    // 💡 调试小技巧：如果报错，弹窗显示具体原因
    // ElMessage.error("推荐失败: " + e.message);
  } finally {
    if (version === quickVersion) recLoading.value = false;
  }
};


async function persistForm(editing) {
  const userId = currentUserId.value
  if (!userId || saving.value) return
  saving.value = true; saveError.value = ''
  try {
    const payload = hasPendingWrite(userId) ? null : serializeItem(editing ? editingItem.value : newItem.value, editing ? editingOriginal.value : null)
    if (payload && editing && !Object.keys(payload).length) { editDialogVisible.value = false; return }
    inventoryReady.value = false; inventoryRevision.value++
    quickVersion++; quickRecipes.value = []
    await saveInventory(userId, editing ? payload : payload && [payload], editing ? editingOriginal.value.id : null, false, editing ? editingOriginal.value._revision : null)
    if (currentUserId.value !== userId) return
    pendingWrite.value = false
    if (editing) editDialogVisible.value = false
    else { showAddDialog.value = false; newItem.value = blankItem() }
    ElMessage.success('已重新读取库存并确认保存')
    await fetchInventory()
  } catch (error) {
    if (currentUserId.value === userId) {
      saveError.value = inventoryErrorMessage(error)
      pendingWrite.value = hasPendingWrite(userId)
    }
  } finally { saving.value = false }
}
const saveEdit = () => persistForm(true)
const saveNewItem = () => persistForm(false)

// 修改 ManageFridge.vue 中的 removeItem 函数
const removeItem = async (id) => {
  const userId = currentUserId.value
  if (!userId) return
  try {
    // 1. 确认框
    await ElMessageBox.confirm('确定要从冰箱移除这件食材吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    // 2. 发送请求
    // 注意：Axios DELETE 的参数放在第二个参数的 params 里
    const res = await deleteInventory(userId,id,inventory.value.find(row=>String(row.id)===String(id))?._revision);

    if (res.data.status === 'success') {
      ElMessage.success('已移出冰箱');

      // ✅ 核心修复：必须加 await，确保数据取回来后再让 Vue 更新界面
      await fetchInventory();

      // 可选：同时刷新下方的灵感菜谱，因为食材变了

    } else {
      ElMessage.error(res.data.message || '移除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error("删除出错:", error);
      ElMessage.error(inventoryErrorMessage(error));
    }
  }
}

const handleUploadSuccess = (response) => {
  if (uploadUserId !== currentUserId.value) { resetRecognitionState(); return }
  const data = response?.data ?? response;
  if (!data) {
    clearRecognitionTimer();
    recognitionStatus.value = 'error';
    ElMessage.error('识别响应为空，请重新尝试');
    return;
  }
  if (data.status === "success" && data.detected && data.detected.length > 0) {
    // 将识别结果转换为临时数据格式，包含存储方式
    const itemsWithStorage = data.detected.map(recognitionItem);

    // 存储到 localStorage 以便在确认页使用
    try { saveDraft(uploadUserId, itemsWithStorage) } catch { handleRecognitionError(new Error('识别草稿保存失败，请检查本地存储')); return }

    clearRecognitionTimer();
    recognizedItemCount.value = data.detected.length;
    recognitionStatus.value = 'success';
    recognitionSuccessTimer = setTimeout(() => {
      router.push('/capture-confirm');
    }, 650);
  } else {
    clearRecognitionTimer();
    recognitionStatus.value = 'error';
    ElMessage.warning('未能识别到食材');
  }
}

watch(currentUserId, (id, previousId) => {
  inventoryRequestVersion++
  freshnessLoader.reset()
  inventoryReady.value = false
  quickVersion++; quickRecipes.value = []
  resetRecognitionState()
  if (id !== previousId) { inventory.value = []; newItem.value = blankItem(); editingItem.value = {}; showAddDialog.value = false; editDialogVisible.value = false; saveError.value = '' }
  pendingWrite.value = id ? hasPendingWrite(id) : false
  if (!id) {
    inventoryLoading.value = true
    return
  }
  if (previousId && previousId !== id) inventory.value = []
  fetchInventory(id)
})

onMounted(() => {
  window.addEventListener('storage', handleStoredUserChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  waitForCurrentUser()
});
onBeforeUnmount(() => {
  freshnessLoader.dispose()
  quickVersion++
  inventoryRequestVersion += 1
  if (userWaitTimer) clearTimeout(userWaitTimer)
  window.removeEventListener('storage', handleStoredUserChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  clearRecognitionTimer();
  if (recognitionSuccessTimer) clearTimeout(recognitionSuccessTimer);
});
const consumptionChanged=event=>{if(event.detail?.user===currentUserId.value)fetchInventory()}
onMounted(()=>window.addEventListener('cookx:inventory-changed',consumptionChanged))
onBeforeUnmount(()=>window.removeEventListener('cookx:inventory-changed',consumptionChanged))
</script>

<style scoped>
.manage-container { position: relative; z-index: 1; display: flex; flex-direction: column; gap: 14px; width: min(100%, var(--ck-page-max)); min-height: calc(100dvh - var(--ck-bottom-inset, 0px)); margin: 0 auto; padding: var(--sat) calc(var(--ck-gutter) + var(--sar)) 24px calc(var(--ck-gutter) + var(--sal)); color: var(--ck-text); }
.manage-container.is-items { gap: 12px; padding-top: 0; }
button { font: inherit; }
.ck-title { margin: 2px 0 2px; }
.recognize-fab :deep(.el-upload) { display: block; }
.recognize-button { display: inline-flex; align-items: center; gap: 6px; min-height: 38px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-surface); color: var(--ck-text); font-size: 14px; font-weight: 600; box-shadow: var(--ck-shadow); cursor: pointer; }
.recognize-button .ck-icon { color: var(--ck-fresh); }
.gap-alert { margin: 0; }

/* 绿色冰箱卡 */
.fridge-card { position: relative; display: flex; flex-direction: column; overflow: hidden; padding: 20px 18px 16px; border-radius: 26px; background: radial-gradient(120% 90% at 100% 0%, var(--ck-photo-tone-85), transparent 55%), linear-gradient(150deg, var(--ck-photo-tone-86) 0%, var(--ck-photo-tone-87) 55%, var(--ck-photo-tone-88) 100%); color: var(--ck-on-accent); box-shadow: 0 16px 34px var(--ck-photo-tone-89); }
.fridge-card__copy { position: relative; z-index: 1; width: 66%; min-width: 0; }
.fridge-art { position: absolute; right: 10px; top: 12px; width: 30%; height: 184px; object-fit: contain; }
.fridge-card h2 { font-size: 22px; font-weight: 800; }
.fridge-card__more { display: inline-flex; align-items: center; gap: 2px; min-height: 30px; padding: 0; border: 0; background: none; color: var(--ck-photo-tone-58); font-size: 14px; }
.fridge-stats { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); margin: 14px 0 16px; }
.fridge-stats div { display: flex; flex-direction: column; align-items: center; gap: 2px; text-align: center; }
.fridge-stats div + div { border-left: 1px solid var(--ck-photo-tone-07); }
.fridge-stats .ck-icon { color: var(--ck-photo-tone-90); }
.fridge-stats .ck-icon.is-warn { color: var(--ck-photo-tone-91); }
.fridge-stats strong { margin-top: 4px; font-size: 24px; font-weight: 600; line-height: 1.1; }
.fridge-stats strong small { margin-left: 2px; font-family: var(--ck-font); font-size: 12px; font-weight: 500; }
.fridge-stats span { color: var(--ck-photo-tone-92); font-size: 12px; }
.add-food-button { position: relative; z-index: 1; display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 50px; border: 1px solid var(--ck-photo-tone-93); border-radius: 16px; background: linear-gradient(180deg, var(--ck-photo-tone-94), var(--ck-photo-tone-78)); color: var(--ck-on-accent); font-size: 16px; font-weight: 700; -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); }

.empty-card { display: flex; flex-direction: column; align-items: center; padding: 26px 20px; text-align: center; }
.empty-card__icon { display: grid; place-items: center; width: 56px; height: 56px; border-radius: 18px; background: var(--ck-fresh-soft); color: var(--ck-fresh); }
.empty-card h2 { margin: 12px 0 4px; font-size: 18px; font-weight: 700; }
.empty-card p { max-width: 300px; color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }

.expiry-card, .category-card { padding: 16px; }
.expiry-list { display: flex; flex-direction: column; }
.expiry-list button { display: flex; align-items: center; gap: 12px; width: 100%; min-height: 70px; padding: 8px 0; border: 0; background: none; color: var(--ck-text); text-align: left; }
.expiry-list button + button { border-top: 1px solid var(--ck-hairline); }
.mini-food-visual, .food-visual { position: relative; display: grid; place-items: center; overflow: hidden; background: var(--ck-photo-tone-95); color: var(--ck-photo-tone-96); }
.mini-food-visual { width: 54px; height: 54px; flex: 0 0 54px; border-radius: 14px; }
.mini-food-visual img, .food-visual img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.expiry-copy { display: flex; flex-direction: column; min-width: 0; flex: 1 1 auto; }
.expiry-copy strong { font-size: 16px; font-weight: 700; }
.expiry-copy small { color: var(--ck-danger-text); font-size: 12.5px; }
.expiry-days { flex: 0 0 auto; padding: 4px 12px; border-radius: 999px; background: var(--ck-fill); color: var(--ck-text-2); font-size: 13px; font-weight: 700; white-space: nowrap; }
.expiry-days.expired, .expiry-days.urgent { background: var(--ck-danger-soft); color: var(--ck-danger-text); }
.expiry-days.soon { background: var(--ck-warn-soft); color: var(--ck-warn-text); }
.expiry-days.fresh { background: var(--ck-fresh-soft); color: var(--ck-fresh-text); }
.expiry-empty { display: flex; align-items: center; gap: 8px; min-height: 44px; color: var(--ck-text-3); font-size: 13px; }

.category-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
.category-tile { display: flex; flex-direction: column; align-items: center; gap: 2px; min-width: 0; padding: 12px 4px 10px; border: 0; border-radius: 16px; background: var(--ck-fill); color: var(--ck-text); }
.category-tile img { width: 46px; height: 46px; margin-bottom: 4px; border-radius: 12px; object-fit: cover; mix-blend-mode: multiply; }
.category-tile b { font-size: 14px; font-weight: 700; }
.category-tile small { color: var(--ck-text-3); font-size: 12px; }
.tone-vegetable { background: var(--ck-photo-tone-97); } .tone-meat { background: var(--ck-photo-tone-98); } .tone-dairy { background: var(--ck-photo-tone-99); } .tone-staple { background: var(--ck-photo-tone-100); } .tone-fruit { background: var(--ck-photo-tone-101); } .tone-condiment { background: var(--ck-photo-tone-102); } .tone-other { background: var(--ck-photo-tone-103); }
:root[data-theme='dark'] .category-tile { background: var(--ck-fill); }
:root[data-theme='dark'] .category-tile img { mix-blend-mode: normal; }

/* 全部食材 */
.nav-add { display: grid; place-items: center; width: 38px; height: 38px; padding: 0; border: 0; border-radius: 50%; background: var(--ck-heat-deep); color: var(--ck-on-accent); }
.inventory-toolbar { display: flex; gap: 8px; margin-top: 4px; }
.search-input { flex: 1 1 auto; }
.search-input :deep(.el-input__wrapper) { min-height: 44px; border-radius: 999px !important; }
.refresh-btn.el-button { min-height: 44px !important; padding: 0 14px !important; font-size: 13px; }
.category-tabs { display: flex; gap: 8px; margin: 0 calc(-1 * var(--ck-gutter)); padding: 0 var(--ck-gutter) 2px; overflow-x: auto; scrollbar-width: none; }
.category-tabs::-webkit-scrollbar { display: none; }
.category-tabs button { display: inline-flex; align-items: center; gap: 4px; flex: 0 0 auto; height: 36px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-surface); color: var(--ck-text-2); font-size: 13px; white-space: nowrap; }
.category-tabs button span { color: var(--ck-text-3); font-size: 12px; }
.category-tabs button.active { border-color: transparent; background: var(--ck-chip-active-bg); color: var(--ck-chip-active-text); font-weight: 600; }
.category-tabs button.active span { color: inherit; opacity: 0.7; }
.last-updated { color: var(--ck-text-3); font-size: 12px; }
.food-grid { display: flex; flex-direction: column; gap: 8px; }
.food-card { overflow: hidden; border: 1px solid var(--ck-glass-border); border-radius: 18px; background: var(--ck-surface); box-shadow: var(--ck-shadow); }
.food-card-top { display: flex; align-items: center; gap: 12px; width: 100%; padding: 10px 12px; border: 0; background: none; color: var(--ck-text); text-align: left; }
.food-visual { width: 52px; height: 52px; flex: 0 0 52px; border-radius: 14px; }
.food-card-body { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1 1 auto; }
.food-title-row { display: flex; align-items: center; gap: 8px; min-width: 0; }
.food-title-row h3 { overflow: hidden; font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.cat-tag { flex: 0 0 auto; padding: 1px 8px; border-radius: 6px; background: var(--ck-fill); color: var(--ck-text-2); font-size: 11px; }
.food-meta { overflow: hidden; color: var(--ck-text-3); font-size: 12.5px; text-overflow: ellipsis; white-space: nowrap; }
.food-detail { padding: 0 12px 12px; border-top: 1px solid var(--ck-hairline); }
.card-actions { display: flex; justify-content: flex-end; gap: 8px; }
.card-actions button { display: inline-flex; align-items: center; gap: 4px; min-height: 36px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill); color: var(--ck-text-2); font-size: 13px; }
.card-actions button.danger { color: var(--ck-danger-text); }
.inventory-empty { display: flex; flex-direction: column; align-items: center; padding: 30px 20px; text-align: center; }
.inventory-empty > span { display: grid; place-items: center; width: 60px; height: 60px; border-radius: 20px; background: var(--ck-fresh-soft); color: var(--ck-fresh); }
.inventory-error > span { background: var(--ck-danger-soft); color: var(--ck-danger); }
.inventory-empty h2 { margin: 14px 0 6px; font-size: 18px; font-weight: 700; }
.inventory-empty p { max-width: 300px; color: var(--ck-text-3); font-size: 13px; line-height: 1.6; }
.empty-actions { margin-top: 16px; }
.empty-actions button { display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 20px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: var(--ck-on-accent); font-weight: 600; }
.recommend-section { padding: 16px; }
.recipe-container { display: flex; flex-direction: column; }
.recipe-row-card { display: flex; align-items: center; gap: 12px; min-height: 56px; }
.recipe-row-card + .recipe-row-card { border-top: 1px solid var(--ck-hairline); }
.rec-icon { display: grid; place-items: center; width: 36px; height: 36px; flex: 0 0 36px; border-radius: 12px; background: var(--ck-heat-soft); color: var(--ck-heat-text); }
.rec-dish-name { flex: 1 1 auto; min-width: 0; overflow: hidden; font-size: 15px; font-weight: 500; text-overflow: ellipsis; white-space: nowrap; }
.rec-go-btn { display: inline-flex; align-items: center; gap: 2px; min-height: 34px; padding: 0 12px; border: 0; border-radius: 999px; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 13px; font-weight: 600; }

.recognition-overlay { position: fixed; inset: 0; z-index: 3000; display: grid; place-items: center; padding: 24px; background: var(--ck-overlay); -webkit-backdrop-filter: blur(8px); backdrop-filter: blur(8px); }
.recognition-card { display: flex; flex-direction: column; align-items: center; gap: 8px; width: min(100%, 360px); padding: 28px 22px; border: 1px solid var(--ck-glass-border); border-radius: 26px; background: var(--ck-surface-strong); text-align: center; box-shadow: var(--ck-shadow-strong); }
.recognition-visual { position: relative; display: grid; place-items: center; width: 76px; height: 76px; border-radius: 24px; background: var(--ck-heat-soft); color: var(--ck-heat); }
.recognition-visual.success { background: var(--ck-fresh-soft); color: var(--ck-fresh); }
.recognition-visual.error { background: var(--ck-danger-soft); color: var(--ck-danger); }
.scan-ring { position: absolute; inset: -6px; border: 2px solid var(--ck-photo-tone-104); border-radius: 28px; animation: scan 1.4s ease-in-out infinite; }
@keyframes scan { 0%, 100% { opacity: 0.2; transform: scale(0.96); } 50% { opacity: 1; transform: scale(1.04); } }
.recognition-card h2 { margin-top: 8px; font-size: 19px; font-weight: 700; }
.recognition-card p { color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }
.upload-progress { width: 100%; margin-top: 6px; }
.upload-progress > div:first-child { display: flex; justify-content: space-between; color: var(--ck-text-2); font-size: 12px; }
.progress-track, .analysis-progress { position: relative; width: 100%; height: 6px; margin-top: 6px; overflow: hidden; border-radius: 3px; background: var(--ck-fill-strong); }
.progress-track i { display: block; height: 100%; border-radius: 3px; background: var(--ck-heat-gradient); }
.analysis-progress i { position: absolute; top: 0; bottom: 0; width: 40%; border-radius: 3px; background: var(--ck-heat-gradient); animation: indeterminate 1.3s ease-in-out infinite; }
@keyframes indeterminate { 0% { left: -40%; } 100% { left: 100%; } }
.elapsed-time { margin-top: 6px; font-size: 14px; font-variant-numeric: tabular-nums; }
.recognition-card small { color: var(--ck-text-3); font-size: 12px; }
.recognition-card button { min-height: 44px; margin-top: 10px; padding: 0 22px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: var(--ck-on-accent); font-weight: 600; }
@media (max-width: 360px) {
  .fridge-card__copy { max-width: 78%; }
  .fridge-stats strong { font-size: 21px; }
  .category-tile img { width: 38px; height: 38px; }
}
</style>
