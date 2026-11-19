package hu.jarvis.ai;
import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import org.json.*;import java.io.*;import java.net.*;
public final class GeminiAgent {
 public interface Callback {void done(String message);}
 public static void run(Activity a,String key,String instruction,Callback cb){
  run(a,key,"gemini-3.8-flash",instruction,cb);
 }
 public static void run(Activity a,String key,String model,String instruction,Callback cb){
  if(!GeminiConnection.validModel(model)){
   a.runOnUiThread(()->cb.done("Gemini-hiba: Érvénytelen vagy nem szöveges modell. Válassz a Gemini API menüben."));
   return;
  }
  new Thread(()->{
   String result;
   try{
    JSONObject body=new JSONObject();JSONArray contents=new JSONArray();
    JSONObject part=new JSONObject().put("text","Te JARVIS vagy. A magyar utasítást értelmezd. Kizárólag JSON objektumot adj vissza, három mezővel: reply (magyar válasz), action (none, open_url, home, back vagy scroll), url (HTTPS webcím vagy üres). Csak kifejezett kérésre válassz műveletet. Ne állíts végrehajtást bizonyíték nélkül. Felhasználói utasítás: "+instruction);
    contents.put(new JSONObject().put("role","user").put("parts",new JSONArray().put(part)));
    body.put("contents",contents).put("generationConfig",new JSONObject().put("responseMimeType","application/json"));
    URL url=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
    HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setRequestMethod("POST");c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setDoOutput(true);
    c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-goog-api-key",key);
    try(OutputStream os=c.getOutputStream()){os.write(body.toString().getBytes("UTF-8"));}
    int status=c.getResponseCode();InputStream stream=status<400?c.getInputStream():c.getErrorStream();
    ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=stream.read(buf))>0&&out.size()<200000)out.write(buf,0,n);
    String raw=out.toString("UTF-8");if(status>=400){
      String note;
      switch(status){
       case 400:note="Hibás modellbeállítás vagy kérés.";break;
       case 401:note="A Gemini API-kulcs nem elfogadott.";break;
       case 403:note="Nincs hozzáférés ehhez a modellhez vagy projekthez.";break;
       case 404:note="A modell nem érhető el ezzel a kulccsal.";break;
       case 429:note="A Gemini kvótája vagy sebességkorlátja betelt.";break;
       default:note="Google API szerverhiba vagy tiltott kérés.";
      }
      throw new IOException("HTTP "+status+" – "+note+" A GEMINI API / MODELL TESZT menü segít.");
     }
    JSONObject response=new JSONObject(raw);String text=response.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
    JSONObject cmd=new JSONObject(text);String action=cmd.optString("action","none");String reply=cmd.optString("reply","Nincs válasz.");String target=cmd.optString("url","");
    a.runOnUiThread(()->{String info=execute(a,action,target);cb.done(reply+(info.isEmpty()?"":"\n"+info));});return;
   }catch(Exception e){result="Gemini-hiba: "+e.getMessage();}
   final String msg=result;a.runOnUiThread(()->cb.done(msg));
  }).start();
 }
 private static String execute(Activity a,String action,String url){
  try{
   switch(action){
    case "home":case "back":case "scroll":
     // Accessibility actions require an enabled service; never claim execution without it.
     return "Művelet javasolva: "+action+". A telefonvezérlés összekapcsolása még szükséges.";
    case "open_url":
     Uri uri=Uri.parse(url);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)return "Érvénytelen vagy nem biztonságos URL.";
     a.startActivity(new Intent(Intent.ACTION_VIEW,uri));return "Weboldal megnyitva.";
    default:return "";
   }
  }catch(Exception e){return "Művelet sikertelen: "+e.getMessage();}
 }
}
