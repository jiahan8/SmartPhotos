package com.jiahan.smartcamera.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.jiahan.smartcamera.core.ui.R

/**
 * An [OutlinedTextField] for a password, with the show/hide toggle wired into its trailing icon.
 *
 * Extracted when `:feature:settings` was pulled out of `:app`, because the change-password dialog
 * held three copies of this block and `AuthScreen` a fourth. The strings the toggle needs
 * (`cd_hide_password`, `cd_show_password`) had consumers in two different future modules, which is
 * the `cd_back` case from the `:feature:explore` extraction: a resource with more than one consumer
 * goes *down*, not sideways, and the composable that owns it comes with it.
 *
 * Note the split in how text reaches this component. [label] and [errorMessage] are parameters
 * because they are product copy the calling feature owns -- `Current password` and `New password`
 * mean nothing to `:core:ui`. The two content descriptions are resolved internally because they
 * describe *this component's own* toggle, and every caller wants the same words for it. That is
 * the same line `NoteItem` fails to draw, and the reason this one is worth copying instead.
 *
 * Visibility is hoisted rather than `remember`ed: `SettingsViewModel` already keeps the three flags
 * on `SettingsDialogState.ChangePassword`, so owning it here would give the dialog two sources of
 * truth for the same boolean.
 *
 * [modifier] defaults to a bare `Modifier`, so every caller repeats `Modifier.fillMaxWidth()`. Do
 * not fold that back into the default: modifiers passed by a caller *replace* the default rather
 * than combining with it, so the first caller to pass one of its own would silently lose the width
 * and render at its intrinsic size. This is Compose's `ModifierParameter` lint rule.
 */
@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    errorMessage: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        shape = MaterialTheme.shapes.large,
        singleLine = true,
        visualTransformation = if (visible)
            VisualTransformation.None
        else
            PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = imeAction
        ),
        keyboardActions = keyboardActions,
        leadingIcon = leadingIcon,
        trailingIcon = {
            Icon(
                modifier = Modifier.clickable(
                    interactionSource = null,
                    indication = null
                ) {
                    onVisibilityChange(!visible)
                },
                painter = if (visible)
                    painterResource(R.drawable.visibility)
                else
                    painterResource(R.drawable.visibility_off),
                contentDescription = if (visible)
                    stringResource(R.string.cd_hide_password)
                else
                    stringResource(R.string.cd_show_password)
            )
        },
        modifier = modifier
    )
}