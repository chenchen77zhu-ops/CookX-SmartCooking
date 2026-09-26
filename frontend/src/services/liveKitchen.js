import { reactive } from 'vue'
import { Capacitor } from '@capacitor/core'
import { onTemperatureData, onTemperatureConnectionStateChanged, getTemperatureConnectionState } from './temperatureDevice.js'
import { TEMPERATURE_STALE_MS } from './temperatureStream.js'
import { getCookingStore } from './cookingStore.js'
import { readUserId } from './recognitionDraft.js'
import { speakSystem } from './systemVoice.js'
import { syncLiveNotification, prepareLiveNotification } from './liveNotification.js'

// 全局实时厨房状态：首页卡片、厨房、悬浮窗与系统通知共用。
// 只读订阅设备数据，不连接、不断开设备；阶段与预警只用于界面提示，不参与算法判断。

const HISTORY_MS = 6 * 60 * 1000
const MUTE_MS = 60 * 1000

export const HEAT_STAGES = [
  { id: 'preheat', label: '预热', min: -Infinity },
  { id: 'heating', label: '升温', min: 90 },
  { id: 'sear', label: '煎香', min: 150 },
  { id: 'cook', label: '烹饪', min: 200 }
]
const STAGE_COPY = {
  preheat: ['预热中', '锅还没热，保持中火'],
  heating: ['加热中', '接近煎香区，保持火力'],
  sear: ['煎香区', '温度合适，可以放入食材'],
  cook: ['高温烹饪', '火力较大，注意快速翻动']
}

export const live = reactive({
  supported: Capacitor.getPlatform() === 'android',
  deviceState: Capacitor.getPlatform() === 'android' ? 'idle' : 'unsupported',
  connected: false,
  temperature: null,
  simulated: false,
  history: [],
  session: null,
  assessment: null,
  alert: null,
  mutedUntil: 0,
  now: Date.now()
})

let started = false, staleTimer = null, lastAlertLevel = null, reachedTarget = false, lastSpokenAt = 0

const round = value => Math.round(value)
export const formatClock = seconds => {
  if (!Number.isFinite(seconds) || seconds < 0) return '--:--'
  const s = Math.round(seconds)
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
}

export function heatStage(temperature) {
  if (temperature === null || temperature === undefined) return null
  let index = 0
  HEAT_STAGES.forEach((stage, i) => { if (temperature >= stage.min) index = i })
  return { index, ...HEAT_STAGES[index] }
}

// 从步骤文字或字段中读出目标温区，例如「160–180 ℃」「约 160°C」
export function parseTarget(value) {
  const numbers = String(value ?? '').match(/\d+(?:\.\d+)?/g)?.map(Number).filter(n => n >= 40 && n <= 320) || []
  if (numbers.length >= 2) return [Math.min(...numbers), Math.max(...numbers)]
  if (numbers.length === 1) return [numbers[0] - 10, numbers[0] + 10]
  return null
}

function readSession() {
  const uid = readUserId()
  if (!uid) return null
  const state = getCookingStore(uid).engine.state
  if (!state?.recipe?.steps?.length || state.status !== 'active') return null
  const engine = getCookingStore(uid).engine
  const steps = state.recipe.steps
  const step = steps[state.stepIndex] || {}
  const next = steps[state.stepIndex + 1]
  const title = (s, i) => (s && typeof s === 'object' && s.title) ? s.title : `步骤 ${i + 1}`
  const text = s => (s && typeof s === 'object') ? (s.text || s.content || '') : String(s || '')
  const timer = state.timers?.[state.stepIndex]
  return {
    id: state.id,
    dish: state.recipe.dish_name || '当前菜谱',
    image: state.recipe.image_url || state.recipe.image || '',
    stepIndex: state.stepIndex,
    total: steps.length,
    stepTitle: title(step, state.stepIndex),
    stepText: text(step),
    nextTitle: next ? title(next, state.stepIndex + 1) : '',
    target: parseTarget(step.temperature),
    remainingMs: engine.remaining(),
    timed: Boolean(step.time_estimate || timer?.round > 0),
    totalSeconds: steps.reduce((sum, s) => sum + Number(s?.time_estimate || 0), 0)
  }
}

