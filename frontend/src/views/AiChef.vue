<template>
  <div class="ai-chef-container">
    <!-- 顶部常驻任务提醒 -->
    <div class="active-tasks" v-if="activeReminders.length > 0">
      <div v-for="r in activeReminders" :key="r.id" class="task-tag">
        <el-icon><Timer /></el-icon>
        <span>{{ r.dishName }} 计时中</span>
      </div>
    </div>

    <!-- 1. 聊天消息区 -->
    <div class="chat-messages" ref="chatBox">
      <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.role]">
        <!-- 头像：紧贴气泡 -->
        <div class="avatar">{{ msg.role === 'user' ? '👤' : '👨‍🍳' }}</div>

        <!-- 消息气泡 -->
        <div class="message-bubble">
          <div class="text-content">{{ msg.content }}</div>

          <!-- 核心：一体化菜谱卡片 -->
          <div v-if="msg.recipe && msg.recipe.steps" class="recipe-card">

            <!-- A. 食材清单：2列居中 -->
            <div class="ingredients-section" v-if="msg.recipe.ingredients_list?.length">
              <p class="section-title">🛒 准备食材</p>
              <div class="ing-grid">
                <div v-for="(ing, i) in msg.recipe.ingredients_list" :key="i" class="ing-item">
                  <span class="ing-name">{{ ing.item }}</span>
                  <span class="ing-amount">{{ ing.amount }}</span>
                </div>
              </div>
            </div>

            <!-- B. 营养参考：4列等宽居中 -->
            <div class="nutrition-section" v-if="msg.recipe.nutrition">
              <p class="section-title">📊 营养参考</p>
              <div class="nutri-grid">
                <div class="nutri-item"><span>能量</span><strong>{{msg.recipe.nutrition.calories}}</strong></div>
                <div class="nutri-item"><span>蛋白</span><strong>{{msg.recipe.nutrition.protein}}</strong></div>
                <div class="nutri-item"><span>脂肪</span><strong>{{msg.recipe.nutrition.fat}}</strong></div>
                <div class="nutri-item"><span>碳水</span><strong>{{msg.recipe.nutrition.carbs}}</strong></div>
              </div>
            </div>

            <!-- C. 菜谱标题与语音 -->
            <div class="recipe-header">
              <h4 class="dish-name">{{ msg.recipe.dish_name || '美味教程' }}</h4>
              <el-button
                type="success"
                :icon="Microphone"
                circle
                size="small"
                class="nav-trigger-btn"
                @click="startNavigation(msg.recipe)"
              />
            </div>

            <!-- D. 步骤列表：更小的字体 -->
            <div class="steps-list">
              <div v-for="(step, sIdx) in msg.recipe.steps" :key="sIdx" class="step-item">
                <span class="step-num">{{ sIdx + 1 }}</span>
                <span class="step-text">
                  {{ typeof step === 'object' ? (step.text || step.content) : step }}
                </span>
              </div>
            </div>

            <!-- E. 底部功能按钮 -->
            <div class="card-actions">
              <el-button type="warning" :icon="Location" size="small" @click="goToMarket" plain round>买食材</el-button>
              <el-button type="danger" :icon="Bicycle" size="small" @click="orderDelivery(msg.recipe.dish_name)" plain round>点外卖</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 2. 底部输入栏 -->
    <div class="chat-input-bar-fixed">
      <div class="input-content">
        <el-input v-model="userInput" placeholder="想吃什么？" @keyup.enter="sendMessage()">
          <template #append>
            <el-button @click="sendMessage()" :icon="Promotion" />
          </template>
        </el-input>
      </div>
    </div>

    <!-- 3. 烹饪导航弹窗 (内部强制居中) -->
    <el-dialog v-model="navigationVisible" title="👨‍🍳 烹饪导航" width="92%" destroy-on-close @closed="stopNavigation">
      <div v-if="activeRecipe?.steps?.length" class="nav-dialog-body">
        <el-tag type="success" size="small" class="step-badge">第 {{ currentStepIdx + 1 }} 步</el-tag>

        <h3 class="nav-main-text">{{ activeRecipe.steps[currentStepIdx]?.text }}</h3>

        <div v-if="activeRecipe.steps[currentStepIdx]?.time_estimate >= 60" class="remind-box">
          <el-button type="warning" :icon="AlarmClock" size="small" round @click="setReminder(activeRecipe.steps[currentStepIdx], activeRecipe.dish_name)">
            一键提醒
          </el-button>
        </div>

        <el-progress
          type="circle"
          :percentage="stepPercentage"
          :width="140"
          stroke-width="10"
          color="#67C23A"
        >
          <div class="progress-label">
            <span class="p-time">{{ formatTime(timeLeft) }}</span>
            <span class="p-desc">剩余</span>
          </div>
        </el-progress>

        <div class="nav-btns">
          <el-button @click="prevStep" :disabled="currentStepIdx === 0" size="default">上一步</el-button>
          <el-button type="primary" @click="nextStep" size="default">下一步</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import axios from 'axios'
