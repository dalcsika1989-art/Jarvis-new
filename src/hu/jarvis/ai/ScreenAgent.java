package hu.jarvis.ai;
import android.app.Activity;
import hu.jarvis.app.ControlService;
import org.json.*;
/** User-triggered, bounded accessibility agent. No background screen uploads. */
public final class ScreenAgent {
 public interface Listener { void event(String type,String message); }
 private ScreenAgent(){}
 public static void run(Activity activity,String apiKey,String goal,Listener listener){
  if(!ControlService.active()){listener.event("error","Engedélyezd a JARVIS kisegítő szolgáltatást.");return;}
  if(goal==null||goal.trim().isEmpty()){listener.event("error","Hiányzik a cél.");return;}
  listener.event("task","Ügynök: képernyőelemzés indul. Maximum 8 lépés.");
  next(activity,apiKey,goal,0,listener);
 }
 private static void next(Activity activity,String key,String goal,int index,Listener listener){
  if(index>=8){listener.event("task","Ügynök leállt: elérte a 8 lépéses korlátot.");return;}
  String screen=ControlService.screenSummary();
  if(screen.startsWith("Nincs")||screen.startsWith("Képernyő")){listener.event("error",screen);return;}
  String prompt="TELEFONVEZÉRLŐ ÜGYNÖK. Cél: "+goal+
   "\nAktuális képernyő (csak látható akadálymentesítési szöveg, nem képernyőkép):\n"+screen+
   "\nVálaszolj KIZÁRÓLAG egy JSON objektummal, markdown nélkül. Engedélyezett műveletek: "+
   "{\"action\":\"open\",\"target\":\"alkalmazásnév\"}, "+
   "{\"action\":\"click\",\"target\":\"pontos látható felirat\"}, "+
   "{\"action\":\"scroll_down\"}, {\"action\":\"scroll_up\"}, "+
   "{\"action\":\"back\"}, {\"action\":\"home\"}, "+
   "{\"action\":\"done\",\"message\":\"eredmény\"}, "+
   "{\"action\":\"stop\",\"message\":\"miért\"}. "+
   "Ne találj ki gombokat. Ha üzenetküldés, vásárlás, fizetés vagy adatmegosztás következne, STOP: kérj felhasználói jóváhagyást. "+
   "Ha nem tudod biztonságosan folytatni, stop.";
  GroqAgent.run(activity,key,prompt,response->{
   try{
    String cleaned=response.trim();int a=cleaned.indexOf('{'),b=cleaned.lastIndexOf('}');
    if(a<0||b<a)throw new IllegalArgumentException(response);
    JSONObject obj=new JSONObject(cleaned.substring(a,b+1));
    String action=obj.optString("action","stop"),target=obj.optString("target","");
    if("done".equals(action)||"stop".equals(action)){listener.event("task","Ügynök: "+obj.optString("message",action));return;}
    String cmd;
    switch(action){
     case "open":cmd="nyisd meg "+target;break;
     case "click":cmd="kattints "+target;break;
     case "scroll_down":cmd="görgess le";break;
     case "scroll_up":cmd="görgess fel";break;
     case "back":cmd="vissza";break;
     case "home":cmd="home";break;
     default:throw new IllegalArgumentException("Ismeretlen művelet: "+action);
    }
    if(!ControlService.executeLocalCommand(cmd)){listener.event("error","Művelet nem fogadható: "+cmd);return;}
    listener.event("task","Ügynök "+(index+1)+"/8: "+cmd);
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
     String status=ControlService.currentStatus();
     if(status.startsWith("Sikertelen:")||status.startsWith("Feladat megszakítva")){listener.event("error",status);return;}
     if(status.startsWith("Minden sorba állított")||status.startsWith("Minden parancs"))next(activity,key,goal,index+1,listener);
     else listener.event("task","Ügynök várakozik: "+status+" (új parancs szükséges a folytatáshoz).");
    },1900);
   }catch(Exception e){listener.event("error","Ügynök tervezési hiba: "+(response.length()>180?response.substring(0,180):response));}
  });
 }
}
