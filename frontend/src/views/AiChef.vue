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
            <span class="voice-pill"><el-icon><Microphone /></el-icon>{{ navigationVisible ? voiceStatusText : '语音待命' }}</span>
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


      </div>
    </header>

    <main class="chef-content">
      <section v-if="LOCAL_TEST_MODE" class="chef-state-card"><p>本地演练：输入任意文字会返回预置练习，未调用 AI。无需点火；可测试步骤、计时、调整和库存核对。</p><button @click="sendMessage('本地操作演练')" :disabled="loading">载入演练菜谱</button></section>
      <CookingCompletion v-if="completionVisible && session" :key="session.id" :session="session" :engine="cookingStore.engine" @changed="completionChanged" @close="completionVisible=false" />
      <button v-if="session?.status === 'completed' && !completionVisible" @click="completionVisible=true">查看完成与库存核对</button>
      <details class="device-diagnostics"><summary>设备状态与诊断</summary><p>{{ temperatureConnectionText }}；恢复页面后等待新测量。后台连续采集能力待真机验证。</p><button @click="openTemperatureDialog">扫描与连接设备</button><button @click="refreshDevice">重新核对设备状态</button><button @click="exportDeviceLog">导出设备诊断</button><p role="status">{{ deviceDiagnosticMessage }}</p></details>
      <p v-if="sessionMessage" role="status">{{ sessionMessage }}</p>
      <section v-if="!navigationVisible && session?.status === 'active'" class="chef-state-card"><p>发现未完成的烹饪，计时按实际经过时间核对。</p><button @click="restoreCooking">恢复烹饪</button></section>
      <section v-if="recipeError" class="chef-state-card" role="alert"><p>{{ recipeError }}</p><button type="button" @click="sendMessage(lastRecipePrompt)">重新生成菜谱</button></section>
      <template v-if="navigationVisible && activeSteps.length">
        <section class="cooking-grid">
          <article :class="['current-step-card', 'panel-card', { 'is-voice-active': isVoicePlaying }]">
            <div class="panel-heading">
              <span><el-icon><Food /></el-icon>当前步骤</span>
              <b v-if="currentStepDuration || session?.timers[currentStepIdx]?.round > 0"><el-icon><Timer /></el-icon>剩余 {{ formatTime(timeLeft) }}</b><b v-else>时长未提供</b>
            </div>
            <div class="step-count">第 <strong>{{ currentStepIdx + 1 }}</strong> / {{ activeSteps.length }} 步</div>
            <h2>{{ currentStepTitle }}</h2>
            <p class="step-description">{{ currentStepText }}</p>
            <div :class="['inline-voice', voicePlaybackState]">
              <div class="inline-voice-main">
                <span class="voice-wave" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
                <strong>{{ inlineVoiceStatusText }}</strong>
                <span v-if="voiceProgressVisible" class="voice-remaining">{{ voiceRemainingTime }}</span>
              </div>
              <div v-if="voiceProgressVisible" class="voice-progress" aria-label="本步骤语音播放进度">
                <span :style="{ width: `${voiceProgressPercent}%` }"></span>
              </div>
              <div class="inline-voice-actions">
                <button type="button" class="voice-toggle" @click="toggleVoicePlayback"><el-icon><VideoPause v-if="isVoicePlaying" /><VideoPlay v-else /></el-icon>{{ voiceControlText }}</button>
                <button type="button" @click="replayCurrentStep"><el-icon><RefreshRight /></el-icon>重新播报</button>
              </div>
            </div>
            <div v-if="currentStepMeta.length" class="step-meta">
              <span v-for="meta in currentStepMeta" :key="meta.label"><b>{{ meta.label }}</b>{{ meta.value }}</span>
            </div>
            <aside v-if="currentStepTip" class="step-tip"><el-icon><Bell /></el-icon><span><b>CookX 提醒</b>{{ currentStepTip }}</span></aside>
            <p v-if="voiceMessage" role="status">{{ voiceMessage }}</p>
            <button v-if="!LOCAL_TEST_MODE" type="button" @click="runCloudStep">重试在线播报</button>
            <VoiceCommands @before-listen="silenceSpeech" @command="executeVoiceCommand" />
            <div class="timer-controls">
              <button @click="pauseTimer">暂停计时</button><button @click="resumeTimer">继续计时</button>
              <label>手动计时（秒）<input v-model="timerSeconds" type="number" min="1" max="86400" /></label><button @click="setManualTimer">开始计时</button>
            </div>
            <CookingReminders :key="session.id" :store="cookingStore" :assessment="thermalAssessment" :replaying="thermalReplaying" @changed="refreshSession" />
            <CookingAdjustments :key="session.id" :session="session" :engine="cookingStore.engine" @changed="refreshSession" />
            <div class="step-switcher">
              <button type="button" :disabled="currentStepIdx === 0" @click="prevStep"><el-icon><ArrowLeft /></el-icon>上一步</button>
              <span>第 {{ currentStepIdx + 1 }} / {{ activeSteps.length }} 步</span>
              <button type="button" class="next" @click="nextStep">{{ isLastStep ? '完成烹饪' : '下一步' }}<el-icon><ArrowRight /></el-icon></button>
            </div>
          </article>

          <article :class="['sense-card', 'panel-card', temperatureLevel.className]">
            <div class="panel-heading">
              <span><el-icon><Connection /></el-icon>CookX Sense 温度监控</span>
              <i :class="{ connected: temperatureConnected }">{{ temperatureConnectionText }}</i>
            </div>
            <div class="temperature-grid">
              <div><span>环境温度</span><strong>{{ ambientTemperature === null ? '--' : ambientTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ ambientTemperature === null ? '当前硬件固件未上传环境温度' : '设备环境温度' }}</p></div>
              <div><span>锅面温度</span><strong>{{ currentTemperature === null ? '--' : currentTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ currentTemperature === null ? '等待数据' : temperatureLevel.status }}</p></div>
            </div>
            <div v-if="temperatureConnected" class="sense-update"><el-icon><CircleCheckFilled /></el-icon><span><b>{{ currentTemperature === null ? '数据暂未更新' : '已收到设备读数，请结合测量质量查看' }}</b>最后更新 {{ lastTemperatureTime }}</span></div>
            <div v-else class="sense-empty"><el-icon><Connection /></el-icon><div><b>尚未连接 CookX Sense</b><span>连接设备后可实时查看温度</span></div></div>
            <TemperatureInsight :assessment="thermalAssessment" :history="thermalHistory" :prediction="thermalPrediction"
              :model-state="thermalModelState" :experimental="thermalExperimental" :replaying="thermalReplaying"
              :connected="temperatureConnected" :storage-message="thermalStorageMessage"
              @confirm="thermal.confirm($event)" @experimental="thermal.setExperimental($event)"
              @replay="thermal.startReplay()" @stop-replay="thermal.stopReplay()" @export="thermal.exportSession($event)" />
            <button v-if="!temperatureConnected" type="button" class="sense-action" :disabled="temperatureConnecting" @click="openTemperatureDialog">{{ temperatureConnecting ? '连接中…' : '连接测温设备' }}</button>
            <button v-else type="button" class="sense-action secondary" @click="disconnectTemperature">断开设备</button>
          </article>
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
                  <div v-if="!LOCAL_TEST_MODE" class="card-actions"><button type="button" @click="goToMarket"><el-icon><Location /></el-icon>买食材</button><button type="button" @click="orderDelivery(msg.recipe.dish_name)"><el-icon><Bicycle /></el-icon>点外卖</button></div>
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

    <el-dialog v-model="temperatureDialogVisible" title="连接 CookX Sense" width="90%" @closed="stopTemperatureScan">
      <div class="bluetooth-scan-status">
        <span>{{ bluetoothScanText }}</span>
        <el-button size="small" :loading="temperatureScanning" @click="startTemperatureScan">重新扫描</el-button>
      </div>
      <div class="bluetooth-device-list">
        <button
          v-for="device in sortedTemperatureDevices"
          :key="device.address"
          type="button"
          :class="['bluetooth-device-item', { recommended: device.isJdy31, selected: temperatureDeviceAddress === device.address }]"
          @click="temperatureDeviceAddress = device.address"
        >
          <span><b>{{ device.name || '未知蓝牙设备' }}</b><small>{{ device.address }}</small></span>
          <em>{{ device.isJdy31 ? 'CookX 设备' : 'Classic Bluetooth' }}</em>
        </button>
        <p v-if="!sortedTemperatureDevices.length" class="temperature-dialog-help">{{ temperatureScanning ? '正在扫描附近的 JDY-31…' : '暂未发现设备，请确认模块已上电。' }}</p>
      </div>
      <details class="bluetooth-debug"><summary>高级调试：手工地址</summary><el-input v-model="temperatureDeviceAddress" placeholder="00:11:22:33:44:55" maxlength="17" clearable /></details>
      <template #footer>
        <el-button @click="temperatureDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="temperatureConnecting" :disabled="!temperatureDeviceAddress" @click="connectTemperature">{{ temperatureConnecting ? '正在连接…' : '连接' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {LOCAL_TEST_MODE} from '../config/buildMode.js'
