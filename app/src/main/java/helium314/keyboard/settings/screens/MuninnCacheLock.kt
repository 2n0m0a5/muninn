// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import helium314.keyboard.latin.R

/**
 * Muninn: the fingerprint (or screen lock) gate in front of the cache in the app.
 * Once unlocked it stays open for a few minutes, so moving between screens doesn't ask again and again.
 */
object MuninnCacheLock {
    private const val OPEN_FOR_MS = 5 * 60 * 1000L
    private var unlockedAt = 0L

    fun isUnlocked() = System.currentTimeMillis() - unlockedAt < OPEN_FOR_MS

    fun ask(context: Context, onUnlocked: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val executor = context.mainExecutor
        val builder = BiometricPrompt.Builder(context)
            .setTitle(context.getString(R.string.muninn_locked))
            .setSubtitle(context.getString(R.string.muninn_cache_title))
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
                        or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
            else -> builder.setNegativeButton(context.getString(android.R.string.cancel), executor) { _, _ -> }
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                unlockedAt = System.currentTimeMillis()
                onUnlocked()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                // the phone has no fingerprint and no screen lock: there is nothing to check against, so let the owner in
                if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS || errorCode == BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && errorCode == BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL)) {
                    unlockedAt = System.currentTimeMillis()
                    onUnlocked()
                }
            }
        }
        try {
            builder.build().authenticate(CancellationSignal(), executor, callback)
        } catch (_: Exception) { }
    }
}