import { Promotion, Microphone, Location, AlarmClock, Timer, Bicycle } from '@element-plus/icons-vue'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'

// --- 基础定义 ---
const BASE_URL = 'https://thermal-armful-surfer.ngrok-free.dev';
const props = defineProps(['pendingDish'])
const emit = defineEmits(['clear-pending'])
const userInput = ref('')
const loading = ref(false)
const chatBox = ref(null)
const messages = ref([])
const userStr = localStorage.getItem('user');
const userId = JSON.parse(userStr || '{}').id;
// --- 导航与提醒状态 ---
const navigationVisible = ref(false)
const activeRecipe = ref({ steps: [] })
const currentStepIdx = ref(0)
const timeLeft = ref(0)
const isListening = ref(false)
const activeReminders = ref([])
let timer = null
let recognition = null
let currentAudio = null // 当前正在播放的音频对象

// --- 工具函数 ---
const scrollToBottom = async () => {
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

const formatTime = (totalSeconds) => {
  const s = Math.max(0, Math.floor(totalSeconds))
  const mins = Math.floor(s / 60)
  const secs = s % 60
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const stepPercentage = computed(() => {
  if (!activeRecipe.value?.steps?.length) return 0
  const step = activeRecipe.value.steps[currentStepIdx.value]
  const total = Math.max(step?.time_estimate || 60, 1)
  return Math.floor(((total - timeLeft.value) / total) * 100)
})

const fetchHistory = async () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  if (!user.id) return;

  try {
    const res = await axios.get(`${BASE_URL}/api/chat-history`, {
      params: { user_id: user.id },
      headers: { "ngrok-skip-browser-warning": "true" }
    });
    if (res.data && res.data.length > 0) {
      messages.value = res.data;
    } else {
      messages.value = [{ role: 'assistant', content: '你好！我是你的智能厨房助手。' }];
    }
    await nextTick();
    scrollToBottom();
  } catch (err) {
    console.error("加载历史记录失败", err);
  }
};

// --- 业务逻辑 ---
// 修改 src/views/AiChef.vue 的加载部分
onMounted(async () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  if (!user.id) return;

  try {
    await axios.get(BASE_URL, { headers: { "ngrok-skip-browser-warning": "true" } });
    console.log("ngrok 预验证成功");
  } catch (e) {
    console.warn("ngrok 预验证跳过");
  }

  // 2. 调用上面定义好的函数
  await fetchHistory();
});

watch(() => props.pendingDish, (newDish) => {
  if (newDish && newDish.trim() !== '') {
    setTimeout(() => {
      sendMessage(`我想做${newDish}，请给我详细教程`)
      emit('clear-pending')
    }, 500)
  }
}, { immediate: true })

const sendMessage = async (val = null) => {
  // 1. 获取文本并清空输入框
  const text = (val && typeof val === 'string') ? val : userInput.value;
  if (!text || !text.trim() || loading.value) return;

  userInput.value = '';

  // 2. 展示用户气泡
  messages.value.push({ role: 'user', content: text });

  // 3. 开启加载状态
  loading.value = true;
  await scrollToBottom();

  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}');

    // 4. 发起标准的 Axios 请求 (不再使用 fetch 流)
    const res = await axios.get('https://thermal-armful-surfer.ngrok-free.dev/api/recommend-recipe', {
      params: {
        user_prompt: text,
        user_id: user.id,
        save_history: true
      },
      headers: { "ngrok-skip-browser-warning": "true" }
    });

    if (res.data.status === 'success') {
      const recipe = res.data.recipe;

      // 5. 将 AI 回复推入列表
      messages.value.push({
        role: 'assistant',
        content: `为你准备好了：${recipe.dish_name}`,
        recipe: recipe,
        missing: recipe.missing || []
      });
    } else {
      messages.value.push({ role: 'assistant', content: res.data.message });
    }
  } catch (e) {
    console.error("请求失败:", e);
    messages.value.push({ role: 'assistant', content: '抱歉，连接服务器失败。' });
  } finally {
    loading.value = false; // 结束加载
    await scrollToBottom();
  }
};

