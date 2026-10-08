const {chromium}=require('./.qa/node_modules/playwright-core');
const assert=require('node:assert/strict');
const {pathToFileURL}=require('node:url');
const path=require('node:path');
(async()=>{
const browser=await chromium.launch({executablePath:'C:/Users/jgiam/AppData/Local/ms-playwright/chromium-1223/chrome-win64/chrome.exe',headless:true});
try {
const page=await browser.newPage({viewport:{width:1920,height:1080}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
assert.equal(await page.locator('.module').count(),91);
assert.equal(await page.locator('body').evaluate(el=>getComputedStyle(el).backgroundColor),'rgb(0, 0, 0)');
assert.equal(await page.locator('.mascot').evaluate(el=>el.complete&&el.naturalWidth>0),true);
assert.equal(await page.locator('.panel-header').first().evaluate(el=>el.offsetHeight),26);
assert.equal(await page.locator('.panel-bottom,.category-char,footer').count(),0);
assert.equal(await page.locator('#interface').evaluate(el=>/[\u3400-\u9fff]/.test(el.innerText)),false);
const armor=page.getByRole('button',{name:'AutoArmor',exact:true});await armor.click();assert.equal(await armor.getAttribute('aria-pressed'),'true');await page.reload();assert.equal(await armor.getAttribute('aria-pressed'),'true');await armor.click();
const crystal=page.getByRole('button',{name:'AutoCrystal',exact:true});await crystal.click({button:'right'});assert.equal(await page.getByLabel('AutoCrystal mode').isVisible(),true);await page.getByLabel('AutoCrystal mode').selectOption('strict');await crystal.click({button:'right'});
await page.getByRole('textbox',{name:'Search modules'}).fill('crystal');assert.equal(await page.locator('.module:visible').count(),1);await page.getByRole('textbox',{name:'Search modules'}).fill('');
const header=page.locator('.panel-header').first();await header.click({button:'right'});assert.equal(await armor.isVisible(),false);await header.click({button:'right'});
const before=await header.boundingBox();await page.mouse.move(before.x+65,before.y+12);await page.mouse.down();await page.mouse.move(before.x+94,before.y+35);await page.mouse.up();assert((await header.boundingBox()).x>before.x);await page.getByRole('button',{name:'layout',exact:true}).click();
await page.getByRole('button',{name:'appearance',exact:true}).click();await page.getByLabel('show mascot',{exact:true}).uncheck();assert.equal(await page.locator('.mascot').isVisible(),false);await page.getByRole('button',{name:'restore cotton candy'}).click();assert.equal(await page.locator('.mascot').isVisible(),true);await page.getByRole('button',{name:'Close appearance',exact:true}).click();
await page.keyboard.press('ShiftRight');assert.equal(await page.locator('#reopen').isVisible(),true);await page.keyboard.press('ShiftRight');assert.equal(await page.locator('#interface').isVisible(),true);
await page.waitForFunction(()=>!document.querySelector('#toast').classList.contains('visible'));
await page.mouse.move(1800,500);await page.screenshot({path:'.qa/cotton-desktop.png'});
await page.setViewportSize({width:1000,height:720});await page.screenshot({path:'.qa/cotton-small.png'});
await page.setViewportSize({width:390,height:844});assert.equal(await page.locator('body').evaluate(el=>el.scrollWidth),390);await page.screenshot({path:'.qa/cotton-mobile.png'});
assert.deepEqual(errors,[]);console.log('PASS: direct file loading, mascot, 91 modules, black background, compact headers, no Chinese labels or footers, toggles, persistence, settings, search, collapse, drag/reset, appearance, hide/reopen, responsive layout.');
}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
