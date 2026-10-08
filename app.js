const categories = [
  {name:'Combat',modules:['AutoArmor','AutoBowRelease','AutoCrystal','AutoTrap','Blocker','Criticals','HoleBuilder','HoleFill','KillAura','Mainhand','Offhand','PearlLauncher','SelfBow','SelfFill','SelfTrap','Suicide','Surround'],on:['AutoCrystal','Blocker','KillAura','Offhand']},
  {name:'Player',modules:['AirPlace','FastPlace','MultiTask','NoEntityTrace','NoInteract','PingSpoof','Reach','Replenish','SpeedMine','Swing','ThrowFirework','ThrowPearl','ThrowXP'],on:['FastPlace','MultiTask','NoInteract','Reach','Replenish','SpeedMine']},
  {name:'Visuals',modules:['Atmosphere','BlockHighlight','BreakHighlight','Chams','Compass','Crosshair','ESP','EntityModifier','FOVModifier','Freecam','FullBright','HandProgress','HoleESP','NameTags','NoRender','PhaseESP','PopChams','RangeVisualizer','Shaders','TextESP','Tracers','Trails','ViewClip','ViewModel'],on:['Atmosphere','BlockHighlight','BreakHighlight','Compass','Crosshair','FOVModifier','FullBright','HandProgress','HoleESP','NameTags','NoRender','PhaseESP','PopChams','RangeVisualizer','Shaders','TextESP','Trails','ViewClip','ViewModel']},
  {name:'Movement',modules:['FakeLag','FastAccel','FastFall','HitboxDesync','HoleSnap','InventoryControl','LagRange','NoRotate','NoSlow','Phase','Speed','Sprint','Step','TickShift','Velocity'],on:['InventoryControl','NoRotate','NoSlow','Sprint','Velocity']},
  {name:'Miscellaneous',modules:['Assist','AutoKit','AutoReconnect','AutoRespawn','BetterChat','FastLatency','FrameSpoofer','LagNotify','MouseFix','NameProtect','Notifications','ServerHandshake','Timer'],on:['AutoReconnect','AutoRespawn','BetterChat','FastLatency','LagNotify','Notifications']},
  {name:'Core',modules:['Chat','ClickGui','Color','Font','HUD','Renders','Robotics','Rotations','Waypoints'],on:['Chat','ClickGui','Color','Font','HUD','Renders','Rotations']}
];
// A separate revision leaves the first sketch's saved layout intact.
const storageKey='xingpaozhieniaohaolongbao-ui-v2';
let saved={};try{saved=JSON.parse(localStorage.getItem(storageKey)||'{}')||{}}catch{}
const state={enabled:saved.enabled||{},settings:saved.settings||{},positions:saved.positions||{},accent:saved.accent||'#edacd0',secondary:saved.secondary||'#a6d6f4',opacity:saved.opacity||90,mascot:saved.mascot!==false,blossoms:saved.blossoms!==false};
const workspace=document.querySelector('#workspace');let topZ=5;let capturing=null;
function save(){try{localStorage.setItem(storageKey,JSON.stringify(state))}catch{}}
function notify(message){const toast=document.querySelector('#toast');toast.textContent=message;toast.classList.add('visible');clearTimeout(notify.timer);notify.timer=setTimeout(()=>toast.classList.remove('visible'),1500)}
function theme(){
  const valid=(hex,fallback)=>/^#[0-9a-f]{6}$/i.test(hex)?hex:fallback;
  state.accent=valid(state.accent,'#edacd0');state.secondary=valid(state.secondary,'#a6d6f4');
  document.documentElement.style.setProperty('--accent',state.accent);document.documentElement.style.setProperty('--secondary',state.secondary);document.documentElement.style.setProperty('--panel-opacity',state.opacity/100);
  const rgb=hex=>hex.slice(1).match(/../g).map(v=>parseInt(v,16)),a=rgb(state.accent),b=rgb(state.secondary);
  document.querySelectorAll('.panel').forEach((panel,i)=>panel.style.setProperty('--panel-color',`rgb(${a.map((v,c)=>Math.round(v+(b[c]-v)*i/5)).join(',')})`));
  document.querySelector('#accent').value=state.accent;document.querySelector('#secondary').value=state.secondary;document.querySelector('#opacity').value=state.opacity;document.querySelector('#show-mascot').checked=state.mascot;document.querySelector('.mascot').hidden=!state.mascot;
  document.querySelector('#show-blossoms').checked=state.blossoms;
  window.dispatchEvent(new Event('blossomsettingschange'));
}
function layout(reset=false){const width=document.querySelector('.panel').offsetWidth,step=width+8;document.querySelectorAll('.panel').forEach((panel,i)=>{const pos=!reset&&state.positions[i];panel.style.left=`${pos?Math.max(0,Math.min(pos.x,Math.max(workspace.clientWidth-width,step*5))):i*step}px`;panel.style.top=`${pos?Math.max(0,Math.min(pos.y,workspace.clientHeight-28)):0}px`})}
categories.forEach((category,index)=>{
  const panel=document.createElement('section');panel.className='panel';panel.setAttribute('aria-label',`${category.name} modules`);
  panel.innerHTML=`<div class="panel-header"><span class="category-name">${category.name}</span><button class="collapse" aria-label="Collapse ${category.name}" aria-expanded="true">−</button></div><div class="module-list"><div class="empty">no matching modules</div></div>`;
  const list=panel.querySelector('.module-list');
  category.modules.forEach(name=>{
    const key=`${category.name}.${name}`;if(!(key in state.enabled))state.enabled[key]=category.on.includes(name);
    const settings=state.settings[key]||{mode:'normal',value:50,visible:true,bind:'none'};state.settings[key]=settings;
    const module=document.createElement('div');module.className='module';module.dataset.name=name.toLowerCase();module.classList.toggle('enabled',state.enabled[key]);
    module.innerHTML=`<div class="module-row"><button class="module-toggle" aria-pressed="${state.enabled[key]}">${name}</button><button class="module-options" aria-label="Settings for ${name}" aria-expanded="false">›</button></div><div class="module-settings" hidden><label class="setting-row">mode<select aria-label="${name} mode"><option>normal</option><option>strict</option><option>custom</option></select></label><label class="range-setting"><span>intensity<output></output></span><input type="range" min="0" max="100" aria-label="${name} intensity"></label><label class="setting-row">show in hud<input type="checkbox" aria-label="Show ${name} in hud"></label><div class="setting-row">keybind<button class="bind-button" aria-label="Bind ${name}"></button></div></div>`;
    const toggle=module.querySelector('.module-toggle'),options=module.querySelector('.module-options'),details=module.querySelector('.module-settings');
    toggle.onclick=()=>{state.enabled[key]=!state.enabled[key];module.classList.toggle('enabled',state.enabled[key]);toggle.setAttribute('aria-pressed',state.enabled[key]);save()};
    function expand(){const open=details.hidden;details.hidden=!open;module.classList.toggle('open',open);options.textContent=open?'⌄':'›';options.setAttribute('aria-expanded',open)}
    options.onclick=expand;module.querySelector('.module-row').oncontextmenu=e=>{e.preventDefault();expand()};
    const select=module.querySelector('select');select.value=settings.mode;select.onchange=()=>{settings.mode=select.value;save()};
    const range=module.querySelector('input[type=range]');range.value=settings.value;module.querySelector('output').textContent=range.value+'%';range.oninput=()=>{settings.value=Number(range.value);module.querySelector('output').textContent=range.value+'%';save()};
    const check=module.querySelector('input[type=checkbox]');check.checked=settings.visible;check.onchange=()=>{settings.visible=check.checked;save()};
    const bind=module.querySelector('.bind-button');bind.textContent=settings.bind;bind.onclick=()=>{if(capturing)capturing.button.textContent=capturing.settings.bind;capturing={settings,button:bind};bind.textContent='press key';notify('Press a key · Esc cancels · Delete clears')};list.append(module);
  });
  const collapse=panel.querySelector('.collapse');function setCollapsed(value){panel.classList.toggle('collapsed',value);collapse.textContent=value?'+':'−';collapse.setAttribute('aria-expanded',!value);collapse.setAttribute('aria-label',`${value?'Expand':'Collapse'} ${category.name}`)}
  collapse.onclick=()=>setCollapsed(!panel.classList.contains('collapsed'));
  const header=panel.querySelector('.panel-header');header.oncontextmenu=e=>{e.preventDefault();setCollapsed(!panel.classList.contains('collapsed'))};
  header.onpointerdown=e=>{if(e.target.closest('button')||e.button!==0)return;panel.style.zIndex=++topZ;const startX=e.clientX,startY=e.clientY,x=panel.offsetLeft,y=panel.offsetTop;header.setPointerCapture(e.pointerId);header.onpointermove=ev=>{const nextX=Math.max(0,Math.min(x+ev.clientX-startX,Math.max(workspace.scrollWidth,workspace.clientWidth)-panel.offsetWidth));const nextY=Math.max(0,Math.min(y+ev.clientY-startY,workspace.clientHeight-28));panel.style.left=nextX+'px';panel.style.top=nextY+'px';state.positions[index]={x:nextX,y:nextY}};const end=()=>{header.onpointermove=null;header.onpointerup=null;header.onpointercancel=null;save()};header.onpointerup=end;header.onpointercancel=end};workspace.append(panel);
});
document.querySelector('#search').oninput=e=>{const query=e.target.value.trim().toLowerCase();document.querySelectorAll('.panel').forEach((panel,i)=>{let found=0;panel.querySelectorAll('.module').forEach(module=>{const match=module.dataset.name.includes(query);module.hidden=!match;if(match)found++});panel.classList.toggle('no-results',!found);if(query){panel.classList.remove('collapsed');const collapse=panel.querySelector('.collapse');collapse.textContent='−';collapse.setAttribute('aria-expanded','true');collapse.setAttribute('aria-label',`Collapse ${categories[i].name}`)}})};
document.querySelector('#reset').onclick=()=>{state.positions={};layout(true);save();notify('Panel positions restored')};
const appearance=document.querySelector('#appearance-panel');function showAppearance(open){appearance.hidden=!open;document.querySelector('#appearance').setAttribute('aria-expanded',open)}
document.querySelector('#appearance').onclick=()=>showAppearance(appearance.hidden);document.querySelector('#close-appearance').onclick=()=>showAppearance(false);
for(const key of ['accent','secondary','opacity'])document.querySelector('#'+key).oninput=e=>{state[key]=key==='opacity'?Number(e.target.value):e.target.value;theme();save()};
document.querySelector('#show-mascot').onchange=e=>{state.mascot=e.target.checked;theme();save()};
document.querySelector('#show-blossoms').onchange=e=>{state.blossoms=e.target.checked;theme();save()};
document.querySelector('#restore-theme').onclick=()=>{Object.assign(state,{accent:'#edacd0',secondary:'#a6d6f4',opacity:90,mascot:true,blossoms:true});theme();save();notify('Cotton candy theme restored')};
function toggleInterface(){const ui=document.querySelector('#interface');ui.hidden=!ui.hidden;document.querySelector('#reopen').hidden=!ui.hidden}
document.querySelector('#reopen').onclick=toggleInterface;
document.addEventListener('keydown',e=>{if(capturing){e.preventDefault();if(e.code==='ShiftRight'){notify('Right Shift is reserved for the menu');return}if(e.key!=='Escape')capturing.settings.bind=['Backspace','Delete'].includes(e.key)?'none':e.key.toLowerCase();capturing.button.textContent=capturing.settings.bind;capturing=null;save();return}const typing=/INPUT|SELECT|TEXTAREA/.test(document.activeElement.tagName);if(e.code==='ShiftRight'&&!e.repeat){e.preventDefault();toggleInterface()}else if(e.key==='Escape'){if(!appearance.hidden)showAppearance(false);else if(typing)document.activeElement.blur();else if(!document.querySelector('#interface').hidden)toggleInterface()}else if(e.key==='/'&&!typing&&!document.querySelector('#interface').hidden){e.preventDefault();document.querySelector('#search').focus()}});
window.addEventListener('resize',()=>layout());theme();layout();
