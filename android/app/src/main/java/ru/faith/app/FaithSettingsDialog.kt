package ru.faith.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight

@Composable
internal fun FaithSettingsDialog(
    context: Context,
    accountEmail: String?,
    accountProvider: String,
    language: String,
    onLanguageChange: (String) -> Unit,
    onOpenAccount: () -> Unit,
    onLogout: () -> Unit,
    deleteBusy: Boolean,
    deleteError: String?,
    onDeleteAccount: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PanelBackground,
        titleContentColor = Color.White,
        textContentColor = LightPurple,
        title = { Text(context.getString(R.string.settings_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    context.getString(R.string.interface_language),
                    color = LightPurple,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showLanguagePicker = true },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            context.getString(
                                if (language == "ru") R.string.russian else R.string.english
                            ),
                            color = Color.White,
                        )
                        Text("›", color = Purple, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    context.getString(R.string.account_section),
                    color = LightPurple,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(10.dp))
                if (accountEmail == null) {
                    Button(
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        onClick = onOpenAccount,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Purple),
                    ) {
                        Text(context.getString(R.string.account_open_auth), color = DarkBackground)
                    }
                } else {
                    Text(
                        context.getString(R.string.account_signed_in, accountEmail),
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenHistory,
                        border = BorderStroke(1.dp, Purple.copy(alpha = 0.6f)),
                    ) {
                        Text(context.getString(R.string.history_open), color = LightPurple)
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onLogout) {
                        Text(context.getString(R.string.account_logout), color = Color(0xFFFF8A9A))
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { confirmDelete = true },
                    ) {
                        Text(context.getString(R.string.account_delete), color = Color(0xFFFF8A9A))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(context.getString(R.string.close), color = Purple)
            }
        },
    )
    if (showLanguagePicker) {
        AlertDialog(
            onDismissRequest = { showLanguagePicker = false },
            containerColor = PanelBackground,
            title = {
                Text(context.getString(R.string.interface_language), color = Color.White)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LanguageButton(
                        title = context.getString(R.string.russian),
                        selected = language == "ru",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onLanguageChange("ru")
                            showLanguagePicker = false
                        },
                    )
                    LanguageButton(
                        title = context.getString(R.string.english),
                        selected = language == "en",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onLanguageChange("en")
                            showLanguagePicker = false
                        },
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguagePicker = false }) {
                    Text(context.getString(R.string.cancel), color = LightPurple)
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleteBusy) confirmDelete = false },
            containerColor = PanelBackground,
            title = { Text(context.getString(R.string.account_delete_title), color = Color.White) },
            text = {
                Column {
                    val passwordAccount = accountProvider == "password"
                    Text(
                        context.getString(
                            if (passwordAccount) R.string.account_delete_warning
                            else R.string.account_delete_oauth_warning
                        ),
                        color = LightPurple,
                    )
                    if (passwordAccount) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(context.getString(R.string.account_password_confirm)) },
                            visualTransformation = PasswordVisualTransformation(),
                            enabled = !deleteBusy,
                            singleLine = true,
                        )
                    }
                    deleteError?.let { Text(it, color = Color(0xFFFF8A9A)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = (accountProvider != "password" || password.isNotBlank()) && !deleteBusy,
                    onClick = { onDeleteAccount(password) },
                ) { Text(context.getString(R.string.account_delete_confirm), color = Color(0xFFFF8A9A)) }
            },
            dismissButton = {
                TextButton(enabled = !deleteBusy, onClick = { confirmDelete = false }) {
                    Text(context.getString(R.string.cancel), color = LightPurple)
                }
            },
        )
    }
}

@Composable
private fun LanguageButton(
    title: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            modifier = modifier.height(42.dp),
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Purple),
        ) {
            Text(title, color = DarkBackground, fontWeight = FontWeight.SemiBold)
        }
    } else {
        OutlinedButton(
            modifier = modifier.height(42.dp),
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, LightPurple.copy(alpha = 0.35f)),
        ) {
            Text(title, color = LightPurple)
        }
    }
}
