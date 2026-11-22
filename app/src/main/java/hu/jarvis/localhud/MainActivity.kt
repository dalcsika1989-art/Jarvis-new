package hu.jarvis.localhud

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var web: WebView
    private val worker=Executors.newSingleThreadExecutor()
    private var tts:TextToSpeech?=null
    private var voiceReady=false
    private var recognizer: SpeechRecognizer? = null
    private var listening=false
    private var voiceResultDelivered=false
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        web=WebView(this)
        web.setBackgroundColor(0xff02080e.toInt())
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.blockNetworkLoads=true
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?, request:WebResourceRequest?):Boolean=true
        }
        web.webChromeClient=WebChromeClient()
        web.addJavascriptInterface(NativeHud(),"NativeHud")
        setContentView(web)
        worker.execute { LocalBridge.start() }
        val html=assets.open("index.html").bufferedReader().use{it.readText()}
        web.loadDataWithBaseURL("https://jarvis.local/",html,"text/html","UTF-8",null)
        tts=TextToSpeech(this){status->
            if(status==TextToSpeech.SUCCESS){
                val engine=tts
                if(engine!=null){
                    engine.language=Locale.forLanguageTag("hu-HU")
                    engine.voices?.firstOrNull{it.locale.language=="hu" && !it.isNetworkConnectionRequired}?.let{engine.voice=it}
                    engine.setPitch(0.85f)
                    engine.setSpeechRate(1.02f)
                    voiceReady=true
                }
            }
        }
    }
    private fun js(script:String){runOnUiThread{if(!isDestroyed)web.evaluateJavascript(script,null)}}
    private fun voice(text:String,error:String=""){js("window.jarvisVoice("+JSONObject.quote(text)+","+JSONObject.quote(error)+")")}
    private fun speechStatus(status:String){js("window.jarvisSpeechStatus("+JSONObject.quote(status)+")")}
    inner class NativeHud {
        @JavascriptInterface fun request(id:String,path:String,body:String,background:Boolean){
            if(id.length>32)return
            if(path !in setOf("/bridge/state","/bridge/command") || body.length>16384){
                js("window.jarvisReply("+JSONObject.quote(id)+","+result(false,"Invalid request")+")");return
            }
            runOnUiThread {
                if(background && LocalBridge.connected)moveTaskToBack(true)
                if(!isDestroyed)worker.execute{
                    if(background && LocalBridge.connected)Thread.sleep(400)
                    val response=LocalBridge.request(path,if(body.isEmpty())null else body)
                    js("window.jarvisReply("+JSONObject.quote(id)+","+response.toString()+")")
                }
            }
        }
        @JavascriptInterface fun accessibility(){runOnUiThread{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))}}
        @JavascriptInterface fun speak(text:String){runOnUiThread{if(voiceReady)tts?.speak(text.take(500),TextToSpeech.QUEUE_FLUSH,null,"jarvis")}}
        @JavascriptInterface fun stopVoice(){runOnUiThread{tts?.stop()}}
        @JavascriptInterface fun stopListening(){runOnUiThread{stopVoiceCapture()}}
        @JavascriptInterface fun listen(){runOnUiThread{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41)
            else startVoice()
        }}
    }
    private fun startVoice(){
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speechStatus("A telefon beszédfelismerő szolgáltatása nem érhető el.")
            voice("","A telefon beszédfelismerő szolgáltatása nem érhető el.")
            return
        }
        stopVoiceCapture()
        voiceResultDelivered=false
        try {
            val engine=SpeechRecognizer.createSpeechRecognizer(this)
            recognizer=engine
            engine.setRecognitionListener(object: RecognitionListener {
                override fun onReadyForSpeech(params:Bundle?){speechStatus("Hallgatás...")}
                override fun onBeginningOfSpeech(){speechStatus("Beszéd észlelve...")}
                override fun onRmsChanged(rmsdB:Float){}
                override fun onBufferReceived(buffer:ByteArray?){}
                override fun onEndOfSpeech(){speechStatus("Feldolgozás...")}
                override fun onError(error:Int){
                    if (voiceResultDelivered) return
                    voiceResultDelivered=true
                    val message=when(error){
                        SpeechRecognizer.ERROR_AUDIO -> "Hangrögzítési hiba."
                        SpeechRecognizer.ERROR_CLIENT -> "A hangfelismerő kliens hibát jelzett."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Hiányzik a mikrofonengedély."
                        SpeechRecognizer.ERROR_NETWORK -> "Hálózati hiba a beszédfelismerésnél."
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hálózati időtúllépés."
                        SpeechRecognizer.ERROR_NO_MATCH -> "Nem értettem a beszédet. Próbáld újra."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "A beszédfelismerő foglalt."
                        SpeechRecognizer.ERROR_SERVER -> "A beszédfelismerő szerver hibát jelzett."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Nem érkezett beszéd."
                        else -> "Beszédfelismerési hiba ($error)."
                    }
                    listening=false
                    speechStatus(message)
                    voice("",message)
                    engine.destroy()
                    if(recognizer===engine)recognizer=null
                }
                override fun onResults(results:Bundle?){
                    if (voiceResultDelivered) return
                    voiceResultDelivered=true
                    val spoken=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    listening=false
                    speechStatus(if(spoken.isNotEmpty())"Felismert szöveg" else "Üres hangfelismerési eredmény")
                    voice(spoken,if(spoken.isBlank())"Nem kaptam felismert szöveget." else "")
                    engine.destroy()
                    if(recognizer===engine)recognizer=null
                }
                override fun onPartialResults(partialResults:Bundle?){}
                override fun onEvent(eventType:Int,params:Bundle?){}
            })
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE,"hu-HU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"hu-HU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            }
            listening=true
            speechStatus("Mikrofon indítása...")
            engine.startListening(intent)
        }catch(e:Exception){
            stopVoiceCapture()
            voice("","A beszédfelismerő nem indítható: "+(e.message?:"ismeretlen hiba"))
        }
    }
    private fun stopVoiceCapture(){
        val engine=recognizer
        recognizer=null
        listening=false
        voiceResultDelivered=true
        try{engine?.cancel()}catch(_:Exception){}
        try{engine?.destroy()}catch(_:Exception){}
        speechStatus("Leállítva")
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==41) { if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startVoice() else voice("","A mikrofonengedély nincs megadva.") }
    }
    override fun onDestroy(){
        stopVoiceCapture()
        worker.shutdownNow()
        tts?.stop();tts?.shutdown()
        web.removeJavascriptInterface("NativeHud")
        web.destroy()
        super.onDestroy()
    }
}