// --- 导航与消耗逻辑 ---
const startNavigation = (recipe) => {
  activeRecipe.value = recipe
  currentStepIdx.value = 0
  navigationVisible.value = true
  initVoiceRecognition()
  runStep()
}

// 修改 runStep 函数
const runStep = async () => {
  // 1. 物理级清理计时器
  if (timer) { clearInterval(timer); timer = null; }

  const step = activeRecipe.value.steps[currentStepIdx.value];
  if (!step) return;

  timeLeft.value = Number(step.time_estimate) || 60;

  // ✅ 核心修复 1：播报前彻底注销识别器，防止它在后台偷偷重启
  if (recognition) {
    try {
      recognition.onend = null;
      recognition.onerror = null;
      recognition.stop();
      isListening.value = false;
      console.log("🔇 准备播报，已物理切断麦克风");
    } catch(e) { console.log("麦克风停止中...") }
  }

  // 2. 请求并播放语音
  try {
    const text = `第${currentStepIdx.value + 1}步：${step.text}`;

    // 1. 获取音频文件路径
    const res = await axios.get(`${BASE_URL}/api/tts`, {
      params: { text },
      headers: { "ngrok-skip-browser-warning": "true" }
    });

    if (res.data && res.data.audio_url) {
      const audioUrl = `${BASE_URL}${res.data.audio_url}`;

      // ✅ 核心修复：不直接用 new Audio(url)
      // 使用 axios 以 blob 形式下载音频，强制带上跳过头
      const audioRes = await axios.get(audioUrl, {
        responseType: 'blob', // 关键：获取二进制数据
        headers: { "ngrok-skip-browser-warning": "true" }
      });

      // 2. 将下载的 Blob 转换为本地临时 URL
      const blobUrl = window.URL.createObjectURL(audioRes.data);
      const audio = new Audio(blobUrl);

      audio.onended = () => {
        window.URL.revokeObjectURL(blobUrl); // 播放完释放内存
        initVoiceRecognition();
      };

      await audio.play();

      // 3. 启动计时器
      timer = setInterval(() => {
        if (timeLeft.value > 0) timeLeft.value--;
      }, 1000);

    }
  } catch (e) {
    console.error("语音播报全链路失败:", e);
    initVoiceRecognition();
  }
};

const setLongTimeReminder = (step, dishName) => {
  const seconds = step.time_estimate
  const minutes = Math.floor(seconds / 60)

  ElNotification({
    title: '定时提醒已开启',
    message: `将在 ${minutes} 分钟后大声提醒您：${step.text.substring(0, 10)}...`,
    type: 'success'
  })

  // 启动后台 setTimeout (即使关闭弹窗也会执行)
  setTimeout(() => {
    // 1. 播放闹钟铃声
    const audio = new Audio("https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3")
    audio.loop = true
    audio.play()

    // 2. 弹出强提醒弹窗
    ElMessageBox.alert(
      `时间到啦！请进行下一步操作：${step.text}`,
      `⏰ [${dishName}] 提醒`,
      {
        confirmButtonText: '关闭闹钟',
        callback: () => audio.pause()
      }
    )
  }, seconds * 1000)
}

const nextStep = async () => {
  if (currentStepIdx.value < activeRecipe.value.steps.length - 1) {
    currentStepIdx.value++
    runStep()
  } else {
    try {
      const used = activeRecipe.value.used_ingredients || []
      if (used.length > 0) await axios.post('https://thermal-armful-surfer.ngrok-free.dev/api/consume-ingredients', used)
      ElMessage.success("烹饪完成，库存已更新！")
    } catch (e) { console.error(e) }
    navigationVisible.value = false
  }
}

const prevStep = () => {
  if (currentStepIdx.value > 0) {
    currentStepIdx.value--
    runStep()
  }
}

