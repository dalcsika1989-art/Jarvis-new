package hu.jarvis.ai;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Plain-text first Gemini chat. Always reports success or an actionable error. */
public final class GeminiAgent {
 private GeminiAgent(){}
 public interface Callback {void done(String message);}
 public static void run(Activity activity,String key,String instruction,Callback callback){
  run(activity,key,"gemini-3.8-flash",instruction,callback);
 }
 public static void run(Activity activity,String key,String model,String instruction,Callback callback){
  if(!GeminiConnection.validModel(model)){
   activity.runOnUiThread(()->callback.done("Gemini-hiba: Válassz érvényes szöveges modellt a Gemini API menüben."));
   return;
  }
  if(key==null||key.trim().isEmpty()){
   activity.runOnUiThread(()->callback.done("Gemini-hiba: Nincs mentett API-kulcs."));
   return;
  }
  new Thread(()->{
   HttpURLConnection connection=null;
   String message;
   try{
    JSONObject userPart=new JSONObject().put("text",
       "Te JARVIS vagy, magyarul válaszoló digitális asszisztens. "+
       "Válaszolj közérthetően, közvetlenül és egyszerű SZÖVEGBEN; "+
       "ne használj JSON-t, kódblokkot vagy más strukturált formátumot, "+
       "kivéve ha a felhasználó kifejezetten kéri. "+
       "Ne állítsd, hogy telefonos műveletet hajtottál végre, ha erre nincs visszaigazolás.\n"+
       "Felhasználó: "+instruction);
    JSONObject body=new JSONObject()
       .put("contents",new JSONArray().put(new JSONObject()
          .put("role","user")
          .put("parts",new JSONArray().put(userPart))));
    // Avoid forcing application/json: plain-text answers must remain usable.
    URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
    connection=(HttpURLConnection)url.openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(15000);
    connection.setReadTimeout(45000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type","application/json");
    connection.setRequestProperty("Accept","application/json");
    connection.setRequestProperty("x-goog-api-key",key.trim());
    try(OutputStream stream=connection.getOutputStream()){
     stream.write(body.toString().getBytes("UTF-8"));
    }
    int http=connection.getResponseCode();
    InputStream input=http>=400?connection.getErrorStream():connection.getInputStream();
    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    if(input!=null)try(InputStream stream=input){
     byte[] buf=new byte[8192];int n;
     while((n=stream.read(buf))!=-1){
      if(bytes.size()+n>320000)throw new IOException("Túl nagy a Gemini válasza.");
      bytes.write(buf,0,n);
     }
    }
    String raw=bytes.toString("UTF-8");
    if(http>=400){
     String reason;
     switch(http){
      case 400:reason="Hibás kérés vagy nem támogatott modell.";break;
      case 401:reason="A Gemini API-kulcsot nem fogadta el.";break;
      case 403:reason="A kulcshoz nincs megfelelő API-jogosultság.";break;
      case 404:reason="A kiválasztott modell nem érhető el ezzel a kulccsal.";break;
      case 429:reason="Kvótakorlát vagy túl sok kérés.";break;
      case 500:case 502:case 503:reason="Átmeneti Gemini szerverhiba.";break;
      default:reason="A Gemini API HTTP "+http+" hibát adott.";
     }
     throw new IOException("HTTP "+http+": "+reason+" Ellenőrizd a Gemini-modelltesztet.");
    }
    JSONObject response=new JSONObject(raw);
    JSONArray candidates=response.optJSONArray("candidates");
    if(candidates==null||candidates.length()==0){
     String blocked=response.optJSONObject("promptFeedback")==null?"":
        response.optJSONObject("promptFeedback").optString("blockReason","");
     throw new IOException(blocked.isEmpty()?
         "A Gemini nem adott megjeleníthető választ.":
         "A Gemini blokkolta a kérést: "+blocked);
    }
    JSONObject candidate=candidates.optJSONObject(0);
    JSONObject content=candidate==null?null:candidate.optJSONObject("content");
    JSONArray parts=content==null?null:content.optJSONArray("parts");
    StringBuilder reply=new StringBuilder();
    if(parts!=null)for(int i=0;i<parts.length();i++){
     JSONObject part=parts.optJSONObject(i);
     if(part==null||part.optBoolean("thought",false))continue;
     String fragment=part.optString("text","");
     if(!fragment.trim().isEmpty()){
      if(reply.length()>0)reply.append("\n");
      reply.append(fragment);
     }
    }
    String answer=reply.toString().trim();
    if(answer.isEmpty()){
     String reason=candidate==null?"ismeretlen":candidate.optString("finishReason","ismeretlen");
     throw new IOException("A Gemini üres választ adott (finishReason: "+reason+").");
    }
    // Backwards compatibility with earlier JARVIS structured responses.
    String action="none",target="";
    if(answer.startsWith("{")){
     try{
      JSONObject cmd=new JSONObject(answer);
      if(cmd.has("reply")){
       action=cmd.optString("action","none");
       target=cmd.optString("url","");
       answer=cmd.optString("reply","").trim();
      }
     }catch(Exception ignored){ /* Not JSON: show original text. */ }
    }
    if(answer.isEmpty())answer="A Gemini üres szöveges választ adott.";
    final String responseText=answer;
    final String command=action, link=target;
    activity.runOnUiThread(()->{
     String actionInfo=execute(activity,command,link);
     callback.done(responseText+(actionInfo.isEmpty()?"":"\n"+actionInfo));
    });
    return;
   }catch(Exception e){
    String explanation=e.getMessage();
    if(explanation==null||explanation.length()>240)explanation=e.getClass().getSimpleName();
    message="Gemini-hiba: "+explanation;
   }finally{
    if(connection!=null)connection.disconnect();
   }
   final String error=message;
   activity.runOnUiThread(()->callback.done(error));
  },"jarvis-gemini-chat").start();
 }
 private static String execute(Activity activity,String action,String url){
  try{
   switch(action){
    case "open_url":
     Uri uri=Uri.parse(url);
     if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)
      return "Hibás vagy nem biztonságos webcím.";
     activity.startActivity(new Intent(Intent.ACTION_VIEW,uri));
     return "Weboldal megnyitásának kérése elküldve.";
    case "home":case "back":case "scroll":
     return "A javasolt telefonműveletet nem hajtottam végre: "+action+
       ". Ezt külön a JARVIS telefonvezérlőjének kell indítania.";
    default:return "";
   }
  }catch(Exception e){return "Nem sikerült a művelet: "+e.getClass().getSimpleName();}
 }
}
