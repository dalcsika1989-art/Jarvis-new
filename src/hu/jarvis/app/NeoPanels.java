package hu.jarvis.app;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Locale;
public final class NeoPanels {
 private static final int CYAN=0xff20d8ff,BG=0xff050b17;
 private NeoPanels(){}
 private static TextView label(Context c,String text,int size){TextView t=new TextView(c);t.setText(text);t.setTextSize(size);t.setTextColor(CYAN);t.setPadding(14,13,10,13);return t;}
 private static LinearLayout column(Context c){LinearLayout l=new LinearLayout(c);l.setOrientation(1);l.setPadding(12,12,12,12);l.setBackgroundColor(BG);return l;}
 private static void item(LinearLayout l,String name,Runnable run){TextView t=label(l.getContext(),"◉   "+name,17);t.setBackground(makeBorder());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,6,0,6);l.addView(t,p);t.setOnClickListener(v->run.run());}
 private static android.graphics.drawable.GradientDrawable makeBorder(){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(0xff070f1e);d.setCornerRadius(13);d.setStroke(2,0xff117d9e);return d;}
 private static void launch(Context c,String action){try{c.startActivity(new Intent(action));}catch(Exception e){Toast.makeText(c,"Nem elérhető: "+e.getMessage(),Toast.LENGTH_SHORT).show();}}
 public static void show(Activity a,String name,Runnable home){
  LinearLayout root=column(a);
  TextView title=label(a,"‹  "+name.toUpperCase(Locale.ROOT),23);root.addView(title);title.setOnClickListener(v->home.run());
  ScrollView scroll=new ScrollView(a);LinearLayout body=column(a);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  switch(name){
   case "GLOBE":
    body.addView(new NeoGlobeView(a),new LinearLayout.LayoutParams(-1,(int)(a.getResources().getDisplayMetrics().density*330)));
    body.addView(label(a,"Animált földgömb: vizualizáció, nem élő műholdas adat.",14));
    break;
   case "NATASHA":
    body.addView(new NatashaView(a),new LinearLayout.LayoutParams(-1,(int)(a.getResources().getDisplayMetrics().density*430)));
    body.addView(label(a,"Holografikus megjelenítés. A beszélgetéshez válaszd az AI CHAT menüt.",14));
    break;
   case "FILES":
    File f=Environment.getExternalStorageDirectory();
    item(body,"PHONE STORAGE  •  "+(f.getUsableSpace()/1048576)+" MB FREE",()->launch(a,Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    item(body,"SELECT LOCAL GGUF MODEL",()->hu.jarvis.local.ModelInstaller.pick(a));
    item(body,"OPEN ANDROID FILE MANAGER",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(i,406);});
    break;
   case "CONTROL":
    SeekBar brightness=new SeekBar(a);brightness.setMax(255);try{brightness.setProgress(Settings.System.getInt(a.getContentResolver(),Settings.System.SCREEN_BRIGHTNESS));}catch(Exception ignored){}
    body.addView(label(a,"BRIGHTNESS (APP WINDOW)",17));body.addView(brightness);
    brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u){WindowManager.LayoutParams w=a.getWindow().getAttributes();w.screenBrightness=Math.max(.01f,p/255f);a.getWindow().setAttributes(w);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    AudioManager audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
    body.addView(label(a,"MEDIA VOLUME",17));SeekBar volume=new SeekBar(a);volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));body.addView(volume);
    volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)audio.setStreamVolume(AudioManager.STREAM_MUSIC,p,0);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
    item(body,"WI-FI SETTINGS",()->launch(a,Settings.ACTION_WIFI_SETTINGS));
    item(body,"BLUETOOTH SETTINGS",()->launch(a,Settings.ACTION_BLUETOOTH_SETTINGS));
    item(body,"DISPLAY SETTINGS",()->launch(a,Settings.ACTION_DISPLAY_SETTINGS));
    item(body,"LOCATION SETTINGS",()->launch(a,Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    break;
   case "QUICK":
    item(body,"JARVIS INTELLIGENCE / LOCAL MODEL",()->hu.jarvis.local.ModelInstaller.pick(a));
    item(body,"SET ALARM",()->launch(a,"android.intent.action.SET_ALARM"));
    item(body,"SET REMINDER / CALENDAR",()->launch(a,"android.intent.action.INSERT"));
    item(body,"VOICE SETTINGS",()->launch(a,"com.android.settings.TTS_SETTINGS"));
    item(body,"ACCESSIBILITY CONTROL",()->launch(a,Settings.ACTION_ACCESSIBILITY_SETTINGS));
    break;
   case "MEDIA":
    item(body,"OPEN AUDIO FILE",()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);a.startActivityForResult(i,407);});
    item(body,"ANDROID AUDIO SETTINGS",()->launch(a,Settings.ACTION_SOUND_SETTINGS));
    break;
   case "SETTINGS":
    item(body,"ACCESSIBILITY AUTOPILOT",()->launch(a,Settings.ACTION_ACCESSIBILITY_SETTINGS));
    item(body,"APP PERMISSIONS",()->{Intent i=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName()));a.startActivity(i);});
    item(body,"LOCATION",()->launch(a,Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    item(body,"HOTSPOT SETTINGS",()->launch(a,Settings.ACTION_WIRELESS_SETTINGS));
    item(body,"POWER SAVING",()->launch(a,Settings.ACTION_BATTERY_SAVER_SETTINGS));
    item(body,"DISPLAY / DARK MODE",()->launch(a,Settings.ACTION_DISPLAY_SETTINGS));
    item(body,"NOTIFICATION SETTINGS",()->launch(a,Settings.ACTION_APP_NOTIFICATION_SETTINGS));
    break;
   default:
    item(body,"JARVIS DASHBOARD",home);
  }
  TextView back=label(a,"⌂  BACK TO DASHBOARD",16);root.addView(back);back.setOnClickListener(v->home.run());
  a.setContentView(root);
 }
}
