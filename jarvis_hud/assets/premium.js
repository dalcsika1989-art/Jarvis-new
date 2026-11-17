
/* JARVIS C v2 - UI integration, real Android app launcher, rule-based knowledge */
(function(){
"use strict";
function by(id){return document.getElementById(id);}
function q(s){return document.querySelector(s);}
function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
function esc(v){return String(v).replace(/[&<>"]/g,function(c){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;"}[c];});}
var nativeOK=!!window.JarvisNative;
var subtitle=q(".subtitle");if(subtitle)subtitle.textContent="ANDROID ASSZISZTENS · HUD v2.0";
var badge=q(".chip");if(badge)badge.textContent="JARVIS C · STRATÉGIAI HUD 2.0";
var stats=q(".stats");if(stats)stats.innerHTML='<span id="time">--:--</span><br>AKKU <b id="battery">--%</b><br><b>● HUD AKTÍV</b>';
var center=q(".center"),left=q(".side.left"),right=q(".side.right");
if(left)left.innerHTML='<span class="label">AKKU · ENERGIA</span><span class="metric"><span id="sideBat">--</span>%</span><div class="meter"><i></i><i></i><i></i><i></i><i class="off"></i></div><span class="small">ARC CORE</span>';
if(right)right.innerHTML='<span class="label">RENDSZER</span><strong class="mgreen">● AKTÍV</strong><div class="line">JARVIS C</div><span class="small">v2.0 HUD</span>';
var ram=el("aside","side left secondary");ram.innerHTML='<span class="label">RAM · MEMÓRIA</span><strong id="memState">--</strong><div class="meter"><i></i><i></i><i class="off"></i><i class="off"></i></div><span class="small" id="memInfo">Rendszeradat</span>';
var sys=el("aside","side right secondary");sys.innerHTML='<span class="label">MODULOK</span><strong>ONLINE</strong><div class="line">TUDÁS · CHAT</div><div class="symbol">⌁ ⌁ ⌁</div>';
if(center){center.appendChild(ram);center.appendChild(sys);}
var navs=q(".bottom");if(navs){
var wave=el("div","waveform");for(var n=0;n<65;n++){var w=el("i","w");var h=2+19*Math.abs(Math.sin(n*.29)*Math.cos(n*.13));w.style.setProperty("--h",h.toFixed(0)+"px");w.style.setProperty("--d",(-n*.038)+"s");wave.appendChild(w);}
navs.parentElement.insertBefore(wave,navs);
var alerts=el("div","home-alerts");alerts.innerHTML='<div class="alerts"><button type="button"><span>◌</span> FOLYAMATBAN</button><button type="button"><span>⌛</span> JÓVÁHAGYÁSRA VÁR</button><button type="button"><span>✓</span> KÉSZ</button><button type="button"><span>⚠</span> HIBA</button></div>';
navs.parentElement.insertBefore(alerts,navs);
var captions=[
["▥","DASHBOARD"],["▣","FÁJLOK"],["▤","CHAT"],["▦","GYORSMENÜ"],
["⌘","TUDÁSHÁLÓ"],["▷","MINI CHAT"],["◈","ALKALMAZÁSOK"],["⚙","BEÁLLÍTÁS"]
];
Array.prototype.forEach.call(navs.querySelectorAll("button.tile"),function(btn,i){
 if(!captions[i])return;btn.querySelector(".ic").textContent=captions[i][0];btn.querySelector(".tx").textContent=captions[i][1];
});
var appTile=navs.querySelector('[data-go="overlay"]');
if(appTile){appTile.removeAttribute("data-go");appTile.addEventListener("click",function(){showLauncher();});}
var floatBtn=el("button","overlay-action","◉ LEBEGŐ IKON · BE / KI");
floatBtn.id="overlayAction";floatBtn.style.cssText="display:block;margin:-7px auto 8px;padding:5px 25px;color:#ecffff;background:linear-gradient(#15516f,#051d30);border:1px solid #45bfff;border-radius:18px;font-size:11px;letter-spacing:1.5px;box-shadow:0 0 9px #0abaff99,inset 0 1px #d6ffff";
navs.parentElement.insertBefore(floatBtn,navs.nextSibling);
floatBtn.addEventListener("click",function(){
 if(!nativeOK){alert("A telefon fölötti ikon csak a telepített Android APK-ban működik.");return;}
 window.JarvisNative.overlay();
});
}
if(q(".reactor-label"))q(".reactor-label").textContent="◀ ÉRINTÉS · GYORSMENÜ ▶";
if(q(".reactor"))q(".reactor").setAttribute("aria-label","JARVIS reaktor, gyorsmenü");
var msgs=by("messages");if(msgs){
 var vis=el("div","chat-visual");vis.innerHTML='<div class="chat-reactor"></div><div class="chat-pedestal"></div><span class="chat-caption">JARVIS CORE · HANG / CHAT MODUL</span>';msgs.insertBefore(vis,msgs.firstChild);
}
var launcher=el("section","launcher");launcher.id="launcher";
launcher.innerHTML='<div class="launchbar">◉ ALKALMAZÁS INDÍTÓ <button id="closeLauncher">✕ BEZÁR</button></div><div class="launchhint" id="launchHint">Telepített Android alkalmazások · érintéssel megnyithatók</div><div class="launchlist" id="launchList"></div>';
document.body.appendChild(launcher);by("closeLauncher").addEventListener("click",function(){launcher.classList.remove("show");});
function showLauncher(){
 var items=by("launchList");items.innerHTML="";
 by("launchHint").textContent=nativeOK?"Telepített alkalmazások · érintéssel megnyithatók":"Alkalmazáslista csak a telepített Android APK-ban működik.";
 if(nativeOK){
  try{
   var apps=JSON.parse(window.JarvisNative.listApps());
   if(!Array.isArray(apps))apps=[];
   apps.sort(function(a,b){return a.name.localeCompare(b.name);});
   apps.forEach(function(app){
    var b=el("button","appitem");b.type="button";
    if(app.icon&&app.icon.startsWith("data:image/")){var img=el("img");img.src=app.icon;img.alt="";b.appendChild(img);}
    else b.appendChild(el("span","fallback","◈"));
    b.appendChild(el("span","",app.name));b.addEventListener("click",function(){window.JarvisNative.launchApp(app.pkg);});
    items.appendChild(b);
   });
   if(!apps.length)items.textContent="Nem található indítható alkalmazás.";
  }catch(err){items.textContent="Hiba az alkalmazáslista megnyitásakor: "+err.message;}
 }else{
  ["Telefon","Üzenetek","Chrome","Térkép","Galéria","Kamera","Beállítások"].forEach(function(name){
   var b=el("button","appitem");b.innerHTML='<span class="fallback">◈</span>';b.appendChild(el("span","",name));
   b.addEventListener("click",function(){alert("Ez a funkció az Android APK-ban érhető el.");});items.appendChild(b);
  });
 }
 launcher.classList.add("show");
}
function smallNativeStats(){
 var t=by("time"),b=by("battery"),sb=by("sideBat");
 var d=new Date();
 if(t)t.textContent=d.toLocaleTimeString("hu-HU",{hour:"2-digit",minute:"2-digit"});
 var battery="—";
 if(nativeOK)try{battery=String(window.JarvisNative.battery());}catch(e){}
 if(b)b.textContent=battery+"%";if(sb)sb.textContent=battery;
 if(nativeOK)try{
  var r=JSON.parse(window.JarvisNative.memoryStats()),percent=Math.min(100,Math.round(100*(1-r.available/r.total)));
  by("memState").textContent=percent+"%";
  by("memInfo").textContent=Math.round(r.total/1048576)+" MB össz.";
 }catch(e){by("memState").textContent="MEMÓRIA";}
 else{by("memState").textContent="HUD 2.0";by("memInfo").textContent="Android statisztika";}
}
smallNativeStats();setInterval(smallNativeStats,25000);
var notices=["Folyamatban: nincs aktív telefonfeladat","Jóváhagyás: nincs függőben", "Kész: HUD betöltve", "Hiba: nincs észlelt hiba"];
var colors=["","#ffcc44","#31ff81","#ff365d"];
var toasts=by("toasts");
Array.prototype.forEach.call(q(".alerts").children,function(b,i){b.addEventListener("click",function(){
 var t=el("div","toast "+(i===1?"warn":i===2?"good":i===3?"bad":""));t.textContent=notices[i];toasts.appendChild(t);setTimeout(function(){t.remove();},4200);
});});
var origShowApp=showLauncher;
window.jarvisOpenPage=function(name){
 if(name==="apps"){origShowApp();return;}
 if(name==="overlay"){if(nativeOK)window.JarvisNative.overlay();return;}
 if(name==="mini"){var m=by("mini");m.classList.add("show");return;}
 if(name==="home"){var close=q(".screen.show [data-close]");if(close)close.click();launcher.classList.remove("show");by("mini").classList.remove("show");return;}
 var tile=q('[data-go="'+(name==="tasks"?"status":name)+'"]');
 if(tile){tile.click();return;}
 if(name==="settings"){q('[data-go="settings"]').click();}
};
var t=q(".chat-visual");if(t){
 var note=el("div","chat-caption","AI-beszélgetés még nincs bekötve. A tudáshálóban a mentett elemek helyben tárolódnak.");
 t.after(note);
}
})();
