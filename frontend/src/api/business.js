import axios from 'axios'
import { API_BASE_URL } from '../config/backend'
import { readUserId } from '../services/recognitionDraft'
export const commandKey=()=>crypto.randomUUID()
export async function businessApi(path,method='get',body) {
  const user=readUserId()
  const response=await axios({url:API_BASE_URL+'/v3'+path,method,data:body,timeout:20000})
  if(readUserId()!==user) throw new Error('账号已切换，请重新打开页面')
  if(response.data?.status==='error') throw new Error(response.data.message||'操作未完成')
  return response.data
}
export const apiError=error=>{const detail=error.response?.data?.detail;return Array.isArray(detail)?'输入不符合要求，请检查数量、单位与日期。':detail||error.message||'请求失败，请重试'}
