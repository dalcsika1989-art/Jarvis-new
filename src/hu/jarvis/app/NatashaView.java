package hu.jarvis.app;
import android.content.Context;
import android.graphics.*;
import android.view.View;
public final class NatashaView extends View {
 private final Paint p=new Paint(3);private final long start=android.os.SystemClock.uptimeMillis();
 public NatashaView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
 private void line(Canvas c,float x,float y,float xx,float yy,int color,float width){p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);p.setShadowLayer(8,0,0,color);c.drawLine(x,y,xx,yy,p);p.clearShadowLayer();}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);c.drawColor(0xff100817);float w=getWidth(),h=getHeight(),cx=w/2f,t=(android.os.SystemClock.uptimeMillis()-start)/1000f;
  int pink=0xffff66dd;float y=h*.26f;
  p.setStyle(Paint.Style.STROKE);p.setColor(0x88ff44cc);p.setStrokeWidth(3);
  for(int i=0;i<5;i++){float ry=h*(.06f+i*.035f);float rx=w*(.25f+i*.055f);c.drawOval(cx-rx,h*.83f-ry,cx+rx,h*.83f+ry,p);}
  p.setStyle(Paint.Style.FILL);p.setColor(0x66ff33aa);p.setShadowLayer(32,0,0,pink);c.drawOval(cx-w*.105f,y-h*.11f,cx+w*.105f,y+h*.11f,p);p.clearShadowLayer();
  line(c,cx-w*.11f,y+h*.11f,cx-w*.26f,h*.55f,pink,6);line(c,cx+w*.11f,y+h*.11f,cx+w*.26f,h*.55f,pink,6);
  line(c,cx-w*.10f,y+h*.12f,cx-w*.13f,h*.66f,pink,5);line(c,cx+w*.10f,y+h*.12f,cx+w*.13f,h*.66f,pink,5);
  line(c,cx-w*.13f,h*.66f,cx-w*.08f,h*.81f,pink,5);line(c,cx+w*.13f,h*.66f,cx+w*.08f,h*.81f,pink,5);
  p.setColor(0x66ff77df);p.setStyle(Paint.Style.FILL);Path torso=new Path();torso.moveTo(cx-w*.11f,y+h*.11f);torso.lineTo(cx+w*.11f,y+h*.11f);torso.lineTo(cx+w*.15f,h*.62f);torso.lineTo(cx-w*.15f,h*.62f);torso.close();c.drawPath(torso,p);
  p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1);
  for(int i=0;i<28;i++){float xx=(float)(cx+Math.sin(i*2.4+t*.7)*w*.4);float yy=(float)((i*47+t*13)%h);p.setColor(0x88ff77dd);c.drawCircle(xx,yy,1+i%3,p);}
  p.setStyle(Paint.Style.FILL);p.setColor(0xffffb8ee);p.setTypeface(Typeface.MONOSPACE);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(27);c.drawText("NATASHA // HOLOGRAM",cx,h*.94f,p);
  postInvalidateDelayed(50);
 }
}