import CookingReminders from '../components/CookingReminders.vue'
import CookingCompletion from '../components/CookingCompletion.vue'
import {reconcileTimer} from '../services/cookingNotifications.js'
import CookingAdjustments from '../components/CookingAdjustments.vue'
import VoiceCommands from '../components/VoiceCommands.vue'
import { speakSystem, stopSystemSpeech, stopListening } from '../services/systemVoice.js'
import { getCookingStore, suspendCookingStores } from '../services/cookingStore.js'
import { normalizeRecipe, safeHistory } from '../services/recipeAdapter.js'
import { readUserId } from '../services/recognitionDraft.js'
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import axios from 'axios'
import {
  AlarmClock, ArrowLeft, ArrowRight, Bell, Bicycle, Check, CircleCheckFilled,
  Connection, DataAnalysis, Food, Location, Microphone, Promotion, RefreshRight,
  ShoppingCart, Timer, User, VideoPause, VideoPlay
} from '@element-plus/icons-vue'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import {
  reconcileTemperatureDevice,
  exportTemperatureDiagnostics,
  connectTemperatureDevice,
  disconnectTemperatureDevice,
  requestBluetoothPermissions,
  getBluetoothState,
  scanTemperatureDevices,
  stopTemperatureDeviceScan,
  onTemperatureDeviceFound,
  onTemperatureConnectionStateChanged,
  onTemperatureData,
  handleTemperatureUpdate,
  resetTemperatureBuffer
} from '@/services/temperatureDevice'
import { TEMPERATURE_STALE_MS } from '@/services/temperatureStream'
import TemperatureInsight from '@/components/TemperatureInsight.vue'
import { useTemperatureIntelligence } from '@/temperature/useTemperatureIntelligence'
import { adaptCookingContext } from '@/temperature/context'
import { API_BASE_URL, resolveBackendUrl } from '@/config/backend'

