package hu.jarvis.hud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Loopback-only client. The secret is stored in private Android preferences, never sent to JS. */
public final class TermuxClient {
    private static final String BASE = "http://127.0.0.1:8765";
    private final Activity activity;
    private final WebView web;
    private final SharedPreferences prefs;

    public TermuxClient(Activity activity, WebView web) {
        this.activity=activity;
        this.web=web;
        this.prefs=activity.getSharedPreferences("jarvis_termux",Context.MODE_PRIVATE);
    }

    @JavascriptInterface public boolean hasToken() {
        return prefs.getString("token","").length()>=24;
    }
    @JavascriptInterface public void pair(final String supplied) {
        new Thread(new Runnable(){@Override public void run(){
            try {
                String token=supplied==null?"":supplied.trim();
                if(token.length()<24 || token.length()>160 || !token.matches("[A-Za-z0-9_-]+")) {
                    emit("pair",false,new JSONObject().put("error","Érvénytelen kulcs."));
                    return;
                }
                JSONObject result=request("GET","/api/health",null,token);
                if(result.optBoolean("ok")) {
                    prefs.edit().putString("token",token).apply();
                    emit("pair",true,result);
                } else emit("pair",false,new JSONObject().put("error","Nem azonosítható a szerver."));
            } catch(Exception e){error("pair",e);}
        }},"JarvisTermuxPair").start();
    }
    @JavascriptInterface public void disconnect(){
        prefs.edit().remove("token").apply();
        emit("disconnect",true,new JSONObject());
    }
    @JavascriptInterface public void fetch(final String name){
        final String path;
        if("health".equals(name))path="/api/health";
        else if("tasks".equals(name))path="/api/tasks";
        else if("knowledge".equals(name))path="/api/knowledge";
        else if("events".equals(name))path="/api/events";
        else {emit(name,false,new JSONObject());return;}
        new Thread(new Runnable(){@Override public void run(){
            try {emit(name,true,request("GET",path,null,getToken()));}
            catch(Exception e){error(name,e);}
        }},"JarvisTermuxGet").start();
    }
    @JavascriptInterface public void remember(final String text){
        if(text==null || text.trim().length()==0 || text.length()>1000){
            emit("remember",false,errorObject("Tudáselem: 1–1000 karakter."));
            return;
        }
        try {
          JSONObject payload=new JSONObject().put("text",text.trim());
          enqueue("remember",payload,"remember");
        } catch(Exception e) {error("remember",e);}
    }

    /** Repeated public HTTPS monitoring; minimum interval five minutes (server enforced). */
    @JavascriptInterface public void watchPage(final String url,final int repeatSeconds){
        try{
            String value=url==null?"":url.trim();
            java.net.URI uri=new java.net.URI(value);
            if(!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost()==null ||
               uri.getUserInfo()!=null || value.length()>2048 ||
               (repeatSeconds!=0 && (repeatSeconds<300 || repeatSeconds>86400))){
                emit("watch",false,errorObject("HTTPS webcím és 0 vagy 300–86400 másodperces ütemezés kell."));
                return;
            }
            JSONObject job=new JSONObject().put("type","fetch_page")
              .put("payload",new JSONObject().put("url",value))
              .put("repeat_seconds",repeatSeconds);
            submit("watch","/api/tasks",job);
        }catch(Exception e){error("watch",e);}
    }

    @JavascriptInterface public void androidAction(final String command,final String argsJson){
        if(!("home".equals(command)||"back".equals(command)||"recents".equals(command)
           ||"notifications".equals(command)||"launch".equals(command)
           ||"scroll_down".equals(command)||"scroll_up".equals(command)
           ||"tap_text".equals(command)||"type_text".equals(command))){
            emit("android",false,errorObject("Nem támogatott Android-művelet."));
            return;
        }
        try {
            JSONObject args=argsJson==null || argsJson.trim().isEmpty()?new JSONObject():new JSONObject(argsJson);
            enqueue("android",new JSONObject().put("command",command).put("args",args),"android");
        } catch(Exception e){error("android",e);}
    }
    @JavascriptInterface public void cancel(final int id){
        if(id<=0){emit("cancel",false,errorObject("Érvénytelen feladatazonosító."));return;}
        try {
            JSONObject data=new JSONObject().put("id",id);
            submit("cancel","/api/tasks/cancel",data);
        } catch(Exception e){error("cancel",e);}
    }
    @JavascriptInterface public void accessibilitySettings(){
        activity.runOnUiThread(new Runnable(){@Override public void run(){
            try {activity.startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}
            catch(Exception ignored){}
        }});
    }

    private String getToken() {
        return prefs.getString("token","");
    }
    private void enqueue(String type,JSONObject payload,String event){
        try{
            JSONObject data=new JSONObject().put("type",type).put("payload",payload);
            submit(event,"/api/tasks",data);
        }catch(Exception e){error(event,e);}
    }
    private void submit(final String event,final String path,final JSONObject value){
        new Thread(new Runnable(){@Override public void run(){
            try {emit(event,true,request("POST",path,value,getToken()));}
            catch(Exception e){error(event,e);}
        }},"JarvisTermuxPost").start();
    }
    private static JSONObject request(String method,String path,JSONObject data,String token) throws Exception{
        if(token==null || token.length()<24)throw new IllegalStateException("Először párosítsa a Termux-szervert.");
        HttpURLConnection conn=(HttpURLConnection)new URL(BASE+path).openConnection();
        try{
            conn.setRequestMethod(method);
            conn.setConnectTimeout(1800);
            conn.setReadTimeout(3800);
            conn.setUseCaches(false);
            conn.setRequestProperty("Authorization","Bearer "+token);
            conn.setRequestProperty("Accept","application/json");
            if("POST".equals(method)){
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type","application/json; charset=utf-8");
                try(OutputStream out=conn.getOutputStream()){
                    out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                }
            }
            int status=conn.getResponseCode();
            InputStream input=status>=400?conn.getErrorStream():conn.getInputStream();
            if(input==null)throw new IllegalStateException("HTTP "+status);
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            try(InputStream in=input){
                byte[] bytes=new byte[4096];int n;
                while((n=in.read(bytes))!=-1){
                    output.write(bytes,0,n);
                    if(output.size()>150000)throw new IllegalStateException("A szerverválasz túl nagy.");
                }
            }
            JSONObject json=new JSONObject(output.toString("UTF-8"));
            if(status<200 || status>=300)
                throw new IllegalStateException("HTTP "+status+": "+json.optString("error","A kérés sikertelen"));
            return json;
        }finally{conn.disconnect();}
    }
    private static JSONObject errorObject(String msg){
        JSONObject j=new JSONObject();try{j.put("error",msg);}catch(Exception ignored){}return j;
    }
    private void error(String action,Exception e){
        String message=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        if(message.contains("Connection refused")||message.contains("Failed to connect"))
            message="A Termux szerver nem érhető el: 127.0.0.1:8765";
        emit(action,false,errorObject(message));
    }
    private void emit(String action,boolean ok,JSONObject response){
        try{
            JSONObject event=new JSONObject().put("action",action).put("ok",ok).put("data",response);
            final String script="if(window.JarvisTermuxUI && window.JarvisTermuxUI.receive)window.JarvisTermuxUI.receive("+JSONObject.quote(event.toString())+");";
            activity.runOnUiThread(new Runnable(){@Override public void run(){
                try{web.evaluateJavascript(script,null);}catch(Exception ignored){}
            }});
        }catch(Exception ignored){}
    }
}