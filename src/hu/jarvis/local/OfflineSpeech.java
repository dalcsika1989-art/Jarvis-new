package hu.jarvis.local;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.util.ArrayList;
import java.util.Locale;

/** Single-tap Hungarian recognition; local engine preferred, online Android fallback. */
public final class OfflineSpeech {
 public interface Callback {void result(String text);void error(String message);}
 private SpeechRecognizer recognizer;
 private boolean started;
 private int session;
 public boolean isListening(){return started;}
 public void listen(Activity activity,Callback callback){
  close();
  if(!SpeechRecognizer.isRecognitionAvailable(activity)){
   callback.error("Nincs beszédfelismerő szolgáltatás a telefonon.");return;
  }
  boolean onDevice=false;
  try{onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);}
  catch(Exception ignored){}
  int request=++session;
  open(activity,callback,onDevice,request);
 }
 private void open(Activity activity,Callback callback,boolean offline,int request){
  if(request!=session)return;
  try {
   recognizer=offline?SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
                    :SpeechRecognizer.createSpeechRecognizer(activity);
   if(recognizer==null){started=false;callback.error("A mikrofon szolgáltatás nem indult.");return;}
   started=true;
   final boolean[] finished={false};
   recognizer.setRecognitionListener(new RecognitionListener(){
    public void onReadyForSpeech(Bundle b){}
    public void onBeginningOfSpeech(){}
    public void onRmsChanged(float rms){}
    public void onBufferReceived(byte[] b){}
    public void onEndOfSpeech(){}
    public void onPartialResults(Bundle b){}
    public void onEvent(int type,Bundle b){}
    public void onResults(Bundle b){
     if(request!=session||finished[0])return;
     finished[0]=true;
     ArrayList<String> results=b==null?null:b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
     closeRecognizer();
     if(results!=null&&!results.isEmpty()&&!results.get(0).trim().isEmpty())callback.result(results.get(0).trim());
     else callback.error("Nem értettem a beszédet. Próbáld újra.");
    }
    public void onError(int error){
     if(request!=session||finished[0])return;
     finished[0]=true;
     closeRecognizer();
     // Unsupported or missing local language model: try the platform recognizer once.
     if(offline&&(error==SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED||
       error==SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE||
       error==SpeechRecognizer.ERROR_CLIENT)){
      open(activity,callback,false,request);
      return;
     }
     callback.error("Beszédfelismerés hibakód: "+error+"; koppints újra a mikrofonra.");
    }
   });
   Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.forLanguageTag("hu-HU").toLanguageTag());
   intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
   intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,offline);
   recognizer.startListening(intent);
  }catch(Exception e){
   closeRecognizer();
   if(offline)open(activity,callback,false,request);
   else callback.error("Nem indítható a beszédfelismerés: "+e.getMessage());
  }
 }
 private void closeRecognizer(){
  started=false;
  SpeechRecognizer instance=recognizer;
  recognizer=null;
  if(instance!=null){
   try{instance.cancel();}catch(Exception ignored){}
   try{instance.destroy();}catch(Exception ignored){}
  }
 }
 public void close(){session++;closeRecognizer();}
}
