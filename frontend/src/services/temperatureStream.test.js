import test from 'node:test'
import assert from 'node:assert/strict'
import { createTemperatureStreamParser } from './temperatureStream.js'

const parseChunks = (chunks) => {
  const values = []
  const parser = createTemperatureStreamParser(({ temperature }) => values.push(temperature))
  chunks.forEach((chunk) => parser.append(chunk))
  return values
}

test('解析单条 LF 温度', () => {
  assert.deepEqual(parseChunks(['27.48\n']), [27.48])
})

test('跨 chunk 拼接完整温度后才更新', () => {
  assert.deepEqual(parseChunks(['29.', '84\n']), [29.84])
})

test('一次 chunk 解析多条粘包温度', () => {
  assert.deepEqual(parseChunks(['29.84\n30.25\n30.18\n']), [29.84, 30.25, 30.18])
})

test('兼容 CRLF', () => {
  assert.deepEqual(parseChunks(['30.25\r\n']), [30.25])
})

test('拒绝非数字、带尾随字符和超范围数据', () => {
  assert.deepEqual(parseChunks(['abc\n30.25abc\n501\n-51\n']), [])
})

test('reset 后不会混入旧连接的残留数据', () => {
  const values = []
  const parser = createTemperatureStreamParser(({ temperature }) => values.push(temperature))
  parser.append('29.')
  parser.reset()
  parser.append('84\n30.25\n')
  assert.deepEqual(values, [84, 30.25])
})
