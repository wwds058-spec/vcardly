package com.yasin.vcardly.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.yasin.vcardly.R
import com.yasin.vcardly.core.designsystem.theme.CardShape

/**
 * Text field with its label above the input (large, readable, like a modern form) and the error message both shown and
 * exposed to screen readers. [required] adds a visual asterisk; the label text itself still names the field.
 */
@Composable
fun VCardlyTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    required: Boolean = false,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier) {
        Text(
            buildAnnotatedString {
                append(label)
                if (required) withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append(" *") }
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            leadingIcon = leadingIcon?.let { { androidx.compose.material3.Icon(it, contentDescription = null) } },
            trailingIcon = trailing,
            isError = errorText != null,
            supportingText = errorText?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                errorContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics {
                    // The visual label sits outside the field, so name the field for TalkBack here.
                    contentDescription = label
                    if (errorText != null) error(errorText)
                },
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { androidx.compose.material3.Icon(it, contentDescription = null) } },
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        shape = CardShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            VCardlyTextButton(
                text = confirmText,
                onClick = onConfirm,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        dismissButton = { VCardlyTextButton(text = stringResource(R.string.common_cancel), onClick = onDismiss, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}
