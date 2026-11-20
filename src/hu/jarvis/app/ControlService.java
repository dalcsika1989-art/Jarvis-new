package hu.jarvis.app;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.graphics.PixelFormat;
import android.graphics.*;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
public class ControlService extends AccessibilityService {
 static ControlService live; WindowManager wm; FrameLayout bubble; WindowManager.LayoutParams pos; boolean expanded=false;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private final ArrayDeque<String> pending=new ArrayDeque<>();
 private boolean processing=false;
 private static volatile String commandStatus="Vezérlő nincs bekapcsolva.";
 public static String currentStatus(){return commandStatus;}
 public static boolean active(){return live!=null;}
 private void setStatus(String message){commandStatus=message;}
 private void stopTask(){pending.clear();processing=false;attempts=0;handler.removeCallbacks(advance);nextAdvanceAt=Long.MAX_VALUE;setStatus("Feladat megszakítva.");}

 private int attempts=0;
 private long lastStep=0;
 private long nextAdvanceAt=Long.MAX_VALUE;
 private final LinkedHashMap<String,String> launcherPackages=new LinkedHashMap<>();
 private final Runnable advance=new Runnable(){public void run(){nextAdvanceAt=Long.MAX_VALUE;step();}};
 // Keep the EARLIEST scheduled step. Repeated accessibility events must not
 // postpone execution indefinitely while the current screen is updating.
 private void scheduleAdvance(long delayMillis){
  long when=android.os.SystemClock.uptimeMillis()+Math.max(0L,delayMillis);
  if(when>=nextAdvanceAt)return;
  handler.removeCallbacks(advance);
  nextAdvanceAt=when;
  handler.postAtTime(advance,when);
 }
 public void onServiceConnected(){live=this;setStatus("JARVIS vezérlő készen áll.");wm=(WindowManager)getSystemService(WINDOW_SERVICE);bubble=new FrameLayout(this);pos=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);pos.gravity=Gravity.TOP|Gravity.END;pos.x=8;pos.y=180;render();try{wm.addView(bubble,pos);}catch(Exception e){bubble=null;}}
 private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
 private void radialButton(String symbol,String title,int cx,int cy,Runnable click){
  LinearLayout tile=new LinearLayout(this);tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);
  android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();
  bg.setColor(0xee071c31);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xff39d7f5);tile.setBackground(bg);
  TextView glyph=new TextView(this);glyph.setText(symbol);glyph.setTextColor(0xff74efff);glyph.setTextSize(23);glyph.setGravity(Gravity.CENTER);
  TextView label=new TextView(this);label.setText(title);label.setTextColor(0xffe0faff);label.setTextSize(10);label.setGravity(Gravity.CENTER);
  tile.addView(glyph);tile.addView(label);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(92),dp(68));
  lp.leftMargin=dp(cx-46);lp.topMargin=dp(cy-34);bubble.addView(tile,lp);
  tile.setAlpha(0f);tile.setScaleX(.55f);tile.setScaleY(.55f);
  tile.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(190).start();
  tile.setOnClickListener(v->click.run());
 }
 private void openPanel(String action){
  expanded=false;render();
  Intent i=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
  i.putExtra("jarvis_panel",action);
  startActivity(i);
 }
 static final class ArcHead extends View {
  final Paint paint=new Paint(3);
  ArcHead(android.content.Context c){super(c);setClickable(true);}
  @Override protected void onDraw(Canvas c){super.onDraw(c);
   float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.43f;
   paint.setShader(new RadialGradient(x,y,r,new int[]{0xffc2fdff,0xff138bb2,0xff04131e},null,Shader.TileMode.CLAMP));
   paint.setStyle(Paint.Style.FILL);c.drawCircle(x,y,r,paint);paint.setShader(null);
   paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3);paint.setColor(0xff50eaff);c.drawCircle(x,y,r*.90f,paint);
   paint.setStrokeWidth(2);paint.setColor(0xff03beef);c.drawCircle(x,y,r*.62f,paint);
   paint.setStrokeWidth(3);paint.setColor(0xffc5faff);
   Path t=new Path();t.moveTo(x,y-r*.52f);t.lineTo(x-r*.48f,y+r*.33f);t.lineTo(x+r*.48f,y+r*.33f);t.close();c.drawPath(t,paint);
   paint.setColor(0xff33dfff);paint.setStrokeWidth(4);
   float angle=(android.os.SystemClock.uptimeMillis()/18L)%360;
   c.drawArc(x-r,y-r,x+r,y+r,angle,82,false,paint);
   if(isAttachedToWindow())postInvalidateDelayed(120);
  }
 }
 private void addReactorHead(int cx,int cy){
  ArcHead head=new ArcHead(this);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(60),dp(60));
  lp.leftMargin=dp(cx-30);lp.topMargin=dp(cy-30);
  bubble.addView(head,lp);
  head.setOnClickListener(v->{expanded=!expanded;render();});
  head.setOnTouchListener(new View.OnTouchListener(){
   float x,y;int px,py;boolean drag;
   public boolean onTouch(View v,MotionEvent e){
    if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=pos.x;py=pos.y;drag=false;return true;}
    if(e.getAction()==MotionEvent.ACTION_MOVE){
     if(Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>dp(12))drag=true;
     if(drag&&!expanded){pos.x=Math.max(0,px-(int)(e.getRawX()-x));pos.y=Math.max(0,py+(int)(e.getRawY()-y));wm.updateViewLayout(bubble,pos);}
     return true;
    }
    if(e.getAction()==MotionEvent.ACTION_UP){if(!drag){expanded=!expanded;render();}return true;}
    return true;
   }
  });
 }
 void render(){
  if(bubble==null)return;
  bubble.removeAllViews();
  int size=expanded?340:60;
  pos.width=dp(size);pos.height=dp(size);
  bubble.setBackgroundColor(expanded?0xe8061525:0x00000000);
  if(expanded){
   // Six evenly spaced actions surrounding the ARC reactor.
   radialButton("◉","CHAT",170,49,()->openPanel("chat"));
   radialButton("🎙","MIKROFON",274,110,()->openPanel("mic"));
   radialButton("◇","AGY",274,230,()->openPanel("brain"));
   radialButton("⚙","BEÁLLÍTÁS",170,291,()->openPanel("settings"));
   radialButton("▦","VEZÉRLÉS",66,230,()->openPanel("control"));
   radialButton("◷","ÁLLAPOT",66,110,()->Toast.makeText(this,currentStatus(),Toast.LENGTH_LONG).show());
   addReactorHead(170,170);
  }else addReactorHead(30,30);
  try{if(bubble.getParent()!=null)wm.updateViewLayout(bubble,pos);}catch(Exception e){android.util.Log.w("JARVIS","Overlay layout",e);}
 }
 public void onAccessibilityEvent(AccessibilityEvent e){
  if(!processing||pending.isEmpty()||e==null)return;
  int type=e.getEventType();
  if(type!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED&&type!=AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)return;
  if(lastStep>0&&android.os.SystemClock.uptimeMillis()-lastStep>=80)scheduleAdvance(120);
 } public void onInterrupt(){} public void onDestroy(){stopTask();live=null;setStatus("JARVIS vezérlő leállt.");if(bubble!=null)try{wm.removeView(bubble);}catch(Exception e){}super.onDestroy();}
 private void step(){
  if(!processing)return;
  String command=pending.peek();
  if(command==null){processing=false;setStatus("Minden parancs átadva az Androidnak (ellenőrizendő).");return;}
  setStatus("Folyamatban: "+command+" ("+(attempts+1)+"/10)");
  boolean accepted=false;
  try{accepted=perform(command);}catch(Exception e){android.util.Log.e("JARVIS","Műveleti hiba",e);}
  lastStep=android.os.SystemClock.uptimeMillis();
  if(accepted){
   pending.poll();attempts=0;
   if(pending.isEmpty()){
    processing=false;
    setStatus("Minden sorba állított művelet befejeződött.");
   }else{
    setStatus("Android elfogadta: "+command+"; további lépések: "+pending.size());
    // Fallback timer: a screen-change event can wake the next step sooner.
    scheduleAdvance(450);
   }
  }else if(++attempts<10){
   setStatus("Várakozás / újrapróbálás: "+command+" ("+attempts+"/10)");
   scheduleAdvance(320+attempts*85L);
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
   app=app.replaceFirst("^(a |az )","").replaceAll("[.!?]+$","").trim();
   if(app.endsWith("-ot")||app.endsWith("-et"))app=app.substring(0,app.length()-3);
   else if((app.endsWith("ot")||app.endsWith("et"))&&app.length()>5)app=app.substring(0,app.length()-2);
   else if(app.endsWith("t")&&app.length()>5)app=app.substring(0,app.length()-1);
   return launchApp(app);
  }
  return false;
 }
 private void refreshLauncherPackages(){
  launcherPackages.clear();
  PackageManager pm=getPackageManager();
  Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
  for(ResolveInfo info:pm.queryIntentActivities(query,0)){
   if(info.activityInfo==null)continue;
   CharSequence title=info.loadLabel(pm);
   if(title==null)continue;
   String label=title.toString().trim().toLowerCase(Locale.forLanguageTag("hu-HU"));
   if(!label.isEmpty())launcherPackages.put(label,info.activityInfo.packageName);
  }
 }
 private String findLauncherPackage(String name){
  String exact=launcherPackages.get(name);
  if(exact!=null)return exact;
  for(Map.Entry<String,String> entry:launcherPackages.entrySet())
   if(entry.getKey().contains(name))return entry.getValue();
  return null;
 }
 private boolean launchApp(String name){
  if(launcherPackages.isEmpty())refreshLauncherPackages();
  String packageName=findLauncherPackage(name);
  if(packageName==null){refreshLauncherPackages();packageName=findLauncherPackage(name);}
  if(packageName==null)return false;
  PackageManager pm=getPackageManager();
  Intent intent=pm.getLaunchIntentForPackage(packageName);
  if(intent==null){
   refreshLauncherPackages();
   packageName=findLauncherPackage(name);
   if(packageName==null)return false;
   intent=pm.getLaunchIntentForPackage(packageName);
  }
  if(intent==null)return false;
  intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  startActivity(intent);
  return true;
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
 public static boolean cancelCurrentTask(){
  ControlService service=live;
  if(service==null)return false;
  service.handler.post(()->service.stopTask());
  return true;
 }
 public static boolean looksLikeLocalCommand(String command){
  if(command==null)return false;
  String s=command.toLowerCase(Locale.forLanguageTag("hu-HU")).trim();
  return s.startsWith("nyisd meg ")||s.startsWith("indítsd el ")||s.startsWith("kattints ")||s.startsWith("nyomd meg ")||s.equals("vissza")||s.equals("menj vissza")||s.equals("home")||s.equals("főképernyő")||s.equals("kezdőképernyő")||s.equals("értesítések")||s.startsWith("görgess ")||s.startsWith("lapozz ");
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
   service.processing=true;service.attempts=0;service.handler.removeCallbacks(service.advance);service.nextAdvanceAt=Long.MAX_VALUE;service.scheduleAdvance(0);
  });
  return true;
 }
 static boolean scrollBack(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scrollBack(n.getChild(i)))return true;return false;}
 static boolean scroll(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;}
 static void collect(AccessibilityNodeInfo n,StringBuilder s,int depth){if(n==null||depth>18||s.length()>6000)return;if(n.getText()!=null)s.append(n.getText()).append(". ");for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),s,depth+1);}
}
