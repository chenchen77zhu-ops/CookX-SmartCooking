import {canonicalName} from './inventoryFields.js'
export const consumptionKey = user => `cookx:consumption:v1:${user}`
export const normalizedName = canonicalName
export function consumptionPreview(rows) {
  return rows.map(row=>{const duplicates=rows.filter(r=>normalizedName(r.name)===normalizedName(row.name)).length;const quantity=Number(row.quantity);return {id:row.id,name:row.name,quantity,after:Math.max(0,quantity-1),blocked:duplicates>1?'同名多批次，请在库存页手动处理':!Number.isInteger(quantity)||quantity<1?'数量格式不支持自动扣减':''}})
}
export function consumptionMatches(transaction,rows){return transaction.items.every(item=>{
  const matches=rows.filter(row=>normalizedName(row.name)===normalizedName(item.name))
  return item.after===0?matches.length===0:matches.length===1 && String(matches[0].id)===String(item.id) && Number(matches[0].quantity)===item.after
})}
export function createConsumption({read,write,storage,isCurrent=()=>true}) {
  const busy=new Set()
  const pending=user=>{try{return JSON.parse(storage.getItem(consumptionKey(user))||'null')}catch{throw new Error('待核对记录损坏，请在库存页手动检查，不能再次自动扣减')}}
  async function reconcile(user){const tx=pending(user);if(!tx)throw new Error('没有待核对记录');const rows=await read(user);if(!consumptionMatches(tx,rows))throw new Error('扣减结果仍不确定，请在库存页手动核对；不会自动重发');storage.setItem(consumptionKey(user),JSON.stringify({...tx,status:'confirmed'}));return rows}
  async function submit(user,sessionId,selected){
    if(busy.has(user))throw new Error('正在核对，请勿重复提交');busy.add(user)
    try {
      const old=pending(user);if(old && (old.status!=='confirmed' || old.sessionId===sessionId))throw new Error('本次扣减已提交，请核对已有结果，不要重复扣减')
      if(!selected.length)throw new Error('请先选择实际使用的食材')
      const rows=await read(user),preview=consumptionPreview(rows),names=new Set()
      const items=selected.map(item=>{const latest=preview.find(r=>String(r.id)===String(item.id));if(!latest || latest.blocked || latest.quantity!==item.quantity || latest.name!==item.name || names.has(normalizedName(latest.name)))throw new Error('库存已变化或存在多批次，请重新读取清单');names.add(normalizedName(latest.name));return latest})
      if(!isCurrent(user))throw new Error('登录用户已变化，已取消本次扣减')
      const tx={schemaVersion:1,user,sessionId,items,status:'uncertain',at:Date.now()}
      // Persist first. A lost response never authorizes a second write.
      storage.setItem(consumptionKey(user),JSON.stringify(tx))
      const response=await write(user,items.map(i=>i.name))
      if(response?.status!=='success') {storage.removeItem(consumptionKey(user));throw new Error(response?.message||'服务端拒绝扣减，烹饪完成记录已保留')}
      return await reconcile(user)
    } finally {busy.delete(user)}
  }
  return {submit,reconcile,pending}
}
