import { PHASES, PHASE_LABELS } from './context.js'
export function acceptPrediction(message, snapshot, epoch, now) {
  if (message.epoch !== epoch || snapshot.quality !== 'usable' || now - message.at > 2500 || now < message.at) return null
  const values = message.forecast
  if (!Array.isArray(values) || values.length !== 9 || !values.every(Number.isFinite)) return null
  const logits = message.logits
  if (!Array.isArray(logits) || logits.length !== 6 || !logits.every(Number.isFinite) || !Number.isFinite(message.qualityLogit)) return null
  const maximum = Math.max(...logits), exp = logits.map(v => Math.exp(v-maximum)), sum = exp.reduce((a,b)=>a+b,0)
  const probability = Math.max(...exp) / sum, index = logits.indexOf(maximum)
  if (probability < .55 || message.qualityLogit < 0) return null
  const forecast = [5,15,30].map((seconds,i) => ({ seconds, low: values[i*3]*300, median: values[i*3+1]*300, high: values[i*3+2]*300 }))
  if (forecast.some(p => p.low > p.median || p.median > p.high || p.low < -70 || p.high > 380)) return null
  return { phase: PHASES[index], phaseLabel: PHASE_LABELS[PHASES[index]], modelProbability: probability, forecast,
    note: '仿真训练模型估计，条件变化后失效；模型概率不是实机准确率。' }
}
