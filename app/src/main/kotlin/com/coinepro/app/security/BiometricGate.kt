package com.coinepro.app.security

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.coinepro.app.R
import com.coinepro.core.security.AppLock
import com.coinepro.core.security.LockCapability

/**
 * Asks the phone who is holding it, and holds the app closed until it answers.
 *
 * ### The three pieces and why they are separate
 *
 * [AppLock] is the policy and is pure — a unit test, not a device. [BiometricPrompt] is the
 * platform's own dialog and is the only thing here that touches hardware. This file is the joint:
 * it watches the lifecycle, asks the policy, and shows the prompt.
 *
 * ### `BIOMETRIC_STRONG or DEVICE_CREDENTIAL`, both
 *
 * A face or a fingerprint, falling back to the PIN. Two reasons, and the second is the one that
 * matters more: a reader with wet hands, a cut finger, a mask or gloves has to be able to get into
 * their own app, and biometric-only locks are the ones people rage-uninstall over. The device
 * credential is also the *same* secret the phone's own lock screen uses, so nothing here asks
 * anybody to remember a new one — this app has no PIN of its own and will not be inventing one.
 *
 * ### It never keeps anybody out
 *
 * If the phone loses its ability to authenticate — the reader removed their fingerprints, a work
 * policy changed — the challenge is skipped rather than shown. See [AppLock.shouldChallenge]. The
 * alternative is an app that cannot be opened again, which is a far worse failure than the one the
 * lock is guarding against.
 */
@Composable
fun BiometricGate(
    enabled: Boolean,
    /** Drawn while the app is locked. Nothing of the app itself is composed behind it. */
    lockedContent: @Composable (onUnlock: () -> Unit) -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findFragmentActivity()
    val capability = rememberLockCapability()

    // The activity is a `FragmentActivity` in this app; the check is here rather than assumed
    // because a preview, a screenshot render and a test all host composables in something else.
    // Without an activity there is no prompt to show, so there is no lock — never a blank screen.
    val armed = enabled && capability.usable && activity != null

    var unlockedAt by remember { mutableStateOf<Long?>(null) }
    var backgroundedAt by remember { mutableStateOf<Long?>(null) }
    var locked by remember { mutableStateOf(false) }
    var prompting by remember { mutableStateOf(false) }

    // `elapsedRealtime`, not the wall clock: it is monotonic and it counts while the phone sleeps,
    // which is exactly the interval this policy is about. The wall clock can be moved by the
    // reader or by the network, and a lock whose grace period can be extended by changing the time
    // is not a lock.
    val now = { SystemClock.elapsedRealtime() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, armed) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> backgroundedAt = now()
                Lifecycle.Event.ON_START -> {
                    if (AppLock.shouldChallenge(
                            enabled = armed,
                            capable = capability.usable,
                            unlockedAtElapsedMillis = unlockedAt,
                            nowElapsedMillis = now(),
                            backgroundedAtElapsedMillis = backgroundedAt,
                        )
                    ) {
                        locked = true
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The first frame. `ON_START` has usually already fired by the time this composes, so the cold
    // start is decided here rather than left to an observer that missed its event.
    LaunchedEffect(armed) {
        locked = AppLock.shouldChallenge(
            enabled = armed,
            capable = capability.usable,
            unlockedAtElapsedMillis = unlockedAt,
            nowElapsedMillis = now(),
            backgroundedAtElapsedMillis = backgroundedAt,
        )
    }

    val challenge: () -> Unit = challenge@{
        val host = activity ?: return@challenge
        if (prompting) return@challenge
        prompting = true
        showPrompt(
            activity = host,
            title = context.getString(R.string.lock_prompt_title),
            subtitle = context.getString(R.string.lock_prompt_subtitle),
            onSuccess = {
                prompting = false
                unlockedAt = now()
                backgroundedAt = null
                locked = false
            },
            onDismissed = { prompting = false },
        )
    }

    // Raised as soon as the app locks, so the reader is met by the prompt rather than by a screen
    // with a button on it. The button behind it is what they use if they dismiss the prompt by
    // accident — which is common enough that a locked screen with no way forward would be a trap.
    LaunchedEffect(locked) {
        if (locked) challenge()
    }

    if (locked) lockedContent(challenge) else content()
}

/**
 * What this phone can do, read once per composition.
 *
 * Deliberately not recomputed on every frame: `canAuthenticate` is a binder call into the system,
 * and the answer changes about as often as somebody enrols a fingerprint — which they do from
 * outside this app, so the reading is refreshed the next time the app starts anyway.
 */
@Composable
fun rememberLockCapability(): LockCapability {
    val context = LocalContext.current
    return remember(context) { context.lockCapability() }
}

/** The same reading, outside a composition — for the settings row and for tests of the wiring. */
fun Context.lockCapability(): LockCapability {
    val manager = BiometricManager.from(this)
    return when (manager.canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)) {
        BiometricManager.BIOMETRIC_SUCCESS -> LockCapability.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
            // Nothing enrolled *for biometrics*. The phone may still have a PIN, and a PIN is a
            // perfectly good curtain — so this asks the narrower question before giving up.
            if (manager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
                LockCapability.CREDENTIAL_ONLY
            } else {
                LockCapability.NOT_ENROLLED
            }
        }
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
        -> if (manager.canAuthenticate(DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS) {
            LockCapability.CREDENTIAL_ONLY
        } else {
            LockCapability.NONE
        }
        else -> LockCapability.NONE
    }
}

/**
 * The system's own enrolment screen, for the one capability state with an action attached.
 *
 * Best effort: the action was added in Android 11 and older phones are sent to the general
 * security settings instead, which is one tap further away but is somewhere rather than nowhere.
 * A device with neither — a stripped image — gets nothing rather than a crash.
 */
fun Context.openBiometricEnrolment() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BIOMETRIC_STRONG or DEVICE_CREDENTIAL,
        )
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    runCatching { startActivity(intent) }
}

private fun showPrompt(
    activity: FragmentActivity,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onDismissed: () -> Unit,
) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // The message is the platform's and is not shown: it is an English system string
                // on a Persian screen, and the screen behind the prompt already says what to do.
                // Every error lands here, including the reader simply pressing back, and all of
                // them mean the same thing to this app — still locked.
                onDismissed()
            }

            // Deliberately not overridden: `onAuthenticationFailed` is one *rejected* finger, not
            // the end of the attempt. The prompt is still up and the reader tries again; treating
            // it as a dismissal would tear the dialog away mid-attempt.
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        // No negative button, because `DEVICE_CREDENTIAL` supplies its own and the two are
        // mutually exclusive — setting both throws.
        .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
        .setConfirmationRequired(false)
        .build()
    runCatching { prompt.authenticate(info) }.onFailure { onDismissed() }
}

/** Walks the context wrappers to the hosting activity, or null where there is not one. */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
