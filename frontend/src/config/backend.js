import { Capacitor } from '@capacitor/core'

const NATIVE_BACKEND_BASE_URL = 'http://192.168.3.29:8000'
const LOCAL_BACKEND_ORIGIN_PATTERN = /^https?:\/\/(?:127\.0\.0\.1|localhost):8000(?=\/|$)/i

export const IS_NATIVE_APP = Capacitor.isNativePlatform()
export const BACKEND_BASE_URL = IS_NATIVE_APP ? NATIVE_BACKEND_BASE_URL : ''
export const API_BASE_URL = `${BACKEND_BASE_URL}/api`

export const resolveBackendUrl = (url) => {
  if (!url) return url
  if (LOCAL_BACKEND_ORIGIN_PATTERN.test(url)) {
    return url.replace(LOCAL_BACKEND_ORIGIN_PATTERN, BACKEND_BASE_URL)
  }
  if (/^https?:\/\//i.test(url)) return url
  const normalizedPath = url.startsWith('/') ? url : `/${url}`
  return `${BACKEND_BASE_URL}${normalizedPath}`
}
