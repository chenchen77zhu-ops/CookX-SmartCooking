import test from 'node:test'
import assert from 'node:assert/strict'
import {readFileSync} from 'node:fs'
import {createTemperatureStreamParser} from '../services/temperatureStream.js'
import {createTemperatureEngine} from './engine.js'
const fixture=JSON.parse(readFileSync(new URL('./fixtures/stationary-indoor-hardware.json',import.meta.url),'utf8'))
test('real stationary indoor capture accepts legacy Bluetooth frames without asserting heating',()=>{
 const engine=createTemperatureEngine(),samples=[],rejected=[],results=[]
 let at=0
 const parser=createTemperatureStreamParser(s=>{samples.push(s);results.push(engine.push(s))},{now:()=>at,onReject:r=>rejected.push(r)})
 for(const c of fixture.chunks){at=c.receivedAt;parser.append(c.text)}
 assert.equal(samples.length,90);assert.equal(rejected.length,2)
 assert.ok(samples.every(s=>s.protocolVersion===1 && s.deviceTimeMs===null && s.sequence===null))
 assert.ok(results.every(s=>s.suggestion===null && s.alert===null))
 assert.equal(engine.getSnapshot().quality,'usable')
 assert.equal(engine.getSnapshot().phase,'steady')
 assert.equal(engine.getSnapshot().phaseLabel,'温度稳定')
 // Inject a missing-data interval after the real trace; this is not a physical disconnect test.
 const stale=engine.expire(samples.at(-1).updatedAt+5001)
 assert.equal(stale.quality,'invalid');assert.equal(stale.risk,'unknown');assert.equal(stale.suggestion,null)
})
