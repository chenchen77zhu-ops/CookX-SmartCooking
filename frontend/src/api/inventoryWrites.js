import axios from 'axios'
import { API_BASE_URL } from '../config/backend'
import { verifyMutation } from '../services/inventoryFields.js'
const active = new Set()
const key = user => `cookx:inventory-pending:v1:${user}`
export const hasPendingWrite = user => !!localStorage.getItem(key(user))
export const dismissPendingWrite = user => localStorage.removeItem(key(user))
export async function readInventory(user) {
  const { data } = await axios.get(`${API_BASE_URL}/inventory`, { params: {user_id:user}, timeout:15000 })
  if (!Array.isArray(data)) throw new Error(data?.message || '库存读取失败')
  return data
}
export async function saveInventory(user, payload, id = null, recognition = false, revision = null) {
  if (active.has(user)) throw new Error('正在保存，请勿重复操作')
  active.add(user)
  try {
    let transaction = JSON.parse(localStorage.getItem(key(user)) || 'null')
    if (!transaction) {
      transaction = { payload, id, recognition, revision, idempotencyKey:crypto.randomUUID(), before: await readInventory(user) }
      // Persist before sending; a timeout or navigation must not cause an automatic duplicate POST.
      localStorage.setItem(key(user), JSON.stringify(transaction))
      try {
        const headers={'Idempotency-Key':transaction.idempotencyKey,...(transaction.revision?{'If-Match':transaction.revision}:{})}
        const options = { params:{user_id:user}, timeout:15000,headers }
        const response = id ? await axios.put(`${API_BASE_URL}/inventory/${encodeURIComponent(id)}`,payload,options)
          : recognition ? await axios.post(`${API_BASE_URL}/inventory/confirm-recognition`,{user_id:user,confirmed:true,items:payload},{timeout:15000,headers})
          : await axios.post(`${API_BASE_URL}/add-to-inventory`,payload,options)
        if (response.data?.status !== 'success') {
          localStorage.removeItem(key(user))
          throw new Error(response.data?.message || '服务端未确认保存')
        }
      } catch (error) {
        if (error.response?.status >= 400 && error.response.status < 500) localStorage.removeItem(key(user))
        throw error
      }
    }
    const rows = await readInventory(user)
    if (!verifyMutation(transaction, rows)) throw new Error('保存结果未确认：可能发生批次归并或字段未保存。请核对库存并联系维护者，不要重复入库。')
    localStorage.removeItem(key(user))
    return rows
  } finally { active.delete(user) }
}

export async function deleteInventory(user,id,revision){
 const pendingKey=`cookx:delete-inventory:v1:${user}:${id}`
 let tx=JSON.parse(localStorage.getItem(pendingKey)||'null')
 if(!tx){tx={revision,key:crypto.randomUUID()};localStorage.setItem(pendingKey,JSON.stringify(tx))}
 try{const response=await axios.delete(`${API_BASE_URL}/inventory/${encodeURIComponent(id)}`,{params:{user_id:user},headers:{'Idempotency-Key':tx.key,...(tx.revision?{'If-Match':tx.revision}:{})},timeout:15000});if(response.data?.status!=='success')throw Error(response.data?.message||'移除未确认');await readInventory(user);localStorage.removeItem(pendingKey);return response}
 catch(e){if(e.response?.status>=400&&e.response.status<500)localStorage.removeItem(pendingKey);throw e}
}
