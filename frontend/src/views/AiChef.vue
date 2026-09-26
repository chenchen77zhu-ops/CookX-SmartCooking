<template>
  <div :class="['ai-chef-container', { 'is-cooking': cookingView }]">
    <!-- 顶部 -->
    <header class="chef-top">
      <template v-if="cookingView">
        <button type="button" class="round-btn" aria-label="收起烹饪导航" @click="minimizeNavigation"><CkIcon name="chevron-left" :size="22" :stroke="2" /></button>
        <div class="chef-top__title"><b>烹饪中</b><small>{{ cookingStatusText }}<template v-if="recipeTotalSeconds"> · 预计 {{ estimatedFinishTime }} 完成</template></small></div>
        <button type="button" class="round-btn" aria-label="设备与连接" @click="openTemperatureDialog"><CkIcon name="sensor" :size="20" /></button>
      </template>
      <template v-else>
        <div class="chef-top__brand"><h1>厨房</h1><p>从菜谱推荐到语音步骤指导</p></div>
        <button type="button" class="sense-chip" @click="openTemperatureDialog">
          <span :class="['ck-dot', { 'is-on': temperatureConnected }]"></span>
          <span><b>CookX Sense</b><small>{{ temperatureConnectionText }}</small></span>
        </button>
      </template>
    </header>

    <main class="chef-content">
      <CookingCompletion v-if="completionVisible && session" :key="session.id" :session="session" :engine="cookingStore.engine" @changed="completionChanged" @close="completionVisible=false" />
      <button v-if="session?.status === 'completed' && !completionVisible" type="button" class="ck-btn ck-btn--ghost ck-btn--block gap" @click="completionVisible=true">查看完成与库存核对</button>
      <!-- ============ 烹饪中（沉浸式） ============ -->
      <template v-if="cookingView">
        <section class="recipe-head ck-glass">
          <div class="recipe-head__copy">
            <h2>{{ activeRecipe.dish_name || '当前菜谱' }}</h2>
            <span class="ai-chip"><CkIcon name="sparkle" :size="14" />CookX AI 实时引导</span>
            <div class="recipe-head__progress">
              <span>第 {{ currentStepIdx + 1 }} / {{ activeSteps.length }} 步</span>
              <i><b :style="{ width: `${overallProgress}%` }"></b></i>
              <span>{{ overallProgress }}%</span>
            </div>
          </div>
          <img v-if="activeRecipeImage" :src="activeRecipeImage" :alt="activeRecipe.dish_name" class="recipe-head__img" />
          <span v-else class="recipe-head__img is-empty"><CkIcon name="pot" :size="30" /></span>
        </section>

        <section :class="['gauge-block', temperatureLevel.className]">
          <CkGauge :value="currentTemperature" :size="gaugeSize" label="当前锅温" :sub="currentStepMeta.find(m => m.label === '目标温度') ? `目标 ${currentStepMeta.find(m => m.label === '目标温度').value}` : temperatureConnectionText">
            <span class="phase-chip">{{ currentTemperature === null ? '等待数据' : temperatureLevel.status }}</span>
          </CkGauge>
          <div class="step-timer">
            <template v-if="hasStepTimer"><span>本步剩余</span><b class="ck-num">{{ formatTime(timeLeft) }}</b></template>
            <template v-else><span>本步未设定时长</span><b class="no-duration">按实际火候判断</b></template>
          </div>
        </section>

        <article :class="['current-step-card', 'ck-glass', { 'is-voice-active': isVoicePlaying }]">
          <div class="panel-heading">
            <span class="step-kicker"><em>第 {{ currentStepIdx + 1 }} 步</em><b>{{ currentStepTitle }}</b></span>
            <span v-if="hasStepTimer" class="step-remaining"><CkIcon name="timer" :size="15" />剩余 {{ formatTime(timeLeft) }}</span><span v-else class="step-remaining is-muted">时长未提供</span>
          </div>
          <p class="step-description">{{ currentStepText }}</p>
          <div v-if="currentStepMeta.length" class="step-meta">
            <span v-for="meta in currentStepMeta" :key="meta.label"><b>{{ meta.label }}</b>{{ meta.value }}</span>
          </div>
          <div :class="['inline-voice', voicePlaybackState]">
            <div class="inline-voice-main">
              <span class="voice-wave" aria-hidden="true"><i></i><i></i><i></i><i></i></span>
              <strong>{{ inlineVoiceStatusText }}</strong>
              <span v-if="voiceProgressVisible" class="voice-remaining">{{ voiceRemainingTime }}</span>
            </div>
            <div v-if="voiceProgressVisible" class="voice-progress" aria-label="本步骤语音播放进度"><span :style="{ width: `${voiceProgressPercent}%` }"></span></div>
            <div class="inline-voice-actions">
              <button type="button" class="voice-toggle" @click="toggleVoicePlayback"><CkIcon :name="isVoicePlaying ? 'pause' : 'play'" :size="16" />{{ voiceControlText }}</button>
              <button type="button" @click="replayCurrentStep"><CkIcon name="refresh" :size="16" />重新播报</button>
            </div>
          </div>
          <p v-if="voiceMessage" role="status" class="inline-status">{{ voiceMessage }}</p>
        </article>

        <button v-if="thermalAssessment.suggestion || currentStepTip" type="button" class="ck-cream advice" @click="replayCurrentStep">
          <span class="advice__icon"><CkIcon name="chef" :size="22" /></span>
          <span class="advice__body"><small>CookX 建议</small><b>{{ thermalAssessment.suggestion || currentStepTip }}</b></span>
          <CkIcon name="chevron-right" :size="18" class="advice__chev" />
        </button>

        <section class="ck-glass card">
          <div class="ck-section-title"><span>实时温度曲线</span><small>{{ thermalReplaying ? '仿真回放' : '设备测温' }}</small></div>
          <CkTempChart :points="chartPoints" :prediction="chartPrediction" :band="chartBand" :height="150" empty-text="连接 CookX Sense 后显示实时曲线" />
          <div class="chart-legend"><span><i class="solid"></i>当前温度</span><span><i class="dash"></i>预测曲线</span></div>
        </section>

        <div class="duo">
          <section class="ck-glass mini">
            <CkIcon name="flame" :size="22" class="mini__icon" />
            <div><small>下一步</small><b>{{ activeSteps[currentStepIdx + 1] ? getStepTitle(activeSteps[currentStepIdx + 1], currentStepIdx + 1) : '即将完成' }}</b></div>
          </section>
          <section class="ck-glass mini">
            <CkIcon name="clock" :size="22" class="mini__icon" />
            <div><small>预计完成</small><b>{{ recipeTotalSeconds ? estimatedFinishTime : '时长未知' }}</b><em v-if="recipeTotalSeconds">总时长 {{ formatDuration(recipeTotalSeconds) }}</em></div>
          </section>
        </div>

        <section class="steps-panel ck-glass card">
          <div class="ck-section-title"><span>烹饪步骤</span><small>{{ overallProgress }}%</small></div>
          <ol class="step-track">
            <li v-for="(step, index) in activeSteps" :key="index" :class="['track-step', { done: index < currentStepIdx, active: index === currentStepIdx }]">
              <span><CkIcon v-if="index < currentStepIdx" name="check" :size="14" :stroke="2.4" /><template v-else>{{ index + 1 }}</template></span>
              <b>{{ getStepTitle(step, index) }}</b>
            </li>
          </ol>
        </section>

        <section class="ck-glass card tools">
          <div class="ck-section-title"><span>烹饪工具</span><small>语音 · 计时 · 调整</small></div>
          <VoiceCommands @before-listen="silenceSpeech" @command="executeVoiceCommand" />
          <div class="timer-controls">
            <button type="button" @click="pauseTimer">暂停计时</button>
            <button type="button" @click="resumeTimer">继续计时</button>
            <label>手动计时（秒）<input v-model="timerSeconds" type="number" min="1" max="86400" /></label>
            <button type="button" @click="setManualTimer">开始计时</button>
          </div>
          <button type="button" class="text-btn" @click="runCloudStep">重试在线播报</button>
          <CookingReminders :key="session.id" :store="cookingStore" :assessment="thermalAssessment" :replaying="thermalReplaying" @changed="refreshSession" />
          <CookingAdjustments :key="session.id" :session="session" :engine="cookingStore.engine" @changed="refreshSession" />
        </section>

        <article :class="['sense-card', 'ck-glass', 'card', temperatureLevel.className]">
          <div class="ck-section-title">
            <span>CookX Sense 温度监控</span>
            <small :class="{ connected: temperatureConnected }">{{ temperatureConnectionText }}</small>
          </div>
          <div class="temperature-grid">
            <div><span>环境温度</span><strong class="ck-num">{{ ambientTemperature === null ? '--' : ambientTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ ambientTemperature === null ? '当前硬件固件未上传环境温度' : '设备环境温度' }}</p></div>
            <div><span>锅面温度</span><strong class="ck-num">{{ currentTemperature === null ? '--' : currentTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ currentTemperature === null ? '等待数据' : temperatureLevel.status }}</p></div>
          </div>
          <p v-if="temperatureConnected" class="sense-note">{{ currentTemperature === null ? '数据暂未更新' : '已收到设备读数，请结合测量质量查看' }} · 最后更新 {{ lastTemperatureTime }}</p>
          <p v-else class="sense-note">尚未连接 CookX Sense，连接设备后可实时查看温度</p>
          <TemperatureInsight :assessment="thermalAssessment" :history="thermalHistory" :prediction="thermalPrediction"
            :model-state="thermalModelState" :experimental="thermalExperimental" :replaying="thermalReplaying"
            :connected="temperatureConnected" :storage-message="thermalStorageMessage"
            @confirm="thermal.confirm($event)" @experimental="thermal.setExperimental($event)"
            @replay="thermal.startReplay()" @stop-replay="thermal.stopReplay()" @export="thermal.exportSession($event)" />
          <button v-if="!temperatureConnected" type="button" class="sense-action" :disabled="temperatureConnecting" @click="openTemperatureDialog">{{ temperatureConnecting ? '连接中…' : '连接测温设备' }}</button>
          <button v-else type="button" class="sense-action secondary" @click="disconnectTemperature">断开设备</button>
        </article>

        <section v-if="activeRecipe.ingredients_list?.length || recipeReminder || activeRecipe.nutrition" class="ck-glass card support">
          <div v-if="activeRecipe.ingredients_list?.length" class="support-row"><CkIcon name="bag" :size="18" /><div><b>食材清单</b><p>{{ ingredientSummary }}</p></div></div>
          <div v-if="recipeReminder" class="support-row"><CkIcon name="bell" :size="18" /><div><b>CookX 提醒</b><p>{{ recipeReminder }}</p></div></div>
          <div v-if="activeRecipe.nutrition" class="support-row"><CkIcon name="chart" :size="18" /><div><b>营养信息</b><p>{{ nutritionSummary }}</p></div></div>
        </section>

        <details class="device-diagnostics ck-glass"><summary>设备状态与诊断</summary><p>{{ temperatureConnectionText }}；恢复页面后等待新测量。后台连续采集能力待真机验证。</p><div class="diag-actions"><button type="button" @click="openTemperatureDialog">扫描与连接设备</button><button type="button" @click="refreshDevice">重新核对设备状态</button><button type="button" @click="exportDeviceLog">导出设备诊断</button></div><p role="status">{{ deviceDiagnosticMessage }}</p></details>
        <p v-if="sessionMessage" role="status" class="inline-status">{{ sessionMessage }}</p>

        <div class="step-switcher">
          <button type="button" :disabled="currentStepIdx === 0" @click="prevStep"><CkIcon name="chevron-left" :size="18" />上一步</button>
          <button type="button" class="next" @click="nextStep">{{ isLastStep ? '完成烹饪' : '下一步' }}<CkIcon name="chevron-right" :size="18" /></button>
        </div>
      </template>

      <!-- ============ 待机：选菜与 AI 对话 ============ -->
      <template v-else>
        <section :class="['gauge-block', 'is-idle', temperatureLevel.className]">
          <CkGauge :value="currentTemperature" :size="220" label="当前锅温" :sub="temperatureConnected ? (currentTemperature === null ? '等待新数据' : temperatureLevel.status) : '测温设备未连接'" />
          <button v-if="!temperatureConnected" type="button" class="ck-btn ck-btn--ghost connect-btn" :disabled="temperatureConnecting" @click="openTemperatureDialog"><CkIcon name="bluetooth" :size="16" />{{ temperatureConnecting ? '连接中…' : '连接测温设备' }}</button>
        </section>

        <p v-if="sessionMessage" role="status" class="inline-status">{{ sessionMessage }}</p>
        <section v-if="session?.status === 'active'" class="ck-cream advice resume">
          <span class="advice__icon"><CkIcon name="pot" :size="22" /></span>
          <span class="advice__body"><small>未完成的烹饪</small><b>{{ session.recipe?.dish_name || '继续上次的菜' }}</b><em>计时按实际经过时间核对</em></span>
          <button type="button" class="ck-btn ck-btn--heat" @click="restoreCooking">恢复烹饪</button>
        </section>
        <section v-if="recipeError" class="chef-state-card ck-glass is-error" role="alert"><p>{{ recipeError }}</p><button type="button" class="ck-btn ck-btn--heat" @click="sendMessage(lastRecipePrompt)">重新生成菜谱</button></section>

        <section v-if="loading" class="chef-state-card ck-glass" v-loading="true"><h2>CookX 正在生成菜谱</h2><p>正在结合你的需求整理烹饪步骤…</p></section>
        <section v-else-if="!latestRecipe" class="chef-state-card ck-glass empty-state">
          <span class="empty-icon"><CkIcon name="chef" :size="28" /></span><h2>还没有开始烹饪</h2><p>告诉 CookX 想吃什么，或从冰箱推荐里挑一道菜。</p><button type="button" class="ck-btn ck-btn--heat" @click="focusRecipeInput">去选择菜谱</button>
        </section>
        <section v-else class="latest-recipe ck-cream">
          <div><small>准备开始</small><h2>{{ latestRecipe.dish_name || '已生成菜谱' }}</h2><p>菜谱已准备好，可以开启语音步骤指导。</p></div>
          <button type="button" class="ck-btn ck-btn--heat" @click="startNavigation(latestRecipe)"><CkIcon name="mic" :size="18" />开始烹饪</button>
        </section>

        <section class="conversation-panel">
          <div class="ck-section-title"><span>AI 菜谱对话</span><small>{{ messages.length }} 条记录</small></div>
          <div ref="chatBox" class="chat-messages">
            <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.role]">
              <span class="avatar"><CkIcon :name="msg.role === 'user' ? 'user' : 'chef'" :size="18" /></span>
              <div class="message-bubble">
                <div class="text-content">{{ msg.content }}</div>
                <article v-if="msg.recipe?.steps" class="recipe-card">
                  <div class="recipe-header"><div><span>COOKX RECIPE</span><h3>{{ msg.recipe.dish_name || '美味教程' }}</h3></div></div>
                  <div v-if="msg.recipe.ingredients_list?.length" class="ing-grid"><span v-for="(ing, i) in msg.recipe.ingredients_list" :key="i"><b>{{ ing.item }}</b>{{ ing.amount }}</span></div>
                  <div class="steps-preview"><p v-for="(step, sIdx) in msg.recipe.steps.slice(0, 3)" :key="sIdx"><i>{{ sIdx + 1 }}</i>{{ getStepText(step) }}</p></div>
                  <button type="button" class="start-guide" @click="startNavigation(msg.recipe)"><CkIcon name="mic" :size="16" />开始指导</button>
                  <div class="card-actions"><button type="button" @click="goToMarket"><CkIcon name="pin" :size="16" />买食材</button><button type="button" @click="orderDelivery(msg.recipe.dish_name)"><CkIcon name="bike" :size="16" />点外卖</button></div>
                </article>
              </div>
            </div>
          </div>
        </section>

        <details class="device-diagnostics ck-glass"><summary>设备状态与诊断</summary><p>{{ temperatureConnectionText }}；恢复页面后等待新测量。后台连续采集能力待真机验证。</p><div class="diag-actions"><button type="button" @click="openTemperatureDialog">扫描与连接设备</button><button type="button" @click="refreshDevice">重新核对设备状态</button><button type="button" @click="exportDeviceLog">导出设备诊断</button></div><p role="status">{{ deviceDiagnosticMessage }}</p></details>
      </template>
    </main>

    <div v-if="!navigationVisible" class="chat-input-bar-fixed">
      <div class="input-content">
        <el-input ref="recipeInput" v-model="userInput" placeholder="告诉 CookX 你想做什么…" @keyup.enter="sendMessage()" />
        <button type="button" class="send-btn" aria-label="发送" :disabled="loading" @click="sendMessage()"><CkIcon name="send" :size="20" /></button>
      </div>
    </div>

    <el-dialog v-model="temperatureDialogVisible" title="连接 CookX Sense" width="90%" @closed="stopTemperatureScan">
      <div class="bluetooth-scan-status">
        <span>{{ bluetoothScanText }}</span>
        <el-button size="small" :loading="temperatureScanning" @click="startTemperatureScan">重新扫描</el-button>
      </div>
      <p class="temperature-dialog-help">扫描最多约 25 秒。已配对设备也会列出，但是否在线需点击连接确认。</p>
      <div class="bluetooth-device-list">
        <button
          v-for="device in sortedTemperatureDevices"
          :key="device.address"
          type="button"
          :class="['bluetooth-device-item', { recommended: device.isJdy31, selected: temperatureDeviceAddress === device.address }]"
          @click="temperatureDeviceAddress = device.address"
        >
          <span><b>{{ device.name || '未知蓝牙设备' }}</b><small>{{ device.address }}</small></span>
          <em>{{ device.bondState === 12 ? '已配对 · 可尝试连接' : device.isJdy31 ? 'CookX 设备' : 'Classic Bluetooth' }}</em>
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
import CookingReminders from '../components/CookingReminders.vue'
import CookingCompletion from '../components/CookingCompletion.vue'
import {reconcileTimer} from '../services/cookingNotifications.js'
import CookingAdjustments from '../components/CookingAdjustments.vue'
import VoiceCommands from '../components/VoiceCommands.vue'
import { speakSystem, stopSystemSpeech, stopListening } from '../services/systemVoice.js'
import { getCookingStore, suspendCookingStores } from '../services/cookingStore.js'
import { takeRecipeDraft } from '../services/recipeDraft'
import { normalizeRecipe, safeHistory } from '../services/recipeAdapter.js'
import { readUserId } from '../services/recognitionDraft.js'
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import axios from 'axios'
import CkIcon from '@/components/ck/CkIcon.vue'
import CkGauge from '@/components/ck/CkGauge.vue'
import CkTempChart from '@/components/ck/CkTempChart.vue'
import { uiState } from '@/services/uiState.js'
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
    SCAN_BUSY: '正在连接、已连接或正在扫描，请先结束当前操作',
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
    const result = await scanTemperatureDevices()
    for (const device of result.devices || []) {
      const index = temperatureDevices.value.findIndex(item => item.address === device.address)
      if (index >= 0) temperatureDevices.value.splice(index, 1, device)
      else temperatureDevices.value.push(device)
    }
    temperatureScanning.value = result.status === 'scanning'
    if (result.warning === 'SCAN_FAILED') ElMessage.warning('附近扫描未能启动；可以选择已配对的 JDY-31 尝试连接。')
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
  if(readUserId()!==user.id)return
  try { const recipe=takeRecipeDraft(user.id);if(recipe)messages.value.push({role:'assistant',content:'独立复刻菜谱已准备好。点击开始指导后才会启动烹饪。',recipe}) } catch(error){recipeError.value=error.message}
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
  try{await speakSystem(`第${currentStepIdx.value+1}步：${currentStepText.value}`,state=>{if(version===voiceRequestVersion){voicePlaybackState.value=state==='error'?'idle':state;if(state==='error')voiceMessage.value='系统播报失败，可手动重试在线播报'}})}
  catch(error){if(version===voiceRequestVersion){voicePlaybackState.value='idle';voiceMessage.value=error.message}}
}
const executeVoiceCommand = command => {
  if(!navigationVisible.value || readUserId()!==userId)return
  const actions={next:nextStep,previous:prevStep,repeat:replayCurrentStep,pauseTimer,resumeTimer,startTimer:()=>{if(currentStepDuration.value>0){timerSeconds.value=currentStepDuration.value;setManualTimer()}else voiceMessage.value='本步未提供时长，请填写手动计时秒数并确认开始'},temperature:()=>{voiceMessage.value=currentTemperature.value===null?'暂无有效实时温度，请检查设备与测量状态':`${thermalReplaying.value?'回放数据':'当前测量'}：${currentTemperature.value.toFixed(1)}℃；${temperatureLevel.value.status}`}}
  actions[command]?.()
}

