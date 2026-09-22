export const emptyFreshness = () => ({ status: 'idle', items: {}, evaluatedAt: null, error: '' })
export const hasScore = value => typeof value === 'number' && Number.isFinite(value)
export function freshnessStatus(detail) {
  if (!detail) return { className: 'unknown', label: '未知', description: '尚无鲜度评估' }
  if (detail.expired) return { className: 'expired', label: '已过期', description: '已过期' }
  if (detail.critical || detail.expiring_soon) return { className: 'soon', label: '临期', description: '临期，请查看评估依据' }
  if (!hasScore(detail.component_scores?.T)) return { className: 'unknown', label: '信息不足', description: '保质期信息不足' }
  return { className: 'fresh', label: detail.freshness_label || '已评估', description: detail.freshness_label || '已评估，请查看依据' }
}
export function freshnessError(error) {
  if (error?.response?.status === 404 && /user|用户/i.test(JSON.stringify(error.response.data))) return '用户不存在，请重新登录'
  if (error?.response?.status === 422) return '鲜度请求参数不受支持，请联系维护者'
  if (error?.code === 'ECONNABORTED') return '鲜度请求超时，请重试'
  return error?.message || '鲜度请求失败，请重试'
}
// Invalidate before refreshing inventory: merged records must never inherit old scores.
export function createFreshnessLoader(request, publish) {
  let version = 0, controller
  function reset() { version++; controller?.abort(); publish(emptyFreshness()) }
  async function load(userId, inventory) {
    reset()
    const ownVersion = version
    controller = new AbortController()
    publish({ ...emptyFreshness(), status: 'loading' })
    try {
      const data = await request(userId, controller.signal)
      if (ownVersion !== version) return
      const ids = new Set(inventory.map(item => String(item.id)))
      const items = Object.fromEntries(data.items.filter(item => ids.has(String(item.item_id))).map(item => [String(item.item_id), item]))
      publish({ status: 'success', items, evaluatedAt: data.evaluated_at, error: '' })
    } catch (error) {
      if (ownVersion === version) publish({ ...emptyFreshness(), status: 'error', error: freshnessError(error) })
    }
  }
  return { load, reset, dispose: reset }
}
