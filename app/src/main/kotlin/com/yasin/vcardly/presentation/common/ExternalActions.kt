package com.yasin.vcardly.presentation.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle

/**
 * Hand-offs to other apps. No permissions are needed: dialling opens the dialer (it never places the call), email and
 * WhatsApp open a compose screen. Returns false when no app can handle the request so the UI can say so.
 */
object ExternalActions {
    fun dial(context: Context, number: String): Boolean =
        context.startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number.trim(), null)))

    fun email(context: Context, address: String): Boolean =
        context.startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", address.trim(), null)))

    /** wa.me needs the number in international format; digits only, no leading "+" or zeros added by us. */
    fun whatsApp(context: Context, number: String): Boolean {
        val digits = whatsAppDigits(number) ?: return false
        return context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits")))
    }

    fun website(context: Context, site: String): Boolean {
        val trimmed = site.trim()
        val withScheme = if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) trimmed else "https://$trimmed"
        return context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(withScheme)))
    }

    fun map(context: Context, address: String): Boolean =
        context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(address.trim()))))

    /**
     * Copies [text] and marks it sensitive so Android 13+ does not show it in the clipboard preview overlay.
     * Returns true when the system shows its own "Copied" confirmation (Android 13+), so the app does not double it.
     */
    fun copy(context: Context, label: String, text: String): Boolean {
        val manager = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
        }
        manager.setPrimaryClip(clip)
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    private fun Context.startSafely(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/** Digits for a wa.me link (7 to 15 digits), or null when the number cannot be used. Pure, unit-tested. */
fun whatsAppDigits(number: String): String? {
    val digits = number.filter { it.isDigit() }.trimStart('0')
    return digits.takeIf { it.length in 7..15 }
}
