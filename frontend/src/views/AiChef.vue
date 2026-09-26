<template>
  <div :class="['ai-chef-container', `is-${viewMode}`, { 'ck-dark': darkPage, 'has-alert': liveState.alert?.level === 'danger' }]">
    <CkStageBackdrop v-if="darkPage" :state="stageState" :cooking="cookingView" />

    <!-- 完成核对与状态（三种模式共用） -->
    <div v-if="(session?.status === 'completed' && !completionVisible) || sessionMessage" class="chef-shared">

      <button v-if="session?.status === 'completed' && !completionVisible" type="button" class="ck-btn ck-btn--ghost ck-btn--block" @click="completionVisible=true">查看完成与库存核对</button>
      <p v-if="sessionMessage" role="status" class="inline-status">{{ sessionMessage }}</p>
    </div>

    <el-drawer v-model="completionVisible" title="完成与库存核对" direction="btt" size="86%" append-to-body>
      <CookingCompletion v-if="completionVisible && session" :key="session.id" :session="session" :engine="cookingStore.engine" @changed="completionChanged" @close="completionVisible=false" />
    </el-drawer>

    <!-- ================= 烹饪中 ================= -->
    <template v-if="cookingView">
      <header class="c-top">
        <button type="button" class="c-top__btn" aria-label="收起烹饪导航" @click="minimizeNavigation"><CkIcon name="chevron-left" :size="24" :stroke="2" /></button>
        <b>烹饪中</b>
        <button type="button" class="c-top__btn" aria-label="烹饪工具" :aria-expanded="toolsOpen" @click="toggleTools"><CkIcon name="more" :size="24" /></button>
      </header>

      <section class="c-recipe k-glass" @click="toggleTools">
        <div class="c-recipe__copy">
          <h2>{{ activeRecipe.dish_name || '当前菜谱' }}</h2>
          <span class="c-chip">CookX AI 实时引导</span>
          <span class="c-recipe__progress">第 {{ currentStepIdx + 1 }} / {{ activeSteps.length }} 步 · {{ overallProgress }}%</span>
        </div>
        <img v-if="activeRecipeImage" :src="activeRecipeImage" :alt="activeRecipe.dish_name" class="c-recipe__plate" />
        <span v-else class="c-recipe__plate is-empty"><CkIcon name="pot" :size="40" /></span>
      </section>

      <section :class="['c-gauge', { 'is-alert': liveState.alert?.level === 'danger' }]">
        <CkGauge :value="displayTemperature" :size="gaugeSize" label="当前温度" :sub="targetText" :alert="liveState.alert?.level === 'danger'">
          <button type="button" class="c-phase" @click="acknowledge">{{ phaseChip }}</button>
        </CkGauge>
        <div class="c-eta">
          <span>{{ eta.label }}</span>
          <b class="ck-num">{{ eta.value }}</b>
          <small>{{ eta.hint }}</small>
        </div>
      </section>

      <section class="k-glass k-card c-chart">
        <div class="k-card__title"><span>实时温度曲线</span><small v-if="thermalReplaying">仿真回放</small></div>
        <CkTempChart :points="chartPoints.length ? chartPoints : trendPoints" :prediction="chartPrediction" :band="chartBand" :height="80" empty-text="连接 CookX Sense 后显示实时曲线" />
        <div class="chart-legend"><span><i class="solid"></i>当前温度</span><span><i class="dash"></i>{{ chartPrediction.length ? '预测曲线' : '预测待有效数据' }}</span></div>
      </section>

      <div class="c-duo">
        <section class="k-glass c-mini">
          <span class="c-mini__icon is-flame"><CkIcon name="flame" :size="22" /></span>
          <div><small>下一阶段</small><b>{{ activeSteps[currentStepIdx + 1] ? getStepTitle(activeSteps[currentStepIdx + 1], currentStepIdx + 1) : '完成烹饪' }}</b><em>{{ hasStepTimer ? `预计 ${Math.max(0, timeLeft)} 秒后` : '按实际火候推进' }}</em></div>
        </section>
        <section class="k-glass c-mini">
          <span class="c-mini__icon"><CkIcon name="clock" :size="22" /></span>
          <div><small>本次烹饪预计</small><b>{{ totalEstimate }}</b><em>根据菜谱步骤时长估算</em></div>
        </section>
      </div>

      <article :class="['current-step-card', 'k-advice', { 'is-voice-active': isVoicePlaying }]" role="button" tabindex="0" aria-label="查看完整步骤与烹饪工具" @click="toggleTools" @keydown.enter="toggleTools" @keydown.space.prevent="toggleTools">
        <span class="k-advice__icon"><CkIcon name="chef" :size="24" /></span>
        <div class="k-advice__body">
          <div class="panel-heading">
            <span>CookX 建议 · 第 {{ currentStepIdx + 1 }} 步 <b>{{ currentStepTitle }}</b></span>

          </div>
          <p class="step-description">{{ currentStepText }}</p>


        </div>
      </article>

      <el-drawer v-model="toolsOpen" title="烹饪工具" direction="btt" size="86%" append-to-body class="ck-dark cooking-tools-drawer">
      <section ref="toolsPanel" class="c-tools">
        <p v-if="hasStepTimer" class="tool-timer">本步剩余 {{ formatTime(timeLeft) }}</p><p v-else class="tool-timer">时长未提供</p>
        <article class="k-advice"><div class="k-advice__body"><b>第 {{ currentStepIdx + 1 }} 步 · {{ currentStepTitle }}</b><p>{{ currentStepText }}</p><p v-if="currentStepTip">{{ currentStepTip }}</p>          <div :class="['inline-voice', voicePlaybackState]">
            <button type="button" class="voice-toggle" @click="toggleVoicePlayback"><span class="voice-wave" aria-hidden="true"><i></i><i></i><i></i></span>{{ voiceControlText }}</button>
            <button type="button" @click="replayCurrentStep"><CkIcon name="refresh" :size="15" />重新播报</button>
            <span class="voice-status">{{ inlineVoiceStatusText }}<template v-if="voiceProgressVisible"> · {{ voiceRemainingTime }}</template></span>
          </div>
          <p v-if="voiceMessage" role="status" class="k-advice__tip">{{ voiceMessage }}</p></div></article>
        <div class="k-glass k-card">
          <div class="k-card__title"><span>烹饪步骤</span><small>{{ overallProgress }}%</small></div>
          <ol class="step-track">
            <li v-for="(step, index) in activeSteps" :key="index" :class="['track-step', { done: index < currentStepIdx, active: index === currentStepIdx }]">
              <span><CkIcon v-if="index < currentStepIdx" name="check" :size="14" :stroke="2.4" /><template v-else>{{ index + 1 }}</template></span>
              <b>{{ getStepTitle(step, index) }}</b>
            </li>
          </ol>
        </div>
        <div class="k-glass k-card tools">
          <div class="k-card__title"><span>语音与计时</span></div>
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
        </div>
        <article :class="['sense-card', 'k-glass', 'k-card', temperatureLevel.className]">
          <div class="k-card__title"><span>CookX Sense 温度监控</span><small :class="{ connected: temperatureConnected }">{{ temperatureConnectionText }}</small></div>
          <div class="temperature-grid">
            <div><span>环境温度</span><strong class="ck-num">{{ ambientTemperature === null ? '--' : ambientTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ ambientTemperature === null ? '当前硬件固件未上传环境温度' : '设备环境温度' }}</p></div>
            <div><span>锅面温度</span><strong class="ck-num">{{ currentTemperature === null ? '--' : currentTemperature.toFixed(1) }}<small>°C</small></strong><p>{{ currentTemperature === null ? '等待数据' : temperatureLevel.status }}</p></div>
          </div>
          <p class="sense-note">{{ temperatureConnected ? `${currentTemperature === null ? '数据暂未更新' : '已收到设备读数，请结合测量质量查看'} · 最后更新 ${lastTemperatureTime}` : '尚未连接 CookX Sense，连接设备后可实时查看温度' }}</p>
          <TemperatureInsight :assessment="thermalAssessment" :history="thermalHistory" :prediction="thermalPrediction"
            :model-state="thermalModelState" :experimental="thermalExperimental" :replaying="thermalReplaying"
            :connected="temperatureConnected" :storage-message="thermalStorageMessage"
            @confirm="thermal.confirm($event)" @experimental="thermal.setExperimental($event)"
            @replay="thermal.startReplay()" @stop-replay="thermal.stopReplay()" @export="thermal.exportSession($event)" />
          <button v-if="!temperatureConnected" type="button" class="sense-action" :disabled="temperatureConnecting" @click="openTemperatureDialog">{{ temperatureConnecting ? '连接中…' : '连接测温设备' }}</button>
          <button v-else type="button" class="sense-action secondary" @click="disconnectTemperature">断开设备</button>
        </article>
        <section v-if="activeRecipe.ingredients_list?.length || recipeReminder || activeRecipe.nutrition" class="k-glass k-card support">
          <div v-if="activeRecipe.ingredients_list?.length" class="support-row"><CkIcon name="bag" :size="18" /><div><b>食材清单</b><p>{{ ingredientSummary }}</p></div></div>
          <div v-if="recipeReminder" class="support-row"><CkIcon name="bell" :size="18" /><div><b>CookX 提醒</b><p>{{ recipeReminder }}</p></div></div>
          <div v-if="activeRecipe.nutrition" class="support-row"><CkIcon name="chart" :size="18" /><div><b>营养信息</b><p>{{ nutritionSummary }}</p></div></div>
        </section>
        <details class="device-diagnostics k-glass"><summary>设备状态与诊断</summary><p>{{ temperatureConnectionText }}；恢复页面后等待新测量。后台连续采集能力待真机验证。</p><div class="diag-actions"><button type="button" @click="openTemperatureDialog">扫描与连接设备</button><button type="button" @click="refreshDevice">重新核对设备状态</button><button type="button" @click="exportDeviceLog">导出设备诊断</button></div><p role="status">{{ deviceDiagnosticMessage }}</p></details>
      </section>

      <div class="step-switcher">
        <button type="button" :disabled="currentStepIdx === 0" @click="prevStep"><CkIcon name="chevron-left" :size="18" />上一步</button>
        <button type="button" class="next" @click="nextStep">{{ isLastStep ? '完成烹饪' : '下一步' }}<CkIcon name="chevron-right" :size="18" /></button>
      </div>
      </el-drawer>
    </template>

    <!-- ================= AI 菜谱 ================= -->
    <template v-else-if="isRecipes">
      <header class="ck-head"><div class="ck-wordmark">Cook<b>X</b></div><button type="button" class="ck-icon-btn" aria-label="设备与连接" @click="openTemperatureDialog"><CkIcon name="sensor" :size="22" /></button></header>
      <div class="ck-title"><h1>AI 菜谱</h1><p>说出想吃的，CookX 结合冰箱与口味生成菜谱</p></div>
      <nav class="r-links" aria-label="菜谱入口">
        <button type="button" class="r-link" @click="router.push('/recipes')"><CkIcon name="book" :size="18" />菜谱库</button>
        <button type="button" class="r-link" @click="router.push('/favorites')"><CkIcon name="star" :size="18" />我的收藏</button>
        <button type="button" class="r-link" @click="router.push('/menus')"><CkIcon name="calendar" :size="18" />七日菜单</button>
      </nav>

      <section v-if="recipeError" class="r-state ck-card is-error" role="alert"><p>{{ recipeError }}</p><button type="button" class="ck-btn ck-btn--heat" @click="sendMessage(lastRecipePrompt)">重新生成菜谱</button></section>
      <section v-if="loading" class="r-state ck-card" v-loading="true"><h2>CookX 正在生成菜谱</h2><p>正在结合你的需求整理烹饪步骤…</p></section>
      <section v-else-if="latestRecipe" class="latest-recipe ck-card">
        <div><small>准备开始</small><h2>{{ latestRecipe.dish_name || '已生成菜谱' }}</h2><p>菜谱已准备好，可以开启语音步骤指导。</p></div>
        <button type="button" class="ck-btn ck-btn--heat" @click="startNavigation(latestRecipe)"><CkIcon name="mic" :size="18" />开始烹饪</button>
      </section>
      <section v-else class="r-empty">
        <p>试试这样问</p>
        <div class="r-chips">
          <button v-for="idea in promptIdeas" :key="idea" type="button" @click="sendMessage(idea)">{{ idea }}</button>
        </div>
      </section>

      <section class="conversation-panel">
        <div ref="chatBox" class="chat-messages">
          <div v-for="(msg, index) in messages" :key="index" :class="['message-wrapper', msg.role]">
            <span class="avatar"><CkIcon :name="msg.role === 'user' ? 'user' : 'chef'" :size="18" /></span>
            <div class="message-bubble">
              <div class="text-content">{{ msg.content }}</div>
              <article v-if="msg.recipe?.steps" class="recipe-card">
                <div class="recipe-header"><span>COOKX RECIPE</span><h3>{{ msg.recipe.dish_name || '美味教程' }}</h3></div>
                <div v-if="msg.recipe.ingredients_list?.length" class="ing-grid"><span v-for="(ing, i) in msg.recipe.ingredients_list" :key="i"><b>{{ ing.item }}</b>{{ ing.amount }}</span></div>
                <div class="steps-preview"><p v-for="(step, sIdx) in msg.recipe.steps.slice(0, 3)" :key="sIdx"><i>{{ sIdx + 1 }}</i>{{ getStepText(step) }}</p></div>
                <button type="button" class="start-guide" @click="startNavigation(msg.recipe)"><CkIcon name="mic" :size="16" />开始指导</button>
                <div class="card-actions"><button type="button" @click="goToMarket"><CkIcon name="pin" :size="16" />买食材</button><button type="button" @click="orderDelivery(msg.recipe.dish_name)"><CkIcon name="bike" :size="16" />点外卖</button></div>
              </article>
            </div>
          </div>
        </div>
      </section>

      <div class="chat-input-bar-fixed">
        <div class="input-content">
          <el-input ref="recipeInput" v-model="userInput" placeholder="告诉 CookX 你想做什么…" @keyup.enter="sendMessage()" />
          <button type="button" class="send-btn" aria-label="发送" :disabled="loading" @click="sendMessage()"><CkIcon name="send" :size="20" /></button>
        </div>
      </div>
    </template>

    <!-- ================= 实时厨房 ================= -->
    <template v-else>
      <header class="k-head">
        <div class="ck-wordmark">Cook<b>X</b></div>
        <button type="button" class="ck-icon-btn" aria-label="查看消息" @click="router.push('/profile')"><CkIcon name="bell" :size="24" /><span class="dot"></span></button>
      </header>
      <div class="k-title">
        <div><h1>实时厨房</h1><p>好食材 · 更好味</p></div>
        <button type="button" class="k-sense" @click="openTemperatureDialog">
          <span :class="['ck-dot', { 'is-on': temperatureConnected || thermalReplaying || liveState.simulated }]"></span>
          <span><b>CookX Sense</b><small>{{ (thermalReplaying || liveState.simulated) && !temperatureConnected ? '仿真回放' : temperatureConnectionText }}</small></span>
        </button>
      </div>

      <section :class="['k-hero', { 'is-alert': liveState.alert?.level === 'danger', 'is-warn': liveState.alert?.level === 'warn' }]">
        <span class="k-hero__label">当前锅温</span>
        <div class="k-hero__temp ck-num"><span :class="{ 'is-empty': displayTemperature === null }">{{ displayTemperature === null ? '--' : Math.round(displayTemperature) }}</span><sup v-if="displayTemperature !== null">°C</sup></div>
        <button type="button" class="k-pill" @click="liveState.alert ? acknowledge() : openTemperatureDialog()">
          <CkIcon :name="liveState.alert ? 'info' : 'flame'" :size="20" />
          <span><b>{{ pill.title }}</b><small>{{ pill.text }}</small></span>
        </button>
      </section>

      <div class="k-pan-space" aria-hidden="true"></div>
      <section class="k-glass k-card k-stages" aria-label="烹饪阶段">
        <div class="k-card__title"><span>烹饪阶段</span></div>
        <ol class="stepper">
          <li v-for="(item, index) in stageLabels" :key="item" :class="{ done: index < stageIndex, current: index === stageIndex }"><i></i><span>{{ item }}</span></li>
        </ol>
      </section>

      <button type="button" class="k-glass k-card k-trend" @click="trendOpen = true">
        <div class="k-card__title"><span>温度趋势</span><CkIcon name="chevron-right" :size="18" /></div>
        <CkTempChart :points="trendPoints" :height="62" empty-text="连接 CookX Sense 后显示实时曲线" />
      </button>

      <button type="button" :aria-label="session?.status === 'active' ? '恢复烹饪' : '查看 CookX 建议与菜谱'" class="k-advice k-live-advice" :class="{ 'is-alert': liveState.alert?.level === 'danger' }" @click="session?.status === 'active' ? restoreCooking() : router.push({ path: '/home', query: { tab: 'Recipes' } })">
        <span class="k-advice__icon"><CkIcon :name="liveState.alert ? 'info' : 'chef'" :size="24" /></span>
        <div class="k-advice__body">
          <small>CookX 建议</small>
          <b>{{ kitchenAdvice.text }}</b>
        </div>
        <CkIcon name="chevron-right" :size="20" class="k-advice__chev" />
      </button>
      <p v-if="recipeError" class="inline-status" role="alert">{{ recipeError }}</p>
    </template>

    <el-drawer v-model="trendOpen" direction="btt" size="72%" title="温度详情" class="trend-drawer" append-to-body>
      <div class="trend-sheet">
        <CkTempChart :points="chartPoints.length ? chartPoints : trendPoints" :prediction="chartPrediction" :band="chartBand" :height="180" />
        <TemperatureInsight v-if="!cookingView" :assessment="thermalAssessment" :history="thermalHistory" :prediction="thermalPrediction"
          :model-state="thermalModelState" :experimental="thermalExperimental" :replaying="thermalReplaying"
          :connected="temperatureConnected" :storage-message="thermalStorageMessage"
          @confirm="thermal.confirm($event)" @experimental="thermal.setExperimental($event)"
          @replay="thermal.startReplay()" @stop-replay="thermal.stopReplay()" @export="thermal.exportSession($event)" />
      </div>
    </el-drawer>

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
      <details v-if="!cookingView" class="device-diagnostics"><summary>设备状态与诊断</summary><p>{{ temperatureConnectionText }}；恢复页面后等待新测量。后台连续采集能力待真机验证。</p><div class="diag-actions"><button type="button" @click="refreshDevice">重新核对设备状态</button><button type="button" @click="exportDeviceLog">导出设备诊断</button><button v-if="temperatureConnected" type="button" @click="disconnectTemperature">断开设备</button></div><p role="status">{{ deviceDiagnosticMessage }}</p></details>
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
import CkStageBackdrop from '@/components/ck/CkStageBackdrop.vue'
import { live, liveAdvice, heatStage, stageCopy, backdropState, targetEta, formatClock, publishKitchen, acknowledgeAlert, refreshSession as refreshLiveSession } from '@/services/liveKitchen.js'
import { setForcedDarkPage } from '@/services/theme.js'
import { useRouter } from 'vue-router'
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
const props = defineProps(['pendingDish', 'mode'])
const router = useRouter()
const liveState = live
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
    if(isRecipes.value)router.replace({path:'/home',query:{tab:'AiChef'}})
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
const isRecipes = computed(() => props.mode === 'Recipes')
const viewMode = computed(() => (cookingView.value ? 'cooking' : isRecipes.value ? 'recipes' : 'live'))
const darkPage = computed(() => viewMode.value !== 'recipes')
const hasStepTimer = computed(() => Boolean(currentStepDuration.value || session.value?.timers[currentStepIdx.value]?.round > 0))
watch(cookingView, value => { uiState.immersive = value; refreshLiveSession(); if (value) window.scrollTo({ top: 0 }) }, { immediate: true })
watch(darkPage, value => setForcedDarkPage(value), { immediate: true })
onUnmounted(() => { uiState.immersive = false; setForcedDarkPage(false); publishKitchen({ assessment: null }) })
watch(completionVisible, visible => { if (visible) { toolsOpen.value = false; window.scrollTo({ top: 0, behavior: 'smooth' }) } })
const minimizeNavigation = () => { stopNavigation(); navigationVisible.value = false; toolsOpen.value = false }
const gaugeSize = 260

