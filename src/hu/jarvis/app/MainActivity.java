package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.widget.*;
import hu.jarvis.local.JarvisHome;
import hu.jarvis.local.ModelInstaller;
import hu.jarvis.local.OfflineBrain;
import hu.jarvis.local.ConversationMemory;
import hu.jarvis.local.HungarianVoice;
import hu.jarvis.local.OfflineSpeech;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
public final class MainActivity extends Activity {
 private TextView status;
 private HungarianVoice voice;
 private OfflineSpeech speech=new OfflineSpeech();
 private Button askButton;
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  voice=new HungarianVoice(this);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
  TextView title=new TextView(this);title.setText("JARVIS 4.0 – HELYI AI");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
  TextView subtitle=new TextView(this);subtitle.setText("I\u0027M ONLINE, SIR  •  LOCAL AI");subtitle.setTextColor(0xff39c9df);subtitle.setTextSize(12);root.addView(subtitle);
  TextView reactor=new TextView(this);reactor.setText("◎\n◉ J A R V I S ◉\n◎");reactor.setTextColor(0xff00e5ff);reactor.setTextSize(29);reactor.setGravity(android.view.Gravity.CENTER);reactor.setPadding(0,28,0,28);root.addView(reactor);
  TextView system=new TextView(this);system.setTextColor(0xff5ee5ef);system.setText("SYSTEM STATUS  •  OFFLINE BRAIN  •  AUTOPILOT");system.setGravity(android.view.Gravity.CENTER);root.addView(system);
  TextView homeInfo=new TextView(this);homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);
  try{
   File home=JarvisHome.initialize(this);
   File model=new File(new File(home,"models"),"model.gguf");
   homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB\nModell: "+(model.isFile()?model.length()/(1024*1024)+" MB GGUF":"nincs telepítve"));
  }catch(Exception e){homeInfo.setText("Tárhelyhiba: "+e.getMessage());}
  Button install=new Button(this);install.setText("HELYI GGUF MODELL KIVÁLASZTÁSA");root.addView(install);
  install.setOnClickListener(v->ModelInstaller.pick(this));
  EditText question=new EditText(this);question.setSingleLine(false);question.setHint("Írd ide a kérdésed...");question.setTextColor(Color.WHITE);question.setHintTextColor(0xffaaaaaa);root.addView(question);
  Button mic=new Button(this);mic.setText("MAGYAR OFFLINE MIKROFON");root.addView(mic);
  mic.setOnClickListener(v->{
   if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},500);status.setText("Engedélyezd a mikrofont, majd nyomd meg újra.");return;}
   status.setText("Offline beszédfelismerés...");
   speech.listen(this,new OfflineSpeech.Callback(){public void result(String s){question.setText(s);status.setText("Felismert parancs: "+s);askButton.performClick();}public void error(String s){status.setText(s);}});
  });
  Button ask=new Button(this);askButton=ask;ask.setText("KÉRDEZÉS HELYBEN");root.addView(ask);
  status=new TextView(this);status.setTextColor(Color.WHITE);
  status.setText("Helyi AI. Modell szükséges. A hangvezérlés még nincs kész.\nKorábbi beszélgetés:\n"+ConversationMemory.recent(this));
  root.addView(status);
  LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
  String[] tabs={"DASH","FILES","CHAT","QUICK","CONTROL","SETTINGS"};
  for(String tab:tabs){Button item=new Button(this);item.setText(tab);item.setTextSize(9);item.setTextColor(0xff00e5ff);nav.addView(item,new LinearLayout.LayoutParams(0,56,1));item.setOnClickListener(v->{if(tab.equals("CHAT"))question.requestFocus();else if(tab.equals("FILES"))ModelInstaller.pick(this);else if(tab.equals("CONTROL"))status.setText("Android-vezérlés: engedélyezd a JARVIS kisegítő lehetőséget a Beállításokban.");else if(tab.equals("QUICK"))status.setText("Gyorsparancs: nyisd meg [alkalmazás]; kattints [felirat]; görgess le");else status.setText("JARVIS "+tab+" – helyi rendszer");});}
  root.addView(nav);
  Button speak=new Button(this);speak.setText("UTOLSÓ VÁLASZ FELOLVASÁSA (OFFLINE)");root.addView(speak);
  speak.setOnClickListener(v->{if(voice.isReady())voice.say(status.getText().toString());else Toast.makeText(this,voice.status(),Toast.LENGTH_LONG).show();});
  ask.setOnClickListener(v->{
   String q=question.getText().toString().trim();if(q.isEmpty())return;
   if(ControlService.executeLocalCommand(q)){status.setText("Android-parancs végrehajtva: "+q);return;}
   File model=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
   if(!model.isFile()){status.setText("Előbb telepíts egy GGUF modellt.");return;}
   if(!OfflineBrain.available()){status.setText("Offline motor nem elérhető: "+OfflineBrain.failure());return;}
   status.setText("JARVIS helyben gondolkodik...");ask.setEnabled(false);
   new Thread(()->{String reply=OfflineBrain.reply(model.getAbsolutePath(),ConversationMemory.recent(this)+"\nFelhasználó: "+q);ConversationMemory.append(this,q,reply);runOnUiThread(()->{status.setText(reply);ask.setEnabled(true);if(voice.isReady())voice.say(reply);});}).start();
  });
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 @Override protected void onDestroy(){speech.close();if(voice!=null)voice.close();super.onDestroy();}
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==ModelInstaller.PICK_MODEL&&resultCode==RESULT_OK&&data!=null){
   status.setText("Modell másolása a JARVIS tárhelyére...");
   ModelInstaller.install(this,data.getData(),s->status.setText(s));
  }
 }
}
