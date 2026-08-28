<template>
  <div class="ai-chef-container">
    <header class="chef-hero">
      <div class="chef-hero-inner">
        <div class="chef-brand-row">
          <div class="chef-brand"><span>Cook<strong>X</strong></span><i></i><b>AI 厨房</b></div>
          <div class="hero-statuses">
            <span :class="['device-pill', { connected: temperatureConnected }]">
              <el-icon><Connection /></el-icon>CookX Sense {{ temperatureConnectionText }}
            </span>
            <span class="voice-pill"><el-icon><Microphone /></el-icon>{{ isCookingPaused ? '语音已暂停' : (navigationVisible ? '语音指导中' : '语音待命') }}</span>
          </div>
        </div>

        <div v-if="navigationVisible && activeSteps.length" class="recipe-overview">
          <article class="recipe-summary">
            <img v-if="activeRecipeImage" :src="activeRecipeImage" :alt="activeRecipe.dish_name" />
            <div v-else class="recipe-image-empty"><el-icon><Food /></el-icon></div>
            <div>
              <span class="hero-kicker">COOKX COOKING</span>
              <h1>{{ activeRecipe.dish_name || '当前菜谱' }}</h1>
              <p>{{ cookingStatusText }}<span v-if="recipeTotalSeconds"> · 总时长 {{ formatDuration(recipeTotalSeconds) }}</span></p>
            </div>
          </article>
          <article class="overall-progress-card">
            <div><span>整体进度</span><strong>{{ currentStepIdx + 1 }} / {{ activeSteps.length }} 步</strong></div>
            <el-progress :percentage="overallProgress" :show-text="false" :stroke-width="9" color="#D86B35" />
            <p><span>预计完成</span><b>{{ estimatedFinishTime }}</b></p>
          </article>
        </div>
        <div v-else class="hero-intro">
          <span class="hero-kicker">COOKX INTELLIGENCE</span>
          <h1>CookX AI 厨房</h1>
          <p>从菜谱推荐到语音步骤指导，让每一步都更从容。</p>
        </div>

        <div v-if="activeReminders.length" class="active-tasks">
          <span v-for="reminder in activeReminders" :key="reminder.id"><el-icon><Timer /></el-icon>{{ reminder.dishName }} 计时中</span>
        </div>
      </div>
    </header>

    <main class="chef-content">
      <template v-if="navigationVisible && activeSteps.length">
        <section class="cooking-grid">
          <article class="current-step-card panel-card">
            <div class="panel-heading">
              <span><el-icon><Food /></el-icon>当前步骤</span>
              <b v-if="currentStepDuration"><el-icon><Timer /></el-icon>剩余 {{ formatTime(timeLeft) }}</b>
            </div>
            <div class="step-count">第 <strong>{{ currentStepIdx + 1 }}</strong> / {{ activeSteps.length }} 步</div>
            <h2>{{ currentStepTitle }}</h2>
            <p class="step-description">{{ currentStepText }}</p>
            <div v-if="currentStepMeta.length" class="step-meta">
              <span v-for="meta in currentStepMeta" :key="meta.label"><b>{{ meta.label }}</b>{{ meta.value }}</span>
            </div>
            <aside v-if="currentStepTip" class="step-tip"><el-icon><Bell /></el-icon><span><b>CookX 提醒</b>{{ currentStepTip }}</span></aside>
            <button v-if="currentStepDuration >= 60" type="button" class="reminder-button" @click="setReminder(currentStep, activeRecipe.dish_name)"><el-icon><AlarmClock /></el-icon>为本步设置提醒</button>
            <div class="step-switcher">
              <button type="button" :disabled="currentStepIdx === 0" @click="prevStep"><el-icon><ArrowLeft /></el-icon>上一步</button>
              <span>第 {{ currentStepIdx + 1 }} / {{ activeSteps.length }} 步</span>
              <button type="button" class="next" @click="nextStep">{{ currentStepIdx === activeSteps.length - 1 ? '完成' : '下一步' }}<el-icon><ArrowRight /></el-icon></button>
            </div>
          </article>

          <article :class="['sense-card', 'panel-card', temperatureLevel.className]">
            <div class="panel-heading">
              <span><el-icon><Connection /></el-icon>CookX Sense 温度监控</span>
              <i :class="{ connected: temperatureConnected }">{{ temperatureConnectionText }}</i>
            </div>
            <div class="temperature-grid">
              <div><span>环境温度</span><strong>--<small>°C</small></strong><p>当前设备未提供</p></div>
              <div><span>锅面温度</span><strong>{{ currentTemperature === null ? '--' : currentTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ currentTemperature === null ? '等待数据' : temperatureLevel.status }}</p></div>
            </div>
            <div v-if="temperatureConnected" class="sense-update"><el-icon><CircleCheckFilled /></el-icon><span><b>设备已连接，数据实时更新</b>最后更新 {{ lastTemperatureTime }}</span></div>
            <div v-else class="sense-empty"><el-icon><Connection /></el-icon><div><b>尚未连接 CookX Sense</b><span>连接设备后可实时查看温度</span></div></div>
            <div v-if="currentTemperature !== null" class="sense-tip"><span>{{ temperatureLevel.status }}</span><p>{{ temperatureLevel.tip }}</p></div>
            <button v-if="!temperatureConnected" type="button" class="sense-action" :disabled="temperatureConnecting" @click="temperatureDialogVisible = true">{{ temperatureConnecting ? '连接中…' : '连接测温设备' }}</button>
            <button v-else type="button" class="sense-action secondary" @click="disconnectTemperature">断开设备</button>
          </article>
        </section>

        <section class="voice-control panel-card">
          <div class="voice-heading"><span><el-icon><Microphone /></el-icon><b>语音助手</b></span><p>{{ isCookingPaused ? '语音已暂停' : 'CookX 正在为你播报当前步骤' }}</p></div>
          <div class="voice-buttons">
            <button type="button" :disabled="currentStepIdx === 0" @click="prevStep"><el-icon><ArrowLeft /></el-icon><span>上一步</span></button>
            <button type="button" @click="replayCurrentStep"><el-icon><RefreshRight /></el-icon><span>重新播报</span></button>
            <button type="button" class="pause-control" @click="toggleCookingPause"><el-icon><VideoPlay v-if="isCookingPaused" /><VideoPause v-else /></el-icon><span>{{ isCookingPaused ? '继续烹饪' : '暂停烹饪' }}</span></button>
            <button type="button" class="next-control" @click="nextStep"><el-icon><ArrowRight /></el-icon><span>{{ currentStepIdx === activeSteps.length - 1 ? '完成' : '下一步' }}</span></button>
          </div>
        </section>

        <section class="steps-panel panel-card">
          <div class="section-heading"><h2>烹饪步骤</h2><span>{{ overallProgress }}%</span></div>
          <div class="step-track">
            <div v-for="(step, index) in activeSteps" :key="index" :class="['track-step', { done: index < currentStepIdx, active: index === currentStepIdx }]">
              <span><el-icon v-if="index < currentStepIdx"><Check /></el-icon><template v-else>{{ index + 1 }}</template></span>
              <b>{{ getStepTitle(step, index) }}</b>
            </div>
          </div>
        </section>

        <section class="support-grid">
          <article v-if="activeRecipe.ingredients_list?.length" class="support-card">
            <span class="support-icon"><el-icon><ShoppingCart /></el-icon></span>
            <div><h3>食材清单</h3><p>{{ ingredientSummary }}</p></div>
          </article>
          <article v-if="recipeReminder" class="support-card">
            <span class="support-icon"><el-icon><Bell /></el-icon></span>
            <div><h3>CookX 提醒</h3><p>{{ recipeReminder }}</p></div>
          </article>
          <article v-if="activeRecipe.nutrition" class="support-card nutrition-card">
            <span class="support-icon"><el-icon><DataAnalysis /></el-icon></span>
            <div><h3>营养信息</h3><p>{{ nutritionSummary }}</p></div>
          </article>
        </section>
      </template>

      <template v-else>
        <section v-if="loading" class="chef-state-card" v-loading="true"><h2>CookX 正在生成菜谱</h2><p>正在结合你的需求整理烹饪步骤…</p></section>
        <section v-else-if="!latestRecipe" class="chef-state-card empty-state">
          <span><el-icon><Food /></el-icon></span><h2>还没有开始烹饪</h2><p>选择一道菜，让 CookX 陪你一步步完成。</p><button type="button" @click="focusRecipeInput">去选择菜谱</button>
        </section>
        <section v-else class="latest-recipe panel-card">
          <div><span class="hero-kicker">READY TO COOK</span><h2>{{ latestRecipe.dish_name || '已生成菜谱' }}</h2><p>菜谱已准备好，可以开启语音步骤指导。</p></div>
          <button type="button" @click="startNavigation(latestRecipe)"><el-icon><Microphone /></el-icon>开始烹饪</button>
        </section>

        <section class="conversation-panel panel-card">
          <div class="section-heading"><h2>AI 菜谱对话</h2><span>{{ messages.length }} 条记录</span></div>
          <div ref="chatBox" class="chat-messages">
            <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.role]">
              <span class="avatar"><el-icon><User v-if="msg.role === 'user'" /><Food v-else /></el-icon></span>
              <div class="message-bubble">
                <div class="text-content">{{ msg.content }}</div>
                <article v-if="msg.recipe?.steps" class="recipe-card">
                  <div class="recipe-header"><div><span>COOKX RECIPE</span><h3>{{ msg.recipe.dish_name || '美味教程' }}</h3></div><button type="button" @click="startNavigation(msg.recipe)"><el-icon><Microphone /></el-icon>开始指导</button></div>
                  <div v-if="msg.recipe.ingredients_list?.length" class="ing-grid"><span v-for="(ing, i) in msg.recipe.ingredients_list" :key="i"><b>{{ ing.item }}</b>{{ ing.amount }}</span></div>
                  <div class="steps-preview"><p v-for="(step, sIdx) in msg.recipe.steps.slice(0, 3)" :key="sIdx"><i>{{ sIdx + 1 }}</i>{{ getStepText(step) }}</p></div>
                  <div class="card-actions"><button type="button" @click="goToMarket"><el-icon><Location /></el-icon>买食材</button><button type="button" @click="orderDelivery(msg.recipe.dish_name)"><el-icon><Bicycle /></el-icon>点外卖</button></div>
                </article>
              </div>
            </div>
          </div>
        </section>
      </template>
    </main>

    <div v-if="!navigationVisible" class="chat-input-bar-fixed">
      <div class="input-content"><el-input ref="recipeInput" v-model="userInput" placeholder="告诉 CookX 你想做什么…" @keyup.enter="sendMessage()"><template #append><el-button :loading="loading" :icon="Promotion" @click="sendMessage()" /></template></el-input></div>
    </div>

    <el-dialog v-model="temperatureDialogVisible" title="连接 JDY-31 测温设备" width="90%">
      <el-input
        v-model="temperatureDeviceAddress"
        placeholder="请输入已配对设备 MAC，例如 00:11:22:33:44:55"
        maxlength="17"
        clearable
        @keyup.enter="connectTemperature"
      />
      <p class="temperature-dialog-help">请先在 Android 系统蓝牙设置中完成 JDY-31 配对。</p>
      <template #footer>
        <el-button @click="temperatureDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="temperatureConnecting" @click="connectTemperature">连接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import axios from 'axios'
