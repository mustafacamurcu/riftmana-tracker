package com.riftmana.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val VERSION_URL =
        "https://mustafacamurcu.github.io/riftmana-tracker/releases/version.json"

    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val apkUrl: String,
        val notes: String
    )

    fun checkForUpdate(context: Context): UpdateInfo? {
        return try {
            val url = URL("$VERSION_URL?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }

            val remoteVersionCode = json.getInt("versionCode")
            val currentVersionCode = currentVersionCode(context)

            if (remoteVersionCode > currentVersionCode) {
                UpdateInfo(
                    versionCode = remoteVersionCode,
                    versionName = json.optString("versionName", ""),
                    apkUrl = json.getString("apkUrl"),
                    notes = json.optString("notes", "")
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun currentVersionCode(context: Context): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
    }

    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** Downloads the APK and launches the system install prompt. Returns false on failure. */
    fun downloadAndInstall(context: Context, info: UpdateInfo): Boolean {
        return try {
            val url = URL("${info.apkUrl}?t=${System.currentTimeMillis()}")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            connection.useCaches = false

            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "riftmana-widget.apk")
            connection.inputStream.use { input ->
                apkFile.outputStream().use { output -> input.copyTo(output) }
            }

            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(installIntent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
