package dev.heckr.ptdl.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import dev.heckr.ptdl.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class AppUpdater(private val fragment: Fragment) {

    enum class State { IDLE, UPDATE_AVAILABLE, DOWNLOADING, DOWNLOADED, INSTALLING }

    var state: State = State.IDLE
        private set

    var onStateChanged: ((State, String) -> Unit)? = null
    /** Progress callback: 0–100 for determinate, -1 for indeterminate */
    var onDownloadProgress: ((Int) -> Unit)? = null

    private var downloadJob: Job? = null
    private var pendingInstallFile: File? = null

    private val installPermissionLauncher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            val context = fragment.context ?: return@registerForActivityResult
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFile?.let { installApk(context, it) }
                    pendingInstallFile = null
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
        } else if (UpdateChecker.lastCheckError != null) {
            state = State.IDLE
            notify(fragment.requireContext().getString(R.string.update_check_failed_format, UpdateChecker.lastCheckError))
        }
    }

    /**
     * Call this when the user taps the update card.
     * If update already known → signal to show dialog. Otherwise trigger a fresh check.
     * Returns true if state is UPDATE_AVAILABLE (caller should show dialog).
     */
    fun onUpdateTapped(context: Context): Boolean {
        when (state) {
            State.UPDATE_AVAILABLE -> return true
            State.DOWNLOADING, State.INSTALLING -> { /* ignore */ }
            else -> {
                notify(context.getString(R.string.checking_for_updates_status))
                UpdateChecker.addListener(checkerListener)
                UpdateChecker.check(context)
            }
        }
        return false
    }

    /** Start the download after the user confirms in the dialog. */
    fun startDownload(context: Context) {
        val url = UpdateChecker.latestApkUrl
        val version = UpdateChecker.latestVersion
        if (url != null && version != null) {
            state = State.DOWNLOADING
            onDownloadProgress?.invoke(-1)
            notify(context.getString(R.string.download_initializing))
            downloadApk(context, url, version)
        } else {
            notify(context.getString(R.string.update_info_missing))
        }
    }

    private val checkerListener: () -> Unit = {
        UpdateChecker.removeListener(checkerListener)
        if (UpdateChecker.updateAvailable) {
            state = State.UPDATE_AVAILABLE
            notify(fragment.requireContext().getString(R.string.update_available_format, UpdateChecker.latestVersion))
        } else if (UpdateChecker.lastCheckError != null) {
            state = State.IDLE
            notify(fragment.requireContext().getString(R.string.update_check_failed_format, UpdateChecker.lastCheckError))
        } else {
            state = State.IDLE
            notify(fragment.requireContext().getString(R.string.up_to_date))
        }
    }

    fun cleanup() {
        UpdateChecker.removeListener(checkerListener)
        downloadJob?.cancel()
    }

    // -- Download --------------------------------------------------------

    private fun downloadApk(context: Context, apkUrl: String, version: String) {
        val updateDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        updateDir.mkdirs()
        val outFile = File(updateDir, "ptdl-$version.apk")

        // If the file already exists and its size matches the expected size from GitHub,
        // skip the download and go straight to installation.
        val expectedSize = UpdateChecker.apkSizeBytes
        if (outFile.exists() && expectedSize > 0L && outFile.length() == expectedSize) {
            state = State.INSTALLING
            onDownloadProgress?.invoke(-1)
            notify(context.getString(R.string.installing_update_status))
            installApk(context, outFile)
            return
        }

        downloadJob?.cancel()
        downloadJob = CoroutineScope(Dispatchers.IO).launch {
            var conn: HttpURLConnection? = null
            var success = false
            try {
                conn = URL(apkUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.requestMethod = "GET"
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    withContext(Dispatchers.Main) {
                        state = State.IDLE
                        onDownloadProgress?.invoke(-1)
                        notify(context.getString(R.string.download_failed_format, responseCode))
                    }
                    return@launch
                }

                val totalBytes = conn.contentLengthLong
                var bytesRead = 0L
                var lastReportedPercent = -1

                conn.inputStream.use { input ->
                    FileOutputStream(outFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var n: Int
                        while (input.read(buffer).also { n = it } != -1) {
                            output.write(buffer, 0, n)
                            bytesRead += n
                            if (totalBytes > 0) {
                                val percent = ((bytesRead * 100) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastReportedPercent) {
                                    lastReportedPercent = percent
                                    withContext(Dispatchers.Main) {
                                        onDownloadProgress?.invoke(percent)
                                        notify(context.getString(R.string.download_progress_format, percent))
                                    }
                                }
                            }
                        }
                    }
                }

                success = true
                withContext(Dispatchers.Main) {
                    state = State.INSTALLING
                    onDownloadProgress?.invoke(-1)
                    notify(context.getString(R.string.installing_update_status))
                    installApk(context, outFile)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state = State.IDLE
                    onDownloadProgress?.invoke(-1)
                    notify(context.getString(R.string.download_setup_failed_format, e.message))
                }
            } finally {
                conn?.disconnect()
                if (!success) outFile.delete()
            }
        }
    }

    // -- Install ---------------------------------------------------------

    private fun installApk(context: Context, file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    pendingInstallFile = file
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${context.packageName}")
                    installPermissionLauncher.launch(intent)
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            fragment.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.installation_failed_format, e.message),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun notify(message: String) {
        onStateChanged?.invoke(state, message)
    }
}
