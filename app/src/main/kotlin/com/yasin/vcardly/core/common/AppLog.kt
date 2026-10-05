package com.yasin.vcardly.core.common

import android.util.Log
import com.yasin.vcardly.BuildConfig

/**
 * The only logging entry point. Debug builds only.
 *
 * PRIVACY RULE: never pass contact data (names, phones, emails, OCR text, file
 * paths containing names) to this class. Log operation names and numeric ids/counts.
 */
object AppLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.d(tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) Log.e(tag, message, throwable)
    }
}
