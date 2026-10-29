package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Production-grade Fast Scrollbar for Jetpack Compose LazyColumn lists.
 *
 * Features:
 * - Real-time synchronization with [LazyListState].
 * - Proportional thumb height reflecting visible vs total items with accessibility minimum (48dp).
 * - Smooth two-way interaction: swiping list moves thumb; dragging thumb scrolls list instantly.
 * - Minimum 48dp touch target width along the right edge for effortless mobile thumb grabbing.
 * - Modern, non-obtrusive visual pill (6-8dp) that expands and highlights while dragging.
 * - Smooth auto-fade when inactive, automatically staying hidden for short non-scrollable lists.
 * - Floating Section/Letter Bubble indicator during dragging for rapid navigation across long libraries.
 * - Zero interference with list items' trailing 3-dot overflow buttons and favorite controls.
 */
@Composable
fun FastScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    sectionLabelProvider: ((itemIndex: Int) -> String?)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val layoutInfo = listState.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    val visibleItems = layoutInfo.visibleItemsInfo

    // Determine if the list has enough content to be scrollable
    val isScrollable by remember(listState) {
        derivedStateOf {
            val count = listState.layoutInfo.totalItemsCount
            val visible = listState.layoutInfo.visibleItemsInfo
            if (count <= 1 || visible.isEmpty()) {
                false
            } else {
                val first = visible.first()
                val last = visible.last()
                val allItemsVisible = first.index == 0 && last.index == count - 1
                if (!allItemsVisible) {
                    true
                } else {
                    val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                    val contentHeight = last.offset + last.size - first.offset
                    contentHeight > viewportHeight
                }
            }
        }
    }

    if (!isScrollable || totalItems <= 1) {
        return
    }

    var isDragging by remember { mutableStateOf(false) }
    var dragProgressState by remember { mutableFloatStateOf(0f) }
    var activeScrollJob by remember { mutableStateOf<Job?>(null) }

    // Auto-fade timer: visible during scroll or drag, smoothly fades out 1.5s after activity stops
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress, isDragging) {
        if (listState.isScrollInProgress || isDragging) {
            isVisible = true
        } else {
            delay(1500)
            isVisible = false
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = when {
            isDragging -> 1f
            isVisible -> 0.85f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 250),
        label = "fast_scrollbar_alpha"
    )

    // Animated visual thumb thickness: 6dp idle -> 8dp when actively held
    val thumbWidthDp by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 6.dp,
        animationSpec = tween(durationMillis = 150),
        label = "fast_scrollbar_thumb_width"
    )

    // Compute proportional scroll progress (0.0 to 1.0)
    val listScrollProgress by remember(listState) {
        derivedStateOf {
            val count = listState.layoutInfo.totalItemsCount
            val visible = listState.layoutInfo.visibleItemsInfo
            if (count <= 1 || visible.isEmpty()) {
                0f
            } else {
                val first = visible.first()
                val firstItemSize = if (first.size > 0) first.size else 1
                val exactFirstItemPosition = first.index.toFloat() +
                    (first.offset.absoluteValue.toFloat() / firstItemSize.toFloat())
                val maxScrollable = (count - visible.size).coerceAtLeast(1).toFloat()
                (exactFirstItemPosition / maxScrollable).coerceIn(0f, 1f)
            }
        }
    }

    val currentProgress = if (isDragging) dragProgressState else listScrollProgress

    BoxWithConstraints(
        modifier = modifier
            .width(48.dp) // Generous 48dp touch column along the edge
            .fillMaxHeight()
            .testTag("fast_scrollbar_container")
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        if (trackHeightPx <= 0f) return@BoxWithConstraints

        val topPaddingPx = with(density) { contentPadding.calculateTopPadding().toPx() }
        val bottomPaddingPx = with(density) { contentPadding.calculateBottomPadding().toPx() }
        val effectiveTrackHeightPx = (trackHeightPx - topPaddingPx - bottomPaddingPx).coerceAtLeast(1f)

        // Calculate proportional thumb height with minimum accessibility threshold
        val minThumbHeightPx = with(density) { 48.dp.toPx() }
        val visibleRatio = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0.08f, 1f)
        val thumbHeightPx = (effectiveTrackHeightPx * visibleRatio)
            .coerceIn(minThumbHeightPx, effectiveTrackHeightPx * 0.9f)
        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }

        val maxThumbOffsetPx = (effectiveTrackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbOffsetPx = topPaddingPx + (currentProgress * maxThumbOffsetPx)
        val thumbOffsetDp = with(density) { thumbOffsetPx.toDp() }

        // Current item index for section bubble
        val currentTargetIndex = (currentProgress * (totalItems - 1))
            .roundToInt()
            .coerceIn(0, totalItems - 1)

        val bubbleText = remember(currentTargetIndex, isDragging, sectionLabelProvider) {
            if (!isDragging) null
            else sectionLabelProvider?.invoke(currentTargetIndex)?.takeIf { it.isNotBlank() }
                ?: if (totalItems > 15) "${currentTargetIndex + 1}" else null
        }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
        ) {
            // Subtle track background line
            if (animatedAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp, top = with(density) { topPaddingPx.toDp() }, bottom = with(density) { bottomPaddingPx.toDp() })
                        .width(2.dp)
                        .fillMaxHeight()
                        .alpha(animatedAlpha * 0.4f)
                        .background(trackColor, RoundedCornerShape(1.dp))
                )
            }

            // Floating Section Bubble Indicator (Appears next to user's thumb during drag)
            if (isDragging && !bubbleText.isNullOrBlank()) {
                val bubbleYOffset = (thumbOffsetDp - 10.dp)
                    .coerceIn(
                        with(density) { topPaddingPx.toDp() },
                        with(density) { (trackHeightPx - bottomPaddingPx).toDp() - 52.dp }
                    )

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-46).dp, y = bubbleYOffset)
                        .testTag("fast_scrollbar_bubble")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = bubbleText,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1
                        )
                    }
                }
            }

            // Interactive Draggable Thumb (Touch target 48dp x thumbHeight, with 6-8dp visual pill)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffsetDp)
                    .size(width = 48.dp, height = thumbHeightDp.coerceAtLeast(48.dp))
                    .pointerInput(isScrollable, isVisible, isDragging, totalItems, maxThumbOffsetPx) {
                        if (!isScrollable || totalItems <= 1 || (!isVisible && !isDragging)) return@pointerInput
                        var dragAccumulatorPx = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                isVisible = true
                                dragProgressState = listScrollProgress
                                dragAccumulatorPx = listScrollProgress * maxThumbOffsetPx
                            },
                            onDragEnd = {
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulatorPx = (dragAccumulatorPx + dragAmount).coerceIn(0f, maxThumbOffsetPx)
                                val newProgress = if (maxThumbOffsetPx > 0f) {
                                    dragAccumulatorPx / maxThumbOffsetPx
                                } else 0f
                                dragProgressState = newProgress
                                val targetIndex = (newProgress * (totalItems - 1))
                                    .roundToInt()
                                    .coerceIn(0, totalItems - 1)
                                activeScrollJob?.cancel()
                                activeScrollJob = coroutineScope.launch {
                                    listState.scrollToItem(targetIndex)
                                }
                            }
                        )
                    }
            ) {
                // Visual Draggable Thumb Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
                        .width(thumbWidthDp)
                        .height(thumbHeightDp)
                        .alpha(animatedAlpha)
                        .clip(CircleShape)
                        .background(
                            if (isDragging) thumbColor else thumbColor.copy(alpha = 0.85f)
                        )
                        .testTag("fast_scrollbar_thumb")
                )
            }
        }
    }
}
