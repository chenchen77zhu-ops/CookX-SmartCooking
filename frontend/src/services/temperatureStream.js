export const MIN_TEMPERATURE = -50
export const MAX_TEMPERATURE = 500
export const TEMPERATURE_STALE_MS = 5000
const decimal = /^[-+]?(?:\d+(?:\.\d*)?|\.\d+)$/
const uint = /^\d+$/
const bounded = (s, lo, hi) => decimal.test(s) && Number(s) >= lo && Number(s) <= hi
export function parseTemperatureFrame(line, receivedAt = Date.now()) {
  const text = line.trim()
  const base = { schemaVersion: 1, updatedAt: receivedAt, receivedAt, source: 'device',
    bootId: null, sequence: null, deviceTimeMs: null, ambientTemperature: null, discontinuity: false }
  if (text.startsWith('CX2,')) {
    const parts = text.split(',')
    if (parts.length !== 7) return null
    const [, bootId, seq, ms, raw, ambient, valid] = parts
    if (!/^[0-9a-f]{8}$/i.test(bootId) || !uint.test(seq) || !uint.test(ms)
      || Number(seq) > 0xffffffff || Number(ms) > 0xffffffff || !['0', '1'].includes(valid)) return null
    if (ambient !== '' && !bounded(ambient, -40, 125)) return null
    if (valid === '1' && !bounded(raw, -70, 380)) return null
    if (valid === '0' && raw !== '') return null
    return { ...base, protocolVersion: 2, bootId, sequence: Number(seq), deviceTimeMs: Number(ms),
      temperature: valid === '1' ? Number(raw) : null, valid: valid === '1',
      ambientTemperature: ambient === '' ? null : Number(ambient) }
  }
  const raw = text.startsWith('TEMP:') ? text.slice(5) : text
  if (!bounded(raw, MIN_TEMPERATURE, MAX_TEMPERATURE)) return null
  return { ...base, protocolVersion: 1, temperature: Number(raw), valid: true }
}
export const createTemperatureStreamParser = (onTemperature, { now = Date.now, onReject = () => {} } = {}) => {
  if (typeof onTemperature !== 'function') throw new TypeError('温度数据回调必须是函数')
  let buffer = '', dropping = false, previous = null
  const emit = line => {
    const sample = parseTemperatureFrame(line, now())
    if (!sample) { if (line.trim()) onReject('malformed'); return }
    if (sample.protocolVersion === 2 && previous?.protocolVersion === 2) {
      if (sample.bootId !== previous.bootId) sample.discontinuity = true
      else {
        if (sample.sequence <= previous.sequence || sample.deviceTimeMs <= previous.deviceTimeMs) {
          if (previous.deviceTimeMs > 0xffff0000 && sample.deviceTimeMs < 60000) sample.discontinuity = true
          else { onReject('out-of-order'); return }
        }
        if (sample.sequence !== previous.sequence + 1 || sample.deviceTimeMs - previous.deviceTimeMs > 1500) sample.discontinuity = true
      }
    } else if (previous && previous.protocolVersion !== sample.protocolVersion) sample.discontinuity = true
    previous = sample
    onTemperature(sample)
  }
  return {
    append(chunk) {
      if (typeof chunk !== 'string') return
      for (const char of chunk) {
        if (char === '\n') {
          if (!dropping) emit(buffer)
          buffer = ''; dropping = false
        } else if (!dropping) {
          buffer += char
          if (buffer.length > 256) { buffer = ''; dropping = true; onReject('oversize') }
        }
      }
    },
    reset() { buffer = ''; dropping = false; previous = null }
  }
}
