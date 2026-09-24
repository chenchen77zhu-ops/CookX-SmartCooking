import {normalizeRecipe} from './recipeAdapter.js'
export const completionQueueKey=user=>`cookx:completion-queue:v1:${user}`
export function createCompletionSync({storage,api,currentUser}){
 const list=user=>JSON.parse(storage.getItem(completionQueueKey(user))||'[]')
 function enqueue(session,provenance='confirmed_session'){
  if(session.status!=='completed'||session.user!==currentUser())throw Error('只能同步当前账号已确认的完成记录')
  const rows=list(session.user),existing=rows.find(r=>r.session_id===session.id)
  if(existing)return existing
  const body={idempotency_key:'completion-'+session.id,session_id:session.id,recipe:normalizeRecipe(session.recipe),recipe_version:session.recipeVersion,started_at:new Date(session.startedAt).toISOString(),completed_at:new Date(session.completedAt).toISOString(),confirmed:true,provenance}
  storage.setItem(completionQueueKey(session.user),JSON.stringify([...rows,body]));return body
 }
 const running=new Map()
 async function sync(user){
  if(user!==currentUser())throw Error('账号已切换')
  if(running.has(user))return running.get(user)
  const task=(async()=>{for(const body of list(user)){
   if(user!==currentUser())throw Error('账号已切换')
   await api('/growth/completions','post',body)
   // Remove only the original entry; preserve additions made while the request was pending.
   storage.setItem(completionQueueKey(user),JSON.stringify(list(user).filter(r=>r.session_id!==body.session_id)))
  }})();running.set(user,task);try{await task}finally{running.delete(user)}
 }
 return {enqueue,sync,list}
}
