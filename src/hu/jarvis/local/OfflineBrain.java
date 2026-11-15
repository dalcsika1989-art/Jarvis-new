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
  return generate(model,prompt,128);
 }
 private static native String generate(String model,String prompt,int maxTokens);
}
