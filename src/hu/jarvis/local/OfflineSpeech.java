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
public final class OfflineSpeech {
 public interface Callback {void result(String text);void error(String message);}
 private SpeechRecognizer recognizer;
 public void listen(Activity activity,Callback callback){
  if(!SpeechRecognizer.isRecognitionAvailable(activity)){
   callback.error("Nem található beszédfelismerő a telefonon.");return;
  }
  close();
  try{
   boolean offline=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(activity);
   recognizer=offline?SpeechRecognizer.createOnDeviceSpeechRecognizer(activity):SpeechRecognizer.createSpeechRecognizer(activity);
   recognizer.setRecognitionListener(new RecognitionListener(){
    public void onReadyForSpeech(Bundle b){}
    public void onBeginningOfSpeech(){}
    public void onRmsChanged(float rms){}
    public void onBufferReceived(byte[] b){}
    public void onEndOfSpeech(){}
    public void onPartialResults(Bundle b){}
    public void onEvent(int type,Bundle b){}
    public void onResults(Bundle b){
     ArrayList<String> results=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
     if(results!=null&&!results.isEmpty())callback.result(results.get(0));
     else callback.error("Nem értettem a beszédet.");
    }
    public void onError(int error){callback.error("Beszédfelismerés hibakód: "+error);}
   });
   Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.forLanguageTag("hu-HU").toLanguageTag());
   i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
   i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,offline);
   recognizer.startListening(i);
  }catch(Exception e){callback.error("Beszédfelismerés nem indítható: "+e.getMessage());}
 }
 public void close(){if(recognizer!=null){recognizer.destroy();recognizer=null;}}
}