import {
  AlarmClock, ArrowLeft, ArrowRight, Bell, Bicycle, Check, CircleCheckFilled,
  Connection, DataAnalysis, Food, Location, Microphone, Promotion, RefreshRight,
  ShoppingCart, Timer, User, VideoPause, VideoPlay
} from '@element-plus/icons-vue'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import {
  connectTemperatureDevice,
  disconnectTemperatureDevice,
  onTemperatureData,
  handleTemperatureUpdate
} from '@/services/temperatureDevice'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'

// --- 基础定义 ---
const props = defineProps(['pendingDish'])
const emit = defineEmits(['clear-pending'])
const userInput = ref('')
const loading = ref(false)
const chatBox = ref(null)
const recipeInput = ref(null)
const messages = ref([])
const userStr = localStorage.getItem('user');
const userId = JSON.parse(userStr || '{}').id;
// --- 导航与提醒状态 ---
const navigationVisible = ref(false)
const activeRecipe = ref({ steps: [] })
const currentStepIdx = ref(0)
const timeLeft = ref(0)
const isListening = ref(false)
const isCookingPaused = ref(false)
const activeReminders = ref([])
let timer = null
let recognition = null
let currentAudio = null // 当前正在播放的音频对象
let currentAudioBlobUrl = null
let voiceRequestVersion = 0
let preloadedVoice = null
let preloadGeneration = 0
let temperatureListener = null

const releaseAudioResource = (audio, blobUrl) => {
  if (audio) {
    audio.onended = null
    audio.onerror = null
    try { audio.pause() } catch (e) {}
    try { audio.currentTime = 0 } catch (e) {}
    audio.removeAttribute('src')
    try { audio.load() } catch (e) {}
  }

  if (blobUrl) window.URL.revokeObjectURL(blobUrl)
}

