<template>
  <div class="manage-container">
    <!-- 1. 顶部栏：标题与按钮并排 -->
    <div class="header-section">
      <div class="brand">
        <h2 class="title">我的冰箱</h2>
        <span class="count-tip">当前共有 {{ inventory.length }} 件食材</span>
      </div>

      <div class="header-actions">
        <!-- 手动添加按钮 -->
        <div class="action-btn-green" @click.stop="showAddDialog = true">
          <el-icon><Plus /></el-icon>
        </div>

        <!-- 拍照识别按钮 -->
        <el-upload
          :action="`${BASE_URL}/api/analyze-fridge`"
          :headers="{ 'ngrok-skip-browser-warning': 'true' }"
          :on-success="handleUploadSuccess"
          :show-file-list="false"
          accept="image/*"
        >
          <!-- ✅ 核心修改：使用统一的绿色按钮类名 -->
          <div class="action-btn-green">
            <el-icon><CameraFilled /></el-icon>
          </div>
        </el-upload>
      </div>
    </div>

    <!-- 2. 搜索框：精简尺寸 -->
    <div class="search-section">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索食材..."
        prefix-icon="Search"
        size="default"
        clearable
      />
    </div>

    <!-- 3. 食材可视化网格：3列布局 -->
    <div class="food-grid">
      <div
        v-for="item in filteredInventory"
        :key="item.id"
        class="food-card"
        @click="editItem(item)"
      >
        <!-- ✅ 左上角：编辑按钮 -->
        <div class="card-btn-edit" @click.stop="editItem(item)">
          <el-icon><EditPen /></el-icon>
        </div>

        <!-- ✅ 右上角：删除按钮 -->
        <div class="card-btn-del" @click.stop="removeItem(item.id)">
          <el-icon><CloseBold /></el-icon>
        </div>

        <!-- 中间主体：图标 -->
        <div class="food-visual">
          <span class="emoji">{{ getFoodInfo(item.name).emoji }}</span>
        </div>

        <!-- 下方：文字和进度 -->
        <div class="food-info">
          <div class="food-name">{{ getFoodInfo(item.name).cn }}</div>
          <div class="food-qty">x{{ item.quantity }}</div>
          <div class="fresh-box">
            <el-progress
              :percentage="Math.min(100, (item.daysUntilExpiry / (item.shelf_life || 7)) * 100)"
              :color="item.daysUntilExpiry <= 3 ? '#F56C6C' : '#67C23A'"
              :stroke-width="3"
              :show-text="false"
            />
            <span class="days-text">{{ item.daysUntilExpiry }}天</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 4. 推荐菜谱区域：横向卡片样式 -->
    <div class="recommend-section" v-if="inventory.length > 0">
      <div class="main-title"> <!-- ✅ 类名改为 main-title -->
        <el-icon><KnifeFork /></el-icon> 今日灵感
      </div>

      <div class="recipe-container" v-loading="recLoading">
        <div v-for="(rec, index) in quickRecipes" :key="index" class="recipe-row-card">
          <!-- 左侧：图标和名字 -->
          <div class="rec-info">
            <span class="rec-icon">🍳</span>
            <span class="rec-dish-name">{{ rec?.dish_name || '构思中...' }}</span>
          </div>
          <!-- 右侧：按钮 -->
          <el-button
            type="success"
            size="small"
            round
            class="rec-go-btn"
            @click="goToChef(rec.dish_name)"
          >
            咨询教程 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>
    </div>

    <!-- 5. 弹窗组件：手动添加 -->
    <el-dialog v-model="showAddDialog" title="添加食材" width="92%" center>
       <el-form :model="newItem" label-width="70px">
          <el-form-item label="名称"><el-input v-model="newItem.name" placeholder="如：牛肉" /></el-form-item>
          <el-form-item label="数量"><el-input-number v-model="newItem.quantity" :min="1" /></el-form-item>
          <el-form-item label="保质期"><el-input-number v-model="newItem.shelf_life" :min="1" /> 天</el-form-item>
       </el-form>
       <template #footer>
         <el-button @click="showAddDialog = false">取消</el-button>
         <el-button type="primary" @click="saveNewItem">确认添加</el-button>
       </template>
    </el-dialog>

    <!-- 6. 弹窗组件：编辑食材 -->
    <el-dialog v-model="editDialogVisible" title="修改信息" width="92%" center>
       <el-form :model="editingItem" label-width="70px">
          <el-form-item label="名称"><el-input v-model="editingItem.name" /></el-form-item>
          <el-form-item label="数量"><el-input-number v-model="editingItem.quantity" :min="1" /></el-form-item>
          <el-form-item label="保质期"><el-input-number v-model="editingItem.shelf_life" :min="1" /> 天</el-form-item>
       </el-form>
       <template #footer>
         <el-button @click="editDialogVisible = false">取消</el-button>
         <el-button type="primary" @click="saveEdit">保存修改</el-button>
       </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { CameraFilled, CircleCloseFilled, Delete, KnifeFork, ArrowRight, EditPen, WarningFilled, Search, Plus, CloseBold } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { BASE_URL } from '@/api/auth';

