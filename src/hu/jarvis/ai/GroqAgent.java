package hu.jarvis.ai;
import android.app.Activity;
import org.json.*;
import java.io.*;
import java.net.*;
/** Groq text-only provider. Voice is generated separately on the device. */
public final class GroqAgent {
 private GroqAgent(){}
 public static void run(Activity activity,String key,String question,GeminiAgent.Callback callback){
  if(key==null||key.trim().isEmpty()){activity.runOnUiThread(()->callback.done("Groq-hiba: Nincs API-kulcs."));return;}
  new Thread(()->{
   HttpURLConnection conn=null;String result;
   try{
    JSONObject body=new JSONObject().put("model","llama-3.3-70b-versatile")
      .put("max_tokens",384).put("temperature",0.4)
      .put("messages",new JSONArray()
        .put(new JSONObject().put("role","system").put("content","Te JARVIS vagy. Magyarul, tömören és érthetően válaszolj. Ne állítsd, hogy elvégeztél egy telefonműveletet, ha nincs róla visszaigazolás."))
        .put(new JSONObject().put("role","user").put("content",question)));
    conn=(HttpURLConnection)new URL("https://api.groq.com/openai/v1/chat/completions").openConnection();
    conn.setRequestMethod("POST");conn.setDoOutput(true);
    conn.setConnectTimeout(12000);conn.setReadTimeout(35000);
    conn.setRequestProperty("Content-Type","application/json");
    conn.setRequestProperty("Authorization","Bearer "+key.trim());
    try(OutputStream out=conn.getOutputStream()){out.write(body.toString().getBytes("UTF-8"));}
    int status=conn.getResponseCode();
    InputStream input=status<400?conn.getInputStream():conn.getErrorStream();
    ByteArrayOutputStream buffer=new ByteArrayOutputStream();
    if(input!=null)try(InputStream in=input){byte[] chunk=new byte[4096];int n;while((n=in.read(chunk))!=-1){if(buffer.size()+n>240000)throw new IOException("Túl nagy válasz.");buffer.write(chunk,0,n);}}
    if(status>=400)throw new IOException("HTTP "+status+(status==429?" – Groq kvótakorlát.":status==401?" – Hibás Groq API-kulcs.":" – Groq API-hiba."));
    JSONObject json=new JSONObject(buffer.toString("UTF-8"));
    JSONArray choices=json.getJSONArray("choices");
    result=choices.getJSONObject(0).getJSONObject("message").optString("content","").trim();
    if(result.isEmpty())throw new IOException("Üres Groq válasz.");
   }catch(Exception e){result="Groq-hiba: "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
   finally{if(conn!=null)conn.disconnect();}
   final String answer=result;activity.runOnUiThread(()->callback.done(answer));
  },"jarvis-groq-chat").start();
 }
}
