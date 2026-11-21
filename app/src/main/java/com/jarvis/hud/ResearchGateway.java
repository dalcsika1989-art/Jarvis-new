package com.jarvis.hud;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** A source-aware search response from the standard Gemini generateContent API. */
public final class ResearchGateway {
 public static String search(String key,String model,String question) throws Exception {
  if(key==null||key.trim().isEmpty()) throw new IllegalArgumentException("Gemini API-kulcs hiányzik.");
  if(model==null||!model.matches("gemini-[A-Za-z0-9_.-]{2,65}")) throw new IllegalArgumentException("Válassz Google Search eszközt támogató Gemini modellt.");
  if(question==null||question.trim().isEmpty()||question.length()>800) throw new IllegalArgumentException("Érvénytelen keresési kérdés.");
  String endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";
  JSONObject prompt=new JSONObject().put("role","user")
   .put("parts",new JSONArray().put(new JSONObject().put("text",
    "Végezz aktuális Google Search keresést, és foglald össze magyarul pontosan, forrásokkal. "+
    "Ha nem áll rendelkezésre tényleges keresési eredmény, ezt egyértelműen jelezd. Kérdés: "+question)));
  JSONObject data=new JSONObject()
   .put("contents",new JSONArray().put(prompt))
   .put("tools",new JSONArray().put(new JSONObject().put("google_search",new JSONObject())));
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
  try {
   c.setRequestMethod("POST"); c.setConnectTimeout(12000); c.setReadTimeout(65000); c.setDoOutput(true);
   c.setRequestProperty("Content-Type","application/json; charset=utf-8");
   c.setRequestProperty("x-goog-api-key",key);
   try(OutputStream out=c.getOutputStream()){out.write(data.toString().getBytes(StandardCharsets.UTF_8));}
   int status=c.getResponseCode();
   InputStream stream=status<400?c.getInputStream():c.getErrorStream();
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] bytes=new byte[4096];int len,total=0;
   if(stream!=null)try(InputStream in=stream){
    while((len=in.read(bytes))!=-1){total+=len;if(total>900000)throw new IllegalStateException("Túl nagy kutatási válasz.");buffer.write(bytes,0,len);}
   }
   String raw=buffer.toString("UTF-8");
   JSONObject result=new JSONObject(raw);
   if(status>=400){
    JSONObject err=result.optJSONObject("error");
    String msg=err!=null?err.optString("message","Hiba"):"HTTP "+status;
    throw new IllegalStateException("Gemini API "+status+": "+msg);
   }
   JSONArray candidates=result.optJSONArray("candidates");
   if(candidates==null||candidates.length()==0)throw new IllegalStateException("A modell nem adott választ.");
   JSONObject choice=candidates.getJSONObject(0);
   JSONObject content=choice.optJSONObject("content");
   StringBuilder answer=new StringBuilder();
   if(content!=null){
    JSONArray parts=content.optJSONArray("parts");
    if(parts!=null)for(int i=0;i<parts.length();i++){
     JSONObject part=parts.optJSONObject(i);if(part!=null)answer.append(part.optString("text",""));
    }
   }
   JSONArray sources=new JSONArray();
   JSONObject meta=choice.optJSONObject("groundingMetadata");
   if(meta!=null){
    JSONArray chunks=meta.optJSONArray("groundingChunks");
    if(chunks!=null)for(int i=0;i<chunks.length()&&sources.length()<12;i++){
     JSONObject chunk=chunks.optJSONObject(i);if(chunk==null)continue;
     JSONObject web=chunk.optJSONObject("web");if(web==null)continue;
     String url=web.optString("uri","");
     if(url.startsWith("https://")){
      sources.put(new JSONObject().put("title",web.optString("title",url)).put("url",url));
     }
    }
   }
   if(answer.length()==0)throw new IllegalStateException("Üres kutatási válasz.");
   return new JSONObject()
    .put("answer",answer.toString())
    .put("sources",sources)
    .put("grounded",sources.length()>0)
    .put("model",model)
    .toString();
  }finally{c.disconnect();}
 }
}
