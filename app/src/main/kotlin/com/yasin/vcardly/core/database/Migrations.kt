package com.yasin.vcardly.core.database

import androidx.room.migration.Migration

/**
 * Every schema change adds a Migration here AND a schema JSON under app/schemas.
 * Destructive fallback is deliberately NOT enabled: contacts are irreplaceable user data.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()
