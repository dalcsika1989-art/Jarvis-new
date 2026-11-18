/* JARVIS 2.1 — ChatGPT and phone companion launcher, honest link diagnostics. */
(function(){
"use strict";
const native=window.Native;
function $(id){return document.getElementById(id);}
const css=[
"#jarvis-chatgpt-panel{position:fixed;z-index:9999;inset:0;display:none;background:radial-gradient(circle at 50% 20%,#0c3447,#030b13 77%);padding:14px;overflow-y:auto;color:#dff8ff}",
"#jarvis-chatgpt-panel.open{display:block}",
"#jarvis-chatgpt-panel .head{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #67ddff;padding:9px 0 17px}",
"#jarvis-chatgpt-panel h2{font:600 16px system-ui;letter-spacing:1.5px;margin:0}",
"#jarvis-chatgpt-panel .box{border:1px solid #275969;background:linear-gradient(#10303d,#081824);border-radius:5px;padding:15px;margin:13px 0}",
"#jarvis-chatgpt-panel h3{font:600 12px system-ui;letter-spacing:1.2px;color:#83dcec;margin:0 0 10px}",
"#jarvis-chatgpt-panel p{font:12px/1.7 system-ui;color:#b9d8e4}",
"#jarvis-chatgpt-panel .value{font:12px/1.6 ui-monospace,monospace;color:#e5fbff;margin:7px 0;padding:9px;background:#071823;border-left:3px solid #e3bd68}",
"#jarvis-chatgpt-panel .value.good{border-color:#34f0ac}",
"#jarvis-chatgpt-panel .value.bad{border-color:#ee6684}",
"#jarvis-chatgpt-panel button{border:1px solid #3ba2bd;background:#0e3545;color:#ddf9ff;padding:11px 14px;border-radius:4px;margin:4px 5px 4px 0;font-size:12px;font-weight:bold}",
"#jarvis-chatgpt-panel .warning{border-left:3px solid #f1cc77;padding:9px;color:#ffe2a6;background:#2a2419;font-size:12px}",
"#jarvis-chatgpt-panel .helper{font:12px/1.8 system-ui;color:#96d9e8}",
"#jarvis-chatgpt-panel small{display:block;font-size:10px;color:#a3c8d8;margin-top:7px}"
].join("\n");
const style=document.createElement("style");style.textContent=css;document.head.appendChild(style);
const panel=document.createElement("section");
panel.id="jarvis-chatgpt-panel";panel.setAttribute("aria-label","ChatGPT telefonkapcsolat diagnosztika");
panel.innerHTML=[
'<div class="head"><h2>◈ CHATGPT / TELEFONHÍD</h2><button type="button" id="jarvis-chatgpt-close">✕ BEZÁR</button></div>',
'<div class="box"><h3>VALÓS ÁLLAPOT / NEM SZIMULÁCIÓ</h3>',
'<div class="value" id="jarvis-chatgpt-status">Az alkalmazások és engedélyek ellenőrzése...</div>',
'<div class="value" id="jarvis-phone-status">Távoli kapcsolat: csak a ChatGPT bővítményben ellenőrizhető.</div>',
'<button type="button" id="jarvis-chatgpt-refresh">HELYI ÁLLAPOT FRISSÍTÉSE</button>',
'<p class="warning">A ChatGPT-telefonkapcsolat külön párosított Android-szolgáltatás. Ennek a HUD-nak a telepítése önmagában nem kapcsolja online állapotba a bővítményt.</p></div>',
'<div class="box"><h3>CSATLAKOZTATÁSI LÉPÉSEK</h3>',
'<div class="helper">1. Nyisd meg a külön JARVIS Telefonkapcsolat alkalmazást és indítsd a kapcsolatát.<br>',
'2. A ChatGPT-ben ellenőrizd a JARVIS bővítmény csatlakoztatását.<br>',
'3. Küldd el a ChatGPT-nek az ellenőrző kérést, hogy közvetlenül lekérdezhesse a telefon online/offline állapotát.</div>',
'<button type="button" id="jarvis-phone-open">TELEFONKAPCSOLAT APP</button>',
'<button type="button" id="jarvis-chatgpt-open">CHATGPT MEGNYITÁSA</button>',
'<button type="button" id="jarvis-chatgpt-copy">ELLENŐRZŐ KÉRÉS MÁSOLÁSA</button></div>',
'<div class="box"><h3>A FUNKCIÓK HATÁRAI</h3>',
'<p>A helyi Android-parancsok és a Gemini Live külön működnek. A ChatGPT-telefonhíd távoli állapotát az Android HUD nem tudja lekérdezni.</p>',
'<small>Az online státuszt csak a ChatGPT-ben visszaigazolt telefonkapcsolat alapján tekintsd aktívnak.</small></div>'
].join("");
document.body.appendChild(panel);
function renderLocal(){
 let data={};try{if(native&&native.chatBridgeStatus)data=JSON.parse(native.chatBridgeStatus());}catch(e){}
 const chatgpt=!!data.chatgptInstalled,access=!!data.localAccessibility,apps=Array.isArray(data.companionApps)?data.companionApps:[];
 const line=$("jarvis-chatgpt-status");
 line.textContent="ChatGPT: "+(chatgpt?"telepítve":"nem található")+" · Android vezérlés: "+(access?"aktív":"nincs engedélyezve")+" · Telefonhíd: "+(apps.length?apps.map(x=>x.name).join(", "):"nem található");
 line.className="value "+(chatgpt&&access?"good":"bad");
 $("jarvis-phone-status").textContent="Távoli kapcsolat: nem ellenőrizhető ebből az APK-ból. Kérj friss státuszt a ChatGPT-ben.";
}
function open(){panel.classList.add("open");renderLocal();}
function close(){panel.classList.remove("open");}
$("jarvis-chatgpt-close").onclick=close;
$("jarvis-chatgpt-refresh").onclick=renderLocal;
$("jarvis-chatgpt-open").onclick=()=>{if(native&&native.launchChatGPT)native.launchChatGPT();};
$("jarvis-phone-open").onclick=()=>{if(native&&native.launchPhoneBridge)native.launchPhoneBridge();};
$("jarvis-chatgpt-copy").onclick=()=>{if(native&&native.copyConnectionCheck)native.copyConnectionCheck();};
window.jarvisChatGPTUI={open,close,refresh:renderLocal};
if(!native || !native.chatBridge){const button=$("chatbridge");if(button)button.onclick=open;}
})();