const stopCurrentAudio = () => {
  if (!currentAudio && !currentAudioBlobUrl) return

  console.log('[Voice] stop current audio')
  const audio = currentAudio
  const blobUrl = currentAudioBlobUrl
  currentAudio = null
  currentAudioBlobUrl = null
  releaseAudioResource(audio, blobUrl)
}

const discardStaleVoiceRequest = (requestVersion) => {
  if (requestVersion === voiceRequestVersion) return false
  console.log(`[Voice] discard stale version=${requestVersion}`)
  return true
}

const discardStepAudio = (audio, blobUrl) => {
  if (currentAudio === audio) {
    currentAudio = null
    currentAudioBlobUrl = null
  }
  releaseAudioResource(audio, blobUrl)
}

const clearPreloadedVoice = () => {
  preloadGeneration++
  if (preloadedVoice?.blobUrl) window.URL.revokeObjectURL(preloadedVoice.blobUrl)
  preloadedVoice = null
}

const preloadNextStep = async (currentStepIndex) => {
  clearPreloadedVoice()

  const nextStepIndex = currentStepIndex + 1
  const nextStep = activeRecipe.value.steps[nextStepIndex]
  if (!nextStep || !navigationVisible.value) return

  const generation = preloadGeneration
  const stepNumber = nextStepIndex + 1
  const text = `第${stepNumber}步：${getStepText(nextStep)}`
  console.log(`[Voice] preload step=${stepNumber}`)

  try {
    const res = await axios.get(`${API_BASE_URL}/tts`, { params: { text } })
    if (generation !== preloadGeneration || !navigationVisible.value) return
    if (!res.data?.audio_url) return

    const audioUrl = resolveBackendUrl(res.data.audio_url)
    const audioRes = await axios.get(audioUrl, { responseType: 'blob' })
    if (generation !== preloadGeneration || !navigationVisible.value) return

    const blobUrl = window.URL.createObjectURL(audioRes.data)
    const currentTarget = activeRecipe.value.steps[nextStepIndex]
    if (
      generation !== preloadGeneration ||
      !currentTarget ||
      `第${stepNumber}步：${getStepText(currentTarget)}` !== text
    ) {
      window.URL.revokeObjectURL(blobUrl)
      return
    }

    preloadedVoice = { stepIndex: nextStepIndex, text, blobUrl }
    console.log(`[Voice] preload ready step=${stepNumber}`)
  } catch (e) {
    if (generation === preloadGeneration) console.warn('[Voice] preload failed', e)
  }
}

const takePreloadedVoice = (stepIndex, text) => {
  if (preloadedVoice?.stepIndex !== stepIndex || preloadedVoice?.text !== text) {
    clearPreloadedVoice()
    return null
  }

  const cached = preloadedVoice
  preloadedVoice = null
  preloadGeneration++
  console.log(`[Voice] use preload step=${stepIndex + 1}`)
  return cached
}

const currentTemperature = ref(null)
const lastTemperatureTimestamp = ref(null)
const temperatureConnected = ref(false)
const temperatureConnecting = ref(false)
const temperatureAcceptingData = ref(false)
const temperatureDialogVisible = ref(false)
const temperatureDeviceAddress = ref(localStorage.getItem('temperatureDeviceAddress') || '')

const temperatureConnectionText = computed(() => {
  if (temperatureConnecting.value) return '连接中'
  return temperatureConnected.value ? '已连接' : '未连接'
})

const lastTemperatureTime = computed(() => {
  if (!lastTemperatureTimestamp.value) return '--'
  return new Date(lastTemperatureTimestamp.value).toLocaleTimeString('zh-CN', { hour12: false })
})

const temperatureLevel = computed(() => {
  const temp = currentTemperature.value
  if (temp === null) {
    return {
      status: '等待数据',
      tip: '连接测温设备后，这里会显示实时油温。',
      tagType: 'info',
      className: 'temperature-idle',
      icon: '🌡️'
    }
  }
  if (temp < 140) {
    return { status: '油温偏低', tip: '油温较低，请继续加热。', tagType: 'info', className: 'temperature-low', icon: '🌡️' }
  }
  if (temp < 170) {
    return { status: '正在升温', tip: '正在接近合适的下锅温度。', tagType: 'warning', className: 'temperature-rising', icon: '♨️' }
  }
  if (temp <= 185) {
    return { status: '适合下锅', tip: '当前油温合适，可以准备下入食材。', tagType: 'success', className: 'temperature-ready', icon: '✅' }
  }
  if (temp <= 205) {
    return { status: '油温偏高', tip: '油温偏高，建议调小火。', tagType: 'warning', className: 'temperature-high', icon: '⚠️' }
  }
  return { status: '危险', tip: '油温过高，请暂缓下锅并降低火力。', tagType: 'danger', className: 'temperature-danger', icon: '🔥' }
})

const connectTemperature = async () => {
  if (temperatureConnecting.value) return
  temperatureConnecting.value = true
  try {
    const result = await connectTemperatureDevice(temperatureDeviceAddress.value)
    if (result.status === 'unsupported') {
      ElMessage.warning('当前环境不支持 Bluetooth Classic 测温，请使用 Android App。')
      return
    }

    temperatureConnected.value = true
    temperatureAcceptingData.value = true
    temperatureDialogVisible.value = false
    localStorage.setItem('temperatureDeviceAddress', temperatureDeviceAddress.value.trim().toUpperCase())
    ElMessage.success(`测温设备已连接${result.mode === 'insecure' ? '（兼容模式）' : ''}`)
  } catch (error) {
    temperatureConnected.value = false
    temperatureAcceptingData.value = false
    ElMessage.error(error?.message || '测温设备连接失败，请检查配对状态和 MAC 地址')
  } finally {
    temperatureConnecting.value = false
  }
}

const disconnectTemperature = async () => {
  temperatureAcceptingData.value = false
  try {
    const result = await disconnectTemperatureDevice()
    if (result.status === 'unsupported') {
      ElMessage.warning('当前环境不支持 Bluetooth Classic 测温，请使用 Android App。')
      return
    }
    ElMessage.success('测温设备已断开')
  } catch (error) {
    ElMessage.error(error?.message || '断开测温设备失败')
  } finally {
    temperatureConnected.value = false
  }
}

const registerTemperatureListener = async () => {
  try {
    temperatureListener = await onTemperatureData((data) => {
      if (!temperatureAcceptingData.value) return
      const update = handleTemperatureUpdate(data.temperature, data.timestamp)
      if (!update) return
      currentTemperature.value = update.temperature
      lastTemperatureTimestamp.value = update.timestamp
    })
  } catch (error) {
    console.error('注册温度监听失败:', error)
    ElMessage.error('无法监听测温设备数据')
  }
}

