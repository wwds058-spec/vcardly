package com.yasin.vcardly.presentation.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasin.vcardly.core.backup.BackupFailure
import com.yasin.vcardly.core.backup.BackupResult
import com.yasin.vcardly.core.backup.BackupService
import com.yasin.vcardly.core.backup.RestoreMode
import com.yasin.vcardly.core.backup.RestoreSummary
import com.yasin.vcardly.core.cloud.CloudBackupProvider
import com.yasin.vcardly.domain.backup.BackupCounts
import com.yasin.vcardly.domain.backup.BackupManifest
import com.yasin.vcardly.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CreateState {
    data object Idle : CreateState
    data object Working : CreateState
    data class Done(val counts: BackupCounts) : CreateState
    data class Failed(val reason: BackupFailure) : CreateState
}

sealed interface RestoreState {
    data object Idle : RestoreState
    data object Working : RestoreState
    /** The chosen file is encrypted. [wrong] is true after a failed attempt. */
    data class NeedsPassword(val wrong: Boolean) : RestoreState
    data class Ready(val manifest: BackupManifest) : RestoreState
    data class Done(val summary: RestoreSummary, val mode: RestoreMode) : RestoreState
    data class Failed(val reason: BackupFailure) : RestoreState
}

data class BackupUiState(
    val lastBackupAt: Long? = null,
    val create: CreateState = CreateState.Idle,
    val restore: RestoreState = RestoreState.Idle,
    val driveConfigured: Boolean = false,
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val service: BackupService,
    prefs: PreferencesRepository,
    cloud: CloudBackupProvider,
) : ViewModel() {
    private val create = MutableStateFlow<CreateState>(CreateState.Idle)
    private val restore = MutableStateFlow<RestoreState>(RestoreState.Idle)

    // Secrets stay out of observable state and are wiped as soon as they are no longer needed.
    private var pendingUri: Uri? = null
    private var pendingPassword: CharArray? = null

    val uiState: StateFlow<BackupUiState> = combine(prefs.lastBackupAt, create, restore) { last, c, r ->
        BackupUiState(last, c, r, driveConfigured = cloud.isConfigured)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupUiState(driveConfigured = cloud.isConfigured))

    /** Minimum length for backup passwords. */
    val minPasswordLength = MIN_PASSWORD

    fun createBackup(uri: Uri, password: CharArray?) {
        if (password != null && password.size < MIN_PASSWORD) { create.value = CreateState.Failed(BackupFailure.IO); return }
        create.value = CreateState.Working
        viewModelScope.launch {
            create.value = when (val r = service.create(uri, password)) {
                is BackupResult.Success -> CreateState.Done(r.value)
                is BackupResult.Failure -> CreateState.Failed(r.reason)
            }
            password?.fill('\u0000')
        }
    }

    fun chooseRestoreFile(uri: Uri) {
        wipe()
        pendingUri = uri
        inspect(null)
    }

    fun submitPassword(password: CharArray) {
        pendingPassword?.fill('\u0000')
        pendingPassword = password
        inspect(password)
    }

    private fun inspect(password: CharArray?) {
        val uri = pendingUri ?: return
        val wasAskingForPassword = restore.value is RestoreState.NeedsPassword
        restore.value = RestoreState.Working
        viewModelScope.launch {
            restore.value = when (val r = service.inspect(uri, password)) {
                is BackupResult.Success -> RestoreState.Ready(r.value)
                is BackupResult.Failure -> when (r.reason) {
                    BackupFailure.NEEDS_PASSWORD -> RestoreState.NeedsPassword(wrong = false)
                    BackupFailure.WRONG_PASSWORD -> if (password != null || wasAskingForPassword) RestoreState.NeedsPassword(wrong = true) else RestoreState.Failed(r.reason)
                    else -> RestoreState.Failed(r.reason)
                }
            }
        }
    }

    fun restore(mode: RestoreMode) {
        val uri = pendingUri ?: return
        restore.value = RestoreState.Working
        viewModelScope.launch {
            restore.value = when (val r = service.restore(uri, pendingPassword, mode)) {
                is BackupResult.Success -> RestoreState.Done(r.value, mode)
                is BackupResult.Failure -> RestoreState.Failed(r.reason)
            }
            wipe(keepState = true)
        }
    }

    fun resetRestore() = wipe()

    private fun wipe(keepState: Boolean = false) {
        pendingPassword?.fill('\u0000'); pendingPassword = null
        pendingUri = null
        if (!keepState) restore.update { RestoreState.Idle }
    }

    override fun onCleared() = wipe(keepState = true)

    private companion object {
        const val MIN_PASSWORD = 8
    }
}
