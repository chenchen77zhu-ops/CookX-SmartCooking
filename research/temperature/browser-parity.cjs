const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright')
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict')
;(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try{
 const page=await browser.newPage()
 const external=[]
 await page.route('**/*',route=>{
   const url=new URL(route.request().url())
   if(!['127.0.0.1','localhost'].includes(url.hostname)){external.push(url.origin);return route.abort()}
   return route.continue()
 })
 await page.goto((process.env.COOKX_BASE_URL||'http://127.0.0.1:4173')+'/#/login',{waitUntil:'networkidle'})
 const assets=path.resolve(__dirname,'../../frontend/dist/assets')
 const workerFile=fs.readdirSync(assets).find(name=>/^inference\.worker-.*\.js$/.test(name))
 assert.ok(workerFile,'Build the frontend before running production WASM parity')
 const fixture=JSON.parse(fs.readFileSync(process.env.COOKX_PARITY_FIXTURE||path.join(__dirname,'parity-fixture.json'),'utf8'))
 const result=await page.evaluate(async ({fixture,workerPath})=>{
   const worker=new Worker(workerPath,{type:'module'}),latencies=[]
   let index=0
   return new Promise((resolve,reject)=>{
     const timeout=setTimeout(()=>{worker.terminate();reject(new Error('Timeout'))},20000)
     const send=()=>worker.postMessage({type:'infer',id:index,epoch:0,at:0,features:new Float32Array(fixture.input.flat(2))})
     worker.onmessage=({data})=>{
       if(data.type==='error'){clearTimeout(timeout);worker.terminate();reject(new Error(data.message))}
       if(data.type==='ready')send()
       if(data.type==='prediction'){
         if(index>=5)latencies.push(data.latencyMs)
         index++
         if(index<105)send()
         else {clearTimeout(timeout);worker.terminate();resolve({data,latencies})}
       }
     }
     worker.postMessage({type:'init',baseUrl:location.origin+'/temperature/'})
   })
 },{fixture,workerPath:'/assets/'+workerFile})
 const values=[...result.data.logits,...result.data.forecast,result.data.qualityLogit]
 const expected=fixture.outputs.flat(3)
 const error=Math.max(...values.map((v,i)=>Math.abs(v-expected[i])))
 assert.ok(error<.0001,'WASM/CPU inference mismatch')
 assert.deepEqual(external,[])
 const ordered=result.latencies.sort((a,b)=>a-b)
 const report={environment:'Desktop Chromium headless, not Android phone',browserVersion:browser.version(),
   samples:100,wasmP95Ms:ordered[94],maxAbsError:error,externalNetworkRequests:external.length}
 fs.writeFileSync(process.env.COOKX_PARITY_OUTPUT||path.resolve(__dirname,'../../docs/temperature/wasm-results.json'),JSON.stringify(report,null,2))
 console.log(JSON.stringify(report))
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
