package com.jarvis.hud;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import java.util.List;
public class JarvisAccessibility extends AccessibilityService {
 public static JarvisAccessibility instance;
 @Override public void onServiceConnected(){instance=this; AccessibilityServiceInfo info=getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;setServiceInfo(info);}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){instance=null;super.onDestroy();}
 public boolean clickText(String text){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null||text==null||text.isEmpty())return false;try{List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text);for(AccessibilityNodeInfo n:nodes){AccessibilityNodeInfo p=n;for(int i=0;i<5&&p!=null;i++,p=p.getParent())if(p.isClickable()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;}return false;}finally{root.recycle();}}
 public boolean scroll(){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;try{return scrollNode(root);}finally{root.recycle();}}
 private boolean scrollNode(AccessibilityNodeInfo n){if(n.isScrollable()&&n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null&&scrollNode(c))return true;}return false;}
 public boolean input(String value){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;try{return inputNode(root,value);}finally{root.recycle();}}
 private boolean inputNode(AccessibilityNodeInfo n,String value){if(n.isEditable()&&n.isFocused()){Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);}for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);if(c!=null&&inputNode(c,value))return true;}return false;}
}
