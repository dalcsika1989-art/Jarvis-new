package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import hu.jarvis.autopilot.AutopilotEngine;
import hu.jarvis.ai.GeminiAgent;
import hu.jarvis.local.JarvisHome;
import java.io.File;
public final class MainActivity extends Activity {
  EditText task; TextView status;
  private void openSearch(String query){
    try {
      String url="https://www.kleinanzeigen.de/s-suchanfrage.html?keywords="+Uri.encode(query);
      Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));
      startActivity(i);
      status.setText("Keresés megnyitva: "+query+". A kedvencek mentése jelenleg kézi művelet; automatikus szívezés még nincs.");
    } catch(Exception e){status.setText("Keresés nem nyitható meg: "+e.getMessage());}
  }
  @Override public void onCreate(Bundle b){super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
    TextView title=new TextView(this);title.setText("JARVIS 3.0 – GEMINI AI");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
    EditText api=new EditText(this);api.setSingleLine(true);api.setHint("Gemini API-kulcs (Google AI Studio)");api.setTextColor(Color.WHITE);api.setHintTextColor(0xffaaaaaa);api.setInputType(129);api.setText(getPreferences(0).getString("gemini_key",""));root.addView(api);
    TextView homeInfo=new TextView(this);homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);
    try{File home=JarvisHome.initialize(this);homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB");}catch(Exception ex){homeInfo.setText("Tárhelyhiba: "+ex.getMessage());}
    task=new EditText(this);task.setTextColor(Color.WHITE);task.setHintTextColor(0xffaaaaaa);task.setHint("Feladat leírása");task.setMinLines(2);root.addView(task);
    Button ask=new Button(this);ask.setText("AI parancs értelmezése");root.addView(ask);
    ask.setOnClickListener(v->{String key=api.getText().toString().trim(),prompt=task.getText().toString().trim();if(key.isEmpty()){status.setText("A Gemini API-kulcs hiányzik. A NEMESIS beállítása nem kerül automatikusan átvételre.");return;}if(prompt.isEmpty()){status.setText("Írj egy utasítást.");return;}getPreferences(0).edit().putString("gemini_key",key).apply();status.setText("Gemini feldolgozás...");GeminiAgent.run(this,key,prompt,message->status.setText(message));});
    Button start=new Button(this);start.setText("Háttérfeladat állapotfigyelés");root.addView(start);
    Button stop=new Button(this);stop.setText("Leállítás");root.addView(stop);
    status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("Az AI szabad magyar utasításokat értelmez. A tényleges telefonműveletek egyelőre korlátozottak.");root.addView(status);
    start.setOnClickListener(v->{String t=task.getText().toString().trim();if(t.isEmpty()){status.setText("Adj meg feladatot.");return;}try{AutopilotEngine.start(this,t,60);status.setText("Háttérfigyelés elindítva. A keresés hálózati eredményét az értesítés mutatja; automatikus szívezés még nincs.");}catch(Exception e){status.setText("Indítási hiba: "+e.getMessage());}});
    stop.setOnClickListener(v->{AutopilotEngine.stop(this);status.setText("Leállítás kérve.");});
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  }
}
