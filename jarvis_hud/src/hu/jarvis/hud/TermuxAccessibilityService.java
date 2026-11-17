package hu.jarvis.hud;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.DisplayMetrics;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Only runs after the user manually enables Accessibility in Android Settings. */
public class TermuxAccessibilityService extends AccessibilityService {
  private volatile boolean alive;
  private Thread worker;
  private final Handler main=new Handler(Looper.getMainLooper());
  private static final String SERVER="http://127.0.0.1:8765";

  @Override public void onServiceConnected(){
    super.onServiceConnected();
    alive=true;
    AccessibilityServiceInfo info=getServiceInfo();
    if(info!=null){
      info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
      setServiceInfo(info);
    }
    if(worker==null || !worker.isAlive()){
       worker=new Thread(new Runnable(){@Override public void run(){pollLoop();}},"JarvisTermuxExecutor");
       worker.start();
    }
  }
  @Override public void onAccessibilityEvent(AccessibilityEvent e){}
  @Override public void onInterrupt(){}
  @Override public void onDestroy(){alive=false;if(worker!=null)worker.interrupt();super.onDestroy();}

  private void pollLoop(){
    while(alive && !Thread.currentThread().isInterrupted()){
      try{
        String token=getSharedPreferences("jarvis_termux",Context.MODE_PRIVATE).getString("token","");
        if(token.length()<24){Thread.sleep(2100);continue;}
        JSONObject job=post("/api/bridge/claim",new JSONObject(),token);
        JSONObject task=job.optJSONObject("task");
        if(task==null){Thread.sleep(1800);continue;}
        final int id=task.getInt("id");
        final JSONObject payload=task.getJSONObject("payload");
        final String[] message={"Nem sikerült feldolgozni"};
        final boolean[] success={false};
        final CountDownLatch latch=new CountDownLatch(1);
        main.post(new Runnable(){@Override public void run(){
           try{
             String command=payload.optString("command","");
             if("scroll_down".equals(command)||"scroll_up".equals(command)){
               boolean down="scroll_down".equals(command);
               if(scrollNode(down)){
                 success[0]=true;message[0]="OK: "+command+" (Accessibility elem)";
                 latch.countDown();
               } else {
                 // dispatchGesture is asynchronous. Acknowledge only from its callback.
                 gestureScroll(down,success,message,latch);
               }
             }else{
               success[0]=execute(payload,message);
               latch.countDown();
             }
           }catch(Exception ex){
             message[0]="Végrehajtási hiba: "+ex.getClass().getSimpleName();
             latch.countDown();
           }
        }});
        boolean returned=latch.await(12,TimeUnit.SECONDS);
        JSONObject ack=new JSONObject().put("id",id).put("ok",returned&&success[0]).put("message",returned?message[0]:"Időtúllépés");
        post("/api/bridge/complete",ack,token);
      }catch(InterruptedException ex){Thread.currentThread().interrupt();break;}
      catch(Exception ex){try{Thread.sleep(4000);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();break;}}
    }
  }
  private JSONObject post(String path,JSONObject input,String token) throws Exception{
    HttpURLConnection con=(HttpURLConnection)new URL(SERVER+path).openConnection();
    try{
      con.setRequestMethod("POST");con.setConnectTimeout(2000);con.setReadTimeout(4200);
      con.setRequestProperty("Authorization","Bearer "+token);
      con.setRequestProperty("Content-Type","application/json; charset=utf-8");
      con.setDoOutput(true);
      try(OutputStream out=con.getOutputStream()){out.write(input.toString().getBytes(StandardCharsets.UTF_8));}
      int status=con.getResponseCode();
      if(status<200||status>=300)throw new IllegalStateException("HTTP "+status);
      try(InputStream in=con.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[4096];int n;
        while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>100000)throw new IllegalStateException("Válasz túl nagy");}
        return new JSONObject(out.toString("UTF-8"));
      }
    }finally{con.disconnect();}
  }

  private boolean execute(JSONObject payload,String[] message) throws Exception{
    String command=payload.getString("command");
    JSONObject args=payload.optJSONObject("args");if(args==null)args=new JSONObject();
    boolean ok=false;
    switch(command){
      case "home":ok=performGlobalAction(GLOBAL_ACTION_HOME);break;
      case "back":ok=performGlobalAction(GLOBAL_ACTION_BACK);break;
      case "recents":ok=performGlobalAction(GLOBAL_ACTION_RECENTS);break;
      case "notifications":ok=performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);break;
      case "launch":
        Intent app=getPackageManager().getLaunchIntentForPackage(args.optString("package"));
        if(app!=null){app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(app);ok=true;}
        break;
      case "scroll_down":message[0]="Görgetés aszinkron végrehajtása szükséges.";return false;
      case "scroll_up":message[0]="Görgetés aszinkron végrehajtása szükséges.";return false;
      case "tap_text":ok=tapText(args.optString("text"));break;
      case "type_text":ok=typeText(args.optString("text"));break;
      default:message[0]="Ismeretlen művelet";return false;
    }
    message[0]=(ok?"OK: ":"Nem sikerült: ")+command;
    return ok;
  }

  private boolean scrollNode(boolean down){
    AccessibilityNodeInfo root=getRootInActiveWindow();
    if(root==null)return false;
    try{
      ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
      queue.add(root);
      for(int visited=0;!queue.isEmpty()&&visited<350;visited++){
        AccessibilityNodeInfo node=queue.removeFirst();
        if(node.isScrollable()&&node.performAction(down?AccessibilityNodeInfo.ACTION_SCROLL_FORWARD:AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;
        for(int i=0;i<node.getChildCount();i++){
          AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);
        }
      }
      return false;
    }finally{root.recycle();}
  }

  /**
   * Fallback for custom drawing surfaces, WebView and terminal apps that expose
   * no scrollable AccessibilityNodeInfo. Called only on the main thread.
   * Completion confirms the gesture was delivered, not that content moved.
   */
  private void gestureScroll(boolean down,final boolean[] success,final String[] message,final CountDownLatch latch){
    DisplayMetrics metrics=getResources().getDisplayMetrics();
    int w=metrics.widthPixels,h=metrics.heightPixels;
    if(w<=0||h<=0){
      message[0]="Nem sikerült meghatározni a képernyő méretét.";
      latch.countDown();return;
    }
    float x=w*.50f;
    float from=down?h*.72f:h*.34f;
    float to=down?h*.34f:h*.72f;
    Path swipe=new Path();
    swipe.moveTo(x,from);
    swipe.lineTo(x,to);
    GestureDescription gesture=new GestureDescription.Builder()
       .addStroke(new GestureDescription.StrokeDescription(swipe,0,430)).build();
    boolean accepted=dispatchGesture(gesture,new GestureResultCallback(){
      @Override public void onCompleted(GestureDescription description){
        success[0]=true;
        message[0]="OK: érintésmozdulat végrehajtva; a tartalom elmozdulása nem ellenőrzött.";
        latch.countDown();
      }
      @Override public void onCancelled(GestureDescription description){
        success[0]=false;
        message[0]="Android megszakította a görgetőmozdulatot.";
        latch.countDown();
      }
    },main);
    if(!accepted){
      success[0]=false;
      message[0]="Az Android nem fogadta el a görgetőmozdulatot (Accessibility engedély?).";
      latch.countDown();
    }
  }

  private boolean tapText(String text){
    if(text==null||text.length()<1||text.length()>200)return false;
    if(text.matches("(?i)^(send|küldés|buy|pay|purchase|vásárlás|bezahlen|kaufen|delete|törlés)$"))return false;
    AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
    try{
      List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(text);
      for(AccessibilityNodeInfo item:found){
        CharSequence label=item.getText(),description=item.getContentDescription();
        if((label==null||!text.equalsIgnoreCase(label.toString().trim()))&&
           (description==null||!text.equalsIgnoreCase(description.toString().trim())))continue;
        AccessibilityNodeInfo cur=item;
        for(int d=0;cur!=null&&d<6;d++){
          if(cur.isClickable()&&cur.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
          cur=cur.getParent();
        }
      }
      return false;
    }finally{root.recycle();}
  }
  private boolean typeText(String text){
    if(text==null||text.length()<1||text.length()>200)return false;
    AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
    try{
      ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
      queue.add(root);
      for(int visited=0;!queue.isEmpty()&&visited<350;visited++){
        AccessibilityNodeInfo node=queue.removeFirst();
        if(node.isEditable()&&node.isFocused()){
          Bundle data=new Bundle();
          data.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
          if(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,data))return true;
        }
        for(int i=0;i<node.getChildCount();i++){
          AccessibilityNodeInfo child=node.getChild(i);if(child!=null)queue.add(child);
        }
      }
      return false;
    }finally{root.recycle();}
  }
}