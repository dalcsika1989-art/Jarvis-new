package com.jarvis.hud;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;
import org.json.JSONArray;
public final class ResearchGateway {
 public static String search(String key,String question) throws Exception{
  if(key.isEmpty())throw new IllegalArgumentException("Gemini API-kulcs szükséges az élő kutatáshoz.");
  if(question==null||question.trim().isEmpty()||question.length()>800)throw new IllegalArgumentException("Érvénytelen kutatási kérdés");
  HttpURLConnection c=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/interactions").openConnection();
  try{
   c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(60000);c.setDoOutput(true);
   c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-goog-api-key",key);
   JSONObject payload=new JSONObject().put("model","gemini-3.8-flash")
    .put("input","Készíts magyar nyelvű, konkrét és tényszerű összefoglalót friss Google Search találatok alapján. Ha nincs megbízható forrás, jelezd. Kérdés: "+question)
    .put("tools",new JSONArray().put(new JSONObject().put("type","google_search")));
   try(OutputStream out=c.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
   int status=c.getResponseCode();InputStream in=status<400?c.getInputStream():c.getErrorStream();
   ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n,total=0;
   while(in!=null&&(n=in.read(b))!=-1){total+=n;if(total>800000)throw new IllegalStateException("Túl nagy kutatási válasz");out.write(b,0,n);}
   JSONObject data=new JSONObject(out.toString("UTF-8"));
   if(status>=400){JSONObject err=data.optJSONObject("error");throw new IllegalStateException("Gemini HTTP "+status+": "+(err==null?data.toString().substring(0,Math.min(200,data.toString().length())):err.optString("message")));}
   JSONObject result=data.optJSONObject("interaction");if(result==null)result=data;
   String answer=result.optString("output_text","");
   JSONArray refs=new JSONArray();JSONArray steps=result.optJSONArray("steps");
   if(steps!=null){for(int i=0;i<steps.length()&&refs.length()<8;i++){JSONObject s=steps.optJSONObject(i);if(s==null)continue;JSONArray content=s.optJSONArray("content");if(content==null)continue;
    for(int j=0;j<content.length()&&refs.length()<8;j++){JSONObject entry=content.optJSONObject(j);if(entry==null)continue;
     if(answer.isEmpty())answer=entry.optString("text",answer);
     JSONArray ann=entry.optJSONArray("annotations");if(ann==null)continue;
     for(int k=0;k<ann.length()&&refs.length()<8;k++){JSONObject a=ann.optJSONObject(k);if(a==null||!"url_citation".equals(a.optString("type")))continue;
      String url=a.optString("url");if(!url.startsWith("https://"))continue;
      refs.put(new JSONObject().put("title",a.optString("title",url)).put("url",url));
     }
    }
   }}
   if(answer.isEmpty())throw new IllegalStateException("Nem érkezett kutatási összefoglaló.");
   return new JSONObject().put("answer",answer).put("sources",refs).toString();
  }finally{c.disconnect();}
 }
}