// --- 功能性跳转 ---
const goToMarket = () => {
  navigator.geolocation.getCurrentPosition((pos) => {
    const { longitude, latitude } = pos.coords;
    const amapScheme = `androidamap://poi?poiname=菜市场&lat=${latitude}&lon=${longitude}&dev=0`;

    window.location.href = amapScheme;

    setTimeout(() => {
      const h5Url = `https://uri.amap.com/search?keyword=菜市场&center=${longitude},${latitude}&view=map&src=smart_cooking&coordinate=gaode&callnative=1`;
      window.open(h5Url, '_blank');
    }, 500);

  }, () => {
    window.open('https://uri.amap.com/search?keyword=菜市场', '_blank');
  });
};

const orderDelivery = (dishName) => {
  if (!dishName) return;

  ElMessageBox.confirm(`这就去为您搜索 [${dishName}] 的外卖吗？`, '外卖下单', {
    confirmButtonText: '出发',
    cancelButtonText: '再想想',
    type: 'info',
    center: true
  }).then(() => {
    // 1. 美团外卖原生 Scheme
    const meituanScheme = `imeituan://www.meituan.com/s/${encodeURIComponent(dishName)}`;

    // 尝试唤起
    window.location.href = meituanScheme;

    // 2. 兜底逻辑：打开美团 H5 搜索页
    setTimeout(() => {
      const h5Url = `https://v.meituan.com/s/${encodeURIComponent(dishName)}`;
      window.location.href = h5Url;
    }, 500);
  }).catch(() => {});
};

const playVoice = async (url) => {
  const fullUrl = `${NGROK_URL}${url}`; // 使用上面导出的域名

  // ✅ 核心技巧：用 axios 强制带 header 下载
  const res = await api.get(fullUrl, { responseType: 'blob' });

  const blobUrl = window.URL.createObjectURL(res.data);
  const audio = new Audio(blobUrl);
  audio.play();

  audio.onended = () => window.URL.revokeObjectURL(blobUrl);
}

// --- 语音识别与定时器 ---
const initVoiceRecognition = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRecognition) return;

  if (recognition) {
     try { recognition.stop(); } catch(e) {}
  }

  recognition = new SpeechRecognition();
  recognition.continuous = true;
  recognition.lang = 'zh-CN';

  recognition.onstart = () => {
    isListening.value = true;
    console.log("🎙️ 麦克风已就绪...");
  };

  recognition.onresult = (event) => {
    const text = event.results[event.results.length - 1][0].transcript.trim();
    console.log("👂 听到了:", text);
    if (text.includes("下一步") || text.includes("下一位")) {
      nextStep();
    }
  };

  recognition.onerror = (event) => {
    console.warn("语音识别详情错误:", event.error);
    if (event.error === 'aborted') {
      // ✅ 如果是由于系统原因被切断，不要立即重启，设为 False
      isListening.value = false;
    }
  };

  // 只有在弹窗还开着的情况下，非主动停止才尝试重启
  recognition.onend = () => {
    if (navigationVisible.value && isListening.value) {
      setTimeout(() => {
        try { recognition.start(); } catch(e) {}
      }, 1000); // 延迟1秒重启，给硬件喘息时间
    } else {
      isListening.value = false;
    }
  };

  try {
    recognition.start();
  } catch (e) {
    console.error("启动失败:", e);
  }
};

const stopNavigation = () => {
  if (timer) { clearInterval(timer); timer = null; }

  if (recognition) {
    console.log("正在销毁识别实例...");
    isListening.value = false; // 先设为 false 防止 onend 自动重启
    recognition.onend = null;
    recognition.onerror = null;
    try { recognition.stop(); } catch(e) {}
    recognition = null;
  }
};

const setReminder = (step, dishName) => {
  const seconds = step.time_estimate || 0
  const reminder = {
    id: Date.now(), dishName, stepText: step.text,
    timer: setTimeout(() => triggerAlarm(reminder), seconds * 1000)
  }
  activeReminders.value.push(reminder)
  ElNotification.success({ title: '提醒已设置', message: `${Math.floor(seconds/60)}分钟后提醒` })
}

