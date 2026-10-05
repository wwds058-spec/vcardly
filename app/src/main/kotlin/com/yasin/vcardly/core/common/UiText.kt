package com.yasin.vcardly.core.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * User-facing text that ViewModels can emit without holding a Context and
 * without hardcoding strings. [Dynamic] is only for user-entered data.
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText
    data class Dynamic(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(id, *args.toTypedArray())
        is Dynamic -> value
    }

    fun asString(context: Context): String = when (this) {
        is Resource -> context.getString(id, *args.toTypedArray())
        is Dynamic -> value
    }

    companion object {
        fun of(@StringRes id: Int, vararg args: Any): UiText = Resource(id, args.toList())
    }
}
