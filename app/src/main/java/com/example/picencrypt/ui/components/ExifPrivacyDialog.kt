package com.example.picencrypt.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.model.MyImage

/**
 * EXIF Privacy metadata report and stripping dialog component.
 */
@Composable
fun ExifPrivacyDialog(
    image: MyImage,
    onDismiss: () -> Unit,
    onStripExif: () -> Unit
) {
    val info = image.exifInfo
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🛡️ ")
                Text(
                    text = stringResource(id = R.string.exif_privacy_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = image.filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (info == null || info.isClean) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Text(
                            text = "✨ 该图片暂无检测到 GPS 地理位置或敏感设备元数据，状态十分安全！",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    if (info.hasGps && info.formatCoordinates() != null) {
                        PrivacyInfoRow(
                            label = stringResource(id = R.string.exif_privacy_gps_label),
                            value = info.formatCoordinates()!!,
                            isWarning = true
                        )
                    }
                    val dev = info.getDeviceDescription()
                    if (dev != null) {
                        PrivacyInfoRow(
                            label = stringResource(id = R.string.exif_privacy_device_label),
                            value = dev,
                            isWarning = false
                        )
                    }
                    if (!info.dateTime.isNullOrBlank()) {
                        PrivacyInfoRow(
                            label = stringResource(id = R.string.exif_privacy_datetime_label),
                            value = info.dateTime,
                            isWarning = false
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ 包含 GPS 地理坐标的照片若在社交平台原图发送，可能会暴露精确居住或出行位置隐私。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            if (info != null && !info.isClean) {
                Button(
                    onClick = onStripExif,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(text = stringResource(id = R.string.exif_privacy_btn_strip))
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "关闭")
            }
        }
    )
}

@Composable
private fun PrivacyInfoRow(label: String, value: String, isWarning: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
