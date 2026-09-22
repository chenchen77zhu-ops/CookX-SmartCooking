import test from 'node:test'
import assert from 'node:assert/strict'
import { createFreshnessLoader, hasScore, freshnessStatus, freshnessError } from './inventoryFreshness.js'
test('zero is a real score; unknown time stays unknown even with storage score', () => {
  assert.equal(hasScore(0), true); assert.equal(hasScore(null), false)
  assert.equal(freshnessStatus({ fresh_score: 100, component_scores: { T: null } }).className, 'unknown')
  assert.equal(freshnessStatus({ expired: true }).label, '已过期')
  assert.equal(freshnessStatus({ expiring_soon: true }).label, '临期')
})
test('map by item id and ignore records removed during normalization', async () => {
  let state
  const loader = createFreshnessLoader(async () => ({items:[{item_id:'b',fresh_score:0},{item_id:'old',fresh_score:90}]}), s => state=s)
  await loader.load('u',[{id:'a'},{id:'b'}])
  assert.equal(state.items.b.fresh_score,0); assert.equal(state.items.a,undefined); assert.equal(state.items.old,undefined)
})
test('late responses cannot overwrite a new user or refresh', async () => {
  let resolveOld, state
  const loader=createFreshnessLoader(id => id==='old' ? new Promise(resolve=>resolveOld=resolve) : Promise.resolve({items:[{item_id:'n'}]}), s=>state=s)
  const old=loader.load('old',[{id:'o'}])
  await loader.load('new',[{id:'n'}])
  resolveOld({items:[{item_id:'o'}]}); await old
  assert.deepEqual(Object.keys(state.items),['n'])
  loader.dispose(); assert.equal(state.status,'idle')
})
test('failure clears old assessments and supports retry', async () => {
  let state, fail=true
  const loader=createFreshnessLoader(async()=>{if(fail)throw new Error('offline');return{items:[]}},s=>state=s)
  await loader.load('u',[]);assert.equal(state.status,'error')
  fail=false;await loader.load('u',[]);assert.equal(state.status,'success')
  assert.match(freshnessError({code:'ECONNABORTED'}),/超时/)
  assert.match(freshnessError({response:{status:404,data:{detail:'User not found'}}}),/重新登录/)
  assert.match(freshnessError({response:{status:422}}),/参数/)
})
