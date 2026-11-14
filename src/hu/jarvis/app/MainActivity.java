package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.os.Build;
import android.graphics.Color;
import android.view.View;
import android.widget.*;
import hu.jarvis.autopilot.AutopilotEngine;
public final class MainActivity extends Activity {
  EditText task; TextView status;
  @Override public void onCreate(Bundle b){super.onCreate(b);
    LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
    TextView title=new TextView(this);title.setText("JARVIS 2.1 – AUTOPILOT");title.setTextColor(0xff83efff);title.setTextSize(23);root.addView(title);
    task=new EditText(this);task.setTextColor(Color.WHITE);task.setHintTextColor(0xffaaaaaa);task.setHint("Feladat leírása");task.setMinLines(2);root.addView(task);
    Button start=new Button(this);start.setText("Háttérfeladat indítása");root.addView(start);
    Button stop=new Button(this);stop.setText("Leállítás");root.addView(stop);
    status=new TextView(this);status.setTextColor(Color.WHITE);status.setText("A háttérmotor jelenleg állapotjelentést készít; nincs hozzákapcsolt webes kereső vagy alkalmazásvezérlő.");root.addView(status);
    start.setOnClickListener(v->{String t=task.getText().toString().trim();if(t.isEmpty()){status.setText("Adj meg feladatot.");return;}try{AutopilotEngine.start(this,t,60);status.setText("Háttérszolgáltatás elindítva. A tényleges keresőmotor még nincs csatlakoztatva.");}catch(Exception e){status.setText("Indítási hiba: "+e.getMessage());}});
    stop.setOnClickListener(v->{AutopilotEngine.stop(this);status.setText("Leállítás kérve.");});
    setContentView(root);
  }
}