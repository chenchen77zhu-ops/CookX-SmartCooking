import test from 'node:test'
import assert from 'node:assert/strict'
import {recordReminder,timerReminder,notificationId} from './cookingReminders.js'
const session=()=>({id:'s',status:'active',stepIndex:0,timers:[{deadline:100,round:1}],reminders:[]})
test('timer expires once per round without advancing; pause cancels expiration',()=>{const s=session();assert.equal(timerReminder(s,99),null);assert.ok(timerReminder(s,100));assert.equal(timerReminder(s,999999),null);assert.equal(s.stepIndex,0);s.timers[0].round++;assert.ok(timerReminder(s,999999));s.timers[0].deadline=null;assert.equal(timerReminder(s,999999),null)})
test('five minute cooldown, dismissal and risk upgrade',()=>{const s=session(),e={type:'temperature',object:'sensor',text:'test',risk:'warning'};const first=recordReminder(s,e,0);first.dismissed=true;assert.equal(recordReminder(s,e,1000),null);assert.ok(recordReminder(s,{...e,risk:'danger'},1001));assert.equal(recordReminder(s,e,299999),null);assert.ok(recordReminder(s,e,301001));assert.equal(notificationId('abc'),notificationId('abc'));assert.notEqual(notificationId('abc'),notificationId('abcd'))})
