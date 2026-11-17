/* JARVIS C 2.5: typed Hungarian commands and safe external-app writing. */
(function(){
"use strict";
function $(id){return document.getElementById(id);}
function make(tag,cls,text){var n=document.createElement(tag);if(cls)n.className=cls;if(text)n.textContent=text;return n;}
function notify(message,kind){
 var box=$("toasts");if(!box)return;
 var element=make("div","toast "+(kind||""),message);
 box.appendChild(element);
 setTimeout(function(){element.remove();},4800);
}
var voiceButton=document.querySelector(".jarvis-voice-trigger");
if(!voiceButton)return;
var parent=voiceButton.parentNode;
var wrapper=make("div","jarvis-input-actions");
parent.insertBefore(wrapper,voiceButton);
wrapper.appendChild(voiceButton);
var typeButton=make("button","jarvis-typing-trigger","✎ ÍRÁS");
typeButton.type="button";
wrapper.appendChild(typeButton);
var dialog=make("section","jarvis-writing-layer");
dialog.id="jarvis-writing-layer";
dialog.innerHTML=
'<header><strong>◈ JARVIS · ÍRÁSOS VEZÉRLÉS</strong><button id="jarvis-writing-close">✕</button></header>'+
'<section class="jarvis-writing-card"><h3>MAGYAR PARANCS BEGÉPELÉSE</h3>'+
'<p>Ugyanazokat a parancsokat írhatod be, amelyeket hanggal is mondanál. A Termux háttérmotor végrehajtja a támogatott utasításokat.</p>'+
'<form id="jarvis-writing-form"><textarea id="jarvis-writing-text" rows="4" maxlength="1000" placeholder="Például: JARVIS, nyisd meg a Chrome-ot" spellcheck="true"></textarea>'+
'<button type="submit" id="jarvis-writing-run">PARANCS VÉGREHAJTÁSA</button></form>'+
'<p id="jarvis-writing-result" aria-live="polite">Várom a parancsot.</p></section>'+
'<section class="jarvis-writing-card"><h3>ÍRÁS MÁS ALKALMAZÁSBA</h3>'+
'<p>1. Engedélyezd a lebegő JARVIS-ikont. 2. Nyisd meg a kívánt alkalmazást és koppints a szövegmezőbe. 3. A lebegő ikon menüjében válaszd az <b>ÍRÁS</b> gombot.</p>'+
'<button id="jarvis-writing-overlay">LEBEGŐ IKON ENGEDÉLYEZÉSE</button>'+
'<p class="jarvis-writing-hint">Biztonság: a JARVIS csak a kijelölt szövegmezőt tölti ki, és soha nem nyom automatikusan Küldés vagy Vásárlás gombot.</p></section>'+
'<section class="jarvis-writing-card"><h3>PÉLDAPARANCSOK</h3>'+
'<p>„JARVIS, főoldal”<br>„JARVIS, görgess le”<br>„JARVIS, nyisd meg a Chrome-ot”<br>„JARVIS, jegyezd meg: új ötlet”<br>„JARVIS, mutasd a feladatokat”</p></section>';
document.body.appendChild(dialog);
var field=$("jarvis-writing-text");
function open(){dialog.classList.add("open");}
function close(){dialog.classList.remove("open");}
function execute(input){
 var value=String(input||"").trim();
 if(!value)return;
 if(!window.JarvisVoice || !window.JarvisVoice.executeText){
  notify("A JARVIS parancsértelmező nincs betöltve.","bad");return;
 }
 $("jarvis-writing-result").textContent="Parancs átadva a feldolgozónak: "+value.slice(0,140);
 // The same interpreter is used for speech and typed commands.
 window.JarvisVoice.executeText(value);
}
typeButton.addEventListener("click",open);
$("jarvis-writing-close").addEventListener("click",close);
$("jarvis-writing-form").addEventListener("submit",function(event){
 event.preventDefault();
 var value=field.value.trim();
 if(!value){notify("Írj be egy parancsot.","warn");return;}
 execute(value);
 field.value="";
});
$("jarvis-writing-overlay").addEventListener("click",function(){
 if(window.JarvisNative && window.JarvisNative.overlay){
  window.JarvisNative.overlay();
  notify("Nyisd meg a célalkalmazást; a JARVIS lebegő menüjében válaszd az ÍRÁS gombot.","good");
 }else notify("Az Android lebegő ikon csak a telepített APK-ban érhető el.","warn");
});
function commandInput(text){
 return /^(?:(?:jarvis|dzsarvis|gyarvis)\b[\s,.:!?-]*)?(?:főoldal|főképernyő|kezdőképernyő|vissza|home|back|görgess\b|lapozz\b|értesítések|legutóbbiak|nyisd meg\b|indítsd el\b|jegyezd meg\b|tanuld meg\b|emlékezz\b|mutasd a feladatokat|termux\b)/i.test(text);
}
// Typed commands in full or mini chat are real instructions, not fake demo replies.
// Free-form text stays in the local demonstration chat.
[["chatForm","chatText"],["miniForm","miniText"]].forEach(function(pair){
 var form=$(pair[0]),entry=$(pair[1]);if(!form||!entry)return;
 form.addEventListener("submit",function(ev){
  var text=entry.value.trim();
  if(!text||!commandInput(text))return;
  ev.preventDefault();
  ev.stopImmediatePropagation();
  entry.value="";
  execute(text);
 },true);
});
var previous=window.jarvisOpenPage;
window.jarvisOpenPage=function(name){
 if(name==="write"){open();return;}
 if(typeof previous==="function")previous(name);
};
window.JarvisWriter={open:open,execute:execute};
})();