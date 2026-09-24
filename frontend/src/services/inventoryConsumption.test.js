import test from 'node:test'
import assert from 'node:assert/strict'
import {consumptionPreview,createConsumption,consumptionKey} from './inventoryConsumption.js'
function setup(mode='success'){
 let rows=[{id:'1',name:'番茄',quantity:2},{id:'2',name:'番茄',quantity:4}],writes=0,receipt=null
 const saved=new Map(),storage={getItem:k=>saved.get(k),setItem:(k,v)=>saved.set(k,v),removeItem:k=>saved.delete(k)}
 const api=createConsumption({storage,makeKey:()=> 'fixed-receipt',read:async()=>structuredClone(rows),lookup:async()=>{if(!receipt)throw Object.assign(Error('missing receipt'),{response:{status:404}});return receipt},write:async(user,body)=>{
  writes++;if(mode==='business')throw Object.assign(Error('conflict'),{response:{status:409}})
  if(!receipt){for(const item of body.items)rows.find(r=>r.id===item.item_id).quantity-=item.quantity;receipt={status:'success',idempotency_key:body.idempotency_key}}
  if(mode==='lost')throw Error('network');return receipt
 }})
 return {api,storage,items:consumptionPreview(rows),writes:()=>writes,change:()=>rows[0].quantity++}
}
test('same-name batches can be selected independently; invalid quantities blocked',()=>{const f=setup();assert.ok(f.items.every(i=>!i.blocked));assert.ok(consumptionPreview([{id:'1',name:'盐',quantity:0.5}])[0].blocked)})
test('selected batch, explicit amount and stable receipt prevent duplicate deductions',async()=>{const f=setup();const result=await f.api.submit('u','s',[{...f.items[0],consumeQuantity:2}]);assert.equal(result[0].quantity,0);assert.equal(result[1].quantity,4);await assert.rejects(()=>f.api.submit('u','s',f.items));assert.equal(f.writes(),1)})
test('lost acknowledgement only queries receipt; later inventory writes do not invalidate it',async()=>{const f=setup('lost');await assert.rejects(()=>f.api.submit('u','s',[f.items[0]]));assert.equal(f.api.pending('u').status,'uncertain');await assert.rejects(()=>f.api.submit('u','s',f.items));f.change();await f.api.reconcile('u');assert.equal(f.writes(),1);assert.equal(f.api.pending('u').status,'confirmed')})
test('definitive rejection with absent receipt allows new reviewed selection',async()=>{const f=setup('business');const request=f.api.submit('u','s',f.items);await assert.rejects(()=>f.api.submit('u','s',f.items));await assert.rejects(()=>request);assert.equal(f.api.pending('u').status,'rejected');assert.equal(f.writes(),1)})
test('corrupt ledger and stale inventory block submission before write',async()=>{const f=setup();await assert.rejects(()=>f.api.submit('u','s',[{...f.items[0],quantity:9}]));assert.equal(f.writes(),0);f.storage.setItem(consumptionKey('u'),'{bad');await assert.rejects(()=>f.api.submit('u','s',f.items));assert.equal(f.writes(),0)})
test('legacy uncertain records cannot be retried or guessed from quantities',async()=>{const f=setup();f.storage.setItem(consumptionKey('u'),JSON.stringify({schemaVersion:1,status:'uncertain',items:[]}));await assert.rejects(()=>f.api.reconcile('u'),/旧版/);await assert.rejects(()=>f.api.retry('u'));assert.equal(f.writes(),0)})
test('invalid count or repeated batch rejected before write',async()=>{for(const selected of [items=>[{...items[0],consumeQuantity:3}],items=>[items[0],items[0]],items=>[{...items[0],consumeQuantity:0.5}]]){const f=setup();await assert.rejects(()=>f.api.submit('u','s',selected(f.items)));assert.equal(f.writes(),0)}})
