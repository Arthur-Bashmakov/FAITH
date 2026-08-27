package ru.faith.app

import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.LinearGradient as AndroidLinearGradient
import android.graphics.Paint as AndroidPaint

@Composable
internal fun LanguageSelectionScreen(onLanguageSelected: (String) -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0xFF17102E), DarkBackground)))
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.choose_language),
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(32.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onLanguageSelected("ru") },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) { Text(stringResource(R.string.russian)) }
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onLanguageSelected("en") },
                    colors = ButtonDefaults.buttonColors(containerColor = Purple),
                ) { Text(stringResource(R.string.english)) }
            }
        }
    }
}

@Composable
internal fun FaithSplash(
    hapticEnabled: Boolean,
    onHapticEnabledChange: (Boolean) -> Unit,
    onFinished: () -> Unit,
) {
    var stageIndex by remember { mutableStateOf(0) }
    val infiniteTransition = rememberInfiniteTransition(label = "splashMotion")
    val particlePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particlePhase",
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "splashPulse",
    )
    LaunchedEffect(Unit) {
        for (index in 1..5) {
            delay(1_600)
            stageIndex = index
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF32185D), Color(0xFF130B28), Color(0xFF070511)),
                    radius = 1050f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            drawCircle(
                color = Purple.copy(alpha = 0.07f * pulse),
                radius = size.minDimension * (0.24f + 0.04f * pulse),
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            )
            drawCircle(
                color = LightPurple.copy(alpha = 0.08f),
                radius = size.minDimension * 0.13f * pulse,
                center = androidx.compose.ui.geometry.Offset(centerX, centerY),
            )

            repeat(86) { index ->
                val angle = index * 2.39996f + stageIndex * 0.37f
                val base = 26f + (index % 13) * 11f
                val distance = base + particlePhase * (115f + (index % 7) * 15f)
                val x = centerX + cos(angle) * distance
                val y = centerY + sin(angle) * distance * 0.72f
                val fade = (1f - particlePhase) * (0.16f + (index % 6) * 0.075f)
                val shardLength = 3f + (index % 5) * 2.2f
                drawLine(
                    color = when (index % 4) {
                        0 -> Color.White.copy(alpha = fade)
                        1 -> Color(0xFFD9C8F2).copy(alpha = fade)
                        else -> Purple.copy(alpha = fade)
                    },
                    start = androidx.compose.ui.geometry.Offset(x, y),
                    end = androidx.compose.ui.geometry.Offset(
                        x + cos(angle) * shardLength,
                        y + sin(angle) * shardLength,
                    ),
                    strokeWidth = 0.8f + (index % 3) * 0.65f,
                    cap = StrokeCap.Round,
                )
            }
        }
        MorphingFaithWord(stageIndex = stageIndex, pulse = pulse)
        IconButton(
            onClick = { onHapticEnabledChange(!hapticEnabled) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(18.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Vibration,
                contentDescription = stringResource(
                    if (hapticEnabled) R.string.launch_haptic_on else R.string.launch_haptic_off,
                ),
                tint = if (hapticEnabled) LightPurple else LightPurple.copy(alpha = 0.35f),
            )
        }
    }
}

private data class LetterTarget(
    val x: androidx.compose.ui.unit.Dp,
    val y: androidx.compose.ui.unit.Dp = 0.dp,
    val alpha: Float = 1f,
    val scale: Float = 1f,
)

@Composable
private fun MorphingFaithWord(stageIndex: Int, pulse: Float) {
    val letters = remember { listOf('F', 'A', 'I', 'T', 'H') }
    val targets = when (stageIndex) {
        0 -> listOf(-104.dp, -52.dp, 0.dp, 52.dp, 104.dp).map { LetterTarget(it) }
        1 -> listOf(
            LetterTarget(-520.dp, -36.dp, 0f, 1.5f),
            LetterTarget(-34.dp, scale = 1.14f),
            LetterTarget(34.dp, scale = 1.14f),
            LetterTarget(520.dp, -50.dp, 0f, 1.5f),
            LetterTarget(620.dp, 70.dp, 0f, 1.7f),
        )
        2 -> listOf(
            LetterTarget(-620.dp, -70.dp, 0f, 1.7f),
            LetterTarget(-520.dp, 52.dp, 0f, 1.5f),
            LetterTarget(0.dp, scale = 1.28f),
            LetterTarget(620.dp, -80.dp, 0f, 1.7f),
            LetterTarget(720.dp, 90.dp, 0f, 1.8f),
        )
        3 -> listOf(
            LetterTarget(-620.dp, -70.dp, 0f, 1.7f),
            LetterTarget(-34.dp, scale = 1.14f),
            LetterTarget(34.dp, scale = 1.14f),
            LetterTarget(620.dp, -80.dp, 0f, 1.7f),
            LetterTarget(720.dp, 90.dp, 0f, 1.8f),
        )
        else -> listOf(-104.dp, -52.dp, 0.dp, 52.dp, 104.dp).map { LetterTarget(it) }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        letters.forEachIndexed { index, letter ->
            val target = targets[index]
            val x by animateDpAsState(target.x, tween(720, easing = FastOutSlowInEasing), label = "${letter}X")
            val y by animateDpAsState(target.y, tween(720, easing = FastOutSlowInEasing), label = "${letter}Y")
            val alpha by animateFloatAsState(target.alpha, tween(500), label = "${letter}Alpha")
            val scale by animateFloatAsState(target.scale, tween(720, easing = FastOutSlowInEasing), label = "${letter}Scale")
            val isAiLetter = letter == 'A' || letter == 'I'

            MetallicLetter(
                letter = letter,
                highlighted = isAiLetter,
                modifier = Modifier.graphicsLayer {
                    translationX = x.toPx()
                    translationY = y.toPx()
                    scaleX = scale * if (isAiLetter) pulse else 1f
                    scaleY = scale * if (isAiLetter) pulse else 1f
                    this.alpha = alpha
                },
            )
        }
    }
}

@Composable
private fun MetallicLetter(
    letter: Char,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(92.dp, 116.dp)) {
        val text = letter.toString()
        val textSize = 82.sp.toPx()
        val typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        val bounds = android.graphics.Rect()

        val measurePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            this.textSize = textSize
        }
        measurePaint.getTextBounds(text, 0, text.length, bounds)
        val x = (size.width - measurePaint.measureText(text)) / 2f
        val y = (size.height - bounds.height()) / 2f - bounds.top

        drawIntoCanvas { canvas ->
            val glowColor = if (highlighted) 0xFFA96CFF.toInt() else 0xFF8A63B8.toInt()
            val glow = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                color = glowColor
                alpha = if (highlighted) 150 else 80
                setShadowLayer(if (highlighted) 24f else 14f, 0f, 0f, glowColor)
            }
            canvas.nativeCanvas.drawText(text, x, y, glow)

            val metal = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                this.textSize = textSize
                shader = AndroidLinearGradient(
                    0f,
                    y - textSize,
                    0f,
                    y + 8f,
                    intArrayOf(
                        0xFFFFFFFF.toInt(),
                        if (highlighted) 0xFFF0DFFF.toInt() else 0xFFE8E4ED.toInt(),
                        0xFFAAA3B3.toInt(),
                        0xFFF8F5FF.toInt(),
                    ),
                    floatArrayOf(0f, 0.32f, 0.68f, 1f),
                    Shader.TileMode.CLAMP,
                )
                setShadowLayer(3f, 0f, 2f, 0xFF000000.toInt())
            }
            canvas.nativeCanvas.drawText(text, x, y, metal)
        }
    }
}
