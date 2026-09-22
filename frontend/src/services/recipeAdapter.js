export function normalizeRecipe(input) {
  let recipe=input
  if (typeof recipe === 'string') {
    try { recipe=JSON.parse(recipe.trim().replace(/^```(?:json)?\s*([\s\S]*?)\s*```$/,'$1')) } catch { throw new Error('菜谱 JSON 无法解析，请重新生成') }
  }
  if (!recipe || typeof recipe !== 'object' || Array.isArray(recipe)) throw new Error('菜谱结果为空或结构异常')
  if (typeof recipe.dish_name !== 'string' || !recipe.dish_name.trim()) throw new Error('菜谱缺少菜名')
  if (!Array.isArray(recipe.steps) || !recipe.steps.length) throw new Error('菜谱没有可执行步骤')
  const steps=recipe.steps.map((raw,index)=>{
    const step=typeof raw==='string'?{text:raw}:raw
    if (!step || typeof step !== 'object' || Array.isArray(step)) throw new Error(`第 ${index+1} 步结构异常`)
    const text=step.text ?? step.content
    if (typeof text !== 'string' || !text.trim()) throw new Error(`第 ${index+1} 步内容为空`)
    const duration=step.time_estimate ?? step.duration
    if (duration != null && duration !== '' && (typeof duration==='boolean' || !Number.isFinite(Number(duration)) || Number(duration)<0 || Number(duration)>86400)) throw new Error(`第 ${index+1} 步时长无效（秒）`)
    return {...step,id:`step-${index+1}`,text:text.trim(),time_estimate:duration==null||duration===''?null:Number(duration)}
  })
  return {...recipe,schemaVersion:1,dish_name:recipe.dish_name.trim(),steps,
    ingredients_list:Array.isArray(recipe.ingredients_list)?recipe.ingredients_list.filter(x=>x && typeof x==='object' && typeof x.item==='string'):[],
    used_ingredients:Array.isArray(recipe.used_ingredients)?recipe.used_ingredients.filter(x=>typeof x==='string' && x.trim()):[],
    missing:Array.isArray(recipe.missing)?recipe.missing.filter(x=>typeof x==='string'):[]}
}
export function safeHistory(rows) {
  if (!Array.isArray(rows)) return []
  return rows.filter(row=>row && ['user','assistant'].includes(row.role)).map(row=>{
    const clean={role:row.role,content:typeof row.content==='string'?row.content:''}
    if(row.recipe) {try{clean.recipe=normalizeRecipe(row.recipe)}catch(error){clean.content+='\n'+error.message}}
    return clean
  })
}
