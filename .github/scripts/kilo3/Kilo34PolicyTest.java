package hu.jarvis.app;
final class Kilo34PolicyTest {
 static void check(boolean condition,String label){
   if(!condition)throw new AssertionError("FAILED: "+label);
   System.out.println("PASS: "+label);
 }
 public static void main(String[] unused){
   String goal="JARVIS, nyisd meg a Kleinanzeigen alkalmazást és keress 10 km körzetben ingyen elvihető tárgyakat";
   check(SearchActionPolicy.marketplaceResearch(goal),"marketplace search recognized");
   check(SearchConstraints.radiusKm(goal)==10,"numeric radius 10 km");
   check(SearchConstraints.radiusKm("keress tíz kilométer körzetben")==10,"word radius 10 km");
   check(!SearchActionPolicy.needsApproval(goal,"tap","Weiter","",true),"continue safe despite model confirmation");
   check(!SearchActionPolicy.needsApproval(goal,"tap","Umkreis","",true),"filter safe despite model confirmation");
   check(!SearchActionPolicy.needsApproval(goal,"type","Wonach suchst du?","E-Scooter",true),"typing into search");
   check(!SearchActionPolicy.needsApproval(goal,"submit","Wonach suchst du?","",true),"submitting search using German hint");
   check(SearchActionPolicy.needsApproval(goal,"tap","Kaufen","",false),"purchase guarded");
   check(SearchActionPolicy.needsApproval(goal,"tap","Alle akzeptieren","",true),"consent guarded");
   check(SearchActionPolicy.needsApproval(goal,"tap","","",true),"unlabeled tap guarded");
   check(SearchActionPolicy.needsApproval("Küldj üzenetet","tap","Weiter","",true),"non-search guarded");
   check(SearchActionPolicy.retryableAsk(goal,"Nyomd meg a sárga folytatás gombot"),"routine ask retryable");
   check(!SearchActionPolicy.retryableAsk(goal,"Jelentkezz be a fiókodba"),"login manual");
 }
}