const router = useRouter()
const inventory = ref([])
const searchKeyword = ref('')
const activeCategory = ref('all')
const editDialogVisible = ref(false)
const editingItem = ref({})
const quickRecipes = ref([])
const recLoading = ref(false)
const showAddDialog = ref(false)
const emit = defineEmits(['consult-recipe', 'clear-pending'])
// 用户隔离
const userStr = localStorage.getItem('user');
const userInfo = userStr ? JSON.parse(userStr) : null;
const userId = userInfo ? userInfo.id : null;

const filteredInventory = computed(() => {
  const list = inventory.value.filter(item => {
    const info = getFoodInfo(item.name);
    const matchesSearch = !searchKeyword.value ||
                         info.cn.includes(searchKeyword.value) ||
                         item.name.toLowerCase().includes(searchKeyword.value.toLowerCase());

    const matchesCategory = activeCategory.value === 'all' ||
                           info.category === activeCategory.value;

    return matchesSearch && matchesCategory;
  });
  return list.map(item => {
    return {
      ...item,
      daysUntilExpiry: calculateDaysUntilExpiry(item) // 调用你定义的计算天数函数
    };
  });
});

const newItem = ref({
  name: '',
  quantity: 1,
  storage_type: '冷藏',
  shelf_life: 7
})

const editItem = (item) => {
  console.log("触发编辑:", item.name); // 调试用
  editingItem.value = { ...item };
  // 转换显示名称
  editingItem.value.name = getFoodInfo(item.name).cn;
  editDialogVisible.value = true;
}

// 分类筛选
const categories = [
  { id: 'all', name: '全部' },
  { id: 'vegetable', name: '蔬菜' },
  { id: 'meat', name: '肉类' },
  { id: 'fruit', name: '水果' },
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
  'carrot': { emoji: '🥕', cn: '胡萝卜', category: 'vegetable' },
  'chili': { emoji: '🌶️', cn: '辣椒', category: 'vegetable' },
  'garlic': { emoji: '🧄', cn: '大蒜', category: 'vegetable' },
  'leek': { emoji: '🌿', cn: '大葱', category: 'vegetable' },
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
  'broccoli': { emoji: '🥦', cn: '西兰花', category: 'vegetable' },
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
  'egg': { emoji: '🥚', cn: '鸡蛋', category: 'other' },
  'tofu': { emoji: '🧈', cn: '豆腐', category: 'other' },
  'rice': { emoji: '🍚', cn: '大米', category: 'other' },
  'noodle': { emoji: '🍜', cn: '面条', category: 'other' }
}

// 中文到英文的反向映射
const cnToEnMap = {
  // 肉类
  '牛肉': 'beef',
  '猪肉': 'pork',
  '鸡肉': 'chicken',
  '鸭肉': 'duck',
  '羊肉': 'mutton',
  '排骨': 'ribs',
  '火腿': 'ham',
  '香肠': 'sausage',
  '牛筋': 'beef_tendon',
  '猪肝': 'pig_liver',

  // 蔬菜
  '白菜': 'cabbage',
  '胡萝卜': 'carrot',
  '辣椒': 'chili',
  '大蒜': 'garlic',
  '大葱': 'leek',
  '洋葱': 'onion',
  '土豆': 'potato',
  '西红柿': 'tomato',
  '黄瓜': 'cucumber',
  '菠菜': 'spinach',
  '生菜': 'lettuce',
  '茄子': 'eggplant',
  '南瓜': 'pumpkin',
  '蘑菇': 'mushroom',
  '生姜': 'ginger',
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
  '豆腐': 'tofu',
  '大米': 'rice',
  '面条': 'noodle'
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
const calculateDaysUntilExpiry = (item) => {
  if (!item.add_time) return Number(item.shelf_life) || 7;

  const addDate = new Date(item.add_time);

  // ✅ 核心点：这里必须使用传入的 item 里的实时 shelf_life
  const lifeDays = Number(item.shelf_life) || 7;

  const expiryDate = new Date(addDate.getTime() + lifeDays * 24 * 60 * 60 * 1000);
  const now = new Date();

  const diffTime = expiryDate - now;
  const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

  return diffDays > 0 ? diffDays : 0;
};

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  return `${date.getMonth() + 1}-${date.getDate()}`;
}

