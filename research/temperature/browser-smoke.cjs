// Run with COOKX_PLAYWRIGHT pointing to a Playwright package, or install playwright.
const { chromium } = require(process.env.COOKX_PLAYWRIGHT || 'playwright')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
;(async()=>{
 const browser=await chromium.launch({headless:true,args:['--autoplay-policy=no-user-gesture-required'],...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try {
 const context=await browser.newContext({viewport:{width:390,height:844},acceptDownloads:true})
 await context.addInitScript(()=>{
   localStorage.setItem('user',JSON.stringify({id:'987654',username:'Local test'}))
   // This suite tests fresh sessions; lifecycle flushes may persist on pagehide.
   localStorage.removeItem('cookx:cooking:v1:987654')
   window.SpeechRecognition=undefined;window.webkitSpeechRecognition=undefined
 })
 const page=await context.newPage(), errors=[]
 page.on('pageerror',e=>errors.push(e.message))
 page.on('console',m=>{if(m.type()==='warning' || m.type()==='error')console.log(m.text())})
 page.on('requestfailed',r=>console.log('FAILED',r.url(),r.failure()?.errorText))
 // Silent local WAV exercises the real HTML audio playback and timer lifecycle.
 let speechMode='audio'
 const wav=Buffer.alloc(44+8000*30*2)
 wav.write('RIFF');wav.writeUInt32LE(wav.length-8,4);wav.write('WAVEfmt ',8)
 wav.writeUInt32LE(16,16);wav.writeUInt16LE(1,20);wav.writeUInt16LE(1,22)
 wav.writeUInt32LE(8000,24);wav.writeUInt32LE(16000,28);wav.writeUInt16LE(2,32);wav.writeUInt16LE(16,34)
 wav.write('data',36);wav.writeUInt32LE(wav.length-44,40)
 await page.route('**/api/**',async route=>{
   if(route.request().url().includes('/tts?')) {
     if(speechMode==='error') return route.fulfill({status:503,json:{detail:'test speech outage'}})
     if(speechMode==='empty') return route.fulfill({json:{}})
     return route.fulfill({json:{audio_url:'/api/test-temperature-audio.wav'}})
   }
   if(route.request().url().includes('/test-temperature-audio.wav')) return route.fulfill({contentType:'audio/wav',body:wav})
   if(route.request().url().includes('recommend-recipe')) return route.fulfill({json:{status:'success',recipe:{
     id:'test-recipe',dish_name:'测试菜谱',steps:[{text:'热锅',temperature:'160–180 ℃',time_estimate:30},{text:'关火观察',temperature:'80–100 ℃',time_estimate:30}]}}})
   return route.fulfill({json:{status:'success',data:[],history:[],items:[]}})
 })
 await page.goto((process.env.COOKX_BASE_URL||'http://127.0.0.1:5173')+'/#/home?tab=Recipes')
 await page.getByPlaceholder('告诉 CookX 你想做什么…').fill('测试菜谱')
 await page.getByPlaceholder('告诉 CookX 你想做什么…').press('Enter')
 await page.getByRole('button',{name:'开始指导'}).click()
 await page.getByRole('button',{name:'烹饪工具',exact:true}).click()
 await page.getByRole('button',{name:'重试在线播报',exact:true}).click()
 await page.waitForFunction(()=>document.querySelector('.c-eta')?.innerText.includes('00:29'))
 await page.getByRole('button',{name:'暂停播报',exact:true}).click()
 await page.getByRole('button',{name:'继续播报',exact:true}).click()
 await page.getByText('算法与记录',{exact:true}).click()
 await page.getByRole('checkbox',{name:'启用实验模型（仿真训练）'}).check()
 await page.waitForFunction(()=>document.body.innerText.includes('实验模型就绪'),{},{timeout:20000})
 await page.getByRole('button',{name:'仿真回放',exact:true}).click()
 await page.waitForFunction(()=>document.body.innerText.includes('物理仿真回放'))
 await page.waitForFunction(()=>/实验模型（仿真训练） · \d+ ms/.test(document.body.innerText),{},{timeout:25000})
 assert.ok(!(await page.locator('.c-eta').innerText()).includes('00:30'),'step timer must continue during inference')
 await page.locator('.cooking-tools-drawer .el-drawer__close-btn').click()
 await page.locator('.c-chart svg path[stroke-dasharray]').waitFor({state:'visible'})
 fs.mkdirSync('tmp/ui-review',{recursive:true})
 await page.screenshot({path:'tmp/ui-review/cooking-simulation-forecast.png',animations:'disabled'})
 await page.getByRole('button',{name:'烹饪工具',exact:true}).click()
 const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>window.innerWidth+2)
 assert.equal(overflow,false,'mobile horizontal overflow')
 const shot=path.resolve(__dirname,'../../tmp/temperature-mobile.png')
 fs.mkdirSync(path.dirname(shot),{recursive:true})
 await page.screenshot({path:shot,fullPage:true})
 const downloadPromise=page.waitForEvent('download')
 await page.getByRole('button',{name:'导出本次',exact:true}).click()
 const download=await downloadPromise
 const exportPath=path.resolve(__dirname,'../../tmp/temperature-export.json')
 await download.saveAs(exportPath)
 const exported=JSON.parse(fs.readFileSync(exportPath,'utf8'))
 assert.equal(exported.source,'simulation');assert.ok(exported.samples.length>=10);assert.ok(exported.assessments.length>=10)
 await page.getByRole('button',{name:'移动了探头',exact:true}).click()
 assert.ok((await page.locator('.insight').innerText()).includes('重新建立连续窗口'))
 await page.getByRole('button',{name:'停止回放',exact:true}).click()
 speechMode='error'
 await page.getByRole('button',{name:'下一步',exact:true}).click()
 assert.ok((await page.locator('.step-description').innerText()).includes('关火观察'))
 await page.getByRole('checkbox',{name:'启用实验模型（仿真训练）'}).uncheck()
 await page.route('**/thermal-transformer.onnx',route=>route.abort())
 await page.getByRole('checkbox',{name:'启用实验模型（仿真训练）'}).check()
 await page.waitForFunction(()=>document.body.innerText.includes('模型不可用，规则判断'),{},{timeout:20000})
 // Reload to eliminate preloaded audio before testing provider outages.
 for(const mode of ['error','empty']) {
   speechMode=mode
   await page.evaluate(()=>localStorage.removeItem('cookx:cooking:v1:987654'))
   await page.goto((process.env.COOKX_BASE_URL||'http://127.0.0.1:5173')+'/#/home?tab=Recipes');await page.reload()
   await page.getByPlaceholder('告诉 CookX 你想做什么…').fill('测试菜谱')
   await page.getByPlaceholder('告诉 CookX 你想做什么…').press('Enter')
   await page.getByRole('button',{name:'开始指导'}).click()
   await page.getByRole('button',{name:'烹饪工具',exact:true}).click()
 await page.getByRole('button',{name:'重试在线播报',exact:true}).click()
   await page.waitForFunction(()=>document.querySelector('.c-eta')?.innerText.includes('00:29'))
 }
 assert.deepEqual(errors,[])
 const report={mobileWidth:390,overflow,uncaughtErrors:errors,exportSamples:exported.samples.length,
   checks:['model-load','wasm-inference','simulation-source','export','probe-correction','step-navigation','model-failure-fallback','audio-pause-resume','timer-during-inference','timer-with-speech-error','timer-with-empty-speech'],
   screenshot:path.relative(path.resolve(__dirname,'../..'),shot).replace(/\\/g,'/')}
 fs.writeFileSync(path.resolve(__dirname,'../../docs/temperature/browser-results.json'),JSON.stringify(report,null,2))
 console.log(JSON.stringify(report))
 } finally {await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
