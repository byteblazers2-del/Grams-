package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * iOS-style "page sheet": the main content shrinks and rounds off from the top
 * while the sheet slides up. Shrink progress follows the finger while dragging.
 *
 * @param visible        whether the sheet is shown
 * @param onDismiss      called on scrim tap, back press, or drag/fling to close
 * @param topGap         gap between screen top and sheet top (default: status bar + 12dp)
 * @param backScale      final scale of the page behind (iOS ~0.92)
 * @param stageColor     color visible around the shrunken page (keep it darker than the page)
 * @param sheetContent   sheet body (put your list/form here; scrolling content closes the sheet
 *                       when overscrolled at the top thanks to nested scroll)
 * @param content        the main screen
 */
@Composable
fun IosSheetHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    topGap: Dp? = null,
    backScale: Float = 0.92f,
    scrimAlpha: Float = 0.28f,
    stageColor: Color = Color.Black,
    sheetContainerColor: Color = MaterialTheme.colorScheme.surface,
    sheetContent: @Composable ColumnScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scope = rememberCoroutineScope()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val peek = 28.dp // how much of the shrunken page shows above the sheet

    BackHandler(enabled = visible) { currentOnDismiss() }

    BoxWithConstraints(modifier.fillMaxSize().background(stageColor)) {
        val density = LocalDensity.current
        val gap = topGap ?: maxOf(statusTop + 116.dp, maxHeight * 0.28f)
        val pageOffsetDp = (minOf(gap, statusTop + 36.dp) - peek).coerceAtLeast(0.dp)
        val sheetH = (maxHeight - gap).coerceAtLeast(240.dp)
        val sheetHPx = with(density) { sheetH.toPx() }
        val pageOffsetPx = with(density) { pageOffsetDp.toPx() }
        val cornerPx = with(density) { 18.dp.toPx() }

        // 0 = fully open, sheetHPx = fully closed
        val offset = remember { Animatable(sheetHPx) }
        val spring = remember { spring<Float>(dampingRatio = 0.80f, stiffness = 360f) }

        LaunchedEffect(visible, sheetHPx) {
            offset.animateTo(if (visible) 0f else sheetHPx, spring)
        }

        // Read only inside graphicsLayer / drawBehind / offset lambdas => no recomposition per frame
        val progress: () -> Float = { 1f - (offset.value / sheetHPx).coerceIn(0f, 1f) }
        val showLayer by remember { derivedStateOf { offset.value < sheetHPx - 0.5f } }

        fun snap(v: Float) {
            scope.launch(start = CoroutineStart.UNDISPATCHED) { offset.snapTo(v) }
        }

        suspend fun settle(velocity: Float) {
            if (offset.value > sheetHPx * 0.3f || velocity > 1500f) currentOnDismiss()
            else offset.animateTo(0f, spring, velocity)
        }

        val nested = remember(sheetHPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val dy = available.y
                    if (dy < 0f && offset.value > 0f) {
                        val new = (offset.value + dy).coerceAtLeast(0f)
                        val consumed = new - offset.value
                        snap(new)
                        return Offset(0f, consumed)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset, available: Offset, source: NestedScrollSource
                ): Offset {
                    if (available.y > 0f && source == NestedScrollSource.UserInput) {
                        snap(offset.value + available.y)
                        return Offset(0f, available.y)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (offset.value > 0f) {
                        settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

        // ---- Main page ----
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val p = progress()
                val s = 1f - (1f - backScale) * p
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
                translationY = pageOffsetPx * p
                if (p > 0f) {
                    shape = RoundedCornerShape(cornerPx * p)
                    clip = true
                }
            }
        ) { content() }

        if (showLayer) {
            // ---- Scrim ----
            Box(
                Modifier.fillMaxSize()
                    .drawBehind { drawRect(Color.Black.copy(alpha = scrimAlpha * progress())) }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { currentOnDismiss() }
            )

            // ---- Sheet ----
            Column(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(sheetH)
                    .offset { IntOffset(0, offset.value.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(sheetContainerColor)
                    .pointerInput(Unit) { detectTapGestures { } } // don't leak taps to scrim
                    .nestedScroll(nested)
            ) {
                // Grabber / drag handle
                Box(
                    Modifier.fillMaxWidth()
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { d ->
                                snap((offset.value + d).coerceAtLeast(0f))
                            },
                            onDragStopped = { v -> settle(v) }
                        )
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(width = 36.dp, height = 5.dp)
                            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
                    )
                }
                sheetContent()
            }
        }
    }
}
