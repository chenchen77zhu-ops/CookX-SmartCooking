<template>
  <div class="capture-confirm-container">
    <!-- 顶部导航栏 -->
    <div class="header-section">
      <div class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </div>
      <h2 class="page-title">识别结果确认</h2>
      <div class="placeholder"></div>
    </div>

    <!-- 识别到的食材列表 -->
    <div class="confirm-content">
      <div class="result-tip">
        <el-icon><CircleCheckFilled /></el-icon>
        <span>识别到 {{ identifiedItems.length }} 种食材，请确认信息</span>
      </div>

      <div class="food-confirm-list">
        <div v-for="(item, index) in identifiedItems" :key="index" class="food-confirm-card">
          <!-- 食材图标和名称 -->
          <div class="food-header">
            <div class="food-emoji">{{ getFoodInfo(item.name).emoji }}</div>
            <div class="food-info">
              <el-input
                v-model="item.name"
                placeholder="食材名称"
                size="small"
                clearable
                class="name-input"
              />
              <div class="food-name-cn">{{ getFoodInfo(item.name).cn }}</div>
            </div>
          </div>

          <!-- 数量和存储方式 -->
          <div class="food-details">
            <div class="detail-row">
              <span class="detail-label">数量：</span>
              <el-input-number
                v-model="item.quantity"
                :min="1"
                :max="99"
                size="small"
                controls-position="right"
              />
            </div>

            <div class="detail-row">
              <span class="detail-label">存储方式：</span>
              <el-radio-group v-model="item.storage_type" size="small">
                <el-radio-button label="冷藏">冷藏</el-radio-button>
                <el-radio-button label="冷冻">冷冻</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <!-- 新鲜度和保质期 -->
          <div class="food-freshness">
            <div class="freshness-row">
              <span class="detail-label">新鲜度：</span>
              <el-tag :type="getFreshnessType(item.freshness)" size="small">
                {{ item.freshness || '新鲜' }}
              </el-tag>
            </div>
            <div class="freshness-row">
              <span class="detail-label">保质期：</span>
              <el-input-number
                v-model="item.shelf_life"
                :min="1"
                :max="365"
                size="small"
                controls-position="right"
                class="shelf-life-input"
              />
              <span class="days-unit">天</span>
            </div>
          </div>

          <!-- 删除按钮 -->
          <div class="delete-action">
            <el-button
              type="danger"
              size="small"
              plain
              @click="removeItem(index)"
            >
              <el-icon><Delete /></el-icon>
              删除
            </el-button>
          </div>
        </div>
      </div>

      <!-- 空状态 -->
      <el-empty v-if="identifiedItems.length === 0" description="暂无识别到的食材" />
    </div>

    <!-- 底部操作栏 -->
    <div class="bottom-actions">
      <el-button
        type="primary"
        size="large"
        class="confirm-btn"
        @click="confirmSave"
        :disabled="identifiedItems.length === 0"
      >
        <el-icon><CircleCheckFilled /></el-icon>
        确认存入库存
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ArrowLeft, CircleCheckFilled, Delete } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { API_BASE_URL } from '@/config/backend'

const router = useRouter()
const identifiedItems = ref([])

const foodConfig = {
  'beef':    { emoji: '🥩', cn: '牛肉' },
  'carrot':  { emoji: '🥕', cn: '胡萝卜' },
  'chicken': { emoji: '🍗', cn: '鸡肉' },
  'chili':   { emoji: '🌶️', cn: '辣椒' },
  'egg':     { emoji: '🥚', cn: '鸡蛋' },
  'garlic':  { emoji: '🧄', cn: '大蒜' },
  'kimchi':  { emoji: '🥗', cn: '泡菜' },
  'leek':    { emoji: '🌿', cn: '大葱' },
  'onion':   { emoji: '🧅', cn: '洋葱' },
  'potato':  { emoji: '🥔', cn: '土豆' }
}

const getFoodInfo = (enName) => {
  if (!enName) return { emoji: '🍱', cn: '未知' }
  const key = enName.toLowerCase().trim()
  return foodConfig[key] || { emoji: '🍱', cn: enName }
}

const getFreshnessType = (freshness) => {
  if (freshness === '新鲜') return 'success'
  if (freshness === '较新鲜') return 'warning'
  if (freshness === '一般') return 'danger'
  return 'success'
}

const removeItem = (index) => {
  identifiedItems.value.splice(index, 1)
  ElMessage.success('已删除')
}

const goBack = () => {
  router.back()
}

