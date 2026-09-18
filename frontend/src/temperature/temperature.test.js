import test from 'node:test'
import assert from 'node:assert/strict'
import { createTemperatureStreamParser, parseTemperatureFrame } from '../services/temperatureStream.js'
import { createTemperatureEngine } from './engine.js'
import { adaptCookingContext } from './context.js'
import { buildFeatures } from './features.js'
import { acceptPrediction } from './prediction.js'
const sample=(t,temp,extra={})=>({updatedAt:t,temperature:temp,valid:true,source:'device',...extra})
const feed=(engine,start=0,temp=100,count=30,slope=0)=>{let result;for(let i=0;i<count;i++)result=engine.push(sample(start+i*500,temp+i*.5*slope));return result}

test('legacy firmware and metadata absence',()=>{
  assert.equal(parseTemperatureFrame('TEMP:160.2').temperature,160.2)
  assert.equal(parseTemperatureFrame('160.2').ambientTemperature,null)
  for(const bad of ['','0x12','1e2','TEMP:NaN','Infinity',' TEMP: '])assert.equal(parseTemperatureFrame(bad),null)
})
test('v2 fragmentation, invalid reading, boot and sequence rejection',()=>{
  let rows=[],rejects=[]
  const p=createTemperatureStreamParser(s=>rows.push(s),{now:()=>1000,onReject:r=>rejects.push(r)})
  p.append('CX2,abcdef01,0,100,150.2,');p.append('24.2,1\nCX2,abcdef01,1,600,,24.1,0\n')
  assert.equal(rows.length,2);assert.equal(rows[1].valid,false);assert.equal(rows[1].temperature,null)
  p.append('CX2,abcdef01,1,600,160,24,1\n')
  assert.equal(rows.length,2);assert.deepEqual(rejects,['out-of-order'])
  p.append('CX2,abcdef02,0,10,160,24,1\n')
  assert.equal(rows[2].discontinuity,true)
})
test('oversized frames cannot leak a numeric tail',()=>{
  const rows=[];const p=createTemperatureStreamParser(s=>rows.push(s))
  p.append('a'.repeat(500)+'120\nTEMP:140\n')
  assert.equal(rows.length,1);assert.equal(rows[0].temperature,140)
})
test('malformed v2 and gaps',()=>{
  assert.equal(parseTemperatureFrame('CX2,abcdef01,0,0,400,25,1'),null)
  assert.equal(parseTemperatureFrame('CX2,abcdef01,0,0,100,25,0'),null)
  const rows=[];const p=createTemperatureStreamParser(s=>rows.push(s))
  p.append('CX2,abcdef01,0,0,100,25,1\nCX2,abcdef01,3,1500,110,25,1\n')
  assert.equal(rows[1].discontinuity,true)
})
test('warmup and causal trend',()=>{
  const e=createTemperatureEngine();assert.equal(e.push(sample(0,25)).quality,'suspect')
  const r=feed(e,500,26,40,1)
  assert.equal(r.phase,'preheat');assert.equal(r.quality,'usable')
})
test('unconfirmed sudden drop refuses action, recovery does not bridge segments',()=>{
  const e=createTemperatureEngine();feed(e,0,180)
  const r=e.push(sample(15000,80));assert.equal(r.quality,'suspect');assert.equal(r.suggestion,null)
  assert.equal(e.getWindow().samples.length,1);assert.equal(r.phase,'unknown')
  assert.equal(feed(e,15500,81,8,1).quality,'suspect')
})
test('identical sensor-move and ingredient trajectories are observationally identical',()=>{
  const a=createTemperatureEngine(),b=createTemperatureEngine()
  for(let i=0;i<40;i++){const t=i<30?180:80+i-30;assert.deepEqual(a.push(sample(i*500,t)),b.push(sample(i*500,t)))}
  assert.equal(a.getSnapshot().suggestion,null)
})
test('user confirmation differentiates recovery and cooling',()=>{
  const e=createTemperatureEngine();feed(e)
  e.confirm('ingredient_added',15000);assert.equal(feed(e,15000,80,40,1).phase,'recovery')
  e.confirm('heat_off',35000);assert.equal(feed(e,35000,110,40,-1).phase,'cooling')
})
test('context changes and gaps reset decisions; no false safe state',()=>{
  const e=createTemperatureEngine();feed(e)
  e.setContext({stepId:'new',targetRange:[80,100]})
  assert.equal(e.getSnapshot().risk,'unknown')
  assert.equal(feed(e,30000,120).risk,'warning')
  assert.equal(e.push(sample(60000,120)).quality,'suspect')
  assert.equal(e.expire(65000).risk,'unknown')
  assert.equal(e.getWindow().samples.length,0)
})
test('invalid and out-of-order samples cannot sustain old advice',()=>{
  const e=createTemperatureEngine();const old=feed(e)
  assert.equal(e.push(sample(1000,300)),old)
  assert.equal(e.push(sample(15000,null,{valid:false})).quality,'invalid')
  assert.equal(e.getSnapshot().suggestion,null)
})
test('persistent high temperature escalates, reminder has cooldown',()=>{
  const e=createTemperatureEngine();let alerts=[]
  for(let i=0;i<90;i++){const r=e.push(sample(i*500,240));if(r.alert)alerts.push(r.alert)}
  assert.equal(e.getSnapshot().risk,'danger')
  assert.equal(alerts[0].risk,'warning');assert.equal(alerts[1].risk,'danger')
  assert.ok(alerts.length<=3)
})
test('recipe adapter rejects vague targets and Fahrenheit',()=>{
  assert.deepEqual(adaptCookingContext({}, {temperature:'160–180 ℃'}).targetRange,[160,180])
  for(const temperature of ['中火','180°F','180','油温180℃即可',[200,100],['100','200']])
    assert.equal(adaptCookingContext({}, {temperature}).targetRange,null)
})
test('feature extraction does not look forward or invent missing temperatures',()=>{
  const x=buildFeatures([sample(0,100),sample(1000,200)],{})
  assert.equal(x.length,960);assert.equal(x[119*8],Math.fround(200/300))
  assert.equal(x[0],0);assert.equal(x[0*8+3],0)
  assert.ok(x.every(Number.isFinite))
})
test('model result rejected after reset, timeout or unusable reading',()=>{
  const m={epoch:2,at:1000,logits:[9,0,0,0,0,0],qualityLogit:3,forecast:[.1,.2,.3,.2,.3,.4,.3,.4,.5]}
  assert.ok(acceptPrediction(m,{quality:'usable'},2,1200))
  assert.equal(acceptPrediction(m,{quality:'usable'},3,1200),null)
  assert.equal(acceptPrediction(m,{quality:'suspect'},2,1200),null)
  assert.equal(acceptPrediction(m,{quality:'usable'},2,5000),null)
  assert.equal(acceptPrediction({...m,forecast:[NaN]},{quality:'usable'},2,1200),null)
})

test('temperature above target is never described as in range',()=>{
  const e=createTemperatureEngine();e.setContext({targetRange:[150,180]})
  const result=feed(e,0,185)
  assert.equal(result.risk,'observing');assert.match(result.suggestion,/高于当前步骤目标温区/)
})
