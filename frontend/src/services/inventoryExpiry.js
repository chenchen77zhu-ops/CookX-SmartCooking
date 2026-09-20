// Missing storage dates or shelf life cannot establish freshness. NaN means unknown.
export function calculateDaysUntilExpiry(item, now = Date.now()) {
  const supplied = item.days_left ?? item.daysUntilExpiry
  if (supplied != null && supplied !== '' && Number.isFinite(Number(supplied))) return Number(supplied)
  if (item.expiration_date) {
    const expiry = new Date(item.expiration_date).getTime()
    if (Number.isFinite(expiry)) return Math.ceil((expiry - now) / 86400000)
  }
  if (!item.add_time || item.shelf_life == null || item.shelf_life === '') return NaN
  const added = new Date(item.add_time).getTime(), life = Number(item.shelf_life)
  if (!Number.isFinite(added) || !Number.isFinite(life) || life < 0) return NaN
  return Math.max(0, Math.ceil((added + life * 86400000 - now) / 86400000))
}