// 显示温度：设备实测优先；仿真回放时使用回放读数（界面会标注「仿真回放」）
const displayTemperature = computed(() => {
  if (currentTemperature.value !== null) return currentTemperature.value
  if (thermalReplaying.value && Number.isFinite(thermalAssessment.value?.temperature)) return thermalAssessment.value.temperature
  return live.temperature
})
watch([displayTemperature, thermalAssessment, thermalReplaying], () => {
  const simulated = thermalReplaying.value && currentTemperature.value === null
  const replayValue = simulated && Number.isFinite(thermalAssessment.value?.temperature) ? thermalAssessment.value.temperature : undefined
  publishKitchen({ temperature: replayValue, simulated, assessment: Number.isFinite(thermalAssessment.value?.temperature) ? thermalAssessment.value : null })
})

const stageState = computed(() => backdropState(displayTemperature.value, live.alert))
const stageLabels = ['预热', '升温', '煎香', '烹饪', '完成']
const stageIndex = computed(() => (session.value?.status === 'completed' ? 4 : heatStage(displayTemperature.value)?.index ?? -1))
const pill = computed(() => {
  if (live.alert) return { title: live.alert.title, text: `${live.alert.text}，点按静音 60 秒` }
  const stage = heatStage(displayTemperature.value)
  if (stage) { const [title, text] = stageCopy(stage.id); return { title, text } }
  if (temperatureConnected.value) return { title: '等待温度数据', text: '保持探头贴近锅面' }
  return { title: '未连接测温设备', text: live.supported ? '点按扫描并连接 CookX Sense' : '在 Android App 中连接 CookX Sense' }
})
const kitchenAdvice = computed(() => {
  if (live.alert) return { text: `${live.alert.title}：${live.alert.text}` }
  if (session.value?.status === 'active') return { text: `继续烹饪「${activeRecipe.value.dish_name || '当前菜谱'}」，进行到第 ${currentStepIdx.value + 1} 步` }
  const advice = liveAdvice()
  if (advice) return { text: advice.text }
  return { text: '连接 CookX Sense，或先选一道菜开始烹饪' }
})
const trendPoints = computed(() => live.history.map(p => ({ at: p.at, t: p.t })))
const acknowledge = () => { if (!live.alert) return; acknowledgeAlert(); ElMessage.success('已静音 60 秒，请及时调小火力') }