const cleanupTemperatureDevice = async () => {
  temperatureAcceptingData.value = false
  if (temperatureListener) {
    try {
      await temperatureListener.remove()
    } catch (error) {
      console.warn('移除温度监听失败:', error)
    }
    temperatureListener = null
  }
  try {
    await disconnectTemperatureDevice()
  } catch (error) {
    console.warn('清理测温设备连接失败:', error)
  }
  temperatureConnected.value = false
}

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

const getStepText = (step) => typeof step === 'object' ? (step?.text || step?.content || '') : String(step || '')
const getStepTitle = (step, index) => typeof step === 'object' && step?.title ? step.title : `步骤 ${index + 1}`
const activeSteps = computed(() => Array.isArray(activeRecipe.value?.steps) ? activeRecipe.value.steps : [])
const currentStep = computed(() => activeSteps.value[currentStepIdx.value] || null)
const currentStepText = computed(() => getStepText(currentStep.value))
const currentStepTitle = computed(() => getStepTitle(currentStep.value, currentStepIdx.value))
const currentStepDuration = computed(() => Number(currentStep.value?.time_estimate || currentStep.value?.duration || 0))
const currentStepTip = computed(() => currentStep.value?.tip || currentStep.value?.note || '')
const currentStepMeta = computed(() => {
  if (!currentStep.value || typeof currentStep.value !== 'object') return []
  return [
    currentStep.value.heat ? { label: '火力', value: currentStep.value.heat } : null,
    currentStep.value.temperature ? { label: '目标温度', value: currentStep.value.temperature } : null
  ].filter(Boolean)
})
const recipeTotalSeconds = computed(() => activeSteps.value.reduce((total, step) => {
  const duration = Number(step?.time_estimate || step?.duration || 0)
  return total + (Number.isFinite(duration) && duration > 0 ? duration : 0)
}, 0))
const overallProgress = computed(() => {
  if (!activeSteps.value.length) return 0
  return Math.min(100, Math.round(((currentStepIdx.value + stepPercentage.value / 100) / activeSteps.value.length) * 100))
})
const remainingRecipeSeconds = computed(() => {
  const future = activeSteps.value.slice(currentStepIdx.value + 1).reduce((total, step) => {
    const duration = Number(step?.time_estimate || step?.duration || 0)
    return total + (Number.isFinite(duration) && duration > 0 ? duration : 0)
  }, 0)
  return Math.max(0, timeLeft.value) + future
})
const estimatedFinishTime = computed(() => {
  if (!remainingRecipeSeconds.value) return '--:--'
  return new Date(Date.now() + remainingRecipeSeconds.value * 1000).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })
})
const cookingStatusText = computed(() => isCookingPaused.value ? '已暂停' : '烹饪中')
const activeRecipeImage = computed(() => {
  const image = activeRecipe.value?.image_url || activeRecipe.value?.image || activeRecipe.value?.thumbnail
  return image ? resolveBackendUrl(image) : ''
})
const latestRecipe = computed(() => [...messages.value].reverse().find(message => message?.recipe?.steps)?.recipe || null)
const ingredientSummary = computed(() => (activeRecipe.value?.ingredients_list || [])
  .map(item => [item.item, item.amount].filter(Boolean).join(' ')).join('、'))
const recipeReminder = computed(() => activeRecipe.value?.tip || activeRecipe.value?.note || activeRecipe.value?.cooking_tip || '')
const nutritionSummary = computed(() => {
  const nutrition = activeRecipe.value?.nutrition
  if (!nutrition) return ''
  return [
    nutrition.calories ? `能量 ${nutrition.calories}` : '',
    nutrition.protein ? `蛋白 ${nutrition.protein}` : '',
    nutrition.fat ? `脂肪 ${nutrition.fat}` : '',
    nutrition.carbs ? `碳水 ${nutrition.carbs}` : ''
  ].filter(Boolean).join(' · ')
})

const formatDuration = (seconds) => {
  const minutes = Math.max(1, Math.round(seconds / 60))
  return `${minutes} 分钟`
}

const focusRecipeInput = async () => {
  await nextTick()
  recipeInput.value?.focus?.()
}

