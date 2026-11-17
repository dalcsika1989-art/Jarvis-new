package hu.jarvis.hud;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.provider.Settings;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowManager;
import android.widget.Toast;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {
    private WebView web;
    private boolean waitingForOverlay;
    private String pendingPage = "home";
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if(getIntent()!=null && getIntent().hasExtra("jarvis_page"))
            pendingPage=getIntent().getStringExtra("jarvis_page");
        web=new WebView(this);
        web.setBackgroundColor(0xff020914);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient(){
          @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest req) {
            String path=req.getUrl().toString();
            return !(path.startsWith("file:///android_asset/")||path.equals("about:blank"));
          }
          @Override public void onPageFinished(WebView v,String url) {openPendingPage();}
        });
        web.addJavascriptInterface(new Bridge(),"JarvisNative");
        web.addJavascriptInterface(new TermuxClient(this,web),"JarvisTermux");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }
    private void openPendingPage(){
       if(web==null)return;
       final String page=pendingPage==null?"home":pendingPage;
       pendingPage=null;
       if(page.matches("home|chat|knowledge|apps|mini|settings|overlay|status|files"))
         web.evaluateJavascript("if(window.jarvisOpenPage)window.jarvisOpenPage('"+page+"');",null);
    }
    @Override protected void onNewIntent(Intent intent){
      super.onNewIntent(intent);
      setIntent(intent);
      if(intent!=null && intent.hasExtra("jarvis_page")){
          pendingPage=intent.getStringExtra("jarvis_page");
          openPendingPage();
      }
    }
    public class Bridge {
        @JavascriptInterface public void overlay() {
            runOnUiThread(new Runnable(){@Override public void run(){startOverlay();}});
        }
        @JavascriptInterface public void stopOverlay() {
            runOnUiThread(new Runnable(){@Override public void run(){
                stopService(new Intent(MainActivity.this,FloatingService.class));
                Toast.makeText(MainActivity.this,"JARVIS ikon kikapcsolva",Toast.LENGTH_SHORT).show();
            }});
        }
        @JavascriptInterface public int battery(){
            Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(i==null)return 0;
            int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            return level<0||scale<=0?0:(int)(100.0f*level/scale);
        }
        @JavascriptInterface public String memoryStats(){
            try{
                ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
                ActivityManager am=(ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
                am.getMemoryInfo(mi);
                JSONObject j=new JSONObject();
                j.put("total",mi.totalMem);j.put("available",mi.availMem);
                return j.toString();
            }catch(Exception e){return "{}";}
        }
        @JavascriptInterface public String listApps(){
            JSONArray array=new JSONArray();
            PackageManager pm=getPackageManager();
            Intent filter=new Intent(Intent.ACTION_MAIN,null);
            filter.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps=pm.queryIntentActivities(filter,0);
            HashSet<String> used=new HashSet<String>();
            int count=0;
            for(ResolveInfo info:apps){
               if(info.activityInfo==null)continue;
               String pkg=info.activityInfo.packageName;
               if(used.contains(pkg))continue;
               used.add(pkg);
               try{
                  JSONObject item=new JSONObject();
                  item.put("name",info.loadLabel(pm).toString());
                  item.put("pkg",pkg);
                  try{
                      Drawable drawable=info.loadIcon(pm);
                      Bitmap bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);
                      Canvas canvas=new Canvas(bitmap);
                      drawable.setBounds(0,0,64,64);
                      drawable.draw(canvas);
                      ByteArrayOutputStream bos=new ByteArrayOutputStream(5000);
                      bitmap.compress(Bitmap.CompressFormat.PNG,75,bos);
                      item.put("icon","data:image/png;base64,"+Base64.encodeToString(bos.toByteArray(),Base64.NO_WRAP));
                      bitmap.recycle();bos.close();
                  }catch(Throwable t){item.put("icon","");}
                  array.put(item);
                  if(++count>=160)break;
               }catch(Exception ignored){}
            }
            return array.toString();
        }
        @JavascriptInterface public void launchApp(final String pkg){
            runOnUiThread(new Runnable(){@Override public void run(){
                try{
                    Intent app=getPackageManager().getLaunchIntentForPackage(pkg);
                    if(app!=null)startActivity(app);
                    else Toast.makeText(MainActivity.this,"Nem indítható: "+pkg,Toast.LENGTH_LONG).show();
                }catch(Exception e){Toast.makeText(MainActivity.this,"Az alkalmazás nem indítható",Toast.LENGTH_SHORT).show();}
            }});
        }
    }
    private void startOverlay(){
        if(!Settings.canDrawOverlays(this)){
            waitingForOverlay=true;
            Intent p=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
            try{startActivity(p);}catch(Exception e){
                Toast.makeText(this,"A lebegő ablak engedélye szükséges",Toast.LENGTH_LONG).show();
            }
            return;
        }
        startService(new Intent(this,FloatingService.class));
        Toast.makeText(this,"JARVIS lebegő ikon aktív",Toast.LENGTH_SHORT).show();
    }
    @Override protected void onResume(){
        super.onResume();
        if(waitingForOverlay && Settings.canDrawOverlays(this)){
            waitingForOverlay=false;
            startOverlay();
        }
    }
    @Override protected void onDestroy(){
        if(web!=null){web.removeJavascriptInterface("JarvisNative");web.destroy();web=null;}
        super.onDestroy();
    }
}