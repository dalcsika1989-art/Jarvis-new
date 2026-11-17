package hu.jarvis.hud;
import android.app.Service;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
public class FloatingService extends Service{
 private WindowManager manager;private WindowManager.LayoutParams params;private View bubble;
 private float touchX,touchY;private int oldX,oldY;private boolean dragging;
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public void onCreate(){
  super.onCreate();if(!Settings.canDrawOverlays(this)){stopSelf();return;}
  manager=(WindowManager)getSystemService(WINDOW_SERVICE);
  params=new WindowManager.LayoutParams(dp(66),dp(66),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.LEFT|Gravity.TOP;params.x=4;params.y=getResources().getDisplayMetrics().heightPixels/3;
  bubble=new OrbView();
  bubble.setOnTouchListener(new View.OnTouchListener(){
    @Override public boolean onTouch(View v,MotionEvent ev){
     switch(ev.getAction()){
      case MotionEvent.ACTION_DOWN:touchX=ev.getRawX();touchY=ev.getRawY();oldX=params.x;oldY=params.y;dragging=false;return true;
      case MotionEvent.ACTION_MOVE:float dx=ev.getRawX()-touchX,dy=ev.getRawY()-touchY;if(Math.abs(dx)+Math.abs(dy)>8)dragging=true;params.x=Math.max(0,oldX+(int)dx);params.y=Math.max(0,oldY+(int)dy);if(manager!=null&&bubble!=null)manager.updateViewLayout(bubble,params);return true;
      case MotionEvent.ACTION_UP:if(!dragging){Intent i=new Intent(FloatingService.this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}return true;
     }return false;
    }
  });
  try{manager.addView(bubble,params);}catch(Exception e){stopSelf();}
 }
 private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
 @Override public void onDestroy(){if(manager!=null&&bubble!=null){try{manager.removeView(bubble);}catch(Exception e){}}bubble=null;super.onDestroy();}
 private class OrbView extends View{
  private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
  OrbView(){super(FloatingService.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
  @Override protected void onDraw(Canvas c){
   super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-4;long t=System.currentTimeMillis();float a=(t%5000)/5000f;
   p.setStyle(Paint.Style.FILL);p.setColor(0xdd021529);c.drawCircle(cx,cy,r,p);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xff06ccff);p.setShadowLayer(dp(7),0,0,0xff08ccff);c.drawCircle(cx,cy,r-2,p);
   p.setColor(0xffff2a50);p.setStrokeWidth(dp(3));c.drawArc(cx-r+7,cy-r+7,cx+r-7,cy+r-7,a*360,58,false,p);
   p.setShadowLayer(dp(5),0,0,0xff00d9ff);p.setColor(0xff72eaff);p.setStrokeWidth(dp(2));c.drawCircle(cx,cy,r*.67f,p);
   Path path=new Path();path.moveTo(cx,cy+r*.58f);path.lineTo(cx-r*.55f,cy-r*.43f);path.lineTo(cx+r*.55f,cy-r*.43f);path.close();p.setStyle(Paint.Style.FILL);p.setColor(0xff08bceb);c.drawPath(path,p);
   p.setShadowLayer(0,0,0,0);p.setColor(0xff06385c);Path in=new Path();in.moveTo(cx,cy+r*.35f);in.lineTo(cx-r*.31f,cy-r*.28f);in.lineTo(cx+r*.31f,cy-r*.28f);in.close();c.drawPath(in,p);
   postInvalidateDelayed(80);
  }
 }
}