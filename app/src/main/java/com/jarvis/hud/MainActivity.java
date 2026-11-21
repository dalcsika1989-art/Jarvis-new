package com.jarvis.hud;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MainActivity extends Activity {
 private WebView web;
 private TextToSpeech tts;
 private boolean ttsReady=false;
 private final ExecutorService aiPool=Executors.newSingleThreadExecutor();
 private Future<?> aiWork;
 private SecureStore vault;
 private SpeechRecognizer wakeRecognizer;
 private boolean wakeMode=false;
 private int wakeErrors=0;
 private final Handler wakeHandler=new Handler(Looper.getMainLooper());
 private final Runnable restartWake=new Runnable(){@Override public void run(){beginWakeSession();}};
 private void beginWakeSession(){
  if(!wakeMode||wakeRecognizer==null)return;
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){stopWake();return;}
  Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");
  i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
  try{wakeRecognizer.startListening(i);}catch(Exception e){stopWake();jsCall("jarvisWakeStatus","error","A mikrofon nem indítható.");}
 }
 private void stopWake(){
  wakeMode=false;wakeHandler.removeCallbacks(restartWake);
  if(wakeRecognizer!=null){try{wakeRecognizer.cancel();wakeRecognizer.destroy();}catch(Exception ignored){}wakeRecognizer=null;}
  jsCall("jarvisWakeStatus","off","Ébresztőszó mód leállt.");
 }
 private final RecognitionListener wakeListener=new RecognitionListener(){
  @Override public void onReadyForSpeech(Bundle p){jsCall("jarvisWakeStatus","listening","JARVIS ébresztőszó figyelés aktív.");}
  @Override public void onBeginningOfSpeech(){}
  @Override public void onRmsChanged(float rms){}
  @Override public void onBufferReceived(byte[] data){}
  @Override public void onEndOfSpeech(){}
  @Override public void onError(int code){
   if(!wakeMode)return;
   wakeErrors++;
   if(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS||wakeErrors>=6){
    stopWake();jsCall("jarvisWakeStatus","error","A beszédfelismerő leállt. Kód: "+code);return;
   }
   wakeHandler.postDelayed(restartWake,Math.min(5000,400*wakeErrors));
  }
  @Override public void onResults(Bundle data){
   if(!wakeMode)return;
   ArrayList<String> phrases=data.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
   if(phrases!=null&&!phrases.isEmpty()){
    String heard=phrases.get(0).trim();
    int found=heard.toLowerCase(Locale.ROOT).indexOf("jarvis");
    if(found>=0&&found<=5){
     String command=heard.substring(found+6).trim();
     stopWake();
     if(command.isEmpty())jsCall("jarvisWakeStatus","ready","Jarvis felismerve; nyomd meg a mikrofont a parancshoz.");
     else jsCall("jarvisWakeCommand",command);
     return;
    }
   }
   wakeErrors=0;wakeHandler.postDelayed(restartWake,700);
  }
  @Override public void onPartialResults(Bundle partial){}
  @Override public void onEvent(int event,Bundle params){}
 };

 private static final int SPEECH=42;
 private static final int NOTIFICATION_REQUEST=43;
 private static final String START_PAGE="file:///android_asset/index.html";
 @Override public void onCreate(Bundle saved){
  super.onCreate(saved);
  vault=new SecureStore(this);
  web=new WebView(this);
  setContentView(web);
  web.getSettings().setJavaScriptEnabled(true);
  web.getSettings().setDomStorageEnabled(true);
  web.getSettings().setAllowFileAccess(true);
  web.getSettings().setAllowFileAccessFromFileURLs(false);
  web.getSettings().setAllowUniversalAccessFromFileURLs(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest req){
    Uri u=req.getUrl();if(START_PAGE.equals(u.toString()))return false;
    if("https".equals(u.getScheme())){startActivity(new Intent(Intent.ACTION_VIEW,u));}return true;
   }
  });
  web.addJavascriptInterface(new Bridge(),"JarvisNative");
  web.loadUrl(START_PAGE);
  tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS&&tts!=null){int code=tts.setLanguage(new Locale("hu","HU"));ttsReady=code!=TextToSpeech.LANG_MISSING_DATA&&code!=TextToSpeech.LANG_NOT_SUPPORTED;}});
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis","JARVIS feladatjelentések",NotificationManager.IMPORTANCE_DEFAULT));
 }
 private void jsCall(String name,String... args){
  runOnUiThread(()->{
   if(web==null)return;
   StringBuilder s=new StringBuilder("if(typeof window."+name+"==='function')window."+name+"(");
   for(int i=0;i<args.length;i++){if(i>0)s.append(',');s.append(JSONObject.quote(args[i]==null?"":args[i]));}s.append(");");
   web.evaluateJavascript(s.toString(),null);
  });
 }
 public class Bridge{
  @JavascriptInterface public void wakeMode(boolean enabled){runOnUiThread(()->{
   if(!enabled){stopWake();return;}
   if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44);
    jsCall("jarvisWakeStatus","permission","Mikrofonengedély szükséges. Engedélyezés után kapcsold be ismét.");
    return;
   }
   if(!SpeechRecognizer.isRecognitionAvailable(MainActivity.this)){
    jsCall("jarvisWakeStatus","error","A készüléken nincs elérhető beszédfelismerő.");return;
   }
   stopWake();
   try{
    wakeRecognizer=SpeechRecognizer.createSpeechRecognizer(MainActivity.this);
    wakeRecognizer.setRecognitionListener(wakeListener);
    wakeErrors=0;wakeMode=true;beginWakeSession();
   }catch(Exception e){stopWake();jsCall("jarvisWakeStatus","error",e.getMessage());}
  });}
  @JavascriptInterface public void listen(){
   runOnUiThread(()->{try{
    Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_PROMPT,"JARVIS hallgatja a parancsot");
    startActivityForResult(i,SPEECH);
   }catch(Exception e){jsCall("jarvisVoiceResult","", "Nem érhető el a rendszer beszédfelismerője.");}});
  }
  @JavascriptInterface public void speak(String text,float pitch){runOnUiThread(()->{
   if(!ttsReady){jsCall("jarvisSpeechStatus","error");return;}
   tts.setPitch(Math.max(0.55f,Math.min(1.4f,pitch)));
   tts.speak(text.length()>1400?text.substring(0,1400):text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-speech");
   jsCall("jarvisSpeechStatus","started");
  });}
  @JavascriptInterface public void stopSpeech(){runOnUiThread(()->{if(tts!=null)tts.stop();jsCall("jarvisSpeechStatus","stopped");});}
  @JavascriptInterface public void saveKey(String provider,String value){try{vault.put(provider,value);jsCall("jarvisSystemMessage","API kulcs biztonságosan mentve: "+provider);}catch(Exception e){jsCall("jarvisSystemMessage","Kulcstárolási hiba: "+e.getMessage());}}
  @JavascriptInterface public boolean hasKey(String provider){return vault.has(provider);}
  @JavascriptInterface public void askAi(String provider,String model,String historyJson,String reqId){
   if(aiWork!=null)aiWork.cancel(true);
   aiWork=aiPool.submit(()->{try{
    String key=vault.get(provider);
    String response=AiGateway.request(provider,model,key,historyJson);
    if(!Thread.currentThread().isInterrupted())jsCall("jarvisAiResult",reqId,"ok",response);
   }catch(Exception e){if(!Thread.currentThread().isInterrupted())jsCall("jarvisAiResult",reqId,"error",e.getMessage());}});
  }
  @JavascriptInterface public void research(String question,String id){
   aiWork=aiPool.submit(()->{try{
    String result=ResearchGateway.search(vault.get("gemini"),question);
    if(!Thread.currentThread().isInterrupted())jsCall("jarvisResearchResult",id,"ok",result);
   }catch(Exception e){if(!Thread.currentThread().isInterrupted())jsCall("jarvisResearchResult",id,"error",e.getMessage());}});
  }
  @JavascriptInterface public void cancelAi(){if(aiWork!=null)aiWork.cancel(true);}
  @JavascriptInterface public void openUrl(String url){runOnUiThread(()->{
   try{Uri u=Uri.parse(url);if(!"https".equals(u.getScheme()))throw new IllegalArgumentException("HTTPS szükséges.");
    startActivity(new Intent(Intent.ACTION_VIEW,u));jsCall("jarvisSystemMessage","Böngésző megnyitva.");
   }catch(Exception e){jsCall("jarvisSystemMessage","Hivatkozás hiba: "+e.getMessage());}
  });}
  @JavascriptInterface public void startAgent(String json){runOnUiThread(()->{
   if(json==null||json.length()>7000){jsCall("jarvisSystemMessage","A műveleti terv túl hosszú.");return;}
   try{
    Intent task=new Intent(MainActivity.this,MissionAgentService.class);
    task.setAction(MissionAgentService.ACTION_START);task.putExtra("steps",json);
    startForegroundService(task);jsCall("jarvisSystemMessage","Natív Mission Control elindítva.");
   }catch(Exception ex){jsCall("jarvisSystemMessage","Mission Control indítási hiba: "+ex.getMessage());}
  });}
  @JavascriptInterface public String agentStatus(){return MissionAgentService.getStatus(MainActivity.this);}
  @JavascriptInterface public void stopAgent(){runOnUiThread(()->{
   Intent task=new Intent(MainActivity.this,MissionAgentService.class);
   task.setAction(MissionAgentService.ACTION_STOP);startService(task);
  });}
  @JavascriptInterface public void launchApp(String pkg,String id){runOnUiThread(()->{
   try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
    if(i==null)throw new IllegalStateException("Alkalmazás nem telepített / nem látható");
    startActivity(i);jsCall("jarvisActionResult",id,"ok","Alkalmazás indítása elküldve: "+pkg);
   }catch(Exception e){jsCall("jarvisActionResult",id,"error","App indítás sikertelen: "+e.getMessage());}
  });}
  @JavascriptInterface public void openApp(String pkg){runOnUiThread(()->{
   try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);
    if(i==null)throw new IllegalStateException("Nincs telepítve / nem látható");
    startActivity(i);jsCall("jarvisSystemMessage","Alkalmazás megnyitva: "+pkg);
   }catch(Exception e){jsCall("jarvisSystemMessage","Alkalmazás hiba: "+e.getMessage());}
  });}
  @JavascriptInterface public void accessibilitySettings(){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));}
  @JavascriptInterface public boolean accessibilityReady(){return JarvisAccessibility.instance!=null;}
  @JavascriptInterface public void action(String type,String value,String reqId){runOnUiThread(()->{
   JarvisAccessibility svc=JarvisAccessibility.instance;
   if(svc==null){jsCall("jarvisActionResult",reqId,"error","Az Accessibility nincs engedélyezve.");return;}
   boolean ok=false;String detail="";
   try{
    switch(type){
     case "click":ok=svc.clickText(value);detail="Szöveg kiválasztása: "+value;break;
     case "scroll_down":ok=svc.scroll(false);detail="Görgetés lefelé";break;
     case "scroll_up":ok=svc.scroll(true);detail="Görgetés felfelé";break;
     case "input":ok=svc.input(value);detail="Szövegbevitel";break;
     case "back":ok=svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);detail="Vissza";break;
     case "home":ok=svc.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);detail="Főképernyő";break;
     case "read":detail=svc.readScreen();ok=!detail.isEmpty();break;
     default:detail="Ismeretlen művelet";
    }
   }catch(Exception e){detail="Android művelet hiba: "+e.getMessage();}
   jsCall("jarvisActionResult",reqId,ok?"ok":"error",detail);
  });}
  @JavascriptInterface public String diagnostics(){
   try{
    JSONObject d=new JSONObject();
    BatteryManager bat=(BatteryManager)getSystemService(BATTERY_SERVICE);
    ActivityManager mgr=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
    ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();mgr.getMemoryInfo(mem);
    d.put("device",Build.MANUFACTURER+" "+Build.MODEL);
    d.put("sdk",Build.VERSION.SDK_INT);
    d.put("battery",bat.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));
    d.put("availableRAM_MB",mem.availMem/1048576);
    d.put("accessibility",JarvisAccessibility.instance!=null);
    d.put("overlay",Build.VERSION.SDK_INT<23||Settings.canDrawOverlays(MainActivity.this));
    d.put("tts",ttsReady);
    d.put("openai_key",vault.has("openai"));
    d.put("gemini_key",vault.has("gemini"));
    d.put("notifications",Build.VERSION.SDK_INT<33||checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED);
    return d.toString();
   }catch(Exception e){return "{\"error\":\"diagnostics failed\"}";}
  }
  @JavascriptInterface public void screenshot(String id){runOnUiThread(()->{
   JarvisAccessibility service=JarvisAccessibility.instance;
   if(service==null){jsCall("jarvisScreenshotResult",id,"error","Accessibility nincs engedélyezve.");return;}
   service.screenshot(payload->{
    if(payload.startsWith("ERROR:"))jsCall("jarvisScreenshotResult",id,"error",payload.substring(6));
    else jsCall("jarvisScreenshotResult",id,"ok",payload);
   });
  });}
  @JavascriptInterface public void requestNotifications(){if(Build.VERSION.SDK_INT>=33){runOnUiThread(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_REQUEST));}}
  @JavascriptInterface public void notifyStatus(String text){runOnUiThread(()->{
   if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){jsCall("jarvisSystemMessage","Értesítési engedély szükséges.");return;}
   Notification n=new Notification.Builder(MainActivity.this,"jarvis").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("JARVIS HUD").setContentText(text.length()>180?text.substring(0,180):text).build();
   ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(100,n);
   jsCall("jarvisSystemMessage","Android értesítés elküldve. Garmin tükrözés a Garmin Connect beállításaitól függ.");
  });}
  @JavascriptInterface public void overlay(boolean enable){runOnUiThread(()->{
   if(enable){
    if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(MainActivity.this)){
     Intent i=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
     startActivity(i);
     jsCall("jarvisSystemMessage","Engedélyezd a más alkalmazások feletti megjelenítést, majd aktiváld ismét.");
     return;
    }
    try{Intent i=new Intent(MainActivity.this,OverlayService.class);startForegroundService(i);jsCall("jarvisSystemMessage","Lebegő JARVIS HUD elindítva.");}catch(Exception e){jsCall("jarvisSystemMessage","HUD hiba: "+e.getMessage());}
   }else{stopService(new Intent(MainActivity.this,OverlayService.class));jsCall("jarvisSystemMessage","Lebegő HUD leállítva.");}
  });}
 }
 @Override protected void onActivityResult(int r,int code,Intent data){
  super.onActivityResult(r,code,data);
  if(r==SPEECH){ArrayList<String> out=(code==RESULT_OK&&data!=null)?data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS):null;
   if(out!=null&&!out.isEmpty())jsCall("jarvisVoiceResult",out.get(0),"");
   else jsCall("jarvisVoiceResult","","A beszédfelismerés megszakadt vagy nem sikerült.");
  }
 }
 @Override protected void onPause(){super.onPause();if(wakeMode)stopWake();}
 @Override protected void onDestroy(){stopWake();if(aiWork!=null)aiWork.cancel(true);aiPool.shutdownNow();if(tts!=null){tts.stop();tts.shutdown();}if(web!=null){web.removeJavascriptInterface("JarvisNative");web.destroy();}super.onDestroy();}
}