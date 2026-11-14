package hu.jarvis.autopilot;
import android.app.*;import android.content.*;import android.os.*;import java.util.concurrent.*;
public final class AutopilotService extends Service {
 public static final String START="hu.jarvis.autopilot.START",STOP="hu.jarvis.autopilot.STOP";
 private static final String CHANNEL="jarvis_autopilot";
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private Future<?> job;
 public void onCreate(){super.onCreate();if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"JARVIS Autopilot",NotificationManager.IMPORTANCE_LOW));}
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  if(STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
  if(!START.equals(intent.getAction()))return START_NOT_STICKY;
  String task=intent.getStringExtra("task");if(task==null)task="";
  startForeground(71,note("Feladat fogadva: "+task));
  if(job!=null)job.cancel(true);
  job=executor.submit(()->{String msg="Nincs csatlakoztatott keresőmotor. Feladat nem lett végrehajtva.";((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,note(msg));});
  return START_NOT_STICKY;
 }
 private Notification note(String message){
  Intent stop=new Intent(this,AutopilotService.class).setAction(STOP);
  PendingIntent pi=PendingIntent.getService(this,72,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync).setContentTitle("JARVIS – Autopilot").setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).addAction(android.R.drawable.ic_delete,"Leállítás",pi).build();
 }
 public void onDestroy(){if(job!=null)job.cancel(true);executor.shutdownNow();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}