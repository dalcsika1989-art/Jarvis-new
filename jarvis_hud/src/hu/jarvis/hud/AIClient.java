package hu.jarvis.hud;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * JARVIS authenticated localhost AI client. Gemini API key is NEVER in this APK.
 * The Termux service stores it in its private ai_config.json and proxies chat calls.
 */
public final class AIClient {
    private final Activity activity;
    private final WebView web;
    private final SharedPreferences prefs;
    private static final String BASE="http://127.0.0.1:8765";
    AIClient(Activity activity,WebView web){
       this.activity=activity;
       this.web=web;
       prefs=activity.getSharedPreferences("jarvis_termux",Context.MODE_PRIVATE);
    }
    @JavascriptInterface public void status(){sendAsync("status","GET","/api/ai/status",null);}
    @JavascriptInterface public void history(){sendAsync("history","GET","/api/ai/history",null);}
    @JavascriptInterface public void clear(){
       sendAsync("clear","POST","/api/ai/clear",new JSONObject());
    }
    @JavascriptInterface public void ask(String prompt){
       final String message=prompt==null?"":prompt.trim();
       if(message.isEmpty()||message.length()>4000){
         emit("chat",false,error("Kérdés hossza 1–4000 karakter."));
         return;
       }
       try{
         JSONObject body=new JSONObject().put("message",message);
         sendAsync("chat","POST","/api/ai/chat",body);
       }catch(Exception ex){emit("chat",false,error("Nem sikerült feldolgozni a kérdést."));}
    }
    private void sendAsync(final String action,final String method,final String path,final JSONObject data){
       new Thread(new Runnable(){@Override public void run(){
          String token=prefs.getString("token","");
          if(token.length()<24){
             emit(action,false,error("A JARVIS Termux-motor nincs párosítva."));
             return;
          }
          try{
             emit(action,true,request(method,path,data,token));
          }catch(Exception ex){
             String detail=ex.getMessage();
             if(detail==null)detail=ex.getClass().getSimpleName();
             if(detail.contains("Connection refused")||detail.contains("Failed to connect"))
                detail="A Termux szerver nem érhető el. Ellenőrizd: sv status jarvis";
             if(detail.length()>430)detail=detail.substring(0,430);
             emit(action,false,error(detail));
          }
       }},"JarvisAi"+action).start();
    }
    private static JSONObject request(String method,String path,JSONObject data,String token) throws Exception{
       HttpURLConnection con=(HttpURLConnection)new URL(BASE+path).openConnection();
       try{
          con.setRequestMethod(method);
          con.setConnectTimeout(2600);
          con.setReadTimeout(path.endsWith("/chat")?38000:4600);
          con.setUseCaches(false);
          con.setRequestProperty("Authorization","Bearer "+token);
          con.setRequestProperty("Accept","application/json");
          if("POST".equals(method)){
             con.setDoOutput(true);
             con.setRequestProperty("Content-Type","application/json; charset=utf-8");
             try(OutputStream out=con.getOutputStream()){
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));
             }
          }
          int status=con.getResponseCode();
          InputStream source=status>=400?con.getErrorStream():con.getInputStream();
          if(source==null)throw new IllegalStateException("HTTP "+status);
          ByteArrayOutputStream buf=new ByteArrayOutputStream();
          try(InputStream in=source){
             byte[] block=new byte[4096];int n;
             while((n=in.read(block))!=-1){
                buf.write(block,0,n);
                if(buf.size()>150000)throw new IllegalStateException("A szerverválasz túl nagy.");
             }
          }
          JSONObject response=new JSONObject(buf.toString("UTF-8"));
          if(status<200||status>=300)
             throw new IllegalStateException(response.optString("error","HTTP "+status));
          return response;
       }finally{con.disconnect();}
    }
    private static JSONObject error(String msg){
       JSONObject obj=new JSONObject();
       try{obj.put("error",msg);}catch(Exception ignored){}
       return obj;
    }
    private void emit(String action,boolean ok,JSONObject obj){
       try{
          JSONObject response=new JSONObject().put("action",action).put("ok",ok).put("data",obj);
          final String code="if(window.JarvisAIUI&&window.JarvisAIUI.receive)window.JarvisAIUI.receive("+JSONObject.quote(response.toString())+");";
          activity.runOnUiThread(new Runnable(){@Override public void run(){
             try{web.evaluateJavascript(code,null);}catch(Exception ignored){}
          }});
       }catch(Exception ignored){}
    }
}