const fetchInventory = async () => {
  if (!userId) return;
  try {
    const res = await axios.get('https://thermal-armful-surfer.ngrok-free.dev/api/inventory',
    {
      params: { user_id: userId },
      headers: { "ngrok-skip-browser-warning": "true" }
    });
    inventory.value = res.data.map(item => ({
      ...item,
      daysUntilExpiry: calculateDaysUntilExpiry(item)
    }));
    if (inventory.value.length > 0) fetchQuickRecipes();
  } catch (error) {
    console.error("同步失败", error);
  }
};

// 1. 打开编辑弹窗
const openEditDialog = (item) => {
  // ✅ 核心点：必须使用结构赋值 {...item} 拷贝一份数据
  // 否则你在弹窗里改字，背景卡片会跟着变，用户点取消也回不去了
  editingItem.value = { ...item };
  editDialogVisible.value = true;
};

// 2. 提交编辑到后端
const confirmEdit = async () => {
  if (!userId) return;

  try {
    // 调用后端更新接口
    const res = await axios.put(
      `https://thermal-armful-surfer.ngrok-free.dev/api/inventory/${editingItem.value.id}`,
      editingItem.value, // 发送修改后的对象
      {
        params: { user_id: userId },
        headers: { "ngrok-skip-browser-warning": "true" }
      }
    );

    if (res.data.status === 'success') {
      ElMessage.success('修改成功');
      editDialogVisible.value = false;
      await fetchInventory(); // 刷新列表
    } else {
      ElMessage.error(res.data.message || '修改失败');
    }
  } catch (error) {
    console.error("编辑失败:", error);
    ElMessage.error('网络错误，无法保存');
  }
};

const goToChef = (dishName) => {
  if (!dishName) return;
  console.log("准备咨询菜谱:", dishName);
  // 向父组件 (App.vue) 发送事件，通知它切换页面并传递菜名
  emit('consult-recipe', dishName);
}

const saveToInventory = async () => {
  if (!userId || tempItems.value.length === 0) return

  try {
    await axios.post('https://thermal-armful-surfer.ngrok-free.dev/api/add-to-inventory', tempItems.value, {
      params: { user_id: userId },
      headers: { "ngrok-skip-browser-warning": "true" }
    })

    showConfirm.value = false
    ElMessage.success('已存入您的私人冰箱')
    fetchInventory() // 刷新列表
  } catch (error) {
    ElMessage.error('入库失败')
  }
}

const fetchQuickRecipes = async () => {
  if (!userId || inventory.value.length === 0) return;
  recLoading.value = true;

  try {
    const res = await axios.get(`${BASE_URL}/api/recommend-recipe`, {
      params: {
        user_id: userId,
        user_prompt: "根据库存推荐1个中文菜名。只需JSON格式: {\"dish_name\":\"菜名\",\"used_main\":\"主要食材\"}",
        save_history: false
      },
      headers: { "ngrok-skip-browser-warning": "true" } // ✅ 双重保险
    });

    if (res.data.status === 'success' && res.data.recipe) {
      quickRecipes.value = [res.data.recipe];
    }
  } catch (e) {
    console.error("首页推荐获取失败:", e);
    // 💡 调试小技巧：如果报错，弹窗显示具体原因
    // ElMessage.error("推荐失败: " + e.message);
  } finally {
    recLoading.value = false;
  }
};


// 保存编辑
const saveEdit = async () => {
  try {
    const enName = convertCnToEn(editingItem.value.name);

    // ✅ 构造要发送的数据体，确保包含 shelf_life
    const submitData = {
      name: enName,
      quantity: editingItem.value.quantity,
      storage_type: editingItem.value.storage_type,
      shelf_life: Number(editingItem.value.shelf_life) // 强制转为数字发送
    };

    const res = await axios.put(
      `https://thermal-armful-surfer.ngrok-free.dev/api/inventory/${editingItem.value.id}`,
      submitData,
      {
        params: { user_id: userId },
        headers: { "ngrok-skip-browser-warning": "true" }
      }
    );

    if (res.data.status === 'success') {
      ElMessage.success('保鲜期已更新');
      editDialogVisible.value = false;

      // ✅ 关键：必须重新获取后端最新的数据
      // 只要 inventory.value 变了，你的计算属性 filteredInventory 就会自动重算天数
      await fetchInventory();
    }
  } catch (error) {
    ElMessage.error('修改失败');
  }
};

