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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MethodAndKeySettings(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    isExpandedAllowed: Boolean = true
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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
            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it, context) }
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
                    .clickable { viewModel.exportFormat = format }
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

    val (hintText, containerColor, textColor) = when (viewModel.exportFormat) {
        ImageExportFormat.PNG -> Triple(
            stringResource(id = R.string.export_hint_png),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        ImageExportFormat.WEBP -> Triple(
            stringResource(id = R.string.export_hint_webp),
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        ImageExportFormat.JPEG -> Triple(
            stringResource(id = R.string.export_hint_jpeg),
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .width(280.dp)
    ) {
        Text(
            text = hintText,
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
