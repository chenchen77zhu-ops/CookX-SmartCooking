import test from 'node:test'
import assert from 'node:assert/strict'
import { chartGeometry, cleanChartPoints, smoothChartPath } from './chartGeometry.js'

test('chart rejects invalid values, sorts timestamps, and keeps one observation per instant', () => {
  assert.deepEqual(cleanChartPoints([{at:2,t:10},{at:1,t:0},{at:2,t:20},{at:3,t:null},{at:NaN,t:40}]), [{at:1,t:0},{at:2,t:20}])
})
test('zero or one observation shows no measured curve or misleading endpoint', () => {
  for (const input of [[], [{at:120000,t:25}]]) {
    const g=chartGeometry(input)
    assert.equal(g.endpoint,null)
    assert.equal(smoothChartPath(g.coordinates(g.measured)), '')
    assert.ok(g.end-g.start>=60000)
  }
})
test('chart includes cold and overheated measurements with room for the label', () => {
  const g=chartGeometry([{at:0,t:25},{at:120000,t:270}])
  assert.equal(g.low,0); assert.equal(g.high,300)
  assert.ok(g.endpoint.x>80 && g.endpoint.x<100)
  assert.ok(g.endpoint.y>=18 && g.endpoint.y<94)
  assert.ok(smoothChartPath(g.coordinates(g.measured)).includes('C'))
})
test('forecast is absent without measurements, and only future supplied points are drawn', () => {
  assert.deepEqual(chartGeometry([], [{at:1,t:100}]).forecast, [])
  const g=chartGeometry([{at:10000,t:90},{at:20000,t:100}], [{at:15000,t:110},{at:35000,t:120}])
  assert.deepEqual(g.forecast,[{at:35000,t:120}])
  assert.equal(g.measured.length,2)
})