onUnmounted(stopNavigation)
onUnmounted(cleanupTemperatureDevice)

// ---- 实时厨房界面 ----
const cookingView = computed(() => navigationVisible.value && activeSteps.value.length > 0)
const hasStepTimer = computed(() => Boolean(currentStepDuration.value || session.value?.timers[currentStepIdx.value]?.round > 0))
watch(cookingView, value => { uiState.immersive = value }, { immediate: true })
onUnmounted(() => { uiState.immersive = false })
watch(completionVisible, visible => { if (visible) window.scrollTo({ top: 0, behavior: 'smooth' }) })
const minimizeNavigation = () => { stopNavigation(); navigationVisible.value = false }
const gaugeSize = Math.min(280, Math.max(220, Math.round((window.innerWidth || 390) * 0.66)))
const chartPoints = computed(() => (thermalHistory.value || [])
  .filter(sample => sample.valid && Number.isFinite(sample.temperature))
  .map(sample => ({ at: sample.updatedAt, t: sample.temperature })))
const chartPrediction = computed(() => {
  const last = chartPoints.value[chartPoints.value.length - 1]
  const forecast = thermalPrediction.value?.forecast
  if (!last || !Array.isArray(forecast)) return []
  return forecast.map(item => ({ at: last.at + item.seconds * 1000, t: (item.low + item.high) / 2 }))
})
const chartBand = computed(() => {
  const target = currentStepMeta.value.find(meta => meta.label === '目标温度')?.value
  const numbers = String(target || '').match(/\d+(\.\d+)?/g)?.map(Number) || []
  if (numbers.length >= 2) return [Math.min(...numbers), Math.max(...numbers)]
  if (numbers.length === 1) return [numbers[0] - 10, numbers[0] + 10]
  return null
})
</script>

