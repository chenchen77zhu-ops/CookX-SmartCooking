import test from 'node:test'
import assert from 'node:assert/strict'
import { blankItem, serializeItem, isoFromLocal, localDateTime, verifyMutation, inventoryErrorMessage, inventoryEditForm } from './inventoryFields.js'
test('unknown metadata is omitted, never defaulted',()=>{
 const data=serializeItem({...blankItem(),name:'牛肉'})
 assert.deepEqual(data,{name:'牛肉',quantity:1})
})
test('dates convert local wall clock to UTC without accepting invalid dates',()=>{
 const local='2025-06-15T08:30'
 assert.equal(localDateTime(isoFromLocal(local)),local)
 assert.match(isoFromLocal(local),/Z$/)
 assert.throws(()=>isoFromLocal('2025-02-30T08:30'),/日期/)
 assert.throws(()=>serializeItem({...blankItem(),name:'牛肉',purchase_time:'2099-01-01T00:00'}),/购买时间/)
 assert.throws(()=>serializeItem({...blankItem(),name:'牛肉',purchase_time:'2025-02-01T00:00',expiry_date:'2025-01-01T00:00'}),/到期时间/)
 for(const quantity of [0,-1,1.5,'']) assert.throws(()=>serializeItem({...blankItem(),name:'牛肉',quantity}),/数量/)
 for(const shelf_life of [0,-1,'NaN']) assert.throws(()=>serializeItem({...blankItem(),name:'牛肉',shelf_life}),/保质期/)
})
test('edit preserves untouched date precision, supports fractions and explicit null',()=>{
 const original={id:'x',name:'tomato',quantity:2,shelf_life:2.5,storage_type:'冷藏',purchase_time:'2025-01-01T01:02:03.456+08:00',add_time:'2025-01-01T02:00:00+08:00',unknown:7}
 const form=inventoryEditForm(original)
 assert.deepEqual(serializeItem({...form,name:'番茄',quantity:3},original),{quantity:3})
 assert.deepEqual(serializeItem({...form,shelf_life:1.5,storage_type:''},original),{shelf_life:1.5,storage_type:null})
 assert.deepEqual(serializeItem({...form,purchase_time:''},original),{purchase_time:null})
 assert.throws(()=>serializeItem({...form,purchase_time:''},{...original,purchase_date:'2025-01-01'}),/旧购买/)
 assert.equal(verifyMutation({id:'x',payload:{purchase_time:null}},[{id:'x',purchase_time:null}]),true)
})
test('read-back verifies quantity and explicit dates even when backend merges records',()=>{
 const item={name:'番茄',quantity:1,purchase_time:'2025-01-01T00:00:00Z'}
 const before=[{id:'a',...item,quantity:2}]
 assert.equal(verifyMutation({before,payload:[item]},[{id:'a',...item,name:'西红柿',quantity:3}]),true)
 assert.equal(verifyMutation({before,payload:[item]},[{id:'a',...item,quantity:3,purchase_time:'2025-02-01T00:00:00Z'}]),false)
 assert.equal(verifyMutation({id:'a',payload:{quantity:4}},[{id:'a',quantity:3}]),false)
 assert.equal(verifyMutation({before:[],payload:[item,item]},[{...item,quantity:1}]),false)
})

test('API validation and missing-record messages remain actionable',()=>{
 assert.match(inventoryErrorMessage({response:{data:{detail:[{loc:['body',0,'expiry_date'],msg:'must be later than add_time'}]}}}),/^到期时间：/)
 assert.equal(inventoryErrorMessage({response:{data:{detail:'库存项目不存在'}}}),'库存项目不存在')
})
