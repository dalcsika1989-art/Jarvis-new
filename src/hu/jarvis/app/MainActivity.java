package hu.jarvis.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.widget.*;
import hu.jarvis.local.JarvisHome;
import hu.jarvis.local.ModelInstaller;
import hu.jarvis.local.ModelDownloader;
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
 private TextView modelInfo;
 private HungarianVoice voice;
 private android.speech.tts.TextToSpeech fallbackVoice;
 private OfflineSpeech speech=new OfflineSpeech();
 private Button askButton;
 private final android.os.Handler commandHandler=new android.os.Handler(android.os.Looper.getMainLooper());
 private Runnable commandWatcher;
 private void watchCommand(){
  if(commandWatcher!=null)commandHandler.removeCallbacks(commandWatcher);
  commandWatcher=new Runnable(){int iterations=0;public void run(){
   String s=ControlService.currentStatus();
   if(status!=null)status.setText(s);
   if(s.startsWith("Minden ")||s.startsWith("Sikertelen:")||s.startsWith("Feladat megszakítva")||s.startsWith("JARVIS vezérlő leállt"))return;
   if(++iterations<400)commandHandler.postDelayed(this,500);
  }};
  commandHandler.post(commandWatcher);
 }
 @Override public void onCreate(Bundle b){
  super.onCreate(b);
  voice=new HungarianVoice(this);
  fallbackVoice=new android.speech.tts.TextToSpeech(this, r->{if(r==android.speech.tts.TextToSpeech.SUCCESS && fallbackVoice!=null){fallbackVoice.setLanguage(new java.util.Locale("hu","HU"));fallbackVoice.setSpeechRate(1.07f);fallbackVoice.setPitch(.86f);}});
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
  TextView title=new TextView(this);title.setText("JARVIS ONE – ARC CONTROL");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
  TextView subtitle=new TextView(this);subtitle.setText("I\u0027M ONLINE, SIR  •  GEMINI + DEVICE CONTROL");subtitle.setTextColor(0xff39c9df);subtitle.setTextSize(12);root.addView(subtitle);
  NeoMonitorView monitor=new NeoMonitorView(this);
  root.addView(monitor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*92)));
  ReactorView reactor=new ReactorView(this);
  root.addView(reactor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*310)));
  TextView system=new TextView(this);system.setTextColor(0xff5ee5ef);system.setText("SYSTEM STATUS  •  OFFLINE BRAIN  •  AUTOPILOT");system.setGravity(android.view.Gravity.CENTER);root.addView(system);
  TextView homeInfo=new TextView(this);modelInfo=homeInfo;homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);homeInfo.setVisibility(android.view.View.GONE);
  try{
   File home=JarvisHome.initialize(this);
   File model=new File(new File(home,"models"),"model.gguf");
   homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB\nModell: "+(model.isFile()?model.length()/(1024*1024)+" MB GGUF":"nincs telepítve"));
  }catch(Exception e){homeInfo.setText("Tárhelyhiba: "+e.getMessage());}
  Button check=new Button(this);check.setText("HELYI AI ELLENŐRZÉSE");root.addView(check);check.setVisibility(android.view.View.GONE);
  check.setOnClickListener(v->{
   File m=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
   status.setText("GGUF: "+(m.isFile()?(m.length()/1048576)+" MB":"HIÁNYZIK")
      +"\nNatív motor: "+(OfflineBrain.available()?"BETÖLTHETŐ":"HIBA: "+OfflineBrain.failure())
      +"\nMagyar offline hang: "+voice.status());
  });
  Button install=new Button(this);install.setText("HELYI GGUF MODELL KIVÁLASZTÁSA");root.addView(install);install.setVisibility(android.view.View.GONE);
  install.setOnClickListener(v->ModelInstaller.pick(this));
  Button obtainModel=new Button(this);obtainModel.setText("OFFLINE AI-MODELL LETÖLTÉSE (KB. 1 GB)");root.addView(obtainModel);obtainModel.setVisibility(android.view.View.GONE);
  obtainModel.setOnClickListener(v->new android.app.AlertDialog.Builder(this)
   .setTitle("Helyi AI telepítése")
   .setMessage("A Qwen2.5 1,5B modell körülbelül 1 GB adatforgalmat használ. Javasolt Wi-Fi-n letölteni. Utána az AI internet nélkül válaszol. Megkezdjem?")
   .setNegativeButton("Mégse",null)
   .setPositiveButton("Letöltés",(dialog,which)->{
    obtainModel.setEnabled(false);
    status.setText("Helyi modell letöltése elindult...");
    ModelDownloader.download(this,new ModelDownloader.Listener(){
     public void progress(String message){status.setText(message);}
     public void finished(String message){
      status.setText(message);obtainModel.setEnabled(true);
      File m=new File(new File(JarvisHome.initialize(MainActivity.this),"models"),"model.gguf");
      modelInfo.setText("GGUF modell: "+(m.isFile()?(m.length()/1048576)+" MB":"nincs telepítve"));
     }
    });
   }).show());

  EditText question=new EditText(this);question.setSingleLine(false);question.setHint("Írd ide a kérdésed...");question.setTextColor(Color.WHITE);question.setHintTextColor(0xffaaaaaa);root.addView(question);

  TextView apiTitle=new TextView(this);apiTitle.setText("GEMINI API-KULCS (csak helyben mentve)");apiTitle.setTextColor(0xff83efff);root.addView(apiTitle);
  EditText geminiKey=new EditText(this);geminiKey.setTextColor(Color.WHITE);geminiKey.setSingleLine(true);
  geminiKey.setHint("Gemini API-kulcs");geminiKey.setHintTextColor(0xff93acb7);
  geminiKey.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
  geminiKey.setText(getPreferences(MODE_PRIVATE).getString("gemini_api_key",""));root.addView(geminiKey);
  Button saveApi=new Button(this);saveApi.setText("GEMINI API-KULCS MENTÉSE");root.addView(saveApi);
  saveApi.setOnClickListener(v->{getPreferences(MODE_PRIVATE).edit().putString("gemini_api_key",geminiKey.getText().toString().trim()).apply();status.setText("API-kulcs elmentve a telefonon.");});
  Button enableControl=new Button(this);enableControl.setText("TELEFONVEZÉRLÉS ENGEDÉLYEZÉSE");root.addView(enableControl);
  enableControl.setOnClickListener(v->startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)));

  Button mic=new Button(this);mic.setText("MAGYAR MIKROFON");root.addView(mic);
  mic.setOnClickListener(v->{
   if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},500);status.setText("Engedélyezd a mikrofont, majd nyomd meg újra.");return;}
   status.setText("Offline beszédfelismerés...");reactor.setActive(true);
   speech.listen(this,new OfflineSpeech.Callback(){public void result(String s){reactor.setActive(false);question.setText(s);status.setText("Felismert parancs: "+s);askButton.performClick();}public void error(String s){reactor.setActive(false);status.setText(s);}});
  });
  Button ask=new Button(this);askButton=ask;ask.setText("JARVIS – VÁLASZ / PARANCS");root.addView(ask);
  status=new TextView(this);status.setTextColor(Color.WHITE);
  status.setText("GEMINI kapcsolat: add meg az API-kulcsot. Android telefonvezérléshez engedélyezd a kisegítő JARVIS szolgáltatást.\nKorábbi beszélgetés:\n"+ConversationMemory.recent(this));
  root.addView(status);
  LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);
  String[] tabs={"DASH","FILES","CHAT","QUICK","CONTROL","SETTINGS"};
  for(String tab:tabs){Button item=new Button(this);item.setText(tab);item.setTextSize(9);item.setTextColor(0xff00e5ff);nav.addView(item,new LinearLayout.LayoutParams(0,56,1));item.setOnClickListener(v->{if(tab.equals("CHAT"))question.requestFocus();else if(tab.equals("FILES"))ModelInstaller.pick(this);else if(tab.equals("CONTROL"))status.setText("Android-vezérlés: engedélyezd a JARVIS kisegítő lehetőséget a Beállításokban.");else if(tab.equals("QUICK"))status.setText("Gyorsparancs: nyisd meg [alkalmazás]; kattints [felirat]; görgess le");else status.setText("JARVIS "+tab+" – helyi rendszer");});}
  root.addView(nav);
  Button cancel=new Button(this);cancel.setText("ANDROID FELADAT LEÁLLÍTÁSA");root.addView(cancel);
  cancel.setOnClickListener(v->{if(ControlService.cancelCurrentTask())status.setText("Leállítás kérve...");else status.setText("A JARVIS hozzáférési szolgáltatás nincs bekapcsolva.");});
  Button speak=new Button(this);speak.setText("UTOLSÓ VÁLASZ FELOLVASÁSA (OFFLINE)");root.addView(speak);
  speak.setOnClickListener(v->{if(voice.isReady())voice.say(status.getText().toString());else Toast.makeText(this,voice.status(),Toast.LENGTH_LONG).show();});

  ask.setOnClickListener(v->{
   String q=question.getText().toString().trim();if(q.isEmpty())return;
   if(q.equalsIgnoreCase("állj")||q.equalsIgnoreCase("stop")){ControlService.cancelCurrentTask();status.setText("Feladat leállítása kérve.");return;}
   if(ControlService.executeLocalCommand(q)){watchCommand();status.setText("Parancs végrehajtásra továbbítva: "+q);return;}
   if(ControlService.looksLikeLocalCommand(q)){
    status.setText("A telefonvezérléshez engedélyezd a JARVIS kisegítő szolgáltatást a Beállításokban.");return;
   }
   String key=getPreferences(MODE_PRIVATE).getString("gemini_api_key","").trim();
   if(key.isEmpty()){status.setText("Gemini API-kulcs hiányzik. Add meg és mentsd el.");return;}
   reactor.setActive(true);ask.setEnabled(false);status.setText("Gemini: válasz feldolgozása...");
   String context=ConversationMemory.recentForPrompt(this);
   hu.jarvis.ai.GeminiAgent.run(this,key,context+"\nFelhasználó: "+q,answer->{
    reactor.setActive(false);ask.setEnabled(true);
    status.setText(answer);
    if(!answer.startsWith("Gemini-hiba:")){
      ConversationMemory.append(this,q,answer);
      if(voice!=null&&voice.isReady())voice.say(answer);
      else if(fallbackVoice!=null)fallbackVoice.speak(answer,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"jarvis-one");
    }
   });
  });
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 @Override protected void onDestroy(){if(commandWatcher!=null)commandHandler.removeCallbacks(commandWatcher);speech.close();if(voice!=null)voice.close();if(fallbackVoice!=null)fallbackVoice.shutdown();super.onDestroy();}
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
  super.onActivityResult(requestCode,resultCode,data);
  if(requestCode==ModelInstaller.PICK_MODEL&&resultCode==RESULT_OK&&data!=null){
   status.setText("Modell másolása a JARVIS tárhelyére...");
   ModelInstaller.install(this,data.getData(),s->{
    status.setText(s);
    File m=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
    modelInfo.setText("GGUF modell: "+(m.isFile()?(m.length()/1048576)+" MB":"nincs telepítve"));
   });
  }
 }
}
