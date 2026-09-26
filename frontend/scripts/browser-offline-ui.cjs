// Actual bundled local adapter; no mocked API and no external requests permitted.
const { chromium } = require(process.env.COOKX_PLAYWRIGHT || 'playwright')
const assert = require('node:assert/strict'), fs = require('node:fs')
const base = process.env.COOKX_BASE_URL || 'http://127.0.0.1:4175', out = 'tmp/localtest/ui'
;(async () => {
 fs.mkdirSync(out, { recursive: true })
 const browser = await chromium.launch({ headless: true, ...(process.env.COOKX_CHROME ? { executablePath: process.env.COOKX_CHROME } : {}) })
 const report = [], errors = [], forbidden = []
 try {
  for (const [width,height] of [[360,780],[390,844],[430,932]]) for (const theme of ['light','dark']) {
   const context = await browser.newContext({viewport:{width,height},reducedMotion:'reduce'})
   const p = await context.newPage(); p.on('pageerror', e => errors.push(e.message)); p.setDefaultTimeout(15000)
   await p.addInitScript(theme => {
    localStorage.setItem('cookx:theme',theme)
    document.addEventListener('DOMContentLoaded', () => {
     document.documentElement.style.setProperty('--safe-area-inset-top','24px')
     document.documentElement.style.setProperty('--safe-area-inset-bottom','24px')
    })
   }, theme)
   await p.route('**/*', r => { const u = new URL(r.request().url()); if(u.origin !== base || u.pathname.startsWith('/api')) { forbidden.push(u.href); return r.abort() } return r.continue() })
   async function snap(name, oneScreen=false) {
    await p.evaluate(() => document.fonts.ready); await p.waitForTimeout(350)
    const metrics = await p.evaluate(() => ({width:document.documentElement.scrollWidth,height:document.documentElement.scrollHeight,viewportHeight:innerHeight}))
    await p.screenshot({path:`${out}/${width}x${height}-${theme}-${name}.png`,animations:'disabled'})
    assert.ok(metrics.width <= width+1, `${name}: horizontal overflow ${metrics.width}/${width}`)
    assert.ok(!oneScreen || metrics.height <= height+1, `${name}: vertical overflow ${metrics.height}/${height}`)
    report.push({name,width,height,theme,...metrics})
   }
   for (const [name,path] of [['home','home'],['fridge','home?tab=Manage'],['items','fridge'],['recipes','home?tab=Recipes'],['profile','profile'],['live','home?tab=AiChef']]) {
    await p.goto(`${base}/#/${path}`); await p.waitForTimeout(500)
    assert.equal(await p.locator('html').getAttribute('data-cookx-mode'),'local-test')
    await snap(name,['home','live'].includes(name))
    if(name==='home') {
     assert.ok(await p.evaluate(() => document.querySelector('.expiry').getBoundingClientRect().bottom <= document.querySelector('.tabbar').getBoundingClientRect().top))
     await p.getByRole('button',{name:/常用服务/}).click(); assert.equal(await p.locator('.service').count(),8); await p.keyboard.press('Escape')
    }
    if(name==='fridge') assert.ok(await p.evaluate(() => {
     const card=document.querySelector('.fridge-card').getBoundingClientRect(), art=document.querySelector('.fridge-art').getBoundingClientRect(), copy=document.querySelector('.fridge-card__copy').getBoundingClientRect()
     return art.left>=copy.right && art.right<=card.right && art.bottom<=card.bottom
    }), 'fridge art must fit')
   }
   await p.goto(`${base}/#/home?tab=Recipes`)
   await p.getByRole('button',{name:'载入演练菜谱',exact:true}).click()
   await p.getByRole('button',{name:'开始指导',exact:true}).click()
   await p.locator('.ai-chef-container.is-cooking').waitFor(); await snap('cooking',true)
   await p.getByRole('button',{name:'烹饪工具',exact:true}).click()
   await p.getByRole('button',{name:'下一步',exact:true}).waitFor()
   await context.close(); console.log(`${width}x${height} ${theme}: passed with 24px system insets + offline toolbar`)
  }
  assert.deepEqual(errors,[]); assert.deepEqual(forbidden,[])
  fs.writeFileSync(`${out}/results.json`,JSON.stringify({screenshots:report.length,report,errors,forbidden,source:'offline fixtures; no device installation claim'},null,2))
 } finally { await browser.close() }
})().catch(e=>{console.error(e);process.exit(1)})
