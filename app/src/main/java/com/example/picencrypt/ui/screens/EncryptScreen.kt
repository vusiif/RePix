package com.example.picencrypt.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.text.intl.Locale as ComposeLocale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.picencrypt.R
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.ui.components.MethodAndKeySettings
import com.example.picencrypt.ui.components.ImageViewerDialog
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.round

@Composable
fun EncryptScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var viewingImage by remember { mutableStateOf<com.example.picencrypt.model.MyImage?>(null) }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            val uri = result.data?.data
            val uris = mutableListOf<android.net.Uri>()
            if (uri == null) {
                val clipData = result.data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        uris.add(clipData.getItemAt(i).uri)
                    }
                }
            } else {
                uris.add(uri)
            }
            if (uris.isNotEmpty()) {
                Toast.makeText(context, context.getString(R.string.start_loading_images), Toast.LENGTH_SHORT).show()
                viewModel.addImages(uris, context) {
                    Toast.makeText(context, context.getString(R.string.image_loading_completed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    )

    fun checkKeyValid(): Boolean {
        val isValid = when (viewModel.selectedAlgorithm) {
            Algorithm.TOMATO -> Algorithm.checkTomatoKeyValidity(viewModel.keyString)
            Algorithm.PIC_ENCRYPT_ROW, Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN ->
                Algorithm.checkPicEncryptKeyValidity(viewModel.keyString)
            else -> true
        }
        if (!isValid) {
            val msg = if (viewModel.selectedAlgorithm == Algorithm.TOMATO) {
                context.getString(R.string.invalid_tomato_key_toast)
            } else {
                context.getString(R.string.invalid_picencrypt_key_toast)
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
        return isValid
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
        ) {
            item(key = "header_section") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // 顶部选择图片按钮
                    Button(
                        onClick = {
                            launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                type = "image/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            })
                        },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .padding(vertical = 6.dp)
                    ) {
                        Text(text = stringResource(id = R.string.select_image_btn))
                    }

                    if (viewModel.images.isNotEmpty()) {
                        val isEnableExpanded = !viewModel.isShowMethodAndKeySettingOnTopLayer
                        MethodAndKeySettings(
                            viewModel = viewModel,
                            isExpandedAllowed = isEnableExpanded
                        )

                        // 批量混淆与解混淆操作按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (checkKeyValid()) {
                                        Toast.makeText(context, context.getString(R.string.start_encryption), Toast.LENGTH_SHORT).show()
                                        viewModel.processAllImages(
                                            processType = ProcessType.ENCRYPT,
                                            onFallbackToast = {
                                                Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            onFailedToast = {
                                                Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            onFinish = { _, successCount, failCount ->
                                                val toastMsg = if (failCount == 0) {
                                                    context.getString(R.string.encryption_completed) + " (共 ${successCount} 张)"
                                                } else {
                                                    "完成混淆：成功 ${successCount} 张，失败 ${failCount} 张"
                                                }
                                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.encrypt_btn))
                            }

                            Button(
                                onClick = {
                                    if (checkKeyValid()) {
                                        Toast.makeText(context, context.getString(R.string.start_decryption), Toast.LENGTH_SHORT).show()
                                        viewModel.processAllImages(
                                            processType = ProcessType.DECRYPT,
                                            onFallbackToast = {
                                                Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            onFailedToast = {
                                                Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                            },
                                            onFinish = { _, successCount, failCount ->
                                                val toastMsg = if (failCount == 0) {
                                                    context.getString(R.string.decryption_completed) + " (共 ${successCount} 张)"
                                                } else {
                                                    "完成解混淆：成功 ${successCount} 张，失败 ${failCount} 张"
                                                }
                                                Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.decrypt_btn))
                            }
                        }

                        // 重置与清空按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Button(
                                onClick = {
                                    viewModel.reloadAllImages(
                                        onFileNotFound = {
                                            Toast.makeText(context, context.getString(R.string.file_not_found), Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.reset_btn))
                            }

                            Button(
                                onClick = { viewModel.clearAllImages() },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.clear_btn))
                            }
                        }

                        // 翻转180度与保存全部按钮
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp)
                        ) {
                            Button(
                                onClick = { viewModel.rotateAllImages() },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.rotate180_btn))
                            }

                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                        ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE")
                                        != PackageManager.PERMISSION_GRANTED
                                    ) {
                                        viewModel.isShowRequestWritePermissionDialog = true
                                        return@Button
                                    }

                                    Toast.makeText(context, context.getString(R.string.start_saving), Toast.LENGTH_SHORT).show()
                                    viewModel.saveAllImages { successCount, failCount ->
                                        val msg = if (failCount == 0) {
                                            context.getString(R.string.succeeded_save_image_toast) + " (${successCount}张)"
                                        } else {
                                            "保存完成：成功 ${successCount} 张，失败 ${failCount} 张"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.width(120.dp)
                            ) {
                                Text(text = stringResource(id = R.string.save_btn))
                            }
                        }

                        // 进度条与数量统计
                        if (viewModel.isShowProgress) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .padding(vertical = 5.dp)
                                    .fillMaxWidth()
                                    .height(30.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { viewModel.progress },
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .padding(horizontal = 10.dp)
                                )
                                val total = if (viewModel.newImageUriCount > 0) {
                                    round(viewModel.images.size.toFloat() / viewModel.progress.coerceAtLeast(0.001f)).toInt()
                                } else {
                                    viewModel.images.size
                                }
                                Text(
                                    text = "${round(total * viewModel.progress).toInt()}/$total",
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        } else {
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }
                }
            }

            // 图片列表使用高效 LazyColumn + 稳定 Key (P1-3)
            itemsIndexed(
                items = viewModel.images,
                key = { _, item -> item.id }
            ) { i, item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .padding(bottom = 15.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSystemInDarkTheme()) Color(50, 50, 50)
                            else Color(245, 240, 242)
                        )
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 文件名与图片展示
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 50.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 15.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SelectionContainer(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = item.filename,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.exifInfo?.hasGps == true) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            modifier = Modifier.padding(end = 6.dp)
                                        ) {
                                            Text(
                                                text = "📍 含GPS",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (item.originalWidth > 0 && item.originalHeight > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                        ) {
                                            Text(
                                                text = "${item.originalWidth} × ${item.originalHeight}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Image(
                                bitmap = item.thumbnail.asImageBitmap(),
                                contentDescription = stringResource(id = R.string.view_full_image_hint),
                                modifier = Modifier
                                    .padding(start = 15.dp, end = 15.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewingImage = item }
                            )

                            Text(
                                text = stringResource(id = R.string.view_full_image_hint),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .clickable { viewingImage = item }
                            )

                            // 混淆质量指标与直方图分析条 (F3)
                            val metrics = item.getMetrics()
                            var isExpandedMetrics by remember(item.thumbnail) { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 15.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isScrambled = metrics.scrambleScore >= 80f
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isScrambled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isScrambled) "🎲 混乱度: " else "🖼️ 混乱度: ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isScrambled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = String.format(ComposeLocale.current.platformLocale, "%.1f%% (%s)", metrics.scrambleScore, if (isScrambled) "高强混淆" else "原图特征"),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isScrambled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Text(
                                    text = if (isExpandedMetrics) "收起分析 ▲" else "直方图分析 ▼",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier
                                        .clickable { isExpandedMetrics = !isExpandedMetrics }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }

                            if (isExpandedMetrics) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 15.dp, end = 15.dp, bottom = 10.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "RGB 通道分布直方图 (暗部 -> 亮部)",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        HistogramChart(
                                            rHist = metrics.rHistogram,
                                            gHist = metrics.gHistogram,
                                            bHist = metrics.bHistogram,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(42.dp)
                                                .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = String.format(ComposeLocale.current.platformLocale, "香农信息熵: %.2f / 8.0 bits", metrics.entropy),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = String.format(ComposeLocale.current.platformLocale, "相邻相关度: %.3f", metrics.correlation),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 单图快捷操作浮动按钮列
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Top,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp)
                        ) {
                            // 删除
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Remove",
                                    modifier = Modifier
                                        .clickable { viewModel.removeImageAt(i) }
                                        .padding(10.dp)
                                )
                            }

                            // 独立设置
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    Icons.Rounded.Settings,
                                    contentDescription = "Settings",
                                    modifier = Modifier
                                        .clickable { viewModel.isShowMethodAndKeySettingOnTopLayer = true }
                                        .padding(10.dp)
                                )
                            }

                            // 独立解混淆 (ArrowBack -> DECRYPT)
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Decrypt",
                                    modifier = Modifier
                                        .clickable {
                                            if (checkKeyValid()) {
                                                Toast.makeText(context, context.getString(R.string.start_decryption), Toast.LENGTH_SHORT).show()
                                                viewModel.processSingleImage(
                                                    index = i,
                                                    processType = ProcessType.DECRYPT,
                                                    onFallbackToast = {
                                                        Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailedToast = {
                                                        Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                        .padding(10.dp)
                                )
                            }

                            // 独立混淆 (ArrowForward -> ENCRYPT)
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = "Encrypt",
                                    modifier = Modifier
                                        .clickable {
                                            if (checkKeyValid()) {
                                                Toast.makeText(context, context.getString(R.string.start_encryption), Toast.LENGTH_SHORT).show()
                                                viewModel.processSingleImage(
                                                    index = i,
                                                    processType = ProcessType.ENCRYPT,
                                                    onFallbackToast = {
                                                        Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                                    },
                                                    onFailedToast = {
                                                        Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                        .padding(10.dp)
                                )
                            }

                            // 旋转 180 度
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.crop_rotate_119243),
                                    contentDescription = "Rotate",
                                    modifier = Modifier
                                        .clickable { viewModel.rotateSingleImage(i) }
                                        .padding(10.dp)
                                )
                            }

                            // 重新加载/重置
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Reset",
                                    modifier = Modifier
                                        .clickable { viewModel.reloadSingleImage(i) }
                                        .padding(10.dp)
                                )
                            }

                            // 保存单张图片
                            Row(modifier = Modifier.clip(CircleShape)) {
                                Icon(
                                    painter = painterResource(id = R.drawable.import_download_icon_176152),
                                    contentDescription = "Save",
                                    modifier = Modifier
                                        .clickable {
                                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                                ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE")
                                                != PackageManager.PERMISSION_GRANTED
                                            ) {
                                                viewModel.isShowRequestWritePermissionDialog = true
                                                return@clickable
                                            }

                                            Toast.makeText(context, context.getString(R.string.start_saving), Toast.LENGTH_SHORT).show()
                                            viewModel.saveSingleImage(i) { success ->
                                                Toast.makeText(
                                                    context,
                                                    if (success) context.getString(R.string.succeeded_save_image_toast)
                                                    else context.getString(R.string.failed_save_image_toast),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 底部占位留白，防止被悬浮按钮挡住
            item(key = "footer_spacer") {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        // 右下角悬浮按钮（添加更多图片、回滚到顶部）
        if (viewModel.images.isNotEmpty()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(30.dp)
            ) {
                // 悬浮添加按钮
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(149, 117, 205, 255))
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = "Add",
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                })
                            }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 回滚到顶部按钮
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(149, 117, 205, 255))
                ) {
                    Icon(
                        Icons.Rounded.KeyboardArrowUp,
                        contentDescription = "Scroll to top",
                        modifier = Modifier
                            .size(40.dp)
                            .clickable {
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            }
                    )
                }
            }
        }
        val currentViewing = viewingImage
        if (currentViewing != null) {
            ImageViewerDialog(
                bitmap = currentViewing.bitmap,
                title = currentViewing.filename,
                subtitle = "${currentViewing.originalWidth} × ${currentViewing.originalHeight}",
                onDismiss = { viewingImage = null }
            )
        }
    }
}

/**
 * 经典 RGB 三通道直方图分布图组件
 */
@Composable
fun HistogramChart(
    rHist: FloatArray,
    gHist: FloatArray,
    bHist: FloatArray,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val binCount = 32
        val binWidth = w / binCount

        fun drawChannel(hist: FloatArray, color: Color) {
            val path = Path()
            path.moveTo(0f, h)
            for (i in 0 until binCount) {
                val value = hist.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                val x = i * binWidth + binWidth / 2f
                val y = h - (value * h * 0.88f)
                path.lineTo(x, y)
            }
            path.lineTo(w, h)
            path.close()
            drawPath(path, color.copy(alpha = 0.32f))
        }

        drawChannel(rHist, Color.Red)
        drawChannel(gHist, Color.Green)
        drawChannel(bHist, Color.Blue)
    }
}
