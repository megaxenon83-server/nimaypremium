package com.omp.chat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL

data class UpdateInfo(
    val available: Boolean = false,
    val androidVersion: String = "",
    val androidVersionCode: Int = 0,
    val androidUrl: String = "",
    val androidSha256: String = "",
    val changelog: String = "",
    val error: String = ""
)

object UpdateChecker {
    const val UPDATE_URL = "https://chat.nimay.net/downloads/nimayomp/version.json"

    fun fetch(context: Context): UpdateInfo? {
        return try {
            val text = URL(UPDATE_URL).openStream().bufferedReader().readText()
            val root = JSONObject(text)
            val android = root.optJSONObject("android") ?: return null
            val code = android.optInt("versionCode", 0)
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            UpdateInfo(
                available = code > current,
                androidVersion = android.optString("version", "").orEmpty(),
                androidVersionCode = code,
                androidUrl = android.optString("url", "").orEmpty(),
                androidSha256 = android.optString("sha256", "").orEmpty(),
                changelog = android.optString("changelog", "").orEmpty()
            )
        } catch (e: Exception) {
            UpdateInfo(error = e.message.orEmpty())
        }
    }

    /**
     * Downloads the APK to the app's cache directory.
     */
    fun downloadApk(context: Context, url: String): File? {
        return try {
            val connection = URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()
            val file = File(context.cacheDir, "nimayomp-update.apk")
            val output = FileOutputStream(file)
            input.copyTo(output)
            input.close()
            output.close()
            file
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Triggers the system package installer for the downloaded APK.
     */
    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}