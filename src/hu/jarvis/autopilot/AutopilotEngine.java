package hu.jarvis.autopilot;
import android.content.*;import android.os.Build;
public final class AutopilotEngine {
 private AutopilotEngine(){}
 public static void start(Context c,String task,int interval){Intent i=new Intent(c,AutopilotService.class).setAction(AutopilotService.START).putExtra("task",task);if(Build.VERSION.SDK_INT>=26)c.startForegroundService(i);else c.startService(i);}
 public static void stop(Context c){c.stopService(new Intent(c,AutopilotService.class));}
}