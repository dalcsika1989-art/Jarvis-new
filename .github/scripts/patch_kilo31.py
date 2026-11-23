#!/usr/bin/env python3
"""JARVIS KILO 3.1: fix 'tudsz az interneten keresni?' misrouted to an ungrounded LLM response."""
import sys
from pathlib import Path
root=Path(sys.argv[1]); java=root/"src/hu/jarvis/app"; manifest=root/"AndroidManifest.xml"
def one(path,old,new):
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1:raise RuntimeError(f"Expected exactly one occurrence in {path.name}, found {n}: {old[:110]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")
main=java/"MainActivity.java"
one(main,'if(CommandRouting.needsAgent(raw,prefs.getBoolean("agentMode",true))){startAgent(raw);return;}',
'''if(OnlineSearch.isCapabilityQuestion(raw)){checkOnlineNow();return;}
 if(CommandRouting.needsAgent(raw,prefs.getBoolean("agentMode",true))){startAgent(raw);return;}''')

one(main,'void askAi(String text){',
'''/** Network capability is tested against a real external search feed; never ask an LLM to guess. */
 void checkOnlineNow(){
   if(busy){answer("Egy korábbi keresés még fut. A DIAGNOSZTIKA alatt külön ellenőrizheted a hálózatot.");return;}
   busy=true;
   event("state","INTERNET TESZT • valós lekérés");
   worker.execute(()->{
     try{
       JSONObject found=OnlineSearch.search("Frankfurt am Main",2);
       int count=found.getJSONArray("results").length();
       if(count>0){
         answer("Igen, Uram, most sikeresen elértem az internetes keresőt: "+found.optString("provider")+"; "+count+" ellenőrzött találat érkezett. Mondd meg, mire keressek rá, például: keress az interneten friss frankfurti híreket.");
       }else answer("A hálózat elérhető lehet, de a kereső nem adott találatot. "+OnlineSearch.status(this));
     }catch(Exception e){
       answer("Az internetes keresést most nem sikerült végrehajtanom. "+OnlineSearch.status(this)+". Keresőhiba: "+(e.getMessage()==null?"ismeretlen":e.getMessage())+". Próbáld a DIAGNOSZTIKA internet tesztjét.");
     }finally{busy=false;}
   });
 }
 void askAi(String text){''')
one(main,'if(webLookup){event("state","INTERNET • Élő keresés");',
'''system+="\\nA JARVIS KILO 3.1 alkalmazásban van külön online keresőmodul, amely Bing RSS és Google News élő adatokat tud lekérni, ha a hálózat eléri őket. Ne állítsd kategorikusan, hogy a JARVIS csak offline működik: ez csak a helyi hangmotorra igaz. Valós találat nélküli friss állítást soha ne találj ki. Az alkalmazás nem rendelkezik általános böngészővezérléssel a szöveges chatmodulon belül.";
       if(webLookup){event("state","INTERNET • Élő keresés");''')

one(manifest,'package="hu.jarvis.kilo3"','package="hu.jarvis.kilo31"')
one(manifest,'android:label="JARVIS KILO 3"','android:label="JARVIS KILO 3.1"')
one(manifest,'android:versionName="3.0-Kilo-Online"','android:versionName="3.1-Kilo-Online"')
for p in java.glob("*.java"):
    s=p.read_text(encoding="utf-8")
    if "hu.jarvis.kilo3" in s:
        p.write_text(s.replace("hu.jarvis.kilo3","hu.jarvis.kilo31"),encoding="utf-8")
ui=root/"assets/index.html"
original=ui.read_text(encoding="utf-8")
for old,new in [
 ("JARVIS / 2.0 • HU","JARVIS KILO / 3.1 • HU"),
 ("HELYI PARANCSOK • AI BEÁLLÍTHATÓ","HELYI HANG • ONLINE KERESŐMODUL"),
 ("AI-KULCS BEÁLLÍTVA • HELYI PARANCSOK AKTÍVAK","KILO AI-KULCS MENTVE • ONLINE KERESŐMODUL"),
 ("HELYI PARANCSOK • AI-KULCS NINCS","KILO KULCS HIÁNYZIK • HELYI HANG")
]:
    if old not in original:raise RuntimeError("UI element not found: "+old)
    original=original.replace(old,new)
ui.write_text(original,encoding="utf-8")
print("JARVIS KILO 3.1: network capability probe, online intent and HUD status patched.")
