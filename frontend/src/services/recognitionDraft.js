import { blankItem, localDateTime } from './inventoryFields.js'
export const draftKey = user => `cookx:recognition:v1:${user}`
export function readUserId(storage = localStorage) {
  try { const user=JSON.parse(storage.getItem('user') || '{}'); return typeof user?.id === 'string' && user.id.trim() ? user.id : null } catch { return null }
}
export function saveDraft(user, items, storage = localStorage) {
  if (!user) throw new Error('登录信息失效')
  storage.setItem(draftKey(user),JSON.stringify({version:1,user,items}))
}
export function loadDraft(user, storage = localStorage) {
  try {
    const data=JSON.parse(storage.getItem(draftKey(user)) || 'null')
    return data?.user === user && data.version === 1 && Array.isArray(data.items) ? data.items.filter(item=>item && typeof item === 'object').map(item=>({...blankItem(),...item})) : []
  } catch { return [] }
}
export const clearDraft = (user, storage = localStorage) => storage.removeItem(draftKey(user))
export const recognitionItem = item => ({...blankItem(),...item,purchase_time:localDateTime(item.purchase_time || item.purchase_date),expiry_date:localDateTime(item.expiry_date)})
