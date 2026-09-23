@file:Suppress("unused", "UNUSED_PARAMETER")
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package androidx.biometric

import androidx.fragment.app.FragmentActivity

/*
 * The app lock, in a browser: the device's own user verification — fingerprint, face, PIN — through
 * WebAuthn's platform authenticator. A credential is made for this site the first time the lock is
 * used and asked for each time it is unlocked; nothing leaves the device and nothing is sent to a
 * server, which is exactly what `BiometricPrompt` does on the phone. Where the browser has no
 * platform authenticator, the capability reads as none and the phone's own code hides the option.
 */

private fun probeJs(done: (Boolean) -> Unit): Unit = js(
    """(function () {
        try {
            if (!window.PublicKeyCredential || !PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable) { done(false); return; }
            PublicKeyCredential.isUserVerifyingPlatformAuthenticatorAvailable().then(function (ok) { done(!!ok); }, function () { done(false); });
        } catch (e) { done(false); }
    })()""",
)

private fun verifyJs(done: (Int) -> Unit): Unit = js(
    """(function () {
        var KEY = 'applock_credential';
        function rnd(n) { var a = new Uint8Array(n); crypto.getRandomValues(a); return a; }
        function b64(buf) { return btoa(String.fromCharCode.apply(null, new Uint8Array(buf))); }
        function unb64(s) { return Uint8Array.from(atob(s), function (c) { return c.charCodeAt(0); }); }
        var stored = null; try { stored = localStorage.getItem(KEY); } catch (e) {}
        var ask = function (id) {
            return navigator.credentials.get({ publicKey: { challenge: rnd(32), timeout: 60000, userVerification: 'required',
                allowCredentials: [{ type: 'public-key', id: id, transports: ['internal'] }] } });
        };
        var p;
        if (stored) {
            p = ask(unb64(stored));
        } else {
            p = navigator.credentials.create({ publicKey: {
                challenge: rnd(32), rp: { name: document.title || 'Pro Chart' },
                user: { id: rnd(16), name: 'lock', displayName: 'Pro Chart' },
                pubKeyCredParams: [{ type: 'public-key', alg: -7 }, { type: 'public-key', alg: -257 }],
                authenticatorSelection: { authenticatorAttachment: 'platform', userVerification: 'required', residentKey: 'discouraged' },
                timeout: 60000 } }).then(function (c) { try { localStorage.setItem(KEY, b64(c.rawId)); } catch (e) {} return c; });
        }
        p.then(function () { done(0); }, function (e) { done(e && e.name === 'NotAllowedError' ? 13 : 7); });
    })()""",
)

class BiometricManager private constructor() {
    fun canAuthenticate(authenticators: Int): Int = if (available) BIOMETRIC_SUCCESS else BIOMETRIC_ERROR_NO_HARDWARE

    object Authenticators {
        const val BIOMETRIC_STRONG = 0x000F
        const val BIOMETRIC_WEAK = 0x00FF
        const val DEVICE_CREDENTIAL = 0x8000
    }

    companion object {
        const val BIOMETRIC_SUCCESS = 0
        const val BIOMETRIC_ERROR_HW_UNAVAILABLE = 1
        const val BIOMETRIC_ERROR_NONE_ENROLLED = 11
        const val BIOMETRIC_ERROR_NO_HARDWARE = 12
        const val BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED = 15
        private var available = false
        private val instance = BiometricManager()
        fun from(context: android.content.Context): BiometricManager = instance
        /** Asked once at start, because the browser answers it asynchronously and the phone asks synchronously. */
        fun probe(done: () -> Unit = {}) = probeJs { available = it; done() }
    }
}

class BiometricPrompt(
    private val activity: FragmentActivity,
    private val executor: java.util.concurrent.Executor,
    private val callback: AuthenticationCallback,
) {
    abstract class AuthenticationCallback {
        open fun onAuthenticationSucceeded(result: AuthenticationResult) {}
        open fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
        open fun onAuthenticationFailed() {}
    }

    class AuthenticationResult internal constructor()

    class PromptInfo private constructor() {
        class Builder {
            fun setTitle(title: CharSequence): Builder = this
            fun setSubtitle(subtitle: CharSequence?): Builder = this
            fun setDescription(description: CharSequence?): Builder = this
            fun setNegativeButtonText(text: CharSequence): Builder = this
            fun setAllowedAuthenticators(authenticators: Int): Builder = this
            fun setConfirmationRequired(required: Boolean): Builder = this
            fun build(): PromptInfo = PromptInfo()
        }
    }

    fun authenticate(info: PromptInfo) {
        verifyJs { code ->
            executor.execute {
                if (code == 0) callback.onAuthenticationSucceeded(AuthenticationResult())
                else callback.onAuthenticationError(code, if (code == ERROR_USER_CANCELED) "Cancelled" else "Unavailable")
            }
        }
    }

    fun cancelAuthentication() {}

    companion object {
        const val ERROR_USER_CANCELED = 10
        const val ERROR_NEGATIVE_BUTTON = 13
        const val ERROR_CANCELED = 5
    }
}
