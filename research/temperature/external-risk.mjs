import fs from 'node:fs'
import { createTemperatureEngine } from '../../frontend/src/temperature/engine.js'
const runs=JSON.parse(fs.readFileSync(0,'utf8'))
const summary={domain:'NIST Type-K pan-center thermocouple, not prototype infrared',samplePeriodSeconds:4,
  adapter:'Engine maxSampleGapMs=5000 for original 4 s cadence; no upsampling',experiments:runs.length,models:{}}
for(const mode of ['legacy_fixed_205C','context_rules']){
 let tp=0,fp=0,fn=0,tn=0,falseEpisodes=0,detected=0,events=0,delays=[],abstentions=0
 for(const run of runs){
   const engine=createTemperatureEngine({maxSampleGapMs:5000})
   let prevAlarm=false,positiveAt=null,detectedAt=null
   for(const row of run.rows){
     const s={updatedAt:row.time*1000,temperature:row.temp,valid:Number.isFinite(row.temp),source:'public_thermocouple'}
     const a=engine.push(s)
     const alarm=mode==='legacy_fixed_205C'?row.temp>205:['warning','danger'].includes(a.risk)
     const known=mode==='legacy_fixed_205C'?Number.isFinite(row.temp):a.quality==='usable'
     if(!known)abstentions++
     const actual=row.preIgnition===1
     if(actual && positiveAt===null)positiveAt=row.time
     if(actual && alarm && detectedAt===null)detectedAt=row.time
     if(alarm && !prevAlarm && !actual)falseEpisodes++
     if(actual){if(alarm)tp++;else fn++}else{if(alarm)fp++;else tn++}
     prevAlarm=alarm
   }
   if(positiveAt!==null){events++;if(detectedAt!==null){detected++;delays.push(detectedAt-positiveAt)}}
 }
 summary.models[mode]={positiveSampleRecall:tp/(tp+fn),negativeSampleFalsePositiveRate:fp/(fp+tn),falseAlarmEpisodes:falseEpisodes,
   eventRecall:detected/events,eventCount:events,missedEvents:events-detected,
   meanDetectionDelaySeconds:delays.length?delays.reduce((a,b)=>a+b,0)/delays.length:null,
   abstainedSamples:abstentions,confusion:{tp,fp,fn,tn}}
}
process.stdout.write(JSON.stringify(summary))
