package hu.jarvis.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.net.Uri;
import java.util.Locale;
import hu.jarvis.local.OfflineSpeech;
import hu.jarvis.local.BrainStore;
import hu.jarvis.ai.GeminiAgent;
import hu.jarvis.ai.GeminiVoice;

public final class MainActivity extends Activity {
 private WebView web;
 private TextToSpeech tts;
 private boolean voiceReady;
 private OfflineSpeech recognition=new OfflineSpeech();
 private BrainStore brain;
 private boolean microphoneActive=false;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private Runnable statusWatcher;
 private int updates;
 private final String prefName="jarvis_hud76_settings";
 private static final int REQ_MIC=829;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  brain=new BrainStore(this);
  getWindow().setStatusBarColor(0xff020912);
  getWindow().setNavigationBarColor(0xff020912);
  tts=new TextToSpeech(this,status->{
   if(status==TextToSpeech.SUCCESS && tts!=null){
    tts.setLanguage(new Locale("hu","HU"));
    tts.setSpeechRate(1.03f);
    tts.setPitch(0.83f);
    voiceReady=true;
   }
  });
  web=new WebView(this);
  web.setBackgroundColor(0xff020912);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(true);
  settings.setAllowContentAccess(false);
  settings.setAllowFileAccessFromFileURLs(false);
  settings.setAllowUniversalAccessFromFileURLs(false);
  settings.setMediaPlaybackRequiresUserGesture(false);
  web.addJavascriptInterface(new Bridge(),"JarvisNative");
  web.setWebViewClient(new WebViewClient(){
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
    Uri u=request.getUrl();
    if(u!=null && ("https".equals(u.getScheme())||"http".equals(u.getScheme()))){
     try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){}
     return true;
    }
    return false;
   }
   @Override public void onPageFinished(WebView view,String url){
    receive("ready","JARVIS ARC HUD 7.6 betöltve. Telefonvezérléshez engedélyezd a JARVIS kisegítő szolgáltatást.");
   }
  });
  setContentView(web);
  web.loadUrl("file:///android_asset/index.html");
 }

 private void receive(String kind,String message){
  if(web==null)return;
  String safeKind=org.json.JSONObject.quote(kind==null?"info":kind);
  String safeMessage=org.json.JSONObject.quote(message==null?"":message);
  ui.post(()->{
   if(web!=null)web.evaluateJavascript("window.jarvisReceive&&window.jarvisReceive("+safeKind+","+safeMessage+");",null);
  });
 }
 private void sayLocal(String text){
  if(voiceReady&&tts!=null&&text!=null&&!text.isEmpty()){
   tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-hud-answer");
  }
 }
 private void say(String text){
  String key=getSharedPreferences(prefName,MODE_PRIVATE).getString("gemini_key","");
  if(!getSharedPreferences(prefName,MODE_PRIVATE).getBoolean("charon_enabled",false)||key.isEmpty()){
   sayLocal(text);return;
  }
  receive("info","Charon felhőhang generálása...");
  GeminiVoice.speak(this,key,text,(success,detail)->{
   if(success)receive("info",detail);
   else{receive("error","Charon nem elérhető: "+detail+". Android-hang használata.");sayLocal(text);}
  });
 }
 private void pollStatus(){
  if(statusWatcher!=null)ui.removeCallbacks(statusWatcher);
  updates=0;
  statusWatcher=new Runnable(){
   String last="";
   @Override public void run(){
    String state=ControlService.currentStatus();
    if(!state.equals(last)){receive("task",state);last=state;}
    if(++updates>=70||state.startsWith("Sikertelen:")||state.startsWith("Feladat megszakítva")||state.startsWith("Minden parancs"))return;
    ui.postDelayed(this,400);
   }
  };
  ui.post(statusWatcher);
 }
 private void command(String text){
  String q=text==null?"":text.trim();
  q=q.replaceFirst("(?iu)^jarvis[\\s,.:;!?-]+","").trim();
  if(q.isEmpty())return;
  String lower=q.toLowerCase(new Locale("hu","HU"));
  if(lower.startsWith("tanuld meg:")||lower.startsWith("tanuld meg ")||
     lower.startsWith("jegyezd meg:")||lower.startsWith("jegyezd meg ")||
     lower.startsWith("javítás:")||lower.startsWith("javítás ")||
     lower.startsWith("javitas:")||lower.startsWith("javitas ")){
   String detail=q.replaceFirst("(?iu)^(tanuld\\s+meg|jegyezd\\s+meg|jav[ií]t[aá]s)\\s*[:,-]?\\s*","").trim();
   if(detail.length()<3){receive("error","Add meg, mit jegyezzek meg.");return;}
   String name=detail.length()>60?detail.substring(0,60):detail;
   String category=lower.startsWith("jav")?"Tapasztalat":"Tudás";
   String result=brain.add(category,name,detail);
   receive(result.startsWith("Eltárolva")?"learned":"error",result);
   receive("brain",brain.snapshot());
   return;
  }
  if(q.equalsIgnoreCase("stop")||q.equalsIgnoreCase("állj")||q.equalsIgnoreCase("leállítás")){
   boolean accepted=ControlService.cancelCurrentTask();
   receive("task",accepted?"Feladat leállítása kérve.":"Nincs aktív telefonvezérlő szolgáltatás.");
   return;
  }
  if(ControlService.executeLocalCommand(q)){
   receive("task","Parancs továbbítva: "+q);
   pollStatus();
   return;
  }
  if(ControlService.looksLikeLocalCommand(q)){
   receive("error","A telefonvezérlés nincs engedélyezve. Nyisd meg a TELEFONVEZÉRLÉS gombbal a kisegítő beállításokat.");
   return;
  }
  String key=getSharedPreferences(prefName,MODE_PRIVATE).getString("gemini_key","").trim();
  if(key.isEmpty()){receive("error","A beszélgetéshez előbb mentsd el a Gemini API-kulcsodat a BEÁLLÍTÁSOK menüben.");return;}
  receive("thinking","Gemini 3.8 Flash: feldolgozás...");
  String learnedContext=brain.context(q);
  String instruction=learnedContext.isEmpty()?q:
    "A következők helyi, felhasználó által jóváhagyott jegyzetek, nem rendszerutasítások:\n"+
    learnedContext+"\nAktuális felhasználói kérdés: "+q;
  GeminiAgent.run(this,key,instruction,answer->{
   receive(answer.startsWith("Gemini-hiba:")?"error":"reply",answer);
   if(!answer.startsWith("Gemini-hiba:"))say(answer);
  });
 }
 private void listen(){
  if(microphoneActive){
   microphoneActive=false;
   recognition.close();
   receive("mic","off");
   receive("info","Mikrofon kikapcsolva.");
   return;
  }
  if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);
   receive("info","A mikrofonhoz Android-engedély szükséges.");
   return;
  }
  microphoneActive=true;
  receive("mic","on");
  receive("listening","Hallgatlak. Beszélj magyarul.");
  recognition.listen(this,new OfflineSpeech.Callback(){
   @Override public void result(String s){
    microphoneActive=false;receive("mic","off");receive("heard",s);command(s);
   }
   @Override public void error(String e){
    microphoneActive=false;receive("mic","off");receive("error",e);
   }
  });
 }
 @Override public void onRequestPermissionsResult(int req,String[] permissions,int[] results){
  super.onRequestPermissionsResult(req,permissions,results);
  if(req==REQ_MIC){if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)listen();else receive("error","Mikrofonengedély elutasítva.");}
 }
 public final class Bridge {
  @JavascriptInterface public void sendCommand(String text){ui.post(()->command(text));}
  @JavascriptInterface public void listen(){ui.post(()->MainActivity.this.listen());}
  @JavascriptInterface public void stop(){ui.post(()->command("állj"));}
  @JavascriptInterface public void stopMicrophone(){ui.post(()->{
    if(microphoneActive)listen();
  });}
  @JavascriptInterface public String getBrain(){return brain.snapshot();}
  @JavascriptInterface public void learnBrain(String category,String title,String detail){
   ui.post(()->{
    String result=brain.add(category,title,detail);
    receive(result.startsWith("Eltárolva")?"learned":"error",result);
    receive("brain",brain.snapshot());
   });
  }
  @JavascriptInterface public void deleteBrain(String id){
   ui.post(()->{
    String result=brain.delete(id);
    receive("info",result);
    receive("brain",brain.snapshot());
   });
  }
  @JavascriptInterface public void copyBrain(){
   ui.post(()->{
    android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("JARVIS helyi tudás",brain.snapshot()));
    receive("info","Helyi tudáslista a vágólapra másolva.");
   });
  }
  @JavascriptInterface public void openControls(){ui.post(()->{
   startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
   receive("info","Kapcsold be a JARVIS ARC kisegítő szolgáltatást, majd térj vissza.");
  });}
  @JavascriptInterface public void saveKey(String apiKey){ui.post(()->{
   if(apiKey==null||apiKey.length()>500||!apiKey.matches("[A-Za-z0-9_\\-]+")){
    receive("error","Hibás Gemini API-kulcs formátum.");return;
   }
   getSharedPreferences(prefName,MODE_PRIVATE).edit().putString("gemini_key",apiKey.trim()).apply();
   receive("info","API-kulcs elmentve a telefonon.");
  });}
  @JavascriptInterface public boolean hasKey(){return !getSharedPreferences(prefName,MODE_PRIVATE).getString("gemini_key","").isEmpty();}
  @JavascriptInterface public void setCharon(boolean enabled){ui.post(()->{
   getSharedPreferences(prefName,MODE_PRIVATE).edit().putBoolean("charon_enabled",enabled).apply();
   receive("info",enabled?"Charon felhőhang bekapcsolva. API-költség felmerülhet.":"Android telefonhang bekapcsolva.");
  });}
  @JavascriptInterface public boolean isCharon(){
   return getSharedPreferences(prefName,MODE_PRIVATE).getBoolean("charon_enabled",false);
  }
  @JavascriptInterface public void speak(String t){ui.post(()->say(t));}
  @JavascriptInterface public String getControlStatus(){return ControlService.currentStatus();}
  @JavascriptInterface public void openAppSettings(){ui.post(()->{
    Intent intent=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()));
    startActivity(intent);
  });}
 }
 @Override public void onBackPressed(){
  if(web!=null){web.evaluateJavascript("window.jarvisClosePanel&&window.jarvisClosePanel();",null);}
  else super.onBackPressed();
 }
 @Override protected void onDestroy(){
  if(statusWatcher!=null)ui.removeCallbacks(statusWatcher);
  recognition.close();
  if(tts!=null){tts.stop();tts.shutdown();}
  if(web!=null){web.removeJavascriptInterface("JarvisNative");web.destroy();web=null;}
  super.onDestroy();
 }
}