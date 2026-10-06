package com.yasin.vcardly.presentation.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.ConfirmDialog
import com.yasin.vcardly.core.designsystem.component.IconBadge
import com.yasin.vcardly.core.designsystem.component.VCardlyGroup
import com.yasin.vcardly.core.designsystem.component.VCardlyNotice
import com.yasin.vcardly.core.designsystem.component.VCardlyOverline
import com.yasin.vcardly.core.designsystem.component.VCardlyTextButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.core.designsystem.component.VCardlyTonalButton
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.Tone
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.theme.vcColors
import com.yasin.vcardly.domain.model.Category
import com.yasin.vcardly.domain.model.TagWithCount
import com.yasin.vcardly.presentation.common.displayName

/** What the name dialog is editing. */
private sealed interface NameDialog {
    data object NewCategory : NameDialog
    data class RenameCategory(val category: Category) : NameDialog
    data class RenameTag(val tag: TagWithCount) : NameDialog
}

private sealed interface DeleteDialog {
    data class DeleteCategory(val category: Category) : DeleteDialog
    data class DeleteTag(val tag: TagWithCount) : DeleteDialog
}

class OrganizeActions(
    val onNavigateUp: () -> Unit = {},
    val onAddCategory: () -> Unit = {},
    val onRenameCategory: (Category) -> Unit = {},
    val onDeleteCategory: (Category) -> Unit = {},
    val onRenameTag: (TagWithCount) -> Unit = {},
    val onDeleteTag: (TagWithCount) -> Unit = {},
)

@Composable
fun OrganizeScreen(onNavigateUp: () -> Unit, viewModel: OrganizeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var nameDialog by remember { mutableStateOf<NameDialog?>(null) }
    var deleteDialog by remember { mutableStateOf<DeleteDialog?>(null) }

    OrganizeContent(
        state,
        OrganizeActions(
            onNavigateUp = onNavigateUp,
            onAddCategory = { nameDialog = NameDialog.NewCategory },
            onRenameCategory = { nameDialog = NameDialog.RenameCategory(it) },
            onDeleteCategory = { deleteDialog = DeleteDialog.DeleteCategory(it) },
            onRenameTag = { nameDialog = NameDialog.RenameTag(it) },
            onDeleteTag = { deleteDialog = DeleteDialog.DeleteTag(it) },
        ),
    )

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
            destructive = true,
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

/** Stateless categories-and-tags screen (used directly by UI tests). */
@Composable
fun OrganizeContent(state: OrganizeUiState, actions: OrganizeActions) {
    val colors = MaterialTheme.vcColors
    Column(Modifier.fillMaxSize()) {
        VCardlyTopBar(title = stringResource(R.string.organize_title), onNavigateUp = actions.onNavigateUp)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = MaterialTheme.spacing.screen).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.organize_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VCardlyOverline(stringResource(R.string.organize_categories), Modifier.padding(top = 8.dp))
            VCardlyGroup {
                state.categories.forEach { category ->
                    val custom = category.systemCategory == null
                    val name = category.displayName().asString()
                    val count = state.categoryCounts[category.id] ?: 0
                    ItemRow(
                        icon = Icons.Rounded.Category,
                        tone = categoryTone(Color(category.colorArgb)),
                        title = name,
                        subtitle = pluralStringResource(R.plurals.organize_contact_count, count, count).let {
                            if (custom) it else stringResource(R.string.organize_count_built_in, it)
                        },
                        onEdit = if (custom) ({ actions.onRenameCategory(category) }) else null,
                        onDelete = if (custom) ({ actions.onDeleteCategory(category) }) else null,
                    )
                }
            }
            VCardlyTonalButton(
                stringResource(R.string.organize_add_category),
                onClick = actions.onAddCategory,
                leadingIcon = Icons.Rounded.Add,
                modifier = Modifier.fillMaxWidth(),
            )
            VCardlyNotice(stringResource(R.string.organize_built_in_hint), colors.blue, Icons.Rounded.Lock)

            VCardlyOverline(stringResource(R.string.organize_tags), Modifier.padding(top = 12.dp))
            if (state.loaded && state.tags.isEmpty()) {
                VCardlyNotice(stringResource(R.string.organize_no_tags), colors.lavender, Icons.Rounded.Info)
            } else {
                VCardlyGroup {
                    state.tags.forEach { row ->
                        val title = "#${row.tag.name}"
                        ItemRow(
                            icon = Icons.AutoMirrored.Rounded.Label,
                            tone = colors.lavender,
                            title = title,
                            subtitle = pluralStringResource(R.plurals.organize_contact_count, row.contactCount, row.contactCount),
                            onEdit = { actions.onRenameTag(row) },
                            onDelete = { actions.onDeleteTag(row) },
                        )
                    }
                }
            }
        }
    }
}

/** A category's own colour as an icon tone (the name is always shown next to it, so colour is never the only cue). */
@Composable
private fun categoryTone(color: Color): Tone = Tone(container = color.copy(alpha = 0.16f), content = MaterialTheme.colorScheme.onSurface, accent = color)

@Composable
private fun ItemRow(
    icon: ImageVector,
    tone: Tone,
    title: String,
    subtitle: String,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = MaterialTheme.spacing.card, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconBadge(icon, tone, size = 40.dp, circle = true)
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val editLabel = stringResource(R.string.organize_rename_item, title)
        val deleteLabel = stringResource(R.string.organize_delete_item, title)
        if (onEdit != null) {
            IconButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = editLabel }) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.semantics { contentDescription = deleteLabel }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
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
            VCardlyTextField(
                text, { text = it; error = null }, stringResource(R.string.organize_name_label),
                errorText = error?.let { stringResource(it.messageRes()) },
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
