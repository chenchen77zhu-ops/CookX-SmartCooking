import { reactive } from 'vue'
import { Capacitor, SystemBars, SystemBarsStyle } from '@capacitor/core'

// 外观：默认浅色；可选深色或跟随系统。实时厨房等摄影页面始终使用深色局部主题。
const KEY = 'cookx:theme'
const OPTIONS = ['light', 'dark', 'system']
const media = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null

const readPreference = () => {
  try { const value = localStorage.getItem(KEY); return OPTIONS.includes(value) ? value : 'light' } catch { return 'light' }
}

export const themeState = reactive({ preference: readPreference(), dark: false })

const resolveDark = () => themeState.preference === 'dark' || (themeState.preference === 'system' && !!media?.matches)

let forcedDarkPage = false
function applyStatusBar() {
  if (!Capacitor.isNativePlatform()) return
  const lightIcons = themeState.dark || forcedDarkPage
  SystemBars.setStyle({ style: lightIcons ? SystemBarsStyle.Dark : SystemBarsStyle.Light }).catch(() => {})
}

export function applyTheme() {
  themeState.dark = resolveDark()
  const root = document.documentElement
  root.dataset.theme = themeState.dark ? 'dark' : 'light'
  root.classList.toggle('dark', themeState.dark) // Element Plus 暗色变量
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', themeState.dark ? '#0D100F' : '#F3F1EC')
  applyStatusBar()
}

export function setThemePreference(value) {
  if (!OPTIONS.includes(value)) return
  themeState.preference = value
  try { localStorage.setItem(KEY, value) } catch {}
  applyTheme()
}

// 进入实时厨房、登录等深色摄影页面时，状态栏图标改为浅色
export function setForcedDarkPage(value) {
  forcedDarkPage = !!value
  applyStatusBar()
}

media?.addEventListener?.('change', () => { if (themeState.preference === 'system') applyTheme() })
