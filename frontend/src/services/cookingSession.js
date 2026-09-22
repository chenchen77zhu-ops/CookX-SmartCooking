import {applyAdjustment,undoAdjustment} from './cookingAdjustments.js'
import { normalizeRecipe } from './recipeAdapter.js'
export const sessionKey = user => `cookx:cooking:v1:${user}`
export function createCookingSession(user, {storage, now=Date.now, makeId=()=>globalThis.crypto.randomUUID()}={}) {
  let state=null, warning=''
  try {
    const saved=JSON.parse(storage?.getItem(sessionKey(user)) || 'null')
    if(saved) {
      if(saved.schemaVersion!==1 || saved.user!==user || !Array.isArray(saved.timers) || !Number.isInteger(saved.stepIndex) || saved.stepIndex<0 || saved.stepIndex>=saved.recipe?.steps?.length || saved.timers.length!==saved.recipe.steps.length || !saved.timers.every(t=>t && Number.isFinite(t.remainingMs) && t.remainingMs>=0 && (t.deadline===null || Number.isFinite(t.deadline)) && Number.isInteger(t.round)))throw new Error('invalid snapshot')
      state={...saved,recipe:normalizeRecipe(saved.recipe)}
    }
  } catch {warning='本地烹饪记录损坏或版本不兼容，请重新选择菜谱'}
  function persist() {try{storage?.setItem(sessionKey(user),JSON.stringify(state))}catch{warning='本地保存不可用，刷新后可能无法恢复'}}
  const remaining=(index=state?.stepIndex)=>{const t=state?.timers[index];return t?Math.max(0,t.deadline===null?t.remainingMs:t.deadline-now()):0}
  function enter(index) {
    const t=state.timers[index]
    state.stepIndex=index
    if(!t.visited){t.visited=true;t.remainingMs=(state.recipe.steps[index].time_estimate||0)*1000;if(t.remainingMs>0){t.deadline=now()+t.remainingMs;t.round++}}
    persist()
  }
  function pause(){if(!state)return;const t=state.timers[state.stepIndex];t.remainingMs=remaining();t.deadline=null;persist()}
  function resume(){if(!state||state.status!=='active')return;const t=state.timers[state.stepIndex];if(t.deadline===null&&t.remainingMs>0)t.deadline=now()+t.remainingMs;persist()}
  function start(recipe, replace=false) {
    const normalized=normalizeRecipe(recipe)
    if(state?.status==='active'&&!replace)throw new Error('已有未完成烹饪，请先确认替换')
    if(state?.status==='completed'){try{const key=`cookx:cooking-history:v1:${user}`,history=JSON.parse(storage?.getItem(key)||'[]');storage?.setItem(key,JSON.stringify([...history.filter(s=>s.id!==state.id),state].slice(-50)))}catch{throw new Error('完成记录归档失败，请导出或释放本地空间后再开始')}}
    state={schemaVersion:1,user,id:makeId(),recipe:normalized,recipeVersion:1,stepIndex:0,status:'active',startedAt:now(),adjustments:[],reminders:[],consumption:'not_requested',timers:normalized.steps.map(()=>({visited:false,remainingMs:0,deadline:null,round:0}))}
    enter(0)
  }
  function move(delta){if(!state||state.status!=='active')return false;const target=state.stepIndex+delta;if(target<0||target>=state.recipe.steps.length)return false;pause();enter(target);return true}
  function setTimer(seconds){if(!Number.isFinite(seconds)||seconds<=0||seconds>86400)throw new Error('计时秒数须大于 0 且不超过 24 小时');if(!state||state.status!=='active')return;const t=state.timers[state.stepIndex];t.remainingMs=seconds*1000;t.deadline=now()+t.remainingMs;t.round++;persist()}
  function finish(){if(!state)return;pause();state.status='completed';state.completedAt=now();persist()}
  function adjust(preview){const record=applyAdjustment(state,preview,now());persist();return record}
  function undo(id){undoAdjustment(state,id,now());persist()}
  return {adjust,undo,get state(){return state},get warning(){return warning},remaining,start,move,pause,resume,setTimer,finish,persist}
}