// 修改 ManageFridge.vue 中的 removeItem 函数
const removeItem = async (id) => {
  try {
    // 1. 确认框
    await ElMessageBox.confirm('确定要从冰箱移除这件食材吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    });

    // 2. 发送请求
    // 注意：Axios DELETE 的参数放在第二个参数的 params 里
    const res = await axios.delete(`https://thermal-armful-surfer.ngrok-free.dev/api/inventory/${id}`, {
      params: { user_id: userId },
      headers: { "ngrok-skip-browser-warning": "true" }
    });

    if (res.data.status === 'success') {
      ElMessage.success('已移出冰箱');

      // ✅ 核心修复：必须加 await，确保数据取回来后再让 Vue 更新界面
      await fetchInventory();

      // 可选：同时刷新下方的灵感菜谱，因为食材变了
      await fetchQuickRecipes();
    } else {
      ElMessage.error(res.data.message || '移除失败');
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error("删除出错:", error);
      ElMessage.error('网络错误，请重试');
    }
  }
}

// 手动添加新食材
const saveNewItem = async () => {
  if (!newItem.value.name || !userId) return ElMessage.warning('请填写名称');
  try {
    // ✅ 修正 3：发送 Body 必须是列表，参数放在 params
    await axios.post('https://thermal-armful-surfer.ngrok-free.dev/api/add-to-inventory', [newItem.value], {
      params: { user_id: userId },
      headers: { "ngrok-skip-browser-warning": "true" }
    });
    ElMessage.success('已加入私人冰箱');
    showAddDialog.value = false;
    fetchInventory();
  } catch (e) { ElMessage.error('存入失败'); }
};

const handleUploadSuccess = (response) => {
  const data = response.data || response;
  if (data.status === "success" && data.detected && data.detected.length > 0) {
    // 将识别结果转换为临时数据格式，包含存储方式
    const itemsWithStorage = data.detected.map(item => ({
      name: item.name,
      quantity: item.quantity || 1,
      storage_type: '冷藏', // 默认冷藏
      shelf_life: 7 // 默认 7 天
    }));
    
    // 存储到 localStorage 以便在确认页使用
    localStorage.setItem('tempIdentifiedItems', JSON.stringify(itemsWithStorage));
    
    // 跳转到识别结果确认页
    router.push('/capture-confirm');
  } else {
    ElMessage.warning('未能识别到食材');
  }
}



onMounted(() => { if (!userId) router.push('/login'); else fetchInventory(); });
</script>

<style scoped>
.action-btn-green {
  width: 40px;
  height: 40px;
  /* 绿色渐变背景 */
  background: linear-gradient(135deg, #4CAF50 0%, #66BB6A 100%);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 10px rgba(76, 175, 80, 0.3);
  cursor: pointer;
  position: relative;
  z-index: 100; /* 确保在最上层，可点击 */
  transition: transform 0.2s, box-shadow 0.2s;
}

.action-btn-green .el-icon {
  color: #ffffff !important;
  font-size: 22px;
}
.action-btn-green:active {
  transform: scale(0.92);
  box-shadow: 0 2px 5px rgba(76, 175, 80, 0.2);
}
/* --- 2. 统一大标题字号 --- */
.title, .main-title {
  font-size: 22px; /* ✅ 确保两者大小一致 */
  font-weight: bold;
  color: #2c3e50;
  margin: 0;
  display: flex;
  align-items: center;
  gap: 8px;
}

.main-title {
  margin-bottom: 15px;
  margin-top: 25px;
}

/* --- 3. 灵感菜谱一排显示 --- */
.recipe-row-card {
  background: linear-gradient(135deg, #ffffff 0%, #f1f9f2 100%);
  border-radius: 16px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between; /* ✅ 关键：左右分布 */
  align-items: center;           /* ✅ 关键：垂直居中 */
  box-shadow: 0 4px 15px rgba(0,0,0,0.05);
  border: 1px solid rgba(76, 175, 80, 0.1);
}

.rec-info {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1; /* 占据左侧空间 */
  min-width: 0; /* 允许内部文字溢出处理 */
}

.rec-icon { font-size: 24px; }

.rec-dish-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
  /* 防止菜名太长破坏一行布局，自动显示省略号 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rec-go-btn {
  margin-left: 10px;
  padding: 8px 16px;
  font-weight: bold;
  flex-shrink: 0; /* ✅ 保证按钮不被压缩 */
}
/* --- 1. 基础容器与布局 --- */
.manage-container {
  padding: 10px 8px; /* 进一步压缩边距 */
  background: #f8faf9;
  min-height: 100vh;
}

/* --- 2. 顶部标题与按钮并排 (核心修改) --- */
.header-section {
  display: flex;
  justify-content: space-between;
  align-items: center; /* 确保标题和按钮中心对齐 */
  margin-bottom: 12px;
  position: relative;
  z-index: 10; /* 确保标题栏整体在上方 */
}

.brand {
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 19px; /* 字体稍微调小 */
  margin: 0;
  font-weight: bold;
  color: #333;
  white-space: nowrap;
}

.count-tip { font-size: 11px; color: #999; }

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

/* 按钮点击热区强化：增加 z-index 和显式 cursor */
.action-trigger {
  width: 38px; height: 38px;
  background: #fff;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  font-size: 20px;
  color: #4CAF50;
  cursor: pointer !important; /* 强制显示手型 */
  position: relative;
  z-index: 100; /* 必须最高，防止被遮挡 */
  pointer-events: auto; /* 强制接收点击事件 */
}

.color-trigger { background: var(--primary-green); color: #fff; }

/* --- 3. 搜索栏收缩 --- */
.search-section { margin-bottom: 15px; }

/* --- 4. 紧凑型三列食材网格 --- */
.food-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 10px;
  margin-top: 15px;
}

.food-card {
  position: relative; /* 关键：作为按钮定位的基准 */
  background: #fff;
  border-radius: 12px;
  padding: 20px 4px 10px; /* 增加顶部内边距，防止图标盖住按钮 */
  display: flex;
  flex-direction: column;
  align-items: center;
  border: 1px solid #f0f0f0;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
  transition: all 0.3s;
}

/* ✅ 按钮点不动终极修复：增加 z-index 并稍微偏移 */
.card-btn-edit, .card-btn-del {
  position: absolute; /* 关键：绝对定位 */
  top: 6px;           /* 距离顶部 6px */
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
}

.card-btn-edit {
  left: 6px;
  color: #4CAF50; /* 绿色编辑 */
  font-size: 16px;
}

.card-btn-del {
  right: 6px;
  color: #ff7675; /* 红色删除 */
  font-size: 16px;
}

/* 缩小图标尺寸 */
.food-visual {
  width: 38px;
  height: 38px;
  font-size: 24px;
  margin-bottom: 8px;
  background: #f8fbf9;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* ✅ 文字排版：小字且完整显示 */
.food-info {
  text-align: center;
  width: 100%;
}

.food-name {
  font-size: 11px;
  font-weight: bold;
  color: #333;
  margin-bottom: 2px;
}

.food-qty {
  font-size: 10px;
  color: #999;
  margin-bottom: 6px;
}

.fresh-box { width: 80%; }
.days-text { font-size: 9px; color: #888; display: block; text-align: center; margin-top: 2px; }

/* --- 5. 灵感菜谱一排显示 --- */
.recommend-section { margin-top: 25px; padding-bottom: 90px; }
.section-title { font-size: 14px; font-weight: bold; color: #555; margin-bottom: 10px; }
.recipe-inline-card {
  background: linear-gradient(135deg, #ffffff 0%, #f1f9f2 100%);
  border-radius: 14px;
  padding: 10px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4px 12px rgba(46, 125, 50, 0.06);
  border: 1px solid rgba(76, 175, 80, 0.1);
  margin-top: 10px;
}
.recipe-mini-card {
  background: linear-gradient(135deg, #ffffff 0%, #f1f9f2 100%);
  border-radius: 14px;
  padding: 10px 14px;
  display: flex;
  justify-content: space-between; /* ✅ 左右撑开 */
  align-items: center;
  box-shadow: 0 4px 12px rgba(46, 125, 50, 0.06);
  border: 1px solid rgba(76, 175, 80, 0.1);
}

.rec-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1; /* 占据左侧所有空间 */
  min-width: 0; /* 允许截断 */
}

.rec-icon { font-size: 22px; }
.rec-name {
  font-weight: bold;
  font-size: 13px;
  color: #2c3e50;
  /* ✅ 防止菜名太长挤坏按钮 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.go-btn {
  font-size: 11px;
  padding: 6px 12px;
  flex-shrink: 0; /* ✅ 禁止按钮被压缩 */
  margin-left: 10px;
}
</style>
