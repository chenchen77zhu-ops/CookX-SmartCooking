import test from 'node:test'
import assert from 'node:assert/strict'
import {createCookingSession} from './cookingSession.js'
import {previewAdjustment} from './cookingAdjustments.js'
const setup=()=>{const e=createCookingSession('u',{makeId:()=> 's'});e.start({dish_name:'菜',steps:['准备','调味','装盘']});return e}
test('all four local adjustments preserve current step, create versions and undo future changes',()=>{for(const type of ['seasoning','salty','dry','undercooked']){const e=setup(),p=previewAdjustment(e.state,type);const r=e.adjust(p);assert.equal(e.state.recipe.steps[0].text,'准备');assert.equal(e.state.recipeVersion,2);assert.match(e.state.recipe.steps[1].text,/调整提示/);e.undo(r.id);assert.equal(e.state.recipe.steps[1].text,'调味');assert.equal(e.state.recipeVersion,3)}})
test('stale preview and entered steps cannot be rewritten or undone',()=>{const e=setup(),p=previewAdjustment(e.state,'salty');e.adjust(p);assert.throws(()=>e.adjust(p));e.move(1);assert.throws(()=>e.undo(e.state.adjustments[0].id));e.move(-1);assert.equal(previewAdjustment(e.state,'dry').patches[0].index,2);e.move(1);e.move(1);const end=previewAdjustment(e.state,'dry');assert.deepEqual(end.patches,[]);assert.throws(()=>e.adjust(end))})
test('preview tampering rejected and cancellation has no side effects',()=>{const e=setup(),before=JSON.stringify(e.state),p=previewAdjustment(e.state,'seasoning');assert.equal(JSON.stringify(e.state),before);p.patches[0].index=0;assert.throws(()=>e.adjust(p));assert.equal(JSON.stringify(e.state),before)})
