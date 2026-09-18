import { ref, shallowRef, onUnmounted } from 'vue'
import { createTemperatureEngine } from './engine.js'
import { buildFeatures } from './features.js'
import { acceptPrediction } from './prediction.js'

export function useTemperatureIntelligence(storageKey = 'cookx-temperature-session') {
  const engine = createTemperatureEngine()
  const assessment = shallowRef(engine.getSnapshot()), history = shallowRef([]), prediction = shallowRef(null)
  const modelState = ref('规则判断'), experimental = ref(false), replaying = ref(false), storageMessage = ref('')
  let worker = null, busy = false, ready = false, requestId = 0, generation = 0, lastInference = -Infinity
  let replayTimer = null, replayClock = 0, lastSave = 0, modelTimer = null, context = {}, anchor = null
  let session = {schemaVersion:1,source:'device',startedAt:Date.now(),samples:[],assessments:[],predictions:[],events:[],contexts:[]}
  const persist = () => {
    if (!session.samples.length && !session.events.length) return
    try { localStorage.setItem(storageKey,JSON.stringify(session));storageMessage.value='' }
    catch { storageMessage.value='本地存储不可用，请导出本次记录' }
  }
  const disableModel = message => {
    worker?.terminate();worker=null;busy=false;ready=false
    clearTimeout(modelTimer);prediction.value=null;modelState.value=message
  }
  const startModel = () => {
    if (worker) return
    try {
      modelState.value='模型加载中'
      worker=new Worker(new URL('./inference.worker.js',import.meta.url),{type:'module'})
      modelTimer=setTimeout(()=>disableModel('模型加载超时，规则判断'),15000)
      worker.onerror=()=>disableModel('模型不可用，规则判断')
      worker.onmessage=({data})=>{
        if(data.type==='ready') {clearTimeout(modelTimer);ready=true;session.modelVersion=data.manifest.modelVersion;modelState.value='实验模型就绪（仿真训练）'}
        if(data.type==='error') { console.warn('[Temperature model]',data.message); disableModel('模型不可用，规则判断') }
        if(data.type==='prediction') {
          clearTimeout(modelTimer);busy=false
          if(data.id!==requestId || !experimental.value) return
          const time=replaying.value?replayClock:Date.now()
          prediction.value=acceptPrediction(data,engine.getSnapshot(),engine.getWindow().epoch,time)
          if(prediction.value) { session.predictions.push({at:data.at,epoch:data.epoch,latencyMs:data.latencyMs,...prediction.value});session.predictions=session.predictions.slice(-2400) }
          modelState.value='实验模型（仿真训练） · '+data.latencyMs.toFixed(0)+' ms'
        }
      }
      worker.postMessage({type:'init',baseUrl:new URL(import.meta.env.BASE_URL+'temperature/',location.href).href})
    } catch {disableModel('模型不可用，规则判断')}
  }
  const setExperimental = enabled => {
    experimental.value=enabled;prediction.value=null
    if(enabled) startModel()
    else { disableModel('规则判断');requestId++ }
  }
  const invalidate = (reason='等待数据') => {
    generation++;requestId++;prediction.value=null;anchor=null
    assessment.value=engine.reset(reason)
  }
  const setContext = next => {
    if(JSON.stringify(context)===JSON.stringify(next)) return
    context=next;engine.setContext(next);prediction.value=null;requestId++
    session.contexts.push({at:Date.now(),context:next})
    assessment.value=engine.getSnapshot()
  }
  const confirm = (type, at=replaying.value?replayClock:Date.now()) => {
    engine.confirm(type,at);prediction.value=null;requestId++;session.events.push({type,at})
    assessment.value=engine.getSnapshot()
  }
  const receive = (input, source='device') => {
    if (source==='device' && replaying.value) return
    if (session.source !== source) {
      persist();session={schemaVersion:1,source,startedAt:Date.now(),samples:[],assessments:[],predictions:[],events:[],contexts:[{at:Date.now(),context}]}
      history.value=[];invalidate('数据来源已切换');engine.setContext(context)
    }
    let sample={...input,source,valid:input.valid!==false && Number.isFinite(input.temperature)}
    if(source==='device' && Number.isFinite(input.deviceTimeMs)) {
      if(!anchor || input.bootId!==anchor.bootId || input.discontinuity) anchor={bootId:input.bootId,device:input.deviceTimeMs,wall:input.receivedAt}
      sample.updatedAt=anchor.wall+input.deviceTimeMs-anchor.device
    }
    if (!Number.isFinite(sample.updatedAt)) return
    const oldEpoch=engine.getWindow().epoch
    assessment.value=engine.push(sample)
    if(engine.getWindow().epoch!==oldEpoch || assessment.value.quality!=='usable') prediction.value=null
    history.value=[...history.value,sample].slice(-120)
    session.samples.push(sample)
    session.assessments.push({at:sample.updatedAt,...assessment.value})
    if(session.assessments.length>2400)session.assessments.shift()
    session.events=session.events.slice(-200)
    session.contexts=session.contexts.slice(-200)
    if(session.samples.length>2400) session.samples.shift()
    session.source=source
    if(Date.now()-lastSave>10000){persist();lastSave=Date.now()}
    if(experimental.value && ready && !busy && assessment.value.quality==='usable' && sample.updatedAt-lastInference>=1000) {
      const window=engine.getWindow()
      lastInference=sample.updatedAt;busy=true;requestId++
      worker.postMessage({type:'infer',id:requestId,epoch:window.epoch,at:sample.updatedAt,features:buildFeatures(window.samples,window.context)})
      modelTimer=setTimeout(()=>disableModel('推理超时，规则判断'),5000)
    }
  }
  const stopReplay = () => {
    clearInterval(replayTimer);replayTimer=null;replaying.value=false;history.value=[]
    invalidate('回放已结束，等待真实设备数据');engine.setContext(context);lastInference=-Infinity
  }
  const startReplay = async () => {
    stopReplay()
    const version=generation
    try {
      const response=await fetch(new URL(import.meta.env.BASE_URL+'temperature/replay.json',location.href))
      if(!response.ok) throw new Error('回放文件不可用')
      const data=await response.json()
      if(version!==generation) return
      if(data.source!=='physics_simulation' || !Array.isArray(data.samples)) throw new Error('回放来源不正确')
      persist()
      session={schemaVersion:1,source:'simulation',startedAt:Date.now(),samples:[],assessments:[],predictions:[],events:[],contexts:[{at:0,context:data.context}]}
      replaying.value=true;engine.setContext(data.context);let index=0
      // One simulated second per wall second, no claimed hardware connection.
      replayTimer=setInterval(()=>{
        if(index>=data.samples.length){stopReplay();return}
        replayClock=data.samples[index].updatedAt
        for(const event of data.events.filter(e=>e.index===index)) confirm(event.type,replayClock)
        receive(data.samples[index++],'simulation')
      },500)
    } catch {modelState.value='回放加载失败';stopReplay()}
  }
  const exportSession = (saved=false) => {
    let data=session
    if(saved) {try {data=JSON.parse(localStorage.getItem(storageKey)||'null')} catch {data=null}}
    if(!data){storageMessage.value='没有已保存的记录';return}
    const blob=new Blob([JSON.stringify(data,null,2)],{type:'application/json'})
    const url=URL.createObjectURL(blob),link=document.createElement('a')
    link.href=url;link.download='cookx-temperature-'+data.source+'-'+Date.now()+'.json';link.click();setTimeout(()=>URL.revokeObjectURL(url),1000)
  }
  const tick=setInterval(()=>{
    if(!replaying.value) {
      assessment.value=engine.expire(Date.now())
      if(assessment.value.quality==='invalid') prediction.value=null
    }
  },1000)
  onUnmounted(()=>{persist();clearInterval(tick);clearInterval(replayTimer);disableModel('规则判断');generation++})
  return {assessment,history,prediction,modelState,experimental,replaying,storageMessage,
    receive,invalidate,setContext,confirm,setExperimental,startReplay,stopReplay,exportSession}
}
