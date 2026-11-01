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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
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
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (viewModel.images.isNotEmpty()) Arrangement.Top else Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 120.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
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
                                    onFinish = {
                                        Toast.makeText(context, context.getString(R.string.encryption_completed), Toast.LENGTH_SHORT).show()
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
                                    onFinish = {
                                        Toast.makeText(context, context.getString(R.string.decryption_completed), Toast.LENGTH_SHORT).show()
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
                            viewModel.saveAllImages {
                                Toast.makeText(context, context.getString(R.string.succeeded_save_image_toast), Toast.LENGTH_LONG).show()
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
                    Spacer(modifier = Modifier.height(30.dp))
                }

                // 图片列表卡片
                for (i in viewModel.images.indices) {
                    val item = viewModel.images[i]
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
                                SelectionContainer {
                                    Text(
                                        text = item.filename,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 15.dp)
                                    )
                                }

                                Image(
                                    bitmap = item.thumbnail.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(start = 15.dp, end = 15.dp, bottom = 10.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                )
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
                                        Icons.Rounded.ArrowBack,
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
                                        Icons.Rounded.ArrowForward,
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
                                    scrollState.animateScrollTo(0, TweenSpec(800))
                                }
                            }
                    )
                }
            }
        }
    }
}
