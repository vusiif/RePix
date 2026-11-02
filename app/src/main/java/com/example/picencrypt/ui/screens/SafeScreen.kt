package com.example.picencrypt.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.picencrypt.utils.performHapticIf
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.PasswordStrength
import com.example.picencrypt.utils.PasswordStrengthEvaluator
import androidx.compose.ui.graphics.Color
import com.example.picencrypt.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    var isPasswordVisible by remember { mutableStateOf(false) }

    // File Pickers
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.setSafeSourceFile(uri, context)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 模式切换 (加密 / 解密)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (viewModel.safeMode == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                            viewModel.safeMode = 0
                            viewModel.safeSourceUri = null
                            viewModel.safeSourceFileName = ""
                            viewModel.safeSourceFileSize = 0L
                            viewModel.safeRpxHeader = null
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.safe_encrypt_mode),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if (viewModel.safeMode == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (viewModel.safeMode == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (viewModel.safeMode == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                            viewModel.safeMode = 1
                            viewModel.safeSourceUri = null
                            viewModel.safeSourceFileName = ""
                            viewModel.safeSourceFileSize = 0L
                            viewModel.safeRpxHeader = null
                        }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.safe_decrypt_mode),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if (viewModel.safeMode == 1) FontWeight.Bold else FontWeight.Normal,
                        color = if (viewModel.safeMode == 1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 密码学安全说明卡片
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(24.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(id = R.string.safe_security_notice),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 文件选择区域
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (viewModel.safeMode == 0)
                            stringResource(id = R.string.safe_select_file_to_encrypt)
                        else
                            stringResource(id = R.string.safe_select_file_to_decrypt),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (viewModel.safeSourceUri == null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .size(56.dp)
                                .clickable {
                                    if (viewModel.safeMode == 0) {
                                        filePicker.launch("*/*")
                                    } else {
                                        filePicker.launch("*/*")
                                    }
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Select File",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    } else {
                        // 显示已选文件信息
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = viewModel.safeSourceFileName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${stringResource(id = R.string.safe_file_size)}: ${FileUtils.formatFileSize(viewModel.safeSourceFileSize)}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = {
                                    filePicker.launch("*/*")
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "更换", fontSize = 12.sp)
                            }
                        }

                        // 如果解密模式下成功探测到内部文件信息
                        val inspected = viewModel.safeRpxHeader
                        if (inspected != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${stringResource(id = R.string.safe_inspect_orig_name)}: ${inspected.originalFilename}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "${stringResource(id = R.string.safe_inspect_orig_size)}: ${FileUtils.formatFileSize(inspected.plainLength)}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 密码输入框
            OutlinedTextField(
                value = viewModel.safePassword,
                onValueChange = { viewModel.safePassword = it },
                label = { Text(text = stringResource(id = R.string.safe_password_label)) },
                placeholder = { Text(text = stringResource(id = R.string.safe_password_hint)) },
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Rounded.Info else Icons.Rounded.Lock,
                            contentDescription = "Toggle Password Visibility"
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // 加密模式下的密码强度实时指示条
            if (viewModel.safeMode == 0 && viewModel.safePassword.isNotEmpty()) {
                val strength = PasswordStrengthEvaluator.evaluate(viewModel.safePassword)
                val (strengthColor, strengthLabel) = when (strength) {
                    PasswordStrength.VERY_WEAK -> Pair(Color(0xFFE53935), stringResource(id = R.string.password_strength_very_weak))
                    PasswordStrength.WEAK -> Pair(Color(0xFFFB8C00), stringResource(id = R.string.password_strength_weak))
                    PasswordStrength.GOOD -> Pair(Color(0xFF1E88E5), stringResource(id = R.string.password_strength_good))
                    PasswordStrength.STRONG -> Pair(Color(0xFF43A047), stringResource(id = R.string.password_strength_strong))
                    PasswordStrength.EMPTY -> Pair(Color.Transparent, "")
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(id = R.string.password_strength_label)}$strengthLabel",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = strengthColor
                    )
                    Text(
                        text = stringResource(id = R.string.password_strength_hint),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { strength.scoreRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = strengthColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 执行操作按钮
            Button(
                onClick = {
                    if (viewModel.safeMode == 0) {
                        viewModel.executeSafeEncryption(context) { success, msg ->
                            if (success) {
                                haptic.performHapticIf(HapticFeedbackType.LongPress, viewModel.isHapticFeedbackEnabled)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        viewModel.executeSafeDecryption(context) { success, msg ->
                            if (success) {
                                haptic.performHapticIf(HapticFeedbackType.LongPress, viewModel.isHapticFeedbackEnabled)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = viewModel.safeSourceUri != null && viewModel.safePassword.isNotEmpty() && !viewModel.isSafeProcessing,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                if (viewModel.isSafeProcessing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(text = "正在安全计算...")
                } else {
                    Text(
                        text = if (viewModel.safeMode == 0)
                            stringResource(id = R.string.safe_btn_encrypt)
                        else
                            stringResource(id = R.string.safe_btn_decrypt),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
