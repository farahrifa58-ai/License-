package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ActivationCodeDoc
import com.example.data.BuyerUserDoc
import com.example.data.ManagedAppConfig
import com.example.ui.theme.LocalSemanticColors
import com.example.ui.theme.MonoSmallStyle

@Composable
fun SettingsThemeDialog(
    isDarkTheme: Boolean,
    onSelectTheme: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Column {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.settings_theme_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeOptionCard(
                    title = stringResource(R.string.settings_theme_dark),
                    description = stringResource(R.string.settings_theme_dark_desc),
                    icon = Icons.Filled.DarkMode,
                    selected = isDarkTheme,
                    onClick = { onSelectTheme(true) },
                    modifier = Modifier.testTag("theme_option_dark")
                )
                ThemeOptionCard(
                    title = stringResource(R.string.settings_theme_light),
                    description = stringResource(R.string.settings_theme_light_desc),
                    icon = Icons.Filled.LightMode,
                    selected = !isDarkTheme,
                    onClick = { onSelectTheme(false) },
                    modifier = Modifier.testTag("theme_option_light")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("settings_close_button")
            ) {
                Text(stringResource(R.string.settings_close))
            }
        }
    )
}

@Composable
private fun ThemeOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalSemanticColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            semantic.subtleSurface
        },
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else semantic.cardBorder
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun AppConfigDialog(
    initialApp: ManagedAppConfig?,
    defaultDatabaseId: String,
    onDismiss: () -> Unit,
    onSave: (name: String, prefix: String, databaseId: String) -> Unit,
    onDeleteRequest: ((ManagedAppConfig) -> Unit)? = null
) {
    val isEdit = initialApp != null
    var name by remember { mutableStateOf(initialApp?.name.orEmpty()) }
    var prefix by remember { mutableStateOf(initialApp?.normalizedPrefix.orEmpty()) }
    var databaseId by remember {
        mutableStateOf(initialApp?.databaseId ?: defaultDatabaseId)
    }

    val isValid = name.trim().isNotEmpty() && prefix.trim().isNotEmpty() && databaseId.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = if (isEdit) {
                    stringResource(R.string.dialog_edit_app_title)
                } else {
                    stringResource(R.string.dialog_add_app_title)
                },
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.dialog_app_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_app_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_app_name_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.uppercase() },
                    label = { Text(stringResource(R.string.dialog_prefix_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_prefix_placeholder)) },
                    singleLine = true,
                    textStyle = MonoSmallStyle,
                    supportingText = {
                        val preview = prefix.trim().uppercase().trimEnd('-').ifBlank { "KASIR" }
                        Text("Contoh hasil: $preview-A7K9-M2P4")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_prefix_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = databaseId,
                    onValueChange = { databaseId = it },
                    label = { Text(stringResource(R.string.dialog_db_id_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_db_id_placeholder)) },
                    singleLine = true,
                    textStyle = MonoSmallStyle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_db_id_input"),
                    shape = RoundedCornerShape(12.dp)
                )

                if (isEdit && initialApp != null && onDeleteRequest != null) {
                    TextButton(
                        onClick = { onDeleteRequest(initialApp) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("dialog_delete_app_button")
                    ) {
                        Text(stringResource(R.string.dialog_delete_app))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), prefix.trim(), databaseId.trim()) },
                enabled = isValid,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("dialog_save_app_button")
            ) {
                Text(stringResource(R.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun DeleteAppConfirmDialog(
    app: ManagedAppConfig,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_app_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_delete_app_message, app.name, app.databaseId),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_app_button")
            ) {
                Text(stringResource(R.string.dialog_delete_app_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun DeleteCodeConfirmDialog(
    code: ActivationCodeDoc,
    databaseId: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_delete_code_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_delete_code_message, code.id, databaseId),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_code_button")
            ) {
                Text(stringResource(R.string.dialog_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun ResetDeviceConfirmDialog(
    buyer: BuyerUserDoc,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val name = buyer.resolvedBusinessName.ifBlank { stringResource(R.string.buyer_unnamed) }
    val email = buyer.resolvedEmail.ifBlank { buyer.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Text(
                text = stringResource(R.string.dialog_reset_device_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_reset_device_message, name, email),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_reset_device_button")
            ) {
                Text(stringResource(R.string.dialog_reset_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
