package hu.jarvis.local;
public final class OfflineBrain {
 private static final String error;
 static {
  String e=null;
  try { System.loadLibrary("jarvis_llm"); }
  catch(Throwable t) {e=t.toString();}
  error=e;
 }
 public static boolean available(){return error==null;}
 public static String failure(){return error;}
 public static String reply(String model,String prompt){
  if(error!=null) return "Offline motor nem tölthető be: "+error;
  byte[] response=generate(model,prompt,128);
  if(response==null)return "A helyi AI-motor nem adott választ.";
  return new String(response,java.nio.charset.StandardCharsets.UTF_8);
 }
 private static native byte[] generate(String model,String prompt,int maxTokens);
}
