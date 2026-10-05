package com.yasin.vcardly.domain.backup

import kotlinx.serialization.Serializable

/**
 * "vcardly-backup-v1". Every field after the required ones has a default and unknown keys are ignored on read, so a
 * future v1.x can add fields without breaking older readers. A breaking change must bump [FORMAT_VERSION] and the format id.
 */
object BackupFormat {
    const val ID = "vcardly-backup-v1"
    const val FORMAT_VERSION = 1
    const val MANIFEST = "manifest.json"
    const val DATA = "data.json"
    const val IMAGES_DIR = "images/"
    /** File extension suggested for backups. */
    const val EXTENSION = "vcbackup"
}

@Serializable
data class BackupCounts(val contacts: Int = 0, val categories: Int = 0, val tags: Int = 0, val followUps: Int = 0, val images: Int = 0)

@Serializable
data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val createdAt: Long = 0,
    val appVersion: String = "",
    val encrypted: Boolean = false,
    val counts: BackupCounts = BackupCounts(),
    /** Entry name -> lowercase hex SHA-256 of that entry's bytes (data.json and every image). */
    val files: Map<String, String> = emptyMap(),
)

@Serializable
data class BCategory(val id: Long, val name: String = "", val colorArgb: Long = 0xFF607D8B, val systemKey: String? = null, val sortOrder: Int = 0, val createdAt: Long = 0)

@Serializable
data class BTag(val id: Long, val name: String, val colorArgb: Long? = null, val createdAt: Long = 0)

@Serializable
data class BContact(
    val id: Long,
    val fullName: String,
    val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    val phoneAlt: String = "",
    val email: String = "",
    val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    val categoryId: Long? = null,
    val isFavorite: Boolean = false,
    /** File name under images/ (not a path into the device). */
    val frontImage: String? = null,
    val backImage: String? = null,
    val source: String = "MANUAL",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val tagIds: List<Long> = emptyList(),
)

@Serializable
data class BFollowUp(
    val id: Long,
    val contactId: Long,
    val title: String,
    val type: String = "OTHER",
    val status: String = "PENDING",
    val notes: String = "",
    val dueAt: Long = 0,
    val reminderEnabled: Boolean = true,
    val reminderOffsetMinutes: Int = 0,
    val completedAt: Long? = null,
    val notifiedAt: Long? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

@Serializable
data class BMyCard(
    val fullName: String = "", val jobTitle: String = "", val company: String = "", val phone: String = "", val phoneAlt: String = "",
    val email: String = "", val emailAlt: String = "", val website: String = "", val address: String = "",
)

@Serializable
data class BackupData(
    val categories: List<BCategory> = emptyList(),
    val tags: List<BTag> = emptyList(),
    val contacts: List<BContact> = emptyList(),
    val followUps: List<BFollowUp> = emptyList(),
    val myCard: BMyCard? = null,
)
