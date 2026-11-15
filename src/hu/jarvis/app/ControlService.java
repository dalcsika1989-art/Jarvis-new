package hu.jarvis.app;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.graphics.PixelFormat;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
public class ControlService extends AccessibilityService {
 static ControlService live; WindowManager wm; LinearLayout bubble; WindowManager.LayoutParams pos; boolean expanded=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<String> pending=new ArrayDeque<>();
 private boolean processing=false;
 private static volatile String commandStatus="Vezérlő nincs bekapcsolva.";
 public static String currentStatus(){return commandStatus;}
 public static boolean active(){return live!=null;}
 private void setStatus(String message){commandStatus=message;}
 private void stopTask(){pending.clear();processing=false;attempts=0;handler.removeCallbacks(advance);setStatus("Feladat megszakítva.");}

 private int attempts=0;
 private long lastStep=0;
 private final Runnable advance=new Runnable(){public void run(){step();}};
 public void onServiceConnected(){live=this;setStatus("JARVIS vezérlő készen áll.");wm=(WindowManager)getSystemService(WINDOW_SERVICE);bubble=new LinearLayout(this);bubble.setOrientation(1);bubble.setPadding(5,5,5,5);bubble.setBackgroundColor(0xe6102935);pos=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);pos.gravity=Gravity.TOP|Gravity.END;pos.x=8;pos.y=180;render();try{wm.addView(bubble,pos);}catch(Exception e){bubble=null;}}
 void button(String text,Runnable click){Button b=new Button(this);b.setText(text);b.setTextColor(0xff83efff);b.setTextSize(12);b.setMinWidth(44);b.setMinimumWidth(44);b.setPadding(8,0,8,0);bubble.addView(b,new LinearLayout.LayoutParams(-2,44));b.setOnClickListener(v->click.run());}
 void render(){if(bubble==null)return;bubble.removeAllViews();button(expanded?"×":"J",()->{expanded=!expanded;render();});if(expanded){button("■ STOP",()->stopTask());button("ÁLLAPOT",()->Toast.makeText(this,currentStatus(),Toast.LENGTH_LONG).show());button("‹",()->performGlobalAction(GLOBAL_ACTION_BACK));button("⌂",()->performGlobalAction(GLOBAL_ACTION_HOME));button("↓",()->{if(!scroll(getRootInActiveWindow()))Toast.makeText(this,"Nincs görgethető tartalom",Toast.LENGTH_SHORT).show();});button("JARVIS",()->{expanded=false;render();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});}Button head=(Button)bubble.getChildAt(0);head.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;boolean drag;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=pos.x;py=pos.y;drag=false;}if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>12)drag=true;if(drag){pos.x=Math.max(0,px-(int)(e.getRawX()-x));pos.y=Math.max(0,py+(int)(e.getRawY()-y));wm.updateViewLayout(bubble,pos);return true;}}if(e.getAction()==MotionEvent.ACTION_UP&&drag)return true;return false;}});}
 public void onAccessibilityEvent(AccessibilityEvent e){if(processing && e!=null && android.os.SystemClock.uptimeMillis()-lastStep>90){handler.removeCallbacks(advance);handler.postDelayed(advance,120);}} public void onInterrupt(){} public void onDestroy(){stopTask();live=null;setStatus("JARVIS vezérlő leállt.");if(bubble!=null)try{wm.removeView(bubble);}catch(Exception e){}super.onDestroy();}
 private void step(){
  if(!processing)return;
  String command=pending.peek();
  if(command==null){processing=false;setStatus("Minden sorba állított művelet befejeződött.");return;}
  setStatus("Folyamatban: "+command+" ("+(attempts+1)+"/10)");
  boolean accepted=false;
  try{accepted=perform(command);}catch(Exception e){android.util.Log.e("JARVIS","Műveleti hiba",e);}
  lastStep=android.os.SystemClock.uptimeMillis();
  if(accepted){
   pending.poll();attempts=0;
   setStatus("Android elfogadta: "+command+"; további lépések: "+pending.size());
   handler.postDelayed(advance,pending.isEmpty()?200:550);
  }else if(++attempts<10){
   setStatus("Várakozás / újrapróbálás: "+command+" ("+attempts+"/10)");
   handler.postDelayed(advance,650);
  }else{
   android.util.Log.w("JARVIS","Sikertelen lépés: "+command);
   pending.clear();processing=false;attempts=0;
   setStatus("Sikertelen: "+command+" (10 próbálkozás után leállítva)");
   Toast.makeText(this,currentStatus(),Toast.LENGTH_LONG).show();
  }
 }
 private boolean perform(String command){
  String s=command.toLowerCase(Locale.forLanguageTag("hu-HU")).trim();
  if(s.equals("vissza")||s.equals("menj vissza"))return performGlobalAction(GLOBAL_ACTION_BACK);
  if(s.equals("főképernyő")||s.equals("kezdőképernyő")||s.equals("home"))return performGlobalAction(GLOBAL_ACTION_HOME);
  if(s.equals("értesítések")||s.equals("nyisd meg az értesítéseket"))return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
  if(s.equals("görgess le")||s.equals("lapozz le"))return scroll(getRootInActiveWindow());
  if(s.equals("görgess fel")||s.equals("lapozz fel"))return scrollBack(getRootInActiveWindow());
  if(s.startsWith("kattints ")||s.startsWith("nyomd meg ")){
   String target=s.startsWith("kattints ")?s.substring(9):s.substring(10);
   return clickText(getRootInActiveWindow(),target,0);
  }
  if(s.startsWith("nyisd meg ")||s.startsWith("indítsd el ")){
   String app=s.startsWith("nyisd meg ")?s.substring(10):s.substring(11);
   return launchApp(app);
  }
  return false;
 }
 private boolean launchApp(String name){
  PackageManager pm=getPackageManager();
  Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  for(ResolveInfo info:pm.queryIntentActivities(query,0)){
   String label=info.loadLabel(pm).toString().toLowerCase(Locale.forLanguageTag("hu-HU"));
   if(label.equals(name)||label.contains(name)){
    Intent intent=pm.getLaunchIntentForPackage(info.activityInfo.packageName);
    if(intent!=null){intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(intent);return true;}
   }
  }
  return false;
 }
 private static boolean clickText(AccessibilityNodeInfo node,String text,int depth){
  if(node==null||depth>30)return false;
  CharSequence label=node.getText(),desc=node.getContentDescription();
  boolean match=(label!=null&&label.toString().toLowerCase(Locale.ROOT).contains(text))||(desc!=null&&desc.toString().toLowerCase(Locale.ROOT).contains(text));
  if(match){
   AccessibilityNodeInfo p=node;
   for(int i=0;i<5&&p!=null;i++,p=p.getParent())if(p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  }
  for(int i=0;i<node.getChildCount();i++)if(clickText(node.getChild(i),text,depth+1))return true;
  return false;
 }
 public static boolean executeLocalCommand(String command){
  ControlService service=live;
  if(service==null||command==null)return false;
  String s=command.toLowerCase(Locale.forLanguageTag("hu-HU")).trim();
  if(!(s.startsWith("nyisd meg ")||s.startsWith("indítsd el ")||s.startsWith("kattints ")||s.startsWith("nyomd meg ")||s.equals("vissza")||s.equals("menj vissza")||s.equals("home")||s.equals("főképernyő")||s.equals("kezdőképernyő")||s.equals("értesítések")||s.equals("nyisd meg az értesítéseket")||s.startsWith("görgess ")||s.startsWith("lapozz ")))return false;
  service.handler.post(()->{
   service.pending.clear();
   service.setStatus("Parancssor fogadva. Előkészítés...");
   for(String part:command.split("[;\\n]"))if(!part.trim().isEmpty())service.pending.add(part.trim());
   service.processing=true;service.attempts=0;service.handler.removeCallbacks(service.advance);service.handler.post(service.advance);
  });
  return true;
 }
 static boolean scrollBack(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scrollBack(n.getChild(i)))return true;return false;}
 static boolean scroll(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;}
 static void collect(AccessibilityNodeInfo n,StringBuilder s,int depth){if(n==null||depth>18||s.length()>6000)return;if(n.getText()!=null)s.append(n.getText()).append(". ");for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),s,depth+1);}
}
