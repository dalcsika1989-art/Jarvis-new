#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])
java=root/"src/hu/jarvis/app"
def edit(name,patches):
    path=root/name if name=="AndroidManifest.xml" else java/name
    s=path.read_text(encoding="utf-8")
    for old,new in patches:
        if s.count(old)!=1:
            raise RuntimeError(f"Expected one match in {name}: {old[:100]!r}; got {s.count(old)}")
        s=s.replace(old,new,1)
    path.write_text(s,encoding="utf-8")
edit("AndroidManifest.xml",[
('package="hu.jarvis.kilotest" android:versionCode="11" android:versionName="2.0"','package="hu.jarvis.kilocontrol" android:versionCode="1" android:versionName="2.0-Kilo-Control"'),
('android:label="JARVIS KILO TEST"','android:label="JARVIS KILO CONTROL"')])
edit("AiClient.java",[
('boolean kilo(){return prefs.getBoolean("kiloMode",false);}','boolean kilo(){return true;} // Kilo-only separate test app'),
('/** OpenAI-compatible Kilo Gateway connection; no screen/vision content is sent in test mode. */','/** Kilo API: screen text requires separate explicit user consent. */')])
edit("AgentRunner.java",[
('if(p.getBoolean("kiloMode",false)){JarvisApp.speak("Kilo Free tesztmódban a telefonos AI-vezérlés kikapcsolva. Képernyőadatokat nem küldök a Kilónak.");return;}if(p.getString("key","").isEmpty()){JarvisApp.speak("Az intelligens vezérléshez add meg a Gemini API-kulcsodat a fogaskeréknél.");return;}','if(p.getString("kiloKey","").trim().isEmpty()){JarvisApp.speak("A telefonvezérléshez add meg a Kilo API-kulcsot.");return;}'),
('Ez a feladathoz szükséges képernyőszöveget a Gemininek küldi.','Ez a feladathoz szükséges képernyőszöveget a Kilo AI szolgáltatásnak küldi.'),
('"hu.jarvis.kilotest".equals(String.valueOf(root.getPackageName()))','"hu.jarvis.kilocontrol".equals(String.valueOf(root.getPackageName()))'),
('if(ApprovalPolicy.valid(service.getSharedPreferences("jarvis",0).getInt("approvalMode",ApprovalPolicy.AUTO))==ApprovalPolicy.AUTO){approved=true;break;}','if(ApprovalPolicy.valid(service.getSharedPreferences("jarvis",0).getInt("approvalMode",ApprovalPolicy.SENSITIVE))==ApprovalPolicy.AUTO&&!scopedRisk){approved=true;break;}')
])
edit("ApprovalPolicy.java",[
('mode=valid(mode);return mode==EACH||mode==SENSITIVE&&AgentRules.needsConfirm(action,label,text,modelRequest);','mode=valid(mode);return AgentRules.needsConfirm(action,label,text,modelRequest)||mode==EACH;')])
edit("ControlService.java",[
('"hu.jarvis.kilotest".equals(pkg.toString())','"hu.jarvis.kilocontrol".equals(pkg.toString())')])
edit("AgentRules.java",[
('n.equals("hu.jarvis.kilotest");','(n.equals("hu.jarvis.kilotest")||n.equals("hu.jarvis.kilocontrol"));')])
edit("MainActivity.java",[
('void startAgent(String goal){if(prefs.getBoolean("kiloMode",false)){answer("Kilo tesztmód: jelenleg csak beszélgetés engedélyezett. A képernyőadatokat nem továbbítom a Kilo Free modelljének.");return;}ControlService','void startAgent(String goal){ControlService'),
('if(prefs.getString("key","").trim().isEmpty()){answer("Az összetett feladathoz nincs mentett Gemini API-kulcs. Az egyszerű appnyitás kulcs nélkül is működik. A DIAGNOSZTIKA ablakban külön tesztelheted a kapcsolatot.");return;}','if(prefs.getString("kiloKey","").trim().isEmpty()){answer("A telefonvezérléshez először mentsd a Kilo API-kulcsodat.");return;}'),
('A feladathoz szükséges látható képernyőszöveget és az alkalmazások listáját a Google Gemini dolgozza fel.','A feladathoz szükséges látható képernyőszöveget és az alkalmazások listáját a Kilo AI és modellszolgáltatója dolgozza fel.'),
('Telefonvezérléskor a feladat képernyőszövege a Google-höz kerül.','Telefonvezérléskor az engedélyezett képernyőszöveg a Kilo AI szolgáltatóhoz kerülhet.'),
('kiloMode.setText("Kilo AI FREE teszt (beszélgetés, JARVIS-HU hang)");kiloMode.setChecked(prefs.getBoolean("kiloMode",false));box.addView(kiloMode);','kiloMode.setText("Kilo AI: beszélgetés + opcionális telefonvezérlés");kiloMode.setChecked(true);kiloMode.setEnabled(false);box.addView(kiloMode);'),
('A korábbi Gemini-beszélgetést, memóriát és képernyőadatot nem továbbítjuk. A telefonos AI-autopilot Kilo tesztmódban letiltva.','Különálló alkalmazás, saját memóriával és API-kulccsal. A telefonvezérléshez külön engedély kell; a Kilo-kéréseket más modellszolgáltató is feldolgozhatja. Képernyőképet nem továbbítunk.'),
('consent.setText("AI telefonvezérlés: képernyőszöveg elküldése a Gemini-nek");','consent.setText("AI telefonvezérlés: képernyőszöveg küldése a Kilo AI-nak (külön engedéllyel)");'),
('vision.setText("Képernyőképes értelmezés: az aktuális app képe is a Geminihez kerül (Android 11+). Csak feladat közben; kikapcsolható.");vision.setChecked(prefs.getBoolean("visionConsent",false));box.addView(vision);','vision.setText("Képernyőképek továbbítása letiltva (csak képernyőszöveg)");vision.setChecked(false);vision.setEnabled(false);box.addView(vision);'),
('kiloMode.isChecked()).putString("kiloKey"','true).putString("kiloKey"'),
('putBoolean("agentConsent",!kiloMode.isChecked()&&consent.isChecked()).putBoolean("visionConsent",!kiloMode.isChecked()&&vision.isChecked())','putBoolean("agentConsent",consent.isChecked()).putBoolean("visionConsent",false)'),
('new String[]{"Automatikus • a kiadott feladaton belül nem kérdez","Csak érzékeny műveleteknél","Minden végrehajtási lépésnél"}','new String[]{"Automatikus • érzékeny műveleteknél jóváhagyás","Csak érzékeny műveleteknél","Minden végrehajtási lépésnél"}'),
('approvals.setSelection(ApprovalPolicy.valid(prefs.getInt("approvalMode",ApprovalPolicy.AUTO)));','approvals.setSelection(ApprovalPolicy.valid(prefs.getInt("approvalMode",ApprovalPolicy.SENSITIVE)));'),
('Az automatikus mód küldést vagy törlést is végrehajthat a kiadott feladaton belül.','Küldéshez, vásárláshoz, törléshez és engedélyadáshoz mindig külön jóváhagyás kell.'),
('cloud.setChecked(prefs.getBoolean("cloudVoice",false));box.addView(cloud);','cloud.setChecked(false);cloud.setEnabled(false);box.addView(cloud);'),
('putBoolean("cloudVoice",cloud.isChecked())','putBoolean("cloudVoice",false)')
])
p=java/"MainActivity.java"
s=p.read_text(encoding="utf-8")
for field in ["key","model"]:
    old=f'{field}.setText(prefs.getString("{field}",""));box.addView({field});'
    new=old.replace(f'box.addView({field});',f'{field}.setVisibility(android.view.View.GONE);box.addView({field});')
    if s.count(old)!=1:raise RuntimeError("legacy field not found "+field)
    s=s.replace(old,new,1)
