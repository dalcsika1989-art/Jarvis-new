(function(){
'use strict';
var css=document.createElement('style');
css.textContent='#jarvisMini{position:fixed;z-index:300;right:9px;bottom:110px;width:60px;height:60px;border-radius:50%;border:2px solid #63efff;background:radial-gradient(circle,#c8ffff,#1d8aab 23%,#041529 51%,#031525);box-shadow:0 0 23px #00cfff,inset 0 0 15px #27bfff;color:#dbfaff;pointer-events:auto;touch-action:none;font:bold 17px sans-serif}#jarvisDock{position:fixed;z-index:320;inset:0;background:rgba(0,9,22,.97);color:#bdfaff;overflow:auto;padding:25px 15px;display:none;pointer-events:auto;font:13px/1.6 sans-serif}#jarvisDock.on{display:block}#jarvisDock button{border:1px solid #347e98;border-radius:6px;background:linear-gradient(140deg,#082137,#06172b);color:#c4faff;min-height:46px;padding:10px 6px;font:12px monospace}#jarvisDock .topline{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #227c9b;padding-bottom:10px;font:16px monospace;letter-spacing:.13em}#jarvisDock .grid{display:grid;grid-template-columns:repeat(3,1fr);gap:7px;margin:13px 0}#jarvisDock #jlog{border:1px solid #206583;background:#020f20;padding:13px;white-space:pre-wrap;min-height:150px;max-height:35vh;overflow:auto}#jarvisDock textarea,#jarvisDock input{box-sizing:border-box;width:100%;padding:12px;background:#041b2e;color:white;border:1px solid #2c819c;border-radius:6px;font:14px sans-serif}#jarvisDock #jkeypane{display:none}#jarvisDock #jkeypane.on{display:block}#jarvisDock .hint{position:static;margin:12px 0 5px;color:#78d4e7}';
document.head.appendChild(css);
var mini=document.createElement('button');mini.id='jarvisMini';mini.textContent='J';
var pane=document.createElement('div');pane.id='jarvisDock';
pane.innerHTML='<div class="topline">JARVIS // ARC CORE <button id="jclose">✕</button></div><div class="hint">EREDETI HUD 7.6 · NATÍV VEZÉRLÉS</div><div class="grid"><button id="jmic">◉ MIKROFON</button><button id="jaccess">▦ VEZÉRLÉS</button><button id="jstop">■ STOP</button><button id="jhome">⌂ HOME</button><button id="jback">‹ VISSZA</button><button id="jscroll">↓ GÖRGETÉS</button><button id="jsetup">⚙ API</button><button id="jstatus">▷ ÁLLAPOT</button><button id="jmini">◇ MINI HUD</button></div><div id="jlog">JARVIS készen áll. A telefonvezérlést az Android kisegítő beállításaiban engedélyezd.</div><div class="hint">PARANCS / CHAT</div><textarea id="jprompt" rows="3" placeholder="Pl. Nyisd meg a YouTube-ot"></textarea><button id="jsend" style="width:100%">PARANCS INDÍTÁSA / ÜZENET</button><div id="jkeypane"><div class="hint">GEMINI API-KULCS</div><input id="jkey" type="password" placeholder="Gemini API-kulcs"><button id="jsave">KULCS MENTÉSE</button><p>A magyar férfihangot az Android beszédmotorja adja; a Charon felhőhang még nincs integrálva.</p></div>';
document.body.appendChild(mini);document.body.appendChild(pane);
function $(id){return document.getElementById(id)}
function log(msg){var x=$('jlog');x.textContent=(x.textContent.length>3000?x.textContent.slice(-1500):x.textContent)+'\n'+msg;x.scrollTop=x.scrollHeight}
function call(m,v){try{if(!window.JarvisNative){log('HTML-előnézet: nincs Android-kapcsolat');return;}if(v===undefined)window.JarvisNative[m]();else window.JarvisNative[m](v)}catch(e){log('Hiba: '+e.message)}}
function visible(on){pane.classList.toggle('on',on);mini.style.display=on?'none':'block'}
mini.onclick=function(){visible(true)};$('jclose').onclick=$('jmini').onclick=function(){visible(false)};
$('jmic').onclick=function(){call('listen')};
$('jaccess').onclick=function(){call('openControls')};
$('jstop').onclick=function(){call('stop')};
$('jhome').onclick=function(){call('sendCommand','főképernyő')};
$('jback').onclick=function(){call('sendCommand','vissza')};
$('jscroll').onclick=function(){call('sendCommand','görgess le')};
$('jsetup').onclick=function(){$('jkeypane').classList.toggle('on')};
$('jstatus').onclick=function(){log(window.JarvisNative?window.JarvisNative.getControlStatus():'Natív kapcsolat nincs')};
$('jsave').onclick=function(){var v=$('jkey').value.trim();if(v){call('saveKey',v);$('jkey').value=''}};
$('jsend').onclick=function(){var v=$('jprompt').value.trim();if(v){log('› '+v);call('sendCommand',v);$('jprompt').value=''}};
window.jarvisReceive=function(k,m){log('['+k+'] '+m)};
window.jarvisClosePanel=function(){visible(false)};
})();