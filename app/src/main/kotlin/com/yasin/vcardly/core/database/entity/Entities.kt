package com.yasin.vcardly.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.yasin.vcardly.domain.model.ContactSource
import com.yasin.vcardly.domain.model.FollowUpStatus
import com.yasin.vcardly.domain.model.FollowUpType

@Entity(
    tableName = "categories",
    indices = [Index(value = ["system_key"], unique = true)],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "system_key") val systemKey: String? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    @ColumnInfo(name = "color_argb") val colorArgb: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "contacts",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("category_id"),
        Index("is_favorite"),
        Index("created_at"),
    ],
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "full_name") val fullName: String,
    @ColumnInfo(name = "job_title") val jobTitle: String = "",
    val company: String = "",
    val phone: String = "",
    @ColumnInfo(name = "phone_alt") val phoneAlt: String = "",
    val email: String = "",
    @ColumnInfo(name = "email_alt") val emailAlt: String = "",
    val website: String = "",
    val address: String = "",
    val notes: String = "",
    @ColumnInfo(name = "category_id") val categoryId: Long? = null,
    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false,
    @ColumnInfo(name = "front_image_path") val frontImagePath: String? = null,
    @ColumnInfo(name = "back_image_path") val backImagePath: String? = null,
    val source: ContactSource = ContactSource.MANUAL,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** Many-to-many join between contacts and tags. */
@Entity(
    tableName = "contact_tags",
    primaryKeys = ["contact_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class ContactTagCrossRef(
    @ColumnInfo(name = "contact_id") val contactId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
)

@Entity(
    tableName = "follow_ups",
    foreignKeys = [
        ForeignKey(
            entity = ContactEntity::class,
            parentColumns = ["id"],
            childColumns = ["contact_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("contact_id"),
        Index(value = ["status", "due_at"]),
    ],
)
data class FollowUpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "contact_id") val contactId: Long,
    val type: FollowUpType = FollowUpType.CALL,
    val status: FollowUpStatus = FollowUpStatus.PENDING,
    val title: String,
    val notes: String = "",
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "reminder_enabled") val reminderEnabled: Boolean = true,
    @ColumnInfo(name = "reminder_offset_minutes") val reminderOffsetMinutes: Int = 0,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
    @ColumnInfo(name = "notified_at") val notifiedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

// ---- read models ----

data class ContactWithRelations(
    @Embedded val contact: ContactEntity,
    @Relation(parentColumn = "category_id", entityColumn = "id")
    val category: CategoryEntity?,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ContactTagCrossRef::class,
            parentColumn = "contact_id",
            entityColumn = "tag_id",
        ),
    )
    val tags: List<TagEntity>,
)

data class FollowUpWithContactEntity(
    @Embedded val followUp: FollowUpEntity,
    @Relation(parentColumn = "contact_id", entityColumn = "id")
    val contact: ContactEntity,
)

data class TagWithCountRow(
    @Embedded val tag: TagEntity,
    @ColumnInfo(name = "contact_count") val contactCount: Int,
)

data class CategoryCountRow(
    @ColumnInfo(name = "category_id") val categoryId: Long?,
    val count: Int,
)
