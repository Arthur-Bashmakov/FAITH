package ru.faith.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun AnalysisHistoryContent(
    items: List<AnalysisHistoryItem>,
    loading: Boolean,
    error: String?,
    context: Context,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Text(
        context.getString(R.string.history_title),
        color = Color.White,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(16.dp))

    when {
        loading -> {
            CircularProgressIndicator(color = Purple)
            Spacer(Modifier.height(12.dp))
            Text(context.getString(R.string.history_loading), color = LightPurple)
        }
        error != null -> MessageCard(
            context.getString(R.string.error_title),
            error,
            Color(0xFFFF8A9A),
            compact = true,
        )
        items.isEmpty() -> Text(
            context.getString(R.string.history_empty),
            color = LightPurple,
        )
        else -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.take(50).forEach { item ->
                HistoryCard(item = item, context = context)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Button(
        onClick = onRefresh,
        enabled = !loading,
        colors = ButtonDefaults.buttonColors(containerColor = RoyalViolet),
    ) {
        Text(context.getString(R.string.history_refresh))
    }
    TextButton(onClick = onBack) {
        Text(context.getString(R.string.back_home), color = LightPurple)
    }
}

@Composable
private fun HistoryCard(item: AnalysisHistoryItem, context: Context) {
    val score = (item.probability * 100).roundToInt().coerceIn(0, 100)
    val title = when (item.verdict) {
        "synthetic" -> context.getString(R.string.result_synthetic)
        "human" -> context.getString(R.string.result_human)
        else -> context.getString(R.string.result_uncertain)
    }
    val accent = when (item.verdict) {
        "synthetic" -> Color(0xFFFF8A9A)
        "human" -> Purple
        else -> Color(0xFFFFC857)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Text(
            item.fileName,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(5.dp))
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(
            context.getString(R.string.synthetic_score, score),
            color = LightPurple,
            fontSize = 12.sp,
        )
        Text(
            formatHistoryDate(item.createdAt, context),
            color = LightPurple.copy(alpha = 0.65f),
            fontSize = 11.sp,
        )
    }
}

private fun formatHistoryDate(value: String, context: Context): String {
    val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
    return runCatching {
        val dateTime = OffsetDateTime.parse(value)
        dateTime
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", locale))
    }.getOrElse { value.replace('T', ' ').take(16) }
}
