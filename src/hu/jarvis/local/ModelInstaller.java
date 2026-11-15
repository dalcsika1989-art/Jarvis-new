package hu.jarvis.local;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.file.*;
public final class ModelInstaller {
 public static final int PICK_MODEL=405;
 public static final long LIMIT_BYTES=5L*1024*1024*1024;
 private ModelInstaller(){}
 public interface Listener{void finished(String status);}
 public static void pick(Activity activity){
  Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
  intent.setType("*/*");
  intent.addCategory(Intent.CATEGORY_OPENABLE);
  activity.startActivityForResult(intent,PICK_MODEL);
 }
 public static void install(Activity activity,Uri uri,Listener listener){
  new Thread(()->{
   File target=new File(new File(JarvisHome.initialize(activity),"models"),"model.gguf");
   File tmp=new File(target.getParentFile(),"model.gguf.part");
   String message;
   try{
    if(uri==null)throw new IOException("Nincs kiválasztott modell.");
    long usable=target.getParentFile().getUsableSpace();
    if(usable<128L*1024*1024)throw new IOException("Nincs elegendő szabad tárhely.");
    long total=0;
    try(InputStream in=activity.getContentResolver().openInputStream(uri);
        OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp))){
     if(in==null)throw new IOException("A kiválasztott fájl nem olvasható.");
     byte[] buffer=new byte[131072];
     int n;
     while((n=in.read(buffer))!=-1){
      total+=n;
      if(total>LIMIT_BYTES)throw new IOException("A GGUF modell nagyobb mint 5 GB.");
      if(total>usable-64L*1024*1024)throw new IOException("Nincs elegendő szabad tárhely a modellhez.");
      out.write(buffer,0,n);
     }
    }
    if(total<1024*1024)throw new IOException("A GGUF modell túl kicsi.");
    try(RandomAccessFile raf=new RandomAccessFile(tmp,"r")){
     byte[] magic=new byte[4];raf.readFully(magic);
     if(magic[0]!='G'||magic[1]!='G'||magic[2]!='U'||magic[3]!='F')
      throw new IOException("Érvénytelen GGUF fejléc.");
    }
    try{
     Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }catch(AtomicMoveNotSupportedException e){
     Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    message="GGUF modell telepítve: "+(target.length()/1048576)+" MB. Az offline motor most használhatja.";
   }catch(Exception e){
    tmp.delete();
    message="Modellimport hiba: "+e.getMessage()+". A korábban telepített modellt megtartottam.";
   }
   final String status=message;
   new Handler(Looper.getMainLooper()).post(()->listener.finished(status));
  },"jarvis-model-import").start();
 }
}
