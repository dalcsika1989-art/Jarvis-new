package hu.jarvis.app;
import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import android.graphics.*;
import android.view.View;
public final class NeoMonitorView extends View {
 private final Paint p=new Paint(3);
 private final ActivityManager am;
 private final Context ctx;
 public NeoMonitorView(Context c){super(c);ctx=c;am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);}
 private void text(Canvas c,String s,float x,float y,int size,int color){p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTypeface(Typeface.create(Typeface.MONOSPACE,Typeface.BOLD));p.setTextSize(size);p.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,p);}
 protected void onDraw(Canvas c){
  super.onDraw(c);
  float w=getWidth(),h=getHeight(),unit=w/4f;
  ActivityManager.MemoryInfo mem=new ActivityManager.MemoryInfo();am.getMemoryInfo(mem);
  int ram=mem.totalMem>0?(int)(100*(mem.totalMem-mem.availMem)/mem.totalMem):0;
  Intent battery=ctx.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
  int scale=battery==null?100:battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
  int charge=level<0||scale<=0?0:level*100/scale;
  long total=Environment.getDataDirectory().getTotalSpace(),free=Environment.getDataDirectory().getUsableSpace();
  int used=total>0?(int)(100*(total-free)/total):0;
  String[] names={"MEMORY","BATTERY","STORAGE","AI CORE"};
  int[] values={ram,charge,used,hu.jarvis.local.OfflineBrain.available()?100:0};
  for(int i=0;i<4;i++){
   float x=unit*(i+.5f),y=h*.44f,r=Math.min(unit*.33f,h*.32f);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xff12384b);c.drawCircle(x,y,r,p);
   p.setColor(0xff19d8fa);c.drawArc(x-r,y-r,x+r,y+r,-90,360*values[i]/100f,false,p);
   text(c,values[i]+"%",x,y+6,18,0xffb7faff);
   text(c,names[i],x,h*.88f,10,0xff36d5ef);
  }
  if(isAttachedToWindow()&&getVisibility()==View.VISIBLE)postInvalidateDelayed(8000);
 }
}