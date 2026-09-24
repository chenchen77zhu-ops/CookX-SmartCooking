// Real session and SQLite API across separate mobile browser accounts.
const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright')
const assert=require('node:assert/strict'),fs=require('node:fs')
;(async()=>{const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})});try{
 const base=process.env.COOKX_BASE_URL||'http://127.0.0.1:4176'
 const pages=[]
 for(const name of ['sqlite-alice','sqlite-bob']){
  const context=await browser.newContext({viewport:{width:390,height:844}}),page=await context.newPage();pages.push(page)
  await page.route('**/api/**',async route=>{const u=new URL(route.request().url());await route.fulfill({response:await route.fetch({url:'http://127.0.0.1:8001'+u.pathname+u.search})})})
  await page.goto(base+'/#/login');await page.getByPlaceholder('请输入昵称').fill(name);await page.getByPlaceholder('请输入密码').fill('test-pass-123');await page.getByRole('button',{name:'登录',exact:true}).click();await page.waitForURL('**/#/home')
 }
 const [alice,bob]=pages
 await alice.goto(base+'/#/home?tab=Manage')
 await alice.getByRole('button',{name:'添加食材',exact:true}).first().click();await alice.getByPlaceholder('如：牛肉').fill('番茄');await alice.getByLabel('保质期（天，可未知）',{exact:true}).fill('2.3');await alice.getByRole('button',{name:'确认添加',exact:true}).click();await alice.getByRole('dialog',{name:'添加食材'}).waitFor({state:'hidden'})
 const a=await alice.evaluate(()=>({user:JSON.parse(localStorage.getItem('user')),session:JSON.parse(sessionStorage.getItem('cookx:session:v1'))}))
 const b=await bob.evaluate(()=>({user:JSON.parse(localStorage.getItem('user')),session:JSON.parse(sessionStorage.getItem('cookx:session:v1'))}))
 assert.ok(a.session.access_token);assert.ok(!('password_hash'in a.user))
 const api='http://127.0.0.1:8001/api'
 assert.equal((await alice.request.get(api+'/inventory?user_id='+b.user.id,{headers:{Authorization:'Bearer '+a.session.access_token}})).status(),403)
 const bobRows=await(await bob.request.get(api+'/inventory?user_id='+b.user.id,{headers:{Authorization:'Bearer '+b.session.access_token}})).json();assert.deepEqual(bobRows,[])
 await alice.reload();await alice.getByText('西红柿',{exact:true}).first().waitFor()
 fs.mkdirSync('tmp/sqlite',{recursive:true});await alice.screenshot({path:'tmp/sqlite/authenticated-inventory.png',fullPage:true,animations:'disabled'})
 await alice.goto(base+'/#/profile');await alice.getByRole('button',{name:'退出登录',exact:true}).click();await alice.locator('.el-message-box__btns .el-button--primary').click();await alice.waitForURL('**/#/login')
 assert.equal((await alice.request.get(api+'/auth/session',{headers:{Authorization:'Bearer '+a.session.access_token}})).status(),401)
 assert.equal(await alice.evaluate(()=>localStorage.getItem('user')),null)
 console.log(JSON.stringify({suite:'sqlite-auth-real-api',checks:['two-mobile-accounts','real-sqlite-write','no-hash-exposure','cross-user-denied','reload-session','logout-revocation']}))
}finally{await browser.close()}})().catch(e=>{console.error(e);process.exit(1)})
