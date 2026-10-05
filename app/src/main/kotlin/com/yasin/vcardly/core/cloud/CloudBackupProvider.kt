package com.yasin.vcardly.core.cloud

import java.io.File

data class CloudBackupInfo(val id: String, val name: String, val modifiedAt: Long, val sizeBytes: Long)

sealed interface CloudResult<out T> {
    data class Success<T>(val value: T) : CloudResult<T>
    /** The build has no Google Cloud OAuth setup. See docs/GOOGLE_DRIVE_SETUP.md. */
    data object NotConfigured : CloudResult<Nothing>
    data object SignInRequired : CloudResult<Nothing>
    data class Failure(val message: String) : CloudResult<Nothing>
}

/**
 * Seam for cloud backup. Only ever hand it an ENCRYPTED backup file (BackupArchive with a password): the file leaves the
 * device, and contact data is personal data.
 *
 * Today the only implementation is [UnconfiguredDriveProvider]. A real Google Drive implementation needs a Google Cloud
 * project, an OAuth consent screen and an Android OAuth client bound to this app's signing key; none of that exists in this
 * repository, so nothing here pretends otherwise. Steps are in docs/GOOGLE_DRIVE_SETUP.md.
 */
interface CloudBackupProvider {
    val isConfigured: Boolean
    suspend fun upload(encryptedBackup: File): CloudResult<CloudBackupInfo>
    suspend fun listBackups(): CloudResult<List<CloudBackupInfo>>
    suspend fun download(id: String, target: File): CloudResult<Unit>
}

/** Honest placeholder: reports "not configured" for everything and never touches the network. */
class UnconfiguredDriveProvider : CloudBackupProvider {
    override val isConfigured: Boolean = false
    override suspend fun upload(encryptedBackup: File) = CloudResult.NotConfigured
    override suspend fun listBackups() = CloudResult.NotConfigured
    override suspend fun download(id: String, target: File) = CloudResult.NotConfigured
}
