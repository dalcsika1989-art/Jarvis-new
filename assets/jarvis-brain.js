(function(){
'use strict';
// JARVIS local knowledge visualizer. Data are explicit, user-approved notes.
// This is not model retraining, unlimited memory, or autonomous code execution.
var host=document.createElement('section');host.id='jarvisBrain';
host.setAttribute('aria-label','JARVIS interaktív tudásháló');
var st=document.createElement('style');
st.textContent='#jarvisBrain{position:fixed;inset:0;z-index:450;background:radial-gradient(ellipse at 52% 38%,#07354d 0,#010e1d 55%,#010713 100%);color:#bafaff;font:12px/1.5 sans-serif;display:none;overflow:auto;pointer-events:auto}#jarvisBrain.on{display:block}#jarvisBrain *{box-sizing:border-box}#jarvisBrain .bar{position:sticky;top:0;z-index:3;display:flex;align-items:center;justify-content:space-between;gap:8px;background:#031629ef;border-bottom:1px solid #1e819a;padding:11px 13px;letter-spacing:.13em}#jarvisBrain .bar strong{color:#e0fcff;font-size:14px}#jarvisBrain button,#jarvisBrain select{background:#092539;color:#b8f8ff;border:1px solid #327d96;border-radius:6px;padding:9px;min-height:40px;font:12px sans-serif}#jarvisBrain button:active{background:#145b73}#jarvisBrain .stage{position:relative;width:100%;height:47vh;min-height:290px;max-height:520px;border-bottom:1px solid #13516b;overflow:hidden;background:radial-gradient(circle at 50% 50%,#0e394a8a,transparent 70%)}#jarvisBrain canvas{position:absolute;inset:0;width:100%;height:100%;touch-action:none;display:block}#jarvisBrain .controls{position:absolute;z-index:2;right:9px;top:8px;display:flex;flex-direction:column;gap:5px}#jarvisBrain .controls button{font-size:17px;font-weight:bold;padding:3px;width:42px;height:42px}#jarvisBrain .hint{position:absolute;left:10px;top:10px;pointer-events:none;color:#69b7ce;font:10px monospace}#jarvisBrain .content{padding:12px;display:grid;gap:10px;grid-template-columns:1fr}#jarvisBrain .pane{border:1px solid #17607b;background:linear-gradient(135deg,#031d31ed,#071b2ae6);padding:12px;border-radius:8px;box-shadow:inset 0 0 13px #04344e}#jarvisBrain h3{font:600 13px monospace;margin:0 0 9px;letter-spacing:.1em;color:#95ecff}#jarvisBrain .count{font:11px monospace;color:#6ac7e4}#jarvisBrain .detail{font:13px/1.55 sans-serif;color:#c8f5ff;min-height:42px;white-space:pre-wrap;overflow-wrap:anywhere}#jarvisBrain .notelist{max-height:190px;overflow-y:auto;margin:8px 0}#jarvisBrain .note{display:block;width:100%;text-align:left;margin:5px 0;padding:9px;border:1px solid #22617c;border-radius:4px;background:#061b30;color:#bbf6ff}#jarvisBrain .note.on{border-color:#6beaf9;background:#104055}#jarvisBrain .form{display:grid;gap:7px}#jarvisBrain input,#jarvisBrain textarea,#jarvisBrain select{width:100%;background:#031829;color:#e4fcff;border:1px solid #296b84;border-radius:5px;padding:10px}#jarvisBrain textarea{min-height:72px;resize:vertical}#jarvisBrain .row{display:flex;gap:7px;flex-wrap:wrap}#jarvisBrain .row button{flex:1}#jarvisBrain #brainDelete{border-color:#a44964;color:#ffb4c7;display:none}#jarvisBrain #brainStatus{font:11px monospace;color:#91d7e5}#jarvisBrain .fine{font:10px/1.5 sans-serif;color:#76b3c7;margin-top:7px}@media(min-width:700px){#jarvisBrain .content{grid-template-columns:1fr 1fr}#jarvisBrain .stage{height:56vh}}';
document.head.appendChild(st);
host.innerHTML='<header class="bar"><strong>◈ JARVIS / TUDÁSHÁLÓ</strong><button id="brainClose" type="button">✕ VISSZA</button></header>'+
'<div class="stage" id="brainStage"><canvas id="brainCanvas"></canvas><div class="hint">HÚZÁS: mozgatás · CSÍPÉS: zoom · KOPPINTÁS: tétel</div><div class="controls"><button id="brainPlus" title="Nagyítás">+</button><button id="brainMinus" title="Kicsinyítés">−</button><button id="brainFit" title="Középre">◎</button></div></div>'+
'<div class="content"><div class="pane"><h3>RENDSZER / MEMÓRIA</h3><div class="count" id="brainCount"></div><h3 style="margin-top:11px" id="brainSelected">JARVIS CORE</h3><div class="detail" id="brainDetail">Érintsd meg a tudásgömböt.</div><div class="notelist" id="brainList"></div><div class="row"><button id="brainDelete">KIVÁLASZTOTT TÉTEL TÖRLÉSE</button><button id="brainCopy">TUDÁS KIMÁSOLÁSA</button></div></div>'+
'<div class="pane"><h3>ÚJ TUDÁS / JAVÍTÁS</h3><div class="form"><select id="brainCategory"><option>Tudás</option><option>Memória</option><option>Feladatok</option><option>Tapasztalat</option><option>Képességek</option><option>Ismeretlen</option></select><input id="brainTitle" maxlength="70" placeholder="A tétel címe"><textarea id="brainText" maxlength="360" placeholder="Mit szeretnél, hogy JARVIS megjegyezzen?"></textarea><button id="brainSave">◈ TUDÁS HOZZÁADÁSA</button></div><p id="brainStatus">A tételeket a telefon helyben tárolja.</p><div class="fine">A JARVIS a mentett tételeket a későbbi válaszokhoz felhasználhatja. Nem módosítja automatikusan a programkódját, és a mentett állításokat nem tekinti ellenőrzött ténynek.</div></div></div>';
document.body.appendChild(host);
var $=function(id){return document.getElementById(id)};
var categories=['Tudás','Memória','Feladatok','Tapasztalat','Képességek','Ismeretlen'];
var colors=['#26edff','#79c8ff','#fdc76b','#ff7a94','#ae93ff','#48e4af'];
var data={count:0,capacity:300,groups:[],notes:[]};
var selected={type:'core',id:''},nodes=[],visible=false,scale=1,panX=0,panY=0;
var canvas=$('brainCanvas'),ctx=canvas.getContext('2d'),width=300,height=300,dpr=1,animation=0,frames=0;
var activePointers=new Map(),pinchDist=0,clickStart=null;
function isNative(){return !!window.JarvisNative&&typeof window.JarvisNative.getBrain==='function'}
var previewCache=null;
function demoRead(){if(previewCache)return previewCache;try{return JSON.parse(localStorage.getItem('jarvis_brain_preview')||'null')}catch(e){return null}}
function load(){
 if(isNative())try{var nativeData=window.JarvisNative.getBrain();return JSON.parse(nativeData)}catch(e){status('Memóriahiba: '+e.message)}
 return demoRead()||{count:0,capacity:300,groups:categories.map(function(n){return {name:n,count:0}}),notes:[]};
}
function status(text){$('brainStatus').textContent=text}
function refresh(){
 data=load();
 if(!Array.isArray(data.notes))data.notes=[];
 data.count=data.notes.length;
 $('brainCount').textContent=data.count+' / '+(data.capacity||300)+' jóváhagyott tétel · 6 tudásterület';
 drawDetails();
 draw();
}
function demoSave(){previewCache=JSON.parse(JSON.stringify(data));try{localStorage.setItem('jarvis_brain_preview',JSON.stringify(data))}catch(e){status('Böngészős előnézet: az adatok csak ideiglenesen tárolhatók.')}}
function noteId(n){return String(n.id||'')}
function selectItem(type,id){selected={type:type,id:id||''};drawDetails();draw()}
function drawDetails(){
 var label=$('brainSelected'),detail=$('brainDetail'),list=$('brainList'),del=$('brainDelete');
 del.style.display=selected.type==='note'?'block':'none';
 list.replaceChildren();
 var filtered=data.notes;
 if(selected.type==='category'){filtered=data.notes.filter(function(n){return n.category===selected.id});label.textContent=selected.id;detail.textContent=filtered.length+' mentett tétel ebben a csoportban.'}
 else if(selected.type==='note'){
  var n=data.notes.find(function(i){return noteId(i)===selected.id});
  if(n){label.textContent=n.title;detail.textContent=(n.category||'Tudás')+' · '+(n.source||'felhasználó')+'\n'+n.detail;filtered=[n]}
  else{label.textContent='JARVIS CORE';detail.textContent='Tétel nem található.';filtered=[]}
 }else{label.textContent='JARVIS CORE';detail.textContent='A hálózaton a helyben tárolt tudásjegyzeteket és kategóriákat látod. A megjelenített pontok nem a nyelvi modell belső neuronjai.'}
 var limit=filtered.length>80?80:filtered.length;
 for(var i=filtered.length-1;i>=filtered.length-limit;i--){
  var n=filtered[i],b=document.createElement('button');
  b.className='note'+(selected.type==='note'&&selected.id===noteId(n)?' on':'');
  b.type='button';b.textContent='◈ '+n.title;b.onclick=(function(v){return function(){selectItem('note',noteId(v))}})(n);
  list.appendChild(b);
 }
 if(filtered.length>80){var more=document.createElement('p');more.textContent='Az utolsó 80 tétel látható. Összesen: '+filtered.length;list.appendChild(more)}
}
function hash(str){var h=2166136261;for(var i=0;i<str.length;i++){h^=str.charCodeAt(i);h=Math.imul(h,16777619)}return h>>>0}
function buildNodes(){
 var out=[{type:'core',id:'',x:0,y:0,r:31,color:'#8feeff',title:'JARVIS'}];
 categories.forEach(function(name,i){
  var a=-Math.PI/2+i*2*Math.PI/6;
  out.push({type:'category',id:name,x:Math.cos(a)*160,y:Math.sin(a)*151,r:20,color:colors[i],title:name});
 });
 var groups={};categories.forEach(function(c){groups[c]=[]});
 data.notes.forEach(function(n){if(groups[n.category])groups[n.category].push(n)});
 categories.forEach(function(name,i){
  var a=-Math.PI/2+i*2*Math.PI/6,arr=groups[name]||[],max=Math.min(14,arr.length),subset=arr.slice(-max);
  subset.forEach(function(n,j){
   var seed=hash(noteId(n)+n.title),offset=(j-(max-1)/2)*.37+(seed%13)/100;
   var radius=74+Math.floor(j/5)*22;
   var angle=a+offset;
   out.push({type:'note',id:noteId(n),x:Math.cos(a)*160+Math.cos(angle)*radius,y:Math.sin(a)*151+Math.sin(angle)*radius,r:7,color:colors[i],title:n.title,parent:name});
  });
 });
 return out;
}
function resize(){
 var bounds=canvas.getBoundingClientRect();
 width=Math.max(100,bounds.width);height=Math.max(100,bounds.height);
 dpr=Math.min(2,window.devicePixelRatio||1);
 canvas.width=Math.floor(width*dpr);canvas.height=Math.floor(height*dpr);
 ctx.setTransform(dpr,0,0,dpr,0,0);
 draw();
}
function screenX(x){return width/2+panX+x*scale}
function screenY(y){return height/2+panY+y*scale}
function line(ax,ay,bx,by,color,alpha){
 ctx.strokeStyle=color;ctx.globalAlpha=alpha;ctx.lineWidth=.85;
 ctx.beginPath();ctx.moveTo(screenX(ax),screenY(ay));ctx.lineTo(screenX(bx),screenY(by));ctx.stroke();ctx.globalAlpha=1;
}
function draw(){
 if(!visible)return;
 ctx.setTransform(dpr,0,0,dpr,0,0);ctx.clearRect(0,0,width,height);
 var time=performance.now()/1000;
 ctx.save();
 for(var ring=1;ring<=5;ring++){
  ctx.beginPath();ctx.arc(screenX(0),screenY(0),ring*68*scale,0,Math.PI*2);
  ctx.strokeStyle='rgba(18,152,190,'+(0.2-ring*.026)+')';ctx.lineWidth=ring%2?1.3:0.8;ctx.stroke();
 }
 if(!nodes.length)nodes=buildNodes();
 nodes.forEach(function(n){
  if(n.type==='category')line(0,0,n.x,n.y,n.color,.48);
  else if(n.type==='note'){
   var parent=nodes.find(function(p){return p.type==='category'&&p.id===n.parent});
   if(parent)line(parent.x,parent.y,n.x,n.y,n.color,.32);
  }
 });
 nodes.forEach(function(n,i){
  var x=screenX(n.x),y=screenY(n.y),r=Math.max(3,n.r*scale),chosen=selected.type===n.type&&selected.id===n.id;
  var glow=ctx.createRadialGradient(x,y,1,x,y,r*2.4);
  glow.addColorStop(0,n.color+'88');glow.addColorStop(1,'#03162700');
  ctx.fillStyle=glow;ctx.beginPath();ctx.arc(x,y,r*2.4,0,Math.PI*2);ctx.fill();
  ctx.strokeStyle=n.color;ctx.lineWidth=chosen?3:1.4;
  ctx.beginPath();ctx.arc(x,y,r+(n.type==='core'?3*Math.sin(time*2):0),0,Math.PI*2);ctx.stroke();
  ctx.fillStyle=chosen?'#e9ffff':n.type==='note'?'#3fc9ee':n.type==='core'?'#ccffff':'#092a3e';
  ctx.beginPath();ctx.arc(x,y,r*.65,0,Math.PI*2);ctx.fill();
  if(n.type==='core'){ctx.strokeStyle='#b1faff';ctx.beginPath();ctx.moveTo(x,y-r*.65);ctx.lineTo(x-r*.60,y+r*.48);ctx.lineTo(x+r*.60,y+r*.48);ctx.closePath();ctx.stroke()}
  if(n.type!=='note'||scale>1.8){
   ctx.fillStyle=n.color;ctx.font=(n.type==='core'?'bold 12px':'10px')+' monospace';ctx.textAlign='center';
   var title=n.title;if(title.length>19)title=title.substring(0,17)+'…';
   ctx.fillText(title,x,y+r+14);
  }
 });
 ctx.restore();
}
function loop(){
 if(!visible)return;
 if(frames++%3===0)draw();
 animation=requestAnimationFrame(loop);
}
function open(){visible=true;host.classList.add('on');document.body.style.overflow='hidden';refresh();resize();cancelAnimationFrame(animation);loop()}
function close(){visible=false;host.classList.remove('on');cancelAnimationFrame(animation);document.body.style.overflow=''}
window.jarvisBrain={open:open,close:close,refresh:refresh};
function zoom(mult,cx,cy){
 var before=scale,after=Math.min(3.4,Math.max(.38,scale*mult));
 panX=(panX-(cx-width/2))*(after/before)+(cx-width/2);
 panY=(panY-(cy-height/2))*(after/before)+(cy-height/2);
 scale=after;draw();
}
function point(e){var r=canvas.getBoundingClientRect();return {x:e.clientX-r.left,y:e.clientY-r.top}}
function distance(pts){if(pts.length<2)return 0;return Math.hypot(pts[0].x-pts[1].x,pts[0].y-pts[1].y)}
canvas.addEventListener('pointerdown',function(e){
 e.preventDefault();canvas.setPointerCapture(e.pointerId);
 var p=point(e);activePointers.set(e.pointerId,p);
 if(activePointers.size===1)clickStart={x:p.x,y:p.y,t:Date.now(),moved:false};
 if(activePointers.size>=2){pinchDist=distance(Array.from(activePointers.values()));clickStart=null}
});
canvas.addEventListener('pointermove',function(e){
 if(!activePointers.has(e.pointerId))return;
 e.preventDefault();
 var previous=activePointers.get(e.pointerId),current=point(e);
 activePointers.set(e.pointerId,current);
 if(activePointers.size===1){
  panX+=current.x-previous.x;panY+=current.y-previous.y;
  if(clickStart&&Math.hypot(current.x-clickStart.x,current.y-clickStart.y)>9)clickStart.moved=true;
 }else{
  var pts=Array.from(activePointers.values());
  var next=distance(pts);
  if(pinchDist>0&&next>0){
   zoom(next/pinchDist,(pts[0].x+pts[1].x)/2,(pts[0].y+pts[1].y)/2);
  }
  pinchDist=next;
 }
 draw();
});
function finish(e){
 var last=activePointers.get(e.pointerId);
 if(!last)return;
 activePointers.delete(e.pointerId);
 if(activePointers.size<2)pinchDist=0;
 if(activePointers.size===0&&clickStart&&!clickStart.moved&&Date.now()-clickStart.t<650){
  var p=point(e),best=null,dist=Infinity;
  nodes.forEach(function(n){var d=Math.hypot(p.x-screenX(n.x),p.y-screenY(n.y));
   if(d<Math.max(18,n.r*scale+12)&&d<dist){best=n;dist=d}
  });
  if(best)selectItem(best.type,best.id);else selectItem('core','');
 }
 clickStart=null;draw();
}
canvas.addEventListener('pointerup',finish);
canvas.addEventListener('pointercancel',function(e){activePointers.delete(e.pointerId);clickStart=null;pinchDist=0});
canvas.addEventListener('wheel',function(e){e.preventDefault();var p=point(e);zoom(e.deltaY<0?1.13:.87,p.x,p.y)},{passive:false});
$('brainPlus').onclick=function(){zoom(1.25,width/2,height/2)};
$('brainMinus').onclick=function(){zoom(.8,width/2,height/2)};
$('brainFit').onclick=function(){scale=1;panX=0;panY=0;selectItem('core','')};
$('brainClose').onclick=close;
$('brainSave').onclick=function(){
 var title=$('brainTitle').value.trim(),detail=$('brainText').value.trim(),category=$('brainCategory').value;
 if(title.length<2||detail.length<3){status('Írj címet és legalább 3 karakteres tartalmat.');return}
 if(isNative()){
  try{window.JarvisNative.learnBrain(category,title,detail);status('Mentés elküldve az Android-memóriának...')}
  catch(e){status('Mentési hiba: '+e.message);return}
 }else{
  if(data.notes.length>=300){status('A 300 tételes memória megtelt.');return}
  data.notes.push({id:String(Date.now()),category:category,title:title,detail:detail,source:'HTML előnézet'});
  demoSave();refresh();status('Elmentve a böngészős előnézetben. Az Android-adatok ettől különállók.');
 }
 $('brainTitle').value='';$('brainText').value='';
};
$('brainDelete').onclick=function(){
 if(selected.type!=='note')return;
 if(!confirm('Biztosan törlöd ezt a mentett tudástételt?'))return;
 if(isNative())window.JarvisNative.deleteBrain(selected.id);
 else{data.notes=data.notes.filter(function(n){return noteId(n)!==selected.id});demoSave();refresh()}
 selectItem('core','');
};
$('brainCopy').onclick=function(){
 if(isNative())window.JarvisNative.copyBrain();
 else{var value=JSON.stringify(data,null,2);status('Előnézet: a böngészőben a mentett tételek: '+value.length+' karakter.')}
};
var brainButton=document.createElement('button');brainButton.id='jbrain';brainButton.type='button';
brainButton.textContent='◈ AGY / HÁLÓ';
var grid=document.querySelector('#jarvisDock .grid');if(grid)grid.appendChild(brainButton);
brainButton.onclick=open;
// Keep the original holographic renderer and the original audio controls intact.
var priorReceive=window.jarvisReceive;
window.jarvisReceive=function(kind,message){
 if(typeof priorReceive==='function')priorReceive(kind,message);
 if(kind==='brain'){
  try{data=JSON.parse(message);if(visible){refresh()}}catch(e){status('Nem olvasható a tudásadat.')}
 }
 if(kind==='mic'){
  var on=message==='on';
  var voice=document.getElementById('voice');
  if(voice)voice.classList.toggle('active',on);
  var mic=document.getElementById('jmic');
  if(mic){mic.textContent=on?'■ MIKROFON KI':'◉ MIKROFON';mic.style.borderColor=on?'#fa6678':'#327d96'}
 }
};
window.addEventListener('resize',function(){if(visible)resize()});
})();