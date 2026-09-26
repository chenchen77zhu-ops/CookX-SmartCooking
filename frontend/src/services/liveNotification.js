import { Capacitor, registerPlugin } from '@capacitor/core'
import { LocalNotifications } from '@capacitor/local-notifications'

// Android 实时烹饪通知：App 退到后台时显示（Android 16+ 为状态栏实时活动胶囊），回到前台时移除。
// 温度由原生蓝牙读取线程直接刷新，避免后台网页暂停导致通知停在旧读数。
const LiveCooking = registerPlugin('LiveCooking')
const isAndroid = () => Capacitor.getPlatform() === 'android'

let shown = false, lastKey = '', lastSentAt = 0

export function syncLiveNotification(payload, immediate = false) {
  if (!isAndroid()) return
  if (!document.hidden || !payload) {
    if (shown) { shown = false; lastKey = ''; LiveCooking.hide().catch(() => {}) }
    return
  }
  const key = JSON.stringify({ ...payload, temperature: payload.temperature === null ? null : Math.round(payload.temperature) })
  const now = Date.now()
  if (!immediate && key === lastKey && now - lastSentAt < 15000) return
  lastKey = key
  lastSentAt = now
  shown = true
  LiveCooking.show(payload).catch(() => { shown = false })
}

// 开始烹饪时（而不是启动 App 时）请求通知权限，退到后台后才能显示实时烹饪通知
let permissionAsked = false
export async function prepareLiveNotification() {
  if (!isAndroid() || permissionAsked) return
  permissionAsked = true
  try {
    const { display } = await LocalNotifications.checkPermissions()
    if (display === 'prompt' || display === 'prompt-with-rationale') await LocalNotifications.requestPermissions()
  } catch {}
}
