export const adjustmentRules = Object.freeze({
  seasoning: {label:'缺调料', reason:'调料种类与配比未知，不能自动给出替代比例。', advice:'开始后续操作前核对缺少的调料；可以暂缓可选调味，必要时停止本菜谱并选择材料齐全的做法。'},
  salty: {label:'过咸', reason:'无法从描述判断盐量或稀释比例。', advice:'后续调味前先检查味道，暂缓额外加盐或含盐调味料；是否调整做法由你确认。'},
  dry: {label:'偏干', reason:'尚不清楚锅内油量、烹饪方式与食材状态，不能自动建议加水。', advice:'继续后续操作前检查锅内状态和菜谱要求，必要时暂停加热；不要向热油中直接加水。'},
  undercooked: {label:'未熟', reason:'锅面温度不能代表食材内部熟度，也不能代替内部温度测量。', advice:'进入后续操作前检查食材是否达到该食材要求的熟制条件；需要延长观察时，请自行设置计时，不把计时结束视为已熟。'}
})
export function previewAdjustment(session, type) {
  const rule=adjustmentRules[type]
  if(!rule || session?.status!=='active')throw new Error('当前没有可调整的烹饪会话')
  const index=session.recipe.steps.findIndex((_,i)=>i>session.stepIndex && !session.timers[i].visited)
  return {schemaVersion:1,sessionId:session.id,baseVersion:session.recipeVersion,type,...rule,patches:index<0?[]:[{index,before:session.recipe.steps[index].text,after:`${session.recipe.steps[index].text}\n调整提示：${rule.advice}`}]}
}
export function applyAdjustment(session, preview, now=Date.now()) {
  if(session?.status!=='active' || preview?.sessionId!==session.id || preview.baseVersion!==session.recipeVersion)throw new Error('菜谱已变化，请重新预览')
  // Recreate the allowed local patch, instead of accepting arbitrary saved/AI text.
  const expected=previewAdjustment(session,preview.type)
  if(JSON.stringify(expected.patches)!==JSON.stringify(preview.patches) || !expected.patches.length)throw new Error('没有可修改的后续步骤，请使用检查建议和手动计时')
  for(const p of expected.patches){if(session.timers[p.index].visited || p.index<=session.stepIndex)throw new Error('已进入的步骤不能修改')}
  const record={...expected,id:`${session.id}:${session.recipeVersion+1}`,appliedAt:now,version:session.recipeVersion+1,undone:false}
  for(const p of record.patches)session.recipe.steps[p.index].text=p.after
  session.recipeVersion++;session.adjustments.push(record);return record
}
export function undoAdjustment(session, id, now=Date.now()) {
  const record=session?.adjustments.find(r=>r.id===id)
  if(session?.status!=='active' || !record || record.undone || record.patches.some(p=>session.timers[p.index].visited || p.index<=session.stepIndex || session.recipe.steps[p.index].text!==p.after))throw new Error('相关步骤已进入或有后续修改，不能撤销')
  for(const p of record.patches)session.recipe.steps[p.index].text=p.before
  record.undone=true;record.undoneAt=now;session.recipeVersion++
}
