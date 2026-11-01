package com.example.picencrypt.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.R
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.viewmodel.MainViewModel

@Composable
fun StegoScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val stegoCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> result.data?.data?.let { viewModel.stegoCoverUri = it } }
    )
    val stegoArchiveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> result.data?.data?.let { viewModel.stegoArchiveUri = it } }
    )
    val stegoExtractLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> result.data?.data?.let { viewModel.stegoExtractTargetUri = it } }
    )

    Card(
        modifier = modifier
            .fillMaxWidth(0.92f)
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(id = R.string.stego_tool_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text(text = stringResource(id = R.string.stego_create_mode), fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.stegoMode = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.stegoMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (viewModel.stegoMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text(text = stringResource(id = R.string.stego_extract_mode), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (viewModel.stegoMode == 0) {
                Button(
                    onClick = {
                        stegoCoverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.stego_select_cover))
                }
                Text(
                    text = viewModel.stegoCoverUri?.let { FileUtils.getUriFilename(context, it) } ?: "未选择封面",
                    fontSize = 12.sp,
                    color = if (viewModel.stegoCoverUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        stegoArchiveLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.stego_select_archive))
                }
                Text(
                    text = viewModel.stegoArchiveUri?.let { FileUtils.getUriFilename(context, it) } ?: "未选择压缩包",
                    fontSize = 12.sp,
                    color = if (viewModel.stegoArchiveUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (viewModel.isStegoProcessing) "正在合成..." else stringResource(id = R.string.stego_btn_create))
                }
            } else {
                Button(
                    onClick = {
                        stegoExtractLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(id = R.string.stego_select_target))
                }
                Text(
                    text = viewModel.stegoExtractTargetUri?.let { FileUtils.getUriFilename(context, it) } ?: "未选择图种文件",
                    fontSize = 12.sp,
                    color = if (viewModel.stegoExtractTargetUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.extractStego(context) { fileName ->
                            val msg = if (fileName != null) {
                                context.getString(R.string.stego_extract_success) + " ($fileName)"
                            } else {
                                context.getString(R.string.stego_no_archive_found)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        }
                    },
                    enabled = viewModel.stegoExtractTargetUri != null && !viewModel.isStegoProcessing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = if (viewModel.isStegoProcessing) "正在提取..." else stringResource(id = R.string.stego_btn_extract))
                }
            }
        }
    }
}
