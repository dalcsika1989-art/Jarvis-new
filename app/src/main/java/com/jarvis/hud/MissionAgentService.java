package com.jarvis.hud;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Executes user-approved, limited Android Accessibility steps while another app
 * is in the foreground. Stops instead of interacting with a locked screen.
 * No external messaging, financial operations or deletes are supported.
 */
public final class MissionAgentService extends Service {
 public static final String ACTION_START="com.jarvis.hud.AGENT_START";
 public static final String ACTION_STOP="com.jarvis.hud.AGENT_STOP";
 private static final String CHANNEL="jarvis_agent";
 private static final String STORE="jarvis_agent_state";
 private static final String[] PACKAGES={
  "com.android.chrome","com.google.android.youtube","com.facebook.orca",
  "com.whatsapp","com.instagram.android","com.sec.android.gallery3d"
 };
 private final Handler handler=new Handler(Looper.getMainLooper());
 private JSONArray steps;
 private int index=0,attempts=0;
 private boolean running=false;
 private final Runnable next=new Runnable(){@Override public void run(){executeStep();}};
 @Override public void onCreate(){
  super.onCreate();
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
   .createNotificationChannel(new NotificationChannel(CHANNEL,"JARVIS küldetések",NotificationManager.IMPORTANCE_LOW));
 }
 private Notification notification(String text){
  return new Notification.Builder(this,CHANNEL)
   .setSmallIcon(android.R.drawable.ic_dialog_info)
   .setContentTitle("JARVIS Mission Control")
   .setContentText(text)
   .setOnlyAlertOnce(true).setOngoing(true).build();
 }
 private void report(String phase,String detail){
  int n=steps==null?0:steps.length();
  JSONObject o=new JSONObject();
  try{o.put("phase",phase);o.put("index",index);o.put("total",n);o.put("detail",detail);}catch(Exception ignored){}
  getSharedPreferences(STORE,MODE_PRIVATE).edit().putString("status",o.toString()).apply();
  if(running) ((NotificationManager)getSystemService(NOTIFICATION_SERVICE))
   .notify(501,notification("Lépés "+index+"/"+n+": "+detail));
 }
 public static String getStatus(android.content.Context c){
  return c.getSharedPreferences(STORE,MODE_PRIVATE).getString("status",
   "{\"phase\":\"idle\",\"index\":0,\"total\":0,\"detail\":\"Nincs aktív küldetés\"}");
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(intent!=null&&ACTION_STOP.equals(intent.getAction())){halt("cancelled","Felhasználó leállította.");return START_NOT_STICKY;}
  if(intent==null||!ACTION_START.equals(intent.getAction()))return START_NOT_STICKY;
  String json=intent.getStringExtra("steps");
  try{
   JSONArray parsed=new JSONArray(json);
   if(parsed.length()<1||parsed.length()>12)throw new IllegalArgumentException("1–12 lépés szükséges.");
   for(int i=0;i<parsed.length();i++)validate(parsed.getJSONObject(i));
   if(running)throw new IllegalStateException("Már fut egy küldetés.");
   steps=parsed;index=0;attempts=0;running=true;
   startForeground(501,notification("Küldetés indul"));
   report("running","Indítás");
   handler.post(next);
  }catch(Exception e){
   report("failed","Hibás vagy nem engedélyezett terv: "+e.getMessage());
   stopSelf();
  }
  return START_NOT_STICKY;
 }
 private void validate(JSONObject item){
  String type=item.optString("type"),arg=item.optString("arg");
  if(arg.length()>400)throw new IllegalArgumentException("Túl hosszú argumentum");
  switch(type){
   case "app": {
    boolean known=false;for(String p:PACKAGES)if(p.equals(arg))known=true;
    if(!known)throw new IllegalArgumentException("Alkalmazás nem engedélyezett: "+arg);
    break;
   }
   case "click":case "input":
    if(arg.isEmpty())throw new IllegalArgumentException("Hiányzó szöveg");
    if(arg.matches("(?is).*(küld|send|publish|fizet|pay|buy|vásár|order|delete|töröl|subscribe|confirm|megerősít|accept|elfogad).*"))
     throw new IllegalArgumentException("Érzékeny művelet tiltva; kézi jóváhagyás szükséges.");
    break;
   case "back":case "home":case "read":case "scroll_up":case "scroll_down":break;
   default:throw new IllegalArgumentException("Ismeretlen lépés: "+type);
  }
 }
 private void executeStep(){
  if(!running||steps==null)return;
  if(index>=steps.length()){halt("completed","Minden lépés befejeződött.");return;}
  PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
  KeyguardManager lock=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
  if(!power.isInteractive()||lock.isKeyguardLocked()){
   halt("paused","Képernyő lezárva. A küldetés leállt, nem fut vakon.");return;
  }
  JSONObject current=steps.optJSONObject(index);
  if(current==null){halt("failed","Hibás lépés");return;}
  String type=current.optString("type"),arg=current.optString("arg");
  JarvisAccessibility a=JarvisAccessibility.instance;
  boolean success=false;
  String detail=type;
  try{
   if("app".equals(type)){
    Intent app=getPackageManager().getLaunchIntentForPackage(arg);
    if(app!=null){app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(app);success=true;}
   }else if(a!=null){
    switch(type){
     case "click":success=a.clickText(arg);break;
     case "input":success=a.input(arg);break;
     case "scroll_up":success=a.scroll(true);break;
     case "scroll_down":success=a.scroll(false);break;
     case "back":success=a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);break;
     case "home":success=a.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);break;
     case "read":success=!a.readScreen().isEmpty();break;
    }
   }
  }catch(Exception e){detail=type+" hiba: "+e.getMessage();}
  if(success){
   attempts=0;index++;report("running","Sikeres: "+type);
   handler.postDelayed(next,"app".equals(type)?1800:800);
  }else if(!"click".equals(type)&&!"input".equals(type)&&attempts<2){
   attempts++;report("retry","Próbálkozás "+attempts+"/2: "+type);
   handler.postDelayed(next,1200);
  }else{
   halt("failed","Nem sikerült: "+detail+" ("+(index+1)+". lépés)");
  }
 }
 private void halt(String phase,String detail){
  handler.removeCallbacks(next);
  running=false;
  report(phase,detail);
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(502,
   new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
   .setContentTitle("JARVIS: "+phase).setContentText(detail).build());
  stopForeground(STOP_FOREGROUND_REMOVE);
  stopSelf();
 }
 @Override public void onDestroy(){
  handler.removeCallbacks(next);
  if(running){running=false;report("stopped","Android megszakította az ügynököt.");}
  super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}