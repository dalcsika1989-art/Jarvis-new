package hu.jarvis.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class NeoGlobeView extends View {
 private final Paint p=new Paint(3);
 private final long start=android.os.SystemClock.uptimeMillis();
 public NeoGlobeView(Context c){super(c);}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w*.5f,cy=h*.49f,r=Math.min(w*.35f,h*.43f);
  p.setColor(0xff06101b);p.setStyle(Paint.Style.FILL);c.drawColor(0xff06101b);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xff23b9dc);c.drawCircle(cx,cy,r,p);
  double t=(android.os.SystemClock.uptimeMillis()-start)/2300.0;
  for(int lat=-60;lat<=60;lat+=30){
   float yy=(float)Math.sin(Math.toRadians(lat))*r;float rr=(float)Math.cos(Math.toRadians(lat))*r;
   p.setColor(0x9946d8ef);p.setStrokeWidth(1);
   c.drawOval(cx-rr,cy+yy-r*.10f,cx+rr,cy+yy+r*.10f,p);
  }
  for(int lon=0;lon<12;lon++){
   double a=lon*Math.PI/6+t;float width=(float)Math.abs(Math.cos(a))*r;
   p.setColor(Math.sin(a)>0?0xaa3fe9ff:0x443fe9ff);
   c.drawOval(cx-width,cy-r,cx+width,cy+r,p);
  }
  p.setStyle(Paint.Style.FILL);p.setColor(0xff91f5ff);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.MONOSPACE);p.setTextSize(23);
  c.drawText("GLOBAL NETWORK // VISUALIZATION",cx,h-18,p);
  postInvalidateDelayed(50);
 }
}