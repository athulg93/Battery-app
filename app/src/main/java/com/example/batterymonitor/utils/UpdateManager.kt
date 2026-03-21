package com.example.batterymonitor.utils

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.example.batterymonitor.BuildConfig
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException

object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val GITHUB_API_URL = "https://api.github.com/repos/athulg93/Battery-app/releases/latest"
    
    interface UpdateCallback {
        fun onUpdateAvailable(newVersion: String, downloadUrl: String, body: String)
        fun onNoUpdate()
        fun onError(message: String)
    }

    fun checkForUpdates(context: Context, callback: UpdateCallback) {
        val client = OkHttpClient()
        val request = Request.Builder().url(GITHUB_API_URL).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to check for updates", e)
                callback.onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        if (it.code == 404) {
                            callback.onError("No releases found on GitHub. Please create a Release first.")
                        } else {
                            callback.onError("Server error: ${it.code}")
                        }
                        return
                    }

                    val body = it.body?.string() ?: return
                    val json = JSONObject(body)
                    val latestVersion = json.getString("tag_name").replace("v", "")
                    val currentVersion = BuildConfig.VERSION_NAME

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        val assets = json.getJSONArray("assets")
                        var downloadUrl = ""
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                downloadUrl = asset.getString("browser_download_url")
                                break
                            }
                        }
                        
                        if (downloadUrl.isNotEmpty()) {
                            callback.onUpdateAvailable(latestVersion, downloadUrl, json.optString("body", ""))
                        } else {
                            callback.onError("No APK found in the latest release")
                        }
                    } else {
                        callback.onNoUpdate()
                    }
                }
            }
        })
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        // Simple semantic versioning comparison
        val currentParts = current.split(".").map { it.toIntOrNull() ?: 0 }
        val latestParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        
        for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
            val c = if (i < currentParts.size) currentParts[i] else 0
            val l = if (i < latestParts.size) latestParts[i] else 0
            if (l > c) return true
            if (c > l) return false
        }
        return false
    }

    fun downloadAndInstallUpdate(context: Context, url: String, fileName: String) {
        val destination = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destination.exists()) {
            destination.delete()
        }

        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("Downloading Update")
            .setDescription("Battery Monitor App update is downloading...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(destination))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Register receiver to install when download is complete
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    installApk(context, destination)
                    context.unregisterReceiver(this)
                }
            }
        }
        
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
    }

    private fun installApk(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}
