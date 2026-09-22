import test from 'node:test'
import assert from 'node:assert/strict'
import { blankItem, serializeItem, isoFromLocal, localDateTime, verifyMutation } from './inventoryFields.js'
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
test('edit sends only changed supported fields and preserves unknown server fields',()=>{
 const original={name:'tomato',quantity:2,shelf_life:2.5,storage_type:'冷藏',purchase_time:'2025-01-01',unknown:7}
 assert.deepEqual(serializeItem({...original,name:'番茄',quantity:3,purchase_time:'bad'},original),{quantity:3})
 assert.throws(()=>serializeItem({...original,shelf_life:1.5},original),/整数天/)
 assert.throws(()=>serializeItem({...original,storage_type:''},original),/A3/)
})
test('read-back verifies quantity and explicit dates even when backend merges records',()=>{
 const item={name:'番茄',quantity:1,purchase_time:'2025-01-01T00:00:00Z'}
 const before=[{id:'a',...item,quantity:2}]
 assert.equal(verifyMutation({before,payload:[item]},[{id:'a',...item,name:'西红柿',quantity:3}]),true)
 assert.equal(verifyMutation({before,payload:[item]},[{id:'a',...item,quantity:3,purchase_time:'2025-02-01T00:00:00Z'}]),false)
 assert.equal(verifyMutation({id:'a',payload:{quantity:4}},[{id:'a',quantity:3}]),false)
 assert.equal(verifyMutation({before:[],payload:[item,item]},[{...item,quantity:1}]),false)
})
