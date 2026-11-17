/* JARVIS C 2.6: real AI chat via authenticated local Termux backend. */
(function(){
"use strict";
var nativeAI=window.JarvisAIEngine;
var nativeTermux=window.JarvisTermux;
function by(id){return document.getElementById(id);}
function el(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
var lastPrompt="",busy=false;
var launch=el("button","jarvis-ai-button","◈ VALÓDI AI · CHAT");
launch.type="button";
var inputRow=document.querySelector(".jarvis-input-actions");
if(inputRow&&inputRow.parentNode)inputRow.parentNode.insertBefore(launch,inputRow.nextSibling);
else {var nav=document.querySelector(".bottom");if(nav)nav.parentNode.insertBefore(launch,nav);}
var modal=el("section","jarvis-ai-modal");
modal.id="jarvis-ai-modal";
modal.innerHTML=
 '<header class="jarvis-ai-header"><div><strong>◈ JARVIS AI / GEMINI</strong><p id="jarvis-ai-status">AI-kapcsolat ellenőrzése...</p></div><button id="jarvis-ai-close" type="button">✕</button></header>'+
 '<div class="jarvis-ai-scroll">'+
 '<section id="jarvis-ai-setup" class="jarvis-ai-card"><h3>AI ÖSSZEKÖTÉS · TERMUX 1.2</h3>'+
 '<p>A korábbi JARVIS C 2.5 csak helyi demóválaszokat adott. Ebben a verzióban valódi AI-válaszhoz a frissített Termux és egy Gemini API-kulcs kell.</p>'+
 '<p>1. Telepítsd a JARVIS Termux 1.2 AI frissítést.<br>2. A Termuxban: <code>python ~/.jarvis/jarvisctl.py ai-key</code><br>3. Másold be saját Gemini API-kulcsodat a Termux rejtett mezőjébe.<br>4. Teszt: <code>python ~/.jarvis/jarvisctl.py ai-chat "Szia JARVIS"</code></p>'+
 '<p>API-kulcs: <b>Google AI Studio</b> – aistudio.google.com/api-keys. A kulcsot ne küldd el chatben. Külső modell használata kvótát vagy díjat vonhat maga után.</p>'+
 '<button id="jarvis-ai-refresh">KAPCSOLAT ELLENŐRZÉSE</button>'+
 '<button id="jarvis-ai-open-termux">TERMUX PANEL</button></section>'+
 '<section class="jarvis-ai-card"><h3>AI-BESZÉLGETÉS</h3><div id="jarvis-ai-messages" class="jarvis-ai-messages" aria-live="polite"></div>'+
 '<form id="jarvis-ai-form"><textarea id="jarvis-ai-input" rows="3" maxlength="4000" placeholder="Mit szeretnél megkérdezni JARVIS-tól?"></textarea><button type="submit" id="jarvis-ai-send">KÉRDÉS KÜLDÉSE AZ AI-NAK</button></form>'+
 '<p class="jarvis-ai-privacy">A kérdés és a rövid előzmény a Gemini API-hoz kerül. A telefonos parancsokat továbbra is külön a Termux/Android motor hajtja végre.</p></section>'+
 '<section class="jarvis-ai-card"><h3>BESZÉLGETÉS KEZELÉSE</h3><button id="jarvis-ai-clear">ELŐZMÉNYEK TÖRLÉSE</button><p id="jarvis-ai-model">Modell: –</p></section></div>';
document.body.appendChild(modal);
var status=by("jarvis-ai-status");
function setStatus(text,kind){
 status.textContent=text;
 status.className=kind||"";
 launch.classList.toggle("online",kind==="good");
 launch.textContent=kind==="good"?"◈ AI · KAPCSOLAT BEÁLLÍTVA":"◈ VALÓDI AI · CHAT";
}
function append(target,role,text){
 if(!target)return;
 var item=el("div","jarvis-ai-msg "+role);
 item.appendChild(el("strong",null,role==="user"?"ÖN":"JARVIS AI"));
 item.appendChild(el("span",null,String(text||"")));
 target.appendChild(item);
 target.scrollTop=target.scrollHeight;
}
function appendChat(role,text){
 var holder=by("messages");
 if(holder){
  var msg=el("div","msg "+(role==="user"?"mine":""));
  if(role==="model")msg.appendChild(el("strong",null,"JARVIS · VALÓDI AI"));
  msg.appendChild(el("span",null,text));
  holder.appendChild(msg);holder.scrollTop=holder.scrollHeight;
 }
 if(by("miniMessages")){
  var tiny=by("miniMessages");
  tiny.textContent=(tiny.textContent||"").slice(-1400)+"\n"+(role==="user"?"ÖN: ":"JARVIS AI: ")+String(text||"").slice(0,1600);
 }
}
function setBusy(value){
 busy=value;
 by("jarvis-ai-send").disabled=value;
 by("jarvis-ai-send").textContent=value?"JARVIS AI · VÁLASZRA VÁR...":"KÉRDÉS KÜLDÉSE AZ AI-NAK";
}
function statusRequest(){
 if(!nativeAI){setStatus("Az AI natív modul nem érhető el: telepítsd a JARVIS 2.6 APK-t.","bad");return;}
 nativeAI.status();
}
function open(){
 modal.classList.add("open");
 statusRequest();
 if(nativeAI)nativeAI.history();
}
function close(){modal.classList.remove("open");}
function ask(text){
 var prompt=String(text||"").trim();
 if(!prompt)return;
 if(prompt.length>4000){setStatus("Maximum 4000 karakter küldhető egyszerre.","bad");return;}
 if(busy){append(by("jarvis-ai-messages"),"model","JARVIS már dolgozik az előző kérdésen.");return;}
 open();
 lastPrompt=prompt;
 append(by("jarvis-ai-messages"),"user",prompt);
 appendChat("user",prompt);
 if(!nativeAI){append(by("jarvis-ai-messages"),"model","Hiányzik az Android AI kapcsolat.");return;}
 setBusy(true);
 nativeAI.ask(prompt);
}
launch.addEventListener("click",open);
by("jarvis-ai-close").addEventListener("click",close);
by("jarvis-ai-refresh").addEventListener("click",statusRequest);
by("jarvis-ai-open-termux").addEventListener("click",function(){close();if(window.JarvisTermuxUI)window.JarvisTermuxUI.open();});
by("jarvis-ai-form").addEventListener("submit",function(event){
 event.preventDefault();
 var value=by("jarvis-ai-input").value.trim();
 if(!value)return;
 by("jarvis-ai-input").value="";
 ask(value);
});
by("jarvis-ai-clear").addEventListener("click",function(){
 if(!nativeAI){setStatus("Nincs AI-kapcsolat.","bad");return;}
 if(confirm("Törlöd a Termuxban tárolt AI-beszélgetési előzményt?"))nativeAI.clear();
});
function receive(input){
 var message;try{message=JSON.parse(input);}catch(e){return;}
 var action=message.action,ok=message.ok,data=message.data||{};
 if(action==="status"){
  if(ok){
   by("jarvis-ai-model").textContent="Modell: "+(data.model||"–");
   setStatus(data.configured?"AI beállítva · Gemini "+data.model+" · online teszt szükséges":"NINCS AI-KULCS · Termuxban állítsd be az ai-key paranccsal",data.configured?"good":"wait");
   by("jarvis-ai-setup").style.display=data.configured?"none":"block";
  }else setStatus(data.error||"Nem érhető el az AI-szerver.","bad");
 }else if(action==="chat"){
  setBusy(false);
  if(ok&&data.reply){
   append(by("jarvis-ai-messages"),"model",data.reply);
   appendChat("model",data.reply);
   setStatus("AI VÁLASZ BEÉRKEZETT · "+(data.model||"Gemini"),"good");
   by("jarvis-ai-setup").style.display="none";
   if(window.JarvisVoiceNative && window.JarvisVoiceNative.say)window.JarvisVoiceNative.say(data.reply.slice(0,220));
  }else{
   var detail=data.error||"Az AI-kérés sikertelen.";
   append(by("jarvis-ai-messages"),"model","HIBA: "+detail);
   appendChat("model","AI-KAPCSOLATI HIBA: "+detail);
   setStatus(detail,"bad");
   if(/kulcs|frissít|unknown endpoint|nincs konfigurálva/i.test(detail))by("jarvis-ai-setup").style.display="block";
  }
 }else if(action==="history"){
  if(!ok)return;
  var list=Array.isArray(data.messages)?data.messages:[];
  var container=by("jarvis-ai-messages");
  if(!busy && list.length){
   container.textContent="";
   list.forEach(function(m){append(container,m.role==="user"?"user":"model",m.text);});
  }
 }else if(action==="clear"){
  if(ok){by("jarvis-ai-messages").textContent="";setStatus("Előzmények törölve.","good");}
  else setStatus(data.error||"Nem sikerült törölni.","bad");
 }
}
window.JarvisAIUI={open:open,ask:ask,receive:receive,status:statusRequest};
var prior=window.jarvisOpenPage;
window.jarvisOpenPage=function(name){if(name==="ai"){open();return;}if(typeof prior==="function")prior(name);};
var note=document.querySelector(".chat-visual + .chat-caption");
if(note)note.textContent="JARVIS valódi AI-válasza a Termux 1.2 + Gemini-kulcs beállítása után érhető el.";
/* Keep known Android commands with write.js; other chat input becomes real AI chat. */
[["chatForm","chatText"],["miniForm","miniText"]].forEach(function(pair){
 var form=by(pair[0]),input=by(pair[1]);
 if(!form||!input)return;
 form.addEventListener("submit",function(event){
   var value=input.value.trim();
   if(!value)return;
   event.preventDefault();
   event.stopImmediatePropagation();
   input.value="";
   ask(value);
 },true);
});
})();