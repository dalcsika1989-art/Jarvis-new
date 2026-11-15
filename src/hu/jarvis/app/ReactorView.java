package hu.jarvis.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class ReactorView extends View {
 private final Paint p=new Paint(3);
 private long started=android.os.SystemClock.uptimeMillis();
 private boolean active=false;
 public ReactorView(Context c){super(c);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
 public void setActive(boolean value){active=value;invalidate();}
 private void circle(Canvas c,float x,float y,float r,int color,float stroke){
  p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);c.drawCircle(x,y,r,p);
 }
 private void arc(Canvas c,float r,float start,float sweep,int color,float width){
  p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(width);c.drawArc(-r,-r,r,r,start,sweep,false,p);
 }
 protected void onDraw(Canvas canvas){
  super.onDraw(canvas);
  float w=getWidth(),h=getHeight(),cx=w/2f,cy=h/2f;
  canvas.drawColor(0xff06101c);
  float t=(android.os.SystemClock.uptimeMillis()-started)/1000f;
  float r=Math.min(w,h)*.31f;
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);p.setColor(0x3324c7e8);
  for(int i=0;i<9;i++){float xx=w*i/8f;canvas.drawLine(xx,0,xx,h,p);}
  for(int i=0;i<10;i++){float yy=h*i/9f;canvas.drawLine(0,yy,w,yy,p);}
  canvas.save();canvas.translate(cx,cy);
  circle(canvas,0,0,r*1.31f,0x4467e6ff,1);
  circle(canvas,0,0,r*1.12f,0xff086c91,2);
  circle(canvas,0,0,r*.78f,0xff0abedc,3);
  circle(canvas,0,0,r*.42f,0xff44e8ff,2);
  for(int i=0;i<48;i++){
   double a=Math.PI*2*i/48+t*.11;
   float x1=(float)Math.cos(a)*r*1.16f,y1=(float)Math.sin(a)*r*1.16f;
   float x2=(float)Math.cos(a)*r*(i%4==0?1.27f:1.21f),y2=(float)Math.sin(a)*r*(i%4==0?1.27f:1.21f);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(i%4==0?3:1);p.setColor(0xff17c9ed);canvas.drawLine(x1,y1,x2,y2,p);
  }
  arc(canvas,r*1.04f,t*30,78,0xff03e4ff,7);
  arc(canvas,r*1.04f,180+t*30,78,0xff03e4ff,7);
  arc(canvas,r*.89f,-t*43,125,0xff69f3ff,4);
  arc(canvas,r*.89f,180-t*43,125,0xff69f3ff,4);
  // Original neon triangular reactor inspired by the supplied HUD reference.
  for(int layer=0;layer<3;layer++){
   float tr=r*(.56f+layer*.13f);
   Path triangle=new Path();
   for(int k=0;k<3;k++){double a=-Math.PI/2+k*2*Math.PI/3+t*(layer%2==0?.035:-.035);float x=(float)Math.cos(a)*tr,y=(float)Math.sin(a)*tr;if(k==0)triangle.moveTo(x,y);else triangle.lineTo(x,y);}
   triangle.close();p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(layer==0?7:3);p.setColor(layer==0?0xffb5f9ff:0xff17cbe9);canvas.drawPath(triangle,p);
  }
  float pulse=(float)(.88+.12*Math.sin(t*3.8));
  p.setStyle(Paint.Style.FILL);p.setColor(active?0x6634dfff:0x4436b5c6);canvas.drawCircle(0,0,r*.19f*pulse,p);
  p.setColor(0xffe3fdff);p.setTypeface(Typeface.create("sans-serif-light",Typeface.BOLD));p.setTextAlign(Paint.Align.CENTER);p.setTextSize(r*.15f);canvas.drawText("J A R V I S",0,r*.035f,p);
  p.setColor(0xff5df1ff);p.setTextSize(r*.105f);canvas.drawText(active?"AI ACTIVE":"JARVIS READY",0,r*.16f,p);
  canvas.restore();
  p.setTypeface(Typeface.MONOSPACE);p.setTextAlign(Paint.Align.LEFT);p.setTextSize(12);p.setColor(0xff5ccfe9);p.setStyle(Paint.Style.FILL);
  canvas.drawText("NEO // LOCAL CORE",15,23,p);canvas.drawText("HUD  •  v1.0",15,h-15,p);
  if(isAttachedToWindow()&&getVisibility()==View.VISIBLE)postInvalidateDelayed(active?50:180);
 }
}