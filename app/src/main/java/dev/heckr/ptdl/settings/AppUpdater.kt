package dev.heckr.ptdl.settings

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import dev.heckr.ptdl.R
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import java.io.File

/**
 * Handles downloading and installing APK updates.
 * Delegates version checking to [UpdateChecker].
 */
class AppUpdater(private val fragment: Fragment) {

    enum class State { IDLE, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOADED }

    var state: State = State.IDLE
        private set

    var onStateChanged: ((State, String) -> Unit)? = null

    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    private var pendingInstallFileName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var downloadCheckRunnable: Runnable? = null

    private val installPermissionLauncher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val context = fragment.context ?: return@registerForActivityResult
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFileName?.let { installApk(context, it) }
                    pendingInstallFileName = null
                } else {
                    Toast.makeText(context, R.string.install_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }

    /** Sync local state with the global [UpdateChecker]. Call from onViewCreated. */
    fun syncFromChecker() {
        if (UpdateChecker.updateAvailable) {
            state = State.UPDATE_AVAILABLE
            notify(fragment.requireContext().getString(R.string.update_available_format, UpdateChecker.latestVersion))
        }
    }

    /**
     * Call this when the user taps the update card.
     * If update already known → download. Otherwise trigger a fresh check.
     */
    fun onUpdateTapped(context: Context) {
        when (state) {
            State.UPDATE_AVAILABLE -> {
                val url = UpdateChecker.latestApkUrl
                val version = UpdateChecker.latestVersion
                if (url != null && version != null) {
                    state = State.DOWNLOADING
                    notify(context.getString(R.string.downloading_update))
                    downloadAndInstallApk(context, url, version)
                } else {
                    notify(context.getString(R.string.update_info_missing))
                }
            }
            State.DOWNLOADING -> { /* ignore */ }
            else -> {
                // Trigger a fresh check via the singleton
                notify(context.getString(R.string.checking_for_updates_status))
                UpdateChecker.addListener(checkerListener)
                UpdateChecker.check(context)
            }
        }
    }

    private val checkerListener: () -> Unit = {
        UpdateChecker.removeListener(checkerListener)
        if (UpdateChecker.updateAvailable) {
            state = State.UPDATE_AVAILABLE
            notify(fragment.requireContext().getString(R.string.update_available_format, UpdateChecker.latestVersion))
        } else {
            state = State.IDLE
            notify(fragment.requireContext().getString(R.string.up_to_date))
        }
    }

    fun cleanup() {
        UpdateChecker.removeListener(checkerListener)
        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
        unregisterReceiver(fragment.requireContext())
    }

    fun unregisterReceiver(context: Context) {
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }
        downloadReceiver = null
    }

    private fun cleanupDownload(context: Context) {
        // Stop polling
        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
        downloadCheckRunnable = null
        
        // Cancel existing download
        if (downloadId != -1L) {
            try {
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.remove(downloadId)
            } catch (_: Exception) {}
            downloadId = -1
        }
        
        // Unregister receiver
        unregisterReceiver(context)
    }

    // -- Download --------------------------------------------------------

    private fun downloadAndInstallApk(context: Context, apkUrl: String, version: String) {
        try {
            // Clean up any previous download attempt
            cleanupDownload(context)
            
            val fileName = "PTDL-$version.apk"

            downloadReceiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                        handleDownloadComplete(context, fileName)
                    }
                }
            }

            ContextCompat.registerReceiver(
                context,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val request = DownloadManager.Request(Uri.parse(apkUrl))
                .setTitle(context.getString(R.string.ptdl_update_title))
                .setDescription(context.getString(R.string.downloading_version_format, version))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            
            startDownloadPolling(context, dm, fileName)
        } catch (e: Exception) {
            state = State.IDLE
            val errorMsg = context.getString(R.string.download_setup_failed_format, e.message)
            notify(errorMsg)
        }
    }

    private fun startDownloadPolling(context: Context, dm: DownloadManager, fileName: String) {
        downloadCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    val cursor: Cursor? = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    if (cursor != null && cursor.moveToFirst()) {
                        val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        val status = cursor.getInt(statusIndex)
                        val statusStr = when(status) {
                            1 -> "PENDING"
                            2 -> "RUNNING"
                            4 -> "SUCCESSFUL"
                            8 -> "FAILED"
                            else -> "UNKNOWN($status)"
                        }                        
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                cursor.close()
                                downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                                handleDownloadComplete(context, fileName)
                                return
                            }
                            DownloadManager.STATUS_FAILED -> {
                                val reasonIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
                                val reason = cursor.getInt(reasonIndex)
                                cursor.close()
                                downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                                state = State.IDLE
                                val errorMsg = context.getString(R.string.download_failed_format, reason)
                                notify(errorMsg)
                                return
                            }
                        }
                        cursor.close()
                    }
                    handler.postDelayed(this, 500)
                } catch (e: Exception) {
                    downloadCheckRunnable?.let { handler.removeCallbacks(it) }
                    state = State.IDLE
                    val errorMsg = context.getString(R.string.download_polling_error_format, e.message)
                    notify(errorMsg)
                }
            }
        }
        handler.post(downloadCheckRunnable!!)
    }

    private fun handleDownloadComplete(context: Context, fileName: String) {
        unregisterReceiver(context)
        state = State.DOWNLOADED
        notify(context.getString(R.string.download_complete_installing))
        Toast.makeText(context, R.string.installing_update, Toast.LENGTH_SHORT).show()
        installApk(context, fileName)
    }

    // -- Install ---------------------------------------------------------

    private fun installApk(context: Context, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFileName = fileName
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${context.packageName}")
                    installPermissionLauncher.launch(intent)
                    return
                }
            }

            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) {
                return
            }

            val intent = Intent(Intent.ACTION_VIEW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val apkUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            fragment.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.installation_failed_format, e.message), Toast.LENGTH_LONG).show()
        }
    }

    private fun notify(message: String) {
        onStateChanged?.invoke(state, message)
    }
}
