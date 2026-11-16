package hu.jarvis.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Context;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
 private static final String ORIGIN="https://jarvis.local";
 private static final int REQ_AUDIO=620;
 private final Handler ui=new Handler(Looper.getMainLooper());
 private WebView web;
 private PermissionRequest waitingAudio;
 private SpeechRecognizer recognizer;
 private boolean muted;
 private boolean userMode=true;
 private String recentTask="";
 private final NativeBridge bridge=new NativeBridge();

 @Override public void onCreate(Bundle state){
  super.onCreate(state);
  web=new WebView(this);
  web.setBackgroundColor(0xff040c13);
  WebSettings settings=web.getSettings();
  settings.setJavaScriptEnabled(true);
  settings.setDomStorageEnabled(true);
  settings.setAllowFileAccess(false);
  settings.setAllowContentAccess(false);
  settings.setJavaScriptCanOpenWindowsAutomatically(false);
  settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
  settings.setMediaPlaybackRequiresUserGesture(false);
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
    Uri uri=req.getUrl();
    if(!"https".equals(uri.getScheme())||!"jarvis.local".equals(uri.getHost()))return null;
    String path=uri.getPath();
    String asset="/".equals(path)||"/index.html".equals(path)?"index.html":
                 "/gemini-live.js".equals(path)?"gemini-live.js":null;
    if(asset==null)return new WebResourceResponse("text/plain","UTF-8",403,"Forbidden",
            java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
    try{
     InputStream in=getAssets().open(asset);
     return new WebResourceResponse(asset.endsWith(".js")?"text/javascript":"text/html","UTF-8",in);
    }catch(IOException ex){return null;}
   }
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){
    Uri uri=req.getUrl();
    if("https".equals(uri.getScheme())&&"jarvis.local".equals(uri.getHost()))return false;
    if(req.isForMainFrame()){
     try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception ignored){}
    }
    return true;
   }
  });
  web.setWebChromeClient(new WebChromeClient(){
   @Override public void onPermissionRequest(PermissionRequest request){
    runOnUiThread(()->{
     if(!ORIGIN.equals(request.getOrigin().toString().replaceAll("/$",""))){request.deny();return;}
     String[] resources=request.getResources();
     if(resources.length!=1 || !PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resources[0])){
      request.deny();return;
     }
     if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED){
      request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
     }else{
      if(waitingAudio!=null)waitingAudio.deny();
      waitingAudio=request;
      requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
     }
    });
   }
   @Override public void onPermissionRequestCanceled(PermissionRequest request){
    if(waitingAudio==request)waitingAudio=null;
   }
  });
  web.addJavascriptInterface(bridge,"Native");
  setContentView(web);
  web.loadUrl(ORIGIN+"/index.html");
 }
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){
  super.onRequestPermissionsResult(requestCode,permissions,results);
  if(requestCode==REQ_AUDIO&&waitingAudio!=null){
   PermissionRequest p=waitingAudio;waitingAudio=null;
   if(results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)
    p.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
   else p.deny();
  }
 }
 private void event(String type,String value){
  ui.post(()->{
   if(web!=null)web.evaluateJavascript("window.nativeEvent&&window.nativeEvent("+
      org.json.JSONObject.quote(type)+","+org.json.JSONObject.quote(value)+")",null);
  });
 }
 private void local(String command){
  if(command==null||command.trim().isEmpty())return;
  recentTask=command.trim();
  if(ControlService.executeLocalCommand(recentTask)){
   event("agent","Parancs átvéve: "+recentTask+". A képernyős teljesítés külön ellenőrzendő.");
  }else if(ControlService.looksLikeLocalCommand(recentTask)){
   event("error","Kapcsold be a JARVIS hozzáférési szolgáltatást az Android kisegítő lehetőségeknél.");
  }else{
   event("reply","A JARVIS Live telefonhíd ezzel a paranccsal nem tud biztonságosan műveletet indítani. A Gemini Live beszélgetés használható.");
  }
 }
 public final class NativeBridge {
  @JavascriptInterface public void send(String text){ui.post(()->local(text));}
  @JavascriptInterface public void setMode(boolean enabled){userMode=enabled;event("mode",String.valueOf(enabled));}
  @JavascriptInterface public void mute(boolean enabled){muted=enabled;event("muted",String.valueOf(enabled));}
  @JavascriptInterface public void stop(){ui.post(()->{ControlService.cancelCurrentTask();stopRecognizer();if(web!=null)web.evaluateJavascript("window.JARVISGeminiLive&&window.JARVISGeminiLive.stop()",null);event("state","Leállítva — Gemini kapcsolat bontva");});}
  @JavascriptInterface public void control(){
   ui.post(()->{try{startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));}
    catch(Exception e){event("error",e.getMessage());}});
  }
  @JavascriptInterface public void diagnostics(){
   event("agent","Android vezérlő: "+(ControlService.active()?"bekapcsolva":"kikapcsolva")+
     "; Gemini Live: a HUD LIVE kapcsolati állapota mérvadó.");
  }
  @JavascriptInterface public void settings(){event("agent","Gemini API-kulcsot a JARVIS LIVE paneljén adhatod meg. Nem mentjük el.");}
  @JavascriptInterface public void listen(){ui.post(()->startRecognizer());}
  @JavascriptInterface public void plan(){event("agent",ControlService.currentStatus());}
  @JavascriptInterface public void taskLog(){event("agent",ControlService.currentStatus());}
  @JavascriptInterface public void testVoice(){event("agent","Gemini Live hangot használj. Az eredeti offline hangmotor nem része a forrásvázlatnak.");}
  @JavascriptInterface public void notificationTest(){event("agent","Óraértesítés ehhez a kiadáshoz még nincs kész.");}
  @JavascriptInterface public void chatBridge(){event("agent","ChatGPT telefonkapcsolat ehhez a fejlesztési ághoz nincs hozzáadva.");}
 }
 private void startRecognizer(){
  if(!SpeechRecognizer.isRecognitionAvailable(this)){
   event("error","Nincs elérhető Android hangfelismerő.");return;
  }
  if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_AUDIO);
   event("error","Engedélyezd a mikrofont, és próbáld újra.");return;
  }
  stopRecognizer();
  recognizer=SpeechRecognizer.createSpeechRecognizer(this);
  recognizer.setRecognitionListener(new RecognitionListener(){
   public void onReadyForSpeech(Bundle params){event("state","Hallgatlak");}
   public void onBeginningOfSpeech(){}
   public void onRmsChanged(float rms){event("level",String.valueOf(rms));}
   public void onBufferReceived(byte[] buffer){}
   public void onEndOfSpeech(){}
   public void onError(int error){event("error","Hangfelismerés hibakód: "+error);}
   public void onResults(Bundle results){
    ArrayList<String> texts=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
    if(texts!=null&&!texts.isEmpty()){
     String text=texts.get(0);
     event("heard",text);
     // Do not automatically execute twice. Gemini Live's microphone uses WebAudio.
    }
   }
   public void onPartialResults(Bundle partial){}
   public void onEvent(int type,Bundle params){}
  });
  Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
  intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");
  intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
  recognizer.startListening(intent);
 }
 private void stopRecognizer(){
  if(recognizer!=null){recognizer.destroy();recognizer=null;}
 }
 @Override protected void onPause(){
  // A háttérbe helyezés nem tarthat fenn díjköteles Gemini Live sessiont.
  if(web!=null)web.evaluateJavascript("window.JARVISGeminiLive&&window.JARVISGeminiLive.stop()",null);
  super.onPause();
 }
 @Override protected void onStop(){
  if(web!=null)web.evaluateJavascript("window.JARVISGeminiLive&&window.JARVISGeminiLive.stop()",null);
  super.onStop();
 }
 @Override public void onDestroy(){
  if(web!=null)web.evaluateJavascript("window.JARVISGeminiLive&&window.JARVISGeminiLive.stop()",null);
  stopRecognizer();
  if(waitingAudio!=null){waitingAudio.deny();waitingAudio=null;}
  if(web!=null){web.removeJavascriptInterface("Native");web.destroy();web=null;}
  super.onDestroy();
 }
}
