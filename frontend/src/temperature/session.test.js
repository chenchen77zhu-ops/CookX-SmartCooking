import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope } from 'vue'
import { useTemperatureIntelligence } from './useTemperatureIntelligence.js'

const realContext={stepId:'real-step',targetRange:[150,180]}
const replayData=()=>({source:'physics_simulation',context:{stepId:'replay-step',targetRange:[170,190]},
  samples:Array.from({length:60},(_,i)=>({updatedAt:i*500,temperature:100+i*.1,valid:true})),events:[]})
function setup(t) {
  t.mock.timers.enable({apis:['Date','setInterval','setTimeout'],now:1800000000000})
  const stored=new Map(),workers=[];let blob
  const globals={location:{href:'http://localhost/'},localStorage:{setItem:(k,v)=>stored.set(k,v),getItem:k=>stored.get(k)??null},
    document:{createElement:()=>({click(){}})},fetch:async()=>({ok:true,json:async()=>replayData()}),
    Worker:class {messages=[];constructor(){workers.push(this)}postMessage(m){this.messages.push(m)}terminate(){this.terminated=true}}}
  const old=Object.fromEntries(Object.keys(globals).map(k=>[k,Object.getOwnPropertyDescriptor(globalThis,k)]))
  for(const [k,v] of Object.entries(globals))Object.defineProperty(globalThis,k,{value:v,writable:true,configurable:true})
  t.mock.method(URL,'createObjectURL',b=>{blob=b;return 'blob:test'})
  t.mock.method(URL,'revokeObjectURL',()=>{})
  const scope=effectScope(),thermal=scope.run(()=>useTemperatureIntelligence('test-session'))
  thermal.setContext(realContext)
  t.after(()=>{scope.stop();for(const [k,d] of Object.entries(old)){if(d)Object.defineProperty(globalThis,k,d);else delete globalThis[k]}})
  return {thermal,stored,workers,read:async(saved=false)=>{thermal.exportSession(saved);return JSON.parse(await blob.text())},
    feed:(count=20,temp=100)=>{for(let i=0;i<count;i++){t.mock.timers.tick(500);thermal.receive({updatedAt:Date.now(),receivedAt:Date.now(),temperature:temp,valid:true})}}}
}
function respond(worker,request) {
  worker.onmessage({data:{type:'prediction',id:request.id,at:request.at,epoch:request.epoch,
    logits:[9,0,0,0,0,0],forecast:[.2,.3,.4,.2,.3,.4,.2,.3,.4],qualityLogit:2,latencyMs:1}})
}
test('replay context and correction use the sample timeline',async t=>{
  const {thermal,read}=setup(t)
  await thermal.startReplay();t.mock.timers.tick(2000)
  thermal.setContext({stepId:'changed',targetRange:[80,100]});thermal.confirm('ingredient_added')
  const data=await read()
  assert.equal(data.timeBase,'relative_ms');assert.equal(data.contexts.at(-1).at,1500)
  assert.equal(data.events.at(-1).at,1500);assert.equal(data.samples.at(-1).updatedAt,1500)
})
test('stop replay preserves recording, restores context and keeps UI reset reason stable',async t=>{
  const {thermal,read,feed}=setup(t)
  await thermal.startReplay();t.mock.timers.tick(2000);thermal.stopReplay()
  const before=thermal.assessment.value
  assert.match(before.reasons[0],/回放已结束/)
  t.mock.timers.tick(1000);assert.deepEqual(thermal.assessment.value,before)
  assert.equal((await read(true)).source,'simulation')
  thermal.confirm('probe_moved');feed(20,160)
  const data=await read()
  assert.equal(data.source,'device');assert.equal(data.timeBase,'unix_ms')
  assert.equal(data.contexts[0].context.stepId,'real-step')
  assert.ok(data.events.every(e=>e.at>1000000000000))
  assert.ok(data.samples.every(s=>s.source==='device'))
  assert.equal(thermal.assessment.value.quality,'usable')
  assert.match(thermal.assessment.value.reasons.join(' '),/150–180/)
})
test('restarting replay resets clock before any manual confirmation',async t=>{
  const {thermal,read}=setup(t)
  await thermal.startReplay();t.mock.timers.tick(6000)
  await thermal.startReplay();thermal.confirm('probe_moved')
  assert.equal((await read()).events[0].at,0)
})
test('a late failed replay request cannot cancel a newer replay',async t=>{
  const {thermal}=setup(t)
  let reject
  globalThis.fetch=()=>new Promise((_,r)=>{reject=r})
  const old=thermal.startReplay()
  globalThis.fetch=async()=>({ok:true,json:async()=>replayData()})
  await thermal.startReplay();reject(new Error('late network error'));await old
  assert.equal(thermal.replaying.value,true)
  t.mock.timers.tick(500);assert.equal(thermal.history.value.length,1)
})
test('cancelled replay response never starts its timer',async t=>{
  const {thermal}=setup(t)
  let resolve
  globalThis.fetch=()=>new Promise(r=>{resolve=r})
  const pending=thermal.startReplay();thermal.stopReplay()
  resolve({ok:true,json:async()=>replayData()});await pending
  t.mock.timers.tick(1000)
  assert.equal(thermal.replaying.value,false);assert.equal(thermal.history.value.length,0)
})
test('worker outputs are invalidated by steps and expire before a stale safe-looking forecast',async t=>{
  const {thermal,workers,feed}=setup(t)
  thermal.setExperimental(true)
  const worker=workers[0]
  worker.onmessage({data:{type:'ready',manifest:{modelVersion:'test-model'}}})
  feed(12)
  const first=worker.messages.find(m=>m.type==='infer');assert.ok(first)
  thermal.setContext({stepId:'new',targetRange:[90,120]});respond(worker,first)
  assert.equal(thermal.prediction.value,null)
  feed(12);const second=worker.messages.filter(m=>m.type==='infer').at(-1)
  assert.notEqual(second.id,first.id);respond(worker,second)
  assert.ok(thermal.prediction.value)
  t.mock.timers.tick(3500)
  assert.equal(thermal.assessment.value.quality,'usable')
  assert.equal(thermal.prediction.value,null)
})
test('invalid readings suppress model output and storage failure remains exportable',async t=>{
  const {thermal,read,feed,workers}=setup(t)
  thermal.setExperimental(true);const worker=workers[0]
  worker.onmessage({data:{type:'ready',manifest:{modelVersion:'test-model'}}})
  feed(12);const pending=worker.messages.find(m=>m.type==='infer')
  t.mock.timers.tick(500)
  thermal.receive({updatedAt:Date.now(),temperature:null,valid:false});respond(worker,pending)
  assert.equal(thermal.prediction.value,null);assert.equal(thermal.assessment.value.quality,'invalid')
  globalThis.localStorage.setItem=()=>{throw Error('quota')}
  t.mock.timers.tick(10000);feed(1)
  assert.match(thermal.storageMessage.value,/本地存储不可用/)
  const data=await read();assert.ok(data.samples.length);assert.equal(data.modelVersion,'test-model')
})
