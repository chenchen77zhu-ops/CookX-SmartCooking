import test from 'node:test'
import assert from 'node:assert/strict'
import {serializeRecommendationPreferences as serialize,blankRecommendationPreferences as blank,loadRecommendationPreferences as load,recommendationPreferenceKey as key} from './recommendationPreferences.js'
test('empty targets omitted; explicit constraints preserve backend units and weights',()=>{
 assert.deepEqual(serialize(blank()),{})
 assert.deepEqual(serialize({...blank(),budget:'23.5',difficulty_target:'easy',min_protein_g:'20'}),{budget:23.5,difficulty_target:'easy',nutrition_target:{min_protein_g:20}})
 for(const value of [0,-1,'NaN',Infinity])assert.throws(()=>serialize({...blank(),budget:value}))
 assert.throws(()=>serialize({...blank(),difficulty_target:'unknown'}))
})
test('preferences isolated by user with corrupt state fallback',()=>{
 const map=new Map([[key('a'),JSON.stringify({budget:20})],[key('bad'),'{']]),storage={getItem:k=>map.get(k)}
 assert.equal(load(storage,'a').budget,20);assert.equal(load(storage,'b').budget,'');assert.deepEqual(load(storage,'bad'),blank())
})
