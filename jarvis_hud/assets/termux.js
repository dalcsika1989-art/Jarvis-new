/* JARVIS C 2.1 - paired Termux background engine control panel. */
(function(){
"use strict";
var api=window.JarvisTermux;
var container=document.querySelector(".app");
if(!container)return;
var paired=Boolean(api && api.hasToken && api.hasToken());
function $(id){return document.getElementById(id);}
function create(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
function say(text,kind){
 var holder=$("toasts");if(!holder)return;
 var element=create("div","toast "+(kind||""),text);
 holder.appendChild(element);setTimeout(function(){element.remove();},4500);
}
var css=String.raw`
#termux-status{position:relative;margin:4px 12px 5px;width:calc(100% - 24px);height:29px;background:linear-gradient(105deg,#0a2e44,#041221);border:1px solid #239ac6;color:#d6f5ff;box-shadow:inset 0 0 10px #15bfff33,0 0 6px #02bfff40;font-size:10px;letter-spacing:.7px;z-index:3;border-radius:3px;text-align:center;font-weight:bold}
#termux-status.online{border-color:#3dff8e;color:#b2ffd0;box-shadow:0 0 9px #1aff7055}
#termux-panel{position:fixed;inset:0;background:radial-gradient(circle at 50% 23%,#103953,#030c18 72%);z-index:38;display:none;flex-direction:column;padding:9px 10px 18px;color:#d4f7ff}
#termux-panel.open{display:flex}
#termux-panel .top{display:flex;align-items:center;justify-content:space-between;border:1px solid #2686b5;background:linear-gradient(#114361,#051522);box-shadow:inset 0 1px #bafaff65,0 0 12px #037ad565;padding:12px 10px;flex-shrink:0}
#termux-panel h2{font-size:15px;letter-spacing:1px;text-shadow:0 0 7px #2bd4ff;margin:0}
#termux-panel button{border:1px solid #23a6d9;color:#effdff;background:linear-gradient(#115477,#032034);border-radius:5px;padding:9px 11px;box-shadow:inset 0 1px #aafaff66}
#termux-panel button:active{filter:brightness(1.5)}
#termux-panel button:disabled{opacity:.4}
#termux-panel .content{overflow-y:auto;flex:1;padding:6px 2px 20px}
#termux-panel section{background:linear-gradient(140deg,#092942df,#020d1bbd);border:1px solid #24749b;box-shadow:inset 0 1px 0 #9ce9ff45,inset 0 0 18px #02bfff17;margin:9px 0;padding:11px;border-radius:6px}
#termux-panel h3{font-size:12px;margin:0 0 9px;letter-spacing:.8px;color:#7bdfff}
#termux-panel p{font-size:11px;color:#acd8e9;line-height:1.45;margin:5px 0 10px}
#termux-panel input{border-radius:4px;min-width:0;background:#04172a;color:white;border:1px solid #2085ab;padding:10px;flex:1;outline:none}
#termux-panel .row{display:flex;gap:7px;align-items:center;margin:7px 0;flex-wrap:wrap}
#termux-panel .row input{flex:1 1 160px}
#termux-panel .state{border-left:4px solid #20b6f0;padding:9px 10px;font-size:12px;background:#0c2437;color:#ceeaf9}
#termux-panel .state.good{border-color:#23fb8b;color:#b9ffd9}
#termux-panel .state.bad{border-color:#ff2c56;color:#ffbacc}
#termux-panel .state.wait{border-color:#ffca29;color:#ffe9b7}
#termux-panel .cmds{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px}
#termux-panel .cmds button{font-size:10px;padding:12px 3px}
#termux-panel .scroll{max-height:170px;overflow:auto;font-size:11px;line-height:1.6}
#termux-panel .task{border-bottom:1px solid #175276;padding:6px 0;display:flex;justify-content:space-between;gap:6px}
#termux-panel .task span:first-child{flex:1}
#termux-panel .task small{color:#91d2e5}
#termux-panel .help{font-size:11px;background:#071a2d;border-left:2px solid #138abb;padding:6px;line-height:1.6;word-break:break-word}
#termux-panel .task .ok{color:#40ff8a}
#termux-panel .task .err{color:#ff5b7d}
#termux-panel .task .pending{color:#fdd452}
`;
var style=document.createElement("style");style.textContent=css;document.head.appendChild(style);
var banner=create("button");banner.id="termux-status";banner.textContent="◈ TERMUX MOTOR · "+(paired?"KAPCSOLÓDÁS":"PÁROSÍTÁS");
var wave=document.querySelector(".waveform");container.insertBefore(banner,wave||container.querySelector(".bottom"));
var panel=create("div");panel.id="termux-panel";panel.innerHTML=[
 '<div class="top"><h2>◈ JARVIS / TERMUX ENGINE</h2><button id="termux-close">✕ BEZÁR</button></div>',
 '<div class="content">',
 '<section><h3>HELYI SZERVER · 127.0.0.1:8765</h3><div id="termux-state" class="state">Kapcsolat ellenőrzése...</div>',
 '<p>Az egyszeri kulcsot a Termuxban kapod meg: <b>python ~/.jarvis/jarvisctl.py token</b>. A kulcsot ne küldd el chatben.</p>',
 '<div class="row"><input type="password" id="termux-token" placeholder="Termux párosítókulcs..." autocomplete="off" autocapitalize="off" spellcheck="false">',
 '<button id="termux-pair">PÁROSÍT</button><button id="termux-disconnect">TÖRLÉS</button></div>',
 '<div class="row"><button id="termux-refresh">FRISSÍTÉS</button><button id="termux-enable">ANDROID VEZÉRLÉS ENGEDÉLYEZÉSE</button></div></section>',
 '<section><h3>FELADATOK · ÁLLAPOT</h3><div id="termux-counters" class="help">Még nincs beolvasva.</div>',
 '<div class="scroll" id="termux-tasks"></div></section>',
 '<section><h3>ANDROID PARANCSOK · VALÓDI FELADATSOR</h3>',
 '<p>Android-művelethez engedélyezd a JARVIS C Accessibility szolgáltatását. A parancsok a Termux szerveren várnak, amíg a végrehajtó felveszi őket.</p>',
 '<div class="cmds"><button data-ac="home">⌂ FŐOLDAL</button><button data-ac="back">↶ VISSZA</button><button data-ac="recents">▤ LEGUTÓBBIAK</button><button data-ac="scroll_down">↓ GÖRGETÉS</button><button data-ac="scroll_up">↑ FELJEBB</button><button data-ac="notifications">◉ ÉRTESÍTÉSEK</button></div>',
 '<div class="row"><input id="termux-text" placeholder="Kijelölt szöveg vagy beírandó szöveg..." maxlength="200">',
 '<button id="termux-tap">KATTINTÁS</button><button id="termux-type">SZÖVEG BEÍRÁSA</button></div>',
 '</section>',
 '<section><h3>MEMÓRIA · TUDÁSHÁLÓ</h3>',
 '<div class="row"><input id="termux-rem" maxlength="1000" placeholder="Mit jegyezzen meg JARVIS?"><button id="termux-save">MENTÉS</button></div>',
 '<p id="termux-knowledge">Mentett elemek: ellenőrzés alatt...</p></section>',
 '</div>'
].join("");
document.body.appendChild(panel);
function state(message,kind){
 var e=$("termux-state");e.textContent=message;e.className="state "+(kind||"");
 banner.classList.toggle("online",kind==="good");
 banner.textContent=kind==="good"?"◈ TERMUX MOTOR · ONLINE":kind==="bad"?"◈ TERMUX MOTOR · NEM ELÉRHETŐ":"◈ TERMUX MOTOR · "+(paired?"ELLENŐRZÉS":"PÁROSÍTÁS");
}
function open(){
 panel.classList.add("open");
 if(!api){state("Ez csak a telepített JARVIS C APK-ban működik.","bad");return;}
 if(!api.hasToken()){state("Nincs párosítva. Másold ide a Termux kulcsát.","wait");return;}
 refresh();
}
function close(){panel.classList.remove("open");}
function refresh(){
 if(!api || !api.hasToken())return;
 api.fetch("health");api.fetch("tasks");api.fetch("knowledge");
}
banner.addEventListener("click",open);
$("termux-close").addEventListener("click",close);
$("termux-refresh").addEventListener("click",refresh);
$("termux-pair").addEventListener("click",function(){
 if(!api){state("A natív Android modul hiányzik.","bad");return;}
 var token=$("termux-token").value.trim();state("Párosítás folyamatban...","wait");api.pair(token);$("termux-token").value="";
});
$("termux-disconnect").addEventListener("click",function(){
 if(api){api.disconnect();paired=false;state("Kulcs törölve, kapcsolat megszüntetve.","wait");}
});
$("termux-enable").addEventListener("click",function(){if(api)api.accessibilitySettings();else say("Ez a funkció csak APK-ban érhető el","warn");});
function sendAndroid(command,args){
 if(!api || !api.hasToken()){state("Először párosítsd a szervert.","wait");return;}
 api.androidAction(command,JSON.stringify(args||{}));
}
panel.querySelectorAll("[data-ac]").forEach(function(b){b.addEventListener("click",function(){sendAndroid(b.getAttribute("data-ac"),{});});});
$("termux-tap").addEventListener("click",function(){
 var value=$("termux-text").value.trim();if(!value){say("Add meg a kijelölt gomb szövegét","warn");return;}
 if(confirm("Kattintás az aktív Android-alkalmazás „"+value+"” feliratú elemén?"))sendAndroid("tap_text",{text:value});
});
$("termux-type").addEventListener("click",function(){
 var value=$("termux-text").value.trim();if(!value){say("Írd be a szöveget","warn");return;}
 if(confirm("A szöveg beírása az aktív alkalmazás fókuszban lévő mezőjébe?"))sendAndroid("type_text",{text:value});
});
$("termux-save").addEventListener("click",function(){
 var value=$("termux-rem").value.trim();if(!value)return;
 if(!api || !api.hasToken()){say("Előbb párosítsd a szervert","warn");return;}
 api.remember(value);$("termux-rem").value="";
});
function renderTasks(value){
 var items=Array.isArray(value.tasks)?value.tasks:[];
 var el=$("termux-tasks");el.textContent="";
 items.slice(0,18).forEach(function(task){
  var row=create("div","task"),label=create("span","",("#"+task.id)+" "+task.type),stat=create("small","",task.status);
  stat.className=task.status==="done"?"ok":task.status==="failed"?"err":"pending";
  row.appendChild(label);row.appendChild(stat);
  if(task.status==="queued" || task.status==="running"){
   var cancel=create("button","", "✕");cancel.style.padding="2px 7px";cancel.addEventListener("click",function(){api.cancel(task.id);});
   row.appendChild(cancel);
  }
  el.appendChild(row);
 });
 if(!items.length)el.textContent="Nincs feladat a sorban.";
}
function renderKnowledge(value){
 var items=Array.isArray(value.knowledge)?value.knowledge:[];
 $("termux-knowledge").textContent=items.length+" szerveroldali tudáselem. A tudáshálóval szinkronizálva.";
 if(window.jarvisKnowledgeImport){
  window.jarvisKnowledgeImport(items.map(function(x){return x.text;}));
 }
}
function receive(data){
 var event;try{event=JSON.parse(data);}catch(e){return;}
 var name=event.action,d=event.data||{};
 if(name==="pair"){
  if(event.ok){paired=true;state("PÁROSÍTVA · a Termux háttérmotor elérhető.","good");say("JARVIS ↔ TERMUX párosítás sikeres","good");refresh();}
  else{paired=false;state(d.error||"Párosítás sikertelen","bad");}
 }else if(name==="health"){
  if(event.ok){paired=true;var q=d.queue||{};state("ONLINE · sorban "+(q.queued||0)+" / fut "+(q.running||0)+" / kész "+(q.done||0),"good");
   $("termux-counters").textContent="Sorban: "+(q.queued||0)+" | Fut: "+(q.running||0)+" | Kész: "+(q.done||0)+" | Hiba: "+(q.failed||0)+" | Tudás: "+(d.knowledge||0);
  }else state(d.error||"Nem elérhető a szerver.","bad");
 }else if(name==="tasks" && event.ok)renderTasks(d);
 else if(name==="knowledge" && event.ok)renderKnowledge(d);
 else if(name==="remember" || name==="android"){
  if(event.ok){say((name==="remember"?"Tanulás":"Android feladat")+" #"+d.id+" sorba állítva","good");setTimeout(refresh,1600);}
  else say(d.error||"A feladat nem sikerült","bad");
 }else if(name==="cancel"){
  if(event.ok){say("Feladat megszakítási kérelem","warn");setTimeout(refresh,500);}
 }else if(name==="disconnect"){state("Termux kapcsolat bontva.","wait");}
}
window.JarvisTermuxUI={open:open,receive:receive,refresh:refresh};
var prior=window.jarvisOpenPage;
window.jarvisOpenPage=function(name){if(name==="termux"){open();return;}if(typeof prior==="function")prior(name);};
document.querySelectorAll('[data-go="knowledge"]').forEach(function(b){b.addEventListener("click",function(){if(api && api.hasToken())api.fetch("knowledge");});});
var learnForm=$("learnForm"),chatForm=$("chatForm"),miniForm=$("miniForm");
if(learnForm)learnForm.addEventListener("submit",function(){
 var val=$("learnText").value.trim();if(val && api && api.hasToken())api.remember(val);
},true);
function learnFromChat(e,inputId){
 var val=$(inputId).value.trim();
 var match=val.match(/^(?:tanuld meg|jegyezd meg|új tudás|emlékezz)\s*[:,-]?\s*(.+)$/i);
 if(match && api && api.hasToken())api.remember(match[1]);
}
if(chatForm)chatForm.addEventListener("submit",function(e){learnFromChat(e,"chatText");},true);
if(miniForm)miniForm.addEventListener("submit",function(e){learnFromChat(e,"miniText");},true);
setInterval(function(){if(panel.classList.contains("open")&&api&&api.hasToken())refresh();},7000);
})();
