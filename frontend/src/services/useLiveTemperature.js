import { ref, onMounted, onBeforeUnmount } from 'vue'
import { Capacitor } from '@capacitor/core'
import { onTemperatureData, onTemperatureConnectionStateChanged, getTemperatureConnectionState } from './temperatureDevice.js'
import { TEMPERATURE_STALE_MS } from './temperatureStream.js'

// 只读订阅：不连接、不断开、不改写设备状态，只用于展示实时锅温与近期曲线
export function useLiveTemperature({ windowMs = 6 * 60 * 1000 } = {}) {
  const supported = Capacitor.getPlatform() === 'android'
  const state = ref(supported ? 'idle' : 'unsupported')
  const connected = ref(false)
  const temperature = ref(null)
  const history = ref([])
  let handles = [], staleTimer = null, mounted = true

  const clearReading = () => { temperature.value = null }
  const cleanup = async () => {
    clearTimeout(staleTimer)
    const current = handles
    handles = []
    for (const handle of current) { try { await handle.remove() } catch {} }
  }

  onMounted(async () => {
    if (!supported) return
    try {
      handles.push(await onTemperatureConnectionStateChanged(connection => {
        state.value = connection.state
        connected.value = connection.state === 'connected'
        if (!connected.value) clearReading()
      }))
      handles.push(await onTemperatureData(sample => {
        if (document.hidden || sample.valid === false) return
        const value = Number(sample.temperature)
        if (!Number.isFinite(value) || value < -50 || value > 500) return
        const at = Number(sample.updatedAt) || Date.now()
        temperature.value = value
        history.value = [...history.value.filter(point => at - point.at < windowMs), { at, t: value }].slice(-480)
        clearTimeout(staleTimer)
        staleTimer = setTimeout(clearReading, TEMPERATURE_STALE_MS)
      }))
      const current = await getTemperatureConnectionState()
      state.value = current.state || state.value
      connected.value = current.state === 'connected' && current.connected !== false
    } catch {
      state.value = 'error'
    }
    if (!mounted) cleanup()
  })
  onBeforeUnmount(() => { mounted = false; cleanup() })

  return { supported, state, connected, temperature, history }
}

// 按锅面温度给出火候阶段（仅用于界面提示，不参与算法）
export const HEAT_STAGES = [
  { id: 'preheat', label: '预热', min: -Infinity },
  { id: 'rising', label: '升温', min: 90 },
  { id: 'sear', label: '煎香', min: 150 },
  { id: 'cook', label: '爆炒', min: 190 },
  { id: 'hot', label: '过热', min: 240 }
]
export function heatStage(temperature) {
  if (temperature === null || temperature === undefined) return null
  let index = 0
  HEAT_STAGES.forEach((stage, i) => { if (temperature >= stage.min) index = i })
  return { index, ...HEAT_STAGES[index] }
}
