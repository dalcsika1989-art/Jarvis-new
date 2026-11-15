package hu.jarvis.local;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
public final class ConversationMemory {
 private ConversationMemory(){}
 private static File path(Context c){return new File(new File(JarvisHome.initialize(c),"memory"),"dialog.txt");}
 public static synchronized String recent(Context c){
  File f=path(c);if(!f.isFile())return "";
  try{
   byte[] all=new byte[(int)Math.min(f.length(),128*1024)];
   try(RandomAccessFile raf=new RandomAccessFile(f,"r")){
    raf.seek(Math.max(0,f.length()-all.length));raf.readFully(all);
   }
   String s=new String(all,StandardCharsets.UTF_8);
   return s.length()>2400?s.substring(s.length()-2400):s;
  }catch(IOException e){return "";}
 }
 public static synchronized String recentForPrompt(Context c){
  String recent=recent(c);
  if(recent.length()<=1000)return recent;
  int cut=recent.length()-1000;
  int next=recent.indexOf("\\nFelhasználó:",cut);
  return next>=0?recent.substring(next):recent.substring(cut);
 }
 public static synchronized void append(Context c,String user,String assistant){
  try(FileOutputStream out=new FileOutputStream(path(c),true)){
   String entry="\nFelhasználó: "+clean(user)+"\nJARVIS: "+clean(assistant)+"\n";
   out.write(entry.getBytes(StandardCharsets.UTF_8));
  }catch(IOException ignored){}
 }
 private static String clean(String s){return s.replace('\n',' ').replace('\r',' ').trim();}
}
