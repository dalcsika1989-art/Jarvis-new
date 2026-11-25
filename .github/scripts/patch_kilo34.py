#!/usr/bin/env python3
"""JARVIS KILO 3.4: keep browsing after benign filters, collapse HUD,
preserve requested search radius, and give truthful error details.

Runs after patch_kilo33.py. Rebuilds a NEW app rather than overwriting 3.3.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1]); java=root/"src/hu/jarvis/app"

def one(path,old,new):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f"KILO 3.4 patch failed in {path.name}: expected 1 match, got {count}: {old[:130]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

control=java/"ControlService.java"
# Approval overlay previously remained full-screen even AFTER permission had
# been granted. It obscured marketplace filter and search controls.
one(control,
    'confirm=approval;if(confirm)expanded=true;render();if(confirm)showApprovalNotification(s);else cancelApprovalNotification();notifyState(s,runner!=null&&runner.running);',
    'confirm=approval;if(confirm)expanded=true;else if(runner!=null&&runner.running)expanded=false;render();if(confirm)showApprovalNotification(s);else cancelApprovalNotification();notifyState(s,runner!=null&&runner.running);')
# Distinguish a labeled search/filter click from real high-impact actions.
one(control,
    'boolean needsApproval(JSONObject a,String label){return ApprovalPolicy.asks(getSharedPreferences("jarvis",0).getInt("approvalMode",ApprovalPolicy.AUTO),a.optString("action"),label,a.optString("text",""),a.optBoolean("confirm",false));}',
    '''boolean needsApproval(JSONObject a,String label){
      int mode=ApprovalPolicy.valid(getSharedPreferences("jarvis",0).getInt("approvalMode",ApprovalPolicy.SENSITIVE));
      if(mode==ApprovalPolicy.EACH)return true;
      if(runner!=null&&runner.running){
        String goal=getSharedPreferences("jarvis",0).getString("planGoal","");
        return SearchActionPolicy.needsApproval(goal,a.optString("action"),label,a.optString("text",""),a.optBoolean("confirm",false));
      }
      return ApprovalPolicy.asks(mode,a.optString("action"),label,a.optString("text",""),a.optBoolean("confirm",false));
    }''')
# Most Android edit fields are labeled using hint text, not the current value.
one(control,
    'return cut(n.getText(),160)+" "+cut(n.getContentDescription(),160);',
    'return cut(n.getText(),160)+" "+cut(n.getContentDescription(),160)+" "+cut(n.getHintText(),120)+" "+cut(n.getViewIdResourceName(),120);')

runner=java/"AgentRunner.java"
one(runner,
    'String previousScreen="",previousAction="";boolean prepared=false;',
    'String previousScreen="",previousAction="";boolean prepared=false;int retryableAskCount=0;')
one(runner,
    'String context=new JSONObject().put("goal",goal).put("fixedUserConstraints",goal)',
    'String context=new JSONObject().put("goal",goal).put("fixedUserConstraints",goal).put("requestedRadiusKm",SearchConstraints.radiusKm(goal))')
one(runner,
    'JSONObject action=ai.jsonImage(SYSTEM,context,image);',
    'JSONObject action=ai.jsonImage(SYSTEM+SearchConstraints.guidance(goal),context,image);')
# The default model occasionally asks a human to click a routine "Weiter"/
# distance button despite an actionable accessibility tree being available.
# Retry at most twice, then show a real help request instead of looping.
one(runner,
    'if(kind.equals("ask")){resumable=false;',
    '''if(kind.equals("ask")){
      String requestedHelp=action.optString("result","");
      if(retryableAskCount<2&&SearchActionPolicy.retryableAsk(goal,requestedHelp)){
        retryableAskCount++;
        String notice="Az AI kézi segítséget kért egy szokásos keresési lépéshez. "+retryableAskCount+"/2 újratervezés, friss képernyőből.";
        log(notice);
        trace.add(notice+" A keresési feltételeket tartsd meg: "+SearchConstraints.guidance(goal));
        trim(trace);
        Thread.sleep(650);
        continue;
      }
      resumable=false;''')
# Don't let an overcautious AI confirm flag create a false security stop.
# The same local policy must be used by the runner and the executor.
one(runner,
    'boolean scopedRisk=AgentRules.needsConfirm(kind,label,action.optString("text",""),action.optBoolean("confirm",false));',
    'boolean scopedRisk=SearchActionPolicy.needsApproval(goal,kind,label,action.optString("text",""),action.optBoolean("confirm",false));')
one(runner,
    'return service.execute(action,permit);});check(gen);String record=',
    'return service.execute(action,permit);});check(gen);retryableAskCount=0;String record=')
one(runner,
    'type felülírja a teljes mezőt. Ha a keresőmezőre kattintás után megjelent a billentyűzet, a következő lépés a type legyen a keresőkifejezéssel, majd submit a megfelelő szerkeszthető mezőn; ne görgess helyette.',
    '''type felülírja a teljes mezőt. Ha a keresőmezőre kattintás után megjelent a billentyűzet, a következő lépés a type legyen a keresőkifejezéssel, majd submit a megfelelő szerkeszthető mezőn; ne görgess helyette. A puszta keresési szűrőválasztás (távolság, termék, ár) nem érzékeny művelet. Ha egy látható Weiter/Folytatás/Keresés szűrőgomb azonosítható a képernyőfában, nyomd meg önállóan; csak valóban érzékeny műveletnél kérj jóváhagyást. A kért keresési távolság kötelező; az eltérő 4 km NEM egyenértékű például 10 km-rel.''')
one(runner,
    'Ár, címzett, tartalom, hely és hatókör mind egyezzen a kéréssel. JSON: confirmed:boolean',
    'Ár, címzett, tartalom, hely és hatókör mind egyezzen a kéréssel. Ha a felhasználó 10 km-es keresést kért, 4 km-es szűrő nem elfogadható; ne igazold a feladat végét, amíg a távolságot nem ellenőrizted. JSON: confirmed:boolean')

manifest=root/"AndroidManifest.xml"
one(manifest,'package="hu.jarvis.kilo33"','package="hu.jarvis.kilo34"')
one(manifest,'android:label="JARVIS KILO 3.3"','android:label="JARVIS KILO 3.4"')
one(manifest,'android:versionName="3.3-Kilo-Typing"','android:versionName="3.4-Kilo-Continue"')
for path in java.glob("*.java"):
    s=path.read_text(encoding="utf-8")
    if "hu.jarvis.kilo33" in s:path.write_text(s.replace("hu.jarvis.kilo33","hu.jarvis.kilo34"),encoding="utf-8")
ui=root/"assets/index.html"
one(ui,"JARVIS KILO / 3.3 • HU","JARVIS KILO / 3.4 • HU")
(root/"KILO34-README.md").write_text("""JARVIS KILO 3.4 – PIAC-TÉRI FELADAT FOLYTATÁS
A JARVIS KILO 3.3 változat különálló utódja, nem írja felül a korábbi APK-kat.
- A MEHET jóváhagyás végeztével a körmenü automatikusan visszacsukódik a 72dp átmérőjű reaktorikonná, hogy ne fedje el a Kleinanzeigen képernyőjét.
- A program a felhasználó kifejezett távolságát megőrzi: 10 km helyett 4 km-rel nem jelenthet feladatbefejezést.
- Egyszerű, azonosítható keresési navigációt a Kilo konzervatív helyi szabályzata jóváhagyás nélkül végezhet, még ha a modell tévesen confirm:true-t ad is.
- Küldés, vásárlás, törlés, telepítés és engedélyadás megerősítést igényel; azonosítatlan célpontnál sem találgat.
- Az AI legfeljebb kétszer újratervezhet rutinszűrési 'kattints helyettem' kérést; ezután egyértelműen jelzi, milyen kézi segítségre lenne szüksége.
- A valós Kleinanzeigen-működés tesztelése még szükséges a telefonon, a cloud compile önmagában nem bizonyítja.
""",encoding="utf-8")
print("KILO 3.4 patch OK: safe approvals, overlay collapse, 10 km constraint, bounded replanning.")
