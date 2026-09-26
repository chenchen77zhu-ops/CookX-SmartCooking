// Isolated REAL backend inventory/FreshFusion/recommendations; recognition fixture only.
const {chromium}=require(process.env.COOKX_PLAYWRIGHT || 'playwright')
const assert=require('node:assert/strict'),fs=require('node:fs')
;(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[]
  page.setDefaultTimeout(15000);page.on('pageerror',e=>errors.push(e.message))
  const api='http://127.0.0.1:8000/api',base=process.env.COOKX_BASE_URL||'http://127.0.0.1:4173'
  async function register(suffix) {
   const response=await page.request.post(api+'/register',{data:{nickname:'p2-'+Date.now()+suffix,phone:'',password:'test-pass-123'}})
   const body=await response.json();assert.equal(body.status,'success');return body.user
  }
  const user=await register('a'),other=await register('b')
  await page.request.post(api+'/add-to-inventory?user_id='+user.id,{data:[{name:'鸡蛋',quantity:1,storage_type:'冷藏',shelf_life:7}]})
  await page.addInitScript(user=>{if(!localStorage.getItem('user'))localStorage.setItem('user',JSON.stringify(user))},user)
  let recommendations=0,failRecommendations=false,writes=0
  await page.route('**/api/**',async route=>{
   const u=new URL(route.request().url())
   if(u.pathname.endsWith('/recommendations')) {
    recommendations++
    if(failRecommendations)return route.fulfill({status:500,json:{detail:'测试推荐故障'}})
   }
   if(u.pathname.endsWith('/inventory/confirm-recognition'))writes++
   if(u.pathname.endsWith('/analyze-fridge'))return route.fulfill({json:{status:'success',detected:[{name:'tomato',quantity:2,freshness_detail:{fresh_score:null,component_scores:{T:null,S:null,V:null,H:null},reasons:['识别不能提供购买时间'],data_quality_notes:['视觉分项数据不足'],disclaimer:'测试识别结果，仅验证页面流程'}}]}})
   const response=await route.fetch({url:'http://127.0.0.1:8000'+u.pathname+u.search})
   await route.fulfill({response})
  })
  await page.goto(base+'/#/fridge')
  await page.locator('.food-card-top').first().click()
  await page.locator('.freshness-card').getByText('FreshScore：',{exact:false}).waitFor()
  await page.goto(base+'/#/home?tab=Manage')
  await page.getByRole('button',{name:'获取智能推荐',exact:true}).click()
  await page.locator('.recommendation-card').first().waitFor()
  assert.equal(recommendations,1)
  await page.locator('input[type=file]').setInputFiles({name:'fixture.png',mimeType:'image/png',buffer:Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aVioAAAAASUVORK5CYII=','base64')})
  await page.waitForURL('**/#/capture-confirm')
  await page.getByText('识别不能提供购买时间',{exact:true}).waitFor()
  await page.getByLabel('储存方式',{exact:true}).selectOption('常温')
  await page.getByLabel('保质期（天，可未知）',{exact:true}).fill('3')
  await page.getByLabel('名称',{exact:true}).fill('番茄')
  await page.getByLabel('数量',{exact:true}).focus();await page.keyboard.press('Tab')
  fs.mkdirSync('tmp/p2',{recursive:true})
  await page.waitForTimeout(400) // Let the existing route fade finish before visual evidence.
  await page.locator('.results-panel').screenshot({path:'tmp/p2/recognition-confirm-mobile.png',animations:'disabled'})
  failRecommendations=true
  await page.getByRole('button',{name:/确认加入冰箱/}).click()
  await page.waitForURL('**/#/home?tab=Manage')
  await page.getByRole('heading',{name:'推荐服务暂时不可用',exact:true}).waitFor()
  assert.equal(writes,1);assert.equal(recommendations,2)
  const rows=await (await page.request.get(api+'/inventory?user_id='+user.id)).json()
  assert.equal(rows.find(item=>item.name==='西红柿').quantity,2)
  assert.equal(await page.evaluate(user=>localStorage.getItem('cookx:recognition:v1:'+user.id),user),null)
  failRecommendations=false;await page.locator('.smart-recommendations').getByRole('button',{name:'重试',exact:true}).click()
  await page.locator('.recommendation-card').first().waitFor()
  await page.goto(base+'/#/home')
  await page.getByText('查看评估依据',{exact:true}).waitFor()
  assert.equal(await page.getByText(/可以放心烹饪/).count(),0)
  await page.goto(base+'/#/fridge')
  await page.getByRole('heading',{name:'西红柿',exact:true}).waitFor()
  await page.evaluate(other=>{localStorage.setItem('user',JSON.stringify(other));window.dispatchEvent(new StorageEvent('storage',{key:'user'}))},other)
  await page.getByRole('heading',{name:'冰箱还是空的',exact:true}).waitFor()
  assert.equal(await page.locator('.food-card').count(),0)
  await page.goto(base+'/#/capture-confirm')
  await page.getByRole('heading',{name:'未检测到可确认的食材',exact:true}).waitFor()
  assert.deepEqual(errors,[])
  const report={checks:['recognition-fixture-to-confirm','metadata-preserved','local-draft-user-isolation','real-inventory-save-readback','real-freshfusion','recommendation-invalidation','recommendation-failure-does-not-undo-save','recommendation-retry','home-neutral-summary','mobile-keyboard'],writes,uncaughtErrors:errors,limits:'Cloud recognition substituted; loopback backend, no server deployment or phone validation'}
  fs.writeFileSync('tmp/p2/flow-results.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report))
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
