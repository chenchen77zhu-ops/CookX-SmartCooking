// Uses tests/browser_server.py: real API routes/storage, substituted cloud and YOLO.
const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright')
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path')
;(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[]
  page.on('pageerror',e=>errors.push(e.message))
  // Forward to the local acceptance backend; preserve real responses and persistence.
  await page.route('**/api/**',async route=>{
   const u=new URL(route.request().url())
   const response=await route.fetch({url:'http://127.0.0.1:8000'+u.pathname+u.search})
   await route.fulfill({response})
  })
  const base=process.env.COOKX_BASE_URL||'http://127.0.0.1:4173'
  await page.goto(base+'/#/login')
  await page.getByPlaceholder('请输入昵称').fill('acceptance')
  await page.getByPlaceholder('请输入密码').fill('test-pass-123')
  await page.getByRole('button',{name:'登录',exact:true}).click()
  await page.waitForURL('**/#/home')
  await page.goto(base+'/#/home?tab=Manage')
  await page.getByText('保质期信息不足',{exact:true}).waitFor()
  for(const name of ['番茄','鸡蛋']) {
   await page.getByRole('button',{name:'添加食材',exact:true}).first().click()
   await page.getByPlaceholder('如：牛肉').fill(name)
   await page.getByRole('button',{name:'确认添加',exact:true}).click()
   await page.getByRole('dialog',{name:'添加食材'}).waitFor({state:'hidden'})
  }
  await page.getByRole('button',{name:'获取智能推荐',exact:true}).click()
  await page.locator('.recommendation-card').first().waitFor()
  assert.ok(await page.locator('.recommendation-card').count()>0)
  const uid=await page.evaluate(()=>JSON.parse(localStorage.getItem('user')).id)
  const freshness=await page.request.get('http://127.0.0.1:8000/api/users/'+uid+'/inventory/freshness')
  const body=await freshness.json()
  assert.equal(body.total_count,3);assert.equal(body.unknown_count,1)
  assert.equal(body.evaluable_count,2)
  const inventory=await (await page.request.get('http://127.0.0.1:8000/api/inventory?user_id='+uid)).json()
  assert.equal(inventory.length,3)
  await page.goto(base+'/#/home?tab=AiChef')
  await page.getByPlaceholder('告诉 CookX 你想做什么…').fill('番茄炒蛋')
  await page.getByPlaceholder('告诉 CookX 你想做什么…').press('Enter')
  await page.getByRole('button',{name:'开始指导'}).click()
  await page.waitForFunction(()=>document.querySelector('.current-step-card .panel-heading')?.innerText.includes('00:29'))
  assert.deepEqual(errors,[])
  const report={environment:'Desktop Chromium with isolated loopback API',externalProviders:'YOLO and cloud substituted; no real recognition, speech or recipe quality claim',
   checks:['login','inventory-read','legacy-unknown-ui','inventory-add-persistence','recommendation-ui-with-real-algorithm','freshfusion-api-with-real-algorithm','recipe-to-cooking','timer-during-provider-outage'],uncaughtErrors:errors}
  fs.writeFileSync(path.resolve(__dirname,'../../docs/temperature/application-results.json'),JSON.stringify(report,null,2))
  console.log(JSON.stringify(report))
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