p.write_text(s,encoding="utf-8")
(root/"KILO_NEMESIS_INTEGRACIO.md").write_text("""# Nemesis / Kilo AI integráció
A JARVIS Kilo Control teljesen külön alkalmazás (hu.jarvis.kilocontrol), nem módosítja a JARVIS KILO TEST alkalmazást.
A Nemesis a Kilo Gateway-t használhatja: POST https://ai-gateway.kilo.ai/api/v1/chat/completions
Authorization: Bearer <a Nemesisben külön megadott saját kulcs>, model: kilo-auto/free.
A Nemesis nem örökli automatikusan a JARVIS API-kulcsát vagy Android kisegítő engedélyét.
A helyi JARVIS-HU hang megmarad; a külön Gemini Charon TTS letiltva.
""",encoding="utf-8")
print("Kilo controller patch complete")

edit("MainActivity.java",[
("a feladathoz szükséges képernyőszöveget a Gemini kapja meg.","a feladathoz szükséges képernyőszöveget a Kilo AI szolgáltató kapja meg, külön jóváhagyás után."),
("Gemini: saját API-kulcs szükséges; díjak és limitek a fiókod szerint.","Kilo AI: saját Kilo API-kulcs szükséges; az elérhető modellek korlátai a Kilo fiók szerint változhatnak."),
("A választható felhőhang Charon férfihangot kér a Gemini TTS-től: a felolvasandó szöveget a Google dolgozza fel, a díjak a fiókod szerint. Hiba esetén helyi hangra vált.","Ebben a változatban a magyar hangot helyben a JARVIS-HU motor készíti, külön Gemini hang-API nélkül."),
('cloud.setText("Magyar férfi AI-hang: Gemini Charon (internet és TTS-kvóta kell)");','cloud.setText("Külső Gemini hang kikapcsolva");cloud.setVisibility(android.view.View.GONE);'),
('kiloKey.setHint("Kilo API-kulcs (külön a Gemini-kulcstól)");','kiloKey.setHint("Kilo API-kulcs");')
])
