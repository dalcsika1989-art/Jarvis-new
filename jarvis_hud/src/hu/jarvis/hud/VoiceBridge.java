package hu.jarvis.hud;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;

/** User-initiated Hungarian dictation and optional system TTS. Not always-listening. */
public final class VoiceBridge {
    private static final int SPEECH_REQUEST=8316;
    private final Activity activity;
    private final WebView web;
    private final SharedPreferences prefs;
    private TextToSpeech tts;
    private volatile boolean ready=false;
    private volatile boolean hungarian=false;

    VoiceBridge(Activity activity,WebView web){
        this.activity=activity;this.web=web;
        prefs=activity.getSharedPreferences("jarvis_voice",Context.MODE_PRIVATE);
        tts=new TextToSpeech(activity,new TextToSpeech.OnInitListener(){
            @Override public void onInit(int status){
                if(status!=TextToSpeech.SUCCESS){ready=false;return;}
                ready=true;
                try{
                    int lang=tts.setLanguage(new Locale("hu","HU"));
                    hungarian=lang!=TextToSpeech.LANG_NOT_SUPPORTED &&
                              lang!=TextToSpeech.LANG_MISSING_DATA;
                    tts.setSpeechRate(0.94f);
                    tts.setPitch(0.89f);
                }catch(Exception ignored){hungarian=false;}
            }
        });
    }

    @JavascriptInterface public void listen(){
        activity.runOnUiThread(new Runnable(){@Override public void run(){
            try{
                Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"hu-HU");
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT,"JARVIS – magyar parancs");
                intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,3);
                activity.startActivityForResult(intent,SPEECH_REQUEST);
                emit("listening","Beszédfelismerő megnyitva");
            }catch(ActivityNotFoundException e){emit("error","Nincs telepített beszédfelismerő az Androidon.");}
            catch(Exception e){emit("error","A mikrofon indítása sikertelen.");}
        }});
    }

    @JavascriptInterface public boolean feedback(){
        return prefs.getBoolean("feedback",false);
    }
    @JavascriptInterface public void setFeedback(boolean value){
        prefs.edit().putBoolean("feedback",value).apply();
    }
    @JavascriptInterface public void say(final String text){
        if(!feedback() || text==null || text.isEmpty())return;
        final String safe=text.length()>220?text.substring(0,220):text;
        activity.runOnUiThread(new Runnable(){@Override public void run(){
            if(!ready||!hungarian||tts==null){emit("tts_unavailable","Magyar TTS-hang nem érhető el. A beszédmotorban magyar nyelvi adat szükséges.");return;}
            try{tts.speak(safe,TextToSpeech.QUEUE_FLUSH,null,"jarvis-response");}
            catch(Exception e){emit("tts_unavailable","Hiba a beszédszintézisben.");}
        }});
    }

    void onResult(int requestCode,int resultCode,Intent data){
        if(requestCode!=SPEECH_REQUEST)return;
        if(resultCode!=Activity.RESULT_OK||data==null){
            emit("cancel","Beszédfelismerés megszakítva");return;
        }
        ArrayList<String> results=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if(results==null||results.isEmpty()||results.get(0)==null){
            emit("error","Nem sikerült felismerni a parancsot.");return;
        }
        emit("recognized",results.get(0).trim());
    }

    void stop(){
        try{if(tts!=null){tts.stop();tts.shutdown();tts=null;}}catch(Exception ignored){}
    }

    private void emit(String type,String text){
        try{
            final String json=new JSONObject().put("type",type).put("text",text).toString();
            activity.runOnUiThread(new Runnable(){@Override public void run(){
                try{web.evaluateJavascript("window.JarvisVoice && window.JarvisVoice.receive("+JSONObject.quote(json)+");",null);}
                catch(Exception ignored){}
            }});
        }catch(Exception ignored){}
    }
}