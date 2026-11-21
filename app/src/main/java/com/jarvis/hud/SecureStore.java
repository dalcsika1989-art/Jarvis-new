package com.jarvis.hud;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureStore {
 private static final String NAME="JARVIS_AI_AES";
 private final SharedPreferences prefs;
 SecureStore(Context c){prefs=c.getSharedPreferences("secure_ai",Context.MODE_PRIVATE);}
 private SecretKey key() throws Exception{
  KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
  if(!ks.containsAlias(NAME)){
   KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
   g.init(new KeyGenParameterSpec.Builder(NAME,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256).build());g.generateKey();
  }
  return ((KeyStore.SecretKeyEntry)ks.getEntry(NAME,null)).getSecretKey();
 }
 synchronized void put(String provider,String value) throws Exception{
  if(!provider.equals("openai")&&!provider.equals("gemini"))throw new IllegalArgumentException("provider");
  if(value==null||value.isEmpty()){prefs.edit().remove(provider).apply();return;}
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
  byte[] encrypted=cipher.doFinal(value.getBytes("UTF-8"));
  prefs.edit().putString(provider,Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply();
 }
 synchronized String get(String provider) throws Exception{
  String v=prefs.getString(provider,null);if(v==null)return "";
  String[] parts=v.split(":",2);if(parts.length!=2)throw new IllegalStateException("Bad vault payload");
  Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
  cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.DEFAULT)));
  return new String(cipher.doFinal(Base64.decode(parts[1],Base64.DEFAULT)),"UTF-8");
 }
 boolean has(String provider){return prefs.contains(provider);}
}