const phaseChip = computed(() => {
  if (live.alert) return live.alert.title
  if (displayTemperature.value === null) return '等待数据'
  const stage = heatStage(displayTemperature.value)
  return temperatureLevel.value.status || (stage ? `${stage.label}阶段` : '温度监测中')
})
const targetText = computed(() => {
  if (chartBand.value) return `目标 ${chartBand.value[0]}–${chartBand.value[1]}°C`
  return thermalReplaying.value && !temperatureConnected.value ? '仿真回放' : temperatureConnectionText.value
})
const eta = computed(() => {
  const target = chartBand.value
  const seconds = targetEta(displayTemperature.value, target)
  if (target && displayTemperature.value !== null && displayTemperature.value < target[0]) {
    return { label: '预计还需', value: formatClock(seconds), hint: seconds === null ? '正在估算升温速度' : '即将达到食材下锅温度' }
  }
  if (hasStepTimer.value) return { label: '本步剩余', value: formatTime(timeLeft.value), hint: target && displayTemperature.value !== null ? '已在目标温区，按菜谱计时' : '按菜谱时长计时' }
  return { label: '预计还需', value: '--:--', hint: displayTemperature.value === null ? '等待温度数据' : '本步未设定时长' }
})
const totalEstimate = computed(() => {
  const total = recipeTotalSeconds.value
  if (!total) return '时长未知'
  const low = Math.max(1, Math.round((total * 0.9) / 60)), high = Math.max(low + 1, Math.ceil((total * 1.2) / 60))
  return `${low}-${high} 分钟`
})
const toolsOpen = ref(false)
const toolsPanel = ref(null)
const toggleTools = async () => {
  toolsOpen.value = !toolsOpen.value

}
const trendOpen = ref(false)
const promptIdeas = ['用冰箱里的食材做晚餐', '15 分钟快手菜', '番茄炒蛋怎么做']

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
.ai-chef-container { position: relative; z-index: 1; width: min(100%, var(--ck-page-max)); min-height: calc(100dvh - var(--ck-bottom-inset, 0px)); margin: 0 auto; padding: var(--sat) calc(var(--ck-gutter) + 4px + var(--sar)) 20px calc(var(--ck-gutter) + 4px + var(--sal)); color: var(--ck-text); }
.ai-chef-container.is-cooking { padding-bottom: calc(100px + var(--sab)); }
.ai-chef-container.is-recipes { padding-bottom: calc(96px + var(--sab)); }
button { font: inherit; }
.inline-status { margin: 8px 0; color: var(--ck-text-2); font-size: 13px; }
.chef-shared { display: flex; flex-direction: column; gap: 10px; padding-top: 12px; }

