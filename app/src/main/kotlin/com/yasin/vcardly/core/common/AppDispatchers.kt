package com.yasin.vcardly.core.common

import kotlinx.coroutines.CoroutineDispatcher

/** Injectable dispatchers so file/CPU work (OCR, export, backup) is testable. */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
