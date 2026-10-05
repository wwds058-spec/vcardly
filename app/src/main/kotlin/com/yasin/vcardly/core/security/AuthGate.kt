package com.yasin.vcardly.core.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class AuthAvailability {
    Available,
    /** No biometrics enrolled and no screen lock, or no hardware: the OS cannot authenticate the user. */
    NotSetUp,
    /** Might work later (hardware busy, security update pending). */
    Temporary,
}

sealed interface AuthResult {
    data object Success : AuthResult
    /** The user dismissed the prompt. */
    data object Cancelled : AuthResult
    data object Error : AuthResult
}

/** Asks the OS to authenticate the user (fingerprint / face / device PIN, pattern or password). */
interface AuthGate {
    fun availability(): AuthAvailability

    /** Shows the system prompt. A second call while one is showing is ignored. */
    fun authenticate(title: String, onResult: (AuthResult) -> Unit)
}

/**
 * BiometricPrompt-backed gate. Must be created in the activity's onCreate. Biometric data never reaches the app; it only
 * receives "succeeded" or an error. This is a UI gate, not encryption: stored data is protected by Android's app sandbox
 * and device encryption, and is not tied to this authentication.
 */
class BiometricGate(private val activity: FragmentActivity) : AuthGate {
    private var pending: ((AuthResult) -> Unit)? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = deliver(AuthResult.Success)

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = deliver(
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON, BiometricPrompt.ERROR_CANCELED -> AuthResult.Cancelled
                    else -> AuthResult.Error
                },
            )

            // A non-matching attempt: the system prompt stays open and lets the user retry.
            override fun onAuthenticationFailed() = Unit
        },
    )

    override fun availability(): AuthAvailability = when (BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS)) {
        BiometricManager.BIOMETRIC_SUCCESS -> AuthAvailability.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
        -> AuthAvailability.NotSetUp
        else -> AuthAvailability.Temporary
    }

    override fun authenticate(title: String, onResult: (AuthResult) -> Unit) {
        if (pending != null) return
        pending = onResult
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build() // no negative button: with DEVICE_CREDENTIAL the system supplies its own
        prompt.authenticate(info)
    }

    private fun deliver(result: AuthResult) {
        val callback = pending
        pending = null
        callback?.invoke(result)
    }

    private companion object {
        /** Fingerprint/face, with the device PIN/pattern/password as the fallback. */
        const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
