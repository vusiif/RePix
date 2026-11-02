package com.example.picencrypt.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.MirageTankBridge
import com.example.picencrypt.utils.MiragePresetGenerator
import com.example.picencrypt.viewmodel.MainViewModel
import com.example.picencrypt.ui.components.ImageViewerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MirageScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var isViewingMiragePreview by remember { mutableStateOf(false) }

    val mirageCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            result.data?.data?.let { viewModel.setMirageCover(it, context) }
        }
    )
    val mirageHiddenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            result.data?.data?.let { viewModel.setMirageHidden(it, context) }
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 主设置卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(id = R.string.mirage_tool_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 彩色/黑白 模式切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            viewModel.mirageIsColoredMode = true
                            viewModel.miragePreviewBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = stringResource(id = R.string.mirage_colored_mode), fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            viewModel.mirageIsColoredMode = false
                            viewModel.miragePreviewBitmap = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (!viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = stringResource(id = R.string.mirage_gray_mode), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 演示示例预设快捷芯片 (Demo Presets)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(id = R.string.mirage_presets_title),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.loadMiragePreset(MiragePresetGenerator.PresetTheme.DAY_AND_NIGHT, context)
                            },
                            label = { Text(text = stringResource(id = R.string.mirage_preset_day_night), fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.loadMiragePreset(MiragePresetGenerator.PresetTheme.QUESTION_AND_TRUTH, context)
                            },
                            label = { Text(text = stringResource(id = R.string.mirage_preset_question_truth), fontSize = 11.sp) }
                        )
                    }
                }

                // 比例适配模式选择 (Fit 自适应 / Crop 裁切 / Stretch 拉伸)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(id = R.string.mirage_scale_title) + ":",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row {
                        FilterChip(
                            selected = viewModel.mirageScaleMode == MirageTankBridge.MirageScaleMode.FIT,
                            onClick = {
                                viewModel.mirageScaleMode = MirageTankBridge.MirageScaleMode.FIT
                                viewModel.miragePreviewBitmap = null
                            },
                            label = { Text(text = "自适应", fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = viewModel.mirageScaleMode == MirageTankBridge.MirageScaleMode.CROP,
                            onClick = {
                                viewModel.mirageScaleMode = MirageTankBridge.MirageScaleMode.CROP
                                viewModel.miragePreviewBitmap = null
                            },
                            label = { Text(text = "裁切", fontSize = 11.sp) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        FilterChip(
                            selected = viewModel.mirageScaleMode == MirageTankBridge.MirageScaleMode.STRETCH,
                            onClick = {
                                viewModel.mirageScaleMode = MirageTankBridge.MirageScaleMode.STRETCH
                                viewModel.miragePreviewBitmap = null
                            },
                            label = { Text(text = "拉伸", fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 表图选择条目
                ImageSelectorRow(
                    label = stringResource(id = R.string.mirage_cover_label),
                    hint = "白底/浅色气泡下可见",
                    uri = viewModel.mirageCoverUri,
                    thumbnail = viewModel.mirageCoverThumbnail,
                    filename = viewModel.mirageCoverUri?.let { FileUtils.getUriFilename(context, it) },
                    onClick = {
                        mirageCoverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 互换表里图按钮
                OutlinedButton(
                    onClick = { viewModel.swapMirageImages() },
                    enabled = viewModel.mirageCoverUri != null || viewModel.mirageHiddenUri != null,
                    modifier = Modifier.height(36.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(text = "⇄ " + stringResource(id = R.string.mirage_swap_button), fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 里图选择条目
                ImageSelectorRow(
                    label = stringResource(id = R.string.mirage_hidden_label),
                    hint = "黑底/全屏暗色下可见",
                    uri = viewModel.mirageHiddenUri,
                    thumbnail = viewModel.mirageHiddenThumbnail,
                    filename = viewModel.mirageHiddenUri?.let { FileUtils.getUriFilename(context, it) },
                    onClick = {
                        mirageHiddenLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 高级画面微调控制台 (折叠卡片)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.isMirageAdjustExpanded = !viewModel.isMirageAdjustExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(id = R.string.mirage_adjust_title),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (viewModel.isMirageAdjustExpanded) "收起 ▲" else "展开 ▼",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (viewModel.isMirageAdjustExpanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(id = R.string.mirage_adjust_hint),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // 表图亮度补偿 (-50 ~ 50)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.mirage_cover_brightness, viewModel.mirageCoverBrightness.toInt()),
                                    fontSize = 12.sp
                                )
                            }
                            Slider(
                                value = viewModel.mirageCoverBrightness,
                                onValueChange = {
                                    viewModel.mirageCoverBrightness = it
                                    viewModel.miragePreviewBitmap = null
                                },
                                valueRange = -50f..50f,
                                steps = 20
                            )

                            // 表图对比度 (0.5 ~ 1.5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.mirage_cover_contrast, viewModel.mirageCoverContrast),
                                    fontSize = 12.sp
                                )
                            }
                            Slider(
                                value = viewModel.mirageCoverContrast,
                                onValueChange = {
                                    viewModel.mirageCoverContrast = it
                                    viewModel.miragePreviewBitmap = null
                                },
                                valueRange = 0.5f..1.5f,
                                steps = 20
                            )

                            // 里图亮度增强 (-50 ~ 50)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.mirage_hidden_brightness, viewModel.mirageHiddenBrightness.toInt()),
                                    fontSize = 12.sp
                                )
                            }
                            Slider(
                                value = viewModel.mirageHiddenBrightness,
                                onValueChange = {
                                    viewModel.mirageHiddenBrightness = it
                                    viewModel.miragePreviewBitmap = null
                                },
                                valueRange = -50f..50f,
                                steps = 20
                            )

                            // 里图对比度 (0.5 ~ 1.5)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(id = R.string.mirage_hidden_contrast, viewModel.mirageHiddenContrast),
                                    fontSize = 12.sp
                                )
                            }
                            Slider(
                                value = viewModel.mirageHiddenContrast,
                                onValueChange = {
                                    viewModel.mirageHiddenContrast = it
                                    viewModel.miragePreviewBitmap = null
                                },
                                valueRange = 0.5f..1.5f,
                                steps = 20
                            )

                            // 恢复默认按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resetMirageAdjustments() },
                                    modifier = Modifier.height(32.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.mirage_adjust_reset), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 生成 / 刷新预览按钮
                Button(
                    onClick = {
                        viewModel.generateMiragePreview(context) { success ->
                            if (!success) {
                                Toast.makeText(context, context.getString(R.string.mirage_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = viewModel.mirageCoverUri != null && viewModel.mirageHiddenUri != null && !viewModel.isMirageProcessing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (viewModel.isMirageProcessing && viewModel.miragePreviewBitmap == null) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "正在计算生成...")
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Preview",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = R.string.mirage_btn_preview))
                    }
                }
            }
        }

        // 实时预览与三色背景切换卡片
        val preview = viewModel.miragePreviewBitmap
        if (preview != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(id = R.string.mirage_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Ready",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 三态背景选择 Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FilterChip(
                            selected = viewModel.mirageBgMode == 0,
                            onClick = { viewModel.mirageBgMode = 0 },
                            label = { Text(text = stringResource(id = R.string.mirage_bg_white), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = viewModel.mirageBgMode == 1,
                            onClick = { viewModel.mirageBgMode = 1 },
                            label = { Text(text = stringResource(id = R.string.mirage_bg_black), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                        FilterChip(
                            selected = viewModel.mirageBgMode == 2,
                            onClick = { viewModel.mirageBgMode = 2 },
                            label = { Text(text = stringResource(id = R.string.mirage_bg_checker), fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 动态效果提示
                    val hintText = when (viewModel.mirageBgMode) {
                        0 -> stringResource(id = R.string.mirage_hint_white)
                        1 -> stringResource(id = R.string.mirage_hint_black)
                        else -> stringResource(id = R.string.mirage_hint_checker)
                    }
                    Text(
                        text = hintText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // 画布容器：根据 mirageBgMode 自适应背景色
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp, max = 340.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .then(
                                when (viewModel.mirageBgMode) {
                                    0 -> Modifier.background(Color.White)
                                    1 -> Modifier.background(Color(0xFF121212))
                                    else -> Modifier.checkerboardBackground()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = preview.asImageBitmap(),
                            contentDescription = "Mirage Preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                                .clickable { isViewingMiragePreview = true },
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 保存到相册按钮
                    Button(
                        onClick = {
                            viewModel.saveMiragePreview(context) { saved ->
                                Toast.makeText(
                                    context,
                                    if (saved) context.getString(R.string.mirage_success) else context.getString(R.string.mirage_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = !viewModel.isMirageProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (viewModel.isMirageProcessing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "正在保存...")
                        } else {
                            Text(text = "💾 " + stringResource(id = R.string.mirage_btn_save), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        if (isViewingMiragePreview && preview != null) {
            ImageViewerDialog(
                bitmap = preview,
                title = stringResource(id = R.string.mirage_tool_title) + " 预览",
                subtitle = "${preview.width} × ${preview.height} (无损 PNG 预览)",
                onDismiss = { isViewingMiragePreview = false }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * 选图条目组件：展示缩略图、表/里图标签与文件名
 */
@Composable
private fun ImageSelectorRow(
    label: String,
    hint: String,
    uri: android.net.Uri?,
    thumbnail: android.graphics.Bitmap?,
    filename: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧缩略图 / 占位图
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Select",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 文本信息
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = filename ?: "点击选择图片",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (uri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 右侧指示
            Text(
                text = if (uri != null) "更换" else "选择",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

/**
 * 经典棋盘格透明背景绘制修饰符
 */
fun Modifier.checkerboardBackground(
    tileSize: Dp = 10.dp,
    color1: Color = Color(0xFFDCDCDC),
    color2: Color = Color(0xFFFFFFFF)
): Modifier = this.drawBehind {
    val tilePx = tileSize.toPx()
    val numCols = (size.width / tilePx).toInt() + 1
    val numRows = (size.height / tilePx).toInt() + 1
    for (row in 0 until numRows) {
        for (col in 0 until numCols) {
            val color = if ((row + col) % 2 == 0) color1 else color2
            drawRect(
                color = color,
                topLeft = Offset(col * tilePx, row * tilePx),
                size = Size(tilePx, tilePx)
            )
        }
    }
}
