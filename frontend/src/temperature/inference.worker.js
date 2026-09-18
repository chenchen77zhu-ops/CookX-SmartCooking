import * as ort from 'onnxruntime-web/wasm'
let session = null, manifest = null
self.onmessage = async ({ data }) => {
  try {
    if (data.type === 'init') {
      const base = data.baseUrl
      const response = await fetch(new URL('manifest.json',base))
      if (!response.ok) throw new Error('模型清单不可用')
      manifest = await response.json()
      if (manifest.featureVersion !== 1) throw new Error('模型特征版本不匹配')
      ort.env.wasm.numThreads = 1
      ort.env.wasm.proxy = false
      ort.env.wasm.wasmPaths = new URL('runtime/',base).href
      const responseModel = await fetch(new URL(manifest.file,base))
      if (!responseModel.ok) throw new Error('模型文件不可用')
      const buffer = await responseModel.arrayBuffer()
      const digest = await crypto.subtle.digest('SHA-256',buffer)
      const sha = Array.from(new Uint8Array(digest)).map(x=>x.toString(16).padStart(2,'0')).join('')
      if (sha !== manifest.sha256) throw new Error('模型校验失败')
      session = await ort.InferenceSession.create(buffer,{executionProviders:['wasm'],graphOptimizationLevel:'all'})
      self.postMessage({type:'ready',manifest})
    } else if (data.type === 'infer' && session) {
      const start = performance.now()
      const output = await session.run({history:new ort.Tensor('float32',data.features,[1,120,8])})
      self.postMessage({type:'prediction',id:data.id,epoch:data.epoch,at:data.at,
        logits:Array.from(output.phase_logits.data),forecast:Array.from(output.forecast.data),
        qualityLogit:output.quality_logit.data[0],latencyMs:performance.now()-start})
    }
  } catch (error) { self.postMessage({type:'error',message:String(error.message || error)}) }
}
