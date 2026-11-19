package hu.jarvis.ai;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
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
import java.util.Locale;

/**
 * Gemini Charon TTS. Raw PCM (audio/L16) is not a WAV file:
 * stream it to AudioTrack. Encoded WAV/MP3 goes to MediaPlayer.
 * Failures are sent to the caller to allow Android TTS fallback.
 */
public final class GeminiVoice {
 private GeminiVoice(){}
 public interface Callback {void done(boolean success,String status);}
 public static void speak(Activity activity,String apiKey,String text,Callback callback){
  new Thread(()->{
   HttpURLConnection conn=null;
   try{
    if(apiKey==null||apiKey.trim().isEmpty())throw new Exception("Nincs Gemini API-kulcs.");
    if(text==null||text.trim().isEmpty())throw new Exception("Nincs felolvasandó szöveg.");
    String speech=text.length()>1100?text.substring(0,1100):text;
    JSONObject generation=new JSONObject()
      .put("responseModalities",new JSONArray().put("AUDIO"))
      .put("speechConfig",new JSONObject().put("voiceConfig",new JSONObject()
        .put("prebuiltVoiceConfig",new JSONObject().put("voiceName","Charon"))));
    JSONObject request=new JSONObject()
      .put("contents",new JSONArray().put(new JSONObject().put("role","user")
        .put("parts",new JSONArray().put(new JSONObject().put("text",speech)))))
      .put("generationConfig",generation);
    URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash-lite-tts:generateContent");
    conn=(HttpURLConnection)url.openConnection();
    conn.setRequestMethod("POST");conn.setDoOutput(true);
    conn.setConnectTimeout(12000);conn.setReadTimeout(50000);
    conn.setRequestProperty("Content-Type","application/json");
    conn.setRequestProperty("x-goog-api-key",apiKey.trim());
    try(OutputStream out=conn.getOutputStream()){out.write(request.toString().getBytes("UTF-8"));}
    int status=conn.getResponseCode();
    InputStream input=status<400?conn.getInputStream():conn.getErrorStream();
    if(input==null)throw new Exception("A Gemini hang API nem adott választ.");
    ByteArrayOutputStream body=new ByteArrayOutputStream();
    byte[] buffer=new byte[8192];int n;
    try(InputStream stream=input){
     while((n=stream.read(buffer))!=-1){
      if(body.size()+n>14000000)throw new Exception("Túl nagy a hangadat.");
      body.write(buffer,0,n);
     }
    }
    if(status>=400){
     String detail=status==401?"A kulcsot nem fogadta el.":
       status==403?"Nincs hozzáférés a hangmodellhez.":
       status==404?"A Charon hangmodell nem érhető el.":
       status==429?"Hang API kvótakorlát.":"Hang API hiba.";
     throw new Exception("HTTP "+status+" – "+detail);
    }
    JSONObject result=new JSONObject(body.toString("UTF-8"));
    JSONArray candidates=result.optJSONArray("candidates");
    if(candidates==null||candidates.length()==0)throw new Exception("A Gemini nem adott hangválaszt.");
    JSONObject first=candidates.optJSONObject(0);
    JSONObject contents=first==null?null:first.optJSONObject("content");
    JSONArray parts=contents==null?null:contents.optJSONArray("parts");
    if(parts==null)throw new Exception("Nincs TTS hangadat.");
    JSONObject inline=null;
    for(int i=0;i<parts.length();i++){
     JSONObject part=parts.optJSONObject(i);
     if(part!=null&&part.has("inlineData")){inline=part.optJSONObject("inlineData");break;}
    }
    if(inline==null)throw new Exception("A hangmodell nem adott lejátszható hangot.");
    String mime=inline.optString("mimeType","").toLowerCase(Locale.ROOT);
    if(!mime.startsWith("audio/"))throw new Exception("Nem hangadat érkezett.");
    byte[] bytes=Base64.decode(inline.getString("data"),Base64.DEFAULT);
    if(bytes.length<100)throw new Exception("A hangadat üres.");
    if(mime.contains("l16")||mime.contains("pcm")){
     int sampleRate=24000;
     int rateIndex=mime.indexOf("rate=");
     if(rateIndex>=0){
      String digits=mime.substring(rateIndex+5).replaceFirst("^([0-9]+).*$","$1");
      try{sampleRate=Integer.parseInt(digits);}catch(Exception ignored){}
     }
     playPcm(activity,bytes,sampleRate,callback);
    }else{
     playEncoded(activity,bytes,mime,callback);
    }
   }catch(Exception e){
    String note=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
    activity.runOnUiThread(()->callback.done(false,note));
   }finally{if(conn!=null)conn.disconnect();}
  },"jarvis-charon-tts").start();
 }
 private static void playPcm(Activity activity,byte[] pcm,int sampleRate,Callback callback){
  AudioTrack track=null;
  try{
   if(sampleRate<8000||sampleRate>96000)sampleRate=24000;
   int min=AudioTrack.getMinBufferSize(sampleRate,
       AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
   if(min<0)throw new Exception("A telefon nem támogatja ezt a PCM hangformátumot.");
   int count=pcm.length-(pcm.length%2);
   int bufferSize=Math.max(min,8192);
   AudioFormat format=new AudioFormat.Builder()
       .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
       .setSampleRate(sampleRate)
       .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
   AudioAttributes attrs=new AudioAttributes.Builder()
       .setUsage(AudioAttributes.USAGE_MEDIA)
       .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
   track=new AudioTrack(attrs,format,bufferSize,AudioTrack.MODE_STREAM,
       android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
   if(track.getState()!=AudioTrack.STATE_INITIALIZED)throw new Exception("PCM hangmotor nem indult.");
   track.play();
   int offset=0;
   while(offset<count){
    int wrote=track.write(pcm,offset,Math.min(bufferSize,count-offset),AudioTrack.WRITE_BLOCKING);
    if(wrote<=0)throw new Exception("PCM lejátszási hiba: "+wrote);
    offset+=wrote;
   }
   final long totalFrames=count/2;
   long deadline=android.os.SystemClock.uptimeMillis()+Math.max(5000,(count*1000L)/(sampleRate*2)+5000);
   while((track.getPlaybackHeadPosition()&0xffffffffL)<totalFrames &&
      android.os.SystemClock.uptimeMillis()<deadline)Thread.sleep(35);
   activity.runOnUiThread(()->callback.done(true,"A Charon hang lejátszása befejeződött."));
  }catch(Exception e){
   String note=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
   activity.runOnUiThread(()->callback.done(false,note));
  }finally{
   if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}
  }
 }
 private static void playEncoded(Activity activity,byte[] bytes,String mime,Callback callback){
  String suffix=mime.contains("mpeg")||mime.contains("mp3")?".mp3":".wav";
  File audio=null;
  try{
   audio=File.createTempFile("jarvis_charon_",suffix,activity.getCacheDir());
   try(FileOutputStream out=new FileOutputStream(audio)){out.write(bytes);}
   final File recording=audio;audio=null;
   activity.runOnUiThread(()->{
    MediaPlayer player=new MediaPlayer();
    try{
     player.setDataSource(recording.getAbsolutePath());
     player.setOnCompletionListener(p->{p.release();recording.delete();});
     player.setOnErrorListener((p,what,extra)->{
      p.release();recording.delete();
      callback.done(false,"A telefon nem tudta dekódolni a Charon-hangot.");
      return true;
     });
     player.prepare();player.start();
     callback.done(true,"A Charon hang megszólalt.");
    }catch(Exception e){
     try{player.release();}catch(Exception ignored){}
     recording.delete();
     callback.done(false,"Hangdekódolási hiba: "+e.getClass().getSimpleName());
    }
   });
  }catch(Exception e){
   if(audio!=null)audio.delete();
   String note=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
   activity.runOnUiThread(()->callback.done(false,note));
  }
 }
}
