#!/usr/bin/env python3
"""KILO 3.7 virtual finger, independently installable Android APK.

The toggle controls a non-touchable accessibility overlay showing AI touch
coordinates. When enabled, the phone agent prefers *real accessibility gestures*
on already verified live UI nodes. If gesture dispatch is unavailable, the
existing AccessibilityNodeInfo click path remains as a fallback.

Run after the 3.6 JSON repair, keeping all earlier Kilo behavior.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
java=root/"src/hu/jarvis/app"

def one(path,old,new):
    s=path.read_text(encoding="utf-8")
    count=s.count(old)
    if count!=1:
        raise RuntimeError(f"KILO 3.7: {path.name}: expected exactly one occurrence, found {count} for {old[:100]!r}")
    path.write_text(s.replace(old,new,1),encoding="utf-8")

control=java/"ControlService.java"

one(control,
    'WindowManager wm;ReactorOverlay orbit;',
    'WindowManager wm;ReactorOverlay orbit;VirtualFingerOverlay virtualFinger;')

one(control,
    'orbit=new ReactorOverlay(this);orbit.show();remote=new RemotePhone(this);',
    '''orbit=new ReactorOverlay(this);orbit.show();
      virtualFinger=new VirtualFingerOverlay(this);
      virtualFinger.setEnabled(getSharedPreferences("jarvis",0).getBoolean("virtualFingerEnabled",false));
      remote=new RemotePhone(this);''')

one(control,
    ' boolean gesture(Rect r,boolean swipe,boolean up){',
    ''' boolean virtualFingerEnabled(){
      return getSharedPreferences("jarvis",0).getBoolean("virtualFingerEnabled",false);
    }
    void setVirtualFingerEnabled(boolean enabled){
      getSharedPreferences("jarvis",0).edit().putBoolean("virtualFingerEnabled",enabled).apply();
      if(virtualFinger!=null)virtualFinger.setEnabled(enabled);
      JarvisApp.event("fingerEnabled",enabled?"true":"false");
      JarvisApp.event("agent",enabled?
        "VIRTUÁLIS UJJ BE • a JARVIS ellenőrzött képernyőelemekre tud érintési gesztust küldeni":
        "VIRTUÁLIS UJJ KI • hagyományos Android kisegítő műveletek");
    }
    /** Long-press on the exact live accessibility node rectangle. */
    boolean virtualHold(Rect source){
      if(!virtualFingerEnabled()||locked()||source.width()<2||source.height()<2)return false;
      AccessibilityNodeInfo root=appRoot();
      if(root==null||AgentRules.sensitivePackage(String.valueOf(root.getPackageName())))return false;
      Rect r=new Rect(source);
      Rect screen=new Rect(0,0,getResources().getDisplayMetrics().widthPixels,
        getResources().getDisplayMetrics().heightPixels);
      if(!r.intersect(screen))return false;
      float x=r.exactCenterX(),y=r.exactCenterY();
      if(virtualFinger!=null)virtualFinger.pointAt(Math.round(x),Math.round(y),"TARTÁS");
      Path path=new Path();path.moveTo(x,y);
      return dispatchGesture(new GestureDescription.Builder()
        .addStroke(new GestureDescription.StrokeDescription(path,0,700)).build(),
        new GestureResultCallback(){
          @Override public void onCancelled(GestureDescription gesture){
            JarvisApp.event("agent","A virtuális ujj hosszú érintését az Android megszakította.");
          }
        },main);
    }
    boolean gesture(Rect r,boolean swipe,boolean up){''')

one(control,
    'Path path=new Path();float x=r.exactCenterX(),y=r.exactCenterY();if(swipe){',
    '''Path path=new Path();float x=r.exactCenterX(),y=r.exactCenterY();
      if(virtualFingerEnabled()&&virtualFinger!=null)
        virtualFinger.pointAt(Math.round(x),Math.round(y),swipe?"GÖRGET":"ÉRINTÉS");
      if(swipe){''')

one(control,
    'else if(action.equals("tap")||action.equals("long_press")){AccessibilityNodeInfo original=target(a),n=original;for(int k=0;k<6&&n!=null;k++,n=n.getParent()){',
    '''else if(action.equals("tap")||action.equals("long_press")){
      // All gestures remain scoped to a fresh, verified accessibility node.
      AccessibilityNodeInfo original=target(a),n=original;
      if(virtualFingerEnabled()){
        Rect hit=new Rect();original.getBoundsInScreen(hit);
        if(action.equals("tap"))ok=gesture(hit,false,false);
        else ok=virtualHold(hit);
      }
      if(!ok)for(int k=0;k<6&&n!=null;k++,n=n.getParent()){''')

one(control,
    'if(!ok&&action.equals("tap")){target(a);Rect r=new Rect();original.getBoundsInScreen(r);ok=gesture(r,false,false);}}else if(action.equals("type"))',
    'if(!ok&&action.equals("tap")){target(a);Rect r=new Rect();original.getBoundsInScreen(r);ok=gesture(r,false,false);}}else if(action.equals("type"))')

one(control,
    'else if(action.equals("submit")){AccessibilityNodeInfo n=target(a);',
    '''else if(action.equals("submit")){AccessibilityNodeInfo n=target(a);
      if(virtualFingerEnabled()&&virtualFinger!=null){
        Rect targetRect=new Rect();n.getBoundsInScreen(targetRect);
        virtualFinger.pointAt(targetRect.centerX(),targetRect.centerY(),"KERESÉS");
      }''')

one(control,
    'else if(action.equals("scroll")){AccessibilityNodeInfo n=target(a);',
    '''else if(action.equals("scroll")){AccessibilityNodeInfo n=target(a);
      if(virtualFingerEnabled()&&virtualFinger!=null){
        Rect scrollRect=new Rect();n.getBoundsInScreen(scrollRect);
        virtualFinger.pointAt(scrollRect.centerX(),scrollRect.centerY(),"GÖRGET");
      }''')

one(control,
    'if(orbit!=null)orbit.dismiss();super.onDestroy();',
    'if(orbit!=null)orbit.dismiss();if(virtualFinger!=null)virtualFinger.stop();super.onDestroy();')

main=java/"MainActivity.java"
one(main,
    'event("autoOps",String.valueOf(prefs.getBoolean("automaticOperations",false)));',
    'event("autoOps",String.valueOf(prefs.getBoolean("automaticOperations",false)));event("fingerEnabled",String.valueOf(prefs.getBoolean("virtualFingerEnabled",false)));')

one(main,
    '@JavascriptInterface public void setAutomaticOperations(boolean enabled){',
    '''@JavascriptInterface public void setVirtualFinger(boolean enabled){
      runOnUiThread(()->{
        ControlService service=ControlService.live;
        if(service==null){
          event("fingerEnabled","false");
          event("error","A virtuális ujjhoz először kapcsold be a JARVIS KILO 3.7 kisegítő szolgáltatását az Android beállításaiban.");
          return;
        }
        service.setVirtualFingerEnabled(enabled);
        event("fingerEnabled",enabled?"true":"false");
      });
    }
    @JavascriptInterface public void setAutomaticOperations(boolean enabled){''')

ui=root/"assets/index.html"
one(ui,
    '<button id="autoops" aria-pressed="false" title="Folyamatos Android-feladatvégrehajtás külön jóváhagyás nélkül a szokásos lépésekhez">AUTOMATIKUS MŰVELETEK: KI</button>',
    '<button id="autoops" aria-pressed="false" title="Folyamatos Android-feladatvégrehajtás külön jóváhagyás nélkül a szokásos lépésekhez">AUTOMATIKUS MŰVELETEK: KI</button><button id="fingerbtn" aria-pressed="false" title="Mutatja az AI érintési pontját, és Android-gesztussal végrehajtja a jóváhagyott érintést">VIRTUÁLIS UJJ: KI</button>')

one(ui,
    'let muted=false,active=false,level=0,taskmode=true,autoOperations=false;',
    'let muted=false,active=false,level=0,taskmode=true,autoOperations=false,virtualFinger=false;')

one(ui,
    "$('autoops').onclick=()=>call('setAutomaticOperations',!autoOperations);",
    """$('autoops').onclick=()=>call('setAutomaticOperations',!autoOperations);
