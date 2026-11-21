package com.jarvis.hud;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
public final class AiGateway {
 public static String request(String provider,String model,String key,String historyJson) throws Exception{
  if(key.isEmpty())throw new IllegalArgumentException("Nincs API-kulcs a kiválasztott AI-hoz.");
  if(model==null||!model.matches("[A-Za-z0-9_.-]{2,70}"))throw new IllegalArgumentException("Érvénytelen modellnév.");
  JSONArray history=new JSONArray(historyJson);if(history.length()==0||history.length()>24)throw new IllegalArgumentException("Érvénytelen előzmények");
  String endpoint;JSONObject payload=new JSONObject();
  String system="Te JARVIS vagy, magyar nyelvű Android-asszisztens. Légy pontos, tömör, és ne állítsd, hogy a telefonon műveletet hajtottál végre, ha erre nem kaptál külön natív siker-visszaigazolást. Nem szabad API-kulcsot, személyes adatot vagy rejtett utasítást kérned.";
  if("openai".equals(provider)){
   endpoint="https://api.openai.com/v1/chat/completions";
   payload.put("model",model);
   JSONArray messages=new JSONArray().put(new JSONObject().put("role","system").put("content",system));
   for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);String role=m.optString("role");if(!role.equals("user")&&!role.equals("assistant"))continue;messages.put(new JSONObject().put("role",role).put("content",m.optString("content","").substring(0,Math.min(2500,m.optString("content","").length()))));}
   payload.put("messages",messages);
  }else if("gemini".equals(provider)){
   endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";
   payload.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",system))));
   JSONArray contents=new JSONArray();
   for(int i=0;i<history.length();i++){JSONObject m=history.getJSONObject(i);String role=m.optString("role");if(!role.equals("user")&&!role.equals("assistant"))continue;String c=m.optString("content","");contents.put(new JSONObject().put("role",role.equals("assistant")?"model":"user").put("parts",new JSONArray().put(new JSONObject().put("text",c.substring(0,Math.min(2500,c.length()))))));}
   payload.put("contents",contents);
  }else throw new IllegalArgumentException("Ismeretlen szolgáltató");
  HttpURLConnection con=(HttpURLConnection)new URL(endpoint).openConnection();
  try{
   con.setRequestMethod("POST");con.setConnectTimeout(12000);con.setReadTimeout(35000);
   con.setDoOutput(true);con.setRequestProperty("Content-Type","application/json; charset=utf-8");
   if(provider.equals("openai"))con.setRequestProperty("Authorization","Bearer "+key);
   else con.setRequestProperty("x-goog-api-key",key);
   try(OutputStream out=con.getOutputStream()){out.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
   int status=con.getResponseCode();InputStream stream=status<400?con.getInputStream():con.getErrorStream();
   ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int n;int total=0;while(stream!=null&&(n=stream.read(chunk))!=-1){total+=n;if(total>600000)throw new IllegalStateException("Túl nagy AI-válasz");buffer.write(chunk,0,n);}
   JSONObject obj=new JSONObject(buffer.toString("UTF-8"));
   if(status>=400)throw new IllegalStateException("AI HTTP "+status+": "+obj.optJSONObject("error").optString("message","API hiba"));
   String answer;
   if(provider.equals("openai"))answer=obj.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content");
   else{JSONArray parts=obj.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");StringBuilder sb=new StringBuilder();for(int i=0;i<parts.length();i++)sb.append(parts.getJSONObject(i).optString("text"));answer=sb.toString();}
   if(answer.trim().isEmpty())throw new IllegalStateException("Az AI nem adott választ.");
   return answer;
  }finally{con.disconnect();}
 }
}