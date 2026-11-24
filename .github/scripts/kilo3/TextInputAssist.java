package hu.jarvis.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Android accessibility text entry for third-party apps.
 * ACTION_SET_TEXT first; ACTION_PASTE only if Android rejects the first method.
 * Does not read the user's existing clipboard or enter password fields.
 */
final class TextInputAssist {
  private TextInputAssist(){}

  static AccessibilityNodeInfo resolve(AccessibilityNodeInfo window,AccessibilityNodeInfo requested)throws Exception{
    if(requested!=null && safeEditor(requested))return requested;

    // Search bars can be a clickable wrapper, with an editable child.
    AccessibilityNodeInfo nested=findEditable(requested,0);
    if(nested!=null)return nested;

    // The user has already tapped the search bar. Favor its keyboard-focused editor.
    if(window!=null){
      AccessibilityNodeInfo focused=window.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
      if(safeEditor(focused))return focused;
    }
    // Avoid guessing when the screen has multiple possible text inputs.
    List<AccessibilityNodeInfo> all=new ArrayList<>();
    collect(window,all,0);
    if(all.size()==1)return all.get(0);
    if(all.isEmpty())throw new Exception("A keresőmező még nem látható szerkeszthető szövegmezőként. Érintsd meg a mezőt, és próbáld újra.");
    throw new Exception("Több beviteli mezőt látok. Először érintsd meg a keresőmezőt; nem gépelek találomra.");
  }

  static boolean safeEditor(AccessibilityNodeInfo n){
    return n!=null&&n.isEditable()&&n.isVisibleToUser()&&n.isEnabled()&&!n.isPassword();
  }
  static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo n,int depth){
    if(n==null||depth>10)return null;
    if(safeEditor(n))return n;
    for(int i=0;i<n.getChildCount();i++){
      AccessibilityNodeInfo found=findEditable(n.getChild(i),depth+1);
      if(found!=null)return found;
    }
    return null;
  }
  static void collect(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> list,int depth){
    if(n==null||depth>22||list.size()>12)return;
    if(safeEditor(n))list.add(n);
    for(int i=0;i<n.getChildCount();i++)collect(n.getChild(i),list,depth+1);
  }

  static String type(ControlService service, AccessibilityNodeInfo n, String text)throws Exception{
    if(!safeEditor(n))throw new Exception("A célpont nem aktív szövegmező.");
    if(text==null||text.isEmpty())throw new Exception("Üres szöveget nem küldök.");
    if(text.length()>3000)throw new Exception("A beírandó szöveg túl hosszú.");

    int type=n.getInputType()&0xfff;
    if(type==129||type==145||type==225||type==18||n.isPassword())
      throw new Exception("Védett jelszómező; kézi bevitel szükséges.");

    // Send actions to the exact accessibility node, not as blind keyboard taps.
    n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    Bundle b=new Bundle();
    b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
    boolean accepted=n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
    if(accepted){
      if(!n.refresh())return "A szöveg beírását az Android elfogadta; az új képernyőn ellenőrzöm.";
      CharSequence actual=n.getText();
      if(text.equals(actual==null?"":actual.toString()))
        return "Szöveg bevitele sikeres (Android ACTION_SET_TEXT, ellenőrizve).";
    }

    // Some WebView and Compose search boxes reject ACTION_SET_TEXT.
    // Only a non-sensitive user-supplied string is placed on the clipboard.
    // Never read prior clipboard contents; restoring them would involve reading private data.
    ClipboardManager clipboard=(ClipboardManager)service.getSystemService(Context.CLIPBOARD_SERVICE);
    if(clipboard==null)throw new Exception("A keresőmező elutasította a szöveget, a vágólap nem érhető el.");
    if(!n.refresh()||!safeEditor(n))
      throw new Exception("A keresőmező közben megváltozott. Új képernyővizsgálat szükséges.");

    CharSequence old=n.getText();
    String existing=old==null?"":old.toString();
    if(!existing.isEmpty()){
      Bundle selection=new Bundle();
      selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT,0);
      selection.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT,existing.length());
      n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION,selection);
    }

    clipboard.setPrimaryClip(ClipData.newPlainText("JARVIS KILO szöveg",text));
    n.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
    boolean pasted=n.performAction(AccessibilityNodeInfo.ACTION_PASTE);
    if(!pasted)throw new Exception("A keresőmező elutasította mind a közvetlen beírást, mind a Beillesztés műveletet.");
    if(n.refresh()){
      CharSequence actual=n.getText();
      if(text.equals(actual==null?"":actual.toString()))
        return "Szöveg beillesztése sikeres (Android ACTION_PASTE, ellenőrizve).";
    }
    return "A szöveg beillesztését az Android elfogadta; az új képernyőn ellenőrzöm.";
  }
}
