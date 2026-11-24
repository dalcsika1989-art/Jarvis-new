#!/usr/bin/env python3
"""JARVIS KILO 3.2: unify floating command panel with web search and phone agent.
Run AFTER patch_kilo31.py to preserve the verified Kilo 3.1 web code.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
java=root/"src/hu/jarvis/app"

def one(path,old,new):
    s=path.read_text(encoding="utf-8")
    n=s.count(old)
    if n!=1:
        raise RuntimeError("KILO32 patch %s: expected exactly one match, found %d for %r"%(path.name,n,old[:120]))
    path.write_text(s.replace(old,new,1),encoding="utf-8")

# Previously the floating bubble always launched the legacy "JARVIS 2.0"
# CommandActivity and submitted EVERY text directly to the phone agent.
# Web questions and ordinary chat must instead be handled by the Kilo app itself.
activity=java/"CommandActivity.java"
one(activity,
    'void startTask(){String text=input.getText().toString().trim();if(text.isEmpty()){status.setText("Mondj vagy írj egy feladatot.");return;}ControlService c=ControlService.live;',
    '''void startTask(){String text=input.getText().toString().trim();if(text.isEmpty()){status.setText("Mondj vagy írj egy feladatot.");return;}
      String normalized=AgentRules.norm(text);
      boolean forcePhone=normalized.startsWith("feladat:")||normalized.startsWith("intezd el:")||normalized.startsWith("vegezd el:");
      boolean explicitWeb=OnlineSearch.isCapabilityQuestion(text)||OnlineSearch.shouldSearch(text)||normalized.startsWith("kereses:");
      boolean phoneAction=CommandRouting.needsAgent(text,true)||forcePhone;
      if(explicitWeb||!phoneAction){
        // Explicit class: NEVER open another installed JARVIS app.
        Intent request=new Intent(this,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("jarvis_kilo32_query",text);
        startActivity(request);
        finish();
        return;
      }
      ControlService c=ControlService.live;''')

main=java/"MainActivity.java"
# For an already running activity, ensure the incoming text is consumed once.
# For a cold launch, dispatch only after the WebView is ready to show the reply.
one(main,
  'void event(String type,String text){',
  '''void dispatchIncomingKiloQuery(Intent intent){
     if(intent==null)return;
     String query=intent.getStringExtra("jarvis_kilo32_query");
     if(query==null||query.trim().isEmpty())return;
     intent.removeExtra("jarvis_kilo32_query");
     command(query);
   }
   @Override protected void onNewIntent(Intent intent){
     super.onNewIntent(intent);
     setIntent(intent);
     h.postDelayed(()->dispatchIncomingKiloQuery(intent),180);
   }
   void event(String type,String text){''')

one(main,
  'else if(!result.isEmpty())event("agent","LEGUTÓBBI LEZÁRT FELADAT • "+result);',
  '''else if(!result.isEmpty())event("agent","LEGUTÓBBI LEZÁRT FELADAT • "+result);
       dispatchIncomingKiloQuery(getIntent());''')

# Prevent "keress az interneten" from being turned into a phone action by
# older command routing heuristics.
route=java/"CommandRouting.java"
one(route,
 'static boolean needsAgent(String raw,boolean auto){String n=AgentRules.norm(raw);',
 '''static boolean needsAgent(String raw,boolean auto){
   if(OnlineSearch.isCapabilityQuestion(raw)||OnlineSearch.shouldSearch(raw))return false;
   String n=AgentRules.norm(raw);''')

# Treat requests to start a search module as a request for a query, not a
# fictional second app. Actual online searches already happen in MainActivity.
one(main,
  'A JARVIS KILO 3.1 alkalmazásban van külön online keresőmodul, amely Bing RSS és Google News élő adatokat tud lekérni, ha a hálózat eléri őket.',
  '''A JARVIS KILO 3.2 alkalmazásnak SAJÁT internetes keresőmodulja van, amely Bing RSS és Google News találatokat kér le. Internetes információkhoz SOHA ne irányítsd a felhasználót a JARVIS 2.0 vagy másik alkalmazásba, és ne állítsd, hogy egy külön keresőmodult el kell indítani. A kereső automatikusan fut, ha internetes keresésre utaló kérést kap. Ha nem hangzott el keresőkifejezés, egyszerűen kérdezd meg, mire keressen.''')
# Clarify the scope of the legacy-looking command dialog.
one(activity,'setTitle("JARVIS • Telefonfeladat")',
             'setTitle("KILO 3.2 • Keresés és telefonfeladat")')
one(activity,
    'status.setText("Mit intézzek el a telefonon?");',
    'status.setText("Írj be online keresést, kérdést vagy telefonfeladatot. Mindent ez a KILO 3.2 kezel.");')

manifest=root/"AndroidManifest.xml"
one(manifest,'package="hu.jarvis.kilo31"','package="hu.jarvis.kilo32"')
one(manifest,'android:label="JARVIS KILO 3.1"','android:label="JARVIS KILO 3.2"')
one(manifest,'android:versionName="3.1-Kilo-Online"','android:versionName="3.2-Kilo-Unified"')
for p in java.glob("*.java"):
    s=p.read_text(encoding="utf-8")
    if "hu.jarvis.kilo31" in s:
        p.write_text(s.replace("hu.jarvis.kilo31","hu.jarvis.kilo32"),encoding="utf-8")
ui=root/"assets/index.html"
one(ui,"JARVIS KILO / 3.1 • HU","JARVIS KILO / 3.2 • HU")
(root/"KILO32-README.md").write_text("""JARVIS KILO 3.2
- ONE Kilo brain, ONE local online search service, ONE local Android accessibility executor.
- Floating command dialog sends internet/chat queries back to the SAME Kilo MainActivity with explicit Intent; never to another installed JARVIS.
- More complex app navigation stays in the local AgentRunner with explicit screen-text consent.
- The 2.0 legacy-looking command dialog has been renamed to clarify that it is part of 3.2.
- Install under package hu.jarvis.kilo32 independently of prior experiments.
- Phone-automation behavior needs real Android device testing. GitHub compilation does not prove that it works.
""",encoding="utf-8")
print("KILO 3.2 unified routing patch succeeded.")
