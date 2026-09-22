// Mirrors the baseline API name aliases solely for read-back verification, not scoring.
const aliases = {"tomato": "西红柿", "番茄": "西红柿", "西红柿": "西红柿", "小番茄": "西红柿", "圣女果": "西红柿", "beef": "牛肉", "牛肉": "牛肉", "milk": "牛奶", "牛奶": "牛奶", "tofu": "豆腐", "豆腐": "豆腐", "potato": "土豆", "土豆": "土豆", "carrot": "胡萝卜", "胡萝卜": "胡萝卜", "chicken": "鸡肉", "鸡肉": "鸡肉", "egg": "鸡蛋", "鸡蛋": "鸡蛋", "onion": "洋葱", "洋葱": "洋葱", "garlic": "大蒜", "大蒜": "大蒜", "ginger": "生姜", "姜": "生姜", "生姜": "生姜", "broccoli": "西兰花", "西兰花": "西兰花", "kimchi": "泡菜", "韩式泡菜": "泡菜", "泡菜": "泡菜", "chili": "红辣椒", "红辣椒": "红辣椒", "青辣椒": "青辣椒"}
export const canonicalName = name => aliases[String(name || '').trim().toLowerCase()] || String(name || '').trim().toLowerCase()
export const blankItem = () => ({ name: '', quantity: 1, storage_type: '', shelf_life: '', purchase_time: '', expiry_date: '' })
const empty = value => value === '' || value === null || value === undefined
export function localDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (!Number.isFinite(date.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth()+1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
export function isoFromLocal(value) {
  if (empty(value)) return undefined
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('日期格式无效')
  const date = new Date(value)
  if (!Number.isFinite(date.getTime()) || localDateTime(date) !== value) throw new Error('日期无效或处于夏令时跳变区间')
  return date.toISOString()
}
export function serializeItem(form, original = null, now = Date.now()) {
  const name = String(form.name || '').trim(), quantity = Number(form.quantity)
  if (!name) throw new Error('请填写名称')
  if (!Number.isSafeInteger(quantity) || quantity <= 0) throw new Error('数量必须是正整数')
  const result = { name, quantity }
  if (!empty(form.storage_type)) result.storage_type = form.storage_type
  if (!empty(form.shelf_life)) {
    const days = Number(form.shelf_life)
    if (!Number.isFinite(days) || days <= 0) throw new Error('保质期必须大于 0')
    result.shelf_life = days
  }
  if (original) {
    for (const key of ['storage_type', 'shelf_life']) {
      if (!empty(original[key]) && empty(form[key])) throw new Error('清空已有字段需等待后端 A3 支持')
    }
    if (result.shelf_life !== undefined && Number(original.shelf_life) !== result.shelf_life && !Number.isInteger(result.shelf_life)) throw new Error('当前编辑接口仅支持整数天，精度升级待 A3 联调')
    return Object.fromEntries(Object.entries(result).filter(([key, value]) => key === 'name' ? canonicalName(value) !== canonicalName(original[key]) : key === 'quantity' || key === 'shelf_life' ? Number(original[key]) !== value : original[key] !== value))
  }
  const purchase = isoFromLocal(form.purchase_time), expiry = isoFromLocal(form.expiry_date)
  if (purchase && Date.parse(purchase) > now) throw new Error('购买时间不能晚于当前时间')
  if (expiry && Date.parse(expiry) < (purchase ? Date.parse(purchase) : now)) throw new Error('到期时间不能早于起始时间；历史记录请填写购买时间')
  if (purchase) result.purchase_time = purchase
  if (expiry) result.expiry_date = expiry
  return result
}
function sameField(key, actual, expected) {
  if (key === 'name') return canonicalName(actual) === canonicalName(expected)
  if (key === 'purchase_time' || key === 'expiry_date') return Date.parse(actual) === Date.parse(expected)
  return actual === expected
}
export function verifyMutation(transaction, after) {
  if (transaction.id) {
    const item = after.find(item => String(item.id) === String(transaction.id))
    return !!item && Object.entries(transaction.payload).every(([key,value]) => sameField(key,item[key],value))
  }
  // Compare groups, including explicit metadata: ignored dates or batch collisions remain unconfirmed.
  const groups = new Map()
  for (const item of transaction.payload) {
    const fields = { ...item, name: canonicalName(item.name) }; delete fields.quantity
    const key = JSON.stringify(fields)
    const group = groups.get(key) || { fields, quantity: 0 }
    group.quantity += item.quantity; groups.set(key,group)
  }
  return [...groups.values()].every(({fields,quantity}) => {
    const count = rows => rows.filter(row => Object.entries(fields).every(([key,value])=>sameField(key,row[key],value))).reduce((n,row)=>n+Number(row.quantity || 0),0)
    return count(after) - count(transaction.before) === quantity
  })
}
