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
import hu.jarvis.ai.MultiModelAgent;
import hu.jarvis.ai.SecureApiKeys;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import java.io.File;
public final class MainActivity extends Activity {
 private TextView status;
 private TextView modelInfo;
 private HungarianVoice voice;
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
  LinearLayout root=new LinearLayout(this);
  root.setOrientation(1);root.setPadding(28,36,28,20);root.setBackgroundColor(Color.rgb(8,16,25));
  TextView title=new TextView(this);title.setText("JARVIS 4.0 – HELYI AI");title.setTextColor(0xff83efff);title.setTextSize(22);root.addView(title);
  TextView subtitle=new TextView(this);subtitle.setText("I\u0027M ONLINE, SIR  •  LOCAL AI");subtitle.setTextColor(0xff39c9df);subtitle.setTextSize(12);root.addView(subtitle);
  NeoMonitorView monitor=new NeoMonitorView(this);
  root.addView(monitor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*92)));
  ReactorView reactor=new ReactorView(this);
  root.addView(reactor,new LinearLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().density*310)));
  TextView system=new TextView(this);system.setTextColor(0xff5ee5ef);system.setText("SYSTEM STATUS  •  OFFLINE BRAIN  •  AUTOPILOT");system.setGravity(android.view.Gravity.CENTER);root.addView(system);
  TextView homeInfo=new TextView(this);modelInfo=homeInfo;homeInfo.setTextColor(0xff83efff);root.addView(homeInfo);
  try{
   File home=JarvisHome.initialize(this);
   File model=new File(new File(home,"models"),"model.gguf");
   homeInfo.setText("HELYI JARVIS-HÁZ: "+home.getAbsolutePath()+"\nSzabad tárhely: "+(JarvisHome.freeBytes(this)/(1024*1024))+" MB\nModell: "+(model.isFile()?model.length()/(1024*1024)+" MB GGUF":"nincs telepítve"));
  }catch(Exception e){homeInfo.setText("Tárhelyhiba: "+e.getMessage());}
  Button check=new Button(this);check.setText("HELYI AI ELLENŐRZÉSE");root.addView(check);
  check.setOnClickListener(v->{
   File m=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
   status.setText("GGUF: "+(m.isFile()?(m.length()/1048576)+" MB":"HIÁNYZIK")
      +"\nNatív motor: "+(OfflineBrain.available()?"BETÖLTHETŐ":"HIBA: "+OfflineBrain.failure())
      +"\nMagyar offline hang: "+voice.status());
  });
  Button install=new Button(this);install.setText("HELYI GGUF MODELL KIVÁLASZTÁSA");root.addView(install);
  install.setOnClickListener(v->ModelInstaller.pick(this));
  Button obtainModel=new Button(this);obtainModel.setText("OFFLINE AI-MODELL LETÖLTÉSE (KB. 1 GB)");root.addView(obtainModel);
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

  TextView aiHeading=new TextView(this);aiHeading.setText("AI MODELLVÁLASZTÓ  •  HELYI / FELHŐ");
  aiHeading.setTextColor(0xff83efff);aiHeading.setTextSize(18);root.addView(aiHeading);
  Spinner modelPicker=new Spinner(this);
  ArrayAdapter<String> modelAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,MultiModelAgent.LABELS);
  modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
  modelPicker.setAdapter(modelAdapter);root.addView(modelPicker);
  android.content.SharedPreferences aiPreferences=getSharedPreferences("jarvis_ai_model",MODE_PRIVATE);
  int savedModel=aiPreferences.getInt("model_index",0);
  modelPicker.setSelection(savedModel>=0&&savedModel<MultiModelAgent.LABELS.length?savedModel:0);
  TextView keyStatus=new TextView(this);keyStatus.setTextColor(0xff6ae5ec);root.addView(keyStatus);
  EditText apiKeyInput=new EditText(this);apiKeyInput.setSingleLine(true);
  apiKeyInput.setHint("API-kulcs – csak a kiválasztott szolgáltatóhoz");
  apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
  apiKeyInput.setTextColor(Color.WHITE);root.addView(apiKeyInput);
  Button saveKey=new Button(this);saveKey.setText("API-KULCS TITKOSÍTOTT MENTÉSE");root.addView(saveKey);
  Button deleteKey=new Button(this);deleteKey.setText("AZ AKTUÁLIS API-KULCS TÖRLÉSE");root.addView(deleteKey);
  TextView privacyNote=new TextView(this);privacyNote.setTextColor(0xff9ad6dd);
  privacyNote.setText("Offline AI: kulcs és internet nélkül. Felhős AI: a kérdés a kiválasztott szolgáltatóhoz kerül; a használat kvótába vagy pénzbe kerülhet. Nincs automatikus szolgáltatóváltás.");
  root.addView(privacyNote);
  Runnable refreshKeyState=()->{
   String provider=MultiModelAgent.provider(modelPicker.getSelectedItemPosition());
   boolean offline=MultiModelAgent.isLocal(modelPicker.getSelectedItemPosition());
   int visibility=offline?View.GONE:View.VISIBLE;
   apiKeyInput.setVisibility(visibility);saveKey.setVisibility(visibility);deleteKey.setVisibility(visibility);
   if(offline){keyStatus.setText("Helyi GGUF – nincs szükség API-kulcsra.");return;}
   try{keyStatus.setText(provider.toUpperCase()+" API-kulcs: "+(SecureApiKeys.load(this,provider).isEmpty()?"HIÁNYZIK":"MENTVE, TITKOSÍTVA"));}
   catch(Exception e){keyStatus.setText("API-kulcs titkosított tárhelye nem érhető el: "+e.getClass().getSimpleName());}
  };
  modelPicker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
   public void onItemSelected(AdapterView<?> parent,View view,int position,long id){
    aiPreferences.edit().putInt("model_index",position).apply();
    apiKeyInput.setText("");
    refreshKeyState.run();
    if(askButton!=null)askButton.setText(MultiModelAgent.isLocal(position)?"KÉRDEZÉS HELYBEN":"KÉRDEZÉS A KIVÁLASZTOTT AI-TÓL");
   }
   public void onNothingSelected(AdapterView<?> parent){}
  });
  saveKey.setOnClickListener(v->{
   String provider=MultiModelAgent.provider(modelPicker.getSelectedItemPosition());
   if("local".equals(provider))return;
   if(apiKeyInput.getText().toString().trim().isEmpty()){Toast.makeText(this,"Írd be az API-kulcsot.",Toast.LENGTH_SHORT).show();return;}
   try{
    SecureApiKeys.save(this,provider,apiKeyInput.getText().toString());
    apiKeyInput.setText("");refreshKeyState.run();
    Toast.makeText(this,"A "+provider+" kulcsa titkosítva elmentve.",Toast.LENGTH_SHORT).show();
   }catch(Exception e){Toast.makeText(this,"Mentési hiba: "+e.getClass().getSimpleName(),Toast.LENGTH_LONG).show();}
  });
  deleteKey.setOnClickListener(v->{
   String provider=MultiModelAgent.provider(modelPicker.getSelectedItemPosition());
   if("local".equals(provider))return;
   SecureApiKeys.clear(this,provider);apiKeyInput.setText("");refreshKeyState.run();
   Toast.makeText(this,"A "+provider+" kulcsa törölve.",Toast.LENGTH_SHORT).show();
  });
  refreshKeyState.run();

  EditText question=new EditText(this);question.setSingleLine(false);question.setHint("Írd ide a kérdésed...");question.setTextColor(Color.WHITE);question.setHintTextColor(0xffaaaaaa);root.addView(question);
  Button mic=new Button(this);mic.setText("MAGYAR OFFLINE MIKROFON");root.addView(mic);
  mic.setOnClickListener(v->{
   if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},500);status.setText("Engedélyezd a mikrofont, majd nyomd meg újra.");return;}
   status.setText("Offline beszédfelismerés...");reactor.setActive(true);
   speech.listen(this,new OfflineSpeech.Callback(){public void result(String s){reactor.setActive(false);question.setText(s);status.setText("Felismert parancs: "+s);askButton.performClick();}public void error(String s){reactor.setActive(false);status.setText(s);}});
  });
  Button ask=new Button(this);askButton=ask;ask.setText("KÉRDEZÉS HELYBEN");root.addView(ask);
  status=new TextView(this);status.setTextColor(Color.WHITE);
  status.setText("Helyi AI: a GGUF-modell és a telefon offline magyar beszédmotorja szükséges.\nKorábbi beszélgetés:\n"+ConversationMemory.recent(this));
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
   if(ControlService.executeLocalCommand(q)){watchCommand();return;}
   if(ControlService.looksLikeLocalCommand(q)&&!ControlService.active()){
    status.setText("A telefonvezérléshez engedélyezze a JARVIS hozzáférési szolgáltatást az Android Kisegítő lehetőségek menüjében.");return;
   }
   int selectedModel=modelPicker.getSelectedItemPosition();
   if(!MultiModelAgent.isLocal(selectedModel)){
    String provider=MultiModelAgent.provider(selectedModel),apiKey;
    try{apiKey=SecureApiKeys.load(this,provider);}
    catch(Exception e){status.setText("A titkosított API-kulcs nem olvasható: "+e.getClass().getSimpleName());return;}
    if(apiKey.isEmpty()){status.setText("Nincs mentett "+provider+" API-kulcs. A modellválasztó alatt add meg.");return;}
    status.setText("JARVIS kapcsolódik: "+MultiModelAgent.LABELS[selectedModel]+"...");
    reactor.setActive(true);ask.setEnabled(false);
    MultiModelAgent.ask(this,selectedModel,apiKey,q,reply->{
     if(isFinishing()||isDestroyed())return;
     reactor.setActive(false);ask.setEnabled(true);
     status.setText(reply);
     ConversationMemory.append(this,q,reply);
     if(voice.isReady()&&!reply.startsWith("AI-kapcsolati hiba"))voice.say(reply);
    });
    return;
   }
   File model=new File(new File(JarvisHome.initialize(this),"models"),"model.gguf");
   if(!model.isFile()){status.setText("Előbb telepíts egy GGUF modellt.");return;}
   if(!OfflineBrain.available()){status.setText("Offline motor nem elérhető: "+OfflineBrain.failure());return;}
   status.setText("JARVIS helyben gondolkodik...");reactor.setActive(true);ask.setEnabled(false);
   new Thread(()->{
    String reply;
    try{
     String context=ConversationMemory.recentForPrompt(this)+"\nFelhasználó: "+q;
     reply=OfflineBrain.replyStreaming(model.getAbsolutePath(),context,partial->runOnUiThread(()->{
      if(!isFinishing())status.setText(partial);
     }));
     ConversationMemory.append(this,q,reply);
    }catch(Throwable e){
     reply="Helyi AI-hiba: "+e.getClass().getSimpleName()+": "+e.getMessage();
    }
    String finalReply=reply;
    runOnUiThread(()->{
     reactor.setActive(false);
     status.setText(finalReply);
     ask.setEnabled(true);
     if(voice.isReady()&&!finalReply.startsWith("Helyi AI-hiba"))voice.say(finalReply);
    });
   },"jarvis-local-inference").start();
  });
  ScrollView scroll=new ScrollView(this);scroll.addView(root);setContentView(scroll);
 }
 @Override protected void onDestroy(){if(commandWatcher!=null)commandHandler.removeCallbacks(commandWatcher);speech.close();if(voice!=null)voice.close();super.onDestroy();}
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
