package com.example.picencrypt.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.picencrypt.R
import kotlin.math.roundToInt

/**
 * Fullscreen Interactive Image Viewer with pinch-to-zoom, pan, double-tap zoom,
 * and split-screen before/after comparison between original and processed images.
 */
@Composable
fun ImageViewerDialog(
    bitmap: Bitmap?,
    originalBitmap: Bitmap? = null,
    title: String = "",
    subtitle: String = "",
    onDismiss: () -> Unit
) {
    if (bitmap == null) return

    val canCompare = originalBitmap != null && originalBitmap != bitmap
    var isCompareMode by remember { mutableStateOf(false) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var isPressingOriginal by remember { mutableStateOf(false) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            val containerWidthPx = constraints.maxWidth.toFloat()
            val containerHeightPx = constraints.maxHeight.toFloat()

            // Interactive gesture viewport for pinch-to-zoom and pan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isCompareMode) {
                        detectTapGestures(
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    scale = 2.5f
                                    offset = Offset(
                                        (size.width / 2f - tapOffset.x) * 1.5f,
                                        (size.height / 2f - tapOffset.y) * 1.5f
                                    )
                                }
                            }
                        )
                    }
                    .pointerInput(isCompareMode) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            if (scale <= 1.01f) {
                                offset = Offset.Zero
                            } else {
                                val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                offset = Offset(
                                    x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                    y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isCompareMode && canCompare) {
                    // Split screen comparison layout
                    Box(
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Base layer: Processed image (right / background)
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Processed Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top clipped layer: Original image (left / clipped)
                        Image(
                            bitmap = originalBitmap.asImageBitmap(),
                            contentDescription = "Original Image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithContent {
                                    clipRect(
                                        left = 0f,
                                        top = 0f,
                                        right = size.width * splitFraction,
                                        bottom = size.height
                                    ) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                        )
                    }
                } else {
                    // Single display (normal mode or press-to-view-original)
                    val displayBitmap = if (isPressingOriginal && originalBitmap != null) originalBitmap else bitmap
                    Image(
                        bitmap = displayBitmap.asImageBitmap(),
                        contentDescription = "Zoomable preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize(0.92f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
            }

            // Split line and draggable handle (overlayed on top of viewport)
            if (isCompareMode && canCompare) {
                val lineX = containerWidthPx * splitFraction
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(containerWidthPx) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (containerWidthPx > 0f) {
                                    splitFraction = (splitFraction + dragAmount.x / containerWidthPx).coerceIn(0.05f, 0.95f)
                                }
                            }
                        }
                ) {
                    // Vertical dividing line
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(lineX.roundToInt(), 0) }
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.85f))
                    )

                    // Circular draggable handle in the center
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (lineX - 22.dp.toPx()).roundToInt(),
                                    (containerHeightPx / 2f - 22.dp.toPx()).roundToInt()
                                )
                            }
                            .size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "◀ ▶",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Left & Right area badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp, start = 20.dp, end = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "◀ " + stringResource(id = R.string.compare_label_original),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = stringResource(id = R.string.compare_label_processed) + " ▶",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Top Bar: Title, resolution, Compare switch, Reset zoom, and Close button
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title.ifEmpty { stringResource(id = R.string.image_viewer_title) },
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = subtitle.ifEmpty { "${bitmap.width} × ${bitmap.height}" }
                        Text(
                            text = sub,
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Compare Mode Toggle Button
                        if (canCompare) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCompareMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                contentColor = if (isCompareMode) MaterialTheme.colorScheme.onPrimary else Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { isCompareMode = !isCompareMode }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.compare_mode_split),
                                        fontSize = 11.sp,
                                        fontWeight = if (isCompareMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // Reset Zoom button
                        if (scale > 1.05f) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Reset Zoom",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "${(scale * 100).toInt()}%",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Close Button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Bar: Hold-to-compare button and navigation hint
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // If compare mode is off but compare is possible, show a press-and-hold pill
                if (!isCompareMode && canCompare) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isPressingOriginal) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        contentColor = if (isPressingOriginal) MaterialTheme.colorScheme.onPrimary else Color.White,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isPressingOriginal = true
                                        tryAwaitRelease()
                                        isPressingOriginal = false
                                    }
                                )
                            }
                    ) {
                        Text(
                            text = if (isPressingOriginal) {
                                stringResource(id = R.string.compare_viewing_original)
                            } else {
                                stringResource(id = R.string.compare_hold_hint)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    val hint = if (isCompareMode) {
                        stringResource(id = R.string.compare_split_slider_hint)
                    } else {
                        "💡 双指捏合缩放 · 拖拽平移 · 双击快速放大"
                    }
                    Text(
                        text = hint,
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
