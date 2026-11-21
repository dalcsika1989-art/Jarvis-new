package hu.jarvis.localhud

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class JarvisAccessibilityService : AccessibilityService() {
    private val main = Handler(Looper.getMainLooper())
    private var worker: ScheduledExecutorService? = null
    override fun onServiceConnected() {
        super.onServiceConnected()
        worker?.shutdownNow()
        LocalBridge.connected = true
        worker = Executors.newSingleThreadScheduledExecutor().also {
            it.scheduleWithFixedDelay({ poll() },0,200,TimeUnit.MILLISECONDS)
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() {
        LocalBridge.connected=false
        worker?.shutdownNow()
        worker=null
        super.onDestroy()
    }
    private fun poll() {
        try {
            val response=LocalBridge.request("/bridge/poll")
            val cmd=response.optJSONObject("job") ?: return
            val id=response.optString("id")
            val deadline=response.optLong("deadline")
            val future=CompletableFuture<JSONObject>()
            main.post {
                if (!LocalBridge.connected || SystemClock.elapsedRealtime()>=deadline) future.complete(result(false,"Command expired."))
                else try { execute(cmd){future.complete(it)} } catch (_:Exception){future.complete(result(false,"Android action failed."))}
            }
            val answer=try { future.get(3,TimeUnit.SECONDS) } catch (_:Exception){ result(false,"Action not confirmed.") }
            LocalBridge.request("/bridge/result",JSONObject().put("id",id).put("result",answer).toString())
        } catch (_:Exception) {}
    }
    private fun execute(cmd:JSONObject,done:(JSONObject)->Unit) {
        val a=cmd.optString("action")
        val p=cmd.optJSONObject("params") ?: JSONObject()
        fun complete(ok:Boolean,why:String="Android rejected the action.") { done(result(ok,if(ok) "" else why)) }
        when(a) {
            "home" -> complete(performGlobalAction(GLOBAL_ACTION_HOME))
            "back" -> complete(performGlobalAction(GLOBAL_ACTION_BACK))
            "recents" -> complete(performGlobalAction(GLOBAL_ACTION_RECENTS))
            "open_app" -> {
                val query=p.optString("name").trim()
                val apps=packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0)
                val matches=apps.filter { it.loadLabel(packageManager).toString().equals(query,true) || it.activityInfo.packageName.equals(query,true) }
                val app=matches.singleOrNull()
                if(app==null) complete(false,"App not found or ambiguous.")
                else { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(app.activityInfo.packageName,app.activityInfo.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); complete(true) }
            }
            "snapshot" -> {
                val r=rootInActiveWindow
                val state=JSONObject().put("package",r?.packageName?.toString() ?: "").put("nodes",JSONArray())
                if(r!=null && r.packageName?.toString()!=packageName) {
                    val list=state.getJSONArray("nodes")
                    walk(r){n->
                        if(list.length()<100 && n.isVisibleToUser && !n.isPassword && !n.isEditable) {
                            val label=(n.text?.toString() ?: n.contentDescription?.toString() ?: "").take(160)
                            if(label.isNotBlank())list.put(JSONObject().put("text",label).put("clickable",n.isClickable).put("class",n.className?.toString() ?: ""))
                        }
                    }
                }
                done(result(true).put("state",state))
            }
            else -> {
                val root=rootInActiveWindow
                if(root==null || root.packageName?.toString()==packageName) { complete(false,"No target app visible. Open an app first.");return }
                if(a=="tap" || a=="scroll_down" || a=="scroll_up") {
                    val rect=Rect()
                    root.getBoundsInScreen(rect)
                    if(rect.isEmpty){complete(false,"Target bounds unavailable.");return}
                    val x=rect.exactCenterX();val y=rect.exactCenterY()
                    val path=Path()
                    if(a=="tap")path.moveTo(x,y) else {
                        val dy=rect.height()*0.25f
                        path.moveTo(x, if(a=="scroll_down")y+dy else y-dy)
                        path.lineTo(x, if(a=="scroll_down")y-dy else y+dy)
                    }
                    val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,if(a=="tap")80 else 350)).build()
                    val sent=dispatchGesture(gesture,object:GestureResultCallback(){
                        override fun onCompleted(gestureDescription:GestureDescription?){complete(true)}
                        override fun onCancelled(gestureDescription:GestureDescription?){complete(false,"Gesture cancelled.")}
                    },main)
                    if(!sent)complete(false,"Gesture not accepted.")
                } else if(a=="type_text") {
                    val n=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    val value=p.optString("text")
                    val args=Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) }
                    complete(n!=null && n.isEditable && !n.isPassword && value.length<=4000 && n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"No compatible editable field.")
                } else if(a=="click_text") {
                    val target=p.optString("text").trim()
                    val matches=mutableListOf<AccessibilityNodeInfo>()
                    walk(root){n->if(target.isNotEmpty() && n.isVisibleToUser && !n.isPassword && (n.text?.toString()?.equals(target,true)==true || n.contentDescription?.toString()?.equals(target,true)==true))matches.add(n)}
                    if(matches.size!=1){complete(false,"No unique visible match.");return}
                    var n:AccessibilityNodeInfo?=matches[0]
                    while(n!=null && !n.isClickable)n=n.parent
                    complete(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Target not clickable.")
                } else complete(false,"Unsupported command.")
            }
        }
    }
    private fun walk(root:AccessibilityNodeInfo,visit:(AccessibilityNodeInfo)->Unit) {
        val stack=java.util.ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var seen=0
        while(stack.isNotEmpty() && seen++<500) {
            val n=stack.removeFirst()
            visit(n)
            for(i in 0 until n.childCount)n.getChild(i)?.let{stack.addLast(it)}
        }
    }
}
