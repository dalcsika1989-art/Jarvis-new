#!/usr/bin/env python3
"""JARVIS KILO 3.6: repair harmless Kilo JSON field types; retry malformed ACTION JSON once.
The actual action still passes AiProtocol.validate before any Android operation.
Patch runs AFTER patch_kilo35.py and builds a distinct APK.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
java=root/"src/hu/jarvis/app"
def replace_one(path,old,new):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f"KILO3.6 {path.name} expected 1 match, got {count}: {old[:120]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

protocol=java/"AiProtocol.java"
repair=r'''
  /** Optional Kilo JSON fields are sometimes null, objects or numbers.
   *  Strip unused fields; NEVER weaken launch or action/id/version validation.
   */
  static void normalizeAction(JSONObject o)throws Exception{
    if(!o.has("action")||!(o.opt("action") instanceof String))return;
    String action=o.getString("action");
    if(!AgentRules.knownAction(action))return;

    Object app=o.opt("app");
    if("launch".equals(action)){
      if(app==JSONObject.NULL)o.remove("app");
      else if(app instanceof JSONObject){
        JSONObject obj=(JSONObject)app;
        Object name=obj.opt("package");
        if(!(name instanceof String))name=obj.opt("packageName");
        if(!(name instanceof String))name=obj.opt("name");
        if(name instanceof String&&!((String)name).trim().isEmpty()){
          // Android executor independently resolves/validates installed package.
          o.put("app",((String)name).trim());
        }
      }
      // Other invalid launch-app types are rejected by validate() and cause
      // a single reformat request, without executing any command.
    }else if(app!=null&&!(app instanceof String))o.remove("app");

    for(String key:new String[]{"summary","text","direction","evidence","result","memory"}){
      Object v=o.opt(key);
      if(v==JSONObject.NULL){
        boolean required= ("type".equals(action)&&"text".equals(key))||
                          ("done".equals(action)&&"evidence".equals(key))||
                          ("ask".equals(action)&&"result".equals(key));
        if(!required)o.remove(key);
      }
      // Non-null wrong-typed fields are never coerced into user-supplied content.
    }
    if(o.opt("confirm")==JSONObject.NULL)o.remove("confirm");
    // A numeric ID encoded as an ASCII decimal string may be safely parsed
    // because the executor still checks the live screen version and target.
    for(String key:new String[]{"id","version","planStep"}){
      Object value=o.opt(key);
      if(value instanceof String&&((String)value).matches("[0-9]{1,9}"))
        o.put(key,Integer.parseInt((String)value));
    }
  }
'''
replace_one(protocol,
  ' static void validate(JSONObject o,int kind)throws Exception{',
  repair+' static void validate(JSONObject o,int kind)throws Exception{')

client=java/"AiClient.java"
replace_one(client,
  ' JSONObject json(String system,String text)throws Exception{int contract=AiProtocol.contract(system);JSONObject result=AiProtocol.object(generateTyped(system,user(text),contract));AiProtocol.validate(result,contract);return result;}',
  ''' JSONObject json(String system,String text)throws Exception{
    int contract=AiProtocol.contract(system);
    AiProtocol.FormatException lastFormat=null;
    int attempts=contract==AiProtocol.ACTION?2:1;
    for(int attempt=0;attempt<attempts;attempt++){
      String prompt=attempt==0?system:system+
        "\\nFONTOS JAVÍTÁS: Az előző válasz JSON mezőtípusa hibás volt ("+
        (lastFormat==null?"ismeretlen":lastFormat.getMessage())+
        "). Küldj egyetlen új érvényes JSON objektumot a megadott sémában. "+
        "Az app mező a launch műveletnél kötelező egyszerű STRING csomagnév, "+
        "nem objektum, tömb, szám vagy null. Egyéb műveleteknél hagyd el az app mezőt. "+
        "Az id és version egész szám. confirm boolean, nem szöveg. "+
        "Érzékeny műveletnél confirm:true. Ne állíts végrehajtott műveletet.";
      try{
        JSONObject result=AiProtocol.object(generateTyped(prompt,user(text),contract));
        if(contract==AiProtocol.ACTION)AiProtocol.normalizeAction(result);
        AiProtocol.validate(result,contract);
        return result;
      }catch(AiProtocol.FormatException format){
        lastFormat=format;
        if(attempt+1==attempts)throw format;
      }
    }
    throw lastFormat;
  }''')
replace_one(client,
  'int contract=AiProtocol.contract(system);JSONObject result=AiProtocol.object(generateTyped(system,contents,contract));AiProtocol.validate(result,contract);return result;',
  '''int contract=AiProtocol.contract(system);JSONObject result=AiProtocol.object(generateTyped(system,contents,contract));
    if(contract==AiProtocol.ACTION)AiProtocol.normalizeAction(result);
    AiProtocol.validate(result,contract);return result;''')

runner=java/"AgentRunner.java"
replace_one(runner,
  'JSONObject action=ai.jsonImage(SYSTEM+SearchConstraints.guidance(goal)',
  'JSONObject action=ai.jsonImage(SYSTEM+"\\nJSON FONTOS: app CSAK launch műveletnél egyszerű szöveges csomagnév, más lépésnél ne adj app mezőt. Soha ne legyen app=null vagy objektum. Az id és version egész szám, confirm valódi boolean." +SearchConstraints.guidance(goal)')

manifest=root/"AndroidManifest.xml"
replace_one(manifest,'package="hu.jarvis.kilo35"','package="hu.jarvis.kilo36"')
replace_one(manifest,'android:label="JARVIS KILO 3.5"','android:label="JARVIS KILO 3.6"')
replace_one(manifest,'android:versionName="3.5-Kilo-Auto"','android:versionName="3.6-Kilo-JsonFix"')
for p in java.glob("*.java"):
    s=p.read_text(encoding="utf-8")
    if "hu.jarvis.kilo35" in s:p.write_text(s.replace("hu.jarvis.kilo35","hu.jarvis.kilo36"),encoding="utf-8")
ui=root/"assets/index.html"
replace_one(ui,"JARVIS KILO / 3.5 • HU","JARVIS KILO / 3.6 • HU")
(root/"KILO36-README.md").write_text("""JARVIS KILO 3.6 – HIBÁS APP TÍPUS JAVÍTÁSA
A 3.5 verziót változatlanul hagyja (új package: hu.jarvis.kilo36).
Az olyan AI válaszoknál, ahol az action=tap/scroll/type és app=null vagy
app=object, az app kulcs elhagyható, mert az Android művelethez nem szükséges.
Launch esetén a package / packageName / name tulajdonságú objektum egyszerű
szöveges névre normalizálható; ismeretlen hibás launch app nem fut.
Ha a modell így sem érvényes formátumú műveletet ad, pontos javítási
utasítással egyszer újra kérjük ugyanazt az action JSON-t.
Egyik formátumjavítás sem jelent végrehajtást, és minden action
átmegy az AiProtocol.validate biztonsági ellenőrzésén.
A korábbi AUTOMATIKUS MŰVELETEK kapcsoló megmarad.
Az APK build NEM bizonyít valós Kleinanzeigen vezérlést; készülékteszt kell.
""",encoding="utf-8")
print("JARVIS KILO 3.6 JSON app-field normalization and bounded retry patched.")
