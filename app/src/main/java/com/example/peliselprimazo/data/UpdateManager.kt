package com.example.peliselprimazo.data

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading = _isDownloading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun downloadAndInstall(url: String, fileName: String = "CFilm_Update.apk") {
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (file.exists()) {
            file.delete()
        }

        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Actualización CFilm")
                .setDescription("Descargando nueva versión v2.1.8...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(file))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadId = downloadManager.enqueue(request)
            _isDownloading.value = true
            _downloadProgress.value = 0f
            _error.value = null

            trackProgress(file)

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        checkDownloadStatus(file)
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            Log.e("UpdateManager", "Error unregistering receiver", e)
                        }
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // ACTION_DOWNLOAD_COMPLETE needs to be exported to be received from system process
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error starting download: ${e.message}")
            _error.value = "Error al iniciar la descarga: ${e.message}"
            _isDownloading.value = false
        }
    }

    private fun checkDownloadStatus(file: File) {
        if (!_isDownloading.value && _downloadProgress.value == 1f) return

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = try { downloadManager.query(query) } catch (e: Exception) { null }
        
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                _downloadProgress.value = 1f
                _isDownloading.value = false
                installApk(file)
            } else if (status == DownloadManager.STATUS_FAILED) {
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                Log.e("UpdateManager", "Download failed with reason: $reason")
                _error.value = "Descarga fallida (Código: $reason)"
                _isDownloading.value = false
            }
        }
        cursor?.close()
    }

    private fun trackProgress(file: File) {
        scope.launch {
            var stuckCount = 0
            while (_isDownloading.value) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = try { downloadManager.query(query) } catch (e: Exception) { null }
                
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                    if (bytesTotal > 0) {
                        val progress = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                        _downloadProgress.value = progress
                        
                        if (progress >= 0.99f) {
                            stuckCount++
                        } else {
                            stuckCount = 0
                        }
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED || stuckCount > 10) {
                        // Fallback: If status is terminal or we've been at ~100% for 10 seconds
                        delay(1000)
                        withContext(Dispatchers.Main) {
                            checkDownloadStatus(file)
                        }
                        cursor.close()
                        break
                    }
                }
                cursor?.close()
                delay(1000)
            }
        }
    }

    fun installApk(file: File) {
        if (!file.exists()) {
            Log.e("UpdateManager", "File not found for installation: ${file.absolutePath}")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                // The system will pause the app here to show the settings
            }
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Error launching APK installer", e)
            _error.value = "Error al abrir el instalador"
        }
    }
}
