package hu.jarvis.ai;
import android.app.Activity;
import hu.jarvis.app.ControlService;
import org.json.*;
import android.os.Handler;
import android.os.Looper;
/** User-triggered, bounded accessibility agent. No background screen uploads. */
public final class ScreenAgent {
 public interface Listener { void event(String type,String message); }
 private ScreenAgent(){}
 private static volatile int session=0;
 public static void cancel(){session++;}
 public static void run(Activity activity,String apiKey,String goal,Listener listener){
  if(!ControlService.active()){listener.event("error","Engedélyezd a JARVIS kisegítő szolgáltatást.");return;}
  if(goal==null||goal.trim().isEmpty()){listener.event("error","Hiányzik a cél.");return;}
  int token=++session;
  listener.event("task","Ügynök: képernyőelemzés indul. Maximum 12 lépés.");
  next(activity,apiKey,goal,0,token,listener);
 }
 private static void next(Activity activity,String key,String goal,int index,int token,Listener listener){
  if(token!=session)return;
  if(index>=12){listener.event("task","Ügynök leállt: elérte a 12 lépéses korlátot.");return;}
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
   if(token!=session)return;
   try{
    if(response.startsWith("Groq-hiba:")){listener.event("error",response);return;}
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
    listener.event("task","Ügynök "+(index+1)+"/12: "+cmd);
    waitForStep(activity,key,goal,index,token,listener,0);

   }catch(Exception e){listener.event("error","Ügynök tervezési hiba: "+(response.length()>180?response.substring(0,180):response));}
  });
 }
 private static void waitForStep(Activity activity,String key,String goal,int index,int token,Listener listener,int checks){
  new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
   if(token!=session)return;
   String status=ControlService.currentStatus();
   if(status.startsWith("Sikertelen:")||status.startsWith("Feladat megszakítva")){
    listener.event("error",status);return;
   }
   if(status.startsWith("Minden sorba állított")||status.startsWith("Minden parancs")){
    next(activity,key,goal,index+1,token,listener);return;
   }
   if(checks>=20){listener.event("error","Ügynök: a művelet nem fejeződött be 12 másodperc alatt. "+status);return;}
   waitForStep(activity,key,goal,index,token,listener,checks+1);
  },600);
 }

}    waitForStep(activity,key,goal,index,listener,0);

   }catch(Exception e){listener.event("error","Ügynök tervezési hiba: "+(response.length()>180?response.substring(0,180):response));}
  });
 }
 private static void waitForStep(Activity activity,String key,String goal,int index,int token,Listener listener,int checks){
  new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{
   if(token!=session)return;
   String status=ControlService.currentStatus();
   if(status.startsWith("Sikertelen:")||status.startsWith("Feladat megszakítva")){
    listener.event("error",status);return;
   }
   if(status.startsWith("Minden sorba állított")||status.startsWith("Minden parancs")){
    next(activity,key,goal,index+1,token,listener);return;
   }
   if(checks>=20){listener.event("error","Ügynök: a művelet nem fejeződött be 12 másodperc alatt. "+status);return;}
   waitForStep(activity,key,goal,index,token,listener,checks+1);
  },600);
 }

 private static void waitForStep(Activity activity,String key,String goal,int index,Listener listener,int attempt){
  String status=ControlService.currentStatus();
  if(status.startsWith("Sikertelen:")||status.startsWith("Feladat megszakítva")){
   listener.event("error",status);return;
  }
  if(status.startsWith("Minden sorba állított művelet befejeződött.")){
   new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
    ()->next(activity,key,goal,index+1,listener),550);
   return;
  }
  if(attempt>=35){listener.event("error","Ügynök időtúllépés: "+status);return;}
  new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
   ()->waitForStep(activity,key,goal,index,listener,attempt+1),400);
 }

}
