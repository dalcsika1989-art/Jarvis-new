package hu.jarvis.local;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
public final class ModelDownloader {
 private static final String URL="https://huggingface.co/bartowski/Qwen2.5-1.5B-Instruct-GGUF/resolve/ba3979d6e9ad24cfa15136fef1f59c9b3bcab8b9/Qwen2.5-1.5B-Instruct-Q4_K_M.gguf";
 private static final String SHA256="1adf0b11065d8ad2e8123ea110d1ec956dab4ab038eab665614adba04b6c3370";
 private static final long MAX_BYTES=2L*1024*1024*1024;
 private static final Handler UI=new Handler(Looper.getMainLooper());
 private ModelDownloader(){}
 public interface Listener {
  void progress(String status);
  void finished(String status);
 }
 private static void notifyProgress(Listener l,String text){UI.post(()->l.progress(text));}
 private static void notifyFinish(Listener l,String text){UI.post(()->l.finished(text));}
 public static void download(Context context,Listener listener){
  Context app=context.getApplicationContext();
  new Thread(()->{
   File model=new File(new File(JarvisHome.initialize(app),"models"),"model.gguf");
   File partial=new File(model.getParentFile(),"qwen-model.gguf.part");
   HttpURLConnection conn=null;
   try{
    long current=partial.isFile()?partial.length():0;
    if(current>MAX_BYTES)throw new IOException("Túl nagy a részleges modellfájl.");
    if(model.getParentFile().getUsableSpace()<1200L*1024*1024-current)
     throw new IOException("Nincs elegendő szabad tárhely (kb. 1,2 GB szükséges).");
    conn=(HttpURLConnection)new URL(URL).openConnection();
    conn.setConnectTimeout(25000);
    conn.setReadTimeout(60000);
    conn.setInstanceFollowRedirects(true);
    conn.setRequestProperty("User-Agent","JARVIS-Android-Offline/4.0");
    if(current>0)conn.setRequestProperty("Range","bytes="+current+"-");
    int code=conn.getResponseCode();
    if(code!=200&&code!=206)throw new IOException("Letöltési HTTP-hiba: "+code);
    if(code==206&&current>0){
     String range=conn.getHeaderField("Content-Range");
     if(range==null||!range.startsWith("bytes "+current+"-"))
      throw new IOException("Érvénytelen folytatható letöltés.");
    }else current=0;
    long expected=conn.getContentLengthLong();
    if(expected>0&&expected+current>MAX_BYTES)throw new IOException("Túl nagy a letöltendő modell.");
    try(InputStream in=new BufferedInputStream(conn.getInputStream());
        OutputStream out=new BufferedOutputStream(new FileOutputStream(partial,current>0))){
     byte[] bytes=new byte[131072];
     int n;long total=current;long lastReported=current;
     while((n=in.read(bytes))!=-1){
      total+=n;
      if(total>MAX_BYTES)throw new IOException("A modell mérete túllépte a 2 GB-ot.");
      out.write(bytes,0,n);
      if(total-lastReported>=16L*1024*1024){
       lastReported=total;
       notifyProgress(listener,"Offline modell letöltése: "+(total/1048576)+" MB");
      }
     }
     out.flush();
    }
    notifyProgress(listener,"Letöltés kész. SHA-256 ellenőrzés...");
    MessageDigest md=MessageDigest.getInstance("SHA-256");
    try(InputStream in=new BufferedInputStream(new FileInputStream(partial))){
     byte[] b=new byte[131072];int n;
     while((n=in.read(b))!=-1)md.update(b,0,n);
    }
    StringBuilder hex=new StringBuilder();
    for(byte b:md.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&0xff));
    if(!SHA256.equals(hex.toString())){
     partial.delete();
     throw new IOException("A modell ellenőrzőösszege nem egyezik; a hibás fájl törölve.");
    }
    try(RandomAccessFile raf=new RandomAccessFile(partial,"r")){
     byte[] magic=new byte[4];raf.readFully(magic);
     if(magic[0]!='G'||magic[1]!='G'||magic[2]!='U'||magic[3]!='F')
      throw new IOException("A letöltés nem GGUF fájl.");
    }
    try{
     Files.move(partial.toPath(),model.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }catch(AtomicMoveNotSupportedException ex){
     Files.move(partial.toPath(),model.toPath(),StandardCopyOption.REPLACE_EXISTING);
    }
    notifyFinish(listener,"Qwen2.5 offline modell telepítve ("+(model.length()/1048576)+" MB). A JARVIS készen áll a helyi kérdésekre.");
   }catch(Exception e){
    notifyFinish(listener,"Letöltési hiba: "+e.getMessage()+". A korábbi modell megmaradt. A letöltés újraindítható.");
   }finally{
    if(conn!=null)conn.disconnect();
   }
  },"jarvis-gguf-download").start();
 }
}
