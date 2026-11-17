package hu.jarvis.hud;

import android.app.Service;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.text.InputFilter;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Explicit opt-in text composer shown above other apps; inserts text, never taps Send. */
public final class OverlayWriter {
    private final Service service;
    private final Handler main=new Handler(Looper.getMainLooper());
    private WindowManager manager;
    private View composer;
    private EditText field;
    private String targetPackage="";

    OverlayWriter(Service service){this.service=service;}
    private int dp(float value){return (int)(value*service.getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable background(int fill,int border,int radius) {
        GradientDrawable draw=new GradientDrawable();
        draw.setColor(fill);draw.setCornerRadius(dp(radius));draw.setStroke(dp(2),border);
        return draw;
    }
    private TextView label(String text,int size){
        TextView v=new TextView(service);v.setText(text);v.setTextSize(size);
        v.setTextColor(0xffd6f5ff);v.setPadding(dp(5),dp(6),dp(5),dp(6));return v;
    }
    void show(){
        if(composer!=null)return;
        SharedPreferences pref=service.getSharedPreferences("jarvis_termux",Context.MODE_PRIVATE);
        final String token=pref.getString("token","");
        if(token.length()<24){
            Toast.makeText(service,"Előbb párosítsd a Termux motort.",Toast.LENGTH_LONG).show();return;
        }
        targetPackage=TermuxAccessibilityService.foregroundPackage();
        if(targetPackage==null||targetPackage.isEmpty()||targetPackage.equals(service.getPackageName())){
            Toast.makeText(service,"Nyisd meg a célalkalmazást és koppints a szövegmezőbe. Utána használd a JARVIS lebegő ikont.",Toast.LENGTH_LONG).show();
            return;
        }
        manager=(WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
        final LinearLayout layout=new LinearLayout(service);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(13),dp(12),dp(13),dp(12));
        layout.setBackground(background(0xff061b31,0xff31d7ff,13));
        TextView title=label("◈ JARVIS · ÍRÁS MÁSIK ALKALMAZÁSBA",13);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);layout.addView(title);
        layout.addView(label("Csak szöveget ír a korábban kijelölt mezőbe. Nem küld üzenetet és nem nyomja meg a Küldés gombot.",11));
        field=new EditText(service);
        field.setTextColor(Color.WHITE);field.setHintTextColor(0xff8cbdce);
        field.setTextSize(16);field.setHint("Ide írd a szöveget...");
        field.setSingleLine(false);field.setMinLines(2);field.setMaxLines(5);
        field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(200)});
        field.setBackground(background(0xff0b2a43,0xff1189b6,7));
        field.setPadding(dp(10),dp(6),dp(10),dp(6));
        layout.addView(field,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout buttons=new LinearLayout(service);buttons.setOrientation(LinearLayout.HORIZONTAL);buttons.setPadding(0,dp(8),0,0);
        Button cancel=new Button(service);cancel.setText("MÉGSE");cancel.setTextColor(0xffe5faff);
        cancel.setAllCaps(false);cancel.setBackground(background(0xff203349,0xff397ca0,6));
        LinearLayout.LayoutParams a=new LinearLayout.LayoutParams(0,dp(48),1);a.rightMargin=dp(6);buttons.addView(cancel,a);
        Button insert=new Button(service);insert.setText("BEÍRÁS");insert.setTextColor(Color.WHITE);
        insert.setAllCaps(false);insert.setBackground(background(0xff0c6087,0xff2adfff,6));
        buttons.addView(insert,new LinearLayout.LayoutParams(0,dp(48),1));layout.addView(buttons);
        int screenW=service.getResources().getDisplayMetrics().widthPixels;
        WindowManager.LayoutParams params=new WindowManager.LayoutParams(
          Math.min(screenW-dp(20),dp(370)),WindowManager.LayoutParams.WRAP_CONTENT,
          WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
          WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          PixelFormat.TRANSLUCENT);
        params.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        params.y=dp(70);params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        composer=layout;
        try{manager.addView(composer,params);}
        catch(Exception e){composer=null;field=null;Toast.makeText(service,"JARVIS íróablak nem nyitható meg.",Toast.LENGTH_SHORT).show();return;}
        cancel.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){hide();}});
        insert.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){
            String text=field==null?"":field.getText().toString();
            if(text.trim().isEmpty()){Toast.makeText(service,"Először írj be szöveget.",Toast.LENGTH_SHORT).show();return;}
            final String toWrite=text, expected=targetPackage;
            hide();
            // Give the target app time to regain focus after removing the overlay.
            main.postDelayed(new Runnable(){@Override public void run(){queue(toWrite,expected);}},900);
        }});
        field.requestFocus();
        main.postDelayed(new Runnable(){@Override public void run(){
            if(field!=null && composer!=null){
                InputMethodManager imm=(InputMethodManager)service.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null)imm.showSoftInput(field,InputMethodManager.SHOW_IMPLICIT);
            }
        }},250);
    }
    void hide(){
        if(field!=null){
            try{InputMethodManager imm=(InputMethodManager)service.getSystemService(Context.INPUT_METHOD_SERVICE);
                if(imm!=null)imm.hideSoftInputFromWindow(field.getWindowToken(),0);
            }catch(Exception ignored){}
        }
        if(composer!=null&&manager!=null){
            try{manager.removeView(composer);}catch(Exception ignored){}
        }
        composer=null;field=null;
    }
    void destroy(){hide();}
    private void queue(final String text,final String expected){
        final String token=service.getSharedPreferences("jarvis_termux",Context.MODE_PRIVATE).getString("token","");
        if(token.length()<24)return;
        new Thread(new Runnable(){@Override public void run(){
            boolean queued=false;
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL("http://127.0.0.1:8765/api/tasks").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(2000);connection.setReadTimeout(3500);
                connection.setRequestProperty("Authorization","Bearer "+token);
                connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                connection.setDoOutput(true);
                JSONObject payload=new JSONObject().put("command","type_text")
                    .put("args",new JSONObject().put("text",text).put("package",expected));
                JSONObject body=new JSONObject().put("type","android").put("payload",payload);
                try(OutputStream out=connection.getOutputStream()){
                    out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status=connection.getResponseCode();
                queued=status>=200 && status<300;
            }catch(Exception ignored){}finally{if(connection!=null)connection.disconnect();}
            final boolean ok=queued;
            main.post(new Runnable(){@Override public void run(){
                Toast.makeText(service,ok?"JARVIS: beírás feladatsorba küldve (nem elküldött üzenet).":"A Termux szerver nem érhető el.",Toast.LENGTH_LONG).show();
            }});
        }},"JarvisOverlayWriter").start();
    }
}