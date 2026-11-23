package hu.jarvis.app;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.*;
import java.net.*;
import java.util.Locale;

/** Live web results for Kilo 3, not a pretend browser-enabled LLM.
 * Only the user's research query is sent to a public search feed.
 * Results are untrusted source data and must be verified/cited by the model.
 */
final class OnlineSearch {
  /** Questions about ability are measured, not answered by a model guessing it is offline. */
  static boolean isCapabilityQuestion(String text){
    String n=AgentRules.norm(text==null?"":text);
    boolean network=n.contains("internet")||n.contains("online")||n.contains("neten")||n.contains("netrol");
    boolean asking=n.contains("tudsz")||n.contains("tudtok")||n.contains("kepes")||n.contains("mukodik")||n.contains("van-e")||n.contains("van internet");
    return network&&asking&&(n.contains("keres")||n.contains("internet")||n.contains("online"));
  }
  /** Search intent is detected automatically, even when user does not say "keress az interneten". */
  static boolean shouldSearch(String text) {
    String n=AgentRules.norm(text==null?"":text);
    if(n.isEmpty())return false;
    // Explicit Android actions must stay with the accessibility agent, not get hijacked by web search.
    if(n.matches(".*(nyisd meg|kattints|gorgess|szivezd|kedvencnek|tedd a kedvencek|foglalj|vasarolj|kuldd el|uzenetet kuldj|torold|telepitsd).*"))return false;
    boolean explicit=n.matches(".*(keress ra|keress az interneten|keress online|online kereses|interneten keress|interneten keres|neten keress|neten keres|keress a neten|keress a weben|google kereses|nezz utana|friss hir|hirek|hireket|mi ujsag|legujabb|aktualis|mostani|mai ujdonsag|ujdonsagok|mi tortent ma|mennyibe kerul most|mennyibe kerul jelenleg|most mennyi|online informacio).*");
    boolean transport=n.matches(".*(vonat|s-bahn|menetrend|indulas|erkezes|kesik|keses|atutas|atszallas|busz|s2 ).*") &&
      n.matches(".*(mikor|hany|most|ma |mai|indul|erkez|kovetkezo|keress|nezz|van |kesik|keses|jarat).*");
    boolean weather=n.matches(".*(idojaras|homerseklet|hany fok|eso lesz|esik e|idojarast).*");
    boolean deals=n.matches(".*(kleinanzeigen|hasznalt ar|hirdetesek|arajanlat|elado|legolcsobb).*") &&
      n.matches(".*(keress|nezz|nezd|talalj|mennyi|milyen|most|mai|ar).*");
    return explicit||transport||weather||deals;
  }
  static String cleanQuery(String t) {
    String s=(t==null?"":t.trim());
    s=s.replaceFirst("(?iu)^\\s*(?:jarvis[,!;:. ]*)?(?:keress\\s+r[aá]|keress\\s+az\\s+interneten|keress\\s+online|n[eé]zz\\s+ut[aá]na(?:\\s+a\\s+neten)?|interneten\\s+keress)\\s*[:,-]?\\s*","");
    return s.trim().isEmpty()?t:s;
  }
  static String status(Context context) {
    try {
      ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if(cm==null)return "Hálózati állapot nem elérhető";
      Network active=cm.getActiveNetwork();
      NetworkCapabilities caps=active==null?null:cm.getNetworkCapabilities(active);
      if(caps==null)return "Nincs aktív hálózat";
      if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))return "Nincs internetképesség a hálózaton";
      if(!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))return "Van hálózat, internet még nem igazolt";
      return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)?"Internet: Wi-Fi ellenőrzött":"Internet: aktív hálózat ellenőrzött";
    }catch(Exception ex){return "A hálózati diagnosztika nem érhető el";}
  }
  static JSONObject search(String query,int limit) throws Exception {
    String cleaned=cleanQuery(query).trim();
    if(cleaned.isEmpty())throw new IOException("Hiányzik a keresőkifejezés");
    limit=Math.max(1,Math.min(8,limit));
    String encoded=URLEncoder.encode(cleaned,"UTF-8");
    boolean news=AgentRules.norm(cleaned).matches(".*(hir|ujdonsag|friss|ma |aktualis|legujabb|mai |2026).*");
    String bing="https://www.bing.com/search?q="+encoded+"&format=rss";
    String google="https://news.google.com/rss/search?q="+encoded+"&hl=hu&gl=DE&ceid=DE:hu";
    String[] sources=news?new String[]{google,bing}:new String[]{bing,google};
    JSONArray results=new JSONArray();
    String lastError="";
    String provider="";
    for(String url:sources){
      try{
        JSONArray found=fetchRss(url,limit);
        if(found.length()>0){
          results=found;
          provider=url.startsWith("https://www.bing")?"Bing web RSS":"Google News RSS";
          break;
        }
      }catch(Exception ex){lastError=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();}
    }
    if(results.length()==0)throw new IOException("Az online kereső nem adott feldolgozható találatot. "+lastError);
    return new JSONObject().put("query",cleaned).put("provider",provider).put("retrievedAt",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",Locale.ROOT).format(new java.util.Date())).put("results",results);
  }
  static JSONArray fetchRss(String url,int limit)throws Exception{
    HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
    c.setConnectTimeout(10000);
    c.setReadTimeout(14000);
    c.setInstanceFollowRedirects(true);
    c.setRequestProperty("User-Agent","JARVIS-Kilo3/1.0 (Android; RSS reader)");
    c.setRequestProperty("Accept","application/rss+xml,application/xml,text/xml,*/*");
    try{
      int status=c.getResponseCode();
      if(status>=400)throw new IOException("Kereső HTTP "+status);
      JSONArray a=new JSONArray();
      try(InputStream in=new BufferedInputStream(c.getInputStream())){
        XmlPullParser p=Xml.newPullParser();
        p.setInput(in,"UTF-8");
        boolean item=false;
        String title="",link="",description="",date="";
        for(int event=p.getEventType();event!=XmlPullParser.END_DOCUMENT;event=p.next()){
          if(event==XmlPullParser.START_TAG){
            String tag=p.getName();
            if("item".equalsIgnoreCase(tag)){item=true;title="";link="";description="";date="";}
            else if(item&&("title".equalsIgnoreCase(tag)||"link".equalsIgnoreCase(tag)||"description".equalsIgnoreCase(tag)||"pubDate".equalsIgnoreCase(tag))){
              String value=p.nextText();
              if("title".equalsIgnoreCase(tag))title=cut(value,170);
              else if("link".equalsIgnoreCase(tag))link=cut(value,600);
              else if("description".equalsIgnoreCase(tag))description=cut(value.replaceAll("<[^>]+>"," "),350);
              else date=cut(value,90);
            }
          }else if(event==XmlPullParser.END_TAG&&"item".equalsIgnoreCase(p.getName())&&item){
            item=false;
            if((link.startsWith("https://")||link.startsWith("http://"))&&!title.trim().isEmpty()){
              a.put(new JSONObject().put("title",title).put("url",link).put("snippet",description).put("date",date));
              if(a.length()>=limit)break;
            }
          }
        }
      }
      return a;
    }finally{c.disconnect();}
  }
  static String cut(String s,int max){
    if(s==null)return "";
    s=s.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]"," ").replaceAll("\\s+"," ").trim();
    return s.length()>max?s.substring(0,max):s;
  }
}
