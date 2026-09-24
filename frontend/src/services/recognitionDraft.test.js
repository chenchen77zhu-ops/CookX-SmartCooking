import test from 'node:test'
import assert from 'node:assert/strict'
import { saveDraft, loadDraft, clearDraft, recognitionItem } from './recognitionDraft.js'
test('drafts stay user scoped and legacy shared drafts are never adopted',()=>{
 const values=new Map([['tempIdentifiedItems','[{"name":"other"}]']])
 const storage={getItem:k=>values.get(k),setItem:(k,v)=>values.set(k,v),removeItem:k=>values.delete(k)}
 assert.deepEqual(loadDraft('a',storage),[])
 const item=recognitionItem({name:'牛肉',quantity:2,freshness_detail:{fresh_score:null,reasons:['缺少时间信息']}})
 saveDraft('a',[item],storage)
 assert.deepEqual(loadDraft('b',storage),[])
 assert.deepEqual(loadDraft('a',storage)[0].freshness_detail.reasons,['缺少时间信息'])
 assert.equal(item.storage_type,'');assert.equal(item.shelf_life,'')
 clearDraft('a',storage);assert.deepEqual(loadDraft('a',storage),[])
})
