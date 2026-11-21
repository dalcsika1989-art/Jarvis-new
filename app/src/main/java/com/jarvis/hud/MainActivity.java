package com.jarvis.hud;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.webkit.JavascriptInterface;
import android.content.Intent;
import android.net.Uri;
import android.speech.RecognizerIntent;
import java.util.ArrayList;
public class MainActivity extends Activity {
 WebView web; static final int SPEECH=42;
 @Override public void onCreate(Bundle b){super.onCreate(b);web=new WebView(this);setContentView(web);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.setWebViewClient(new WebViewClient());web.addJavascriptInterface(new Bridge(),"JarvisNative");web.loadUrl("file:///android_asset/index.html");}
 public class Bridge {
  @JavascriptInterface public void listen(){runOnUiThread(()->{try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);startActivityForResult(i,SPEECH);}catch(Exception e){send("A beszédfelismerés nem érhető el.");}});}
  @JavascriptInterface public void openUrl(String url){runOnUiThread(()->{try{Uri u=Uri.parse(url);if(!"https".equals(u.getScheme()))throw new Exception();startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){send("Csak HTTPS hivatkozás nyitható.");}});}
  @JavascriptInterface public void openApp(String pkg){runOnUiThread(()->{try{Intent i=getPackageManager().getLaunchIntentForPackage(pkg);if(i==null)throw new Exception();startActivity(i);}catch(Exception e){send("Az alkalmazás nem érhető el.");}});}
 }
 void send(String s){runOnUiThread(()->web.evaluateJavascript("window.jarvisResult("+org.json.JSONObject.quote(s)+")",null));}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==SPEECH && c==RESULT_OK && d!=null){ArrayList<String> a=d.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(a!=null&&!a.isEmpty())send(a.get(0));}}
}
