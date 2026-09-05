package com.googleedge.shixian

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("shixian_data", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.openai.com/v1") ?: "https://api.openai.com/v1"
        set(v) = prefs.edit().putString("base_url", v.trim().trimEnd('/')).apply()
    var apiKey: String
        get() = decrypt(prefs.getString("api_key", "") ?: "")
        set(v) = prefs.edit().putString("api_key", encrypt(v.trim())).apply()
    var model: String
        get() = prefs.getString("model", "") ?: ""
        set(v) = prefs.edit().putString("model", v).apply()
    var customNeed: String
        get() = prefs.getString("custom_need", "") ?: ""
        set(v) = prefs.edit().putString("custom_need", v).apply()

    var task: TaskSnapshot
        get() = runCatching { TaskSnapshot.fromJson(JSONObject(prefs.getString("active_task", "{}") ?: "{}")) }.getOrDefault(TaskSnapshot())
        set(v) { prefs.edit().putString("active_task", v.toJson().toString()).apply() }

    var logs: List<LogEntry>
        get() = runCatching { JSONArray(prefs.getString("logs", "[]")).objects().map(LogEntry::fromJson) }.getOrDefault(emptyList())
        private set(v) { prefs.edit().putString("logs", JSONArray().also { a -> v.takeLast(300).forEach { a.put(it.toJson()) } }.toString()).apply() }

    fun appendLog(kind: String, message: String, detail: String = "") {
        logs = logs + LogEntry(System.currentTimeMillis(), kind, message, detail)
    }
    fun clearLogs() { logs = emptyList() }

    var ingredients: List<Ingredient>
        get() = runCatching { JSONArray(prefs.getString("ingredients", "[]")).objects().map { Ingredient(it.optString("name"),it.optString("amount"),it.optString("category")) } }.getOrDefault(emptyList())
        set(v) { prefs.edit().putString("ingredients", JSONArray().also { a -> v.forEach { a.put(JSONObject().put("name",it.name).put("amount",it.amount).put("category",it.category)) } }.toString()).apply() }
    var favorites: List<Recipe>
        get() = runCatching { JSONArray(prefs.getString("favorites", "[]")).objects().map(Recipe::fromJson) }.getOrDefault(emptyList())
        set(v) { prefs.edit().putString("favorites", JSONArray().also { a -> v.forEach { a.put(it.toJson()) } }.toString()).apply() }
    var shopping: List<String>
        get() = runCatching { JSONArray(prefs.getString("shopping", "[]")).strings() }.getOrDefault(emptyList())
        set(v) { prefs.edit().putString("shopping", JSONArray(v).toString()).apply() }
    var preferences: Preferences
        get() = runCatching { JSONObject(prefs.getString("preferences", "{}") ?: "{}").let { Preferences(it.optString("city"),it.optString("budget","不限"),it.optString("taste","清淡"),it.optString("avoid"),it.optString("allergies"),it.optInt("people",1),it.optString("goal","均衡饮食"),it.optInt("minutes",30)) } }.getOrDefault(Preferences())
        set(v) { prefs.edit().putString("preferences", JSONObject().put("city",v.city).put("budget",v.budget).put("taste",v.taste).put("avoid",v.avoid).put("allergies",v.allergies).put("people",v.people).put("goal",v.goal).put("minutes",v.minutes).toString()).apply() }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("shixian_api_key", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("shixian_api_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun encrypt(value: String): String = if (value.isBlank()) "" else runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key())
        Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray()), Base64.NO_WRAP)
    }.getOrDefault("")
    private fun decrypt(value: String): String = if (value.isBlank()) "" else runCatching {
        val all = Base64.decode(value, Base64.NO_WRAP); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0,12)))
        String(cipher.doFinal(all.copyOfRange(12,all.size)))
    }.getOrDefault("")
}
