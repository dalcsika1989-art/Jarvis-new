package hu.jarvis.localhud

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
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
                    engine.language=Locale.US
                    engine.voices?.firstOrNull{it.locale.language=="en" && !it.isNetworkConnectionRequired}?.let{engine.voice=it}
                    engine.setPitch(0.85f)
                    engine.setSpeechRate(1.02f)
                    voiceReady=true
                }
            }
        }
    }
    private fun js(script:String){runOnUiThread{if(!isDestroyed)web.evaluateJavascript(script,null)}}
    private fun voice(text:String,error:String=""){js("window.jarvisVoice("+JSONObject.quote(text)+","+JSONObject.quote(error)+")")}
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
        @JavascriptInterface fun listen(){runOnUiThread{
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),41)
            else startVoice()
        }}
    }
    private fun startVoice(){
        try {
            val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US")
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1)
            startActivityForResult(intent,42)
        } catch (_:Exception){voice("","No Android speech recognizer installed.")}
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==41) { if(grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED)startVoice() else voice("","Microphone permission denied.") }
    }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==42){
            val text=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if(resultCode==RESULT_OK && !text.isNullOrBlank())voice(text) else voice("","Speech recognition cancelled or unavailable.")
        }
    }
    override fun onDestroy(){
        worker.shutdownNow()
        tts?.stop();tts?.shutdown()
        web.removeJavascriptInterface("NativeHud")
        web.destroy()
        super.onDestroy()
    }
}
