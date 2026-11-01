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
fun MirageScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val mirageCoverLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> result.data?.data?.let { viewModel.mirageCoverUri = it } }
    )
    val mirageHiddenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result -> result.data?.data?.let { viewModel.mirageHiddenUri = it } }
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
                text = stringResource(id = R.string.mirage_tool_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { viewModel.mirageIsColoredMode = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).padding(end = 4.dp)
                ) {
                    Text(text = stringResource(id = R.string.mirage_colored_mode), fontSize = 13.sp)
                }
                Button(
                    onClick = { viewModel.mirageIsColoredMode = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (!viewModel.mirageIsColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                ) {
                    Text(text = stringResource(id = R.string.mirage_gray_mode), fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    mirageCoverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.mirage_select_cover))
            }
            Text(
                text = viewModel.mirageCoverUri?.let { FileUtils.getUriFilename(context, it) } ?: "未选择表图",
                fontSize = 12.sp,
                color = if (viewModel.mirageCoverUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    mirageHiddenLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.mirage_select_hidden))
            }
            Text(
                text = viewModel.mirageHiddenUri?.let { FileUtils.getUriFilename(context, it) } ?: "未选择里图",
                fontSize = 12.sp,
                color = if (viewModel.mirageHiddenUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    viewModel.generateMirage(context) { saved ->
                        Toast.makeText(
                            context,
                            if (saved) context.getString(R.string.mirage_success) else context.getString(R.string.mirage_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = viewModel.mirageCoverUri != null && viewModel.mirageHiddenUri != null && !viewModel.isMirageProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (viewModel.isMirageProcessing) "正在生成..." else stringResource(id = R.string.mirage_btn_generate))
            }
        }
    }
}