/* ---------- 深色玻璃（图三） ---------- */
.k-glass {
  border: 1px solid var(--ck-photo-tone-01);
  border-radius: 22px;
  background: linear-gradient(160deg, var(--ck-photo-tone-02), var(--ck-photo-tone-03));
  box-shadow: inset 0 1px 0 var(--ck-photo-tone-04);
  -webkit-backdrop-filter: saturate(140%) blur(24px);
  backdrop-filter: saturate(140%) blur(24px);
}
.k-card { width: 100%; margin-bottom: 12px; padding: 16px 18px; color: var(--ck-text); text-align: left; }
.k-card__title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-size: 17px; font-weight: 700; }
.k-card__title small { color: var(--ck-text-3); font-size: 12px; font-weight: 400; }
.k-card__title small.connected { color: var(--ck-photo-tone-05); }

/* ---------- 实时厨房（图三左） ---------- */
.k-head { display: flex; align-items: center; justify-content: space-between; height: 58px; }
.k-head .ck-wordmark { color: var(--ck-on-accent); font-size: 30px; }
.k-head .ck-icon-btn { color: var(--ck-on-accent); }
.k-head .ck-icon-btn .dot { border-color: transparent; }
.k-title { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.k-title h1 { color: var(--ck-on-accent); font-size: 31px; font-weight: 800; letter-spacing: 0.5px; line-height: 1.25; }
.k-title p { margin-top: 2px; color: var(--ck-photo-tone-06); font-size: 16px; letter-spacing: 1px; }
.k-sense { display: flex; align-items: center; gap: 10px; min-height: 52px; padding: 8px 16px 8px 14px; border: 1px solid var(--ck-photo-tone-07); border-radius: 16px; background: var(--ck-photo-tone-08); color: var(--ck-on-accent); text-align: left; -webkit-backdrop-filter: blur(20px); backdrop-filter: blur(20px); }
.k-sense .ck-dot { width: 11px; height: 11px; }
.k-sense .ck-dot.is-on { background: var(--ck-photo-tone-09); box-shadow: 0 0 0 4px var(--ck-photo-tone-10), 0 0 12px var(--ck-photo-tone-11); }
.k-sense span:last-child { display: flex; flex-direction: column; line-height: 1.3; }
.k-sense b { font-size: 14px; font-weight: 500; }
.k-sense small { color: var(--ck-photo-tone-12); font-size: 12.5px; }

.k-hero { display: flex; flex-direction: column; align-items: center; padding: clamp(6px, 2.4vh, 28px) 0 clamp(18px, 6.5vh, 72px); text-align: center; }
.k-hero__label { color: var(--ck-photo-tone-13); font-size: 17px; letter-spacing: 1px; text-shadow: 0 2px 12px var(--ck-photo-tone-14); }
.k-hero__temp { display: flex; align-items: flex-start; margin-top: -4px; color: var(--ck-on-accent); font-size: clamp(96px, 29vw, 128px); font-weight: 600; line-height: 1; letter-spacing: -3px; text-shadow: 0 6px 30px var(--ck-photo-tone-15); transition: color 0.5s ease; }
.k-hero__temp sup { margin: 0.16em 0 0 6px; font-size: 0.3em; font-weight: 500; letter-spacing: 0; }
.k-hero__temp .is-empty { color: var(--ck-photo-tone-16); font-family: var(--ck-font); font-size: 0.62em; font-weight: 200; letter-spacing: 0.12em; }
.k-hero.is-warn .k-hero__temp { color: var(--ck-photo-tone-17); }
.k-hero.is-alert .k-hero__temp { color: var(--ck-photo-tone-18); text-shadow: 0 0 40px var(--ck-photo-tone-19); animation: temp-alert 1.2s ease-in-out infinite; }
@keyframes temp-alert { 0%, 100% { transform: scale(1); } 50% { transform: scale(1.03); } }
.k-pill { display: flex; align-items: center; gap: 12px; margin-top: 8px; padding: 12px 30px 12px 24px; border: 1px solid var(--ck-photo-tone-20); border-radius: 30px; background: linear-gradient(135deg, var(--ck-photo-tone-21), var(--ck-photo-tone-22)); color: var(--ck-on-accent); text-align: left; box-shadow: 0 10px 30px var(--ck-photo-tone-23); -webkit-backdrop-filter: blur(18px); backdrop-filter: blur(18px); }
.k-pill > .ck-icon { color: var(--ck-photo-tone-24); }
.k-pill span { display: flex; flex-direction: column; align-items: center; line-height: 1.35; }
.k-pill b { font-size: 19px; font-weight: 700; }
.k-pill small { color: var(--ck-photo-tone-25); font-size: 14px; }
.k-hero.is-alert .k-pill { border-color: var(--ck-photo-tone-26); background: linear-gradient(135deg, var(--ck-photo-tone-27), var(--ck-photo-tone-28)); }
.k-hero.is-alert .k-pill > .ck-icon { color: var(--ck-photo-tone-29); }

.stepper { position: relative; display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); margin: 2px 0 0; padding: 0; list-style: none; }
.stepper li { position: relative; display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--ck-photo-tone-12); font-size: 13.5px; text-align: center; }
.stepper li::before { content: ''; position: absolute; top: 9px; right: 50%; left: -50%; height: 2px; background: var(--ck-photo-tone-07); }
.stepper li:first-child::before { display: none; }
.stepper li.done::before, .stepper li.current::before { background: var(--ck-photo-tone-30); }
.stepper i { position: relative; z-index: 1; width: 20px; height: 20px; border: 2px solid var(--ck-photo-tone-31); border-radius: 50%; background: var(--ck-photo-tone-32); }
.stepper li.done i { border-color: var(--ck-photo-tone-30); background: var(--ck-photo-tone-30); }
.stepper li.current i { border: 3px solid var(--ck-photo-tone-33); background: var(--ck-photo-tone-34); box-shadow: 0 0 0 4px var(--ck-photo-tone-35), 0 0 14px var(--ck-photo-tone-36); }
.stepper li.current { color: var(--ck-on-accent); font-weight: 700; }
.k-trend { display: block; border: 1px solid var(--ck-photo-tone-01); }
.k-trend:focus-visible { outline: 2px solid var(--ck-photo-tone-37); outline-offset: -3px; }
.k-trend .k-card__title .ck-icon { color: var(--ck-photo-tone-38); }

