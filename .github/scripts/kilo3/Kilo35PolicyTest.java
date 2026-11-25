package hu.jarvis.app;
final class Kilo35PolicyTest {
 static void check(boolean truth,String label){
   if(!truth)throw new AssertionError(label);
   System.out.println("PASS: "+label);
 }
 static boolean need(String goal,String action,String label,String text,boolean confirm,String visible,String draft){
   return AutoOperationsPolicy.needsApproval(goal,action,label,text,confirm,"com.ebay.kleinanzeigen",visible,draft);
 }
 public static void main(String[] args){
   String research="JARVIS, keress a Kleinanzeigen oldalon 10 km körzetben e-scootert";
   check(!need(research,"launch","Kleinanzeigen","",true,"",""),"auto launch");
   check(!need(research,"tap","Weiter","",true,"",""),"skip model-confirm on continue");
   check(!need(research,"tap","Umkreis","",true,"",""),"skip model-confirm on distance filter");
   check(!need(research,"type","Wonach suchst du?","E-Scooter",true,"",""),"auto type");
   check(!need(research,"scroll","","",true,"",""),"auto scroll");
   check(!need(research,"submit","Wonach suchst du?","",true,"",""),"auto search keyboard submit");
   check(need(research,"tap","","",true,"",""),"unknown unlabeled tap gated");
   check(need(research,"tap","Kaufen","",false,"",""),"purchase gated");
   check(need(research,"tap","Bezahlen","",false,"",""),"payment gated");
   check(need(research,"tap","Alle akzeptieren","",true,"",""),"consent gated");
   check(need(research,"tap","Löschen","",false,"",""),"delete gated");
   check(need(research,"tap","Senden","",false,"Peter Hallo Morgen","Hallo Morgen"),"unrequested message gated");
   String explicit="JARVIS, küldj üzenetet Péternek: Szia, holnap találkozunk!";
   check(AutoOperationsPolicy.userRequestedSend(explicit),"explicit recipient and body parsed");
   check(!need(explicit,"tap","Senden","",true,
     "Címzett Péter | Üzenet Szia, holnap találkozunk!","Szia, holnap találkozunk!"),"exact explicit message auto-send");
   check(need(explicit,"tap","Senden","",true,
     "Címzett Éva | Üzenet Szia, holnap találkozunk!","Szia, holnap találkozunk!"),"wrong recipient gated");
   check(need(explicit,"tap","Senden","",true,
     "Címzett Péter | Üzenet Szia, holnap találkozunk!","Szia holnap beszélünk"),"different draft gated");
   check(AutoOperationsPolicy.retryRoutine("Nyomd meg a folytatás gombot"),"routine retry");
   check(!AutoOperationsPolicy.retryRoutine("Add meg a jelszavad"),"password blocked");
   check(AutoOperationsPolicy.needsApproval(research,"tap","Weiter","",false,"com.paypal.android","", "") ,"financial app blocked");
 }
}
