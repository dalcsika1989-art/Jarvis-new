package hu.jarvis.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Separate per-provider secrets, encrypted with an Android Keystore backed AES key. */
public final class SecureApiKeys {
 private static final String STORE="jarvis_private_ai_keys_v1";
 private static final String ALIAS="jarvis_private_ai_aes_v1";
 private SecureApiKeys(){}

 private static SecretKey key() throws Exception {
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
  ks.load(null);
  if(!ks.containsAlias(ALIAS)){
   KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   generator.init(new KeyGenParameterSpec.Builder(ALIAS,
      KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256).build());
   generator.generateKey();
  }
  return (SecretKey)ks.getKey(ALIAS,null);
 }
 public static void save(Context context,String provider,String rawKey)throws Exception {
  if(!MultiModelAgent.validProvider(provider)||"local".equals(provider))throw new IllegalArgumentException("Invalid provider");
  String keyText=rawKey.trim();
  if(keyText.isEmpty()){clear(context,provider);return;}
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] encrypted=cipher.doFinal(keyText.getBytes(StandardCharsets.UTF_8));
  String payload=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP);
  context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().putString(provider,payload).apply();
 }
 public static String load(Context context,String provider)throws Exception{
  if(!MultiModelAgent.validProvider(provider)||"local".equals(provider))return "";
  SharedPreferences prefs=context.getSharedPreferences(STORE,Context.MODE_PRIVATE);
  String payload=prefs.getString(provider,"");
  if(payload==null||payload.isEmpty())return "";
  String[] parts=payload.split(":",2);
  if(parts.length!=2)throw new IllegalStateException("Invalid secret storage");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
  return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
 }
 public static void clear(Context context,String provider){
  context.getSharedPreferences(STORE,Context.MODE_PRIVATE).edit().remove(provider).apply();
 }
}
