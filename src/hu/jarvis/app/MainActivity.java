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
 private android.media.MediaPlayer mediaPlayer;
 private void playSelectedAudio(android.net.Uri uri){
  try{if(mediaPlayer!=null){mediaPlayer.release();mediaPlayer=null;}
   mediaPlayer=new android.media.MediaPlayer();mediaPlayer.setDataSource(this,uri);mediaPlayer.setOnPreparedListener(p->{p.start();status.setText("Médialejátszás elindult.");});
   mediaPlayer.setOnCompletionListener(p->{status.setText("Médialejátszás befejeződött.");p.release();mediaPlayer=null;});mediaPlayer.prepareAsync();
  }catch(Exception e){status.setText("Médiahiba: "+e.getMessage());}
 }

 private android.view.View dashboard;
 private void showChat(EditText question,TextView status,Button ask){
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(16,25,16,16);page.setBackgroundColor(0xff050b17);
  TextView head=new TextView(this);head.setText("‹    AI CHAT   •   JARVIS NEO");head.setTextSize(22);head.setTextColor(0xff20d8ff);head.setPadding(10,15,10,25);page.addView(head);head.setOnClickListener(v->showDashboard());
  TextView history=new TextView(this);history.setTextColor(0xffb5f7ff);history.setTextSize(15);history.setText("KORÁBBI BESZÉLGETÉS\\n"+ConversationMemory.recent(this));ScrollView sc=new ScrollView(this);sc.addView(history);page.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
  EditText input=new EditText(this);input.setTextColor(-1);input.setHintTextColor(0xff48b7ca);input.setHint("ASK JARVIS...");page.addView(input);
  Button send=new Button(this);send.setText("SEND  ◈");page.addView(send);
  TextView reply=new TextView(this);reply.setTextColor(-1);reply.setText("A válasz a főképernyőn is megjelenik.");page.addView(reply);
  send.setOnClickListener(v->{String q=input.getText().toString().trim();if(q.isEmpty())return;question.setText(q);ask.performClick();reply.setText("Kérés továbbítva. Vissza a főképernyőre a válasz megtekintéséhez.");});
  setContentView(page);
 }

 private void showDashboard(){if(dashboard!=null)setContentView(dashboard);}
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  voice=new HungarianVoice(this);
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
  TextView title=new TextView(this);title.setText("JARVIS NEO  •  COMMAND CENTER");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
  TextView subtitle=new TextView(this);subtitle.setText("I\u0027M ONLINE, SIR  •  LOCAL AI");subtitle.setTextColor(0xff39c9df);subtitle.setTextSize(12);root.addView(subtitle);
  NeoMonitorView monitor=new NeoMonitorView(this);
  root.addView(monitor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*105)));
  ReactorView reactor=new ReactorView(this);
  root.addView(reactor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*340)));
  TextView system=new TextView(this);system.setTextColor(0xff5ee5ef);system.setText("SYSTEM STATUS  •  OFFLINE BRAIN  •  AUTOPILOT");system.setGravity(android.view.Gravity.CENTER);root.addView(system);
  TextView homeInfo=new TextView(this);homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);
  try{
   File home=JarvisHome.initialize(this);
   File model=new File(new File(home,"models"),"model.gguf");
   homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB\nModell: "+(model.isFile()?model.length()/(1024*1024)+" MB GGUF":"nincs telepítve"));
  }catch(Exception e){homeInfo.setText("Tárhelyhiba: "+e.getMessage());}
  Button refreshModel=new Button(this);refreshModel.setText("AGY ÉS MOTOR ELLENŐRZÉSE");root.addView(refreshModel);
  refreshModel.setOnClickListener(v->{File m=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");status.setText("GGUF: "+(m.isFile()?(m.length()/1048576)+" MB":"NINCS TELEPÍTVE")+"\nNatív motor: "+(OfflineBrain.available()?"BETÖLTVE":"HIBA: "+OfflineBrain.failure()));});
  Button install=new Button(this);install.setText("HELYI GGUF MODELL KIVÁLASZTÁSA");root.addView(install);
  install.setOnClickListener(v->ModelInstaller.pick(this));
  EditText question=new EditText(this);question.setSingleLine(false);question.setHint("Írd ide a kérdésed...");question.setTextColor(Color.WHITE);question.setHintTextColor(0xffaaaaaa);root.addView(question);
  Button mic=new Button(this);mic.setText("MAGYAR OFFLINE MIKROFON");root.addView(mic);
  mic.setOnClickListener(v->{
   if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},500);status.setText("Engedélyezd a mikrofont, majd nyomd meg újra.");return;}
   status.setText("Offline beszédfelismerés...");reactor.setActive(true);
   speech.listen(this,new OfflineSpeech.Callback(){public void result(String s){question.setText(s);status.setText("Felismert parancs: "+s);askButton.performClick();}public void error(String s){status.setText(s);}});
  });
  Button ask=new Button(this);askButton=ask;ask.setText("KÉRDEZÉS HELYBEN");root.addView(ask);
  status=new TextView(this);status.setTextColor(Color.WHITE);
  status.setText("Helyi AI. Modell szükséges. A hangvezérlés készül.\nKorábbi beszélgetés:\n"+ConversationMemory.recent(this));
  root.addView(status);
  LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
  String[] tabs={"DASH","FILES","CHAT","QUICK","CONTROL","MEDIA","SETTINGS"};
  for(String tab:tabs){Button item=new Button(this);item.setText(tab);item.setTextSize(9);item.setTextColor(0xff00e5ff);nav.addView(item,new LinearLayout.LayoutParams(0,56,1));item.setOnClickListener(v->{if(tab.equals("CHAT"))showChat(question,status,askButton)else if(tab.equals("DASH"))showDashboard();else NeoPanels.show(this,tab,()->showDashboard());});}
  root.addView(nav);
  Button speak=new Button(this);speak.setText("UTOLSÓ VÁLASZ FELOLVASÁSA (OFFLINE)");root.addView(speak);
  speak.setOnClickListener(v->{if(voice.isReady())voice.say(status.getText().toString());else Toast.makeText(this,voice.status(),Toast.LENGTH_LONG).show();});
  ask.setOnClickListener(v->{
   String q=question.getText().toString().trim();if(q.isEmpty())return;
   if(ControlService.executeLocalCommand(q)){status.setText("Android-parancs továbbítva: "+q+"\nA tényleges végrehajtást az Android vezérlőszolgáltatás végzi; az eredmény még nem igazolt.");return;}
   File model=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
   if(!model.isFile()){status.setText("Előbb telepíts egy GGUF modellt.");return;}
   if(!OfflineBrain.available()){status.setText("Offline motor nem elérhető: "+OfflineBrain.failure());return;}
   status.setText("JARVIS helyben gondolkodik...");ask.setEnabled(false);
   new Thread(()->{String reply=OfflineBrain.reply(model.getAbsolutePath(),ConversationMemory.recent(this)+"\nFelhasználó: "+q);ConversationMemory.append(this,q,reply);runOnUiThread(()->{status.setText(reply);ask.setEnabled(true);if(voice.isReady())voice.say(reply);});}).start();
  });
  ScrollView scroll=new ScrollView(this);scroll.addView(root);dashboard=scroll;setContentView(scroll);
 }
 @Override protected void onDestroy(){if(mediaPlayer!=null){mediaPlayer.release();mediaPlayer=null;}speech.close();if(voice!=null)voice.close();super.onDestroy();}
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==407&&resultCode==RESULT_OK&&data!=null){playSelectedAudio(data.getData());return;}
  if(requestCode==ModelInstaller.PICK_MODEL&&resultCode==RESULT_OK&&data!=null){
   status.setText("Modell másolása a JARVIS tárhelyére...");
   ModelInstaller.install(this,data.getData(),s->status.setText(s));
  }
 }
}
