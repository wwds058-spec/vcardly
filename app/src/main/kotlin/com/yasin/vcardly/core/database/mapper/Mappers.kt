package com.yasin.vcardly.core.database.mapper

import com.yasin.vcardly.core.database.entity.CategoryEntity
import com.yasin.vcardly.core.database.entity.ContactEntity
import com.yasin.vcardly.core.database.entity.ContactWithRelations
import com.yasin.vcardly.core.database.entity.FollowUpEntity
import com.yasin.vcardly.core.database.entity.FollowUpWithContactEntity
import com.yasin.vcardly.core.database.entity.TagEntity
import com.yasin.vcardly.core.database.entity.TagWithCountRow
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.Contact
import com.yasin.vcardly.domain.model.ContactDetails
import com.yasin.vcardly.domain.model.FollowUp
import com.yasin.vcardly.domain.model.FollowUpWithContact
import com.yasin.vcardly.domain.model.SystemCategory
import com.yasin.vcardly.domain.model.Tag
import com.yasin.vcardly.domain.model.TagWithCount

fun ContactEntity.toDomain() = Contact(
    id = id, fullName = fullName, jobTitle = jobTitle, company = company,
    phone = phone, phoneAlt = phoneAlt, email = email, emailAlt = emailAlt,
    website = website, address = address, notes = notes, categoryId = categoryId,
    isFavorite = isFavorite, frontImagePath = frontImagePath, backImagePath = backImagePath,
    source = source, createdAt = createdAt, updatedAt = updatedAt,
)

fun Contact.toEntity() = ContactEntity(
    id = id, fullName = fullName.trim(), jobTitle = jobTitle.trim(), company = company.trim(),
    phone = phone.trim(), phoneAlt = phoneAlt.trim(), email = email.trim(), emailAlt = emailAlt.trim(),
    website = website.trim(), address = address.trim(), notes = notes.trim(), categoryId = categoryId,
    isFavorite = isFavorite, frontImagePath = frontImagePath, backImagePath = backImagePath,
    source = source, createdAt = createdAt, updatedAt = updatedAt,
)

fun CategoryEntity.toDomain() = Category(
    id = id, name = name, colorArgb = colorArgb,
    systemCategory = SystemCategory.fromKey(systemKey), sortOrder = sortOrder,
)

fun TagEntity.toDomain() = Tag(id = id, name = name, colorArgb = colorArgb)

fun TagWithCountRow.toDomain() = TagWithCount(tag.toDomain(), contactCount)

fun ContactWithRelations.toDomain() = ContactDetails(
    contact = contact.toDomain(),
    category = category?.toDomain(),
    tags = tags.map { it.toDomain() }.sortedBy { it.name.lowercase() },
)

fun FollowUpEntity.toDomain() = FollowUp(
    id = id, contactId = contactId, type = type, status = status, title = title, notes = notes,
    dueAt = dueAt, reminderEnabled = reminderEnabled, reminderOffsetMinutes = reminderOffsetMinutes,
    completedAt = completedAt, notifiedAt = notifiedAt, createdAt = createdAt, updatedAt = updatedAt,
)

fun FollowUp.toEntity() = FollowUpEntity(
    id = id, contactId = contactId, type = type, status = status, title = title.trim(), notes = notes.trim(),
    dueAt = dueAt, reminderEnabled = reminderEnabled, reminderOffsetMinutes = reminderOffsetMinutes,
    completedAt = completedAt, notifiedAt = notifiedAt, createdAt = createdAt, updatedAt = updatedAt,
)

fun FollowUpWithContactEntity.toDomain() = FollowUpWithContact(
    followUp = followUp.toDomain(),
    contactName = contact.fullName,
    contactCompany = contact.company,
)
