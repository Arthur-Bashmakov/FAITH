package ru.faith.app

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff

@Composable
internal fun FaithAuthDialog(
    context: Context,
    busy: Boolean,
    error: String?,
    onSubmit: (String, String, Boolean) -> Unit,
    onYandexSignIn: () -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var registerMode by remember { mutableStateOf(false) }
    Dialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = PanelBackground,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    context.getString(R.string.account_title),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(context.getString(R.string.account_email)) },
                    leadingIcon = phoneCountryFlag(email)?.let { flag ->
                        { Text(flag) }
                    },
                    singleLine = true,
                    enabled = !busy,
                    colors = authFieldColors(),
                )
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(context.getString(R.string.account_password)) },
                    singleLine = true,
                    enabled = !busy,
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = context.getString(
                                    if (passwordVisible) R.string.hide_password else R.string.show_password
                                ),
                                tint = LightPurple,
                            )
                        }
                    },
                    colors = authFieldColors(),
                )
                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFF8A9A))
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    enabled = !busy && email.isNotBlank() && (
                        if (registerMode) password.length >= 10 else password.isNotBlank()
                    ),
                    onClick = { onSubmit(email, password, registerMode) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Purple,
                        disabledContainerColor = Purple.copy(alpha = if (busy) 0.72f else 0.28f),
                        disabledContentColor = DarkBackground.copy(alpha = 0.72f),
                    ),
                ) {
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = DarkBackground,
                                strokeWidth = 2.dp,
                            )
                            Text(
                                context.getString(R.string.account_waiting),
                                modifier = Modifier.padding(start = 8.dp),
                                color = DarkBackground,
                            )
                        }
                    } else {
                        Text(
                            context.getString(
                                if (registerMode) R.string.account_register else R.string.account_login
                            ),
                            color = DarkBackground,
                        )
                    }
                }
                TextButton(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    enabled = !busy,
                    onClick = { registerMode = !registerMode },
                ) {
                    Text(
                        context.getString(
                            if (registerMode) R.string.account_login_prompt
                            else R.string.account_register_prompt
                        ),
                        color = LightPurple,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !busy,
                    onClick = onYandexSignIn,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoyalViolet,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(context.getString(R.string.account_yandex_sign_in))
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    modifier = Modifier.align(Alignment.End),
                    enabled = !busy,
                    onClick = onDismiss,
                ) {
                    Text(context.getString(R.string.cancel), color = LightPurple)
                }
            }
        }
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

private fun phoneCountryFlag(identifier: String): String? {
    if ('@' in identifier) return null
    val compact = identifier.filter { it.isDigit() || it == '+' }
    if (compact.startsWith("8") && compact.length <= 11) return "🇷🇺"
    return countryPrefixes.firstOrNull { compact.startsWith(it.first) }?.second
}

private val countryPrefixes = listOf(
    "+375" to "🇧🇾",
    "+374" to "🇦🇲",
    "+380" to "🇺🇦",
    "+995" to "🇬🇪",
    "+998" to "🇺🇿",
    "+996" to "🇰🇬",
    "+992" to "🇹🇯",
    "+994" to "🇦🇿",
    "+971" to "🇦🇪",
    "+972" to "🇮🇱",
    "+44" to "🇬🇧",
    "+49" to "🇩🇪",
    "+33" to "🇫🇷",
    "+39" to "🇮🇹",
    "+34" to "🇪🇸",
    "+90" to "🇹🇷",
    "+86" to "🇨🇳",
    "+91" to "🇮🇳",
    "+81" to "🇯🇵",
    "+82" to "🇰🇷",
    "+7" to "🇷🇺",
    "+1" to "🇺🇸",
)
