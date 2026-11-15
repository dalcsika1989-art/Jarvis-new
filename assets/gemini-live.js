/* JARVIS 2.0 — Gemini Live WebSocket module; no hard-coded API keys.
 * Reuses the real JARVIS 2.0 HUD and the existing Native bridge.
 * Input: 16 kHz PCM, output: 24 kHz PCM. API key stays in session memory.
 */
(()=>{
 'use strict';
 const WS_ROOT='wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent';
 const MODEL='models/gemini-3.1-flash-live-preview';
 const $=id=>document.getElementById(id);
 const ui={};
 let socket=null, inputStream=null, micCtx=null, micSource=null, processor=null;
 let outputCtx=null, scheduled=[], nextAudioTime=0, currentTranscription='';
 let userKey='';
 let connected=false, micEnabled=false, startup=false;
 const SAFE_ACTIONS=['open_app','home','back','scroll','click_text','notifications'];
 const safety='Ha egy feladat üzenetküldést, vásárlást, fizetést, törlést, jelszót, jogosultságváltoztatást vagy más jelentős műveletet érint, először kérj egyértelmű jóváhagyást. Ne állítsd, hogy egy Android-művelet kész, amíg erre megerősítés nincs.';
 function liveState(s){
  if($('state'))$('state').textContent=s;
  if($('connection'))$('connection').textContent=s;
  if(ui.status)ui.status.textContent=s;
  if($('hint'))$('hint').textContent=s;
 }
 function showResult(who,txt){
  try {if(typeof window.add==='function') window.add(who,txt);else if(typeof window.nativeEvent==='function')window.nativeEvent(who,txt);}catch(e){console.warn(e);}
 }
 function announce(message){if(typeof window.nativeEvent==='function')window.nativeEvent('agent',message);else liveState(message);}
 function jsonSend(data){if(socket && socket.readyState===WebSocket.OPEN)socket.send(JSON.stringify(data));}
 function clearPlayback(){for(const s of scheduled){try{s.stop()}catch(e){}}scheduled=[];nextAudioTime=0;}
 function audioContext(){
  if(!outputCtx)outputCtx=new (window.AudioContext||window.webkitAudioContext)({sampleRate:24000});
  if(outputCtx.state==='suspended')outputCtx.resume().catch(()=>{});
  return outputCtx;
 }
 function playPcm(b64){
  const bytes=Uint8Array.from(atob(b64), c=>c.charCodeAt(0));
  if(bytes.byteLength<2)return;
  const sampleCount=bytes.byteLength>>1;
  const ctx=audioContext(), buf=ctx.createBuffer(1,sampleCount,24000),ch=buf.getChannelData(0);
  for(let i=0;i<sampleCount;i++){const v=bytes[2*i]|(bytes[2*i+1]<<8);ch[i]=(v&0x8000?v-65536:v)/32768;}
  const src=ctx.createBufferSource();src.buffer=buf;src.connect(ctx.destination);
  const when=Math.max(ctx.currentTime+0.035,nextAudioTime);
  src.start(when);nextAudioTime=when+buf.duration;
  scheduled.push(src);src.onended=()=>{const i=scheduled.indexOf(src);if(i>=0)scheduled.splice(i,1);};
 }
 function toBase64(bytes){let out='';for(let i=0;i<bytes.length;i++)out+=String.fromCharCode(bytes[i]);return btoa(out);}
 function sampleToPcm(samples, rate){
  const n=Math.floor(samples.length*16000/rate);
  const bytes=new Uint8Array(n*2);
  for(let i=0;i<n;i++){
   const pos=i*rate/16000,lo=Math.floor(pos),hi=Math.min(samples.length-1,lo+1),frac=pos-lo;
   const v=Math.max(-1,Math.min(1,samples[lo]*(1-frac)+samples[hi]*frac));
   const x=v<0?Math.round(v*32768):Math.round(v*32767);
   bytes[2*i]=x&255;bytes[2*i+1]=(x>>8)&255;
  }
  return toBase64(bytes);
 }
 async function startMicrophone(){
  if(micEnabled)return;
  if(!connected)throw Error('Gemini kapcsolat még nincs kész.');
  if(!window.isSecureContext||!navigator.mediaDevices?.getUserMedia)throw Error('A mikrofonhoz biztonságos WebView eredet és Android mikrofonengedély szükséges.');
  await audioContext().resume();
  inputStream=await navigator.mediaDevices.getUserMedia({audio:{channelCount:1,echoCancellation:true,noiseSuppression:true,autoGainControl:true}});
  micCtx=new (window.AudioContext||window.webkitAudioContext)({sampleRate:16000});
  await micCtx.resume();
  micSource=micCtx.createMediaStreamSource(inputStream);
  processor=micCtx.createScriptProcessor(1024,1,1);
  processor.onaudioprocess=e=>{
   e.outputBuffer.getChannelData(0).fill(0);
   if(!connected||!micEnabled)return;
   const b64=sampleToPcm(e.inputBuffer.getChannelData(0),micCtx.sampleRate);
   jsonSend({realtimeInput:{audio:{data:b64,mimeType:'audio/pcm;rate=16000'}}});
  };
  micSource.connect(processor);processor.connect(micCtx.destination);
  micEnabled=true;ui.mic.textContent='MIKROFON LEÁLLÍTÁSA';
  announce('GEMINI LIVE — MIKROFON AKTÍV');
 }
 function stopMicrophone(notify=true){
  micEnabled=false;
  if(processor){processor.disconnect();processor.onaudioprocess=null;processor=null;}
  if(micSource){micSource.disconnect();micSource=null;}
  if(inputStream){inputStream.getTracks().forEach(t=>t.stop());inputStream=null;}
  if(micCtx){micCtx.close().catch(()=>{});micCtx=null;}
  if(ui.mic)ui.mic.textContent='ÉLŐ MIKROFON';
  if(notify&&connected)jsonSend({realtimeInput:{audioStreamEnd:true}});
 }
 function sendText(text){
  text=String(text||'').trim();if(!text)return;
  if(!connected){liveState('Előbb csatlakoztasd a Gemini Live-ot.');return;}
  showResult('user',text);
  jsonSend({clientContent:{turns:[{role:'user',parts:[{text}]}],turnComplete:true}});
 }
 function requestedAction(action,target){
  if(!SAFE_ACTIONS.includes(action))return {status:'refused',reason:'Ismeretlen telefonművelet'};
  const native=window.Native;
  if(!native || typeof native.send!=='function')return {status:'unavailable',reason:'A telefon Android-hídja nem elérhető'};
  const text=String(target||'').replace(/[\r\n;]/g,' ').slice(0,90).trim();
  if(text.length>80)return {status:'refused',reason:'Túl hosszú célpont'};
  let cmd;
  switch(action){
   case 'open_app':if(!text)return {status:'refused',reason:'Hiányzó alkalmazásnév'};cmd='nyisd meg '+text;break;
   case 'click_text':if(!text)return {status:'refused',reason:'Hiányzó képernyőfelirat'};cmd='kattints '+text;break;
   case 'home':cmd='főképernyő';break;
   case 'back':cmd='vissza';break;
   case 'scroll':cmd=text.toLowerCase().includes('fel')?'görgess fel':'görgess le';break;
   case 'notifications':cmd='nyisd meg az értesítéseket';break;
  }
  try{native.send(cmd);return {status:'requested',verified:false,command:cmd};}
  catch(e){return {status:'failed',error:String(e).slice(0,140)};}
 }
 async function handleToolCalls(obj){
  const responses=[];
  for(const f of obj.functionCalls||[]){
   const args=f.args||{};
   let result;
   if(f.name==='phone_action')result=requestedAction(String(args.action||''),String(args.target||''));
   else result={status:'refused',reason:'Ismeretlen eszköz'};
   responses.push({id:f.id,name:f.name,response:{result}});
  }
  if(responses.length)jsonSend({toolResponse:{functionResponses:responses}});
 }
 function incoming(raw){
  let d;try{d=JSON.parse(raw)}catch(e){announce('Nem érthető Gemini üzenet');return;}
  if(d.setupComplete){connected=true;startup=false;liveState('GEMINI LIVE — CSATLAKOZVA');ui.connect.textContent='LIVE KAPCSOLAT BONTÁSA';if(ui.launch)ui.launch.textContent='● LIVE';if(ui.panel)ui.panel.style.display='none';}
  if(d.toolCall)handleToolCalls(d.toolCall);
  const content=d.serverContent;
  if(!content)return;
  if(content.interrupted){clearPlayback();currentTranscription='';}
  if(content.inputTranscription?.text){if($('hint'))$('hint').textContent='Ön: '+content.inputTranscription.text;}
  if(content.outputTranscription?.text){currentTranscription+=content.outputTranscription.text;if($('state'))$('state').textContent='JARVIS BESZÉL';}
  for(const part of content.modelTurn?.parts||[]){if(part.inlineData?.data)playPcm(part.inlineData.data);}
  if(content.turnComplete && currentTranscription.trim()){
   showResult('reply',currentTranscription.trim());currentTranscription='';
  }
 }
 function teardown(){
  stopMicrophone(false);clearPlayback();connected=false;startup=false;currentTranscription='';
  if(socket){const ws=socket;socket=null;try{ws.close(1000,'JARVIS bontás')}catch(e){}}
  if(ui.connect)ui.connect.textContent='GEMINI LIVE INDÍTÁSA';if(ui.launch)ui.launch.textContent='◉ LIVE';
  liveState('GEMINI LIVE — LEÁLLÍTVA');
 }
 async function connect(){
  if(socket){teardown();return;}
  if(startup)return;
  let key=ui.key.value.trim()||userKey;
  if(!key){liveState('Írd be a saját Gemini API-kulcsodat.');ui.key.focus();return;}
  if(!/^[A-Za-z0-9_-]{12,180}$/.test(key)){liveState('A Gemini API-kulcs formátuma érvénytelen.');return;}
  userKey=key;ui.key.value='';startup=true;
  try{await audioContext().resume();}catch(e){}
  const url=WS_ROOT+'?key='+encodeURIComponent(key);
  liveState('GEMINI LIVE — KAPCSOLÓDÁS...');
  try{
   socket=new WebSocket(url);
   socket.onopen=()=>{
    const setup={model:MODEL,responseModalities:['AUDIO'],
     systemInstruction:{parts:[{text:'JARVIS vagy, gyors magyar nyelvű hangasszisztens. Válaszolj magyarul, világosan és röviden. Egyetlen telefonműveletet se állíts végrehajtottnak megerősítés nélkül. '+safety}]},
     outputAudioTranscription:{},inputAudioTranscription:{},
     tools:[{functionDeclarations:[{name:'phone_action',description:'Telefonművelet kérése a helyi Android JARVIS-vezérlőtől. A kérés nem bizonyítja a tényleges végrehajtást.',parameters:{type:'OBJECT',properties:{action:{type:'STRING',enum:SAFE_ACTIONS},target:{type:'STRING',description:'Alkalmazásnév, képernyőfelirat vagy görgetési irány'}},required:['action']}}]}]};
    jsonSend({setup});
   };
   socket.onmessage=e=>incoming(e.data);
   socket.onerror=()=>liveState('GEMINI LIVE — HÁLÓZATI / KULCS HIBA');
   socket.onclose=e=>{if(socket){socket=null;stopMicrophone(false);connected=false;startup=false;ui.connect.textContent='GEMINI LIVE INDÍTÁSA';liveState('Live kapcsolat lezárult (kód: '+e.code+'). Ellenőrizd a kulcsot és az API-jogosultságot.');}};
  }catch(e){teardown();liveState('Live indítás sikertelen: '+String(e.message||e));}
 }
 function install(){
  const box=document.createElement('section');box.className='panel';box.style.cssText='margin:8px auto 18px;max-width:850px;padding:14px;border:1px solid #2a6474;background:#081d29';
  const heading=document.createElement('div');heading.className='label';heading.textContent='GEMINI LIVE — ONLINE HANGMOTOR';box.append(heading);
  const tip=document.createElement('p');tip.style.cssText='font-size:11px;color:#91bbc8;line-height:1.5';tip.textContent='Az élő hangkapcsolathoz internet és külön Gemini API-kulcs szükséges. A kulcsot nem mentjük az alkalmazás tárhelyére. A régi JARVIS telefonvezérlője változatlan marad.';box.append(tip);
  const row=document.createElement('div');row.style.cssText='display:flex;gap:8px;flex-wrap:wrap';
  const key=document.createElement('input');key.type='password';key.autocomplete='off';key.placeholder='Gemini API-kulcs (AI Studio)';key.style.cssText='min-width:180px;flex:1;background:#061017;color:#d5f7ff;border:1px solid #46879b;padding:10px';row.append(key);ui.key=key;
  const connectBtn=document.createElement('button');connectBtn.textContent='GEMINI LIVE INDÍTÁSA';row.append(connectBtn);ui.connect=connectBtn;connectBtn.addEventListener('click',connect);
  const micBtn=document.createElement('button');micBtn.textContent='ÉLŐ MIKROFON';row.append(micBtn);ui.mic=micBtn;micBtn.onclick=async()=>{
   if(!connected){liveState('Előbb kapcsolódj Gemini Live-hoz.');return;}
   if(micEnabled){stopMicrophone(true);announce('GEMINI LIVE — MIKROFON LEÁLLÍTVA');return;}
   try{await startMicrophone();}catch(e){stopMicrophone(false);liveState('Mikrofon nem indítható: '+String(e.message||e));}
  };
  box.append(row);
  const status=document.createElement('div');status.textContent='NINCS LIVE KAPCSOLAT';status.style.cssText='font:11px ui-monospace,monospace;color:#64eaff;margin-top:10px';box.append(status);ui.status=status;
  ui.panel=box;
  box.style.display='none';
  const launch=document.createElement('button');launch.textContent='◉ LIVE';launch.style.cssText='font-size:11px;padding:7px 9px;color:#7ceaff;border-color:#27586a';
  launch.addEventListener('click',()=>{box.style.display=box.style.display==='none'?'block':'none';});ui.launch=launch;
  const settings=$('settings');if(settings?.parentNode)settings.parentNode.insertBefore(launch,settings);
  else document.querySelector('.top')?.append(launch);
  const top=document.querySelector('.top');
  if(top?.parentNode)top.parentNode.insertBefore(box,top.nextSibling);
  else document.querySelector('main')?.append(box);
  const form=$('form');
  form?.addEventListener('submit',e=>{if(!connected)return;e.preventDefault();e.stopImmediatePropagation();const input=$('input');const value=input?.value||'';if(input)input.value='';sendText(value);},true);
  const originalMic=$('mic');originalMic?.addEventListener('click',e=>{
   if(!connected)return;e.preventDefault();e.stopImmediatePropagation();micBtn.click();
  },true);
  window.addEventListener('beforeunload',teardown);
  document.addEventListener('visibilitychange',()=>{if(document.hidden&&micEnabled)stopMicrophone(true);});
 }
 if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',install,{once:true});else install();
 window.JARVISGeminiLive={sendText,start:connect,stop:teardown,get connected(){return connected}};
})();