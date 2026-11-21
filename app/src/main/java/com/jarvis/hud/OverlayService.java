package com.jarvis.hud;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class OverlayService extends Service {
 public static volatile boolean isRunning=false;
 private WindowManager wm;private TextView bubble;private WindowManager.LayoutParams params;
 @Override public void onCreate(){super.onCreate();
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel("jarvis_overlay","JARVIS lebegő HUD",NotificationManager.IMPORTANCE_LOW));
  Notification n=new Notification.Builder(this,"jarvis_overlay").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("JARVIS HUD aktív").setContentText("A lebegő ikon a JARVIS alkalmazást nyitja meg.").build();
  startForeground(9,n);
  if(Build.VERSION.SDK_INT>=23&&!android.provider.Settings.canDrawOverlays(this)){stopSelf();return;}
  wm=(WindowManager)getSystemService(WINDOW_SERVICE);
  bubble=new TextView(this);bubble.setText("◉");bubble.setTextSize(35);bubble.setGravity(Gravity.CENTER);bubble.setTextColor(Color.CYAN);
  GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff102c41,0xff06111e});
  bg.setCornerRadius(100);bg.setStroke(3,Color.CYAN);bubble.setBackground(bg);
  params=new WindowManager.LayoutParams(72*getResources().getDisplayMetrics().density>0?(int)(72*getResources().getDisplayMetrics().density):72,(int)(72*getResources().getDisplayMetrics().density),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);
  params.gravity=Gravity.TOP|Gravity.START;params.x=20;params.y=180;
  bubble.setOnTouchListener(new View.OnTouchListener(){float downX,downY;int startX,startY;long startTime;
   @Override public boolean onTouch(View v,MotionEvent e){
    switch(e.getAction()){case MotionEvent.ACTION_DOWN:downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;startTime=System.currentTimeMillis();return true;
    case MotionEvent.ACTION_MOVE:params.x=startX+(int)(e.getRawX()-downX);params.y=startY+(int)(e.getRawY()-downY);wm.updateViewLayout(bubble,params);return true;
    case MotionEvent.ACTION_UP:if(Math.abs(e.getRawX()-downX)<15&&Math.abs(e.getRawY()-downY)<15&&System.currentTimeMillis()-startTime<600){Intent i=new Intent(OverlayService.this,MainActivity.class);i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(i);}return true;}return false;
   }
  });
  try{wm.addView(bubble,params);isRunning=true;}catch(Exception e){isRunning=false;stopSelf();}
 }
 @Override public int onStartCommand(Intent i,int flags,int id){return START_NOT_STICKY;}
 @Override public void onDestroy(){isRunning=false;if(wm!=null&&bubble!=null){try{wm.removeView(bubble);}catch(Exception ignored){}}super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}