<style scoped>
.ai-chef-container { position: relative; z-index: 1; width: min(100%, var(--ck-page-max)); min-height: 100vh; margin: 0 auto; padding: var(--sat) calc(var(--ck-gutter) + var(--sar)) calc(96px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); color: var(--ck-text); }
.ai-chef-container.is-cooking { padding-bottom: calc(96px + var(--sab)); }
button { font: inherit; }

/* 顶部 */
.chef-top { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 64px; padding: 8px 0; }
.chef-top__brand h1 { font-size: 30px; font-weight: 700; letter-spacing: -0.4px; line-height: 1.2; }
.chef-top__brand p { margin-top: 2px; color: var(--ck-text-2); font-size: 13px; }
.chef-top__title { display: flex; flex-direction: column; align-items: center; min-width: 0; text-align: center; }
.chef-top__title b { font-size: 17px; font-weight: 600; }
.chef-top__title small { color: var(--ck-text-2); font-size: 12px; }
.round-btn { display: grid; place-items: center; width: 40px; height: 40px; flex: 0 0 40px; padding: 0; border: 1px solid var(--ck-glass-border); border-radius: 50%; background: rgba(20, 22, 21, 0.5); color: var(--ck-text); -webkit-backdrop-filter: blur(12px); backdrop-filter: blur(12px); }
.sense-chip { display: flex; align-items: center; gap: 8px; min-height: 44px; padding: 6px 14px 6px 12px; border: 1px solid var(--ck-glass-border); border-radius: 16px; background: rgba(20, 22, 21, 0.5); color: var(--ck-text); text-align: left; -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
.sense-chip span:last-child { display: flex; flex-direction: column; line-height: 1.25; }
.sense-chip b { font-size: 12.5px; font-weight: 600; }
.sense-chip small { color: var(--ck-text-2); font-size: 11.5px; }

.chef-content { display: flex; flex-direction: column; gap: 12px; }
.card { padding: 16px; }
.inline-status { color: var(--ck-text-2); font-size: 13px; }

/* 菜谱头 */
.recipe-head { display: flex; align-items: center; gap: 12px; padding: 16px; overflow: hidden; }
.recipe-head__copy { display: flex; flex-direction: column; align-items: flex-start; gap: 8px; min-width: 0; flex: 1 1 auto; }
.recipe-head h2 { max-width: 100%; overflow: hidden; font-size: 22px; font-weight: 700; line-height: 1.25; text-overflow: ellipsis; white-space: nowrap; }
.ai-chip { display: inline-flex; align-items: center; gap: 5px; height: 26px; padding: 0 10px; border: 1px solid rgba(255, 138, 61, 0.4); border-radius: 999px; background: rgba(255, 138, 61, 0.12); color: #FFB27F; font-size: 12px; }
.recipe-head__progress { display: flex; align-items: center; gap: 8px; width: 100%; color: var(--ck-text-2); font-size: 12px; font-variant-numeric: tabular-nums; }
.recipe-head__progress i { position: relative; flex: 1 1 auto; height: 4px; border-radius: 2px; background: rgba(255, 255, 255, 0.12); overflow: hidden; }
.recipe-head__progress i b { position: absolute; inset: 0 auto 0 0; border-radius: 2px; background: var(--ck-heat-gradient); transition: width 0.4s ease; }
.recipe-head__img { width: 84px; height: 84px; flex: 0 0 84px; border-radius: 50%; object-fit: cover; box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4); }
.recipe-head__img.is-empty { display: grid; place-items: center; background: var(--ck-fill); color: var(--ck-heat); }

/* 仪表 */
.gauge-block { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 8px 0 4px; }
.gauge-block.is-idle { padding-top: 0; }
.phase-chip { display: inline-flex; align-items: center; height: 32px; margin-top: 10px; padding: 0 18px; border-radius: 999px; background: linear-gradient(90deg, #E8641F, #C8501A); color: #fff; font-size: 14px; font-weight: 600; box-shadow: 0 8px 20px rgba(232, 100, 31, 0.35); }
.temperature-danger .phase-chip { background: #D8412F; }
.step-timer { display: flex; flex-direction: column; align-items: center; margin-top: -4px; }
.step-timer span { color: var(--ck-text-2); font-size: 13px; }
.step-timer b { color: #fff; font-size: 44px; font-weight: 300; line-height: 1.1; }
.step-timer b.no-duration { font-size: 18px; font-weight: 500; color: var(--ck-text-2); }
.connect-btn { min-height: 40px; margin-top: 4px; font-size: 14px; }

/* 当前步骤 */
.current-step-card { padding: 18px 16px; }
.current-step-card.is-voice-active { border-color: rgba(255, 138, 61, 0.45); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 10px; margin-bottom: 8px; }
.step-kicker { display: flex; align-items: baseline; gap: 8px; min-width: 0; }
.step-kicker em { flex: 0 0 auto; color: var(--ck-heat); font-size: 13px; font-style: normal; font-weight: 700; }
.step-kicker b { overflow: hidden; font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.step-remaining { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 999px; background: var(--ck-heat-soft); color: #FFB27F; font-size: 12.5px; font-variant-numeric: tabular-nums; }
.step-remaining.is-muted { background: var(--ck-fill); color: var(--ck-text-3); }
.step-description { color: var(--ck-text); font-size: 19px; font-weight: 500; line-height: 1.6; }
.step-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.step-meta span { display: inline-flex; gap: 6px; padding: 5px 10px; border-radius: 10px; background: var(--ck-fill); color: var(--ck-text); font-size: 13px; }
.step-meta b { color: var(--ck-text-3); font-weight: 500; }
.inline-voice { margin-top: 14px; padding: 12px; border-radius: 16px; background: rgba(255, 255, 255, 0.05); }
.inline-voice-main { display: flex; align-items: center; gap: 10px; font-size: 13px; }
.inline-voice-main strong { flex: 1 1 auto; font-weight: 500; }
.voice-remaining { color: var(--ck-text-3); font-variant-numeric: tabular-nums; }
.voice-wave { display: flex; align-items: flex-end; gap: 2px; height: 16px; }
.voice-wave i { width: 3px; height: 5px; border-radius: 2px; background: var(--ck-heat); }
.inline-voice.playing .voice-wave i { animation: wave 0.9s ease-in-out infinite; }
.inline-voice.playing .voice-wave i:nth-child(2) { animation-delay: 0.15s; }
.inline-voice.playing .voice-wave i:nth-child(3) { animation-delay: 0.3s; }
.inline-voice.playing .voice-wave i:nth-child(4) { animation-delay: 0.45s; }
@keyframes wave { 0%, 100% { height: 5px; } 50% { height: 16px; } }
.voice-progress { height: 3px; margin-top: 10px; border-radius: 2px; background: rgba(255, 255, 255, 0.1); overflow: hidden; }
.voice-progress span { display: block; height: 100%; background: var(--ck-heat); }
.inline-voice-actions { display: flex; gap: 8px; margin-top: 10px; }
.inline-voice-actions button { display: inline-flex; flex: 1 1 0; align-items: center; justify-content: center; gap: 6px; min-height: 40px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill); color: var(--ck-text); font-size: 13px; font-weight: 600; }
.inline-voice-actions .voice-toggle { background: var(--ck-heat-soft); border-color: rgba(255, 138, 61, 0.35); color: #FFB27F; }

/* 奶油建议卡 */
.advice { display: flex; align-items: center; gap: 14px; width: 100%; padding: 16px; border: 0; text-align: left; box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35); }
.advice__icon { display: grid; place-items: center; width: 44px; height: 44px; flex: 0 0 44px; border-radius: 14px; background: #FFE3CC; color: #D2561A; }
.advice__body { display: flex; flex-direction: column; gap: 2px; min-width: 0; flex: 1 1 auto; }
.advice__body small { color: var(--ck-cream-text-2); font-size: 12px; }
.advice__body b { font-size: 16px; font-weight: 700; line-height: 1.45; }
.advice__body em { color: var(--ck-cream-text-2); font-size: 12.5px; font-style: normal; }
.advice__chev { color: var(--ck-cream-text-2); }
.advice.resume .ck-btn { min-height: 40px; padding: 0 16px; font-size: 14px; }

.chart-legend { display: flex; justify-content: center; gap: 18px; margin-top: 8px; color: var(--ck-text-3); font-size: 12px; }
.chart-legend span { display: inline-flex; align-items: center; gap: 6px; }
.chart-legend i { width: 16px; height: 0; border-top: 2px solid var(--ck-heat); }
.chart-legend i.dash { border-top: 2px dashed rgba(246, 243, 238, 0.55); }

.duo { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.mini { display: flex; align-items: flex-start; gap: 10px; padding: 14px; }
.mini__icon { color: var(--ck-heat); margin-top: 2px; }
.mini div { display: flex; flex-direction: column; min-width: 0; }
.mini small { color: var(--ck-text-3); font-size: 12px; }
.mini b { overflow: hidden; font-size: 15px; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; }
.mini em { color: var(--ck-text-3); font-size: 11.5px; font-style: normal; }

/* 步骤列表 */
.step-track { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.track-step { position: relative; display: flex; align-items: center; gap: 12px; min-height: 44px; color: var(--ck-text-3); font-size: 14px; }
.track-step:not(:last-child)::after { content: ''; position: absolute; top: 34px; bottom: -10px; left: 12px; width: 2px; background: rgba(255, 255, 255, 0.1); }
.track-step span { position: relative; z-index: 1; display: grid; place-items: center; width: 26px; height: 26px; flex: 0 0 26px; border: 1.5px solid rgba(255, 255, 255, 0.2); border-radius: 50%; background: #202422; font-size: 12px; font-variant-numeric: tabular-nums; }
.track-step b { font-weight: 500; }
.track-step.done { color: var(--ck-text-2); }
.track-step.done span { border-color: var(--ck-heat); background: var(--ck-heat); color: #fff; }
.track-step.done::after { background: var(--ck-heat); }
.track-step.active { color: var(--ck-text); }
.track-step.active span { border-color: var(--ck-heat); color: var(--ck-heat); box-shadow: 0 0 0 4px rgba(255, 138, 61, 0.2); }
.track-step.active b { font-weight: 700; }

/* 工具 */
.tools :deep(button) { font-family: inherit; }
.timer-controls { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 8px; margin: 12px 0; }
.timer-controls button, .text-btn, .diag-actions button { min-height: 40px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 13px; font-weight: 600; }
.timer-controls label { display: flex; flex-direction: column; gap: 4px; color: var(--ck-text-3); font-size: 12px; }
.timer-controls input { width: 110px; min-height: 40px; padding: 0 12px; border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 12px; background: rgba(255, 255, 255, 0.07); color: var(--ck-text); }
.text-btn { margin-bottom: 4px; }

/* 设备 */
.sense-card small.connected { color: var(--ck-fresh); }
.temperature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.temperature-grid > div { padding: 12px; border-radius: 14px; background: var(--ck-fill); }
.temperature-grid span { color: var(--ck-text-3); font-size: 12px; }
.temperature-grid strong { display: block; font-size: 28px; font-weight: 300; }
.temperature-grid strong small { font-size: 13px; }
.temperature-grid p { color: var(--ck-text-3); font-size: 11.5px; line-height: 1.5; }
.sense-note { margin: 10px 0 0; color: var(--ck-text-3); font-size: 12px; }
.sense-action { width: 100%; min-height: 46px; margin-top: 14px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: #fff; font-weight: 600; }
.sense-action.secondary { border: 1px solid var(--ck-glass-border); background: var(--ck-fill); color: var(--ck-text); }
.sense-action:disabled { opacity: 0.5; }

.support { display: flex; flex-direction: column; gap: 14px; }
.support-row { display: flex; gap: 12px; }
.support-row > .ck-icon { color: var(--ck-heat); margin-top: 2px; }
.support-row b { font-size: 14px; }
.support-row p { color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }

.device-diagnostics { padding: 0 16px; font-size: 13px; color: var(--ck-text-2); }
.device-diagnostics summary { display: flex; align-items: center; min-height: 48px; color: var(--ck-text-2); list-style: none; cursor: pointer; }
.device-diagnostics summary::-webkit-details-marker { display: none; }
.device-diagnostics summary::after { content: '›'; margin-left: auto; font-size: 20px; transition: transform 0.2s ease; }
.device-diagnostics[open] summary::after { transform: rotate(90deg); }
.device-diagnostics p { margin-bottom: 10px; line-height: 1.6; }
.diag-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }

/* 底部步骤切换 */
.step-switcher { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.6fr); gap: 10px; padding: 12px calc(var(--ck-gutter) + var(--sar)) calc(12px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); background: linear-gradient(180deg, rgba(13, 16, 15, 0), rgba(13, 16, 15, 0.92) 30%); }
.step-switcher button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 54px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: rgba(40, 44, 42, 0.9); color: var(--ck-text); font-size: 16px; font-weight: 600; -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px); }
.step-switcher button.next { border: 0; background: var(--ck-heat-deep); color: #fff; box-shadow: 0 10px 24px rgba(232, 100, 31, 0.35); }
.step-switcher button:disabled { opacity: 0.4; }

/* 待机：状态卡与对话 */
.gap { margin: 0; }
.chef-state-card { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 24px 18px; text-align: center; }
.chef-state-card h2 { font-size: 18px; font-weight: 600; }
.chef-state-card p { color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }
.chef-state-card .ck-btn { margin-top: 6px; }
.chef-state-card.is-error { border-color: rgba(255, 107, 91, 0.35); }
.empty-icon { display: grid; place-items: center; width: 60px; height: 60px; border-radius: 20px; background: var(--ck-heat-soft); color: var(--ck-heat); }
.latest-recipe { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 18px 16px; }
.latest-recipe small { color: #C4561C; font-size: 12px; font-weight: 700; }
.latest-recipe h2 { margin: 2px 0; font-size: 19px; font-weight: 700; }
.latest-recipe p { color: var(--ck-cream-text-2); font-size: 12.5px; }
.latest-recipe .ck-btn { flex: 0 0 auto; min-height: 44px; padding: 0 16px; font-size: 14px; }

.conversation-panel { margin-top: 4px; }
.chat-messages { display: flex; flex-direction: column; gap: 14px; }
.message-wrapper { display: flex; align-items: flex-start; gap: 10px; }
.message-wrapper.user { flex-direction: row-reverse; }
.avatar { display: grid; place-items: center; width: 32px; height: 32px; flex: 0 0 32px; border-radius: 50%; background: var(--ck-fill-strong); color: var(--ck-heat); }
.message-wrapper.user .avatar { background: rgba(255, 138, 61, 0.2); color: #FFB27F; }
.message-bubble { max-width: calc(100% - 44px); min-width: 0; }
.text-content { padding: 11px 14px; border: 1px solid var(--ck-glass-border); border-radius: 18px 18px 18px 6px; background: var(--ck-glass); color: var(--ck-text); font-size: 14.5px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.message-wrapper.user .text-content { border-color: transparent; border-radius: 18px 18px 6px 18px; background: var(--ck-heat-deep); color: #fff; }
.recipe-card { margin-top: 10px; padding: 16px; border: 1px solid var(--ck-glass-border); border-radius: 20px; background: var(--ck-glass-strong); -webkit-backdrop-filter: var(--ck-blur); backdrop-filter: var(--ck-blur); }
.recipe-header span { color: #FFB27F; font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.recipe-header h3 { margin: 2px 0 10px; font-size: 18px; font-weight: 700; }
.ing-grid { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.ing-grid span { display: inline-flex; gap: 5px; padding: 5px 10px; border-radius: 10px; background: var(--ck-fill); color: var(--ck-text-2); font-size: 12.5px; }
.ing-grid b { color: var(--ck-text); font-weight: 600; }
.steps-preview { display: flex; flex-direction: column; gap: 8px; }
.steps-preview p { display: flex; gap: 10px; color: var(--ck-text-2); font-size: 13.5px; line-height: 1.55; }
.steps-preview i { display: grid; place-items: center; width: 22px; height: 22px; flex: 0 0 22px; border-radius: 50%; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 12px; font-style: normal; }
.start-guide { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; min-height: 46px; margin-top: 14px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: #fff; font-size: 15px; font-weight: 600; }
.card-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 8px; }
.card-actions button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 42px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill); color: var(--ck-text); font-size: 13.5px; font-weight: 600; }

/* 输入栏：贴在底栏上方 */
.chat-input-bar-fixed { position: fixed; right: 0; bottom: calc(var(--ck-tabbar-height) + var(--sab)); left: 0; z-index: 40; padding: 10px calc(var(--ck-gutter) + var(--sar)) 10px calc(var(--ck-gutter) + var(--sal)); background: linear-gradient(180deg, rgba(13, 16, 15, 0), rgba(13, 16, 15, 0.9) 40%); }
.input-content { display: flex; align-items: center; gap: 8px; width: min(100%, calc(var(--ck-page-max) - 32px)); margin: 0 auto; padding: 5px 5px 5px 6px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: rgba(36, 40, 38, 0.92); -webkit-backdrop-filter: blur(20px); backdrop-filter: blur(20px); }
.input-content :deep(.el-input__wrapper) { min-height: 42px; background: transparent !important; box-shadow: none !important; }
.send-btn { display: grid; place-items: center; width: 42px; height: 42px; flex: 0 0 42px; padding: 0; border: 0; border-radius: 50%; background: var(--ck-heat-deep); color: #fff; }
.send-btn:disabled { opacity: 0.5; }

/* 蓝牙对话框 */
.bluetooth-scan-status { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--ck-text); }
.temperature-dialog-help { margin: 10px 0; color: var(--ck-text-3); font-size: 12.5px; line-height: 1.6; }
.bluetooth-device-list { display: flex; flex-direction: column; gap: 8px; max-height: 46vh; overflow-y: auto; }
.bluetooth-device-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 56px; padding: 10px 14px; border: 1px solid var(--ck-glass-border); border-radius: 14px; background: var(--ck-fill); color: var(--ck-text); text-align: left; }
.bluetooth-device-item span { display: flex; flex-direction: column; min-width: 0; }
.bluetooth-device-item small { color: var(--ck-text-3); font-size: 12px; }
.bluetooth-device-item em { flex: 0 0 auto; color: var(--ck-text-2); font-size: 12px; font-style: normal; }
.bluetooth-device-item.recommended em { color: var(--ck-fresh); }
.bluetooth-device-item.selected { border-color: var(--ck-heat); background: var(--ck-heat-soft); }
.bluetooth-debug { margin-top: 12px; color: var(--ck-text-2); font-size: 13px; }
.bluetooth-debug summary { min-height: 36px; cursor: pointer; }

@media (max-width: 360px) {
  .chef-top__brand h1 { font-size: 26px; }
  .step-description { font-size: 17px; }
  .recipe-head__img { width: 68px; height: 68px; flex-basis: 68px; }
  .duo { grid-template-columns: minmax(0, 1fr); }
}
</style>