// --- 基础定义 ---
const props = defineProps(['pendingDish'])
const emit = defineEmits(['clear-pending'])
const userInput = ref('')
const loading = ref(false)
const recipeError = ref('')
const lastRecipePrompt = ref('')
let recipeRequestVersion = 0, recipeController, pageActive = true
const chatBox = ref(null)
const recipeInput = ref(null)
const messages = ref([])
const userId = readUserId();
const thermal = useTemperatureIntelligence('cookx-temperature-session-' + (userId ?? 'guest'))
const { assessment: thermalAssessment, history: thermalHistory, prediction: thermalPrediction, modelState: thermalModelState, experimental: thermalExperimental, replaying: thermalReplaying, storageMessage: thermalStorageMessage } = thermal
// --- 导航与提醒状态 ---
const navigationVisible = ref(false)
const cookingStore=getCookingStore(userId)
const session=cookingStore.state
const tick=ref(Date.now())
const activeRecipe=computed(()=>session.value?.recipe || {steps:[]})
const currentStepIdx=computed(()=>session.value?.stepIndex || 0)
const timeLeft=computed(()=>{tick.value;return Math.ceil(cookingStore.engine.remaining()/1000)})
const timerSeconds=ref(60)
const sessionMessage=ref(cookingStore.engine.warning)
const refreshSession=()=>{cookingStore.refresh();tick.value=Date.now();sessionMessage.value=cookingStore.engine.warning}
const pauseTimer=()=>{cookingStore.engine.pause();refreshSession()}
const resumeTimer=()=>{cookingStore.engine.resume();refreshSession()}
const setManualTimer=()=>{try{cookingStore.engine.setTimer(Number(timerSeconds.value));refreshSession()}catch(error){ElMessage.warning(error.message)}}
const isListening = ref(false)
const isCookingPaused = computed(()=>session.value?.timers[currentStepIdx.value]?.deadline==null)
const voicePlaybackState = ref('idle')
const voiceCurrentTime = ref(0)
const voiceDuration = ref(0)
const completionVisible=ref(false)
const completionChanged=()=>{refreshSession();reconcileTimer(cookingStore);if(session.value?.status==='completed'){stopNavigation();navigationVisible.value=false}}
let timer = null
const voiceMessage=ref('')
let currentAudio = null // 当前正在播放的音频对象
let currentAudioBlobUrl = null
let voiceRequestVersion = 0
let preloadedVoice = null
let preloadGeneration = 0
let temperatureListener = null
let temperatureStaleTimer = null
let temperatureDeviceFoundListener = null
let temperatureConnectionStateListener = null

const releaseAudioResource = (audio, blobUrl) => {
  if (audio) {
    audio.onended = null
    audio.onerror = null
    audio.ontimeupdate = null
    audio.ondurationchange = null
    try { audio.pause() } catch (e) {}
    try { audio.currentTime = 0 } catch (e) {}
    audio.removeAttribute('src')
    try { audio.load() } catch (e) {}
  }

  if (blobUrl) window.URL.revokeObjectURL(blobUrl)
}