.k-advice { display: flex; align-items: center; gap: 14px; width: 100%; margin-bottom: 12px; padding: 18px 16px 18px 18px; border-radius: 22px; background: var(--ck-cream); color: var(--ck-cream-text); text-align: left; box-shadow: 0 18px 40px var(--ck-photo-tone-39); }
.k-advice__icon { display: grid; place-items: center; width: 50px; height: 50px; flex: 0 0 50px; border-radius: 50%; background: var(--ck-photo-tone-40); color: var(--ck-photo-tone-41); }
.k-advice__body { display: flex; flex-direction: column; gap: 3px; min-width: 0; flex: 1 1 auto; }
.k-advice__body > small { color: var(--ck-cream-text-2); font-size: 14px; }
.k-advice__body > b { font-size: 18px; font-weight: 700; line-height: 1.45; }
.k-advice__action { align-self: flex-start; min-height: 38px !important; margin-top: 6px; padding: 0 16px !important; font-size: 14px !important; }
.k-advice__chev { color: var(--ck-cream-text-2); flex: 0 0 auto; }
.k-advice.is-alert { background: var(--ck-photo-tone-42); }
.k-advice.is-alert .k-advice__icon { background: var(--ck-photo-tone-43); color: var(--ck-photo-tone-44); }
.k-advice__tip { color: var(--ck-cream-text-2); font-size: 13px; line-height: 1.55; }

