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
import org.json.JSONArray
import java.net.URL
import java.net.HttpURLConnection
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
        @JavascriptInterface fun speak(text:String){runOnUiThread{if(voiceReady)tts?.speak(text.take(1600),TextToSpeech.QUEUE_FLUSH,null,"jarvis")}}
        @JavascriptInterface fun aiChat(id:String, messagesJson:String){
            if(id.length !in 1..32 || !id.all { it.isDigit() } || messagesJson.length>22000){
                js("window.jarvisAiReply("+JSONObject.quote(id)+","+result(false,"Érvénytelen AI-kérés.")+")")
                return
            }
            worker.execute{
                val reply=askKiloFree(messagesJson)
                js("window.jarvisAiReply("+JSONObject.quote(id)+","+reply.toString()+")")
            }
        }
        @JavascriptInterface fun stopVoice(){runOnUiThread{tts?.stop()}}
        @JavascriptInterface fun stopListening(){runOnUiThread{stopVoiceCapture()}}
        @JavascriptInterface fun listen(){runOnUiThread{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41)
            else startVoice()
        }}
    }

    // Online AI: anonymous/free Kilo Gateway. Only explicit chat text/history leaves the device.
    // Accessibility nodes, device state and local application actions are never included.
    private fun askKiloFree(messagesJson:String): JSONObject {
        var conn:HttpURLConnection?=null
        return try{
            val incoming=JSONArray(messagesJson)
            if(incoming.length() !in 1..9)return result(false,"Üres vagy túl hosszú beszélgetés.")
            val messages=JSONArray()
            messages.put(JSONObject().put("role","system").put("content",
                "Te J.A.R.V.I.S. vagy, egy magyarul természetesen beszélgető, tárgyilagos Android-asszisztens. " +
                "Válaszolj magyarul, világosan, több mondatban is, ha indokolt. " +
                "Nem vagy azonos a ChatGPT-vel. Nincs automatikus hozzáférésed a telefonhoz, az alkalmazásokhoz vagy élő adatokhoz. " +
                "Csak a felhasználó itt elküldött beszélgetését látod. " +
                "Ne állítsd, hogy műveletet végrehajtottál, ha arra nem kaptál ellenőrzött visszajelzést. " +
                "Ne kérj jelszót vagy privát adatot. A válaszaid hangosan felolvasásra is alkalmasak legyenek."
            ))
            for(i in 0 until incoming.length()){
                val item=incoming.optJSONObject(i) ?: return result(false,"Hibás üzenet.")
                val role=item.optString("role")
                val content=item.optString("content").trim()
                if(role !in setOf("user","assistant") || content.isEmpty() || content.length>2500)
                    return result(false,"Nem megfelelő üzenetadat.")
                messages.put(JSONObject().put("role",role).put("content",content))
            }
            if(incoming.getJSONObject(incoming.length()-1).optString("role")!="user")
                return result(false,"Hiányzik a felhasználói kérdés.")
            val payload=JSONObject().put("model","kilo-auto/free")
                .put("messages",messages) // Same payload shape as the working Python client.
            conn=(URL("https://api.kilo.ai/api/gateway/chat/completions").openConnection() as HttpURLConnection)
            val connection=conn
            connection.requestMethod="POST"
            connection.connectTimeout=12000
            connection.readTimeout=26000
            connection.instanceFollowRedirects=false
            connection.doOutput=true
            connection.setRequestProperty("Content-Type","application/json")
            connection.setRequestProperty("Accept","application/json")
            val bytes=payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.outputStream.use{it.write(bytes)}
            val status=connection.responseCode
            if(status !in 200..299){
                // A 429 can be from the gateway IP quota OR a transient upstream shared pool.
                // Inspect the provider response; never assert that the user has exhausted quota.
                val errorBody=try {
                    connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(6000) }.orEmpty()
                } catch (_:Exception) { "" }
                val json=try { JSONObject(errorBody) } catch (_:Exception) { JSONObject() }
                val errorObject=json.optJSONObject("error") ?: JSONObject()
                val meta=errorObject.optJSONObject("metadata")
                    ?: json.optJSONObject("metadata") ?: JSONObject()
                val source=meta.optString("limit_source",json.optString("limit_source","")).take(100)
                val providerCode=meta.optString("provider_error_code",json.optString("provider_error_code","")).take(100)
                val errorType=errorObject.optString("error_type",json.optString("error_type","")).take(100)
                val retryAfter=connection.getHeaderField("Retry-After")?.toIntOrNull()
                    ?: meta.optInt("retry_after_seconds",-1)
                val fromProvider=source.contains("upstream",true) ||
                    source.contains("provider",true) ||
                    providerCode.contains("model_rate_limit",true)
                val error=when(status){
                    401,403->"A Kilo AI nem fogadta el az anonim kérést ($status)."
                    429 -> if(fromProvider)
                        "Kilo HTTP 429: az ingyenes modell szolgáltatója átmenetileg korlátozza a kéréseket; ez nem bizonyítja a saját kvóta kimerülését."
                    else
                        "Kilo HTTP 429: a Kilo-kapu kéréskorlátot jelzett. A pontos forrás nem mindig azonosítható."
                    in 500..599->"A Kilo AI szervere hibát jelzett ($status)."
                    else->"Kilo AI HTTP $status."
                }
                result(false,error).put("httpStatus",status)
                    .put("limitSource",source.ifBlank{"nem ismert"})
                    .put("providerCode",providerCode.ifBlank{"nem ismert"})
                    .put("errorType",errorType.ifBlank{"nem ismert"})
                    .put("retryAfterSeconds",retryAfter)
            }else{
                val body=connection.inputStream.bufferedReader(Charsets.UTF_8).use{it.readText().take(75000)}
                val response=JSONObject(body)
                val content=response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
                val answer=when(content){
                    is String->content.trim()
                    is JSONArray->(0 until content.length()).joinToString(" ") { j ->
                        content.optJSONObject(j)?.optString("text") ?: ""
                    }.trim()
                    else->""
                }
                if(answer.isBlank()) result(false,"A szolgáltatótól üres AI-válasz érkezett.")
                else result(true).put("reply",answer.take(6000))
            }
        }catch(e:java.net.SocketTimeoutException){
            result(false,"A Kilo AI túllépte az időkorlátot. Ellenőrizd az internetkapcsolatot.")
        }catch(e:Exception){
            result(false,"A Kilo AI kapcsolat nem sikerült. Ellenőrizd az internetkapcsolatot. ("+e.javaClass.simpleName+")")
        }finally{conn?.disconnect()}
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
