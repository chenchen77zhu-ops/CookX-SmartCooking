import {Capacitor} from '@capacitor/core'
import {LocalNotifications as notifications} from '@capacitor/local-notifications'
import {App} from '@capacitor/app'
import {ref} from 'vue'
import {readUserId} from './recognitionDraft.js'
import {notificationId,reminderId} from './cookingReminders.js'
const native=()=>Capacitor.getPlatform()==='android'
export const notificationMessage=ref('提醒默认关闭，可主动开启。')
const preference=user=>`cookx:reminders:v1:${user}`
export const remindersEnabled=user=>localStorage.getItem(preference(user))==='true'
let queue=Promise.resolve(),initialized=false
const serialized=fn=>{queue=queue.catch(()=>{}).then(fn);return queue.catch(e=>{notificationMessage.value=`系统提醒不可用：${e.message}；请返回页面核对计时。`})}
async function cancelAll(){if(!native())return;const pending=await notifications.getPending();if(pending.notifications.length)await notifications.cancel({notifications:pending.notifications.map(n=>({id:n.id}))});const delivered=await notifications.getDeliveredNotifications();if(delivered.notifications.length)await notifications.removeDeliveredNotifications({notifications:delivered.notifications})}
export async function setReminders(user,enabled){
  if(user!==readUserId())return
  if(enabled && native()){const p=await notifications.requestPermissions();if(p.display!=='granted'){notificationMessage.value='通知权限未授予，请保持页面可见并查看计时状态。';return false}}
  if(user!==readUserId())return false
  localStorage.setItem(preference(user),String(enabled))
  if(!enabled)await serialized(cancelAll)
  notificationMessage.value=enabled?(native()?'已开启提醒，正在核对系统权限。':'已开启页面提醒；浏览器关闭或后台暂停时不能保证通知。'):'提醒已关闭；状态卡片仍可查看。'
  return enabled
}
export async function exactSettings(){if(native())await notifications.changeExactNotificationSetting()}
export function reconcileTimer(store){return serialized(async()=>{
  if(!native())return
  const s=store.engine.state,permitted=await notifications.checkPermissions()
  const pending=await notifications.getPending()
  const enabled=s && store.ready && s.user===readUserId() && remindersEnabled(s.user) && permitted.display==='granted' && s.status==='active'
  const timer=s?.timers[s.stepIndex],future=enabled && timer?.deadline>Date.now()
  const key=future?reminderId(s,'timer',`${s.stepIndex}:${timer.round}`):null,id=key?notificationId(key):null
  const obsolete=pending.notifications.filter(n=>n.extra?.cookx && (n.id!==id || n.extra?.deadline!==timer?.deadline))
  if(obsolete.length)await notifications.cancel({notifications:obsolete.map(n=>({id:n.id}))})
  if(!future)return
  const exact=(await notifications.checkExactNotificationSetting()).exact_alarm==='granted'
  notificationMessage.value=exact?'系统计时提醒已开启。':'未授予精确计时权限，系统提醒可能延迟；返回前台会核对计时。'
  if(!pending.notifications.some(n=>n.id===id && n.extra?.deadline===timer.deadline)){
    await notifications.schedule({notifications:[{id,title:'CookX 步骤计时',body:`第 ${s.stepIndex+1} 步计时结束，请检查状态。`,schedule:{at:new Date(timer.deadline),allowWhileIdle:true},isExactNotification:exact,extra:{cookx:true,user:s.user,key,deadline:timer.deadline}}]})
  }
  // This flag suppresses a second foreground notification; the status card is still produced.
  timer.notificationKey=key;store.engine.persist()
})}
export function notifyEvent(user,event){return serialized(async()=>{
  if(!native() || user!==readUserId() || !remindersEnabled(user))return
  if((await notifications.checkPermissions()).display!=='granted')return
  await notifications.schedule({notifications:[{id:notificationId(event.key),title:'CookX 烹饪提醒',body:event.text,isExactNotification:false,extra:{cookx:true,user,key:event.key}}]})
})}
export function initializeNotifications(){
  if(initialized)return;initialized=true
  // Restoration always requires user confirmation after an app restart.
  serialized(cancelAll)
  window.addEventListener('cookx:user-changed',()=>serialized(cancelAll))
  if(native())App.addListener('appStateChange',({isActive})=>{if(isActive)window.dispatchEvent(new Event('cookx:foreground'))})
}