// 最近 20 秒的升温速度（°C/秒），用于估算达到目标温度的时间
export function heatingRate(history = live.history) {
  const now = history[history.length - 1]?.at
  const window = history.filter(p => now - p.at <= 20000)
  if (window.length < 5) return null
  const n = window.length, mx = window.reduce((s, p) => s + p.at, 0) / n, my = window.reduce((s, p) => s + p.t, 0) / n
  const num = window.reduce((s, p) => s + (p.at - mx) * (p.t - my), 0), den = window.reduce((s, p) => s + (p.at - mx) ** 2, 0)
  return den ? (num / den) * 1000 : null
}

export function targetEta(temperature = live.temperature, target = live.session?.target) {
  if (temperature === null || !target) return null
  if (temperature >= target[0]) return 0
  const rate = heatingRate()
  if (!rate || rate < 0.05) return null
  return (target[0] - temperature) / rate
}

export function computeAlert(temperature = live.temperature, target = live.session?.target, assessment = live.assessment) {
  if (temperature === null && assessment?.risk !== 'danger') return null
  const hi = target?.[1]
  if (assessment?.risk === 'danger' || temperature >= 260 || (hi && temperature > hi + 40)) {
    return { level: 'danger', title: '锅温过高', text: '已超出安全范围，请立即调小火力或离火' }
  }
  if (assessment?.risk === 'warning' || temperature >= 235 || (hi && temperature > hi + 15)) {
    return { level: 'warn', title: '温度偏高', text: '建议调小火力，避免油冒烟' }
  }
  return null
}

// 界面上的阶段胶囊与「CookX 建议」
export function liveAdvice() {
  const t = live.temperature, s = live.session, stage = heatStage(t)
  if (live.alert) return { title: live.alert.title, text: live.alert.text, tone: live.alert.level }
  if (live.assessment?.suggestion) return { title: stage ? STAGE_COPY[stage.id][0] : '温度分析', text: live.assessment.suggestion, tone: 'normal' }
  if (t !== null && s?.target) {
    const eta = targetEta(t, s.target)
    if (t < s.target[0]) return { title: '加热中', text: eta ? `再等 ${Math.max(1, Math.round(eta))} 秒，达到 ${s.target[0]}°C 后放入食材` : `加热到 ${s.target[0]}°C 后放入食材`, tone: 'normal' }
    if (t <= s.target[1]) return { title: '温度合适', text: '已达到目标温度，可以放入食材', tone: 'good' }
    return { title: '温度偏高', text: `目标 ${s.target[0]}–${s.target[1]}°C，建议调小火力`, tone: 'warn' }
  }
  if (stage) { const [title, text] = STAGE_COPY[stage.id]; return { title, text, tone: 'normal' } }
  return null
}

// 背景图状态：冷锅 → 预热 → 升温 → 煎香 → 过热
export function backdropState(temperature = live.temperature, alert = live.alert) {
  if (alert) return 'overheat'
  if (temperature === null || temperature < 50) return 'idle'
  if (temperature < 90) return 'preheat'
  if (temperature < 150) return 'heating'
  return 'sear'
}

export function acknowledgeAlert() { live.mutedUntil = Date.now() + MUTE_MS }

function alertEffects() {
  const alert = computeAlert()
  const level = alert?.level || null
  live.alert = alert
  if (level === 'danger' && lastAlertLevel !== 'danger' && Date.now() > live.mutedUntil) {
    try { navigator.vibrate?.([320, 120, 320, 120, 320]) } catch {}
    if (Date.now() - lastSpokenAt > 30000) { lastSpokenAt = Date.now(); speakSystem('锅温过高，请立即调小火力').catch(() => {}) }
  }
  lastAlertLevel = level
  const target = live.session?.target
  const inTarget = target && live.temperature !== null && live.temperature >= target[0] && live.temperature <= target[1]
  if (inTarget && !reachedTarget) { try { navigator.vibrate?.(160) } catch {} }
  reachedTarget = !!inTarget
}

