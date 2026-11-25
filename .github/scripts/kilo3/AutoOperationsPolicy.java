package hu.jarvis.app;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opt-in automatic execution for a single user-issued Android task.
 *
 * Model-supplied confirm=true is NOT a blanket reason to pause on safe clicks.
 * High-impact actions require explicit scope, and dangerous screens remain blocked.
 */
final class AutoOperationsPolicy {
  private AutoOperationsPolicy(){}

  static String n(String s){return AgentRules.norm(s==null?"":s);}
  static boolean has(String s,String expression){return Pattern.compile(expression).matcher(s).find();}

  static boolean hazardousLabel(String label){
    String x=n(label);
    return has(x,"(?iu)(?:^|\\W)(?:kaufen|buy|purchase|bestellen|checkout|order|bezahlen|payment|pay|fizet|vasarol|rendeles|transfer|uberweisen|utalas|bank|delete|remove|loschen|torles|torol|deinstall|uninstall|reset|format|install|telepit|permission|berechtigung|engedely|allow|grant|zulassen|akzeptieren|akzeptier|accept|zustimmen|agree|cookies|consent|anmelden|login|password|jelszo|passwort|pin|captcha|hivas|call|anrufen|publish|veroffentlichen|posten)(?:$|\\W)");
  }

  static boolean sendButton(String label){
    String x=n(label);
    return has(x,"(?iu)(?:^|\\W)(?:send|senden|sendmessage|kuldes|elkuld|kuld|schicken|absenden|abschicken|nachricht senden)(?:$|\\W)");
  }

  static boolean favoriteButton(String label){
    String x=n(label);
    return has(x,"(?iu)(?:favorit|merken|merkliste|kedvenc|sziv|heart|save|speichern)");
  }

  /** Explicit recipient + exact user-supplied body; not a model-generated message. */
  static String userRecipient(String goal){
    String before=n(goal);
    int colon=before.lastIndexOf(':');
    if(colon<0)return "";
    before=before.substring(0,colon);
    Matcher m=Pattern.compile("\\b([a-z0-9_.-]{2,})n(?:a|e)k\\b").matcher(before);
    String result="";
    while(m.find())result=m.group(1);
    return result;
  }
  static String userMessage(String goal){
    if(goal==null)return "";
    int colon=goal.lastIndexOf(':');
    if(colon<0||colon>=goal.length()-2)return "";
    return goal.substring(colon+1).trim();
  }
  static boolean userRequestedSend(String goal){
    String g=n(goal);
    return has(g,"\\b(kuldj|kuldd|kuldes|elkuld|elkuldj|send|schick|schicke|senden)\\b") &&
      !userRecipient(goal).isEmpty() &&
      userMessage(goal).length()>=2;
  }

  /** For a message, UI must visibly show BOTH the named recipient and exact body. */
  static boolean provenSend(String goal,String screenData,String editableDraft){
    if(!userRequestedSend(goal)||screenData==null)return false;
    String screen=n(screenData);
    String target=userRecipient(goal);
    String message=n(userMessage(goal));
    return screen.contains(target)&&message.equals(n(editableDraft));
  }

  static boolean needsApproval(String goal,String action,String label,String input,
        boolean modelConfirm,String screenPackage,String currentScreenText,String editableDraft){
    String a=action==null?"":action;
    if(!AgentRules.knownAction(a))return true;
    if(a.equals("done")||a.equals("ask")||a.equals("wait"))return false;
    if(AgentRules.sensitivePackage(screenPackage==null?"":screenPackage))return true;
    String l=n(label);
    boolean sending=sendButton(label);
    if(sending){
      // Only an explicitly requested, fully specified message can be sent
      // without another prompt. This is intentionally stricter than generic AUTO.
      return !(a.equals("tap")||a.equals("submit"))||!provenSend(goal,currentScreenText,editableDraft);
    }
    if(hazardousLabel(label))return true;
    if(a.equals("launch")||a.equals("scroll")||a.equals("back")||a.equals("home")||a.equals("recents"))return false;
    if(a.equals("tap"))return l.isEmpty();
    if(a.equals("type"))return has(l,"\\b(password|passwort|jelszo|pin|otp|tan|cvv|security)\\b");
    if(a.equals("submit")){
      // Search-box keyboard action only, not login, publishing, checkout, etc.
      return !has(l,"(such|search|keres|finden)");
    }
    if(a.equals("long_press")){
      String g=n(goal);
      return !(favoriteButton(label)&&has(g,"(kedvenc|sziv|favorit|merken|jelold|mentsd)"));
    }
    return true;
  }

  static boolean retryRoutine(String question){
    String q=n(question);
    if(has(q,"(captcha|jelszo|password|login|bejelentkez|sms kod|ketfaktor|ellenorzo kod|fizet|vasarol|kaufen|payment|torles|delete|engedely|permission|cimzett|uzenet szoveg)"))return false;
    return has(q,"(folytat|weiter|keres|such|szuro|filter|kattints|gomb|gorgess|tavolsag|kilometer|km|korzet|beir|irj be)");
  }
}
