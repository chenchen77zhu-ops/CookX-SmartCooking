// Deterministic visual fixtures, NOT cloud / hardware acceptance.
// Run against Vite dev: the production APK deliberately has no temperature injection hook.
const {chromium}=require(process.env.COOKX_PLAYWRIGHT||'playwright')
const assert=require('node:assert/strict'),fs=require('node:fs')
const base=process.env.COOKX_UI_BASE_URL||'http://127.0.0.1:5173', out=process.env.COOKX_UI_SAFE_AREA?'tmp/ui-review/safe-area':'tmp/ui-review'
const sizes=[[360,780],[390,844],[430,932]], themes=['light','dark'], report=[]
const inventory=[['牛奶',2],['豆腐',1],['鸡蛋',3],['西兰花',1],['鸡胸肉',2],['大米',2]].map(([name,quantity],i)=>({id:`ui-${i}`,name,quantity,storage_type:'冷藏',shelf_life:7,purchase_time:'2026-09-23T09:00:00+08:00',add_time:'2026-09-23T09:00:00+08:00',expiry_date:i<2?'2026-09-27T09:00:00+08:00':'2026-09-30T09:00:00+08:00'}))
const recipe={dish_name:'香煎鸡胸肉',image_url:'/src/assets/images/home-hero-dish.webp',steps:[{title:'热锅',text:'等待锅温达到菜谱目标温区，再放入鸡胸肉。',temperature:'170–180°C',time_estimate:60},{title:'开始煎制',text:'观察表面变化，按需翻面。',time_estimate:240}]}
async function inject(page,temp,history=true){
 await page.evaluate(({temp,history})=>{const hook=window.__cookxLive;const now=Date.now();hook.live.history=history?Array.from({length:90},(_,i)=>({at:now-(89-i)*4000,t:25+(temp-25)*i/89+Math.sin(i/4)*2})):[];hook.inject(temp)}, {temp,history})
}
async function snap(page,name,width,height,theme,oneScreen=false){
 await page.evaluate(()=>document.fonts.ready);await page.waitForTimeout(180)
 const metrics=await page.evaluate(()=>({scrollWidth:document.documentElement.scrollWidth,scrollHeight:document.documentElement.scrollHeight,viewportWidth:innerWidth,viewportHeight:innerHeight,safeArea:parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--sat'))||0,safeBottom:parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--sab'))||0,boxes:Object.fromEntries([...document.querySelectorAll('.home-sense,.expiry,.k-hero,.k-pan-space,.k-stages,.k-trend,.k-live-advice,.tabbar,.c-recipe,.c-gauge,.c-chart,.c-duo,.current-step-card')].map(e=>{const r=e.getBoundingClientRect();return [e.matches('.k-stages')?'k-stages':e.matches('.k-trend')?'k-trend':e.classList[0],{top:r.top,bottom:r.bottom,left:r.left,right:r.right}]}))}))
 assert.ok(metrics.scrollWidth<=width+1,`${name} horizontal overflow`)
 if(oneScreen && metrics.scrollHeight>height+1){console.log(metrics);await page.screenshot({path:out+'/debug-overflow.png',animations:'disabled'})}
 if(oneScreen) assert.ok(metrics.scrollHeight<=height+1,`${name} vertical overflow: ${metrics.scrollHeight}/${height}`)
 if(name==='cooking') assert.ok(metrics.boxes['current-step-card'].bottom<=height-metrics.safeBottom,'cooking advice under system navigation')
 if(name==='home') assert.ok(metrics.boxes.expiry.bottom<=metrics.boxes.tabbar.top, 'home reminder under tabbar')
 if(name==='live') {assert.ok(metrics.boxes.tabbar.top-metrics.boxes['k-advice'].bottom>=11.9,'advice needs 12px clearance');assert.ok(metrics.boxes['k-pan-space'].bottom-metrics.boxes['k-pan-space'].top>=height*.12-metrics.safeArea*2,'pan space must remain visible')}
 const path=`${out}/${width}x${height}-${theme}-${name}.png`
 await page.screenshot({path,animations:'disabled'})
 report.push({name,width,height,theme,path,...metrics})
}
;(async()=>{fs.mkdirSync(out,{recursive:true});const browser=await chromium.launch({headless:true,...(process.env.COOKX_CHROME?{executablePath:process.env.COOKX_CHROME}:{})})
 try {for(const [width,height] of sizes) for(const theme of themes){
  const context=await browser.newContext({viewport:{width,height},reducedMotion:'reduce'}),page=await context.newPage(),errors=[]
  page.setDefaultTimeout(12000);page.on('pageerror',e=>errors.push(e.message))
  if(process.env.COOKX_UI_SAFE_AREA)await page.addInitScript(()=>document.addEventListener('DOMContentLoaded',()=>{document.documentElement.style.setProperty('--sat','24px');document.documentElement.style.setProperty('--sab','24px')}))
  await page.addInitScript(theme=>{localStorage.setItem('user',JSON.stringify({id:'ui-visual-fixture',username:'小厨'}));localStorage.setItem('cookx:theme',theme)},theme)
  await page.route('**/api/**',r=>{const path=new URL(r.request().url()).pathname;if(!path.startsWith('/api/'))return r.continue()
   if(path.endsWith('/inventory/freshness'))return r.fulfill({json:{evaluated_at:'2026-09-26T09:00:00+08:00',items:inventory.map((item,i)=>({item_id:item.id,fresh_score:i<2?62:88,freshness_label:i<2?'临期':'新鲜',expiring_soon:i<2,confidence_score:.7,component_scores:{T:80,S:70,V:null,H:null},reasons:['日期与储存信息（界面测试数据）'],disclaimer:'辅助判断，不能替代食品安全检测'}))}})
   if(path.includes('/user/'))return r.fulfill({json:{status:'success',user:{id:'ui-visual-fixture',username:'小厨',nickname:'小厨',created_at:'2026-09-01T00:00:00+08:00'}}})
   if(path.endsWith('/inventory'))return r.fulfill({json:inventory})
   if(path.endsWith('/recommend-recipe'))return r.fulfill({json:{status:'success',recipe}})
   if(path.includes('/tts'))return r.fulfill({status:503,json:{detail:'visual fixture: voice disabled'}})
   return r.fulfill({json:[]})
  })
  for(const [name,path] of [['home','home'],['fridge','home?tab=Manage'],['all-food','fridge'],['ai-recipes','home?tab=Recipes'],['profile','profile'],['live','home?tab=AiChef']]){
   await page.goto(base+'/#/'+path);await page.waitForTimeout(600);if(process.env.COOKX_UI_SAFE_AREA)await page.evaluate(()=>{document.documentElement.style.setProperty('--sat','24px');document.documentElement.style.setProperty('--sab','24px')})
   if(name==='live')await inject(page,180)
   await snap(page,name,width,height,theme,['home','live'].includes(name))
   if(name==='home'){
    await page.getByRole('button',{name:'常用服务',exact:false}).click();assert.equal(await page.locator('.service').count(),8);await page.keyboard.press('Escape')
   }
   if(name==='fridge'){
    const fit=await page.evaluate(()=>{const card=document.querySelector('.fridge-card').getBoundingClientRect(),img=document.querySelector('.fridge-art').getBoundingClientRect(),copy=document.querySelector('.fridge-card__copy').getBoundingClientRect();return img.right<=card.right&&img.left>=copy.right&&img.bottom<=card.bottom})
    assert.ok(fit,'fridge must not clip or overlap statistics')
   }
  }
  if(width===390&&theme==='light')for(const t of [25,120,180,270]){await inject(page,t,false);await snap(page,`live-${t}`,width,height,theme,true)}
  await page.evaluate(async recipe=>{const {queueRecipeDraft}=await import('/src/services/recipeDraft.js');queueRecipeDraft('ui-visual-fixture',recipe)},recipe)
  await page.goto(base+'/#/home?tab=Recipes');await page.reload();await page.getByRole('button',{name:'开始指导',exact:true}).last().click();await inject(page,178)
  await snap(page,'cooking',width,height,theme,true)
  await page.getByRole('button',{name:'烹饪工具',exact:true}).click();await page.getByRole('button',{name:'暂停计时',exact:true}).click();assert.ok(await page.getByRole('button',{name:'下一步',exact:true}).isVisible());await page.keyboard.press('Escape')
  assert.deepEqual(errors,[]);await context.close()
  console.log(`${width}×${height} ${theme}: seven pages passed`)
 }
 fs.writeFileSync(out+'/layout-results.json',JSON.stringify({source:'visual fixtures; injected temperature is simulation',screenshots:report.length,report},null,2))
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)})
