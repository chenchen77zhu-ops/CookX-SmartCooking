export const nutritionFields = [
 ['max_calories_kcal','每份热量上限（kcal）'],['min_protein_g','每份蛋白质下限（g）'],['max_fat_g','每份脂肪上限（g）'],['max_carbohydrates_g','每份碳水上限（g）']
]
export const recommendationPreferenceKey=user=>`cookx:recommendation-preferences:v1:${user}`
export const blankRecommendationPreferences=()=>({budget:'',difficulty_target:'',...Object.fromEntries(nutritionFields.map(([key])=>[key,'']))})
export function serializeRecommendationPreferences(form){
 const result={},nutrition={}
 for(const [key,label] of [['budget','整道菜预算'],...nutritionFields]){
  if(form[key]==='' || form[key]==null)continue
  const value=Number(form[key]);if(!Number.isFinite(value)||value<=0)throw new Error(`${label}必须是正数`)
  if(key==='budget')result.budget=value;else nutrition[key]=value
 }
 if(form.difficulty_target){if(!['easy','medium','hard'].includes(form.difficulty_target))throw new Error('请选择有效难度');result.difficulty_target=form.difficulty_target}
 if(Object.keys(nutrition).length)result.nutrition_target=nutrition
 return result
}
export function loadRecommendationPreferences(storage,user){
 try{return {...blankRecommendationPreferences(),...JSON.parse(storage.getItem(recommendationPreferenceKey(user))||'{}')}}catch{return blankRecommendationPreferences()}
}
