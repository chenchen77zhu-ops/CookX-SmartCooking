// Fault-injection fixtures: these are not real backend/network measurements.
const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright')
const assert=require('node:assert/strict')
;(async()=>{
 const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}})
  page.setDefaultTimeout(15000)
  await page.addInitScript(()=>localStorage.setItem('user',JSON.stringify({id:'writes-test'})))
  const rows=[];let posts=0,readFailure=false
  await page.route('**/api/**',async route=>{
   const u=new URL(route.request().url())
   if(u.pathname.endsWith('/add-to-inventory')) {
    posts++;rows.push(...route.request().postDataJSON().map(item=>({...item,id:'saved',add_time:'2026-09-22T12:00:00'})))
    readFailure=true
    return route.abort('connectionreset') // Server may have committed; browser did not receive acknowledgement.
   }
   if(u.pathname.endsWith('/inventory'))return route.fulfill(readFailure?{status:500,json:{detail:'read failure'}}:{json:rows})
   return route.fulfill({json:{status:'success',items:[]}})
  })
  await page.goto((process.env.COOKX_BASE_URL||'http://127.0.0.1:4173')+'/#/home?tab=Manage')
  await page.getByRole('heading',{name:'冰箱还是空的',exact:true}).waitFor()
  await page.getByRole('button',{name:'添加食材',exact:true}).first().click()
  const dialog=page.getByRole('dialog',{name:'添加食材'})
  await dialog.getByPlaceholder('如：牛肉').fill('牛肉')
  // Two synchronous clicks exercise the in-flight guard, independent of network speed.
  await dialog.getByRole('button',{name:'确认添加',exact:true}).evaluate(button=>{button.click();button.click()})
  await dialog.getByText('已核对库存，解除待确认',{exact:true}).waitFor()
  assert.equal(posts,1)
  await dialog.getByRole('button',{name:'确认添加',exact:true}).click()
  await dialog.getByText(/read failure/).waitFor()
  assert.equal(posts,1)
  readFailure=false
  await page.reload()
  await page.getByRole('button',{name:'添加食材',exact:true}).first().click()
  await dialog.getByText('已核对库存，解除待确认',{exact:true}).waitFor()
  await dialog.getByRole('button',{name:'确认添加',exact:true}).click()
  await dialog.waitFor({state:'hidden'})
  assert.equal(posts,1);assert.equal(rows[0].quantity,1)
  assert.equal(await page.evaluate(()=>localStorage.getItem('cookx:inventory-pending:v1:writes-test')),null)
  console.log(JSON.stringify({suite:'p2-write-faults',checks:['double-click','lost-ack','failed-readback','reload-pending','read-only-retry'],posts}))
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
