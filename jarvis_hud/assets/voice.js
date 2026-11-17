
/* JARVIS C 2.4 - user initiated speech + background task view */
(function(){
"use strict";
var voice=window.JarvisVoiceNative;
var termux=window.JarvisTermux;
var termuxUI=window.JarvisTermuxUI;
function by(id){return document.getElementById(id);}
function create(tag,cls,label){var e=document.createElement(tag);if(cls)e.className=cls;if(label)e.textContent=label;return e;}
function toast(message,kind){var box=by("toasts");if(!box)return;var el=create("div","toast "+(kind||""),message);box.appendChild(el);setTimeout(function(){el.remove();},4800);}
function strip(t){return String(t||"").toLocaleLowerCase("hu-HU").normalize("NFD").replace(/[\u0300-\u036f]/g,"").trim();}
function feedback(text,speak){var n=by("jarvis-voice-note");if(n)n.textContent=text;var e=by("jarvis-voice-last");if(e)e.textContent=text;toast(text);if(speak&&voice&&voice.say)voice.say(text);}
var app=document.querySelector(".app"),navigation=document.querySelector(".bottom");
if(!app || !navigation)return;
var trigger=create("button","jarvis-voice-trigger","🎙 MAGYAR HANGVEZÉRLÉS");
var note=create("div","jarvis-voice-status","jarvis-voice-note");note.textContent="Beszéd csak gombnyomásra · Termux háttérmotor";
navigation.parentElement.insertBefore(trigger,navigation);
navigation.parentElement.insertBefore(note,navigation);
note.id="jarvis-voice-note";
var dialog=create("section","jarvis-voice-layer");dialog.id="jarvis-voice-layer";
dialog.innerHTML=
'<div class="heading"><h2>◉ JARVIS · MAGYAR HANG</h2><button id="jarvis-voice-close">✕</button></div>'+
'<div class="card"><h3>MAGYAR PARANCSFELISMERÉS</h3><p>Indítás csak a mikrofon megérintésével. A felismeréshez az Android beszédszolgáltatása szükséges.</p><button class="big" id="jarvis-voice-listen">🎙 BESZÉLJ JARVIS-HOZ</button><div id="jarvis-voice-last" class="recognized">Mikrofon készen áll.</div></div>'+
'<div class="card"><h3>MAGYAR VÁLASZHANG</h3><p>Android rendszerhang, opcionális. A magyar nyelvi adat külön telepítést igényelhet.</p><label><input type="checkbox" id="jarvis-voice-feedback"> Válaszok felolvasása</label> <button id="jarvis-voice-test">HANGPRÓBA</button></div>'+
'<div class="card"><h3>ELÉRHETŐ PARANCSOK</h3><div class="samples">„JARVIS, főoldal”<br>„JARVIS, vissza”<br>„JARVIS, mutasd a feladatokat”<br>„JARVIS, nyisd meg a Chrome-ot”<br>„JARVIS, jegyezd meg: jó ötlet”<br>„JARVIS, görgess le” (az aktív képernyőn)</div></div>';
document.body.appendChild(dialog);
function openVoice(){dialog.classList.add("open");if(voice&&voice.feedback)by("jarvis-voice-feedback").checked=!!voice.feedback();}
function closeVoice(){dialog.classList.remove("open");}
function listen(){if(!voice||!voice.listen){feedback("Android APK hangmodul nem érhető el.",false);return;}by("jarvis-voice-last").textContent="Beszédfelismerő indítása...";voice.listen();}
trigger.onclick=openVoice;
by("jarvis-voice-close").onclick=closeVoice;
by("jarvis-voice-listen").onclick=listen;
by("jarvis-voice-feedback").onchange=function(){if(voice)voice.setFeedback(this.checked);};
by("jarvis-voice-test").onclick=function(){if(voice){voice.say("JARVIS rendszerhang teszt. A hangvezérlés aktív.");}};
function addInline(form){
 if(!form)return;
 var btn=create("button","jarvis-mic-inline","🎙");btn.type="button";btn.title="Magyar hangparancs";btn.onclick=function(){openVoice();listen();};
 form.insertBefore(btn,form.lastElementChild);
}
addInline(by("chatForm"));addInline(by("miniForm"));
function android(command,args){if(!termux||!termux.hasToken||!termux.hasToken()){feedback("Először párosítsd a JARVIS Termux-motort.",true);return false;}termux.androidAction(command,JSON.stringify(args||{}));return true;}
function interpreted(raw){
 var phrase=String(raw||"").replace(/^\s*(?:jarvis|dzsarvis|gyarvis)\s*[,.:!?-]?\s*/i,"").trim();
 var phraseNorm=strip(phrase).replace(/[.!?]+$/,"");
 if(!phraseNorm){feedback("Nem ismertem fel a parancsot.",true);return;}
 var actions=[
 [/^(?:fo(kepernyo|oldal)|menj haza|kezdokepernyo|home)$/,"home"],
 [/^(?:vissza|lepes vissza|back)$/,"back"],
 [/^(?:legutobbiak|futo alkalmazasok|alkalmazasvalto)$/,"recents"],
 [/^(?:ertesitesek|mutasd az ertesiteseket)$/,"notifications"],
 [/^(?:gorgess le|gorgess lefele|gorgess lefelé|lapozz le|lefele)$/,"scroll_down"],
 [/^(?:gorgess fel|gorgess felfele|lapozz fel|felfele)$/,"scroll_up"]
 ];
 for(var i=0;i<actions.length;i++){
  if(actions[i][0].test(phraseNorm)){
   if(android(actions[i][1],{}))feedback("Háttérmotorba küldve: "+phrase+".",true);
   return;
  }
 }
 if(/^(?:feladatok|mutasd a feladatokat|termux|rendszerallapot|statusz)$/.test(phraseNorm)){
  if(termuxUI&&termuxUI.open){closeVoice();termuxUI.open();feedback("Termux feladatpanel megnyitva.",true);}
  return;
 }
 var memory=phrase.match(/^(?:jegyezd meg|tanuld meg|emlékezz)\s*[:,-]?\s*(.{1,1000})$/i);
 if(memory){
  if(termux&&termux.hasToken&&termux.hasToken()){termux.remember(memory[1]);feedback("Tudáselem elküldve a Termuxnak.",true);}
  else feedback("A mentéshez párosítsd a Termuxot.",true);
  return;
 }
 var opened=phrase.match(/^(?:nyisd meg|indítsd el)\s+(?:a[z]?\s+)?(.{2,80})$/i);
 if(opened){
  var requested=strip(opened[1]).replace(/\s+(?:alkalmazast|appot)$/,"").trim();
  var apps=[];
  try{apps=JSON.parse(window.JarvisNative.listApps());}catch(e){}
  var matched=apps.filter(function(a){var name=strip(a.name);return name===requested||name.startsWith(requested)||requested.startsWith(name);});
  if(matched.length===1){
   if(android("launch",{package:matched[0].pkg}))feedback("Alkalmazás indítása: "+matched[0].name+".",true);
  }else feedback(matched.length?"Több alkalmazás található ezen a néven.":"Nem találtam az alkalmazást.",true);
  return;
 }
 feedback("Ezt a parancsot még nem támogatja a JARVIS: "+phrase+".",true);
}
window.JarvisVoice={
 open:openVoice,listen:listen,receive:function(json){
  var e;try{e=JSON.parse(json);}catch(err){return;}
  if(e.type==="recognized"){by("jarvis-voice-last").textContent="Felismerve: "+e.text;interpreted(e.text);}
  else if(e.type==="error"||e.type==="tts_unavailable")feedback(e.text,false);
  else if(e.type==="listening")by("jarvis-voice-last").textContent="Hallgatlak...";
  else if(e.type==="cancel")by("jarvis-voice-last").textContent="Beszédfelismerés megszakítva.";
 }
};
var previousPage=window.jarvisOpenPage;
window.jarvisOpenPage=function(page){
 if(page==="voice"){openVoice();listen();return;}
 if(typeof previousPage==="function")previousPage(page);
};
/* Durable monitored-page jobs: the Termux backend performs the work and holds results. */
if(termuxUI && by("termux-panel")){
 var section=create("section");section.id="termux-monitor";
 section.innerHTML='<h3>HÁTTÉRWEBELLENŐRZÉS · TERMUX</h3>'+
 '<p>Nyilvános HTTPS-weboldalak ellenőrzése a HUD bezárása után is. A munkát a Termux szerver végzi.</p>'+
 '<div class="row"><input id="termux-monitor-url" placeholder="https://example.org/" type="url" autocapitalize="off" spellcheck="false"></div>'+
 '<div class="row"><select id="termux-monitor-repeat">'+
 '<option value="0">Egyszeri</option><option value="300">5 perc</option><option value="900">15 perc</option><option value="3600">Óránként</option><option value="21600">6 óránként</option><option value="86400">Naponta</option></select>'+
 '<button id="termux-monitor-submit">INDÍTÁS</button></div><div id="termux-monitor-list"></div>';
 by("termux-panel").querySelector(".content").appendChild(section);
 by("termux-monitor-submit").onclick=function(){
  var url=by("termux-monitor-url").value.trim(),repeat=Number(by("termux-monitor-repeat").value);
  if(!/^https:\/\/[^\/\s?#]+/i.test(url)){feedback("Érvényes HTTPS-webcímet adj meg.",false);return;}
  if(!termux||!termux.hasToken||!termux.hasToken()){feedback("Párosítsd a Termux-motort.",false);return;}
  if(confirm(repeat?"Indulhat az ismétlődő weboldal-ellenőrzés?":"Indulhat az egyszeri weboldal-ellenőrzés?"))termux.watchPage(url,repeat);
 };
 var previousReceive=termuxUI.receive;
 termuxUI.receive=function(raw){
  if(previousReceive)previousReceive.call(termuxUI,raw);
  try{
   var event=JSON.parse(raw),out=event.data||{};
   if(event.action==="watch"){feedback(event.ok?"Háttérfeladat elküldve: #"+out.id:(out.error||"Ütemezési hiba"),false);if(event.ok)setTimeout(function(){termuxUI.refresh();},900);}
   if(event.action==="tasks"&&event.ok&&by("termux-monitor-list")){
    var tasks=(out.tasks||[]).filter(function(t){return t.type==="fetch_page";}).slice(0,12);
    var host=by("termux-monitor-list");host.textContent="";
    if(!tasks.length){host.textContent="Még nincs webes háttérfeladat.";return;}
    tasks.forEach(function(t){
      var row=create("div","list-item"),period=t.repeat_seconds>0?t.repeat_seconds+" mp":"egyszeri";
      row.appendChild(create("strong",null,"#"+t.id+" · "+t.status+" · "+period));
      row.appendChild(create("div",null,t.payload&&t.payload.url||""));
      var result=t.result||{};
      if(result.title||result.error||result.excerpt)row.appendChild(create("div",null,result.title||result.error||result.excerpt));
      host.appendChild(row);
    });
   }
  }catch(e){}
 };
}
})();
