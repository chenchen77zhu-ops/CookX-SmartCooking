// Authoritative feature contract, imported by both product and research prepare.mjs: 120 x 8.
export const FEATURE_VERSION = 1
export const WINDOW = 120
export const CHANNELS = 8
export function buildFeatures(samples, context = {}) {
  const output = new Float32Array(WINDOW * CHANNELS)
  if (!samples.length) return output
  const end = samples.at(-1).updatedAt
  let cursor = -1, previous = null
  for (let i = 0; i < WINDOW; i++) {
    const at = end - (WINDOW - 1 - i) * 500
    while (cursor + 1 < samples.length && samples[cursor + 1].updatedAt <= at) cursor++
    const s = samples[cursor]
    const valid = !!s?.valid && Number.isFinite(s.temperature) && at - s.updatedAt < 750
    if (!valid) { previous = null; continue }
    const dt = previous ? (s.updatedAt - previous.updatedAt) / 1000 : 0.5
    const range = context.targetRange
    output.set([s.temperature / 300, previous && dt > 0 ? Math.max(-5, Math.min(5, (s.temperature - previous.temperature) / dt)) / 5 : 0,
      Math.min(5, Math.max(0, dt)) / 5, 1, s.ambientTemperature == null ? 0 : s.ambientTemperature / 100,
      range ? range[0] / 300 : 0, range ? range[1] / 300 : 0, range ? 1 : 0], i * CHANNELS)
    previous = s
  }
  return output
}