$('fingerbtn').onclick=()=>call('setVirtualFinger',!virtualFinger);
function updateVirtualFinger(){
  const btn=$('fingerbtn');
  btn.textContent='VIRTUÁLIS UJJ: '+(virtualFinger?'BE':'KI');
  btn.setAttribute('aria-pressed',String(virtualFinger));
  btn.style.borderColor=virtualFinger?'#64eaff':'#2d6171';
  btn.style.background=virtualFinger?'#153e48':'#0b202b';
}""")

one(ui,
    "if(type==='autoOps'){autoOperations=text==='true';updateAutoOperations();return}",
    "if(type==='fingerEnabled'){virtualFinger=text==='true';updateVirtualFinger();return}if(type==='autoOps'){autoOperations=text==='true';updateAutoOperations();return}")

manifest=root/"AndroidManifest.xml"
one(manifest,'package="hu.jarvis.kilo36"','package="hu.jarvis.kilo37"')
one(manifest,'android:label="JARVIS KILO 3.6"','android:label="JARVIS KILO 3.7"')
one(manifest,'android:versionName="3.6-Kilo-JsonFix"','android:versionName="3.7-Kilo-Finger"')

for p in java.glob("*.java"):
    content=p.read_text(encoding="utf-8")
    if "hu.jarvis.kilo36" in content:
        p.write_text(content.replace("hu.jarvis.kilo36","hu.jarvis.kilo37"),encoding="utf-8")

one(ui,"JARVIS KILO / 3.6 • HU","JARVIS KILO / 3.7 • HU")

(root/"KILO37-README.md").write_text("""JARVIS KILO 3.7 – VIRTUÁLIS UJJ
Különálló csomagazonosító hu.jarvis.kilo37; nem módosítja a 3.5-ös alkalmazást.
Főképernyő: VIRTUÁLIS UJJ: BE/KI.
BE: a KILO telefonos AI-ügynöke által ellenőrzött kattintási célpontnál
a képernyő fölött megjelenik egy rajzolt, világoskék ujj és érintési pont.
A virtuális ujj tényleges Android AccessibilityService.dispatchGesture()
érintést vagy hosszan érintést hajt végre az ellenőrzött elem közepére.
Ha a gesztus nem fogadható el, visszaáll az AccessibilityNodeInfo gombra kattintásra.
A marker önmagában nem érintőréteg: FLAG_NOT_TOUCHABLE. Nem akadályozza a felhasználó
érintéseit; 1.1 másodperc után magától eltűnik.
Nem kerülhet meg képernyőzárat, jelszót vagy Android-engedélyeket.
Az automatikus műveletek korábbi jóváhagyási szabályai megmaradnak.
KI: hagyományos kisegítő szolgáltatás, ujj nélkül.
Telepítés után a KILO 3.7 saját kisegítő szolgáltatását kell engedélyezni.
A build sikere nem bizonyítja a készülékoldali gesztusvégrehajtást.
""",encoding="utf-8")
print("KILO 3.7 virtual finger toggle and accessibility gesture overlay patched.")