const fetchHistory = async () => {
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  if (!user.id) return;

  try {
    const res = await axios.get(`${API_BASE_URL}/chat-history`, {
      params: { user_id: user.id }
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

  await fetchHistory();
});

onMounted(registerTemperatureListener)

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
    const res = await axios.get(`${API_BASE_URL}/recommend-recipe`, {
      params: {
        user_prompt: text,
        user_id: user.id,
        save_history: true
      }
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
  clearPreloadedVoice()
  activeRecipe.value = recipe
  currentStepIdx.value = 0
  isCookingPaused.value = false
  navigationVisible.value = true
  initVoiceRecognition()
  runStep()
}

const startStepTimer = () => {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (!isCookingPaused.value && timeLeft.value > 0) timeLeft.value--
  }, 1000)
}

// 修改 runStep 函数
const runStep = async () => {
  const requestVersion = ++voiceRequestVersion
  stopCurrentAudio()
  isCookingPaused.value = false

  // 1. 物理级清理计时器
  if (timer) { clearInterval(timer); timer = null; }

  const step = activeRecipe.value.steps[currentStepIdx.value];
  if (!step) return;
  const stepIndex = currentStepIdx.value;
  const stepNumber = stepIndex + 1;
  console.log(`[Voice] runStep version=${requestVersion} step=${stepNumber}`);

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
  let blobUrl = null;
  let audio = null;
  try {
    const text = `第${stepNumber}步：${getStepText(step)}`;
    const cachedVoice = takePreloadedVoice(stepIndex, text);

    if (cachedVoice) {
      blobUrl = cachedVoice.blobUrl;
    } else {
      // 1. 获取音频文件路径
      const res = await axios.get(`${API_BASE_URL}/tts`, {
        params: { text }
      });

      if (discardStaleVoiceRequest(requestVersion)) return;

      if (!res.data?.audio_url) return;
      const audioUrl = resolveBackendUrl(res.data.audio_url);

      // ✅ 核心修复：不直接用 new Audio(url)
      // 使用 axios 以 blob 形式下载音频，强制带上跳过头
      const audioRes = await axios.get(audioUrl, { responseType: 'blob' });

      if (discardStaleVoiceRequest(requestVersion)) return;

      // 2. 将下载的 Blob 转换为本地临时 URL
      blobUrl = window.URL.createObjectURL(audioRes.data);
    }

    if (discardStaleVoiceRequest(requestVersion)) {
      releaseAudioResource(null, blobUrl);
      return;
    }

    console.log(`[Voice] create audio version=${requestVersion} step=${stepNumber}`);
    audio = new Audio(blobUrl);
    if (discardStaleVoiceRequest(requestVersion)) {
      discardStepAudio(audio, blobUrl);
      return;
    }

    currentAudioBlobUrl = blobUrl;
    currentAudio = audio;

    audio.onended = () => {
      if (currentAudio === audio) {
        stopCurrentAudio();
        if (requestVersion === voiceRequestVersion) initVoiceRecognition();
      }
    };

    if (discardStaleVoiceRequest(requestVersion)) {
      discardStepAudio(audio, blobUrl);
      return;
    }

    console.log(`[Voice] play version=${requestVersion} step=${stepNumber}`);
    await audio.play();

    if (discardStaleVoiceRequest(requestVersion)) {
      discardStepAudio(audio, blobUrl);
      return;
    }

    // 3. 启动计时器
    startStepTimer();

    void preloadNextStep(stepIndex);
  } catch (e) {
    if (discardStaleVoiceRequest(requestVersion)) {
      if (audio || blobUrl) discardStepAudio(audio, blobUrl);
      return;
    }
    stopCurrentAudio();
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
  voiceRequestVersion++
  stopCurrentAudio()
  isCookingPaused.value = false
  if (timer) { clearInterval(timer); timer = null; }

  if (currentStepIdx.value < activeRecipe.value.steps.length - 1) {
    currentStepIdx.value++
    runStep()
  } else {
    try {
      const used = activeRecipe.value.used_ingredients || []
      if (used.length > 0) {
        if (!userId) {
          ElMessage.error('登录状态已失效，请重新登录')
          navigationVisible.value = false
          return
        }
        await axios.post(`${API_BASE_URL}/consume-ingredients`, used, {
          params: { user_id: userId }
        })
      }
      ElMessage.success("烹饪完成，库存已更新！")
    } catch (e) { console.error(e) }
    stopNavigation()
    navigationVisible.value = false
  }
}

const prevStep = () => {
  if (currentStepIdx.value > 0) {
    voiceRequestVersion++
    stopCurrentAudio()
    isCookingPaused.value = false
    if (timer) { clearInterval(timer); timer = null; }
    currentStepIdx.value--
    runStep()
  }
}

const replayCurrentStep = () => {
  isCookingPaused.value = false
  runStep()
}

const toggleCookingPause = async () => {
  if (!navigationVisible.value) return
  if (!isCookingPaused.value) {
    isCookingPaused.value = true
    if (timer) { clearInterval(timer); timer = null }
    if (currentAudio) {
      try { currentAudio.pause() } catch (error) { console.warn('暂停语音失败:', error) }
    } else {
      voiceRequestVersion++
    }
    return
  }

  isCookingPaused.value = false
  if (currentAudio) {
    try {
      await currentAudio.play()
      startStepTimer()
    } catch (error) {
      console.warn('继续语音失败，重新播报当前步骤:', error)
      runStep()
    }
  } else {
    runStep()
  }
}

// --- 功能性跳转 ---
const goToMarket = () => {
  const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
  const openAmapWeb = (longitude, latitude) => {
    const center = longitude != null && latitude != null
      ? `&center=${longitude},${latitude}`
      : '';
    window.location.href = `https://uri.amap.com/search?keyword=${encodeURIComponent('菜市场')}${center}&view=map&src=smart_cooking&coordinate=gaode`;
  };

  navigator.geolocation.getCurrentPosition((pos) => {
    const { longitude, latitude } = pos.coords;
    if (!isMobile) {
      openAmapWeb(longitude, latitude);
      return;
    }

    const scheme = /iPhone|iPad|iPod/i.test(navigator.userAgent) ? 'iosamap' : 'androidamap';
    const amapScheme = `${scheme}://arroundpoi?sourceApplication=smart_cooking&keywords=${encodeURIComponent('菜市场')}&lat=${latitude}&lon=${longitude}&dev=0`;
    const fallbackTimer = window.setTimeout(() => {
      if (!document.hidden) openAmapWeb(longitude, latitude);
    }, 1500);
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) window.clearTimeout(fallbackTimer);
    }, { once: true });
    window.location.href = amapScheme;
  }, () => {
    openAmapWeb();
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
    const encodedDishName = encodeURIComponent(dishName);
    const h5Url = 'https://h5.waimai.meituan.com/waimai/mindex/home';
    const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
    const openMeituanH5 = () => {
      navigator.clipboard?.writeText(dishName).catch(() => {});
      ElMessage.info(`请在美团外卖中搜索“${dishName}”`);
      window.location.href = h5Url;
    };

    if (!isMobile) {
      openMeituanH5();
      return;
    }

    const fallbackTimer = window.setTimeout(() => {
      if (!document.hidden) openMeituanH5();
    }, 1500);
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) window.clearTimeout(fallbackTimer);
    }, { once: true });
    window.location.href = `imeituan://www.meituan.com/s/${encodedDishName}`;
  }).catch(() => {});
};

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
  voiceRequestVersion++
  stopCurrentAudio()
  clearPreloadedVoice()
  isCookingPaused.value = false

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
    id: Date.now(), dishName, stepText: getStepText(step),
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
onUnmounted(cleanupTemperatureDevice)
</script>

<style scoped>
.ai-chef-container {
  height: calc(100vh - 70px);
  display: flex;
  flex-direction: column;
  background: #f1f4f3;
  overflow: hidden;
}

.temperature-card {
  margin: 10px 10px 0;
  padding: 14px;
  border: 1px solid #dfe9e3;
  border-left: 5px solid #8aa89a;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 3px 12px rgba(32, 82, 57, 0.08);
  transition: border-color 0.25s, background 0.25s, box-shadow 0.25s;
}

.temperature-header,
.temperature-meta,
.temperature-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.temperature-eyebrow {
  color: #64746c;
  font-size: 12px;
  font-weight: 600;
}

.temperature-reading {
  margin-top: 2px;
  color: #263b31;
  font-size: 30px;
  font-weight: 700;
  line-height: 1;
}

.temperature-reading small {
  margin-left: 2px;
  font-size: 14px;
  font-weight: 600;
}

.temperature-meta {
  margin-top: 12px;
  color: #7a8881;
  font-size: 11px;
}

.temperature-tip {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 10px;
  border-radius: 9px;
  background: #f5f8f6;
  color: #526159;
  font-size: 12px;
  line-height: 1.4;
}

.temperature-alert-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.temperature-actions {
  justify-content: flex-end;
  margin-top: 10px;
}

