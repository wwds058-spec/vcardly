package com.yasin.vcardly.presentation.organize

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.VCardlySectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.presentation.common.displayName

/** What the name dialog is editing. id = 0 means "create". */
private sealed interface NameDialog {
    data object NewCategory : NameDialog
    data class RenameCategory(val category: Category) : NameDialog
    data class RenameTag(val tag: TagWithCount) : NameDialog
}

private sealed interface DeleteDialog {
    data class DeleteCategory(val category: Category) : DeleteDialog
    data class DeleteTag(val tag: TagWithCount) : DeleteDialog
}

@Composable
fun OrganizeScreen(onNavigateUp: () -> Unit, viewModel: OrganizeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var deleteDialog by remember { mutableStateOf<DeleteDialog?>(null) }

    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.organize_title), onNavigateUp = onNavigateUp)
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = MaterialTheme.spacing.md)) {
            item(key = "h-categories") { VCardlySectionHeader(stringResource(R.string.organize_categories)) }
            items(state.categories, key = { "c${it.id}" }) { category ->
                ItemRow(
                    title = category.displayName().asString(),
                    subtitle = null,
                    swatch = Color(category.colorArgb),
                    // Seeded categories are fixed; only custom ones can be renamed or deleted.
                    onEdit = if (category.systemCategory == null) ({ nameDialog = NameDialog.RenameCategory(category) }) else null,
                    onDelete = if (category.systemCategory == null) ({ deleteDialog = DeleteDialog.DeleteCategory(category) }) else null,
                )
            }
            item(key = "add-category") {
                VCardlyTextButton(
                    text = stringResource(R.string.organize_add_category),
                    onClick = { nameDialog = NameDialog.NewCategory },
                )
            }

            item(key = "h-tags") { VCardlySectionHeader(stringResource(R.string.organize_tags)) }
            if (state.tags.isEmpty()) {
                item(key = "no-tags") {
                    Text(
                        stringResource(R.string.organize_no_tags),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = MaterialTheme.spacing.sm),
                    )
                }
            }
            items(state.tags, key = { "t${it.tag.id}" }) { row ->
                ItemRow(
                    title = "#${row.tag.name}",
                    subtitle = pluralStringResource(R.plurals.organize_contact_count, row.contactCount, row.contactCount),
                    swatch = null,
                    onEdit = { nameDialog = NameDialog.RenameTag(row) },
                    onDelete = { deleteDialog = DeleteDialog.DeleteTag(row) },
                )
            }
        }
    }

    nameDialog?.let { dialog ->
        val initial = when (dialog) {
            NameDialog.NewCategory -> ""
            is NameDialog.RenameCategory -> dialog.category.name
            is NameDialog.RenameTag -> dialog.tag.tag.name
        }
        NameEditDialog(
            title = stringResource(if (dialog is NameDialog.NewCategory) R.string.organize_add_category else R.string.organize_rename),
            initial = initial,
            onDismiss = { nameDialog = null },
            onSubmit = { name, reportError ->
                val done: (NameError?) -> Unit = { error -> if (error == null) nameDialog = null else reportError(error) }
                when (dialog) {
                    NameDialog.NewCategory -> viewModel.saveCategory(0, name, done)
                    is NameDialog.RenameCategory -> viewModel.saveCategory(dialog.category.id, name, done)
                    is NameDialog.RenameTag -> viewModel.renameTag(dialog.tag.tag.id, name, done)
                }
            },
        )
    }

    deleteDialog?.let { dialog ->
        val (name, message) = when (dialog) {
            is DeleteDialog.DeleteCategory -> dialog.category.name to stringResource(R.string.organize_delete_category_message, dialog.category.name)
            is DeleteDialog.DeleteTag -> dialog.tag.tag.name to stringResource(R.string.organize_delete_tag_message, dialog.tag.tag.name)
        }
        ConfirmDialog(
            title = stringResource(R.string.organize_delete_title, name),
            message = message,
            confirmText = stringResource(R.string.organize_delete),
            onConfirm = {
                when (dialog) {
                    is DeleteDialog.DeleteCategory -> viewModel.deleteCategory(dialog.category.id)
                    is DeleteDialog.DeleteTag -> viewModel.deleteTag(dialog.tag.tag.id)
                }
                deleteDialog = null
            },
            onDismiss = { deleteDialog = null },
        )
    }
}

@Composable
private fun ItemRow(
    title: String,
    subtitle: String?,
    swatch: Color?,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    val editLabel = stringResource(R.string.organize_rename_item, title)
    val deleteLabel = stringResource(R.string.organize_delete_item, title)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) Box(Modifier.size(12.dp).clip(CircleShape).background(swatch))
        Column(Modifier.weight(1f).padding(start = if (swatch != null) MaterialTheme.spacing.md else 0.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = editLabel) }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = deleteLabel) }
    }
}

@Composable
private fun NameEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (name: String, reportError: (NameError) -> Unit) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<NameError?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                singleLine = true,
                label = { Text(stringResource(R.string.organize_name_label)) },
                isError = error != null,
                supportingText = error?.let { { Text(stringResource(it.messageRes())) } },
            )
        },
        confirmButton = { VCardlyTextButton(stringResource(R.string.common_save), onClick = { onSubmit(text) { error = it } }) },
        dismissButton = { VCardlyTextButton(stringResource(R.string.common_cancel), onClick = onDismiss) },
    )
}

private fun NameError.messageRes(): Int = when (this) {
    NameError.BLANK -> R.string.error_required
    NameError.TOO_LONG -> R.string.error_too_long
    NameError.DUPLICATE -> R.string.error_duplicate
}
