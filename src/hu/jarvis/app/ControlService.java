package hu.jarvis.app;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.*;
import android.view.*;
import android.widget.*;
import android.graphics.PixelFormat;
import android.content.Intent;
public class ControlService extends AccessibilityService {
 static ControlService live; WindowManager wm; LinearLayout bubble; WindowManager.LayoutParams pos; boolean expanded=false;
 public void onServiceConnected(){live=this;wm=(WindowManager)getSystemService(WINDOW_SERVICE);bubble=new LinearLayout(this);bubble.setOrientation(1);bubble.setPadding(5,5,5,5);bubble.setBackgroundColor(0xe6102935);pos=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);pos.gravity=Gravity.TOP|Gravity.END;pos.x=8;pos.y=180;render();try{wm.addView(bubble,pos);}catch(Exception e){bubble=null;}}
 void button(String text,Runnable click){Button b=new Button(this);b.setText(text);b.setTextColor(0xff83efff);b.setTextSize(12);b.setMinWidth(44);b.setMinimumWidth(44);b.setPadding(8,0,8,0);bubble.addView(b,new LinearLayout.LayoutParams(-2,44));b.setOnClickListener(v->click.run());}
 void render(){if(bubble==null)return;bubble.removeAllViews();button(expanded?"×":"J",()->{expanded=!expanded;render();});if(expanded){button("‹",()->performGlobalAction(GLOBAL_ACTION_BACK));button("⌂",()->performGlobalAction(GLOBAL_ACTION_HOME));button("↓",()->{if(!scroll(getRootInActiveWindow()))Toast.makeText(this,"Nincs görgethető tartalom",Toast.LENGTH_SHORT).show();});button("JARVIS",()->{expanded=false;render();startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP));});}Button head=(Button)bubble.getChildAt(0);head.setOnTouchListener(new View.OnTouchListener(){float x,y;int px,py;boolean drag;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();px=pos.x;py=pos.y;drag=false;}if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>12)drag=true;if(drag){pos.x=Math.max(0,px-(int)(e.getRawX()-x));pos.y=Math.max(0,py+(int)(e.getRawY()-y));wm.updateViewLayout(bubble,pos);return true;}}if(e.getAction()==MotionEvent.ACTION_UP&&drag)return true;return false;}});}
 public void onAccessibilityEvent(AccessibilityEvent e){} public void onInterrupt(){} public void onDestroy(){live=null;if(bubble!=null)try{wm.removeView(bubble);}catch(Exception e){}super.onDestroy();}
 public static boolean executeLocalCommand(String command){
  ControlService service=live;
  if(service==null||command==null)return false;
  String s=command.toLowerCase(java.util.Locale.forLanguageTag("hu-HU")).trim();
  if(s.equals("vissza")||s.equals("menj vissza"))return service.performGlobalAction(GLOBAL_ACTION_BACK);
  if(s.equals("főképernyő")||s.equals("kezdőképernyő")||s.equals("home"))return service.performGlobalAction(GLOBAL_ACTION_HOME);
  if(s.equals("értesítések")||s.equals("nyisd meg az értesítéseket"))return service.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
  if(s.equals("görgess le")||s.equals("lapozz le"))return scroll(service.getRootInActiveWindow());
  if(s.equals("görgess fel")||s.equals("lapozz fel"))return scrollBack(service.getRootInActiveWindow());
  return false;
 }
 static boolean scrollBack(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scrollBack(n.getChild(i)))return true;return false;}
 static boolean scroll(AccessibilityNodeInfo n){if(n==null)return false;if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++)if(scroll(n.getChild(i)))return true;return false;}
 static void collect(AccessibilityNodeInfo n,StringBuilder s,int depth){if(n==null||depth>18||s.length()>6000)return;if(n.getText()!=null)s.append(n.getText()).append(". ");for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),s,depth+1);}
}