function record(value, at = Date.now(), simulated = false) {
  live.temperature = value
  live.simulated = simulated
  live.history = [...live.history.filter(p => at - p.at < HISTORY_MS), { at, t: value }].slice(-480)
  clearTimeout(staleTimer)
  staleTimer = setTimeout(() => { live.temperature = null; alertEffects() }, TEMPERATURE_STALE_MS)
  alertEffects()
}

// 厨房页面把温度分析（含仿真回放）同步给全局，便于悬浮窗和通知使用同一读数
export function publishKitchen({ temperature, simulated = false, assessment } = {}) {
  if (assessment !== undefined) live.assessment = assessment ? { risk: assessment.risk, suggestion: assessment.suggestion || '', phaseLabel: assessment.phaseLabel || '' } : null
  if (Number.isFinite(temperature)) record(temperature, Date.now(), simulated)
  else if (temperature === null && simulated === false && !live.connected) { live.temperature = null; alertEffects() }
}

// 系统通知内容（与首页 CookX Sense 卡片一致）
export function notificationPayload() {
  const s = live.session
  if (!s && !live.connected) return null
  const advice = liveAdvice()
  const timerEndsAt = s?.timed && s.remainingMs > 0 ? Date.now() + s.remainingMs : 0
  return {
    connected: live.connected,
    temperature: live.temperature,
    maxTemperature: 250,
    targetLow: s?.target?.[0] ?? 0,
    targetHigh: s?.target?.[1] ?? 0,
    status: s ? '正在烹饪中' : '待机中',
    dish: s ? s.dish : '选择一道菜开始',
    step: s ? `第 ${s.stepIndex + 1}/${s.total} 步 · ${s.stepTitle}` : '',
    adviceTitle: live.alert ? live.alert.title : '下一步建议',
    adviceText: live.alert ? live.alert.text : (advice?.text || (s?.nextTitle ? `接下来：${s.nextTitle}` : s?.stepText || '')),
    alertLevel: live.alert?.level || '',
    timerEndsAt
  }
}

export function refreshSession() {
  const previous = live.session?.id
  live.session = readSession()
  if (live.session && live.session.id !== previous) prepareLiveNotification()
  live.now = Date.now()
}

export async function startLiveKitchen() {
  if (started) return
  started = true
  refreshSession()
  setInterval(() => { refreshSession(); syncLiveNotification(notificationPayload()) }, 1000)
  window.addEventListener('storage', refreshSession)
  window.addEventListener('cookx:user-changed', () => { live.history = []; live.temperature = null; refreshSession() })
  document.addEventListener('visibilitychange', () => syncLiveNotification(notificationPayload(), true))
  if (!live.supported) return
  try {
    await onTemperatureConnectionStateChanged(connection => {
      live.deviceState = connection.state
      live.connected = connection.state === 'connected'
      if (!live.connected) { live.temperature = null; alertEffects() }
    })
    await onTemperatureData(sample => {
      if (sample.valid === false) return
      const value = Number(sample.temperature)
      if (!Number.isFinite(value) || value < -50 || value > 500) return
      record(value, Number(sample.updatedAt) || Date.now())
    })
    const current = await getTemperatureConnectionState()
    live.deviceState = current.state || live.deviceState
    live.connected = current.state === 'connected' && current.connected !== false
  } catch {
    live.deviceState = 'error'
  }
}

// 开发调试：本地 dev 环境可在控制台注入温度，生产构建不包含
if (import.meta.env?.DEV && typeof window !== 'undefined') window.__cookxLive = { live, inject: (t, simulated = true) => record(t, Date.now(), simulated) }

export const stageCopy = id => STAGE_COPY[id] || ['待机', '连接 CookX Sense 后显示实时锅温']
export { round as roundTemperature }
