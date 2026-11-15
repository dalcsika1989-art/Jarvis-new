package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.widget.*;
import hu.jarvis.local.JarvisHome;
import hu.jarvis.local.ModelInstaller;
import java.io.File;
public final class MainActivity extends Activity {
 private TextView status;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
  TextView title=new TextView(this);title.setText("JARVIS 4.0 – HELYI AI");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
  TextView homeInfo=new TextView(this);homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);
  try{
   File home=JarvisHome.initialize(this);
   File model=new File(new File(home,"models"),"model.gguf");
   homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB\nModell: "+(model.isFile()?model.length()/(1024*1024)+" MB GGUF":"nincs telepítve"));
  }catch(Exception e){homeInfo.setText("Tárhelyhiba: "+e.getMessage());}
  Button install=new Button(this);install.setText("HELYI GGUF MODELL KIVÁLASZTÁSA");root.addView(install);
  install.setOnClickListener(v->ModelInstaller.pick(this));
  status=new TextView(this);status.setTextColor(Color.WHITE);
  status.setText("Offline előkészítés. A modellfuttató és a hangvezérlés még nincs csatlakoztatva. Nem használ Gemini API-t.");
  root.addView(status);
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==ModelInstaller.PICK_MODEL&&resultCode==RESULT_OK&&data!=null){
   status.setText("Modell másolása a JARVIS tárhelyére...");
   ModelInstaller.install(this,data.getData(),s->status.setText(s));
  }
 }
}
