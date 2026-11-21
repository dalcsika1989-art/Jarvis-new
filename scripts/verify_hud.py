#!/usr/bin/env python3
"""Fast, dependency-free static health checks before the Android APK build."""
from html.parser import HTMLParser
from pathlib import Path
from xml.etree import ElementTree
import re, subprocess, sys, tempfile

ROOT=Path(__file__).resolve().parents[1]
asset=ROOT/"app/src/main/assets/index.html"
class IDs(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids=[]
    def handle_starttag(self,tag,attrs):
        for key,val in attrs:
            if key=="id":self.ids.append(val)

page=asset.read_text(encoding="utf-8")
html=IDs();html.feed(page)
ids=set(html.ids)
dupes={name for name in ids if html.ids.count(name)>1}
if dupes:raise SystemExit("Duplicate HTML IDs: "+str(dupes))
match=re.search(r"<script>(.*?)</script>",page,re.S)
if not match:raise SystemExit("Missing inline JARVIS script")
js=match.group(1)
required=set(re.findall(r'\$\(\s*["\x27]([A-Za-z_][A-Za-z_0-9-]*)["\x27]\s*\)',js))
missing=required-ids
if missing:raise SystemExit("Missing HTML elements: "+str(sorted(missing)))
with tempfile.TemporaryDirectory() as folder:
    path=Path(folder)/"hud.js";path.write_text(js,encoding="utf-8")
    subprocess.run(["node","--check",str(path)],check=True)
for relative in ["app/src/main/AndroidManifest.xml","app/src/main/res/xml/jarvis_accessibility.xml","app/src/main/res/values/strings.xml"]:
    ElementTree.parse(ROOT/relative)
main=(ROOT/"app/src/main/java/com/jarvis/hud/MainActivity.java").read_text()
methods=set(re.findall(r'@JavascriptInterface\s+public\s+\w+\s+(\w+)\s*\(',main))
called=set(re.findall(r'JarvisNative\.(\w+)\s*\(',js))
unsupported=called-methods
if unsupported:raise SystemExit("JS calls unknown Android bridge functions: "+str(sorted(unsupported)))
java=list((ROOT/"app/src/main/java/com/jarvis/hud").glob("*.java"))
for file in java:
    source=file.read_text(encoding="utf-8")
    name=re.search(r"public\s+(?:final\s+)?class\s+(\w+)",source)
    if name and file.stem!=name.group(1):raise SystemExit("Class name mismatch: "+str(file))
print("PASS: HUD inline JavaScript syntax, DOM ids, XML and native bridge functions")
