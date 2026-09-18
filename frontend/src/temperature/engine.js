import { PHASE_LABELS } from './context.js'
const median = values => [...values].sort((a, b) => a - b)[Math.floor(values.length / 2)]
const blank = reason => ({ schemaVersion: 1, quality: 'invalid', phase: 'unknown', risk: 'unknown',
  temperature: null, slope: null, forecast: [], reasons: [reason], suggestion: null,
  phaseLabel: '不确定', modelVersion: 'rules-v1', alert: null })

// Pure causal engine, independent of UI, transport and model lifecycle.
export function createTemperatureEngine({ qualityGate = true, maxSampleGapMs = 1500 } = {}) {
  let samples = [], context = {}, previous = null, segment = 0, acceptedPhase = 'unknown'
  let candidate = '', candidateSince = 0, stableSince = null, highSince = null, lastAlert = -Infinity
  let lastEvent = null, result = blank('等待连续温度数据'), epoch = 0
  const clearSegment = () => {
    samples = []; segment++; acceptedPhase = 'unknown'; candidate = ''; stableSince = null; highSince = null
  }
  const reset = (reason = '等待连续温度数据') => {
    clearSegment(); previous = null; lastEvent = null; epoch++; result = blank(reason)
    return result
  }
  const confirm = (type, at) => {
    if (!['ingredient_added', 'probe_moved', 'heat_off'].includes(type) || !Number.isFinite(at)) return
    clearSegment(); epoch++; lastEvent = { type, at }; result = blank('操作已记录，重新建立连续窗口')
  }
  const setContext = next => {
    if (JSON.stringify(context) !== JSON.stringify(next)) {
      context = { ...next }; clearSegment(); lastEvent = null; epoch++; result = blank('步骤已更新，重新判断')
    }
  }
  const push = sample => {
    const t = sample.updatedAt
    if (!Number.isFinite(t)) return result
    if (previous && t <= previous.updatedAt) return result
    if (!sample.valid || !Number.isFinite(sample.temperature) || sample.temperature < -70 || sample.temperature > 380) {
      reset('设备上报无效读数'); previous = sample; return result
    }
    const prev = previous
    const dt = prev ? (t - prev.updatedAt) / 1000 : 0.5
    const discontinuity = sample.discontinuity || (prev && (dt * 1000 > maxSampleGapMs || sample.source !== prev.source))
    const jump = prev?.valid && dt > 0 && Math.abs(sample.temperature - prev.temperature) > Math.max(12, 12 * dt)
    if (discontinuity || (qualityGate && jump)) { clearSegment(); epoch++ }
    previous = sample
    samples.push({ ...sample, segment })
    samples = samples.filter(s => t - s.updatedAt <= 60000).slice(-120)
    const recent = samples.slice(-20)
    const filtered = median(samples.slice(-5).map(s => s.temperature))
    const span = (recent.at(-1).updatedAt - recent[0].updatedAt) / 1000
    const meanX = recent.reduce((sum, x) => sum + (x.updatedAt - recent[0].updatedAt) / 1000, 0) / recent.length
    const meanY = recent.reduce((sum, x) => sum + x.temperature, 0) / recent.length
    let numerator = 0, denominator = 0
    for (const x of recent) { const dx = (x.updatedAt - recent[0].updatedAt) / 1000 - meanX; numerator += dx * (x.temperature - meanY); denominator += dx * dx }
    const slope = denominator ? numerator / denominator : 0
    const residual = Math.sqrt(recent.reduce((sum, s) => sum + (s.temperature - meanY - slope * ((s.updatedAt - recent[0].updatedAt) / 1000 - meanX)) ** 2, 0) / recent.length)
    const stable = !discontinuity && !jump && residual < 4
    if (stable) stableSince ??= t
    else stableSince = null
    const quality = qualityGate && (span < 4.5 || stableSince === null || t - stableSince < 5000) ? 'suspect' : 'usable'
    let phase = 'unknown', reasons = []
    const eventFresh = lastEvent && t >= lastEvent.at && t - lastEvent.at < 60000
    if (quality === 'usable') {
      if (slope > 0.3) phase = eventFresh && lastEvent.type === 'ingredient_added' ? 'recovery' : 'preheat'
      else if (slope < -0.3) phase = eventFresh && lastEvent.type === 'heat_off' ? 'cooling' : eventFresh && lastEvent.type === 'ingredient_added' ? 'drop' : 'unknown'
      else phase = 'steady'
      if (phase !== candidate) { candidate = phase; candidateSince = t }
      if (phase === 'unknown' || t - candidateSince >= 2000) acceptedPhase = phase
      phase = acceptedPhase
      reasons.push('近段温变 ' + slope.toFixed(2) + ' ℃/秒')
      if (phase === 'unknown') reasons.push('阶段证据尚不明确，可确认投料、关火或探头移动')
    } else {
      acceptedPhase = 'unknown'; candidate = ''; highSince = null
      reasons.push(jump ? '温度突变：可能是投料、遮挡或测量目标变化' : discontinuity ? '数据不连续，已重建窗口' : '正在积累稳定测量；移动时可能无法辨认阶段')
    }
    let risk = 'unknown', suggestion = null
    const range = context.targetRange
    if (quality === 'usable') {
      const elevated = filtered > 230 || (range && filtered > range[1] + 10)
      highSince = elevated ? (highSince ?? t) : null
      risk = elevated ? (t - highSince >= 10000 ? 'danger' : 'warning') : 'observing'
      if (risk === 'danger') suggestion = '持续高温，请检查锅内情况与测温位置，必要时降低火力。'
      else if (risk === 'warning') suggestion = '温度偏高，请核对当前步骤与测温位置。'
      else if (phase !== 'unknown' && range) suggestion = filtered < range[0] ? '低于当前步骤目标温区，请结合操作状态确认。' : filtered > range[1] ? '高于当前步骤目标温区，请结合操作状态确认。' : '处于当前步骤目标温区，仍需观察锅内情况。'
      reasons.push(range ? '步骤目标 ' + Number(range[0]).toFixed(0) + '–' + Number(range[1]).toFixed(0) + ' ℃' : '缺少明确步骤温区，仅判断温度趋势')
    }
    let alert = null
    if (['warning', 'danger'].includes(risk) && (risk !== result.risk || t - lastAlert >= 30000)) { alert = { at: t, risk }; lastAlert = t }
    result = { schemaVersion: 1, quality, phase, phaseLabel: PHASE_LABELS[phase], risk, temperature: filtered,
      slope, forecast: [], reasons, suggestion, modelVersion: 'rules-v1', alert, updatedAt: t, epoch }
    return result
  }
  return { push, confirm, setContext, reset,
    expire(now) { if (previous && now - previous.updatedAt >= 5000 && result.quality !== 'invalid') reset('温度已超时，不能继续判断'); return result },
    getSnapshot: () => result,
    getWindow: () => ({ samples: samples.map(s => ({ ...s })), context: { ...context }, epoch }) }
}
