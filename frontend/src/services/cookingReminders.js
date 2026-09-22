export const reminderId = (session,type,object) => `${session.id}:${type}:${object}`
const rank={info:0,warning:1,danger:2}
export function recordReminder(session,{type,object,text,risk='info',once=false},now=Date.now()) {
  if(!session)return null
  const key=reminderId(session,type,object),previous=session.reminders.find(e=>e.key===key)
  if(previous && (once || (now-previous.at<300000 && (rank[risk]??0)<=(rank[previous.risk]??0))))return null
  const event={schemaVersion:1,key,type,object,text,risk,at:now,dismissed:false}
  session.reminders=session.reminders.filter(e=>e.key!==key);session.reminders.push(event);session.reminders=session.reminders.slice(-100)
  return event
}
export function timerReminder(session,now=Date.now()) {
  const timer=session?.timers[session.stepIndex]
  if(session?.status!=='active' || !timer || timer.deadline===null || timer.deadline>now)return null
  return recordReminder(session,{type:'timer',object:`${session.stepIndex}:${timer.round}`,once:true,text:`第 ${session.stepIndex+1} 步计时结束，请检查烹饪状态；不会自动跳步。`},now)
}
export function notificationId(key){let hash=2166136261;for(const c of key){hash^=c.charCodeAt(0);hash=Math.imul(hash,16777619)}return (hash>>>0)%2000000000+1}