const stopCurrentAudio = () => {
  if (currentAudio || currentAudioBlobUrl) console.log('[Voice] stop current audio')
  const audio = currentAudio
  const blobUrl = currentAudioBlobUrl
  currentAudio = null
  currentAudioBlobUrl = null
  voicePlaybackState.value = 'idle'
  voiceCurrentTime.value = 0
  voiceDuration.value = 0
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
    voicePlaybackState.value = 'idle'
    voiceCurrentTime.value = 0
    voiceDuration.value = 0
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
const ambientTemperature = ref(null)
const lastTemperatureTimestamp = ref(null)
const temperatureConnected = ref(false)
const temperatureConnecting = ref(false)
const temperatureState=ref('idle')
const deviceDiagnosticMessage=ref('')
const temperatureAcceptingData = ref(false)
const temperatureDialogVisible = ref(false)
const temperatureDeviceAddress = ref(localStorage.getItem('temperatureDeviceAddress') || '')
const temperatureDevices = ref([])
const temperatureScanning = ref(false)

const sortedTemperatureDevices = computed(() => [...temperatureDevices.value].sort((a, b) => Number(b.isJdy31) - Number(a.isJdy31)))
const bluetoothScanText = computed(() => temperatureScanning.value ? '正在扫描 Classic Bluetooth 设备' : `发现 ${temperatureDevices.value.length} 个设备`)

const clearTemperatureReading = () => {
  thermal.invalidate()
  if (temperatureStaleTimer) {
    window.clearTimeout(temperatureStaleTimer)
    temperatureStaleTimer = null
  }
  currentTemperature.value = null
  ambientTemperature.value = null
}

const scheduleTemperatureStaleTimeout = () => {
  if (temperatureStaleTimer) window.clearTimeout(temperatureStaleTimer)
  temperatureStaleTimer = window.setTimeout(() => {
    temperatureStaleTimer = null
    currentTemperature.value = null
    ambientTemperature.value = null
    thermal.invalidate('温度已超时，不能继续判断')
  }, TEMPERATURE_STALE_MS)
}

const temperatureConnectionText = computed(() => {
 if(temperatureConnecting.value)return '连接中'
 const labels={'bluetooth-off':'蓝牙关闭','permission-denied':'需要附近设备权限',reconnecting:'正在重连',connecting:'连接中',error:'连接异常',unsupported:'需 Android App'}
 return labels[temperatureState.value] || (temperatureConnected.value ? (currentTemperature.value===null?'已连接 · 等待新数据':'已连接'):'未连接')
})

const lastTemperatureTime = computed(() => {
  if (!lastTemperatureTimestamp.value) return '--'
  return new Date(lastTemperatureTimestamp.value).toLocaleTimeString('zh-CN', { hour12: false })
})

const temperatureLevel = computed(() => ({
  status: thermalAssessment.value.phaseLabel,
  className: thermalAssessment.value.risk === 'danger' ? 'temperature-danger' : thermalAssessment.value.risk === 'warning' ? 'temperature-high' : 'temperature-idle'
}))

const bluetoothErrorMessage = (error) => {
  const messages = {
    BLUETOOTH_UNSUPPORTED: '此手机不支持蓝牙',
    BLUETOOTH_DISABLED: '请开启手机蓝牙后继续',
    PERMISSION_DENIED: '请允许 CookX 使用附近设备权限',
    SCAN_FAILED: '蓝牙扫描启动失败，请稍后重试',
    DEVICE_NOT_FOUND: '请选择扫描到的 JDY-31 设备',
    CONNECT_FAILED: '无法连接 JDY-31，请确认模块已上电并靠近手机',
    CONNECTION_LOST: 'CookX Sense 连接已断开',
    NOT_CONNECTED: '设备尚未连接',
    WRITE_FAILED: '蓝牙数据发送失败'
  }
  return messages[error?.code] || error?.message || '蓝牙操作失败'
}

const startTemperatureScan = async () => {
  temperatureDevices.value = []
  temperatureScanning.value = true
  try {
    await requestBluetoothPermissions()
    const bluetooth = await getBluetoothState()
    if (bluetooth.status === 'unsupported') return
    if (!bluetooth.enabled) throw Object.assign(new Error('请开启手机蓝牙后继续'), { code: 'BLUETOOTH_DISABLED' })
    await scanTemperatureDevices()
  } catch (error) {
    temperatureScanning.value = false
    ElMessage.error(bluetoothErrorMessage(error))
  }
}

const stopTemperatureScan = async () => {
  temperatureScanning.value = false
  try { await stopTemperatureDeviceScan() } catch (error) { console.warn('停止蓝牙扫描失败:', error) }
}

const openTemperatureDialog = async () => {
  temperatureDialogVisible.value = true
  await startTemperatureScan()
}

const connectTemperature = async () => {
  if (thermalReplaying.value) thermal.stopReplay()
  if (temperatureConnecting.value) return
  clearTemperatureReading()
  lastTemperatureTimestamp.value = null
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
    localStorage.setItem('temperatureDeviceAddress', result.address || temperatureDeviceAddress.value.trim().toUpperCase())
    await stopTemperatureScan()
    ElMessage.success(`CookX Sense 已连接${result.name ? `：${result.name}` : ''}`)
  } catch (error) {
    clearTemperatureReading()
    lastTemperatureTimestamp.value = null
    temperatureConnected.value = false
    temperatureAcceptingData.value = false
    ElMessage.error(bluetoothErrorMessage(error))
  } finally {
    temperatureConnecting.value = false
  }
}

