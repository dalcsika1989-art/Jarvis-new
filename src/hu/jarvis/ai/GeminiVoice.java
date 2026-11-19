package hu.jarvis.ai;

import android.app.Activity;
import android.media.MediaPlayer;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class GeminiVoice {
 public interface Callback { void done(boolean success,String status); }
 public static void speak(Activity activity,String apiKey,String text,Callback callback){
  new Thread(()->{
   HttpURLConnection conn=null;
   File audio=null;
   try{
    if(apiKey==null||apiKey.trim().isEmpty())throw new Exception("Nincs Gemini API-kulcs.");
    if(text==null||text.trim().isEmpty())throw new Exception("Üres szöveg.");
    String speech=text.length()>1200?text.substring(0,1200):text;
    JSONObject cfg=new JSONObject();
    cfg.put("responseModalities",new JSONArray().put("AUDIO"));
    cfg.put("speechConfig",new JSONObject().put("voiceConfig",new JSONObject()
       .put("prebuiltVoiceConfig",new JSONObject().put("voiceName","Charon"))));
    JSONObject request=new JSONObject();
    request.put("contents",new JSONArray().put(new JSONObject().put("role","user")
       .put("parts",new JSONArray().put(new JSONObject().put("text",speech)))));
    request.put("generationConfig",cfg);
    URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-lite-tts:generateContent");
    conn=(HttpURLConnection)url.openConnection();
    conn.setRequestMethod("POST");conn.setDoOutput(true);
    conn.setConnectTimeout(12000);conn.setReadTimeout(50000);
    conn.setRequestProperty("Content-Type","application/json");
    conn.setRequestProperty("x-goog-api-key",apiKey);
    try(OutputStream out=conn.getOutputStream()){out.write(request.toString().getBytes("UTF-8"));}
    int status=conn.getResponseCode();
    InputStream input=(status<400?conn.getInputStream():conn.getErrorStream());
    if(input==null)throw new Exception("A beszéd API nem adott választ.");
    ByteArrayOutputStream body=new ByteArrayOutputStream();
    byte[] buffer=new byte[8192];int read;
    try(InputStream in=input){
     while((read=in.read(buffer))!=-1){
      if(body.size()+read>14000000)throw new Exception("A válasz túl nagy.");
      body.write(buffer,0,read);
     }
    }
    if(status>=400)throw new Exception("Gemini TTS HTTP "+status);
    JSONObject result=new JSONObject(body.toString("UTF-8"));
    JSONArray parts=result.getJSONArray("candidates").getJSONObject(0)
      .getJSONObject("content").getJSONArray("parts");
    JSONObject inline=null;
    for(int i=0;i<parts.length();i++)if(parts.getJSONObject(i).has("inlineData")){
      inline=parts.getJSONObject(i).getJSONObject("inlineData");break;
    }
    if(inline==null)throw new Exception("Nincs hangadat a Gemini válaszában.");
    String mime=inline.optString("mimeType","");
    if(!mime.startsWith("audio/"))throw new Exception("A válasz nem hang.");
    byte[] bytes=Base64.decode(inline.getString("data"),Base64.DEFAULT);
    if(bytes.length<100)throw new Exception("A hangadat üres.");
    audio=File.createTempFile("jarvis_charon_",".wav",activity.getCacheDir());
    try(FileOutputStream out=new FileOutputStream(audio)){out.write(bytes);}
    final File recording=audio;audio=null;
    activity.runOnUiThread(()->{
     try{
      MediaPlayer player=new MediaPlayer();
      player.setDataSource(recording.getAbsolutePath());
      player.setOnCompletionListener(p->{p.release();recording.delete();});
      player.setOnErrorListener((p,what,extra)->{p.release();recording.delete();return true;});
      player.prepare();player.start();
      callback.done(true,"Gemini Charon hang elindult.");
     }catch(Exception ex){recording.delete();callback.done(false,"Charon lejátszás: "+ex.getClass().getSimpleName());}
    });
   }catch(Exception e){
    final String msg=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    activity.runOnUiThread(()->callback.done(false,msg));
   }finally{
    if(conn!=null)conn.disconnect();
    if(audio!=null)audio.delete();
   }
  },"jarvis-charon-tts").start();
 }
}