.temperature-ready { border-left-color: #43a047; }
.temperature-rising { border-left-color: #e6a23c; }
.temperature-high {
  border-color: #efb35b;
  border-left-color: #e67e22;
  background: #fff9ef;
  box-shadow: 0 3px 14px rgba(230, 126, 34, 0.18);
}
.temperature-danger {
  border-color: #e76b65;
  border-left-color: #d9363e;
  background: #fff1f0;
  box-shadow: 0 3px 16px rgba(217, 54, 62, 0.24);
}
.temperature-danger .temperature-reading { color: #c62828; }
.temperature-high .temperature-tip { background: #fff0d9; color: #9a5a00; }
.temperature-danger .temperature-tip { background: #ffe0dd; color: #b42318; font-weight: 600; }

.temperature-dialog-help {
  margin: 10px 2px 0;
  color: #849087;
  font-size: 12px;
  line-height: 1.5;
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

/* CookX AI 厨房仪表盘 */
.ai-chef-container {
  min-height: calc(100vh - 70px);
  height: auto;
  display: block;
  overflow: visible;
  background: var(--cookx-bg);
  color: var(--cookx-text);
}
.chef-hero {
  color: #fff;
  background: radial-gradient(circle at 88% 0, rgba(77,139,105,.2), transparent 30%), linear-gradient(145deg, #092a22, var(--cookx-primary-dark));
}
.chef-hero-inner, .chef-content { width: min(var(--cookx-page-max), 100%); margin: 0 auto; }
.chef-hero-inner { padding: 22px var(--cookx-page-gutter) 30px; }
.chef-brand-row, .chef-brand, .hero-statuses, .recipe-overview, .recipe-summary, .overall-progress-card > div,
.overall-progress-card p, .panel-heading, .step-switcher, .voice-heading, .voice-heading > span, .section-heading,
.latest-recipe, .recipe-header, .card-actions, .support-card, .sense-update, .sense-empty { display: flex; align-items: center; }
.chef-brand-row { justify-content: space-between; gap: 18px; }
.chef-brand { gap: 13px; white-space: nowrap; }
.chef-brand > span { font-size: 29px; font-weight: 800; letter-spacing: -.8px; }
.chef-brand > span strong { color: var(--cookx-accent); }
.chef-brand > i { width: 1px; height: 26px; background: rgba(255,255,255,.22); }
.chef-brand > b { font-size: 18px; }
.hero-statuses { justify-content: flex-end; gap: 9px; }
.device-pill, .voice-pill { display: inline-flex; align-items: center; gap: 7px; min-height: 38px; padding: 0 13px; border: 1px solid rgba(255,255,255,.17); border-radius: 999px; background: rgba(255,255,255,.06); color: rgba(255,255,255,.8); font-size: 12px; }
.device-pill.connected { border-color: rgba(85,207,133,.35); color: #bceacb; }
.recipe-overview { align-items: stretch; gap: 18px; margin-top: 25px; }
.recipe-summary, .overall-progress-card { border: 1px solid rgba(255,255,255,.14); border-radius: var(--cookx-radius-large); background: rgba(255,255,255,.055); }
.recipe-summary { flex: 1.35; gap: 18px; padding: 14px; }
.recipe-summary img, .recipe-image-empty { flex: 0 0 145px; width: 145px; height: 112px; border-radius: 18px; object-fit: cover; }
.recipe-image-empty { display: grid; background: rgba(255,255,255,.08); color: var(--cookx-gold); font-size: 38px; place-items: center; }
.hero-kicker { color: var(--cookx-gold); font-size: 10px; font-weight: 800; letter-spacing: 1.4px; }
.recipe-summary h1, .hero-intro h1 { margin: 7px 0 8px; font-size: clamp(25px, 4vw, 34px); }
.recipe-summary p, .hero-intro p { margin: 0; color: rgba(255,255,255,.67); font-size: 13px; }
.overall-progress-card { flex: .9; padding: 20px; }
.overall-progress-card > div, .overall-progress-card p { justify-content: space-between; }
.overall-progress-card > div { margin-bottom: 18px; font-size: 13px; }
.overall-progress-card > div strong { font-size: 17px; }
.overall-progress-card p { margin: 17px 0 0; color: rgba(255,255,255,.62); font-size: 12px; }
.overall-progress-card p b { color: #fff; font-size: 14px; }
.hero-intro { padding: 42px 0 22px; }
.hero-intro p { max-width: 520px; line-height: 1.7; }
.active-tasks { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 14px; }
.active-tasks span { display: inline-flex; align-items: center; gap: 6px; padding: 7px 11px; border-radius: 10px; background: rgba(233,162,59,.14); color: #f3c77e; font-size: 11px; }
.chef-content { box-sizing: border-box; padding: 22px var(--cookx-page-gutter) calc(118px + env(safe-area-inset-bottom)); }
.panel-card, .chef-state-card { border: var(--cookx-border); border-radius: var(--cookx-radius-large); background: var(--cookx-surface); box-shadow: var(--cookx-shadow); }
.cooking-grid { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(320px, .75fr); gap: 18px; }
.current-step-card, .sense-card { padding: 23px; }
.panel-heading { justify-content: space-between; gap: 12px; margin-bottom: 22px; }
.panel-heading > span { display: inline-flex; align-items: center; gap: 9px; font-size: 17px; font-weight: 750; }
.panel-heading > span .el-icon { display: grid; width: 31px; height: 31px; border-radius: 10px; background: #edf3ef; color: var(--cookx-primary); place-items: center; }
.panel-heading > b { display: inline-flex; align-items: center; gap: 5px; padding: 8px 11px; border-radius: 999px; background: #fff2e9; color: var(--cookx-accent); font-size: 12px; }
.panel-heading > i { position: relative; padding-left: 13px; color: var(--cookx-text-secondary); font-size: 11px; font-style: normal; }
.panel-heading > i::before { position: absolute; top: 50%; left: 0; width: 7px; height: 7px; border-radius: 50%; background: #c8ceca; content: ''; transform: translateY(-50%); }
.panel-heading > i.connected::before { background: #4caf6a; box-shadow: 0 0 0 4px rgba(76,175,106,.1); }
.step-count { color: var(--cookx-text-secondary); font-size: 15px; }
.step-count strong { color: var(--cookx-accent); font-size: 23px; }
.current-step-card h2 { margin: 12px 0 13px; color: var(--cookx-primary-dark); font-size: clamp(25px, 4vw, 36px); line-height: 1.2; }
.step-description { min-height: 70px; margin: 0; color: #4f5a54; font-size: 15px; line-height: 1.85; white-space: pre-line; }
.step-meta { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 18px; }
.step-meta span { padding: 9px 12px; border-radius: 11px; background: #f7f5ef; color: var(--cookx-text-secondary); font-size: 12px; }
.step-meta b { margin-right: 6px; color: var(--cookx-primary); }
.step-tip { display: flex; align-items: flex-start; gap: 10px; margin-top: 18px; padding: 14px; border-radius: 15px; background: #eef5ef; color: #52635a; font-size: 12px; line-height: 1.6; }
.step-tip .el-icon { margin-top: 3px; color: var(--cookx-primary); }
.step-tip b { display: block; color: var(--cookx-primary-dark); }
.reminder-button { display: inline-flex; align-items: center; gap: 6px; min-height: 40px; margin-top: 14px; padding: 0 14px; border: 1px solid rgba(216,107,53,.22); border-radius: 13px; background: #fff8f2; color: var(--cookx-accent); font-weight: 650; cursor: pointer; }
.step-switcher { justify-content: space-between; gap: 12px; margin: 23px -23px -23px; padding: 17px 23px; border-top: var(--cookx-border); }
.step-switcher > span { color: var(--cookx-text-secondary); font-size: 12px; }
.step-switcher button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-width: 112px; min-height: 44px; border: 1px solid rgba(23,63,53,.2); border-radius: 14px; background: #fff; color: var(--cookx-primary); font-weight: 700; cursor: pointer; }
.step-switcher button.next { border-color: var(--cookx-accent); background: var(--cookx-accent); color: #fff; }
.step-switcher button:disabled, .voice-buttons button:disabled { cursor: not-allowed; opacity: .38; }
.temperature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.temperature-grid > div { padding: 16px; border-radius: 16px; background: #f8f7f3; }
.temperature-grid span { color: var(--cookx-text-secondary); font-size: 11px; }
.temperature-grid strong { display: block; margin: 9px 0 5px; color: var(--cookx-primary-dark); font-size: clamp(27px, 5vw, 38px); line-height: 1; }
.temperature-grid strong small { margin-left: 2px; font-size: 14px; }
.temperature-grid p { margin: 0; color: var(--cookx-success); font-size: 11px; }
.sense-update, .sense-empty { gap: 10px; margin-top: 14px; padding: 13px; border-radius: 14px; background: #eff6f0; }
.sense-update .el-icon { color: var(--cookx-success); font-size: 20px; }
.sense-update span, .sense-empty div { display: flex; flex-direction: column; gap: 2px; color: var(--cookx-text-secondary); font-size: 10px; }
.sense-update b, .sense-empty b { color: var(--cookx-primary-dark); font-size: 12px; }
.sense-empty > .el-icon { color: var(--cookx-text-secondary); font-size: 23px; }
.sense-tip { margin-top: 13px; padding: 12px; border-radius: 13px; background: #fff8ef; }
.sense-tip span { color: var(--cookx-accent); font-size: 12px; font-weight: 750; }
.sense-tip p { margin: 4px 0 0; color: var(--cookx-text-secondary); font-size: 11px; }
.sense-action { width: 100%; min-height: 43px; margin-top: 14px; border: 0; border-radius: 13px; background: var(--cookx-primary); color: #fff; font-weight: 700; cursor: pointer; }
.sense-action.secondary { border: 1px solid rgba(23,63,53,.16); background: #fff; color: var(--cookx-primary); }
.voice-control { margin-top: 18px; padding: 19px 22px; }
.voice-heading { justify-content: space-between; gap: 12px; }
.voice-heading > span { gap: 8px; color: var(--cookx-primary-dark); }
.voice-heading > span .el-icon { color: var(--cookx-primary); font-size: 20px; }
.voice-heading p { margin: 0; color: var(--cookx-text-secondary); font-size: 11px; }
.voice-buttons { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 17px; }
.voice-buttons button { display: flex; align-items: center; justify-content: center; gap: 8px; min-height: 52px; border: 1px solid rgba(23,63,53,.12); border-radius: 16px; background: #f7f9f6; color: var(--cookx-primary); font-weight: 700; cursor: pointer; }
.voice-buttons .pause-control { border-color: transparent; background: var(--cookx-accent); color: #fff; box-shadow: 0 8px 22px rgba(216,107,53,.2); }
.voice-buttons .next-control { border-color: transparent; background: var(--cookx-primary); color: #fff; }
.steps-panel { margin-top: 18px; padding: 21px 22px; overflow: hidden; }
.section-heading { justify-content: space-between; margin-bottom: 18px; }
.section-heading h2 { margin: 0; font-size: 17px; }
.section-heading span { color: var(--cookx-text-secondary); font-size: 11px; }
.step-track { display: grid; grid-template-columns: repeat(auto-fit, minmax(80px, 1fr)); gap: 0; overflow-x: auto; padding-bottom: 4px; }
.track-step { position: relative; min-width: 80px; text-align: center; }
.track-step::before { position: absolute; z-index: 0; top: 17px; right: 50%; left: -50%; height: 2px; background: #e3e5e2; content: ''; }
.track-step:first-child::before { display: none; }
.track-step > span { position: relative; z-index: 1; display: grid; width: 34px; height: 34px; margin: 0 auto 9px; border: 1px solid #d9ddda; border-radius: 50%; background: #fff; color: var(--cookx-text-secondary); font-size: 12px; place-items: center; }
.track-step b { display: block; overflow: hidden; color: var(--cookx-text-secondary); font-size: 10px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.track-step.done::before, .track-step.active::before { background: var(--cookx-primary); }
.track-step.done > span { border-color: var(--cookx-primary); background: var(--cookx-primary); color: #fff; }
.track-step.active > span { border-color: var(--cookx-accent); background: var(--cookx-accent); color: #fff; box-shadow: 0 0 0 5px rgba(216,107,53,.1); }
.track-step.active b { color: var(--cookx-accent); }
.support-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
.support-card { gap: 13px; min-width: 0; padding: 17px; border: var(--cookx-border); border-radius: var(--cookx-radius-card); background: var(--cookx-surface); box-shadow: var(--cookx-shadow); }
.support-icon { display: grid; flex: 0 0 40px; width: 40px; height: 40px; border-radius: 13px; background: #edf4ef; color: var(--cookx-primary); font-size: 19px; place-items: center; }
.support-card h3 { margin: 0 0 5px; font-size: 14px; }
.support-card p { display: -webkit-box; overflow: hidden; margin: 0; color: var(--cookx-text-secondary); font-size: 11px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.chef-state-card { min-height: 230px; padding: 35px 20px; text-align: center; }
.chef-state-card h2 { margin: 15px 0 7px; font-size: 21px; }
.chef-state-card p { margin: 0 auto; color: var(--cookx-text-secondary); font-size: 13px; line-height: 1.7; }
.empty-state > span { display: grid; width: 62px; height: 62px; margin: 0 auto; border-radius: 20px; background: #eaf1ec; color: var(--cookx-primary); font-size: 27px; place-items: center; }
.empty-state button, .latest-recipe button, .recipe-header button { display: inline-flex; align-items: center; justify-content: center; gap: 7px; min-height: 44px; padding: 0 17px; border: 0; border-radius: 14px; background: var(--cookx-primary); color: #fff; font-weight: 700; cursor: pointer; }
.empty-state button { margin-top: 18px; }
.latest-recipe { justify-content: space-between; gap: 20px; margin-bottom: 18px; padding: 22px; }
.latest-recipe h2 { margin: 5px 0; font-size: 22px; }
.latest-recipe p { margin: 0; color: var(--cookx-text-secondary); font-size: 12px; }
.conversation-panel { padding: 20px; }
.conversation-panel .chat-messages { max-height: 620px; padding: 2px 2px 18px; overflow-y: auto; }
.message-wrapper { gap: 9px; width: 100%; max-width: 100%; margin-bottom: 13px; }
.message-wrapper.user { align-self: stretch; }
.avatar { display: grid; flex: 0 0 34px; width: 34px; height: 34px; border-radius: 11px; background: #edf3ef; color: var(--cookx-primary); font-size: 17px; place-items: center; box-shadow: none; }
.message-bubble { max-width: min(82%, 700px); padding: 11px 14px; border: var(--cookx-border); border-radius: 15px; background: #fff; color: var(--cookx-text); font-size: 13px; box-shadow: none; }
.user .message-bubble { border: 0; border-top-right-radius: 4px; background: var(--cookx-primary); color: #fff; }
.assistant .message-bubble { border-top-left-radius: 4px; }
.recipe-card { margin-top: 13px; padding: 15px; border: 0; border-radius: 16px; background: #f7f7f2; }
.recipe-header { justify-content: space-between; gap: 12px; margin: 0 0 13px; padding: 0 0 12px; border-bottom: var(--cookx-border); }
.recipe-header span { color: var(--cookx-accent); font-size: 9px; font-weight: 800; letter-spacing: 1px; }
.recipe-header h3 { margin: 3px 0 0; font-size: 17px; }
.recipe-header button { min-height: 38px; padding: 0 12px; font-size: 11px; }
.ing-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px; padding: 0; background: transparent; }
.ing-grid > span { display: flex; flex-direction: column; min-width: 0; padding: 9px; border-radius: 10px; background: #fff; color: var(--cookx-text-secondary); font-size: 10px; }
.ing-grid > span b { overflow: hidden; color: var(--cookx-text); font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.steps-preview { margin-top: 12px; }
.steps-preview p { display: flex; align-items: flex-start; gap: 8px; margin: 7px 0; color: #4f5954; font-size: 11px; line-height: 1.5; }
.steps-preview i { display: grid; flex: 0 0 20px; width: 20px; height: 20px; border-radius: 50%; background: var(--cookx-primary); color: #fff; font-size: 9px; font-style: normal; place-items: center; }
.card-actions { justify-content: flex-end; gap: 8px; margin-top: 13px; }
.card-actions button { display: inline-flex; align-items: center; gap: 5px; min-height: 36px; padding: 0 11px; border: 1px solid rgba(23,63,53,.12); border-radius: 11px; background: #fff; color: var(--cookx-primary); font-size: 10px; cursor: pointer; }
.chat-input-bar-fixed { bottom: calc(70px + env(safe-area-inset-bottom)); padding: 10px 14px; border-top: var(--cookx-border); background: rgba(247,245,239,.94); backdrop-filter: blur(12px); }
.input-content { width: min(860px, 100%); margin: 0 auto; }
.input-content :deep(.el-input__wrapper) { min-height: 46px; border-radius: 15px 0 0 15px; box-shadow: 0 0 0 1px rgba(23,63,53,.1) inset; }
.input-content :deep(.el-input-group__append) { border-radius: 0 15px 15px 0; background: var(--cookx-primary); color: #fff; box-shadow: none; }

@media (max-width: 767px) {
  .chef-hero-inner { padding: 17px var(--cookx-page-gutter-mobile) 23px; }
  .chef-brand-row { align-items: flex-start; }
  .chef-brand > span { font-size: 25px; }
  .chef-brand > b { font-size: 15px; }
  .hero-statuses { flex-direction: column; align-items: flex-end; gap: 5px; }
  .device-pill, .voice-pill { min-height: 30px; padding: 0 9px; font-size: 9px; }
  .recipe-overview { flex-direction: column; margin-top: 19px; }
  .recipe-summary { padding: 12px; }
  .recipe-summary img, .recipe-image-empty { flex-basis: 92px; width: 92px; height: 88px; border-radius: 14px; }
  .recipe-summary h1 { font-size: 23px; }
  .overall-progress-card { padding: 16px; }
  .hero-intro { padding: 34px 2px 17px; }
  .chef-content { padding: 15px var(--cookx-page-gutter-mobile) calc(122px + env(safe-area-inset-bottom)); }
  .cooking-grid { grid-template-columns: 1fr; gap: 14px; }
  .current-step-card, .sense-card { padding: 18px; }
  .panel-heading { margin-bottom: 17px; }
  .panel-heading > span { font-size: 15px; }
  .current-step-card h2 { font-size: 27px; }
  .step-description { min-height: 0; font-size: 14px; }
  .step-switcher { margin: 20px -18px -18px; padding: 14px 18px; }
  .step-switcher button { min-width: 96px; min-height: 44px; }
  .temperature-grid > div { padding: 14px 11px; }
  .temperature-grid strong { font-size: 29px; }
  .voice-control { padding: 17px 14px; }
  .voice-heading { align-items: flex-start; flex-direction: column; }
  .voice-buttons { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
  .voice-buttons button { min-height: 48px; font-size: 12px; }
  .steps-panel { padding: 18px 14px; }
  .step-track { grid-auto-columns: 84px; grid-auto-flow: column; grid-template-columns: none; padding: 5px 0 8px; }
  .support-grid { grid-template-columns: 1fr; gap: 10px; }
  .latest-recipe { align-items: flex-start; flex-direction: column; }
  .latest-recipe button { width: 100%; }
  .conversation-panel { padding: 15px 12px; }
  .message-bubble { max-width: calc(100% - 43px); }
  .recipe-header { align-items: flex-start; flex-direction: column; }
  .recipe-header button { width: 100%; }
}

@media (max-width: 390px) {
  .chef-brand > i, .voice-pill { display: none; }
  .chef-brand-row { align-items: center; }
  .recipe-summary img, .recipe-image-empty { flex-basis: 78px; width: 78px; height: 78px; }
  .recipe-summary h1 { font-size: 20px; }
  .step-switcher > span { display: none; }
  .step-switcher button { flex: 1; }
  .temperature-grid { gap: 8px; }
  .temperature-grid strong { font-size: 25px; }
  .ing-grid { grid-template-columns: 1fr; }
}
</style>