const disconnectTemperature = async () => {
  temperatureAcceptingData.value = false
  clearTemperatureReading()
  lastTemperatureTimestamp.value = null
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

const refreshDevice = async () => {
 clearTemperatureReading();temperatureAcceptingData.value=false
 try{const result=await reconcileTemperatureDevice();if(!pageActive)return;temperatureState.value=result.status==='unsupported'?'unsupported':result.state;temperatureConnected.value=result.state==='connected' && result.connected!==false;temperatureAcceptingData.value=temperatureConnected.value;deviceDiagnosticMessage.value=result.status==='unsupported'?'浏览器不支持 Bluetooth Classic，请使用 Android 安装包。':'状态已核对；等待新的连续测量窗口。'}catch(error){if(pageActive)deviceDiagnosticMessage.value=bluetoothErrorMessage(error)}
}
const exportDeviceLog = async()=>{try{await exportTemperatureDiagnostics();deviceDiagnosticMessage.value='诊断文件已导出；包含设备信息、原始温度帧及连接事件。'}catch(error){deviceDiagnosticMessage.value=error.message}}
const temperatureForeground=()=>{if(document.hidden){temperatureAcceptingData.value=false;clearTemperatureReading()}else refreshDevice()}
onMounted(()=>{document.addEventListener('visibilitychange',temperatureForeground);window.addEventListener('cookx:foreground',temperatureForeground)})
onUnmounted(()=>{document.removeEventListener('visibilitychange',temperatureForeground);window.removeEventListener('cookx:foreground',temperatureForeground)})

const registerTemperatureListener = async () => {
  try {
    temperatureListener = await onTemperatureData((data) => {
      if (!pageActive || document.hidden || !temperatureAcceptingData.value) return
      thermal.receive(data)
      ambientTemperature.value = Number.isFinite(data.ambientTemperature) ? data.ambientTemperature : null
      if (data.valid === false) { currentTemperature.value = null; return }
      const update = handleTemperatureUpdate(data.temperature, data.updatedAt)
      if (!update) return
      currentTemperature.value = update.temperature
      lastTemperatureTimestamp.value = update.updatedAt
      scheduleTemperatureStaleTimeout()
    })
    temperatureDeviceFoundListener = await onTemperatureDeviceFound((device) => {
      const index = temperatureDevices.value.findIndex(item => item.address === device.address)
      if (index >= 0) temperatureDevices.value.splice(index, 1, device)
      else temperatureDevices.value.push(device)
    })
    temperatureConnectionStateListener = await onTemperatureConnectionStateChanged((connection) => {
      if(!pageActive)return
      temperatureState.value=connection.state
      if(connection.awaitingSample)clearTemperatureReading()
      temperatureScanning.value = connection.state === 'scanning'
      temperatureConnected.value = connection.state === 'connected'
      temperatureAcceptingData.value = temperatureConnected.value
      if (!temperatureConnected.value) clearTemperatureReading()
      if (connection.state === 'error' && connection.message) ElMessage.error(connection.message)
    })
    if(!pageActive){await cleanupTemperatureDevice();return}
    await refreshDevice()
  } catch (error) {
    console.error('注册温度监听失败:', error)
    ElMessage.error('无法监听测温设备数据')
  }
}

const cleanupTemperatureDevice = async () => {
  temperatureAcceptingData.value = false
  clearTemperatureReading()
  lastTemperatureTimestamp.value = null
  if (temperatureListener) {
    try {
      await temperatureListener.remove()
    } catch (error) {
      console.warn('移除温度监听失败:', error)
    }
    temperatureListener = null
  }
  for (const listener of [temperatureDeviceFoundListener, temperatureConnectionStateListener]) {
    try { await listener?.remove() } catch (error) { console.warn('移除蓝牙监听失败:', error) }
  }
  temperatureDeviceFoundListener = null
  temperatureConnectionStateListener = null
  await stopTemperatureScan()
  // The application owns the connection; page teardown only removes view subscribers.
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
  const total = Math.max(step?.time_estimate || 0, 1)
  return Math.floor(((total - timeLeft.value) / total) * 100)
})

