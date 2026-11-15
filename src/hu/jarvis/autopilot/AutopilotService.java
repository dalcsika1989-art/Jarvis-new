package hu.jarvis.autopilot;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
public final class AutopilotService extends Service {
 public static final String START="hu.jarvis.autopilot.START",STOP="hu.jarvis.autopilot.STOP";
 private static final String CHANNEL="jarvis_autopilot";
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private Future<?> job;
 private volatile boolean running=false;
 private static final String[] QUERIES={"elektro scooter","zu verschenken"};
 public void onCreate(){super.onCreate();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"JARVIS háttérfigyelés",NotificationManager.IMPORTANCE_LOW));}
 public int onStartCommand(Intent intent,int flags,int startId){
  if(intent==null)return START_NOT_STICKY;
  if(STOP.equals(intent.getAction())){running=false;if(job!=null)job.cancel(true);stopSelf();return START_NOT_STICKY;}
  if(!START.equals(intent.getAction()))return START_NOT_STICKY;
  startForeground(71,note("Kleinanzeigen háttérfigyelés indul"));
  running=false;if(job!=null)job.cancel(true);
  running=true;
  job=executor.submit(()->monitor());
  return START_NOT_STICKY;
 }
 private void monitor(){
  while(running&&!Thread.currentThread().isInterrupted()){
   for(String query:QUERIES){
    if(!running)return;
    try{
     Set<String> ids=fetch(query);
     if(ids.isEmpty()){status("Nincs feldolgozható találat: "+query);continue;}
     android.content.SharedPreferences prefs=getSharedPreferences("monitor",MODE_PRIVATE);
     String key="seen_"+query;
     Set<String> old=prefs.getStringSet(key,null);
     if(old!=null){
      int added=0;for(String id:ids)if(!old.contains(id))added++;
      if(added>0)alert(query,added);
     }
     prefs.edit().putStringSet(key,new HashSet<String>(ids)).apply();
     status("Ellenőrizve: "+query+" ("+ids.size()+" találat)");
    }catch(Exception e){status("Keresési hiba: "+query+" – "+e.getClass().getSimpleName());}
   }
   try{Thread.sleep(15*60*1000L);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
  }
 }
 private Set<String> fetch(String query)throws IOException{
  String url="https://www.kleinanzeigen.de/s-suchanfrage.html?keywords="+Uri.encode(query);
  HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
  conn.setConnectTimeout(12000);conn.setReadTimeout(12000);
  conn.setRequestProperty("User-Agent","Mozilla/5.0 (Android; JARVIS monitor)");
  try{
   if(conn.getResponseCode()!=200)throw new IOException("HTTP "+conn.getResponseCode());
   StringBuilder html=new StringBuilder();
   try(BufferedReader in=new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"))){
    char[] b=new char[4096];int n;while((n=in.read(b))!=-1&&html.length()<1000000)html.append(b,0,n);
   }
   Matcher m=Pattern.compile("/s-anzeige/[^\\\"'<>\\s]+").matcher(html);
   Set<String> found=new LinkedHashSet<String>();
   while(m.find()&&found.size()<100){String v=m.group();int end=v.indexOf('?');if(end>=0)v=v.substring(0,end);found.add(v);}
   return found;
  }finally{conn.disconnect();}
 }
 private void alert(String query,int count){
  String url="https://www.kleinanzeigen.de/s-suchanfrage.html?keywords="+Uri.encode(query);
  Intent open=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
  PendingIntent pi=PendingIntent.getActivity(this,query.hashCode(),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification n=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
   .setContentTitle("JARVIS: "+count+" új találat").setContentText(query+" – ellenőrizd a hirdetéseket")
   .setContentIntent(pi).setAutoCancel(true).build();
  ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(query.hashCode(),n);
 }
 private void status(String msg){((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(71,note(msg));}
 private Notification note(String msg){
  Intent stop=new Intent(this,AutopilotService.class).setAction(STOP);
  PendingIntent pi=PendingIntent.getService(this,72,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  return new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_sync)
   .setContentTitle("JARVIS – háttérfigyelés").setContentText(msg)
   .setStyle(new Notification.BigTextStyle().bigText(msg))
   .addAction(android.R.drawable.ic_delete,"Leállítás",pi).build();
 }
 public void onDestroy(){running=false;if(job!=null)job.cancel(true);executor.shutdownNow();super.onDestroy();}
 public IBinder onBind(Intent i){return null;}
}
