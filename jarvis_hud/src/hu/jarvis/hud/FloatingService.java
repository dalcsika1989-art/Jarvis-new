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

public class FloatingService extends Service {
    private WindowManager manager;
    private WindowManager.LayoutParams bubbleParams,menuParams;
    private View bubble,menu;
    private float touchX,touchY;
    private int oldX,oldY;
    private boolean dragging;
    private static final String[] LABELS={"MIKROFON","ALKALM.","TUDÁS","MINI CHAT","BEÁLL.","FŐOLDAL","FÁJLOK","CHAT"};
    private static final String[] PAGES={"chat","apps","knowledge","mini","settings","home","files","chat"};

    @Override public IBinder onBind(Intent i){return null;}
    private int dp(float d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(){
      super.onCreate();
      if(!Settings.canDrawOverlays(this)){stopSelf();return;}
      manager=(WindowManager)getSystemService(WINDOW_SERVICE);
      bubbleParams=new WindowManager.LayoutParams(dp(64),dp(64),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT);
      bubbleParams.gravity=Gravity.TOP|Gravity.LEFT;
      bubbleParams.x=5;bubbleParams.y=getResources().getDisplayMetrics().heightPixels/3;
      bubble=new OrbView();
      bubble.setOnTouchListener(new View.OnTouchListener(){
         @Override public boolean onTouch(View v,MotionEvent e){
            switch(e.getActionMasked()){
              case MotionEvent.ACTION_DOWN:
                touchX=e.getRawX();touchY=e.getRawY();
                oldX=bubbleParams.x;oldY=bubbleParams.y;
                dragging=false;return true;
              case MotionEvent.ACTION_MOVE:
                float dx=e.getRawX()-touchX,dy=e.getRawY()-touchY;
                if(Math.abs(dx)+Math.abs(dy)>dp(8))dragging=true;
                if(dragging){
                  hideMenu();
                  bubbleParams.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-dp(64),oldX+(int)dx));
                  bubbleParams.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-dp(64),oldY+(int)dy));
                  try{manager.updateViewLayout(bubble,bubbleParams);}catch(Exception ignored){}
                }return true;
              case MotionEvent.ACTION_UP:
                if(!dragging)toggleMenu();
                return true;
              case MotionEvent.ACTION_CANCEL:return true;
            }
            return false;
         }
      });
      try{manager.addView(bubble,bubbleParams);}catch(Exception e){stopSelf();}
    }

    private void toggleMenu(){
       if(menu!=null){hideMenu();return;}
       if(!Settings.canDrawOverlays(this))return;
       int w=dp(325),h=dp(325);
       menuParams=new WindowManager.LayoutParams(w,h,
         WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
         WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
         PixelFormat.TRANSLUCENT);
       menuParams.gravity=Gravity.TOP|Gravity.LEFT;
       int cx=bubbleParams.x+dp(32),cy=bubbleParams.y+dp(32);
       menuParams.x=Math.max(0,Math.min(getResources().getDisplayMetrics().widthPixels-w,cx-w/2));
       menuParams.y=Math.max(0,Math.min(getResources().getDisplayMetrics().heightPixels-h,cy-h/2));
       menu=new RadialView();
       menu.setOnTouchListener(new View.OnTouchListener(){
          @Override public boolean onTouch(View v,MotionEvent e){
            if(e.getActionMasked()!=MotionEvent.ACTION_UP)return true;
            float cx=v.getWidth()/2f,cy=v.getHeight()/2f;
            float x=e.getX()-cx,y=e.getY()-cy;
            float dist=(float)Math.hypot(x,y);
            if(dist<dp(50)){hideMenu();return true;}
            if(dist>dp(163)){hideMenu();return true;}
            double angle=Math.atan2(y,x)+Math.PI/2;
            if(angle<0)angle+=Math.PI*2;
            int i=((int)Math.round(angle/(Math.PI/4)))%8;
            String page=PAGES[i];
            hideMenu();
            open(page);
            return true;
          }
       });
       try{manager.addView(menu,menuParams);}catch(Exception e){menu=null;}
    }
    private void open(String page){
       Intent intent=new Intent(this,MainActivity.class);
       intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
       intent.putExtra("jarvis_page",page);
       try{startActivity(intent);}catch(Exception ignored){}
    }
    private void hideMenu(){
       if(menu!=null && manager!=null)try{manager.removeView(menu);}catch(Exception ignored){}
       menu=null;
    }
    @Override public void onDestroy(){
      hideMenu();
      if(manager!=null&&bubble!=null)try{manager.removeView(bubble);}catch(Exception ignored){}
      bubble=null;
      super.onDestroy();
    }
    private class OrbView extends View {
       private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
       OrbView(){super(FloatingService.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
       @Override protected void onDraw(Canvas c){
         super.onDraw(c);
         float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(4);
         long t=System.currentTimeMillis();
         float sweep=(t%6000)/6000f;
         p.setStyle(Paint.Style.FILL);p.setShadowLayer(0,0,0,0);p.setColor(0xe9021228);
         c.drawCircle(cx,cy,r,p);
         p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));
         p.setColor(0xff12d5ff);p.setShadowLayer(dp(8),0,0,0xff11aaff);
         c.drawCircle(cx,cy,r-2,p);
         p.setColor(0xffff2854);p.setStrokeWidth(dp(5));
         c.drawArc(cx-r+dp(5),cy-r+dp(5),cx+r-dp(5),cy+r-dp(5),sweep*360,66,false,p);
         p.setColor(0xff40dfff);p.setStrokeWidth(dp(2));
         c.drawCircle(cx,cy,r*.69f,p);
         Path shape=new Path();shape.moveTo(cx,cy+r*.55f);
         shape.lineTo(cx-r*.60f,cy-r*.42f);shape.lineTo(cx+r*.60f,cy-r*.42f);shape.close();
         p.setStyle(Paint.Style.FILL);p.setColor(0xff12dfff);p.setShadowLayer(dp(8),0,0,0xff0cbbff);c.drawPath(shape,p);
         Path inner=new Path();inner.moveTo(cx,cy+r*.31f);
         inner.lineTo(cx-r*.32f,cy-r*.27f);inner.lineTo(cx+r*.32f,cy-r*.27f);inner.close();
         p.setShadowLayer(0,0,0,0);p.setColor(0xff05284c);c.drawPath(inner,p);
         postInvalidateDelayed(90);
       }
    }
    private class RadialView extends View {
       private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
       RadialView(){super(FloatingService.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
       @Override protected void onDraw(Canvas c){
         float cx=getWidth()/2f,cy=getHeight()/2f;
         float orbit=dp(119);
         p.setShadowLayer(0,0,0,0);p.setStyle(Paint.Style.FILL);
         p.setColor(0xe9020b1b);c.drawCircle(cx,cy,dp(155),p);
         p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xff3edaff);
         p.setShadowLayer(dp(9),0,0,0xff00cfff);c.drawCircle(cx,cy,dp(147),p);
         p.setStrokeWidth(dp(1));p.setColor(0xff146f9e);c.drawCircle(cx,cy,dp(89),p);
         p.setShadowLayer(0,0,0,0);
         for(int i=0;i<8;i++){
           double a=-Math.PI/2+i*Math.PI/4;
           float px=cx+(float)Math.cos(a)*orbit,py=cy+(float)Math.sin(a)*orbit;
           p.setStrokeWidth(dp(2));p.setColor(i==6?0xffff3560:0xff1ecaff);
           p.setShadowLayer(dp(6),0,0,i==6?0xffff2455:0xff12beff);p.setStyle(Paint.Style.STROKE);
           c.drawCircle(px,py,dp(32),p);
           p.setStyle(Paint.Style.FILL);p.setColor(0xff062d4a);c.drawCircle(px,py,dp(30),p);
           p.setShadowLayer(0,0,0,0);p.setColor(0xffe9faff);
           p.setTextAlign(Paint.Align.CENTER);p.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD));
           p.setTextSize(dp(10));c.drawText(LABELS[i],px,py+dp(4),p);
         }
         p.setStyle(Paint.Style.FILL);p.setColor(0xff073e61);
         p.setShadowLayer(dp(15),0,0,0xff00cfff);
         c.drawCircle(cx,cy,dp(54),p);
         p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(3));p.setColor(0xff6beaff);
         c.drawCircle(cx,cy,dp(51),p);
         p.setStyle(Paint.Style.FILL);p.setShadowLayer(dp(10),0,0,0xff4deaff);p.setColor(0xffd9ffff);
         p.setTextSize(dp(48));p.setTextAlign(Paint.Align.CENTER);c.drawText("▽",cx,cy+dp(12),p);
       }
    }
}
