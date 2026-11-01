package com.example.picencrypt.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.SteganographyHelper
import com.example.picencrypt.viewmodel.MainViewModel

@Composable
fun StegoScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val stegoCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            result.data?.data?.let { viewModel.setStegoCover(it, context) }
        }
    )
    val stegoArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            result.data?.data?.let { viewModel.setStegoArchive(it, context) }
        }
    )
    val stegoExtractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            result.data?.data?.let { viewModel.setStegoExtractTarget(it, context) }
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth(0.94f)
            .verticalScroll(scrollState)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
                    text = stringResource(id = R.string.stego_tool_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 模式切换
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { viewModel.stegoMode = 0 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.stegoMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (viewModel.stegoMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = stringResource(id = R.string.stego_create_mode), fontSize = 13.sp)
                    }
                    Button(
                        onClick = { viewModel.stegoMode = 1 },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (viewModel.stegoMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (viewModel.stegoMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(text = stringResource(id = R.string.stego_extract_mode), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (viewModel.stegoMode == 0) {
                    // ---- 合成模式 ----
                    StegoItemRow(
                        label = stringResource(id = R.string.stego_cover_label),
                        filename = viewModel.stegoCoverUri?.let { FileUtils.getUriFilename(context, it) },
                        sizeText = if (viewModel.stegoCoverFileSize > 0L) FileUtils.formatFileSize(viewModel.stegoCoverFileSize) else null,
                        thumbnail = viewModel.stegoCoverThumbnail,
                        isArchive = false,
                        onClick = {
                            stegoCoverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    StegoItemRow(
                        label = stringResource(id = R.string.stego_archive_label),
                        filename = viewModel.stegoArchiveUri?.let { FileUtils.getUriFilename(context, it) },
                        sizeText = if (viewModel.stegoArchiveFileSize > 0L) FileUtils.formatFileSize(viewModel.stegoArchiveFileSize) else null,
                        thumbnail = null,
                        isArchive = true,
                        onClick = {
                            stegoArchiveLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                        }
                    )

                    // 预估大小与说明
                    if (viewModel.stegoCoverUri != null && viewModel.stegoArchiveUri != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        val totalEstimated = viewModel.stegoCoverFileSize + viewModel.stegoArchiveFileSize
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "📦 " + stringResource(id = R.string.stego_estimated_total_size) + ": " + FileUtils.formatFileSize(totalEstimated),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "合成原理：将压缩包数据无损追加在图片文件尾部，常规看图正常显示，解压软件可直接解出内部文件。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.createStego(context) { ok ->
                                Toast.makeText(
                                    context,
                                    if (ok) context.getString(R.string.stego_create_success) else context.getString(R.string.stego_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = viewModel.stegoCoverUri != null && viewModel.stegoArchiveUri != null && !viewModel.isStegoProcessing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (viewModel.isStegoProcessing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "正在合成图种...")
                        } else {
                            Text(text = stringResource(id = R.string.stego_btn_create), fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    // ---- 提取模式 ----
                    StegoItemRow(
                        label = stringResource(id = R.string.stego_target_label),
                        filename = viewModel.stegoExtractTargetUri?.let { FileUtils.getUriFilename(context, it) },
                        sizeText = if (viewModel.stegoExtractFileSize > 0L) FileUtils.formatFileSize(viewModel.stegoExtractFileSize) else null,
                        thumbnail = viewModel.stegoExtractThumbnail,
                        isArchive = false,
                        onClick = {
                            stegoExtractLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                        }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 探测状态卡片
                    if (viewModel.isStegoInspecting) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(id = R.string.stego_inspecting),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        val inspection = viewModel.stegoInspection
                        if (inspection != null) {
                            if (inspection.hasArchive) {
                                // 检测到有效压缩包
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Found",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = stringResource(id = R.string.stego_found_archive),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        InspectionItem(
                                            name = stringResource(id = R.string.stego_archive_type),
                                            value = "${inspection.type.name} 压缩包 (.${inspection.type.extension})"
                                        )
                                        InspectionItem(
                                            name = stringResource(id = R.string.stego_archive_size),
                                            value = FileUtils.formatFileSize(inspection.archiveSize)
                                        )
                                        InspectionItem(
                                            name = stringResource(id = R.string.stego_inject_offset),
                                            value = "${inspection.offset} 字节处 (${FileUtils.formatFileSize(inspection.offset)})"
                                        )
                                    }
                                }
                            } else {
                                // 未检测到隐藏压缩包
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(id = R.string.stego_not_found_archive),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    val canExtract = viewModel.stegoExtractTargetUri != null &&
                            (viewModel.stegoInspection?.hasArchive == true) &&
                            !viewModel.isStegoProcessing

                    Button(
                        onClick = {
                            viewModel.extractStego(context) { outName ->
                                Toast.makeText(
                                    context,
                                    if (outName != null) "已提取至 Download/RePix: $outName" else context.getString(R.string.stego_failed),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        enabled = canExtract,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (viewModel.isStegoProcessing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "正在提取压缩包...")
                        } else {
                            val archType = viewModel.stegoInspection?.type?.name
                            val btnText = if (archType != null) "一键提取 $archType 压缩包" else stringResource(id = R.string.stego_btn_extract)
                            Text(text = btnText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InspectionItem(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StegoItemRow(
    label: String,
    filename: String?,
    sizeText: String?,
    thumbnail: android.graphics.Bitmap?,
    isArchive: Boolean,
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
                } else if (isArchive) {
                    Text(text = "📦", fontSize = 24.sp)
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

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = filename ?: "点击选择文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (filename != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (sizeText != null) {
                    Text(
                        text = "大小: $sizeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 11.sp
                    )
                }
            }

            Text(
                text = if (filename != null) "更换" else "选择",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
