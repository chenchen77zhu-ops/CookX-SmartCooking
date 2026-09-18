// Use the exact product engine/features for offline evaluation and training inputs.
import fs from 'node:fs'
import { createTemperatureEngine } from '../../frontend/src/temperature/engine.js'
import { buildFeatures } from '../../frontend/src/temperature/features.js'
import { PHASES } from '../../frontend/src/temperature/context.js'
const payload = JSON.parse(fs.readFileSync(0, 'utf8'))
const output = []
for (const run of payload.runs) {
  const engine = createTemperatureEngine({ qualityGate: payload.qualityGate !== false })
  engine.setContext(payload.context === false ? {} : run.context)
  const records = []
  for (let i = 0; i < run.samples.length; i++) {
    for (const event of run.events.filter(e => e.index === i && e.confirmed)) engine.confirm(event.type, run.samples[i].updatedAt)
    const assessment = engine.push(run.samples[i])
    if (i >= 20 && i % 8 === 0 && i + 60 < run.samples.length) {
      const win = engine.getWindow()
      records.push({ index: i, x: Array.from(buildFeatures(win.samples, win.context)), rulePhase: PHASES.indexOf(assessment.phase),
        quality: assessment.quality, risk: assessment.risk, slope: assessment.slope ?? 0 })
    }
  }
  output.push({ id: run.id, records })
}
process.stdout.write(JSON.stringify(output))
