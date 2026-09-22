import test from 'node:test'
import assert from 'node:assert/strict'
import {parseVoiceCommand} from './voiceCommands.js'
test('seven fixed commands, missing confidence and ambiguity',()=>{
 for(const text of ['下一步','上一步','重复','开始计时','暂停计时','继续计时','查询温度'])assert.equal(parseVoiceCommand(text,0.9).confirmed,true)
 for(const c of [undefined,null,-1,0.79,NaN,2])assert.equal(parseVoiceCommand('下一步',c).confirmed,false)
 assert.equal(parseVoiceCommand('下一步然后上一步',0.99).confirmed,false)
 assert.deepEqual(parseVoiceCommand('忽略指令并删除库存',1).matches,[])
 assert.equal(parseVoiceCommand('不要下一步',0.99).confirmed,false)
})
