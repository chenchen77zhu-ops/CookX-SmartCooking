import test from 'node:test'
import assert from 'node:assert/strict'
import { normalizeRecipe, safeHistory } from './recipeAdapter.js'
test('legacy and structured recipes normalize without invented durations',()=>{
 const r=normalizeRecipe({dish_name:' 菜 ',steps:['准备',{content:'热锅',duration:30}]})
 assert.equal(r.steps[0].time_estimate,null);assert.equal(r.steps[1].time_estimate,30)
 assert.equal(normalizeRecipe(JSON.stringify(r)).dish_name,'菜')
})
test('invalid JSON, empty and malformed recipes cannot start',()=>{
 for(const recipe of ['{bad',null,{dish_name:'菜',steps:[]},{dish_name:'菜',steps:[{}]},{dish_name:'菜',steps:[{text:'煮',time_estimate:-1}]},{dish_name:'菜',steps:[{text:'煮',time_estimate:true}]}])assert.throws(()=>normalizeRecipe(recipe))
 assert.equal(safeHistory([{role:'assistant',recipe:{},content:'原文'}])[0].recipe,undefined)
})

import {reactive} from 'vue'
test('nested API recipe metadata detaches Vue proxies for session snapshots',()=>{const recipe=reactive({dish_name:'菜',steps:[{text:'热锅',time_estimate:30}],nutrition:{calories:'200'},ingredients_list:[{item:'鸡蛋',amount:'1'}]});const normalized=normalizeRecipe(recipe);assert.doesNotThrow(()=>structuredClone(normalized));recipe.nutrition.calories='300';assert.equal(normalized.nutrition.calories,'200')})
