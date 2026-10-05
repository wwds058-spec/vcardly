package com.yasin.vcardly.presentation.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.component.VCardlyEmptyState
import com.yasin.vcardly.core.designsystem.component.SectionHeader
import com.yasin.vcardly.core.designsystem.component.VCardlyTopBar
import com.yasin.vcardly.core.designsystem.theme.spacing
import com.yasin.vcardly.core.designsystem.component.VCardlyAvatar
import com.yasin.vcardly.presentation.followups.labelRes
import java.text.DateFormat
import java.util.Date

@Composable
fun SearchScreen(
    onNavigateUp: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenFollowUp: (Long) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().navigationBarsPadding().imePadding()) {
        VCardlyTopBar(title = stringResource(R.string.search_title), onNavigateUp = onNavigateUp)
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.contacts_clear_search)) }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.md).focusRequester(focus),
        )
        when {
            state.isBlank -> VCardlyEmptyState(Icons.Filled.Search, stringResource(R.string.search_prompt_title), stringResource(R.string.search_prompt_message))
            state.isEmpty -> VCardlyEmptyState(Icons.Filled.Search, stringResource(R.string.contacts_no_results_title), stringResource(R.string.search_no_results_message))
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (state.contacts.isNotEmpty()) {
                    item(key = "h-contacts") {
                        SectionHeader(pluralStringResource(R.plurals.search_contacts_header, state.contacts.size, state.contacts.size), Modifier.padding(horizontal = MaterialTheme.spacing.md))
                    }
                    items(state.contacts, key = { "c${it.contact.id}" }) { d ->
                        val c = d.contact
                        val sub = listOf(c.jobTitle, c.company).filter { it.isNotBlank() }.joinToString(" · ")
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpenContact(c.id) }
                                .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            VCardlyAvatar(c.fullName, d.category?.colorArgb)
                            Column(Modifier.padding(start = MaterialTheme.spacing.md)) {
                                Text(c.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (state.followUps.isNotEmpty()) {
                    item(key = "h-followups") {
                        SectionHeader(pluralStringResource(R.plurals.search_followups_header, state.followUps.size, state.followUps.size), Modifier.padding(horizontal = MaterialTheme.spacing.md))
                    }
                    items(state.followUps, key = { "f${it.followUp.id}" }) { item ->
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { onOpenFollowUp(item.followUp.id) }
                                .padding(horizontal = MaterialTheme.spacing.md, vertical = MaterialTheme.spacing.sm),
                        ) {
                            Text(item.followUp.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.followup_row_subtitle, stringResource(item.followUp.type.labelRes()), item.contactName),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            Text(dateFormat.format(Date(item.followUp.dueAt)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}
