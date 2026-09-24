const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright'),assert=require('node:assert/strict'),fs=require('node:fs')
;(async()=>{const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})});try{
 const base=process.env.COOKX_BASE_URL||'http://127.0.0.1:4176',page=await browser.newPage({viewport:{width:390,height:844}})
 await page.route('**/api/**',async r=>{const u=new URL(r.request().url());await r.fulfill({response:await r.fetch({url:'http://127.0.0.1:8001'+u.pathname+u.search})})})
 await page.goto(base+'/#/login');await page.getByPlaceholder('请输入昵称').fill('sqlite-alice');await page.getByPlaceholder('请输入密码').fill('test-pass-123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/#/home')
 const session=await page.evaluate(()=>JSON.parse(sessionStorage.getItem('cookx:session:v1'))),headers={Authorization:'Bearer '+session.access_token},api='http://127.0.0.1:8001/api/v3'
 const created=await(await page.request.post(api+'/households',{headers,data:{name:'采购家庭',idempotency_key:require('node:crypto').randomUUID()}})).json(),family=created.household.id
 await page.goto(base+'/#/shopping');await page.getByLabel('选择家庭',{exact:true}).selectOption(family)
 await page.getByLabel('食材名称',{exact:true}).fill('鸡蛋');await page.getByLabel('需求数量（可未知）',{exact:true}).fill('6');await page.getByLabel('需求单位',{exact:true}).fill('个');await page.getByRole('button',{name:'添加到共同清单',exact:true}).click();await page.getByText('鸡蛋 · 6 个',{exact:true}).waitFor()
 await page.getByRole('button',{name:'我来采购',exact:true}).click();await page.getByText('我已认领',{exact:true}).waitFor();await page.getByRole('button',{name:'标记已购买',exact:true}).click();await page.getByText('已购买，等待确认实际入库',{exact:true}).waitFor()
 assert.deepEqual((await(await page.request.get(api+'/households/'+family,{headers})).json()).inventory,[])
 await page.getByRole('button',{name:'确认实际入库信息',exact:true}).click();await page.getByLabel('实际数量',{exact:true}).fill('5');await page.getByLabel('储存方式',{exact:true}).selectOption('冷藏');await page.getByLabel('保质期（天，可未知）',{exact:true}).fill('7');await page.getByRole('button',{name:'确认入家庭冰箱',exact:true}).click();await page.getByText('鸡蛋 · 已入库',{exact:true}).waitFor()
 const inventory=(await(await page.request.get(api+'/households/'+family,{headers})).json()).inventory;assert.equal(inventory.length,1);assert.equal(inventory[0].quantity,5);assert.equal(inventory[0].unit,'个')
 await page.getByLabel('选择来源',{exact:true}).selectOption('recipe_001');await page.getByRole('button',{name:'确认生成采购需求',exact:true}).click();await page.getByText('鸡蛋 · 3 个',{exact:true}).waitFor()
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);await page.locator('.el-message').waitFor({state:'hidden'});fs.mkdirSync('tmp/shopping',{recursive:true});await page.screenshot({path:'tmp/shopping/purchased-history.png',fullPage:true,animations:'disabled'})
 console.log(JSON.stringify({suite:'shopping-mobile-real-api',checks:['manual-demand','claim','bought-without-stock-write','actual-quantity-confirmation','stock-readback','history','recipe-generation','mobile-layout']}))
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)})
