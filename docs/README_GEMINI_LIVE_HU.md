# JARVIS 2.0 – Gemini Live integrációs forrás (nem telepíthető APK)

Az `assets/index.html` **az eredeti JARVIS-2.0.apk** változatlan vezérlőfelülete; csak egy helyi script-hivatkozás került bele. A `gemini-live.js` a Google Gemini Live WebSocket protokollját használja.

## Funkciók a forrásban

- A régi JARVIS 2.0 HUD megmarad.
- Gemini 3.1 Flash Live Preview: valós idejű WebSocket, magyar nyelvű utasítás.
- Audio: 16 kHz PCM be és 24 kHz PCM ki. A natív mikrofonhozzáféréshez WebView-engedély és Android RECORD_AUDIO engedély szükséges.
- Egyedi API-kulcs a felületen, **nem tároljuk**, nem kerül a forráskódba.
- Szöveges chat, audio-válasz és magyar átírás.
- A már létező `window.Native.send` kapcsolathoz illesztett, **csak engedélyezett** telefonműveletek: appnyitás, vissza, home, értesítések, görgetés, felirat-kattintás. A függvényválasz `requested`, nem „végrehajtva”: a képernyő megerősítése még szükséges.
- Újabb Android-engedélyeket vagy írásos jóváhagyást igénylő feladatokat a Live híd önmagában nem hajt végre.
- Megszakításkor a kimeneti hangtár törlődik; a mikrofon gombbal indítható/leállítható.

## Az eredeti alkalmazásba építés technikai követelményei

1. A teljes JARVIS 2.0 Android **forrásprojekt** szükséges, amely tartalmazza a korábbi Native JavaScript-hidat és az Android WebView konfigurációját. Az APK önmagában csak lefordított DEX-kódot tartalmaz.
2. Az eredeti **aláíró keystore / signing key** kell az adatok elvesztése nélküli frissítéshez. A JARVIS 2.0 csomagneve `hu.jarvis.app`, a régi fejlesztési APK-k más aláírással készültek. A kulcsot nem kell nyilvánosan vagy chatben megosztani, célszerű saját gépen vagy titkos CI secretben megőrizni.
3. A `docs/ANDROID_WEBVIEW_ADAPTER.java` az eredeti Activity `WebView` kezelésének kiegészítését mutatja. Fontos a biztonságos HTTPS virtuális eredet és az engedélyezett mikrofonhozzáférés; a `file://` megnyitásból nem garantált a `navigator.mediaDevices.getUserMedia`.
4. Az eredeti Native.bridge műveleteit meg kell őrizni. A teljes körű telefonvezérlés a korábbi Android-kisegítő szolgáltatáson és a felhasználói engedélyeken múlik, nem a Gemini szolgáltatás közvetlen jogosultsága.
5. A Gemini Live API nem azonos a fogyasztói Gemini Live alkalmazással, külön [Google AI Studio](https://aistudio.google.com/apikey) kulcs és hozzáférési kvóta szükséges. Az API használata költséggel járhat; a sebesség hálózat- és szerverterhelés-függő.
6. Mielőtt telepíthető APK-nak nevezzük, ellenőrizni kell az Android WebView mikrofont, az élő kapcsolatot és legalább egy appnyitási parancs valódi képernyős eredményét. Ez ebben a csomagban **nem tesztelt**.

**Nem szabad ezt a ZIP-et APK-ként telepíteni; ez fejlesztési forrás és integrációs lépés.**

Források: https://ai.google.dev/gemini-api/docs/live-api/get-started-websocket , https://ai.google.dev/gemini-api/docs/live-api/capabilities , https://ai.google.dev/gemini-api/docs/live-api/tools