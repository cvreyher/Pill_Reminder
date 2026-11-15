package com.vonreyher.medicationtracker.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vonreyher.medicationtracker.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ThumbSize = 56.dp
private val TrackPadding = 4.dp
private const val ConfirmThreshold = 0.85f

/**
 * Slide-to-confirm control: the thumb has to be dragged to the end of the track,
 * releasing it earlier springs it back. Built from Material 3 surfaces and color roles.
 */
@Composable
fun SlideToConfirm(
    text: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var confirmed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    val thumbScale by animateFloatAsState(if (dragging) 1.08f else 1f, label = "thumbScale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ThumbSize + TrackPadding * 2)
            .semantics(mergeDescendants = true) {
                // Accessibility services can't drag, so expose the confirmation as a click action.
                onClick(label = text) {
                    onConfirm()
                    true
                }
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        BoxWithConstraints(Modifier.padding(TrackPadding)) {
            val maxOffset = with(LocalDensity.current) { (maxWidth - ThumbSize).toPx() }
                .coerceAtLeast(0f)
            val fraction = if (maxOffset == 0f) 0f else offsetX / maxOffset

            // Fills the track behind the thumb as it is dragged.
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .height(ThumbSize)
                    .width(ThumbSize + with(LocalDensity.current) { offsetX.toDp() })
            ) {}

            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(start = ThumbSize)
                    .graphicsLayer { alpha = 1f - fraction }
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .size(ThumbSize)
                    .graphicsLayer {
                        scaleX = thumbScale
                        scaleY = thumbScale
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = !confirmed,
                        state = rememberDraggableState { delta ->
                            offsetX = (offsetX + delta).coerceIn(0f, maxOffset)
                        },
                        onDragStarted = { dragging = true },
                        onDragStopped = {
                            dragging = false
                            if (maxOffset > 0f && offsetX >= maxOffset * ConfirmThreshold) {
                                confirmed = true
                                animate(offsetX, maxOffset) { value, _ -> offsetX = value }
                                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                delay(150)
                                onConfirm()
                            } else {
                                animate(offsetX, 0f, animationSpec = spring()) { value, _ ->
                                    offsetX = value
                                }
                            }
                        }
                    )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(
                            if (confirmed) R.drawable.ic_check else R.drawable.ic_arrow_forward
                        ),
                        contentDescription = null
                    )
                }
            }
        }
    }
}