/* ---------- 烹饪中（图三右） ---------- */
.c-top { display: grid; grid-template-columns: 44px minmax(0, 1fr) 44px; align-items: center; height: 56px; color: var(--ck-on-accent); }
.c-top b { font-size: 19px; font-weight: 700; text-align: center; }
.c-top__btn { display: grid; place-items: center; width: 44px; height: 44px; padding: 0; border: 0; background: none; color: var(--ck-on-accent); }
.c-recipe { position: relative; display: flex; align-items: flex-start; min-height: 120px; margin-top: 6px; overflow: hidden; padding: 18px 18px 16px; cursor: pointer; }
.c-recipe__copy { position: relative; z-index: 1; display: flex; flex-direction: column; align-items: flex-start; gap: 10px; max-width: 64%; }
.c-recipe h2 { color: var(--ck-on-accent); font-size: 25px; font-weight: 800; line-height: 1.25; }
.c-chip { padding: 4px 13px; border: 1px solid var(--ck-photo-tone-45); border-radius: 999px; background: var(--ck-photo-tone-46); color: var(--ck-photo-tone-47); font-size: 13px; }
.c-recipe__progress { color: var(--ck-photo-tone-48); font-size: 12.5px; }
.c-recipe__plate { position: absolute; top: -18px; right: -22px; width: 150px; height: 150px; border-radius: 50%; object-fit: cover; box-shadow: 0 16px 36px var(--ck-photo-tone-15); }
.c-recipe__plate.is-empty { display: grid; place-items: center; background: radial-gradient(circle at 40% 35%, var(--ck-on-accent), var(--ck-photo-tone-49)); color: var(--ck-photo-tone-50); }
.c-gauge { display: flex; flex-direction: column; align-items: center; margin: -6px 0 14px; }
.c-phase { margin-top: 12px; padding: 8px 26px; border: 1px solid var(--ck-photo-tone-51); border-radius: 999px; background: linear-gradient(135deg, var(--ck-photo-tone-52), var(--ck-photo-tone-53)); color: var(--ck-on-accent); font-size: 17px; font-weight: 700; box-shadow: 0 8px 22px var(--ck-photo-tone-54); }
.c-gauge.is-alert .c-phase { border-color: var(--ck-photo-tone-55); background: linear-gradient(135deg, var(--ck-photo-tone-56), var(--ck-photo-tone-57)); }
.c-eta { display: flex; flex-direction: column; align-items: center; margin-top: 4px; color: var(--ck-on-accent); text-align: center; }
.c-eta span { color: var(--ck-photo-tone-06); font-size: 16px; }
.c-eta b { font-size: 50px; font-weight: 600; line-height: 1.1; letter-spacing: -1px; }
.c-eta small { color: var(--ck-photo-tone-58); font-size: 15px; }
.chart-legend { display: flex; justify-content: center; gap: 22px; margin-top: 8px; color: var(--ck-photo-tone-12); font-size: 12.5px; }
.chart-legend span { display: inline-flex; align-items: center; gap: 6px; }
.chart-legend i.solid { width: 9px; height: 9px; border-radius: 50%; background: var(--ck-chart-end); }
.chart-legend i.dash { width: 18px; border-top: 2px dashed var(--ck-photo-tone-59); }
.c-duo { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.c-mini { display: flex; align-items: flex-start; gap: 10px; padding: 14px; }
.c-mini__icon { display: grid; place-items: center; width: 40px; height: 40px; flex: 0 0 40px; border-radius: 50%; background: var(--ck-photo-tone-60); color: var(--ck-on-accent); }
.c-mini__icon.is-flame { background: var(--ck-photo-tone-61); color: var(--ck-photo-tone-62); }
.c-mini div { display: flex; flex-direction: column; min-width: 0; }
.c-mini small { color: var(--ck-photo-tone-12); font-size: 12.5px; }
.c-mini b { overflow: hidden; color: var(--ck-on-accent); font-size: 17px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.c-mini em { overflow: hidden; color: var(--ck-photo-tone-59); font-size: 12px; font-style: normal; text-overflow: ellipsis; white-space: nowrap; }
.current-step-card.is-voice-active { box-shadow: 0 0 0 2px var(--ck-photo-tone-63), 0 18px 40px var(--ck-photo-tone-39); }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: var(--ck-cream-text-2); font-size: 13.5px; }
.panel-heading > span:first-child { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.step-remaining { flex: 0 0 auto; padding: 2px 10px; border-radius: 999px; background: var(--ck-photo-tone-40); color: var(--ck-photo-tone-64); font-size: 12.5px; font-variant-numeric: tabular-nums; }
.step-remaining.is-muted { background: var(--ck-photo-tone-65); color: var(--ck-cream-text-2); }
.step-description { margin: 4px 0 2px; color: var(--ck-cream-text); font-size: 17.5px; font-weight: 700; line-height: 1.5; }
.inline-voice { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 8px; }
.inline-voice button { display: inline-flex; align-items: center; gap: 5px; min-height: 34px; padding: 0 12px; border: 1px solid var(--ck-photo-tone-66); border-radius: 999px; background: var(--ck-on-accent); color: var(--ck-photo-tone-67); font-size: 13px; font-weight: 600; }
.inline-voice .voice-toggle { border-color: var(--ck-photo-tone-68); background: var(--ck-photo-tone-69); color: var(--ck-photo-tone-64); }
.voice-status { color: var(--ck-cream-text-2); font-size: 12px; }
.voice-wave { display: inline-flex; align-items: flex-end; gap: 2px; height: 12px; }
.voice-wave i { width: 2.5px; height: 4px; border-radius: 2px; background: currentColor; }
.inline-voice.playing .voice-wave i { animation: wave 0.9s ease-in-out infinite; }
.inline-voice.playing .voice-wave i:nth-child(2) { animation-delay: 0.15s; }
.inline-voice.playing .voice-wave i:nth-child(3) { animation-delay: 0.3s; }
@keyframes wave { 0%, 100% { height: 4px; } 50% { height: 12px; } }
.c-more { display: block; width: 100%; min-height: 44px; margin-bottom: 12px; border: 1px dashed var(--ck-photo-tone-70); border-radius: 16px; background: var(--ck-photo-tone-71); color: var(--ck-photo-tone-06); font-size: 14px; }
.c-tools { scroll-margin-top: calc(var(--sat) + 12px); }

.step-track { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.track-step { position: relative; display: flex; align-items: center; gap: 12px; min-height: 42px; color: var(--ck-photo-tone-72); font-size: 14px; }
.track-step:not(:last-child)::after { content: ''; position: absolute; top: 32px; bottom: -10px; left: 12px; width: 2px; background: var(--ck-photo-tone-73); }
.track-step span { position: relative; z-index: 1; display: grid; place-items: center; width: 26px; height: 26px; flex: 0 0 26px; border: 1.5px solid var(--ck-photo-tone-70); border-radius: 50%; background: var(--ck-photo-tone-74); font-size: 12px; }
.track-step.done span { border-color: var(--ck-chart-end); background: var(--ck-chart-end); color: var(--ck-on-accent); }
.track-step.done::after { background: var(--ck-chart-end); }
.track-step.active { color: var(--ck-on-accent); }
.track-step.active span { border-color: var(--ck-chart-end); color: var(--ck-chart-end); box-shadow: 0 0 0 4px var(--ck-photo-tone-75); }
.track-step.active b { font-weight: 700; }
.timer-controls { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 8px; margin: 12px 0; }
.timer-controls button, .text-btn, .diag-actions button { min-height: 40px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 13px; font-weight: 600; }
.timer-controls label { display: flex; flex-direction: column; gap: 4px; color: var(--ck-text-3); font-size: 12px; }
.timer-controls input { width: 110px; min-height: 40px; padding: 0 12px; border: 1px solid var(--ck-input-border); border-radius: 12px; background: var(--ck-input-bg); color: var(--ck-text); }
.temperature-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.temperature-grid > div { padding: 12px; border-radius: 14px; background: var(--ck-fill); }
.temperature-grid span { color: var(--ck-text-3); font-size: 12px; }
.temperature-grid strong { display: block; font-size: 28px; font-weight: 500; }
.temperature-grid strong small { font-size: 13px; }
.temperature-grid p { color: var(--ck-text-3); font-size: 11.5px; line-height: 1.5; }
.sense-note { margin: 10px 0 0; color: var(--ck-text-3); font-size: 12px; }
.sense-action { width: 100%; min-height: 46px; margin-top: 14px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: var(--ck-on-accent); font-weight: 600; }
.sense-action.secondary { border: 1px solid var(--ck-glass-border); background: var(--ck-fill); color: var(--ck-text); }
.support { display: flex; flex-direction: column; gap: 14px; }
.support-row { display: flex; gap: 12px; }
.support-row > .ck-icon { margin-top: 2px; color: var(--ck-heat); }
.support-row p { color: var(--ck-text-2); font-size: 13px; line-height: 1.6; }
.device-diagnostics { margin-bottom: 12px; padding: 0 16px; color: var(--ck-text-2); font-size: 13px; }
.device-diagnostics summary { display: flex; align-items: center; min-height: 48px; list-style: none; cursor: pointer; }
.device-diagnostics summary::-webkit-details-marker { display: none; }
.device-diagnostics summary::after { content: '›'; margin-left: auto; font-size: 20px; transition: transform 0.2s ease; }
.device-diagnostics[open] summary::after { transform: rotate(90deg); }
.device-diagnostics p { margin-bottom: 10px; line-height: 1.6; }
.diag-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 10px; }
.step-switcher { position: fixed; right: 0; bottom: 0; left: 0; z-index: 30; display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1.6fr); gap: 10px; padding: 14px calc(var(--ck-gutter) + var(--sar)) calc(12px + var(--sab)) calc(var(--ck-gutter) + var(--sal)); background: linear-gradient(180deg, var(--ck-photo-tone-76), var(--ck-photo-tone-77) 35%); }
.step-switcher button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 54px; border: 1px solid var(--ck-photo-tone-78); border-radius: 999px; background: var(--ck-photo-tone-79); color: var(--ck-on-accent); font-size: 16px; font-weight: 600; -webkit-backdrop-filter: blur(16px); backdrop-filter: blur(16px); }
.step-switcher button.next { border: 0; background: linear-gradient(135deg, var(--ck-photo-tone-80), var(--ck-photo-tone-81)); box-shadow: 0 10px 24px var(--ck-photo-tone-82); }
.step-switcher button:disabled { opacity: 0.4; }

