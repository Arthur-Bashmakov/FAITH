package ru.faith.app

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FaithAuthDialog(
    context: Context,
    busy: Boolean,
    error: String?,
    onSubmit: (String, String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var providerNotice by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Color(0xFF211934),
        titleContentColor = Color.White,
        textContentColor = LightPurple,
        title = { Text(context.getString(R.string.account_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(context.getString(R.string.account_email)) },
                    singleLine = true,
                    enabled = !busy,
                    colors = authFieldColors(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(context.getString(R.string.account_password)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = authFieldColors(),
                )
                error?.let { Text(it, color = Color(0xFFFF8A9A)) }
                Spacer(Modifier.height(12.dp))
                Text(context.getString(R.string.account_social_title), color = LightPurple)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SocialAuthButton("G", "Google", Modifier.weight(1f)) {
                        providerNotice = context.getString(R.string.account_social_pending, "Google")
                    }
                    SocialAuthButton("Я", "Яндекс", Modifier.weight(1f)) {
                        providerNotice = context.getString(R.string.account_social_pending, "Яндекс")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SocialAuthButton("VK", "VK", Modifier.weight(1f)) {
                        providerNotice = context.getString(R.string.account_social_pending, "VK")
                    }
                    SocialAuthButton("A", "Apple", Modifier.weight(1f)) {
                        providerNotice = context.getString(R.string.account_social_pending, "Apple")
                    }
                }
                providerNotice?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = LightPurple)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                onClick = { onSubmit(email, password, false) },
            ) { Text(context.getString(R.string.account_login), color = LightPurple) }
        },
        dismissButton = {
            TextButton(
                enabled = !busy && email.isNotBlank() && password.length >= 10,
                onClick = { onSubmit(email, password, true) },
            ) { Text(context.getString(R.string.account_register), color = Purple) }
        },
    )
}

@Composable
private fun SocialAuthButton(
    badge: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier.height(48.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Purple.copy(alpha = 0.72f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF302348),
            contentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(Purple.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = badge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = title,
            modifier = Modifier.padding(start = 7.dp),
            color = Color.White,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = Purple,
    unfocusedLabelColor = LightPurple.copy(alpha = 0.72f),
    focusedBorderColor = Purple,
    unfocusedBorderColor = LightPurple.copy(alpha = 0.38f),
    cursorColor = Purple,
    disabledTextColor = LightPurple.copy(alpha = 0.5f),
)
