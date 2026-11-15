package hu.jarvis.local;
import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import java.util.Locale;
public final class HungarianVoice implements TextToSpeech.OnInitListener {
 private TextToSpeech tts;
 private volatile boolean ready=false;
 private String message="Magyar offline hang még nem érhető el.";
 public HungarianVoice(Context context){tts=new TextToSpeech(context.getApplicationContext(),this);}
 @Override public void onInit(int status){
  if(status!=TextToSpeech.SUCCESS){message="A telefon beszédmotorja nem indult.";return;}
  Locale hu=new Locale("hu","HU");
  if(tts.isLanguageAvailable(hu)<TextToSpeech.LANG_AVAILABLE){message="Nincs telepített magyar TTS hang.";return;}
  tts.setLanguage(hu);
  Voice chosen=null;
  try{
   for(Voice voice:tts.getVoices()){
    if(voice.getLocale().getLanguage().equals("hu")&&!voice.isNetworkConnectionRequired()){
     chosen=voice;break;
    }
   }
  }catch(Exception ignored){}
  if(chosen==null){message="Nincs letöltött, hálózat nélkül működő magyar hang.";return;}
  tts.setVoice(chosen);ready=true;message="Magyar offline hang készen áll.";
 }
 public boolean isReady(){return ready;}
 public String status(){return message;}
 public void say(String text){if(ready)tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"jarvis-answer");}
 public void close(){tts.stop();tts.shutdown();}
}
