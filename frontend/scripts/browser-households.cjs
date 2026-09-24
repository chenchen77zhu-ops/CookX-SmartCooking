const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright'),assert=require('node:assert/strict'),fs=require('node:fs')
;(async()=>{const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})});try{
 const base=process.env.COOKX_BASE_URL||'http://127.0.0.1:4176',pages=[]
 for(const name of ['sqlite-alice','sqlite-bob']){const page=await browser.newPage({viewport:{width:390,height:844}});pages.push(page);await page.route('**/api/**',async r=>{const u=new URL(r.request().url());await r.fulfill({response:await r.fetch({url:'http://127.0.0.1:8001'+u.pathname+u.search})})});await page.goto(base+'/#/login');await page.getByPlaceholder('请输入昵称').fill(name);await page.getByPlaceholder('请输入密码').fill('test-pass-123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/#/home');await page.goto(base+'/#/household');await page.getByLabel('我的家庭',{exact:true}).selectOption('')}
 const [a,b]=pages
 await a.getByLabel('家庭名称',{exact:true}).fill('浏览器家庭');await a.getByRole('button',{name:'创建家庭',exact:true}).click();await a.getByRole('heading',{name:'共享库存',exact:true}).waitFor()
 await a.getByRole('button',{name:'生成一次性邀请',exact:true}).click();await a.locator('code').waitFor();const code=await a.locator('code').innerText()
 await b.getByLabel('家庭邀请码',{exact:true}).fill(code);await b.getByRole('button',{name:'确认加入',exact:true}).click();await b.getByRole('heading',{name:'共享库存',exact:true}).waitFor()
 await a.getByLabel('食材名称',{exact:true}).fill('面粉');await a.getByLabel('实际数量',{exact:true}).fill('1.5');await a.getByLabel('计量单位',{exact:true}).selectOption('千克');await a.getByRole('button',{name:'确认入库',exact:true}).click();await a.getByText('面粉 · 1.5 千克',{exact:true}).waitFor()
 await b.getByRole('button',{name:'刷新家庭',exact:true}).click();await b.getByText('面粉 · 1.5 千克',{exact:true}).waitFor();await b.getByText('数据不足',{exact:false}).first().waitFor()
 assert.equal(await a.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false)
 await b.locator('.el-message').waitFor({state:'hidden'});fs.mkdirSync('tmp/household',{recursive:true});await b.screenshot({path:'tmp/household/member-inventory.png',fullPage:true,animations:'disabled'})
 await b.getByRole('button',{name:'退出家庭',exact:true}).click();await b.locator('.el-message-box__btns .el-button--primary').click();await b.getByRole('heading',{name:'创建或加入家庭',exact:true}).waitFor()
 await a.getByRole('button',{name:'刷新家庭',exact:true}).click();await a.getByText('成员 1 人',{exact:false}).waitFor()
 console.log(JSON.stringify({suite:'household-mobile-real-api',checks:['create','invite','join','shared-quantity-unit','freshness-unknown','no-horizontal-overflow','leave-revokes','independent-accounts']}))
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)})
