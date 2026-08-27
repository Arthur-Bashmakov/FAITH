package ru.faith.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
internal fun FaithSettingsDialog(
    context: Context,
    accountEmail: String?,
    onOpenAccount: () -> Unit,
    onLogout: () -> Unit,
    onOpenHistory: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF211934),
        titleContentColor = Color.White,
        textContentColor = LightPurple,
        title = { Text(context.getString(R.string.settings_title)) },
        text = {
            Column {
                TextButton(onClick = onOpenHistory) {
                    Text(context.getString(R.string.history_open), color = LightPurple)
                }
                if (accountEmail == null) {
                    TextButton(onClick = onOpenAccount) {
                        Text(context.getString(R.string.account_login), color = LightPurple)
                    }
                } else {
                    Text(context.getString(R.string.account_signed_in, accountEmail))
                    TextButton(onClick = onLogout) {
                        Text(context.getString(R.string.account_logout), color = Color(0xFFFF8A9A))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(context.getString(R.string.save), color = Purple)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.cancel), color = LightPurple)
            }
        },
    )
}
