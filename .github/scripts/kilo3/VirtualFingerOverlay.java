package hu.jarvis.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * Optional visible "virtual finger" for accessibility-driven JARVIS taps.
 * This is strictly a non-touchable visual overlay: it never intercepts user
 * input or grants extra permissions. Real interactions are dispatched by the
 * already-authorized ControlService using dispatchGesture().
 */
final class VirtualFingerOverlay {
  private final ControlService service;
  private final WindowManager wm;
  private final Handler main=new Handler(Looper.getMainLooper());
  private final PointerView pointer;
  private final WindowManager.LayoutParams params;
  private final int size;
  private boolean enabled,attached;
  private long sequence=0;

  VirtualFingerOverlay(ControlService s){
    service=s;
    wm=(WindowManager)s.getSystemService(Context.WINDOW_SERVICE);
    float d=s.getResources().getDisplayMetrics().density;
    size=Math.round(82*d);
    pointer=new PointerView(s);
    params=new WindowManager.LayoutParams(
      size,size,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
      WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
      WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
      WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
      PixelFormat.TRANSLUCENT);
    params.gravity=Gravity.TOP|Gravity.LEFT;
    params.x=0;params.y=0;
  }

  boolean isEnabled(){return enabled;}
  void setEnabled(boolean on){
    main.post(()->{
      enabled=on;
      if(!on)detach();
      JarvisApp.event("finger",on?"BE":"KI");
    });
  }
  void pointAt(int screenX,int screenY,String action){
    main.post(()->{
      if(!enabled)return;
      int width=service.getResources().getDisplayMetrics().widthPixels;
      int height=service.getResources().getDisplayMetrics().heightPixels;
      if(screenX<0||screenY<0||screenX>=width||screenY>=height)return;
      params.x=Math.max(0,Math.min(width-size,screenX-size/2));
      params.y=Math.max(0,Math.min(height-size,screenY-size/2));
      pointer.action=action==null?"ÉRINTÉS":action;
      pointer.started=android.os.SystemClock.uptimeMillis();
      sequence++;
      long current=sequence;
      try{
        if(!attached){wm.addView(pointer,params);attached=true;}
        else wm.updateViewLayout(pointer,params);
        pointer.invalidate();
        main.postDelayed(()->{
          if(sequence==current)detach();
        },1100);
      }catch(Exception err){
        detach();
        JarvisApp.event("error","A virtuális ujj megjelenítése nem sikerült: "+err.getMessage());
      }
    });
  }
  void stop(){
    main.post(()->{enabled=false;sequence++;detach();});
  }
  private void detach(){
    if(attached){
      attached=false;
      try{wm.removeView(pointer);}catch(Exception ignored){}
    }
  }

  static final class PointerView extends View {
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    long started;
    String action="ÉRINTÉS";
    PointerView(Context c){super(c);setWillNotDraw(false);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
      super.onDraw(canvas);
      float d=getResources().getDisplayMetrics().density;
      float cx=getWidth()/2f,cy=getHeight()/2f;
      float age=Math.min(1f,(android.os.SystemClock.uptimeMillis()-started)/750f);
      float radius=(15+17*age)*d;
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(2.5f*d);
      paint.setColor(0xaa61edff);
      paint.setAlpha(Math.max(0,(int)(180*(1-age))));
      canvas.drawCircle(cx,cy,radius,paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(0xcc0b3140);
      canvas.drawCircle(cx,cy,13*d,paint);
      paint.setColor(0xff75f2ff);
      canvas.drawCircle(cx,cy,7*d,paint);
      paint.setColor(0xffffffff);
      canvas.drawCircle(cx,cy,2.2f*d,paint);
      // Stylized fingertip silhouette below/right of the contact mark.
      Path finger=new Path();
      finger.moveTo(cx+8*d,cy+10*d);
      finger.cubicTo(cx+5*d,cy-1*d,cx+7*d,cy-9*d,cx+11*d,cy-10*d);
      finger.cubicTo(cx+17*d,cy-11*d,cx+16*d,cy+1*d,cx+17*d,cy+6*d);
      finger.cubicTo(cx+23*d,cy+2*d,cx+29*d,cy+9*d,cx+27*d,cy+16*d);
      finger.lineTo(cx+24*d,cy+31*d);
      finger.lineTo(cx+12*d,cy+31*d);
      finger.close();
      paint.setColor(0xe5bfeef8);
      canvas.drawPath(finger,paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(0xff28546d);
      paint.setStrokeWidth(1.6f*d);
      canvas.drawPath(finger,paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(0xffc7f7ff);
      paint.setTextSize(9*d);
      paint.setTextAlign(Paint.Align.CENTER);
      canvas.drawText(action,cx,11*d,paint);
      if(age<1&&isAttachedToWindow())postInvalidateDelayed(32);
    }
  }
}
