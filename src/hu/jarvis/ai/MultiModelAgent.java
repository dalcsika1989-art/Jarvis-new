package hu.jarvis.ai;

import android.app.Activity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Explicitly selected provider/model: no auto-fallback or background paid API use. */
public final class MultiModelAgent {
 private MultiModelAgent(){}
 public interface Callback{void done(String reply);}
 public static final String[] LABELS={
   "Helyi AI – offline GGUF",
   "Gemini 2.5 Flash",
   "Gemini 2.5 Flash-Lite",
   "OpenAI GPT-4.1 mini",
   "OpenAI GPT-4o mini",
   "Claude Haiku 4.5",
   "Groq Llama 3.3 70B"
 };
 private static final String[] PROVIDERS={"local","gemini","gemini","openai","openai","anthropic","groq"};
 private static final String[] MODELS={"local","gemini-2.5-flash","gemini-2.5-flash-lite","gpt-4.1-mini","gpt-4o-mini","claude-haiku-4-5-20251001","llama-3.3-70b-versatile"};
 private static final String SYSTEM="Te JARVIS vagy, egy magyarul beszélő digitális asszisztens. Légy pontos és rövid. Nem hajthatsz végre telefonműveletet ezen a felhős beszélgetési csatornán; ne állítsd, hogy végrehajtottad.";
 public static String provider(int index){return PROVIDERS[index>=0&&index<PROVIDERS.length?index:0];}
 public static boolean validProvider(String p){return "local".equals(p)||"gemini".equals(p)||"openai".equals(p)||"anthropic".equals(p)||"groq".equals(p);}
 public static boolean isLocal(int index){return "local".equals(provider(index));}

 public static void ask(Activity activity,int index,String key,String prompt,Callback callback){
  if(index<1||index>=MODELS.length){callback.done("Válassz online AI-modellt.");return;}
  final String provider=provider(index),model=MODELS[index];
  new Thread(()->{
   String answer;
   try{answer=request(provider,model,key,prompt);}
   catch(Exception e){answer="AI-kapcsolati hiba: "+e.getMessage();}
   final String delivered=answer;
   activity.runOnUiThread(()->callback.done(delivered));
  },"jarvis-online-ai").start();
 }
 private static String request(String provider,String model,String key,String prompt)throws Exception{
  if(key==null||key.trim().isEmpty())throw new Exception("Hiányzik a "+provider+" API-kulcs.");
  JSONObject request=new JSONObject();
  String endpoint;
  if("gemini".equals(provider)){
   endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent";
   request.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(new JSONObject().put("text",SYSTEM))));
   request.put("contents",new JSONArray().put(new JSONObject().put("role","user").put("parts",new JSONArray().put(new JSONObject().put("text",prompt)))));
  }else if("anthropic".equals(provider)){
   endpoint="https://api.anthropic.com/v1/messages";
   request.put("model",model).put("max_tokens",1024).put("system",SYSTEM);
   request.put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",prompt)));
  }else{
   endpoint="groq".equals(provider)?"https://api.groq.com/openai/v1/chat/completions":"https://api.openai.com/v1/chat/completions";
   request.put("model",model).put("messages",new JSONArray()
    .put(new JSONObject().put("role","system").put("content",SYSTEM))
    .put(new JSONObject().put("role","user").put("content",prompt)));
  }
  HttpURLConnection c=(HttpURLConnection)new URL(endpoint).openConnection();
  try{
   c.setRequestMethod("POST");
   c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setDoOutput(true);
   c.setRequestProperty("Content-Type","application/json");
   if("gemini".equals(provider))c.setRequestProperty("x-goog-api-key",key);
   else if("anthropic".equals(provider)){c.setRequestProperty("x-api-key",key);c.setRequestProperty("anthropic-version","2023-06-01");}
   else c.setRequestProperty("Authorization","Bearer "+key);
   try(OutputStream os=c.getOutputStream()){os.write(request.toString().getBytes(StandardCharsets.UTF_8));}
   int code=c.getResponseCode();
   if(code>=400)throw new Exception(errorForStatus(code));
   ByteArrayOutputStream b=new ByteArrayOutputStream();
   try(InputStream in=c.getInputStream()){
    byte[] buf=new byte[4096];int n;
    while((n=in.read(buf))!=-1){if(b.size()+n>524288)throw new Exception("Túl hosszú AI-válasz.");b.write(buf,0,n);}
   }
   JSONObject response=new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8));
   String answer;
   if("gemini".equals(provider)){
    JSONArray parts=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts");
    StringBuilder text=new StringBuilder();for(int i=0;i<parts.length();i++)text.append(parts.getJSONObject(i).optString("text",""));
    answer=text.toString();
   }else if("anthropic".equals(provider)){
    JSONArray content=response.getJSONArray("content");
    StringBuilder text=new StringBuilder();for(int i=0;i<content.length();i++)text.append(content.getJSONObject(i).optString("text",""));
    answer=text.toString();
   }else answer=response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content","");
   if(answer.trim().isEmpty())throw new Exception("A modell nem adott szöveges választ.");
   return answer.trim();
  }finally{c.disconnect();}
 }
 private static String errorForStatus(int code){
  switch(code){
   case 400:return "HTTP 400 – Hibás kérés vagy nem támogatott modell.";
   case 401:return "HTTP 401 – Érvénytelen API-kulcs.";
   case 402:return "HTTP 402 – A szolgáltató fizetést kér.";
   case 403:return "HTTP 403 – Nincs jogosultság ehhez a modellhez.";
   case 404:return "HTTP 404 – A modell nem elérhető; válassz másikat.";
   case 429:return "HTTP 429 – Kvóta vagy sebességkorlát.";
   default:return "HTTP "+code+" – Szolgáltatói hiba.";
  }
 }
}
