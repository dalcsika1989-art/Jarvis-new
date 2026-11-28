# JARVIS HUD — 10 modul Android tesztprojekt

Ez a **forráskód és buildkonfiguráció**, nem igazoltan lefordított APK. A telepíthetőséget és minden Android-funkciót **valódi készüléken** kell ellenőrizni.

## Beépített modulok és valós határok

| Modul | Beépített megoldás | A még szükséges ellenőrzés / korlát |
|---|---|---|
| 1. AI-agy | OpenAI és Gemini natív HTTPS API-kérés; választó; Android Keystore AES/GCM | Saját API-kulcs és kvóta kell, élő API-teszt nélkül nem igazolt |
| 2. Hang | Magyar Android RecognizerIntent és Android TTS, mélységállítással | Jelenleg kérésenkénti beszédfelismerés, **nem** állandó háttérben hallgató hotword |
| 3. Autopilot | Accessibility: kattintás feliratra, görgetés, beírás, vissza, főképernyő, képernyő szövege | Engedély kell; minden alkalmazás másképp viselkedik; képernyőzárnál nem általánosan működik |
| 4. Memória | LocalStorage, keresés, feladatlista; AI-nak való megosztás külön opt-in | A tárolás helyi, nem felhő-szinkronizált |
| 5. ARC HUD | Animált reaktor, voice/thinking/error állapotok | Telefonon vizuális és teljesítményteszt |
| 6. Mission Control | Többlépéses helyi + AI-tervek, lépéslista, jóváhagyás, STOP, hibaértesítés | A WebView-ból vezérelt küldetések háttérben nem garantáltan folytatódnak |
| 7. Garmin | Android-értesítés + Garmin Connect tükrözés | Nincs közvetlen Garmin SDK-kapcsolat vagy garantált kézbesítés |
| 8. Diagnosztika | Akkumulátor, RAM, Android-verzió, jogosultságok, kulcsbeállítás állapota | Készülékteszttel kell megerősíteni |
| 9. Research | Külső webkeresés és Gemini Google Search grounding API forráslinkekkel | Gemini API-kulcs szükséges; források a szolgáltatás válaszától függnek |
| 10. Lebegő HUD | Android TYPE_APPLICATION_OVERLAY előtérszolgáltatással, húzható gyorsikonnal | Külön overlay engedély kell, agresszív akkumulátorkezelés megszakíthatja |

## GitHub Actions — APK
A workflow: `.github/workflows/build-apk.yml` — `assembleDebug`, utána `JARVIS-10-MODULES-debug-APK` artifact.

Az alkalmazás **nem állítható késznek**, amíg:
1. A GitHub Actions build **success** státuszú és tényleges APK-t ad.
2. Android telefonon telepítés, indulás, képernyő és beszéd ellenőrzött.
3. Az AI-kérés saját kulccsal sikeresen visszatért.
4. Az Accessibility engedélyezve, és legalább 3 valódi app-művelet sikerült.
5. A Garmin Connect értesítés tükrözése és a lebegő HUD ellenőrzött.

## Biztonság
Az API-kulcsok Android Keystore AES-GCM védelemmel kerülnek mentésre. A forráskód nem tartalmaz kulcsot.
A képernyő kiolvasása és a kattintás csak felhasználói műveletből történjen.
Külső üzenetek, fizetés, vásárlás és törlés külön jóváhagyást igényelnek.
Harmadik félnek személyes memóriát továbbítani csak az opt-in kapcsoló után szabad.
Az app nem igényel Termuxot.