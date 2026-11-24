package com.example.picencrypt.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.model.Algorithm
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.picencrypt.utils.ExportSizeEstimator
import com.example.picencrypt.utils.ExportFilenameGenerator
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalHapticFeedback
import com.example.picencrypt.model.AppThemeMode
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.utils.performHapticIf
import com.example.picencrypt.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodAndKeySettings(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isExpandedAllowed: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current

    ExposedDropdownMenuBox(
        expanded = viewModel.isDropdownMenuExpanded && isExpandedAllowed,
        onExpandedChange = {
            viewModel.isDropdownMenuExpanded = !viewModel.isDropdownMenuExpanded
        },
        modifier = modifier.padding(5.dp)
    ) {
        TextField(
            value = stringResource(id = viewModel.selectedAlgorithm.stringResId),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(text = stringResource(id = R.string.select_algorithm)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = viewModel.isDropdownMenuExpanded) },
            colors = TextFieldDefaults.colors(
                disabledTextColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, isExpandedAllowed)
                .clip(RoundedCornerShape(20.dp))
        )
        ExposedDropdownMenu(
            expanded = viewModel.isDropdownMenuExpanded && isExpandedAllowed,
            onDismissRequest = { viewModel.isDropdownMenuExpanded = false }
        ) {
            for (algo in Algorithm.entries) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(id = algo.stringResId)) },
                    onClick = {
                        viewModel.selectAlgorithm(algo)
                    }
                )
            }
        }
    }

    val keyboardType = if (Algorithm.isTomatoOrPicEncrypt(viewModel.selectedAlgorithm)) {
        KeyboardType.Decimal
    } else {
        KeyboardType.Text
    }

    TextField(
        value = viewModel.keyString,
        onValueChange = { viewModel.keyString = it },
        singleLine = true,
        label = { Text(text = stringResource(id = R.string.key_text_field_label)) },
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            disabledTextColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = Modifier
            .padding(vertical = 5.dp)
            .clip(RoundedCornerShape(20.dp))
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Text(
            text = stringResource(id = R.string.compat_mode_label),
            fontSize = 14.sp
        )
        Switch(
            checked = viewModel.isCompatMode,
            onCheckedChange = { viewModel.isCompatMode = it }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "触觉震动反馈",
                fontSize = 14.sp
            )
            Text(
                text = "操作完成与交互时振动确认",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = viewModel.isHapticFeedbackEnabled,
            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = stringResource(id = R.string.strip_exif_label),
                fontSize = 14.sp
            )
            Text(
                text = stringResource(id = R.string.strip_exif_desc),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = viewModel.isStripExifOnExport,
            onCheckedChange = {
                haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                viewModel.setStripExifOnExport(it)
            }
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Text(
            text = stringResource(id = R.string.theme_setting_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .width(280.dp)
    ) {
        for (themeMode in AppThemeMode.entries) {
            val isSelected = viewModel.appThemeMode == themeMode
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                        viewModel.setAppThemeMode(themeMode)
                    }
            ) {
                Text(
                    text = stringResource(id = themeMode.stringResId),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Text(
            text = stringResource(id = R.string.export_format_label),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .width(280.dp)
    ) {
        for (format in ImageExportFormat.entries) {
            val isSelected = viewModel.exportFormat == format
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = containerColor,
                contentColor = contentColor,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                        viewModel.setExportFormat(format)
                    }
            ) {
                Text(
                    text = format.extension.uppercase(),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // Quality adjustment slider & presets (for WEBP and JPEG)
    if (viewModel.exportFormat.supportsQualityAdjustment) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .width(280.dp)
        ) {
            Text(
                text = stringResource(id = R.string.export_quality_label),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                val qualityText = if (viewModel.exportFormat == ImageExportFormat.WEBP && viewModel.exportQuality >= 100) {
                    stringResource(id = R.string.export_quality_lossless)
                } else {
                    stringResource(id = R.string.export_quality_value, viewModel.exportQuality)
                }
                Text(
                    text = qualityText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Slider(
            value = viewModel.exportQuality.toFloat(),
            onValueChange = { newVal ->
                val q = newVal.roundToInt()
                if (q != viewModel.exportQuality) {
                    if (q % 5 == 0) {
                        haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                    }
                    viewModel.setExportQuality(q)
                }
            },
            valueRange = 50f..100f,
            steps = 49,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .width(280.dp)
        )

        // Presets Chips
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .width(280.dp)
        ) {
            val presets = listOf(
                Pair(95, stringResource(id = R.string.export_preset_high)),
                Pair(85, stringResource(id = R.string.export_preset_balanced)),
                Pair(70, stringResource(id = R.string.export_preset_compact))
            )
            for ((presetQ, label) in presets) {
                val isSelected = viewModel.exportQuality == presetQ
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                            viewModel.setExportQuality(presetQ)
                        }
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(vertical = 4.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // Size Estimation
    val sampleImage = viewModel.images.firstOrNull()
    val refW = if ((sampleImage?.originalWidth ?: 0) > 0) sampleImage!!.originalWidth else 1920
    val refH = if ((sampleImage?.originalHeight ?: 0) > 0) sampleImage!!.originalHeight else 1080
    val estimatedBytes = ExportSizeEstimator.estimateBytes(refW, refH, viewModel.exportFormat, viewModel.exportQuality)
    val formattedEst = ExportSizeEstimator.formatFileSize(estimatedBytes)
    val ratioPercent = ExportSizeEstimator.relativeRatioPercent(viewModel.exportFormat, viewModel.exportQuality)

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .width(280.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(id = R.string.export_size_estimate_label),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "~$formattedEst ($ratioPercent%)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    // Lossy Warning vs Lossless Badge
    if (viewModel.exportFormat.isLossy(viewModel.exportQuality)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .width(280.dp)
        ) {
            Text(
                text = stringResource(id = R.string.export_lossy_warning),
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    } else {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .width(280.dp)
        ) {
            Text(
                text = stringResource(id = R.string.export_lossless_badge),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(6.dp))

    // Export Filename Template Section
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .width(280.dp)
    ) {
        Text(
            text = stringResource(id = R.string.export_naming_section_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }

    // Preset Naming Chips
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .width(280.dp)
    ) {
        val presets = listOf(
            Pair(ExportFilenameGenerator.PATTERN_TIMESTAMP, stringResource(id = R.string.export_template_timestamp)),
            Pair(ExportFilenameGenerator.PATTERN_ORIGINAL_PREFIX, stringResource(id = R.string.export_template_keep_original)),
            Pair(ExportFilenameGenerator.PATTERN_ALGORITHM_TAGGED, stringResource(id = R.string.export_template_algo_tagged)),
            Pair(ExportFilenameGenerator.PATTERN_SEQUENTIAL, stringResource(id = R.string.export_template_sequential))
        )
        for ((pattern, label) in presets) {
            val isSelected = viewModel.exportFilenamePattern == pattern
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptic.performHapticIf(HapticFeedbackType.TextHandleMove, viewModel.isHapticFeedbackEnabled)
                        viewModel.setExportFilenamePattern(pattern)
                    }
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(vertical = 5.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // Custom Prefix TextField
    TextField(
        value = viewModel.exportCustomPrefix,
        onValueChange = { viewModel.setExportCustomPrefix(it) },
        label = { Text(stringResource(id = R.string.export_prefix_label), fontSize = 11.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
            unfocusedIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .width(280.dp)
    )

    // Real-time Preview Badge
    val previewSampleName = viewModel.images.firstOrNull()?.filename ?: "sample_photo.jpg"
    val previewFilename = ExportFilenameGenerator.generateFilename(
        template = viewModel.exportFilenamePattern,
        prefix = viewModel.exportCustomPrefix,
        originalFilename = previewSampleName,
        index = 0,
        algorithmName = viewModel.selectedAlgorithm.name,
        extension = viewModel.exportFormat.extension
    )

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .width(280.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text = "📁 " + stringResource(id = R.string.export_naming_preview_label) + ": ",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = previewFilename,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}
