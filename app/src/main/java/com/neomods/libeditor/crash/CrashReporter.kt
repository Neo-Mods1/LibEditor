package com.neomods.libeditor.crash

import android.content.Context
import android.os.Build
import com.neomods.libeditor.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object CrashReporter {

    private const val API_URL = "https://neo-crash-system.vercel.app/api/report"
    private const val PREFS = "crash_reporter"
    private const val KEY_OPTED_IN = "opted_in"
    private const val KEY_SENT_HASHES = "sent_hashes"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun isOptedIn(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_OPTED_IN, false)
    }

    fun setOptedIn(context: Context, optedIn: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_OPTED_IN, optedIn).apply()
    }

    fun reportCrash(
        context: Context,
        exceptionType: String,
        message: String,
        stacktrace: String,
        thread: String,
        crashLog: String
    ) {
        if (!isOptedIn(context)) return

        val hash = computeHash(exceptionType, message, stacktrace)
        if (hasSentBefore(context, hash)) return

        Thread {
            try {
                val body = buildPayload(exceptionType, message, stacktrace, thread, crashLog)
                val request = Request.Builder()
                    .url(API_URL)
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    markSent(context, hash)
                }
                response.close()
            } catch (_: Exception) {}
        }.start()
    }

    private fun buildPayload(
        exceptionType: String,
        message: String,
        stacktrace: String,
        thread: String,
        crashLog: String
    ): String {
        val json = JSONObject()
        json.put("appName", "NeoLibEditor")
        json.put("packageName", BuildConfig.APPLICATION_ID)
        json.put("versionName", BuildConfig.VERSION_NAME)
        json.put("versionCode", BuildConfig.VERSION_CODE.toString())
        json.put("androidVersion", Build.VERSION.RELEASE)
        json.put("sdk", Build.VERSION.SDK_INT.toString())
        json.put("manufacturer", Build.MANUFACTURER)
        json.put("brand", Build.BRAND)
        json.put("model", Build.MODEL)
        json.put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        json.put("timestamp", SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date()))
        json.put("crashType", "UNCAUGHT")
        json.put("exceptionType", exceptionType)
        json.put("message", message)
        json.put("stacktrace", stacktrace)
        json.put("thread", thread)
        json.put("occurrenceHash", computeHash(exceptionType, message, stacktrace))
        return json.toString()
    }

    private fun computeHash(exceptionType: String, message: String, stacktrace: String): String {
        val frames = stacktrace.lines()
            .filter { it.trim().startsWith("at ") }
            .take(10)
            .joinToString("\n")
        val input = "$exceptionType|$message|$frames"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun hasSentBefore(context: Context, hash: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hashes = prefs.getStringSet(KEY_SENT_HASHES, emptySet()) ?: emptySet()
        return hash in hashes
    }

    private fun markSent(context: Context, hash: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val hashes = prefs.getStringSet(KEY_SENT_HASHES, emptySet())?.toMutableSet() ?: mutableSetOf()
        hashes.add(hash)
        prefs.edit().putStringSet(KEY_SENT_HASHES, hashes).apply()
    }
}
