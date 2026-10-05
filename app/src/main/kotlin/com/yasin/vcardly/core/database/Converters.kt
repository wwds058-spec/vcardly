package com.yasin.vcardly.core.database

import androidx.room.TypeConverter
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType

/**
 * Enums are stored by name. Unknown values (e.g. from a newer backup) fall back to a
 * safe default instead of crashing the whole query.
 */
class Converters {
    @TypeConverter fun contactSourceToString(v: ContactSource): String = v.name
    @TypeConverter fun stringToContactSource(s: String): ContactSource =
        ContactSource.entries.firstOrNull { it.name == s } ?: ContactSource.MANUAL

    @TypeConverter fun followUpTypeToString(v: FollowUpType): String = v.name
    @TypeConverter fun stringToFollowUpType(s: String): FollowUpType =
        FollowUpType.entries.firstOrNull { it.name == s } ?: FollowUpType.OTHER

    @TypeConverter fun followUpStatusToString(v: FollowUpStatus): String = v.name
    @TypeConverter fun stringToFollowUpStatus(s: String): FollowUpStatus =
        FollowUpStatus.entries.firstOrNull { it.name == s } ?: FollowUpStatus.PENDING
}
