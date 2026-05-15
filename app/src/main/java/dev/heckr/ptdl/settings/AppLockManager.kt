package dev.heckr.ptdl.settings

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

object AppLockManager {

    const val TYPE_DEVICE = "device"
    const val TYPE_PIN = "pin"

    var isSessionUnlocked = false
        private set

    fun markUnlocked() {
        isSessionUnlocked = true
    }

    fun lockSession() {
        isSessionUnlocked = false
    }

    fun isEnabled(context: Context): Boolean =
        SettingsManager(context).getBoolean(SettingsManager.KEY_APP_LOCK_ENABLED)

    fun getLockType(context: Context): String =
        SettingsManager(context).getString(SettingsManager.KEY_APP_LOCK_TYPE, TYPE_DEVICE)

    fun canUseDeviceLock(context: Context): Boolean {
        val result = BiometricManager.from(context)
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun enable(context: Context, type: String) {
        val sm = SettingsManager(context)
        sm.putBoolean(SettingsManager.KEY_APP_LOCK_ENABLED, true)
        sm.putString(SettingsManager.KEY_APP_LOCK_TYPE, type)
    }

    fun disable(context: Context) {
        val sm = SettingsManager(context)
        sm.putBoolean(SettingsManager.KEY_APP_LOCK_ENABLED, false)
        sm.remove(SettingsManager.KEY_APP_LOCK_TYPE)
        sm.remove(SettingsManager.KEY_APP_LOCK_PIN_HASH)
    }

    fun setupPin(context: Context, pin: String) {
        SettingsManager(context).putString(SettingsManager.KEY_APP_LOCK_PIN_HASH, hashPin(pin))
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = SettingsManager(context).getString(SettingsManager.KEY_APP_LOCK_PIN_HASH)
        return stored.isNotBlank() && stored == hashPin(pin)
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                markUnlocked()
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                if (!silent) onError?.invoke(errString.toString())
            }

            override fun onAuthenticationFailed() { /* wrong attempt — prompt stays open */ }
        })

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(dev.heckr.ptdl.R.string.lock_prompt_title))
            .setSubtitle(activity.getString(dev.heckr.ptdl.R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(info)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
