package hu.jarvis.local;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
public final class ModelInstaller {
 public static final int PICK_MODEL=405;
 public static final long LIMIT_BYTES=5L*1024*1024*1024;
 private ModelInstaller(){}
 public interface Listener{void finished(String status);}
 public static void pick(Activity a){
  Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);
  a.startActivityForResult(i,PICK_MODEL);
 }
 public static void install(Activity a,Uri uri,Listener listener){
  new Thread(()->{
   String message;
   File target=new File(new File(JarvisHome.initialize(a),"models"),"model.gguf");
   File tmp=new File(target.getParentFile(),"model.gguf.part");
   try{
    String name=a.getContentResolver().getType(uri);
    if(uri==null)throw new IOException("Nincs kiválasztott fájl.");
    try(InputStream in=a.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(tmp)){
     if(in==null)throw new IOException("A fájl nem olvasható.");
     byte[] b=new byte[65536];int n;long total=0;
     while((n=in.read(b))!=-1){
      total+=n;if(total>LIMIT_BYTES)throw new IOException("A modell nagyobb mint 5 GB.");
      out.write(b,0,n);
     }
     if(total<1024*1024)throw new IOException("A fájl túl kicsi egy GGUF modellhez.");
    }
    try(RandomAccessFile raf=new RandomAccessFile(tmp,"r")){
     byte[] magic=new byte[4];raf.readFully(magic);
     if(magic[0]!='G'||magic[1]!='G'||magic[2]!='U'||magic[3]!='F')throw new IOException("Ez nem GGUF modellfájl.");
    }
    if(target.exists()&&!target.delete())throw new IOException("Régi modell nem cserélhető.");
    if(!tmp.renameTo(target))throw new IOException("Modell áthelyezése sikertelen.");
    message="Helyi GGUF modell tárolva: "+(target.length()/(1024*1024))+" MB. A futtatómotor még nincs beépítve.";
   }catch(Exception e){tmp.delete();message="Modell importálási hiba: "+e.getMessage();}
   String result=message;new Handler(Looper.getMainLooper()).post(()->listener.finished(result));
  }).start();
 }
}