/* ---------- AI 菜谱（随主题） ---------- */
.r-links { display: flex; gap: 8px; margin: 16px calc(-1 * var(--ck-gutter)) 14px; padding: 0 var(--ck-gutter); overflow-x: auto; scrollbar-width: none; }
.r-links::-webkit-scrollbar { display: none; }
.r-link { display: inline-flex; flex: 0 0 auto; align-items: center; gap: 6px; min-height: 40px; padding: 0 16px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-surface); color: var(--ck-text); font-size: 14px; font-weight: 600; box-shadow: var(--ck-shadow); }
.r-link .ck-icon { color: var(--ck-heat); }
.r-state { display: flex; flex-direction: column; align-items: center; gap: 8px; margin-bottom: 12px; padding: 22px 18px; text-align: center; }
.r-state h2 { font-size: 17px; }
.r-state p { color: var(--ck-text-2); font-size: 13px; }
.r-state.is-error { border-color: var(--ck-danger-soft); }
.r-empty { margin-bottom: 12px; }
.r-empty p { margin-bottom: 8px; color: var(--ck-text-3); font-size: 13px; }
.r-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.r-chips button { min-height: 38px; padding: 0 14px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-heat-soft); color: var(--ck-heat-text); font-size: 13.5px; font-weight: 600; }
.latest-recipe { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 12px; padding: 18px 16px; }
.latest-recipe small { color: var(--ck-heat-text); font-size: 12px; font-weight: 700; }
.latest-recipe h2 { margin: 2px 0; font-size: 19px; font-weight: 700; }
.latest-recipe p { color: var(--ck-text-2); font-size: 12.5px; }
.latest-recipe .ck-btn { flex: 0 0 auto; min-height: 44px; padding: 0 16px; font-size: 14px; }
.chat-messages { display: flex; flex-direction: column; gap: 14px; }
.message-wrapper { display: flex; align-items: flex-start; gap: 10px; }
.message-wrapper.user { flex-direction: row-reverse; }
.avatar { display: grid; place-items: center; width: 32px; height: 32px; flex: 0 0 32px; border-radius: 50%; background: var(--ck-heat-soft); color: var(--ck-heat-text); }
.message-wrapper.user .avatar { background: var(--ck-fill-strong); color: var(--ck-text-2); }
.message-bubble { max-width: calc(100% - 44px); min-width: 0; }
.text-content { padding: 11px 14px; border: 1px solid var(--ck-glass-border); border-radius: 18px 18px 18px 6px; background: var(--ck-surface); color: var(--ck-text); font-size: 15px; line-height: 1.6; white-space: pre-wrap; overflow-wrap: anywhere; box-shadow: var(--ck-shadow); }
.message-wrapper.user .text-content { border-color: transparent; border-radius: 18px 18px 6px 18px; background: var(--ck-heat-deep); color: var(--ck-on-accent); }
.recipe-card { margin-top: 10px; padding: 16px; border: 1px solid var(--ck-glass-border); border-radius: 20px; background: var(--ck-surface); box-shadow: var(--ck-shadow); }
.recipe-header span { color: var(--ck-heat-text); font-size: 11px; font-weight: 700; letter-spacing: 1px; }
.recipe-header h3 { margin: 2px 0 10px; font-size: 18px; font-weight: 700; }
.ing-grid { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 12px; }
.ing-grid span { display: inline-flex; gap: 5px; padding: 5px 10px; border-radius: 10px; background: var(--ck-fill); color: var(--ck-text-2); font-size: 12.5px; }
.ing-grid b { color: var(--ck-text); font-weight: 600; }
.steps-preview { display: flex; flex-direction: column; gap: 8px; }
.steps-preview p { display: flex; gap: 10px; color: var(--ck-text-2); font-size: 13.5px; line-height: 1.55; }
.steps-preview i { display: grid; place-items: center; width: 22px; height: 22px; flex: 0 0 22px; border-radius: 50%; background: var(--ck-fill-strong); color: var(--ck-text); font-size: 12px; font-style: normal; }
.start-guide { display: flex; align-items: center; justify-content: center; gap: 6px; width: 100%; min-height: 46px; margin-top: 14px; border: 0; border-radius: 999px; background: var(--ck-heat-deep); color: var(--ck-on-accent); font-size: 15px; font-weight: 600; }
.card-actions { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; margin-top: 8px; }
.card-actions button { display: inline-flex; align-items: center; justify-content: center; gap: 6px; min-height: 42px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-fill); color: var(--ck-text); font-size: 13.5px; font-weight: 600; }
.chat-input-bar-fixed { position: fixed; right: 0; bottom: calc(var(--ck-tabbar-height) + var(--sab)); left: 0; z-index: 40; padding: 10px calc(var(--ck-gutter) + var(--sar)) 10px calc(var(--ck-gutter) + var(--sal)); background: linear-gradient(180deg, transparent, var(--ck-bg) 45%); }
.input-content { display: flex; align-items: center; gap: 8px; width: min(100%, calc(var(--ck-page-max) - 32px)); margin: 0 auto; padding: 5px 5px 5px 6px; border: 1px solid var(--ck-glass-border); border-radius: 999px; background: var(--ck-surface); box-shadow: var(--ck-shadow-strong); }
.input-content :deep(.el-input__wrapper) { min-height: 42px; background: transparent !important; box-shadow: none !important; }
.send-btn { display: grid; place-items: center; width: 42px; height: 42px; flex: 0 0 42px; padding: 0; border: 0; border-radius: 50%; background: var(--ck-heat-deep); color: var(--ck-on-accent); }
.send-btn:disabled { opacity: 0.5; }

/* ---------- 抽屉与对话框 ---------- */
.trend-sheet { display: flex; flex-direction: column; gap: 12px; }
.bluetooth-scan-status { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--ck-text); }
.temperature-dialog-help { margin: 10px 0; color: var(--ck-text-3); font-size: 12.5px; line-height: 1.6; }
.bluetooth-device-list { display: flex; flex-direction: column; gap: 8px; max-height: 40vh; overflow-y: auto; }
.bluetooth-device-item { display: flex; align-items: center; justify-content: space-between; gap: 10px; min-height: 56px; padding: 10px 14px; border: 1px solid var(--ck-glass-border); border-radius: 14px; background: var(--ck-fill); color: var(--ck-text); text-align: left; }
.bluetooth-device-item span { display: flex; flex-direction: column; min-width: 0; }
.bluetooth-device-item small { color: var(--ck-text-3); font-size: 12px; }
.bluetooth-device-item em { flex: 0 0 auto; color: var(--ck-text-2); font-size: 12px; font-style: normal; }
.bluetooth-device-item.recommended em { color: var(--ck-fresh-text); }
.bluetooth-device-item.selected { border-color: var(--ck-heat); background: var(--ck-heat-soft); }
.bluetooth-debug { margin-top: 12px; color: var(--ck-text-2); font-size: 13px; }
.bluetooth-debug summary { min-height: 36px; cursor: pointer; }
.el-dialog .device-diagnostics { margin: 8px 0 0; padding: 0; border-top: 1px solid var(--ck-hairline); }

