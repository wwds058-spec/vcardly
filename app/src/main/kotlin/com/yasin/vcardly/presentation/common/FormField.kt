package com.yasin.vcardly.presentation.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.yasin.vcardly.core.designsystem.component.VCardlyTextField
import com.yasin.vcardly.domain.usecase.ContactField

@Composable
fun FormField(
    field: ContactField,
    @androidx.annotation.StringRes label: Int,
    value: String,
    errors: Map<ContactField, com.yasin.vcardly.core.common.AppError.Validation.Reason>,
    keyboard: KeyboardType = KeyboardType.Text,
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit,
) {
    VCardlyTextField(
        value = value,
        onValueChange = onValueChange,
        label = stringResource(label),
        errorText = errors[field]?.let { stringResource(validationMessageRes(field, it)) },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboard,
            capitalization = caps,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

