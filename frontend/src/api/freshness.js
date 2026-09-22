import axios from 'axios'
import { API_BASE_URL } from '../config/backend'
export async function getInventoryFreshness(userId, signal) {
  const { data } = await axios.get(`${API_BASE_URL}/users/${encodeURIComponent(userId)}/inventory/freshness`, { signal, timeout: 15000 })
  if (data?.status === 'error' || !Array.isArray(data?.items)) throw new Error(data?.message || '鲜度响应格式异常')
  return data
}
