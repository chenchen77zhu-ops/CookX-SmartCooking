import {ref} from 'vue'
import {businessApi,commandKey,apiError} from '../api/business'
import {readUserId} from './recognitionDraft'
export function usePendingCommand(scope,onSuccess=async()=>{}) {
 const user=readUserId(),key=`cookx:pending:${scope}:v1:${user}`,pending=ref(null),busy=ref(false),error=ref('')
 try{pending.value=JSON.parse(localStorage.getItem(key)||'null')}catch{error.value='本地待确认记录损坏，请保留设备数据并联系维护者'}
 async function retry(){
  if(busy.value||!pending.value||readUserId()!==user)return
  busy.value=true;error.value=''
  try{const p=pending.value,result=await businessApi(p.path,p.method,p.body);localStorage.removeItem(key);pending.value=null;await onSuccess(result)}
  catch(e){error.value=String(apiError(e));if(e.response?.status>=400&&e.response.status<500){localStorage.removeItem(key);pending.value=null}}
  finally{busy.value=false}
 }
 async function send(path,method,body){
  if(busy.value||pending.value||readUserId()!==user)return
  const value={path,method,body:{...body,idempotency_key:commandKey()}}
  try{localStorage.setItem(key,JSON.stringify(value))}catch{error.value='无法保存操作凭证，请释放设备存储后再试';return}
  pending.value=value;await retry()
 }
 return {pending,busy,error,send,retry}
}
