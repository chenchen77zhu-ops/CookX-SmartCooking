<template>
  <div v-loading="inventoryLoading" class="manage-container">
    <section class="fridge-hero">
      <div class="hero-inner">
        <header class="fridge-topbar">
          <div class="fridge-wordmark"><span>Cook</span><strong>X</strong></div>
          <h1>我的冰箱</h1>
          <button class="add-food-button" type="button" @click="showAddDialog = true">
            <el-icon><Plus /></el-icon><span>添加食材</span>
          </button>
        </header>

        <div class="hero-grid">
          <article class="overview-card dark-card">
            <div class="overview-title">
              <span class="overview-icon"><el-icon><Box /></el-icon></span>
              <div><h2>库存概览</h2><p>数据实时更新</p></div>
            </div>
            <div class="overview-metrics">
              <div><el-icon><KnifeFork /></el-icon><span>食材种类</span><strong>{{ inventoryKindCount }}<small>种</small></strong></div>
              <div><el-icon><Box /></el-icon><span>库存总量</span><strong>{{ totalQuantity }}<small>库存计数</small></strong></div>
              <div><el-icon><AlarmClock /></el-icon><span>即将过期</span><strong>{{ expiringCount }}<small>种</small></strong></div>
              <div><el-icon><WarningFilled /></el-icon><span>已过期</span><strong>{{ expiredCount }}<small>种</small></strong></div>
            </div>
            <p class="last-updated">最后更新：{{ lastUpdatedText }}</p>
          </article>

          <article class="expiry-card">
            <div class="summary-card-heading"><h2>临期食材提醒</h2><span>最近 {{ expiringPreview.length }} 项</span></div>
            <div v-if="expiringPreview.length" class="expiry-list">
              <button v-for="item in expiringPreview" :key="item.id" type="button" @click="editItem(item)">
                <span class="mini-food-visual">
                  <el-icon><KnifeFork /></el-icon>
                  <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="getFoodInfo(item.name).cn" @error="hideBrokenImage" />
                </span>
                <span class="expiry-copy"><strong>{{ getFoodInfo(item.name).cn }}</strong><small>{{ expiryStatus(item).description }}</small></span>
                <span :class="['expiry-days', expiryStatus(item).className]">{{ expiryStatus(item).label }}</span>
              </button>
            </div>
            <div v-else class="expiry-empty"><el-icon><KnifeFork /></el-icon><span>{{ freshness.status === 'success' ? '未发现已评估的临期项，未知项请补充信息' : '等待鲜度评估' }}</span></div>
          </article>

          <article class="health-card">
            <div class="health-copy"><span>COOKX FRESH</span><h2>食材新鲜，<br />生活更健康</h2><p>合理管理冰箱食材<br />让每一餐都安心美味</p></div>
            <div class="fridge-art" aria-hidden="true"><span class="door-line"></span><i class="leaf-one"></i><i class="leaf-two"></i></div>
          </article>
        </div>
      </div>
    </section>

    <main class="inventory-surface">
      <el-alert v-if="inventoryError" title="库存加载失败，当前显示上次库存；鲜度需重新评估" type="error" :closable="false" />
      <el-alert v-if="freshness.status === 'error'" :title="freshness.error" type="warning" :closable="false" />
      <el-button @click="fetchInventory()" :loading="inventoryLoading">刷新库存与鲜度</el-button>
      <section class="inventory-toolbar">
        <el-input v-model="searchKeyword" placeholder="搜索食材名称" :prefix-icon="Search" clearable class="search-input" />
        <button class="compact-add" type="button" @click="showAddDialog = true"><el-icon><Plus /></el-icon><span>添加</span></button>
      </section>

      <nav class="category-tabs" aria-label="食材分类">
        <button
          v-for="category in visibleCategories"
          :key="category.id"
          type="button"
          :class="{ active: activeCategory === category.id }"
          @click="activeCategory = category.id"
        >
          {{ category.name }} <span>{{ category.count }}</span>
        </button>
      </nav>

      <div v-if="filteredInventory.length" class="food-grid">
        <article v-for="item in filteredInventory" :key="item.id" class="food-card" @click="editItem(item)">
          <div class="food-visual">
            <span class="food-image-placeholder"><el-icon><KnifeFork /></el-icon></span>
            <img v-if="getItemImage(item)" :src="getItemImage(item)" :alt="getFoodInfo(item.name).cn" loading="lazy" @error="hideBrokenImage" />
          </div>
          <div class="food-card-body">
            <div class="food-title-row"><h3>{{ getFoodInfo(item.name).cn }}</h3><span :class="`category-${getItemCategory(item)}`">{{ categoryName(getItemCategory(item)) }}</span></div>
            <p class="food-meta">{{ getItemMeasureText(item) }}<span v-if="item.storage_type"> · {{ item.storage_type }}</span></p>
            <FreshnessCard :detail="freshness.items[item.id]" :status="freshness.status" :evaluated-at="freshness.evaluatedAt" />
            <div class="food-footer"><span>添加于 {{ formatDate(item.add_time) }}</span><div class="card-actions"><button type="button" aria-label="编辑食材" @click.stop="editItem(item)"><el-icon><EditPen /></el-icon></button><button type="button" aria-label="删除食材" @click.stop="removeItem(item.id)"><el-icon><Delete /></el-icon></button></div></div>
          </div>
        </article>
      </div>

      <section v-else-if="inventoryError" class="inventory-empty inventory-error">
        <span><el-icon><WarningFilled /></el-icon></span><h2>库存加载失败</h2>
        <p>请检查网络连接后重新加载，已有库存数据不会因此被清空。</p>
        <div class="empty-actions"><button type="button" @click="fetchInventory()"><el-icon><Refresh /></el-icon>重新加载</button></div>
      </section>

      <section v-else-if="!inventoryLoading" class="inventory-empty">
        <span><el-icon><Box /></el-icon></span><h2>{{ inventory.length ? '没有匹配的食材' : '冰箱还是空的' }}</h2>
        <p>{{ inventory.length ? '换个关键词或分类看看吧' : '添加一些食材，CookX 就能开始为你规划下一餐' }}</p>
        <div v-if="!inventory.length" class="empty-actions"><button type="button" @click="showAddDialog = true"><el-icon><Plus /></el-icon>添加食材</button></div>
      </section>

      <MultiObjectiveRecommendations :inventory-revision="inventoryRevision" :inventory-ready="inventoryReady" :user-id="currentUserId" @manage-inventory="showAddDialog = true" />

      <section v-if="inventory.length > 0" class="recommend-section">
        <div class="main-title"><span><el-icon><KnifeFork /></el-icon>AI 生成灵感</span><small>由大模型生成新的菜谱方案</small></div>
        <div class="recipe-container" v-loading="recLoading">
          <div v-for="(rec, index) in quickRecipes" :key="index" class="recipe-row-card">
            <div class="rec-info"><span class="rec-icon"><el-icon><KnifeFork /></el-icon></span><span class="rec-dish-name">{{ rec?.dish_name || '构思中...' }}</span></div>
            <el-button type="success" size="small" round class="rec-go-btn" @click="goToChef(rec.dish_name)">咨询教程 <el-icon><ArrowRight /></el-icon></el-button>
          </div>
        </div>
      </section>

      <aside class="fridge-tip"><el-icon><KnifeFork /></el-icon><span><strong>CookX 小贴士：</strong>定期清理过期食材，保持冰箱整洁；合理搭配食材，吃得健康又美味。</span></aside>
    </main>

    <el-upload
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
      <div class="recognize-button"><el-icon><CameraFilled /></el-icon><span>识别食材</span></div>
    </el-upload>

    <div v-if="recognitionStatus !== 'idle'" class="recognition-overlay" role="status" aria-live="polite">
      <section class="recognition-card">
        <div :class="['recognition-visual', recognitionStatus]">
          <el-icon v-if="recognitionStatus === 'success'"><CircleCheckFilled /></el-icon>
          <el-icon v-else-if="recognitionStatus === 'error'"><WarningFilled /></el-icon>
          <el-icon v-else><CameraFilled /></el-icon>
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
import { AlarmClock, Box, CameraFilled, CircleCheckFilled, Delete, KnifeFork, ArrowRight, EditPen, WarningFilled, Search, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'
import MultiObjectiveRecommendations from '@/components/MultiObjectiveRecommendations.vue'
import tomatoImage from '@/assets/images/ingredients/tomato.png'
import potatoImage from '@/assets/images/ingredients/potato.png'
import carrotImage from '@/assets/images/ingredients/carrot.png'
import onionImage from '@/assets/images/ingredients/onion.png'
import eggImage from '@/assets/images/ingredients/egg.png'
import chickenImage from '@/assets/images/ingredients/chicken.png'
import lettuceImage from '@/assets/images/ingredients/lettuce.png'
import broccoliImage from '@/assets/images/ingredients/broccoli.png'
import riceImage from '@/assets/images/ingredients/rice.png'
import milkImage from '@/assets/images/ingredients/milk.png'
import beefImage from '@/assets/images/ingredients/beef.png'
import chiliImage from '@/assets/images/ingredients/chili.png'
import garlicImage from '@/assets/images/ingredients/garlic.png'
import kimchiImage from '@/assets/images/ingredients/kimchi.png'
import leekImage from '@/assets/images/ingredients/leek.png'
import cabbageImage from '@/assets/images/ingredients/cabbage.png'
import spinachImage from '@/assets/images/ingredients/spinach.png'
import tofuImage from '@/assets/images/ingredients/tofu.png'
import cilantroImage from '@/assets/images/ingredients/cilantro.png'
import greenChiliImage from '@/assets/images/ingredients/green-chili.png'
import gingerImage from '@/assets/images/ingredients/ginger.png'
import doubanjiangImage from '@/assets/images/ingredients/doubanjiang.png'

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
  if (item?.weight != null) return `${quantityText} · ${formatMeasure(item.weight, item.weight_unit || item.unit || 'g')}`
  if (item?.volume != null) return `${quantityText} · ${formatMeasure(item.volume, item.volume_unit || item.unit || 'ml')}`
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
    if (inventory.value.length > 0) fetchQuickRecipes();
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
/* CookX 冰箱高保真视觉层 */
.manage-container {
  position: relative;
  min-height: calc(100vh - 126px);
  padding: 0 0 calc(112px + env(safe-area-inset-bottom));
  background: var(--cookx-bg);
}

button { font: inherit; }

.fridge-hero {
  padding: calc(16px + env(safe-area-inset-top)) 16px 30px;
  background:
    radial-gradient(circle at 88% 8%, rgba(77, 139, 105, .20), transparent 28%),
    linear-gradient(145deg, #082d24, var(--cookx-primary-dark));
  color: #fff;
}

.hero-inner,
.inventory-surface {
  width: min(100%, var(--cookx-page-max));
  margin: 0 auto;
}

.fridge-topbar {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  margin-bottom: 20px;
}

.fridge-wordmark {
  font-size: 23px;
  font-weight: 760;
  letter-spacing: -.6px;
}

.fridge-wordmark strong { color: var(--cookx-accent); }

.fridge-topbar h1 {
  margin: 0;
  font-size: 19px;
  font-weight: 680;
}

.add-food-button,
.compact-add {
  display: inline-flex;
  align-items: center;
  justify-self: end;
  gap: 5px;
  min-height: 40px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, .12);
  border-radius: 14px;
  background: rgba(23, 117, 88, .72);
  color: #fff;
  font-size: 12px;
  font-weight: 650;
  cursor: pointer;
}

.hero-grid {
  display: grid;
  gap: 13px;
}

.overview-card,
.expiry-card,
.health-card {
  min-width: 0;
  border-radius: 20px;
}

.overview-card {
  padding: 18px;
  border: 1px solid rgba(255, 255, 255, .13);
  background: rgba(255, 255, 255, .055);
  box-shadow: inset 0 1px rgba(255, 255, 255, .04);
}

.overview-title {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overview-icon {
  display: grid;
  width: 38px;
  height: 38px;
  border: 1px solid rgba(255, 255, 255, .16);
  border-radius: 12px;
  color: #d8ede2;
  place-items: center;
}

.overview-title h2,
.summary-card-heading h2,
.health-copy h2 {
  margin: 0;
  font-size: 16px;
}

.overview-title p {
  margin: 3px 0 0;
  color: rgba(255, 255, 255, .50);
  font-size: 10px;
}

.overview-metrics {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 17px 12px;
  margin-top: 21px;
}

.overview-metrics > div {
  display: grid;
  grid-template-columns: 22px 1fr;
  align-items: center;
}

.overview-metrics .el-icon {
  grid-row: 1 / 3;
  color: var(--cookx-gold);
  font-size: 17px;
}

.overview-metrics span {
  color: rgba(255, 255, 255, .58);
  font-size: 9px;
}

.overview-metrics strong {
  margin-top: 2px;
  font-size: 23px;
  line-height: 1;
}

.overview-metrics strong small {
  margin-left: 3px;
  font-size: 9px;
  font-weight: 500;
}

.last-updated {
  margin: 19px 0 0;
  color: rgba(255, 255, 255, .46);
  font-size: 9px;
}

.expiry-card {
  padding: 18px;
  background: var(--cookx-surface);
  color: var(--cookx-text);
  box-shadow: 0 12px 30px rgba(0, 0, 0, .13);
}

.summary-card-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
}

.summary-card-heading span {
  color: var(--cookx-text-secondary);
  font-size: 9px;
}

.expiry-list button {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 55px;
  padding: 8px 0;
  border: 0;
  border-bottom: var(--cookx-border);
  background: none;
  text-align: left;
  cursor: pointer;
}

.expiry-list button:last-child { border-bottom: 0; }

.mini-food-visual {
  position: relative;
  display: grid;
  flex: 0 0 39px;
  width: 39px;
  height: 39px;
  border-radius: 11px;
  background: #f1f3ed;
  color: var(--cookx-success);
  font-size: 18px;
  place-items: center;
  overflow: hidden;
}

.mini-food-visual img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }

