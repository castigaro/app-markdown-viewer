package com.castigaro.mdviewer.util

import com.castigaro.mdviewer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Prüft anhand einer kleinen JSON-Datei auf der Website, ob eine neuere
 * Version der App verfügbar ist. Die Datei wird bei jedem CI-Build
 * automatisch mitveröffentlicht (siehe build-apk.yml).
 */
object UpdateChecker {

    private const val VERSION_URL = "https://appsonar.de/downloads/markdown-viewer-version.json"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(val versionName: String, val apkUrl: String)

    /** Liefert Update-Infos, wenn eine neuere Version verfügbar ist, sonst null. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(VERSION_URL).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val json = JSONObject(body)
                val remoteCode = json.optInt("versionCode", 0)
                if (remoteCode > BuildConfig.VERSION_CODE) {
                    UpdateInfo(
                        versionName = json.optString("versionName", "?"),
                        apkUrl = json.optString("apkUrl", "https://appsonar.de/"),
                    )
                } else {
                    null
                }
            }
        }.getOrNull()
    }
}
