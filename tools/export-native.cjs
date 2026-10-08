// Export the approved browser artwork and Jost rasterization for the native GUI.
const {chromium}=require('../.qa/node_modules/playwright-core');
const fs=require('node:fs');const path=require('node:path');const {pathToFileURL}=require('node:url');
(async()=>{
const root=path.resolve(__dirname,'..'),out=path.join(root,'src/main/resources/assets/xingclient/textures');fs.mkdirSync(out,{recursive:true});
const browser=await chromium.launch({executablePath:'C:/Users/jgiam/AppData/Local/ms-playwright/chromium-1223/chrome-win64/chrome.exe',headless:true});
try{
const page=await browser.newPage();
await page.addInitScript(()=>{window.exportCanvases=[];const create=document.createElement.bind(document);document.createElement=function(tag,...args){const el=create(tag,...args);if(tag==='canvas')window.exportCanvases.push(el);return el}});
await page.goto(pathToFileURL(path.join(root,'index.html')).href);await page.evaluate(()=>document.fonts.ready);
const sprites=await page.evaluate(()=>window.exportCanvases.filter(c=>c.width===192&&c.height===192).map(c=>c.toDataURL()));
if(sprites.length!==4)throw Error('Expected four approved blossom sprites');
sprites.forEach((data,i)=>fs.writeFileSync(path.join(out,`blossom-${i}.png`),Buffer.from(data.split(',')[1],'base64')));
const atlases=await page.evaluate(async()=>{const result=[];for(let size=10;size<=16;size++){await document.fonts.load(`${size*2}px Jost`);const canvas=document.createElement('canvas');canvas.width=1024;canvas.height=384;const ctx=canvas.getContext('2d');ctx.font=`${size*2}px Jost`;ctx.fillStyle='white';ctx.textBaseline='middle';const advance=[];for(let i=0;i<95;i++){const ch=String.fromCharCode(i+32);advance.push(ctx.measureText(ch).width/2);ctx.fillText(ch,(i%16)*64+4,Math.floor(i/16)*64+32)}result.push({size,advance,data:canvas.toDataURL()})}return result});
const metrics={};for(const a of atlases){fs.writeFileSync(path.join(out,`jost-${a.size}.png`),Buffer.from(a.data.split(',')[1],'base64'));metrics[a.size]=a.advance}
fs.writeFileSync(path.join(out,'jost-metrics.json'),JSON.stringify(metrics));
console.log('Exported 4 blossom sprites and 7 Jost atlases with exact browser advance widths.');
}finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
