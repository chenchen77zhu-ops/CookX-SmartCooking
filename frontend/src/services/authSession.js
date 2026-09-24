import axios from 'axios'
import { BACKEND_BASE_URL, API_BASE_URL } from '../config/backend'

const KEY='cookx:session:v1'
const sessionOrigin=()=>new URL(BACKEND_BASE_URL||window.location.origin,window.location.href).origin
export function readSession() {
  try { const s=JSON.parse(sessionStorage.getItem(KEY)||'null'); return s?.expires_at*1000>Date.now()&&s.origin===sessionOrigin()?s:null } catch { return null }
}
export function clearSession() {
  sessionStorage.removeItem(KEY)
  localStorage.removeItem('user')
  window.dispatchEvent(new Event('storage'))
}
export function saveSession(data) {
  sessionStorage.removeItem(KEY)
  if(data.access_token) sessionStorage.setItem(KEY,JSON.stringify({access_token:data.access_token,expires_at:data.expires_at,user_id:data.user.id,origin:sessionOrigin()}))
  localStorage.setItem('user',JSON.stringify(data.user))
  window.dispatchEvent(new Event('storage'))
}
export async function logoutSession() {
  // Only clear after revocation is acknowledged; failures remain retryable.
  if(readSession()) await axios.post(`${API_BASE_URL}/auth/logout`,null,{timeout:10000})
  clearSession()
}

// Installed before axios.create consumers. Tokens go only to this app's API origin.
const nativeAdapter=axios.getAdapter(axios.defaults.adapter)
axios.defaults.adapter=async config=>{
  const url=new URL(axios.getUri(config),window.location.href)
  const expected=new URL(BACKEND_BASE_URL||window.location.origin,window.location.href)
  const api=url.origin===expected.origin&&url.pathname.startsWith('/api/')
  const session=readSession()
  let currentUser=null
  try { currentUser=JSON.parse(localStorage.getItem('user')||'null')?.id } catch {}
  const sent=api&&session?.user_id===currentUser?session?.access_token:null
  if(sent) config.headers.set('Authorization',`Bearer ${sent}`)
  try { return await nativeAdapter(config) } catch(error) {
    if(api&&error.response?.status===401&&readSession()?.access_token===session?.access_token) {
      clearSession()
      window.location.hash='#/login'
    }
    throw error
  }
}
