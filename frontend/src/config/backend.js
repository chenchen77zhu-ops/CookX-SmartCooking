import {LOCAL_TEST_MODE} from './buildMode.js'
import {SERVER_KEY,normalizeServerOrigin} from '../services/serverAddress'
import { Capacitor } from '@capacitor/core'

// Set the reachable backend origin at build time; no secrets belong in VITE variables.
const configuredOrigin = LOCAL_TEST_MODE ? undefined : import.meta.env.VITE_BACKEND_ORIGIN?.replace(/\/$/, '')
if (configuredOrigin && !/^https?:\/\/[^/?#]+(?::\d+)?$/.test(configuredOrigin)) throw new Error('VITE_BACKEND_ORIGIN must be an HTTP(S) origin')
const NATIVE_BACKEND_BASE_URL = configuredOrigin || 'http://192.168.43.49:8000'
const LOCAL_BACKEND_ORIGIN_PATTERN = /^https?:\/\/(?:127\.0\.0\.1|localhost):8000(?=\/|$)/i

export const IS_NATIVE_APP = Capacitor.isNativePlatform()
let savedOrigin='';try{savedOrigin=normalizeServerOrigin(localStorage.getItem(SERVER_KEY))}catch{}
export const BACKEND_BASE_URL = LOCAL_TEST_MODE ? '' : savedOrigin || configuredOrigin || (IS_NATIVE_APP ? NATIVE_BACKEND_BASE_URL : '')
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