const confirmSave = async () => {
  if (identifiedItems.value.length === 0) {
    ElMessage.warning('没有可保存的食材')
    return
  }

  // ✅ 1. 从 localStorage 获取当前用户的 ID
  const user = JSON.parse(localStorage.getItem('user') || '{}')
  const userId = user.id

  if (!userId) {
    ElMessage.error('用户信息失效，请重新登录')
    router.push('/login')
    return
  }

  try {
    // 准备保存的数据（Body 数据）
    const itemsToSave = identifiedItems.value.map(item => ({
      name: item.name,
      quantity: item.quantity,
      storage_type: item.storage_type,
      shelf_life: item.shelf_life
    }))

    // ✅ 2. 核心修改：正确配置 axios.post
    // 参数1: URL
    // 参数2: Body 数据 (itemsToSave)
    // 参数3: 配置对象，其中 params 会被转化为 URL 后的 ?user_id=xxx
    const response = await axios.post(
      `${API_BASE_URL}/add-to-inventory`,
      itemsToSave,
      {
        params: { user_id: userId }
      }
    )

    if (response.data.status === 'success') {
      ElMessage.success('食材已成功存入您的私人库存')
      // 清除临时数据
      localStorage.removeItem('tempIdentifiedItems')
      // 返回冰箱管理页面
      setTimeout(() => {
        router.push('/home')
      }, 500)
    } else {
      ElMessage.error(response.data.message || '保存失败')
    }
  } catch (error) {
    // 如果后端返回 422，通常是这里没传对
    console.error('保存失败详情:', error.response?.data || error.message)
    ElMessage.error('保存失败，请检查登录状态或联系管理员')
  }
}

onMounted(() => {
  // 从 localStorage 加载识别结果
  const tempItems = localStorage.getItem('tempIdentifiedItems')
  if (tempItems) {
    try {
      identifiedItems.value = JSON.parse(tempItems)
    } catch (error) {
      console.error('解析识别结果失败:', error)
      ElMessage.error('识别数据加载失败')
    }
  } else {
    ElMessage.warning('没有找到识别结果')
  }
})
</script>

<style scoped>
.capture-confirm-container {
  min-height: 100vh;
  background: var(--bg-page);
  padding-bottom: 120px; /* 为底部按钮留出更多空间 */
}

.header-section {
  height: 56px;
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  box-shadow: 0 2px 12px rgba(46, 125, 50, 0.15);
  position: sticky;
  top: 0;
  z-index: 100;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  padding: 8px;
  border-radius: 8px;
  transition: all 0.3s ease;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #fff;
  margin: 0;
}

.placeholder {
  width: 60px;
}

.confirm-content {
  padding: 16px;
}

.result-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--super-light-green);
  border-radius: 12px;
  margin-bottom: 16px;
  color: var(--primary-green);
  font-weight: 500;
  font-size: 14px;
}

.food-confirm-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.food-confirm-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: var(--shadow-light);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}

.food-confirm-card:hover {
  box-shadow: var(--shadow-medium);
  transform: translateY(-2px);
}

.food-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.food-emoji {
  font-size: 42px;
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--super-light-green);
  border-radius: 50%;
  flex-shrink: 0;
}

.food-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.name-input {
  width: 100%;
}

.food-name-cn {
  font-size: 12px;
  color: var(--text-secondary);
}

.food-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.detail-label {
  font-size: 14px;
  color: var(--text-secondary);
  min-width: 70px;
}

.food-freshness {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.freshness-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.shelf-life-input {
  width: 100px;
}

.days-unit {
  font-size: 14px;
  color: var(--text-secondary);
}

.delete-action {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}

.bottom-actions {
  position: fixed;
  bottom: 70px; /* 在底部导航栏上方 */
  left: 0;
  right: 0;
  padding: 16px;
  background: linear-gradient(to top, #fff 0%, rgba(255,255,255,0.98) 100%);
  box-shadow: 0 -4px 20px rgba(46, 125, 50, 0.12);
  display: flex;
  justify-content: center;
  z-index: 1001; /* 确保在导航栏上方 */
  backdrop-filter: blur(10px);
}

.confirm-btn {
  width: 100%;
  max-width: 500px;
  background: linear-gradient(135deg, var(--primary-green) 0%, var(--light-green) 100%);
  border: none;
  font-size: 16px;
  font-weight: 600;
  padding: 16px 32px;
  box-shadow: 0 6px 20px rgba(46, 125, 50, 0.35);
  transition: all 0.3s ease;
  border-radius: 12px;
}

.confirm-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(46, 125, 50, 0.45);
}

.confirm-btn:active {
  transform: scale(0.98);
}

.confirm-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  transform: none;
}

:deep(.el-input-number) {
  width: 120px;
}

:deep(.el-radio-button__inner) {
  background: #fff;
  border-color: var(--light-green);
  color: var(--text-body);
}

:deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: var(--primary-green);
  border-color: var(--primary-green);
  color: #fff;
}

:deep(.el-tag--success) {
  background: var(--super-light-green);
  border-color: var(--light-green);
  color: var(--primary-green);
}
</style>