const triggerAlarm = (reminder) => {
  const audio = new Audio("https://assets.mixkit.co/active_storage/sfx/2869/2869-preview.mp3")
  audio.loop = true; audio.play()
  ElMessageBox.confirm(`[${reminder.dishName}] 阶段完成！内容：${reminder.stepText}`, '⏰ 时间到', {
    confirmButtonText: '确定', showCancelButton: false, type: 'warning'
  }).then(() => {
    audio.pause()
    activeReminders.value = activeReminders.value.filter(r => r.id !== reminder.id)
  })
}

onUnmounted(stopNavigation)
</script>

<style scoped>
.ai-chef-container {
  height: calc(100vh - 70px);
  display: flex;
  flex-direction: column;
  background: #f1f4f3;
  overflow: hidden;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 10px 8px 100px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* ✅ 头像与气泡紧密连接 */
.message-wrapper {
  display: flex;
  gap: 4px; /* 极小间距 */
  max-width: 98%;
}
.message-wrapper.user { align-self: flex-end; flex-direction: row-reverse; }

.avatar {
  width: 32px;
  height: 32px;
  font-size: 18px;
  background: #fff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
}

.message-bubble {
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px; /* 较小字体 */
  line-height: 1.5;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  max-width: 85%;
}
.user .message-bubble { background: #4CAF50; color: #fff; border-top-right-radius: 2px; }
.assistant .message-bubble { border-top-left-radius: 2px; }

/* ✅ 菜谱卡片优化 */
.recipe-card {
  margin-top: 10px;
  padding: 10px;
  background: #fdfdfd;
  border-radius: 10px;
  border-left: 4px solid var(--primary-green);
  /* ✅ 关键：确保卡片本身不超出父容器 */
  max-width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.section-title {
  font-size: 13px;
  font-weight: bold;
  color: #666;
  margin: 10px 0 8px 0;
  display: flex;
  align-items: center;
  gap: 4px;
}

.ing-grid {
  display: grid;
  /* ✅ 使用 minmax(0, 1fr) 强制列宽不超出范围 */
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  background: #f9f9f9;
  padding: 10px;
  border-radius: 8px;
  width: 100%;
  box-sizing: border-box;
}

.ing-item {
  display: flex;
  /* ✅ 改为垂直排列（名字在上，用量在下），这是适配手机长文本最稳妥的方法 */
  flex-direction: column;
  align-items: flex-start;
  padding: 4px 6px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #f0f4f1;
  min-width: 0; /* 防止内容撑开 grid */
}
.ing-name {
  font-size: 12px;
  font-weight: bold;
  color: #333;
  width: 100%;
  /* 名字过长时显示省略号 */
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ing-amount {
  font-size: 10px; /* 用量字体调小 */
  color: #888;
  margin-top: 2px;
  /* ✅ 允许用量部分换行，防止括号里的长文字超出 */
  word-break: break-all;
  line-height: 1.2;
}

.nutri-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  width: 100%;
  box-sizing: border-box;
}
.nutri-item span { display: block; font-size: 10px; color: #999; }
.nutri-item strong { font-size: 11px; color: #4CAF50; }

.recipe-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 8px;
  padding-bottom: 5px;
  border-bottom: 1px solid #f0f0f0;
}
.dish-name { font-size: 14px; margin: 0; color: #333; }

.step-item { display: flex; gap: 8px; margin-bottom: 8px; align-items: flex-start; }
.step-num {
  background: #4CAF50; color: #fff; width: 18px; height: 18px;
  border-radius: 50%; display: flex; align-items: center; justify-content: center;
  font-size: 10px; flex-shrink: 0; margin-top: 2px;
}
.step-text { font-size: 13px; color: #444; }

.card-actions { display: flex; gap: 6px; margin-top: 10px; justify-content: center; }

/* ✅ 导航弹窗居中布局 */
.nav-dialog-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.nav-main-text { font-size: 16px; margin: 15px 0; min-height: 50px; color: #333; }
.progress-label { display: flex; flex-direction: column; }
.p-time { font-size: 22px; font-weight: bold; color: #4CAF50; }
.p-desc { font-size: 10px; color: #999; }
.nav-btns { display: flex; gap: 15px; margin-top: 20px; }

/* 固定底部输入框 */
.chat-input-bar-fixed {
  position: fixed;
  bottom: 70px;
  left: 0; right: 0;
  padding: 8px 10px env(safe-area-inset-bottom);
  background: rgba(255,255,255,0.95);
  border-top: 1px solid #eee;
  z-index: 100;
}
</style>