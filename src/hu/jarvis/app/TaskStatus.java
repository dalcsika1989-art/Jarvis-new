package hu.jarvis.app;
import android.app.*;
import android.content.*;
import android.os.Build;
/** Nonintrusive status notification; not a background automation engine. */
public final class TaskStatus {
 private static final String CHANNEL="jarvis_status";
 private TaskStatus(){}
 public static void notify(Context context,String type,String message){
  try {
   NotificationManager manager=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
   if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"JARVIS feladatállapot",NotificationManager.IMPORTANCE_LOW));
   if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
   Intent launch=new Intent(context,MainActivity.class);
   PendingIntent pending=PendingIntent.getActivity(context,0,launch,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
   builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("error".equals(type)?"JARVIS – hiba":"JARVIS – állapot").setContentText(message).setContentIntent(pending).setOnlyAlertOnce(true).setAutoCancel(false);
   manager.notify(2002,builder.build());
  }catch(Exception ignored){}
 }
}
