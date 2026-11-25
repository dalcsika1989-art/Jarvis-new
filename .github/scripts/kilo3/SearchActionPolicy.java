package hu.jarvis.app;

/**
 * Conservative approval policy for read-only marketplace research.
 * Model-generated "confirm":true must not block ordinary navigation when
 * the actual Android action and its accessible label are non-sensitive.
 *
 * Sending, publishing, buying, deleting, permission changes and unknown
 * unlabeled targets still require approval or manual verification.
 */
final class SearchActionPolicy {
  private SearchActionPolicy(){}

  static boolean marketplaceResearch(String goal) {
    String n=AgentRules.norm(goal==null?"":goal);
    boolean marketplace=n.contains("kleinanzeigen")||n.contains("ebay")||n.contains("e-bay");
    boolean researching=n.contains("keress")||n.contains("keresd")||n.contains("kereses")||n.contains("ingyen")||n.contains("verschenken")||n.contains("zu verschenken");
    boolean sending=n.matches(".*\\b(?:kuldes|kuldj|elkuld|uzenetet irj|rendeld|rendeles|vasarolj|vasarlas|fizess|torold|torles)\\b.*");
    return marketplace&&researching&&!sending;
  }

  static boolean safeNavigation(String goal,String action,String label,String text) {
    if(!marketplaceResearch(goal))return false;
    String type=action==null?"":action;
    String l=AgentRules.norm(label==null?"":label);
    String t=AgentRules.norm(text==null?"":text);

    // Don't treat a clickable but unlabeled control as harmless.
    if(type.equals("tap")&&l.isEmpty())return false;
    // Long press may favorite, share or trigger a destructive context menu.
    if(type.equals("long_press"))return false;

    // Never bypass consent for explicit risky labels or user input.
    if(AgentRules.needsConfirm(type,l,t,false))return false;

    if(type.equals("tap"))return true;
    if(type.equals("type"))return !l.isEmpty()&&
       (l.contains("such")||l.contains("keres")||l.contains("search")||
        l.contains("ort")||l.contains("umkreis")||l.contains("radius")||
        l.contains("entfernung")||l.contains("plz")||l.contains("standort")||
        l.contains("preis")||l.contains("preisgrenze"));
    if(type.equals("submit")){
      return l.contains("such")||l.contains("keres")||l.contains("search")||l.contains("finden");
    }
    return type.equals("launch")||type.equals("scroll")||type.equals("back")||
      type.equals("home")||type.equals("wait");
  }

  static boolean needsApproval(String goal,String action,String label,String text,boolean aiRequest){
    // Real action risk always overrides the model's confidence.
    boolean risky=AgentRules.needsConfirm(action,label,text,false);
    if(risky)return true;
    // A model may over-classify a benign step as risky; the local
    // accessible action + label may override ONLY in safe search navigation.
    if(aiRequest&&!safeNavigation(goal,action,label,text))return true;
    return false;
  }

  /** Retry an AI "ask user" once or twice when it merely asks the user
   *  to navigate an ordinary search/filter dialog. Never retry login/captcha.
   */
  static boolean retryableAsk(String goal,String question){
    if(!marketplaceResearch(goal))return false;
    String n=AgentRules.norm(question==null?"":question);
    if(n.matches(".*(captcha|bejelentkez|login|password|jelszo|sms kod|kodot|engedely|pay|fizet|bank).*"))return false;
    return n.matches(".*(folytat|weiter|gomb|kattints|keres|such|szuro|filter|tavolsag|km|korzet).*");
  }
}