const getStepText = (step) => typeof step === 'object' ? (step?.text || step?.content || '') : String(step || '')
const getStepTitle = (step, index) => typeof step === 'object' && step?.title ? step.title : `步骤 ${index + 1}`
const activeSteps = computed(() => Array.isArray(activeRecipe.value?.steps) ? activeRecipe.value.steps : [])
const currentStep = computed(() => activeSteps.value[currentStepIdx.value] || null)
const isLastStep = computed(() => currentStepIdx.value >= activeSteps.value.length - 1)
const isVoicePlaying = computed(() => voicePlaybackState.value === 'playing')
const voiceStatusText = computed(() => {
  if (voicePlaybackState.value === 'loading') return `正在准备 · 第 ${currentStepIdx.value + 1} 步`
  if (voicePlaybackState.value === 'playing') return `正在播报 · 第 ${currentStepIdx.value + 1} 步`
  if (voicePlaybackState.value === 'paused') return `播报已暂停 · 第 ${currentStepIdx.value + 1} 步`
  return `语音待播放 · 第 ${currentStepIdx.value + 1} 步`
})
const inlineVoiceStatusText = computed(() => {
  if (voicePlaybackState.value === 'loading') return '正在准备本步骤语音'
  if (voicePlaybackState.value === 'playing') return '正在播报本步骤'
  if (voicePlaybackState.value === 'paused') return '本步骤播报已暂停'
  return '本步骤语音待播放'
})
const voiceControlText = computed(() => {
  if (voicePlaybackState.value === 'playing') return '暂停播报'
  if (voicePlaybackState.value === 'paused') return '继续播报'
  return '播放本步骤'
})
const voiceProgressVisible = computed(() => Number.isFinite(voiceDuration.value) && voiceDuration.value > 0)
const voiceProgressPercent = computed(() => {
  if (!voiceProgressVisible.value) return 0
  return Math.min(100, Math.max(0, (voiceCurrentTime.value / voiceDuration.value) * 100))
})
const voiceRemainingTime = computed(() => {
  if (!voiceProgressVisible.value) return ''
  return formatTime(Math.max(0, Math.ceil(voiceDuration.value - voiceCurrentTime.value)))
})
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
watch(() => [activeRecipe.value, currentStep.value, currentStepIdx.value], () => {
  thermal.setContext(adaptCookingContext(activeRecipe.value, currentStep.value, currentStepIdx.value))
}, { deep: true, immediate: true })

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
    if (!pageActive || readUserId() !== user.id || loading.value) return
    if (Array.isArray(res.data) && res.data.length > 0) {
      messages.value = safeHistory(res.data);
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
  const text=typeof val==='string'?val:userInput.value, requestedUser=readUserId()
  if (!text?.trim() || !requestedUser) return
  const version=++recipeRequestVersion
  recipeController?.abort();recipeController=new AbortController()
  lastRecipePrompt.value=text;recipeError.value='';loading.value=true
  messages.value.push({role:'user',content:text})
  try {
    const res=await axios.get(`${API_BASE_URL}/recommend-recipe`,{params:{user_id:requestedUser,user_prompt:text,save_history:true},signal:recipeController.signal,timeout:45000})
    if (!pageActive || version!==recipeRequestVersion || readUserId()!==requestedUser) return
    if(res.data?.status!=='success')throw new Error(res.data?.message || '菜谱服务未返回成功结果')
    const recipe=normalizeRecipe(res.data.recipe)
    messages.value.push({role:'assistant',content:`为你准备好了：${recipe.dish_name}`,recipe,missing:recipe.missing})
    if(userInput.value===text)userInput.value=''
  } catch(error) {
    if(pageActive && version===recipeRequestVersion && readUserId()===requestedUser && error.code!=='ERR_CANCELED') recipeError.value=error.code==='ECONNABORTED'?'菜谱请求超时，请重试':error.message || '菜谱请求失败'
  } finally {if(version===recipeRequestVersion){loading.value=false;await scrollToBottom()}}
}
const recipeUserChanged = () => {
  if(readUserId()!==userId){suspendCookingStores();recipeRequestVersion++;recipeController?.abort();messages.value=[];recipeError.value='';loading.value=false;stopNavigation();navigationVisible.value=false}
}
onMounted(()=>window.addEventListener('storage',recipeUserChanged))
onUnmounted(()=>{pageActive=false;recipeRequestVersion++;recipeController?.abort();window.removeEventListener('storage',recipeUserChanged)})

// --- 导航与消耗逻辑 ---
const restoreCooking = () => {cookingStore.ready=true;navigationVisible.value=true;refreshSession();startStepTimer()}
const startNavigation = async recipe => {
  try {
    recipe=normalizeRecipe(recipe)
    if(session.value?.status==='active')await ElMessageBox.confirm('开始新的菜谱将替换当前烹饪记录，是否继续？','替换烹饪',{confirmButtonText:'替换',cancelButtonText:'保留当前'})
    clearPreloadedVoice();cookingStore.engine.start(recipe,true);cookingStore.ready=true;refreshSession();navigationVisible.value=true;startStepTimer();runStep()
  } catch(error) {if(error!=='cancel'&&error!=='close')recipeError.value=error.message}
}
const startStepTimer = () => {if(timer)clearInterval(timer);timer=setInterval(()=>{tick.value=Date.now()},250)}
onMounted(()=>{if(cookingStore.ready && session.value?.status==='active')restoreCooking()})

