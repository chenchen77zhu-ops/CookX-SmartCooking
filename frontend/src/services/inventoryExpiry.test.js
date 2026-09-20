import test from 'node:test'
import assert from 'node:assert/strict'
import { calculateDaysUntilExpiry as days } from './inventoryExpiry.js'
const now = Date.parse('2026-09-20T12:00:00Z')
test('legacy missing metadata stays unknown, never fresh or expired', () => {
  for (const item of [{}, {add_time:'2026-09-20'}, {shelf_life:7}, {add_time:'bad',shelf_life:7}, {days_left:null}, {add_time:'2026-09-20',shelf_life:''}]) {
    const value=days(item,now)
    assert.ok(Number.isNaN(value));assert.equal(value<=5,false);assert.equal(value<=0,false)
  }
})
test('valid dates and explicit zero shelf life remain authoritative', () => {
  assert.equal(days({add_time:'2026-09-19T12:00:00Z',shelf_life:4},now),3)
  assert.equal(days({add_time:'2026-09-19T12:00:00Z',shelf_life:0},now),0)
  assert.equal(days({expiration_date:'2026-09-22T12:00:00Z'},now),2)
  assert.equal(days({days_left:0},now),0)
  assert.ok(Number.isNaN(days({add_time:'2026-09-19',shelf_life:-1},now)))
})
