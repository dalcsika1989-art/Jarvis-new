package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.widget.*;
import hu.jarvis.autopilot.AutopilotEngine;
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
    TextView title=new TextView(this);title.setText("JARVIS 2.2 – KERESÉS ÉS AUTOPILOT");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
    task=new EditText(this);task.setTextColor(Color.WHITE);task.setHintTextColor(0xffaaaaaa);task.setHint("Feladat leírása");task.setMinLines(2);root.addView(task);
    Button scooters=new Button(this);scooters.setText("Kleinanzeigen: elektromos rollerek");root.addView(scooters);
    Button free=new Button(this);free.setText("Kleinanzeigen: zu verschenken");root.addView(free);
    Button start=new Button(this);start.setText("Háttérfeladat állapotfigyelés");root.addView(start);
    Button stop=new Button(this);stop.setText("Leállítás");root.addView(stop);
    status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("A keresőgombok megnyitják a valódi Kleinanzeigen keresést. Az automatikus szívezés és az ismétlődő háttérkeresés még nincs bekötve.");root.addView(status);
    scooters.setOnClickListener(v->openSearch("elektro scooter"));
    free.setOnClickListener(v->openSearch("zu verschenken"));
    start.setOnClickListener(v->{String t=task.getText().toString().trim();if(t.isEmpty()){status.setText("Adj meg feladatot.");return;}try{AutopilotEngine.start(this,t,60);status.setText("Állapotfigyelő szolgáltatás indítva. A feladat NINCS automatikusan végrehajtva.");}catch(Exception e){status.setText("Indítási hiba: "+e.getMessage());}});
    stop.setOnClickListener(v->{AutopilotEngine.stop(this);status.setText("Leállítás kérve.");});
    ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
  }
}