@media (max-width: 360px) {
  .k-title h1 { font-size: 27px; }
  .k-sense { padding: 6px 12px; }
  .c-duo { grid-template-columns: minmax(0, 1fr); }
  .c-recipe__plate { width: 120px; height: 120px; }
}

/* Viewport composition: the middle row is reserved exclusively for the pan. */
.ai-chef-container.is-live { height: calc(100dvh - var(--ck-bottom-inset)); min-height: 0; display: grid; grid-template-rows: 52px 80px 190px minmax(16px, 1fr) 84px 110px 92px; row-gap: 0; padding-top: calc(var(--sat) + 10px); padding-bottom: 12px; }
.is-live .k-head { height: auto; }
.is-live .k-title { align-items: center; }
.is-live .k-title h1 { font-size: 28px; }
.is-live .k-title p { font-size: 14px; }
.is-live .k-sense { padding: 8px 12px; gap: 8px; }
.is-live .k-hero { padding: 10px 0 0; }
.is-live .k-hero__temp { font-size: clamp(96px, 26vw, 110px); min-height: 105px; }
.is-live .k-pill { padding: 9px 18px; margin-top: 4px; max-width: 100%; }
.is-live .k-pill b { font-size: 17px; }
.is-live .k-pill small { font-size: 12px; }
.is-live .k-stages, .is-live .k-trend { padding: 10px 14px; margin: 0 0 12px; }
.is-live .k-card__title { font-size: 13px; line-height: 18px; margin-bottom: 4px; }
.is-live .k-trend { padding-block: 7px; }
.is-live .stepper li { font-size: 11px; line-height: 14px; gap: 3px; }
.is-live .stepper i { width: 14px; height: 14px; }
.is-live .stepper li::before { top: 7px; }
.stepper li.current i { border: 2px solid var(--ck-on-accent); background: var(--ck-heat); }
.k-live-advice { margin: 0; border: 0; padding: 12px 14px; gap: 10px; }
.k-live-advice .k-advice__body > b { font-size: 15px; line-height: 1.4; }
.k-live-advice .k-advice__body > small { font-size: 12px; }
.k-live-advice .k-advice__icon { width: 42px; height: 42px; flex-basis: 42px; }
.is-live .chef-shared, .is-live > .inline-status { position: fixed; z-index: 5; top: calc(var(--sat) + 8px); left: 20px; right: 20px; background: var(--ck-surface); border-radius: var(--ck-radius-md); padding: 12px; }
.ai-chef-container.is-cooking { height: 100dvh; min-height: 0; padding-top: calc(var(--sat) + 8px); padding-bottom: calc(var(--sab) + 12px); display: flex; flex-direction: column; gap: 10px; }
.is-cooking > * { flex-shrink: 0; }
.is-cooking .c-top { height: 38px; }
.is-cooking .c-recipe { min-height: 92px; padding: 12px 16px; margin: 0; }
.is-cooking .c-recipe__copy { gap: 4px; }
.is-cooking .c-recipe h2 { font-size: 23px; }
.is-cooking .c-recipe__plate { top: 4px; right: 8px; width: 88px; height: 88px; }
.is-cooking .c-chip { font-size: 11px; padding: 2px 10px; }
.is-cooking .c-recipe__progress { font-size: 11px; }
.is-cooking .c-gauge { flex: 1 1 auto; min-height: 278px; justify-content: center; margin: 0; }
.is-cooking .c-phase { margin-top: 6px; padding: 5px 22px; font-size: 14px; }
.is-cooking .c-eta { margin-top: -4px; }
.is-cooking .c-eta span, .is-cooking .c-eta small { font-size: 12px; }
.is-cooking .c-eta b { font-size: 40px; }
.is-cooking .c-chart { padding: 12px 14px; margin: 0; }
.is-cooking .k-card__title { margin-bottom: 10px; font-size: 14px; }
.is-cooking .chart-legend { margin-top: 6px; font-size: 10px; }
.is-cooking .c-duo { grid-template-columns: repeat(2, minmax(0, 1fr)); margin: 0; gap: 10px; }
.is-cooking .c-mini { padding: 10px; gap: 8px; }
.is-cooking .c-mini__icon { width: 30px; height: 30px; flex-basis: 30px; }
.is-cooking .c-mini b { font-size: 15px; }
.is-cooking .c-mini em, .is-cooking .c-mini small { font-size: 10px; }
.is-cooking .current-step-card { min-height: 88px; max-height: 106px; margin: 0; padding: 12px 14px; gap: 10px; cursor: pointer; }
.is-cooking .current-step-card .k-advice__icon { width: 40px; height: 40px; flex-basis: 40px; }
.is-cooking .step-description { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; font-size: 15px; }
.is-cooking .panel-heading { font-size: 11px; }
.is-cooking .panel-heading .step-remaining { display: none; }
.c-tools .step-description { font-size: 15px; }
.step-switcher { position: sticky; padding: 12px 0 calc(12px + var(--sab)); }
.r-links { flex-wrap: wrap; overflow: visible; margin-inline: 0; padding-inline: 0; }
.r-link { padding-inline: 12px; }
@media (max-height: 800px) {
 .ai-chef-container.is-live { grid-template-rows: 46px 68px 182px minmax(14px, 1fr) 84px 110px 92px; }
 .is-live .k-hero { padding-top: 6px; }
 .is-cooking .c-gauge { min-height: 260px; }
 .is-cooking .c-chart :deep(.ck-chart) { height: 70px !important; }
 .is-cooking .c-recipe { padding-block: 9px; }
 .is-cooking .c-eta b { font-size: 34px; }
 .is-cooking .c-gauge :deep(.ck-gauge) { width: 230px !important; height: 207px !important; }
 .is-cooking .c-gauge :deep(.ck-gauge__num) { font-size: 62px; }
}

@media (max-height: 850px) {
 .is-cooking .c-recipe { min-height: 80px; padding-block: 8px; }
 .is-cooking .c-recipe__copy { gap: 2px; }
 .is-cooking .c-recipe h2 { font-size: 21px; }
 .is-cooking .c-recipe__plate { width: 76px; height: 76px; }
 .is-cooking .c-chart { padding-block: 10px; }
 .is-cooking .c-chart :deep(.ck-chart) { height: 60px !important; }
 .is-cooking .c-chart .k-card__title { margin-bottom: 6px; }
 .is-cooking .chart-legend { margin-top: 4px; }
 .is-cooking .c-mini { padding: 8px; }
 .is-cooking .current-step-card { padding-block: 10px; }
 .is-cooking .step-description { font-size: 14px; }
}
</style>
