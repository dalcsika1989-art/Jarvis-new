package com.jarvis.hud;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.os.Bundle;
import android.os.BatteryManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.JavascriptInterface;
import java.util.ArrayList;
import java.util.Locale;
public class MainActivity extends Activity {
 WebView web; TextToSpeech tts; static final int SPEECH=42;
 @Override public void onCreate(Bundle b){super.onCreate(b);web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"JarvisNative");web.loadUrl("file:///android_asset/index.html");tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS)tts.setLanguage(new Locale("hu","HU"));});((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis","JARVIS Status",NotificationManager.IMPORTANCE_DEFAULT));}
 public class Bridge {
  @JavascriptInterface public void listen(){runOnUiThread(()->{try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);startActivityForResult(i,SPEECH);}catch(Exception e){send("A beszédfelismerés nem érhető el.");}});}
  @JavascriptInterface public void speak(String s){runOnUiThread(()->tts.speak(s,TextToSpeech.QUEUE_FLUSH,null,"jarvis"));}
  @JavascriptInterface public void stopSpeech(){runOnUiThread(()->tts.stop());}
  @JavascriptInterface public void openUrl(String url){runOnUiThread(()->{try{Uri u=Uri.parse(url);if(!"https".equals(u.getScheme()))throw new Exception();startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){send("Csak HTTPS hivatkozás nyitható.");}});}
  @JavascriptInterface public void openApp(String pkg){runOnUiThread(()->{try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)throw new Exception();startActivity(i);}catch(Exception e){send("Az alkalmazás nem érhető el.");}});}
  @JavascriptInterface public String battery(){BatteryManager b=(BatteryManager)getSystemService(BATTERY_SERVICE);return String.valueOf(b.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY));}
  @JavascriptInterface public boolean accessibilityReady(){return JarvisAccessibility.instance!=null;}
  @JavascriptInterface public void accessibilitySettings(){runOnUiThread(()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));}
  @JavascriptInterface public void action(String type,String value){runOnUiThread(()->{JarvisAccessibility a=JarvisAccessibility.instance;if(a==null){send("Accessibility nincs engedélyezve.");return;}boolean ok=false;switch(type){case "click":ok=a.clickText(value);break;case "scroll":ok=a.scroll();break;case "input":ok=a.input(value);break;case "back":ok=a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);break;case "home":ok=a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);break;}send(ok?"Művelet végrehajtva.":"Nem sikerült a művelet. Ellenőrizd a képernyőt.");});}
  @JavascriptInterface public void notifyStatus(String s){runOnUiThread(()->{Notification n=new Notification.Builder(MainActivity.this,"jarvis").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("JARVIS HUD").setContentText(s).build();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(100,n);});}
 }
 void send(String s){runOnUiThread(()->web.evaluateJavascript("window.jarvisResult("+org.json.JSONObject.quote(s)+")",null));}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==SPEECH && c==RESULT_OK && d!=null){ArrayList<String> a=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(a!=null&&!a.isEmpty())send(a.get(0));}}
 @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}