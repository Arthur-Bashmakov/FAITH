package ru.faith.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun FaithVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    if (scrollState.maxValue <= 0) return
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = with(density) { maxHeight.toPx() }
        val viewport = scrollState.viewportSize.coerceAtLeast(1)
        val contentHeight = viewport + scrollState.maxValue
        val minimumThumbPx = with(density) { 42.dp.toPx() }
        val thumbHeightPx = (trackHeightPx * viewport / contentHeight)
            .coerceIn(minimumThumbPx, trackHeightPx)
        val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
        val thumbOffsetPx = travelPx * scrollState.value / scrollState.maxValue
        val scrollPerDragPx = scrollState.maxValue / travelPx

        Box(
            modifier = Modifier
                .width(18.dp)
                .fillMaxHeight()
                .pointerInput(scrollState.maxValue, travelPx) {
                    detectTapGestures { position ->
                        val target = ((position.y - thumbHeightPx / 2f) / travelPx)
                            .coerceIn(0f, 1f)
                        scope.launch { scrollState.animateScrollTo((target * scrollState.maxValue).roundToInt()) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.12f)),
            )
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .width(8.dp)
                .fillMaxHeight(thumbHeightPx / trackHeightPx)
                .clip(RoundedCornerShape(50))
                .background(LightPurple.copy(alpha = 0.9f))
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        scope.launch { scrollState.scrollBy(delta * scrollPerDragPx) }
                    },
                ),
        )
    }
}
