package hu.jarvis.local;
import android.content.Context;
import java.io.File;
public final class JarvisHome {
 private static final String[] DIRS={"brain","models","memory","voice","tasks","logs","cache"};
 private JarvisHome(){}
 public static File initialize(Context context){
  File base=new File(context.getFilesDir(),"jarvis_home");
  if(!base.isDirectory()&&!base.mkdirs())throw new IllegalStateException("Nem hozható létre a JARVIS tárhely.");
  for(String name:DIRS){
   File dir=new File(base,name);
   if(!dir.isDirectory()&&!dir.mkdirs())throw new IllegalStateException("Nem hozható létre: "+name);
  }
  return base;
 }
 public static long freeBytes(Context context){return initialize(context).getUsableSpace();}
}
