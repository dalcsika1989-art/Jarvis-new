package hu.jarvis.ai;

import android.app.Activity;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;

/** Gemini Developer API diagnostics. Never writes or returns the API key. */
public final class GeminiConnection {
 private GeminiConnection(){}
 public interface Callback {void done(String json);}
 public static boolean validModel(String name){
  return name!=null && name.matches("gemini-[A-Za-z0-9._-]{1,110}") &&
   !name.toLowerCase(java.util.Locale.ROOT).contains("tts") &&
   !name.toLowerCase(java.util.Locale.ROOT).contains("live") &&
   !name.toLowerCase(java.util.Locale.ROOT).contains("image") &&
   !name.toLowerCase(java.util.Locale.ROOT).contains("embedding");
 }
 private static JSONObject fail(int status,String code,String note){
  JSONObject j=new JSONObject();
  try{
   j.put("ok",false);j.put("http",status);j.put("code",code);
   j.put("message",note);
  }catch(Exception ignored){}
  return j;
 }
 private static String explanation(int status){
  switch(status){
   case 400:return "Hibás kérés. Ellenőrizd a kiválasztott modellt.";
   case 401:return "A Gemini API nem fogadja el a kulcsot. Google AI Studio API-kulcsot használj.";
   case 403:return "A kulcshoz/projekthez nincs megfelelő jogosultság, vagy a szolgáltatás tiltott.";
   case 404:return "Ez a modell nem érhető el ennél a projektnél vagy API-végpontnál.";
   case 429:return "Kvóta vagy sebességkorlát. A kérés nem igazolja, hogy hibás a kulcs.";
   case 500:case 502:case 503:return "Átmeneti Google szerverhiba. Később újra próbálható.";
   default:return "HTTP "+status+": a Gemini szerver elutasította a kérést.";
  }
 }
 private static JSONObject request(String key,String method,String url,JSONObject payload) throws Exception {
  HttpURLConnection c=null;
  try{
   c=(HttpURLConnection)new URL(url).openConnection();
   c.setRequestMethod(method);
   c.setConnectTimeout(12000);c.setReadTimeout(20000);
   c.setRequestProperty("x-goog-api-key",key);
   c.setRequestProperty("Accept","application/json");
   if(payload!=null){
    c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
    try(OutputStream o=c.getOutputStream()){o.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
   }
   int code=c.getResponseCode();
   InputStream in=code<400?c.getInputStream():c.getErrorStream();
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   if(in!=null)try(InputStream stream=in){
    byte[] buffer=new byte[8192];int n;
    while((n=stream.read(buffer))!=-1){
     if(bytes.size()+n>400000)throw new IOException("A válasz túl nagy.");
     bytes.write(buffer,0,n);
    }
   }
   if(code>=400)return fail(code,"GEMINI_HTTP",explanation(code));
   return new JSONObject(bytes.toString("UTF-8"));
  }finally{if(c!=null)c.disconnect();}
 }
 private static void call(Activity activity,String key,boolean test,String model,Callback callback){
  new Thread(()->{
   JSONObject result;
   try{
    if(key==null||key.trim().isEmpty())throw new IllegalArgumentException("Mentsd el előbb a Gemini API-kulcsot.");
    if(test){
     if(!validModel(model))throw new IllegalArgumentException("Érvénytelen Gemini modellnév.");
     JSONObject p=new JSONObject()
       .put("contents",new JSONArray().put(new JSONObject().put("parts",
         new JSONArray().put(new JSONObject().put("text","Válaszolj egyetlen szóval: OK")))))
       .put("generationConfig",new JSONObject().put("maxOutputTokens",80));
     JSONObject returned=request(key,"POST",
       "https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent",p);
     if(!returned.has("candidates"))result=returned;
     else{
      JSONArray cs=returned.optJSONArray("candidates");
      String responseText="";
      if(cs!=null&&cs.length()>0){
       JSONObject candidate=cs.optJSONObject(0);
       JSONObject content=candidate==null?null:candidate.optJSONObject("content");
       JSONArray parts=content==null?null:content.optJSONArray("parts");
       if(parts!=null)for(int i=0;i<parts.length();i++){
        JSONObject fragment=parts.optJSONObject(i);
        if(fragment!=null&&!fragment.optBoolean("thought",false))
         responseText+=fragment.optString("text","");
       }
      }
      result=new JSONObject().put("ok",!responseText.trim().isEmpty()).put("model",model)
       .put("message",responseText.trim().isEmpty()?
         "Az API HTTP 200 választ adott, de nem jött megjeleníthető szöveg. Próbálj másik szöveges modellt.":
         "Sikeres választeszt: "+responseText.trim().substring(0,Math.min(160,responseText.trim().length())));
     }
    }else{
     JSONArray found=new JSONArray();LinkedHashSet<String> unique=new LinkedHashSet<>();
     String page="";
     for(int round=0;round<5;round++){
      String url="https://generativelanguage.googleapis.com/v1beta/models?pageSize=100";
      if(!page.isEmpty())url+="&pageToken="+URLEncoder.encode(page,"UTF-8");
      JSONObject returned=request(key,"GET",url,null);
      if(returned.has("ok")&&!returned.optBoolean("ok")){final String errorJson=returned.toString();
       activity.runOnUiThread(()->callback.done(errorJson));return;}
      JSONArray models=returned.optJSONArray("models");
      if(models!=null)for(int i=0;i<models.length();i++){
       JSONObject entry=models.optJSONObject(i);if(entry==null)continue;
       String name=entry.optString("name","").replaceFirst("^models/","");
       if(!validModel(name)||unique.contains(name))continue;
       JSONArray methods=entry.optJSONArray("supportedGenerationMethods");
       boolean supported=false;
       if(methods!=null)for(int j=0;j<methods.length();j++){
        if("generateContent".equals(methods.optString(j)))supported=true;
       }
       if(!supported)continue;
       unique.add(name);
       found.put(new JSONObject().put("id",name).put("label",entry.optString("displayName",name)));
      }
      page=returned.optString("nextPageToken","");
      if(page.isEmpty())break;
     }
     result=new JSONObject().put("ok",true).put("models",found)
       .put("message",found.length()>0?
          "A kulcs elfogadva. Válassz modellt, majd futtasd a választesztet.":
          "A kulcsot elfogadta a modell-listázás, de nem találtam generateContent-szövegmodellt.");
    }
   }catch(Exception e){
    String note=e instanceof IllegalArgumentException?e.getMessage():
      "Hálózati vagy feldolgozási hiba: "+e.getClass().getSimpleName();
    result=fail(0,"NETWORK",note);
   }
   JSONObject output=result;
   activity.runOnUiThread(()->callback.done(output.toString()));
  },test?"jarvis-gemini-test":"jarvis-gemini-list").start();
 }
 public static void list(Activity activity,String key,Callback callback){call(activity,key,false,"",callback);}
 public static void test(Activity activity,String key,String model,Callback callback){call(activity,key,true,model,callback);}
}