// 修改 runStep 函数
const runCloudStep = async () => {
  await stopSystemSpeech(); await stopListening()
  const requestVersion = ++voiceRequestVersion
  stopCurrentAudio()

  // 1. 物理级清理计时器
  const step = activeRecipe.value.steps[currentStepIdx.value];
  if (!step) return;
  const stepIndex = currentStepIdx.value;
  const stepNumber = stepIndex + 1;
  voicePlaybackState.value = 'loading';
  console.log(`[Voice] runStep version=${requestVersion} step=${stepNumber}`);

  // Cooking time must continue even when the optional speech service fails.
  startStepTimer();

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

      if (!res.data?.audio_url) {
        if (requestVersion === voiceRequestVersion) voicePlaybackState.value = 'idle';
        return;
      }
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

    const syncVoiceProgress = () => {
      if (currentAudio !== audio || requestVersion !== voiceRequestVersion) return;
      voiceCurrentTime.value = Number.isFinite(audio.currentTime) ? audio.currentTime : 0;
      voiceDuration.value = Number.isFinite(audio.duration) ? audio.duration : 0;
    };
    audio.ontimeupdate = syncVoiceProgress;
    audio.ondurationchange = syncVoiceProgress;

    audio.onended = () => {
      if (currentAudio === audio) {
        stopCurrentAudio();
        
      }
    };

    audio.onerror = () => {
      if (currentAudio === audio) {
        stopCurrentAudio();
        
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

    voicePlaybackState.value = 'playing';

    // Online speech is requested only by the explicit fallback button.
  } catch (e) {
    if (discardStaleVoiceRequest(requestVersion)) {
      if (audio || blobUrl) discardStepAudio(audio, blobUrl);
      return;
    }
    stopCurrentAudio();
    console.error("语音播报全链路失败:", e);
    voiceMessage.value='在线播报不可用，步骤与计时不受影响';
  }
};

const nextStep = async () => {
  silenceSpeech()
  if(isLastStep.value) {
    completionVisible.value=true;silenceSpeech()
    return
  }
  voiceRequestVersion++;stopCurrentAudio();cookingStore.engine.move(1);refreshSession();runStep()
}
const prevStep = () => {if(currentStepIdx.value>0){silenceSpeech();voiceRequestVersion++;stopCurrentAudio();cookingStore.engine.move(-1);refreshSession();runStep()}}
const replayCurrentStep = () => runStep()
const toggleVoicePlayback = async () => {
  if(voicePlaybackState.value==='playing' && !currentAudio){await stopSystemSpeech();voicePlaybackState.value='paused';voiceMessage.value='系统播报已暂停，再次播放将从本步开头播报';return}
  if(voicePlaybackState.value==='playing' && currentAudio){currentAudio.pause();voicePlaybackState.value='paused';return}
  if(voicePlaybackState.value==='paused' && currentAudio){try{await currentAudio.play();voicePlaybackState.value='playing'}catch{runStep()}return}
  runStep()
}

// --- 功能性跳转 ---
const goToMarket = () => {
  const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent);
  const openAmapWeb = (longitude, latitude) => {
    const center = longitude != null && latitude != null
      ? `&center=${longitude},${latitude}`
      : '';
    const amapUrl = `https://uri.amap.com/search?keyword=${encodeURIComponent('菜市场')}${center}&view=map&src=smart_cooking&coordinate=gaode`;
    window.open(amapUrl, '_blank');
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

const silenceSpeech = () => {voiceRequestVersion++;stopCurrentAudio();stopSystemSpeech();stopListening();voicePlaybackState.value='idle'}
const stopNavigation = () => {silenceSpeech();clearPreloadedVoice();if(timer){clearInterval(timer);timer=null}}
const foregroundVoice = () => {if(document.hidden)silenceSpeech()}
onMounted(()=>document.addEventListener('visibilitychange',foregroundVoice))
onUnmounted(()=>document.removeEventListener('visibilitychange',foregroundVoice))
const runStep = async () => {
  stopListening()
  const version=++voiceRequestVersion;stopCurrentAudio();voiceMessage.value='';voicePlaybackState.value='loading'
  try{await speakSystem(`第${currentStepIdx.value+1}步：${currentStepText.value}`,state=>{if(version===voiceRequestVersion){voicePlaybackState.value=state==='error'?'idle':state;if(state==='error')voiceMessage.value=LOCAL_TEST_MODE?'系统播报失败，请使用文字或按钮':'系统播报失败，可手动重试在线播报'}})}
  catch(error){if(version===voiceRequestVersion){voicePlaybackState.value='idle';voiceMessage.value=error.message}}
}
const executeVoiceCommand = command => {
  if(!navigationVisible.value || readUserId()!==userId)return
  const actions={next:nextStep,previous:prevStep,repeat:replayCurrentStep,pauseTimer,resumeTimer,startTimer:()=>{if(currentStepDuration.value>0){timerSeconds.value=currentStepDuration.value;setManualTimer()}else voiceMessage.value='本步未提供时长，请填写手动计时秒数并确认开始'},temperature:()=>{voiceMessage.value=currentTemperature.value===null?'暂无有效实时温度，请检查设备与测量状态':`${thermalReplaying.value?'回放数据':'当前测量'}：${currentTemperature.value.toFixed(1)}℃；${temperatureLevel.value.status}`}}
  actions[command]?.()
}

onUnmounted(stopNavigation)
onUnmounted(cleanupTemperatureDevice)
</script>

<style scoped>
.device-diagnostics{margin:12px 0;padding:16px;border:1px solid #dce6df;border-radius:16px;background:#fff;color:#315340;font-size:13px;line-height:1.7}
.device-diagnostics summary{cursor:pointer;font-weight:600}
.device-diagnostics button{min-height:42px;padding:8px 10px;margin:4px 4px 4px 0;border:1px solid #c7d6cb;border-radius:9px;background:#eff5f0;color:#284b37;font:inherit}
.chef-state-card{padding:16px;margin:12px 0;border:1px solid #dce6df;border-radius:16px;background:white;line-height:1.6}
.timer-controls{display:flex;flex-wrap:wrap;gap:6px;align-items:center;padding:10px 0;font-size:13px}
.timer-controls label{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.timer-controls input{width:78px;min-height:40px;padding:6px;border:1px solid #c7d6cb;border-radius:9px;font:inherit;box-sizing:border-box}
.timer-controls button,.chef-state-card button{min-height:42px;padding:8px 10px;border:1px solid #c7d6cb;border-radius:9px;background:#eff5f0;color:#284b37;font:inherit}

.timer-controls { display:flex; flex-wrap:wrap; gap:8px; margin:12px 0; }
.timer-controls button,.timer-controls input { padding:8px; border:1px solid #cddbd3; border-radius:8px; background:#fff; color:#234c3b; }
.timer-controls input { width:80px; }
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
.bluetooth-scan-status { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 12px; color: var(--cookx-text-secondary); font-size: 12px; }
.bluetooth-device-list { display: grid; gap: 8px; max-height: 300px; overflow-y: auto; }
.bluetooth-device-item { display: flex; align-items: center; justify-content: space-between; width: 100%; padding: 12px 13px; border: 1px solid rgba(23,63,53,.12); border-radius: 12px; background: #fafbf8; color: var(--cookx-primary-dark); text-align: left; cursor: pointer; }
.bluetooth-device-item span { display: grid; gap: 3px; }
.bluetooth-device-item small { color: var(--cookx-text-secondary); font-family: monospace; }
.bluetooth-device-item em { color: var(--cookx-text-secondary); font-size: 10px; font-style: normal; }
.bluetooth-device-item.recommended { border-color: rgba(77,139,105,.35); background: rgba(77,139,105,.08); }
.bluetooth-device-item.selected { border-color: var(--cookx-primary); box-shadow: 0 0 0 2px rgba(23,63,53,.08); }
.bluetooth-debug { margin-top: 12px; color: var(--cookx-text-secondary); font-size: 11px; }
.bluetooth-debug summary { margin-bottom: 8px; cursor: pointer; }

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
.current-step-card { transition: border-color .22s ease, box-shadow .22s ease, transform .22s ease; }
.current-step-card.is-voice-active { border-color: rgba(77,139,105,.34); box-shadow: 0 12px 34px rgba(23,63,53,.13); }
.panel-heading { justify-content: space-between; gap: 12px; margin-bottom: 22px; }
.panel-heading > span { display: inline-flex; align-items: center; gap: 9px; font-size: 17px; font-weight: 750; }
.panel-heading > span .el-icon { display: grid; width: 31px; height: 31px; border-radius: 10px; background: #edf3ef; color: var(--cookx-primary); place-items: center; }
.panel-heading > b { display: inline-flex; align-items: center; gap: 5px; padding: 8px 11px; border-radius: 999px; background: #fff2e9; color: var(--cookx-accent); font-size: 12px; }
.panel-heading > i { position: relative; padding-left: 13px; color: var(--cookx-text-secondary); font-size: 11px; font-style: normal; }
.panel-heading > i::before { position: absolute; top: 50%; left: 0; width: 7px; height: 7px; border-radius: 50%; background: #c8ceca; content: ''; transform: translateY(-50%); }
.panel-heading > i.connected::before { background: #4caf6a; box-shadow: 0 0 0 4px rgba(76,175,106,.1); }
.inline-voice { margin-top: 17px; padding: 12px 13px; border: 1px solid rgba(23,63,53,.09); border-radius: 14px; background: #f8f8f4; color: var(--cookx-text-secondary); transition: border-color .2s ease, background .2s ease; }
.inline-voice.playing { border-color: rgba(77,139,105,.34); background: rgba(77,139,105,.09); color: var(--cookx-primary); }
.inline-voice.loading { border-color: rgba(233,162,59,.26); background: rgba(233,162,59,.07); color: #93631f; }
.inline-voice.paused { border-color: rgba(216,107,53,.2); background: rgba(216,107,53,.06); color: var(--cookx-accent); }
.inline-voice-main { display: flex; align-items: center; gap: 9px; min-height: 24px; font-size: 12px; }
.voice-remaining { margin-left: auto; color: currentColor; font-variant-numeric: tabular-nums; font-weight: 700; }
.voice-wave { display: inline-flex; align-items: center; justify-content: center; gap: 2px; width: 20px; height: 18px; }
.voice-wave i { width: 2px; height: 5px; border-radius: 2px; background: currentColor; transform-origin: center; }
.inline-voice.playing .voice-wave i { animation: cookx-voice-wave .8s ease-in-out infinite alternate; }
.inline-voice.playing .voice-wave i:nth-child(2) { animation-delay: -.6s; }
.inline-voice.playing .voice-wave i:nth-child(3) { animation-delay: -.3s; }
.inline-voice.playing .voice-wave i:nth-child(4) { animation-delay: -.7s; }
@keyframes cookx-voice-wave { from { height: 5px; opacity: .58; } to { height: 16px; opacity: 1; } }
.inline-voice .voice-progress { margin-top: 9px; }
.inline-voice-actions { display: flex; gap: 8px; margin-top: 10px; }
.inline-voice-actions button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 38px; padding: 0 12px; border: 1px solid rgba(23,63,53,.14); border-radius: 11px; background: #fff; color: var(--cookx-primary); font-size: 11px; font-weight: 700; cursor: pointer; }
.inline-voice-actions .voice-toggle { border-color: transparent; background: var(--cookx-primary); color: #fff; }
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
.voice-progress { height: 4px; margin-top: 15px; overflow: hidden; border-radius: 999px; background: rgba(23,63,53,.09); }
.voice-progress span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--cookx-success), var(--cookx-gold)); transition: width .18s linear; }
.step-switcher button.next:disabled { border-color: rgba(23,63,53,.12); background: #edf0ed; color: var(--cookx-text-secondary); box-shadow: none; }
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
  .inline-voice-actions button { flex: 1; min-height: 42px; }
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
