package hu.jarvis.local;
import java.nio.charset.StandardCharsets;
public final class OfflineBrain {
 private static final String error;
 static {
  String e=null;
  try {System.loadLibrary("jarvis_llm");}
  catch(Throwable t){e=t.toString();}
  error=e;
 }
 private OfflineBrain(){}
 public interface Progress {void onText(String partial);}
 private interface NativeProgress {void onBytes(byte[] partial);}
 public static boolean available(){return error==null;}
 public static String failure(){return error;}
 public static String reply(String model,String prompt){
  return replyStreaming(model,prompt,null);
 }
 public static String replyStreaming(String model,String prompt,Progress progress){
  if(error!=null)return "Offline motor nem tölthető be: "+error;
  NativeProgress nativeProgress=progress==null?null:bytes->{
   if(bytes!=null)progress.onText(new String(bytes,StandardCharsets.UTF_8));
  };
  byte[] answer=generate(model,prompt,100,nativeProgress);
  if(answer==null)return "A helyi AI-motor nem adott választ.";
  return new String(answer,StandardCharsets.UTF_8);
 }
 private static native byte[] generate(String model,String prompt,int maxTokens,NativeProgress progress);
}
