import {canonicalName} from './inventoryFields.js'
export const consumptionKey = user => `cookx:consumption:v1:${user}`
export const normalizedName = canonicalName
export function consumptionPreview(rows) {
 return rows.map(row=>({...row,quantity:Number(row.quantity),consumeQuantity:1,after:Math.max(0,Number(row.quantity)-1),blocked:!Number.isSafeInteger(Number(row.quantity))||Number(row.quantity)<1?'数量格式不支持自动扣减':''}))
}
export function consumptionMatches(transaction,rows){return transaction.items.every(item=>{
 const matches=rows.filter(row=>String(row.id)===String(item.id))
 return item.after===0?matches.length===0:matches.length===1 && Number(matches[0].quantity)===item.after
})}
export function createConsumption({read,write,lookup,storage,isCurrent=()=>true,makeKey=()=>crypto.randomUUID()}) {
 const busy=new Set()
 const pending=user=>{try{return JSON.parse(storage.getItem(consumptionKey(user))||'null')}catch{throw new Error('待核对记录损坏，请在库存页手动检查，不能再次自动扣减')}}
 const persist=(user,tx)=>storage.setItem(consumptionKey(user),JSON.stringify(tx))
 async function reconcile(user){
  const tx=pending(user);if(!tx)throw new Error('没有待核对记录')
  if(tx.schemaVersion!==2)throw new Error('旧版扣减没有服务端凭证，请人工核对库存后处理原记录；不会自动重发')
  const receipt=await lookup(user,tx.idempotencyKey)
  if(receipt?.status!=='success' || receipt.idempotency_key!==tx.idempotencyKey)throw new Error('服务端尚未确认扣减凭证，请保留原凭证核对')
  persist(user,{...tx,status:'committed',receipt})
  const rows=await read(user)
  persist(user,{...tx,status:'confirmed',receipt})
  return rows
 }
 async function send(user,tx){
  if(!isCurrent(user))throw new Error('登录用户已变化，已取消本次扣减')
  try {
   const response=await write(user,{user_id:user,idempotency_key:tx.idempotencyKey,items:tx.items.map(i=>({item_id:String(i.id),quantity:i.consumeQuantity,expected_quantity:i.quantity}))})
   if(response?.status!=='success')throw new Error(response?.message||'扣减未确认，请查询原凭证')
  } catch(error) {
   // Only a definitive rejection AND absent receipt permits a fresh, reviewed transaction.
   if([409,422].includes(error.response?.status)) {
    try{await lookup(user,tx.idempotencyKey)}catch(check){if(check.response?.status===404)persist(user,{...tx,status:'rejected'})}
   }
   throw error
  }
  return reconcile(user)
 }
 async function submit(user,sessionId,selected){
  if(busy.has(user))throw new Error('正在核对，请勿重复提交');busy.add(user)
  try {
   const old=pending(user);if(old && old.status!=='rejected' && (old.status!=='confirmed' || old.sessionId===sessionId))throw new Error('本次扣减已提交，请核对已有结果，不要重复扣减')
   if(!selected.length)throw new Error('请先选择实际使用的食材')
   const preview=consumptionPreview(await read(user)),ids=new Set()
   const items=selected.map(item=>{
    const latest=preview.find(r=>String(r.id)===String(item.id)),amount=Number(item.consumeQuantity ?? 1)
    if(!latest || latest.blocked || latest.quantity!==item.quantity || latest.name!==item.name || ids.has(String(latest.id)) || !Number.isSafeInteger(amount) || amount<1 || amount>latest.quantity)throw new Error('库存或使用数量已变化，请重新读取清单')
    ids.add(String(latest.id));return {...latest,consumeQuantity:amount,after:latest.quantity-amount}
   })
   if(!isCurrent(user))throw new Error('登录用户已变化，已取消本次扣减')
   const tx={schemaVersion:2,user,sessionId,items,idempotencyKey:makeKey(),status:'uncertain',at:Date.now()}
   persist(user,tx)
   return await send(user,tx)
  }finally{busy.delete(user)}
 }
 async function retry(user){
  if(busy.has(user))throw new Error('正在核对，请勿重复提交');busy.add(user)
  try{const tx=pending(user);if(tx?.schemaVersion!==2 || ['confirmed','rejected'].includes(tx.status))throw new Error('没有可安全重试的原凭证');return await send(user,tx)}finally{busy.delete(user)}
 }
 return {submit,reconcile,retry,pending}
}
