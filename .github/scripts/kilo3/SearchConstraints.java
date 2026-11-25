package hu.jarvis.app;
import java.util.regex.*;

/** Preserves user-specified distance filters in Android marketplace searches. */
final class SearchConstraints {
  private SearchConstraints(){}
  static int radiusKm(String goal){
    String n=AgentRules.norm(goal==null?"":goal);
    Matcher m=Pattern.compile("(\\d{1,3})\\s*(?:km|kilometer)\\b").matcher(n);
    if(m.find())try{return Integer.parseInt(m.group(1));}catch(Exception ignored){}
    if(n.matches(".*\\b(?:tiz kilometer|tiz km|tizkilometer|tizkilometeres)\\b.*"))return 10;
    if(n.matches(".*\\b(?:ot kilometer|ot km|otkilometer|otkilometeres)\\b.*"))return 5;
    return -1;
  }
  static String guidance(String goal){
    int km=radiusKm(goal);
    if(km<=0)return "";
    return "\nKÖTELEZŐ FELHASZNÁLÓI SZŰRŐ: "+km+" km-es keresési távolság. "+
      "Ha a Kleinanzeigen jelenleg például 4 km-t mutat, az NEM azonos a kért "+
      km+" km-rel. Keresd meg a Távolság / Umkreis / Radius szűrőt, és "+
      "válaszd ki a pontos "+km+" km-t, majd ellenőrizd az aktuális képernyőn. "+
      "A szűrő kiválasztásához ne kérj kézi kattintást, ha a képernyőfa "+
      "azonosítható kattintható elemet tartalmaz. "+
      "Ha az alkalmazás nem támogatja a pontos értéket, mondd el a felhasználónak "+
      "és ne állítsd, hogy teljesült a keresés. "+
      "Nem szabad önkényesen 4 vagy más km-re cserélni a felhasználó kérését.";
  }
}
