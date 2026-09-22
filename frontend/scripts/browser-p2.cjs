// UI contract fixtures only; real backend acceptance runs separately.
const { chromium } = require(process.env.COOKX_PLAYWRIGHT || 'playwright')
const assert = require('node:assert/strict'), fs = require('node:fs')
;(async () => {
 const browser = await chromium.launch({headless:true,...(process.env.COOKX_CHROME ? {executablePath:process.env.COOKX_CHROME} : {})})
 try {
  const page = await browser.newPage({viewport:{width:390,height:844}}), errors=[]
  page.on('pageerror', e=>errors.push(e.message))
  await page.addInitScript(()=>localStorage.setItem('user',JSON.stringify({id:'p2-test',username:'测试'})))
  let failure=0
  const inventory=[{id:'zero',name:'鸡蛋',quantity:2},{id:'unknown',name:'牛肉',quantity:1}]
  await page.route('**/api/**',async route=>{
   const path = new URL(route.request().url()).pathname
   if(path.endsWith('/inventory/freshness')) {
    if(failure) return route.fulfill({status:failure,contentType:'application/json',body:JSON.stringify(failure===200?{status:'error',message:'测试业务失败'}:{detail:'测试失败'})})
    return route.fulfill({json:{evaluated_at:'2026-09-22T01:00:00Z',items:[
     {item_id:'unknown',fresh_score:null,component_scores:{T:null,S:null,V:null,H:null},reasons:['缺少日期'],disclaimer:'辅助判断，不能替代食品安全检测'},
     {item_id:'zero',fresh_score:0,freshness_label:'风险较高',expired:true,confidence_score:0,component_scores:{T:0,S:0,V:null,H:null},effective_weights:{T:0.7,S:0.3},reasons:['已超过到期时间'],data_quality_notes:['未提供视觉依据'],disclaimer:'辅助判断，不能替代食品安全检测'}]}})
   }
   if(path.endsWith('/inventory')) return route.fulfill({json:inventory})
   return route.fulfill({json:{status:'success',data:[],items:[]}})
  })
  await page.goto((process.env.COOKX_BASE_URL || 'http://127.0.0.1:4173')+'/#/home?tab=Manage')
  const card=page.locator('.food-card').filter({has:page.getByRole('heading',{name:'鸡蛋',exact:true})})
  await card.getByText('FreshScore：0',{exact:false}).waitFor()
  assert.match(await card.innerText(),/置信度：0%/)
  await page.getByText('FreshScore：数据不足',{exact:false}).waitFor()
  for(const code of [500,422,200]) {
   failure=code;await page.getByRole('button',{name:'刷新库存与鲜度'}).click()
   await page.getByText('鲜度评估失败，库存仍保留').first().waitFor()
   assert.equal(await page.locator('.food-card').count(),2)
  }
  failure=0;await page.getByRole('button',{name:'刷新库存与鲜度'}).click()
  await card.getByText('FreshScore：0',{exact:false}).waitFor()
  {
   await card.getByText('查看鲜度依据',{exact:true}).click()
   await card.getByText('V 视觉',{exact:true}).waitFor()
   assert.match(await card.innerText(),/未参与/)
  }
  await card.scrollIntoViewIfNeeded()
  fs.mkdirSync('tmp/p2',{recursive:true})
  await page.screenshot({path:'tmp/p2/freshness-mobile.png',fullPage:true})
  assert.deepEqual(errors,[])
  console.log(JSON.stringify({suite:'p2-fixture-ui',checks:['zero','unknown','500','422','business-error','retry','mobile'],uncaughtErrors:errors}))
 } finally { await browser.close() }
})().catch(e=>{console.error(e);process.exit(1)})
