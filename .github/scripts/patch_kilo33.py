#!/usr/bin/env python3
"""JARVIS KILO 3.3: third-party Android text entry.
Executes after KILO 3.2 patch; preserves prior apps and UI.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
src=root/"src/hu/jarvis/app"
def one(path,old,new):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1:raise RuntimeError(f"KILO3.3 {path.name}: expected 1 match got {count}: {old[:90]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")
service=src/"ControlService.java"
one(service,
'else if(action.equals("type")){AccessibilityNodeInfo n=target(a);if(!n.isEditable())throw new Exception("Nem szerkeszthető mező");int type=n.getInputType()&0xfff;if(type==129||type==145||type==225||type==18)throw new Exception("Védett beviteli mező");String text=a.getString("text");if(text.length()>3000)throw new Exception("Túl hosszú szöveg");Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);ok=n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}',
'else if(action.equals("type")){AccessibilityNodeInfo selected=target(a);AccessibilityNodeInfo editable=TextInputAssist.resolve(appRoot(),selected);return TextInputAssist.type(this,editable,a.getString("text"));}')

agent=src/"AgentRunner.java"
one(agent,
'type felülírja a teljes mezőt. A submit az adott szerkeszthető mező billentyűzet-műveletét indítja.',
'type felülírja a teljes mezőt. Ha a keresőmezőre kattintás után megjelent a billentyűzet, a következő lépés a type legyen a keresőkifejezéssel, majd submit a megfelelő szerkeszthető mezőn; ne görgess helyette. A type egy megjelölt szövegmezőre vagy annak kattintható burkolóelemére irányítható. A submit az adott szerkeszthető mező billentyűzet-műveletét indítja.')

manifest=root/"AndroidManifest.xml"
one(manifest,'package="hu.jarvis.kilo32"','package="hu.jarvis.kilo33"')
one(manifest,'android:label="JARVIS KILO 3.2"','android:label="JARVIS KILO 3.3"')
one(manifest,'android:versionName="3.2-Kilo-Unified"','android:versionName="3.3-Kilo-Typing"')
for p in src.glob("*.java"):
    s=p.read_text(encoding="utf-8")
    if "hu.jarvis.kilo32" in s:
        p.write_text(s.replace("hu.jarvis.kilo32","hu.jarvis.kilo33"),encoding="utf-8")
ui=root/"assets/index.html"
one(ui,"JARVIS KILO / 3.2 • HU","JARVIS KILO / 3.3 • HU")
(root/"KILO33-README.md").write_text("""JARVIS KILO 3.3 – SZÖVEGBEVITEL JAVÍTÁS

Külön alkalmazás: hu.jarvis.kilo33. A korábbi működő APK-kat nem írja felül.
A harmadik féltől származó alkalmazások szerkeszthető mezőit az Android Accessibility fájából választja:
- Először ACTION_SET_TEXT; az Android visszajelzését és a mező tartalmát ellenőrzi.
- Ha a cél egy keresőmezőt körülvevő kattintható elem, csak az adott fa szerkeszthető gyermekét / az egyértelműen fókuszált mezőt választja.
- Elutasítás esetén ACTION_PASTE a felhasználó által megadott szöveggel. Ez a vágólap tartalmát ideiglenesen lecseréli. A korábbi vágólapot adatvédelmi okokból nem olvassa ki.
- Jelszómezőbe nem gépel; több szerkeszthető mező esetén nem találgat.
- A bevitelt és a sikertelenség okát megkülönböztető állapotot küld vissza.
Az APK lefordulása nem bizonyítja a külső Kleinanzeigen mező működését: azt a telefonon kell kipróbálni.
""",encoding="utf-8")
print("KILO 3.3 text input patch applied.")