.expiry-copy {
  display: flex;
  min-width: 0;
  margin-left: 10px;
  flex-direction: column;
}

.expiry-copy strong { font-size: 12px; }
.expiry-copy small { margin-top: 3px; color: var(--cookx-text-secondary); font-size: 9px; }
.expiry-days { margin-left: auto; font-size: 11px; font-weight: 700; }
.expiry-days.expired, .expiry-days.urgent { color: var(--cookx-danger); }
.expiry-days.soon { color: var(--cookx-accent); }
.expiry-days.fresh { color: var(--cookx-success); }

.expiry-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 140px;
  color: var(--cookx-success);
  font-size: 12px;
}

.health-card {
  position: relative;
  min-height: 205px;
  padding: 20px;
  overflow: hidden;
  background: linear-gradient(145deg, #f4fbf2, #dff0de);
  color: var(--cookx-text);
  box-shadow: 0 12px 30px rgba(0, 0, 0, .10);
}

.health-copy { position: relative; z-index: 2; width: 58%; }
.health-copy > span { color: var(--cookx-success); font-size: 8px; font-weight: 750; letter-spacing: 1px; }
.health-copy h2 { margin-top: 8px; font-size: 18px; line-height: 1.35; }
.health-copy p { margin: 13px 0 0; color: var(--cookx-text-secondary); font-size: 10px; line-height: 1.65; }

.fridge-art {
  position: absolute;
  right: 20px;
  bottom: 18px;
  width: 80px;
  height: 134px;
  border-radius: 18px;
  background: linear-gradient(145deg, #d7f1d6, #8fcf8d);
  box-shadow: 0 14px 28px rgba(77, 139, 105, .20);
}

.fridge-art::before {
  content: '';
  position: absolute;
  top: 14px;
  left: 10px;
  width: 4px;
  height: 24px;
  border-radius: 3px;
  background: rgba(23, 63, 53, .24);
}

.door-line { position: absolute; top: 50px; right: 0; left: 0; border-top: 1px solid rgba(23, 63, 53, .14); }
.fridge-art i { position: absolute; width: 24px; height: 11px; border-radius: 100% 0 100% 0; background: #59af60; }
.leaf-one { right: -16px; bottom: 12px; transform: rotate(-38deg); }
.leaf-two { right: -7px; bottom: 32px; transform: rotate(-70deg) scale(.75); }

.inventory-surface {
  position: relative;
  z-index: 3;
  margin-top: -12px;
  padding: 17px 14px calc(108px + env(safe-area-inset-bottom));
  border: var(--cookx-border);
  border-radius: 22px;
  background: rgba(255, 255, 255, .82);
  box-shadow: var(--cookx-shadow);
}

.inventory-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
}

.search-input :deep(.el-input__wrapper) {
  min-height: 46px;
  border-radius: 14px !important;
  background: #fff;
}

.compact-add {
  min-height: 46px;
  border-color: rgba(23, 63, 53, .08);
  background: var(--cookx-primary);
}

.category-tabs {
  display: flex;
  gap: 8px;
  margin: 15px -3px 17px;
  padding: 0 3px 3px;
  overflow-x: auto;
  scrollbar-width: none;
}

.category-tabs::-webkit-scrollbar { display: none; }

.category-tabs button {
  flex: 0 0 auto;
  min-height: 35px;
  padding: 0 14px;
  border: 1px solid rgba(23, 63, 53, .07);
  border-radius: 999px;
  background: #faf9f5;
  color: var(--cookx-text-secondary);
  font-size: 11px;
  cursor: pointer;
}

.category-tabs button span { margin-left: 4px; color: inherit; opacity: .65; }
.category-tabs button.active { border-color: var(--cookx-primary); background: var(--cookx-primary); color: #fff; box-shadow: 0 7px 16px rgba(23, 63, 53, .15); }

.food-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 0;
}

.food-card {
  display: block;
  position: relative;
  min-width: 0;
  padding: 8px;
  overflow: hidden;
  border: var(--cookx-border);
  border-radius: 17px;
  background: var(--cookx-surface);
  box-shadow: 0 7px 22px rgba(28, 48, 40, .07);
  cursor: pointer;
  transition: transform .2s ease, box-shadow .2s ease;
}

.food-card:hover { transform: translateY(-2px); box-shadow: var(--cookx-shadow-hover); }
.food-card:active { transform: scale(.985); }

.food-visual {
  position: relative;
  display: grid;
  width: 100%;
  height: 112px;
  margin: 0;
  overflow: hidden;
  border-radius: 13px;
  background: linear-gradient(145deg, #f1f4ed, #e4ece3);
  color: rgba(23, 63, 53, .35);
  font-size: 32px;
  place-items: center;
}

.food-image-placeholder { display: grid; width: 52px; height: 52px; border: 1px solid rgba(23, 63, 53, .08); border-radius: 17px; background: rgba(255, 255, 255, .55); place-items: center; }
.food-visual img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.food-card-body { padding: 10px 3px 3px; text-align: left; }
.food-title-row { display: flex; align-items: center; gap: 6px; }
.food-title-row h3 { min-width: 0; margin: 0; overflow: hidden; color: var(--cookx-text); font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }
.food-title-row > span { flex: 0 0 auto; margin-left: auto; padding: 3px 7px; border-radius: 999px; background: #eaf3e8; color: var(--cookx-success); font-size: 8px; }
.food-title-row > span.category-meat { background: #fff0ee; color: #c9574d; }
.food-title-row > span.category-dairy { background: #edf6fb; color: #397ca2; }
.food-title-row > span.category-staple,
.food-title-row > span.category-condiment { background: #fff3e4; color: #bd702f; }
.food-title-row > span.category-fruit { background: #fff1e8; color: var(--cookx-accent); }
.food-title-row > span.category-other { background: #f0f2ef; color: var(--cookx-text-secondary); }
.food-meta { margin: 7px 0 5px; color: var(--cookx-text-secondary); font-size: 10px; }
.freshness-label { display: block; font-size: 11px; }
.freshness-label.expired, .freshness-label.urgent { color: var(--cookx-danger); }
.freshness-label.soon { color: var(--cookx-accent); }
.freshness-label.fresh { color: var(--cookx-success); }
.food-footer { display: flex; align-items: center; margin-top: 9px; padding-top: 8px; border-top: var(--cookx-border); color: var(--cookx-text-secondary); font-size: 9px; }
.card-actions { display: flex; gap: 4px; margin-left: auto; }
.card-actions button { display: grid; width: 27px; height: 27px; padding: 0; border: 0; border-radius: 9px; background: #f5f5f1; color: var(--cookx-primary); cursor: pointer; place-items: center; }
.card-actions button:last-child { color: var(--cookx-danger); }

.inventory-empty {
  padding: 42px 20px;
  border: 1px dashed rgba(23, 63, 53, .14);
  border-radius: 18px;
  background: #faf9f5;
  text-align: center;
}

.inventory-empty > span { display: grid; width: 54px; height: 54px; margin: 0 auto 13px; border-radius: 17px; background: #e7eee9; color: var(--cookx-primary); font-size: 24px; place-items: center; }
.inventory-error > span { background: #fff0ed; color: var(--cookx-danger); }
.inventory-empty h2 { margin: 0; font-size: 16px; }
.inventory-empty p { margin: 7px auto 15px; color: var(--cookx-text-secondary); font-size: 11px; line-height: 1.55; }
.empty-actions button { display: inline-flex; align-items: center; gap: 5px; min-height: 44px; padding: 0 16px; border: 0; border-radius: 13px; background: var(--cookx-primary); color: #fff; font-size: 11px; font-weight: 650; cursor: pointer; }

.recommend-section { margin-top: 18px; padding: 17px; border: var(--cookx-border); border-radius: 18px; background: #fff; }
.main-title { display: flex; align-items: center; justify-content: space-between; margin: 0 0 12px; color: var(--cookx-text); font-size: 16px; }
.main-title > span { display: inline-flex; align-items: center; gap: 7px; }
.main-title small { color: var(--cookx-text-secondary); font-size: 9px; font-weight: 500; }
.recipe-row-card { padding: 12px; border: var(--cookx-border); border-radius: 14px; background: linear-gradient(135deg, #fff, #f3f7f2); box-shadow: none; }
.rec-icon { display: grid; width: 35px; height: 35px; border-radius: 11px; background: #e7eee9; color: var(--cookx-primary); font-size: 17px; place-items: center; }
.rec-dish-name { color: var(--cookx-text); font-size: 13px; }

.fridge-tip {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  margin-top: 16px;
  padding: 13px 14px;
  border-radius: 14px;
  background: #eff5ee;
  color: var(--cookx-text-secondary);
  font-size: 10px;
  line-height: 1.55;
}

.fridge-tip > .el-icon { flex: 0 0 auto; margin-top: 2px; color: var(--cookx-success); }
.fridge-tip strong { color: var(--cookx-success); }

.recognize-fab {
  position: fixed;
  z-index: 900;
  right: max(18px, calc((100vw - var(--cookx-page-max)) / 2 + 18px));
  bottom: calc(var(--cookx-bottom-nav-height, 72px) + env(safe-area-inset-bottom) + 16px);
}

.recognize-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68px;
  height: 68px;
  border: 3px solid rgba(255, 255, 255, .92);
  border-radius: 50%;
  background: var(--cookx-primary);
  color: #fff;
  box-shadow: 0 12px 26px rgba(23, 63, 53, .27);
  cursor: pointer;
  flex-direction: column;
}

.recognize-button .el-icon { font-size: 21px; }
.recognize-button span { margin-top: 3px; font-size: 9px; font-weight: 650; }

.recognize-fab.is-disabled .recognize-button { opacity: .55; cursor: wait; }

.recognition-overlay {
  position: fixed;
  z-index: 2100;
  inset: 0;
  display: grid;
  padding: 22px;
  background: rgba(8, 38, 31, .54);
  backdrop-filter: blur(7px);
  place-items: center;
}

.recognition-card {
  width: min(100%, 360px);
  padding: 28px 24px 24px;
  border: 1px solid rgba(255, 255, 255, .68);
  border-radius: 24px;
  background: #fffdf8;
  box-shadow: 0 24px 70px rgba(5, 31, 25, .28);
  text-align: center;
}

.recognition-visual {
  position: relative;
  display: grid;
  width: 70px;
  height: 70px;
  margin: 0 auto 17px;
  border-radius: 22px;
  background: #e9f2e9;
  color: var(--cookx-primary);
  font-size: 31px;
  place-items: center;
}

.recognition-visual.success { background: #e7f3e9; color: var(--cookx-success); }
.recognition-visual.error { background: #fff0ed; color: var(--cookx-danger); }
.scan-ring { position: absolute; inset: -7px; border: 2px solid rgba(23, 63, 53, .12); border-top-color: var(--cookx-accent); border-radius: 25px; animation: recognition-spin 1.15s linear infinite; }
.recognition-card h2 { margin: 0; color: var(--cookx-text); font-size: 20px; }
.recognition-card > p { min-height: 38px; margin: 8px 0 17px; color: var(--cookx-text-secondary); font-size: 11px; line-height: 1.7; }
.upload-progress > div:first-child { display: flex; justify-content: space-between; color: var(--cookx-text-secondary); font-size: 10px; }
.upload-progress strong { color: var(--cookx-primary); }
.progress-track,
.analysis-progress { height: 7px; margin-top: 8px; overflow: hidden; border-radius: 999px; background: #e5ebe6; }
.progress-track i { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--cookx-primary), var(--cookx-accent)); transition: width .2s ease; }
.analysis-progress { position: relative; }
.analysis-progress i { position: absolute; width: 38%; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--cookx-primary), var(--cookx-accent)); animation: recognition-scan 1.35s ease-in-out infinite; }
.elapsed-time { display: block; margin-top: 14px; color: var(--cookx-primary); font-size: 11px; }
.recognition-card > small { display: block; margin-top: 5px; color: var(--cookx-text-secondary); font-size: 9px; }
.recognition-card > button { min-height: 44px; margin-top: 4px; padding: 0 17px; border: 0; border-radius: 13px; background: var(--cookx-primary); color: #fff; cursor: pointer; }

@keyframes recognition-spin { to { transform: rotate(360deg); } }
@keyframes recognition-scan { 0% { left: -38%; } 55%, 100% { left: 100%; } }

:deep(.el-dialog) { max-width: 430px; }

@media (min-width: 720px) {
  .fridge-hero { padding-right: 24px; padding-bottom: 42px; padding-left: 24px; }
  .hero-grid { grid-template-columns: 1.05fr 1fr .9fr; }
  .inventory-surface { margin-top: -18px; padding: 22px 22px calc(108px + env(safe-area-inset-bottom)); }
  .food-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
  .food-visual { height: 150px; }
}

@media (min-width: 980px) {
  .food-grid { grid-template-columns: repeat(4, minmax(0, 1fr)); }
}

@media (max-width: 620px) {
  .inventory-surface { padding-bottom: 24px; }

  .recognize-fab {
    position: relative;
    right: auto;
    bottom: auto;
    display: flex;
    width: max-content;
    margin: 14px 14px calc(var(--cookx-bottom-nav-height, 72px) + env(safe-area-inset-bottom) + 12px) auto;
  }

  .recognize-button {
    width: 60px;
    height: 60px;
    border-width: 2px;
  }

  .recognize-button .el-icon { font-size: 19px; }
}
@media (max-width: 370px) {
  .fridge-hero { padding-right: 12px; padding-left: 12px; }
  .inventory-surface { padding-right: 10px; padding-left: 10px; }
  .fridge-wordmark { font-size: 20px; }
  .fridge-topbar h1 { font-size: 17px; }
  .add-food-button span { display: none; }
  .add-food-button { width: 40px; padding: 0; }
  .metric-card { padding: 10px 7px; }
  .food-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
  .food-visual { height: 96px; }
  .food-footer > span { max-width: 72px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
}

@media (max-width: 320px) {
  .food-grid { grid-template-columns: 1fr; }
}
</style>
