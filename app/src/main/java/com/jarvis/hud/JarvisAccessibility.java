package com.jarvis.hud;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.Display;
import android.hardware.HardwareBuffer;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;
import java.util.List;
public class JarvisAccessibility extends AccessibilityService {
 public static volatile JarvisAccessibility instance;
 @Override public void onServiceConnected(){instance=this;AccessibilityServiceInfo i=getServiceInfo();i.flags|=AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;setServiceInfo(i);}
 @Override public void onAccessibilityEvent(AccessibilityEvent event){}
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){instance=null;super.onDestroy();}
 public boolean clickText(String text){
  if(text==null||text.trim().isEmpty()||text.length()>180)return false;
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;
  try{List<AccessibilityNodeInfo> nodes=root.findAccessibilityNodeInfosByText(text);for(AccessibilityNodeInfo n:nodes){
   if(n==null||!n.isVisibleToUser())continue;
   AccessibilityNodeInfo p=n;
   for(int i=0;i<6&&p!=null;i++,p=p.getParent())if(p.isClickable()&&p.isEnabled()&&p.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
  }return false;}finally{root.recycle();}
 }
 public boolean scroll(boolean up){AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;try{return scrollNode(root,up,0);}finally{root.recycle();}}
 private boolean scrollNode(AccessibilityNodeInfo n,boolean up,int depth){
  if(depth>16||n==null)return false;
  if(n.isScrollable()&&n.performAction(up?AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD:AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
  for(int i=0;i<n.getChildCount();i++)if(scrollNode(n.getChild(i),up,depth+1))return true;return false;
 }
 public boolean input(String value){if(value==null||value.length()>1200)return false;
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return false;try{return inputNode(root,value,0);}finally{root.recycle();}
 }
 private boolean inputNode(AccessibilityNodeInfo n,String value,int depth){
  if(n==null||depth>16)return false;
  if(n.isEditable()&&n.isFocused()&&!n.isPassword()){
   Bundle b=new Bundle();b.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value);
   return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b);
  }
  for(int i=0;i<n.getChildCount();i++)if(inputNode(n.getChild(i),value,depth+1))return true;return false;
 }
 public String readScreen(){
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return "";
  try{StringBuilder out=new StringBuilder();appendText(root,out,0);return out.toString();}finally{root.recycle();}
 }
 private void appendText(AccessibilityNodeInfo node,StringBuilder out,int depth){
  if(node==null||depth>16||out.length()>3500||node.isPassword()||!node.isVisibleToUser())return;
  CharSequence text=node.getText();
  if(text!=null&&text.length()>0&&text.length()<400){out.append(text).append("\n");}
  for(int i=0;i<node.getChildCount();i++)appendText(node.getChild(i),out,depth+1);
 }
 public void screenshot(Consumer<String> callback) {
  if(Build.VERSION.SDK_INT<30){callback.accept("ERROR:Requires Android 11+");return;}
  takeScreenshot(Display.DEFAULT_DISPLAY,getMainExecutor(),new AccessibilityService.TakeScreenshotCallback(){
   @Override public void onSuccess(AccessibilityService.ScreenshotResult result){
    HardwareBuffer buffer=result.getHardwareBuffer();
    if(buffer==null){callback.accept("ERROR:No screenshot buffer");return;}
    Bitmap hw=null; Bitmap small=null; Bitmap software=null;
    try{
     hw=Bitmap.wrapHardwareBuffer(buffer,result.getColorSpace());
     if(hw==null)throw new IllegalStateException("Hardware bitmap unavailable");
     int width=hw.getWidth(),height=hw.getHeight();
     int targetWidth=Math.min(640,width);
     int targetHeight=(int)(height*(targetWidth/(double)width));
     software=hw.copy(Bitmap.Config.ARGB_8888,false);
     small=Bitmap.createScaledBitmap(software,targetWidth,targetHeight,true);
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     if(!small.compress(Bitmap.CompressFormat.JPEG,66,bytes))throw new IllegalStateException("Image conversion failed");
     String payload=Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP);
     callback.accept(payload);
    }catch(Exception ex){callback.accept("ERROR:"+ex.getMessage());}
    finally{if(small!=null&&!small.isRecycled())small.recycle();if(software!=null&&!software.isRecycled())software.recycle();buffer.close();}
   }
   @Override public void onFailure(int code){callback.accept("ERROR:Screenshot service code "+code);}
  });
 }

}