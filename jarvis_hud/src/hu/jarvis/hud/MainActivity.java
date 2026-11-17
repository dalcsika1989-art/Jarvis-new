package hu.jarvis.hud;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.provider.Settings;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.WindowManager;
import android.widget.Toast;
public class MainActivity extends Activity {
    private WebView web;
    private boolean waitingForOverlay;
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        web=new WebView(this);
        web.setBackgroundColor(0xff020914);
        WebSettings s=web.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(true);
        s.setAllowContentAccess(false);s.setSupportZoom(false);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(),"JarvisNative");
        setContentView(web);web.loadUrl("file:///android_asset/index.html");
    }
    public class Bridge {
        @JavascriptInterface public void overlay() {
            runOnUiThread(new Runnable(){@Override public void run(){startOverlay();}});
        }
        @JavascriptInterface public void stopOverlay() {
            runOnUiThread(new Runnable(){@Override public void run(){stopService(new Intent(MainActivity.this,FloatingService.class));Toast.makeText(MainActivity.this,"JARVIS ikon kikapcsolva",Toast.LENGTH_SHORT).show();}});
        }
        @JavascriptInterface public int battery(){
            Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if(i==null)return 0;
            int level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
            return level<0||scale<=0?0:(int)(100.0f*level/scale);
        }
    }
    private void startOverlay(){
        if(!Settings.canDrawOverlays(this)){
            waitingForOverlay=true;
            Intent p=new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()));
            try{startActivity(p);}catch(Exception e){Toast.makeText(this,"A lebegő ablak engedélye szükséges",Toast.LENGTH_LONG).show();}
            return;
        }
        startService(new Intent(this,FloatingService.class));
        Toast.makeText(this,"JARVIS lebegő ikon aktív",Toast.LENGTH_SHORT).show();
    }
    @Override protected void onResume(){
        super.onResume();
        if(waitingForOverlay && Settings.canDrawOverlays(this)){
            waitingForOverlay=false;startOverlay();
        }
    }
    @Override protected void onDestroy(){
        if(web!=null){web.removeJavascriptInterface("JarvisNative");web.destroy();web=null;}
        super.onDestroy();
    }
}