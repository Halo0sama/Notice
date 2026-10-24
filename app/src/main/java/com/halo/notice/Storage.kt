package com.halo.notice

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.*
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class Storage(private val context:Context) {
    @Synchronized fun read(name:String):JSONObject = runCatching {
        JSONObject(AtomicFile(File(context.filesDir,name)).openRead().bufferedReader().use{it.readText()})
    }.getOrDefault(JSONObject())
    @Synchronized fun write(name:String,j:JSONObject) {
        val f=AtomicFile(File(context.filesDir,name)); val out=f.startWrite()
        try { out.write(j.toString().toByteArray());f.finishWrite(out) } catch(t:Throwable){f.failWrite(out);throw t}
    }
    private fun key():SecretKey {
        val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (ks.getKey("notice-api",null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("notice-api",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun saveKey(value:String) {
        if(value.isBlank()) { context.getSharedPreferences("secrets",0).edit().clear().commit();return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
        val bytes=cipher.doFinal(value.trim().toByteArray())
        check(context.getSharedPreferences("secrets",0).edit().putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
            .putString("key",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit())
    }
    fun apiKey():String = runCatching {
        val p=context.getSharedPreferences("secrets",0);val iv=p.getString("iv",null)?:return ""
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(iv,Base64.NO_WRAP)))}
        String(cipher.doFinal(Base64.decode(p.getString("key",""),Base64.NO_WRAP)))
    }.getOrDefault("")
}
