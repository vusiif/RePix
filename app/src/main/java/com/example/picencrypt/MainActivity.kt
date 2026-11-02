package com.example.picencrypt

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.picencrypt.ui.components.AppAboutDialog
import com.example.picencrypt.ui.components.MethodAndKeySettings
import com.example.picencrypt.ui.screens.EncryptScreen
import com.example.picencrypt.ui.screens.MirageScreen
import com.example.picencrypt.ui.screens.SafeScreen
import com.example.picencrypt.ui.screens.StegoScreen
import com.example.picencrypt.ui.theme.PicEncryptTheme
import com.example.picencrypt.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicEncryptTheme {
                MainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = stringResource(id = R.string.app_name)) },
                    navigationIcon = {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(80.dp)
                        )
                    },
                    actions = {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(id = R.string.settings_title),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clickable {
                                    viewModel.isShowMethodAndKeySettingOnTopLayer = true
                                }
                        )
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "About",
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .clickable {
                                    viewModel.isShowInfo = !viewModel.isShowInfo
                                }
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = viewModel.currentTab == 0,
                        onClick = { viewModel.currentTab = 0 },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_background),
                                contentDescription = stringResource(id = R.string.tab_encrypt),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(text = stringResource(id = R.string.tab_encrypt)) }
                    )
                    NavigationBarItem(
                        selected = viewModel.currentTab == 1,
                        onClick = { viewModel.currentTab = 1 },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.import_download_icon_176152),
                                contentDescription = stringResource(id = R.string.tab_stego),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(text = stringResource(id = R.string.tab_stego)) }
                    )
                    NavigationBarItem(
                        selected = viewModel.currentTab == 2,
                        onClick = { viewModel.currentTab = 2 },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.crop_rotate_119243),
                                contentDescription = stringResource(id = R.string.tab_mirage),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(text = stringResource(id = R.string.tab_mirage)) }
                    )
                    NavigationBarItem(
                        selected = viewModel.currentTab == 3,
                        onClick = { viewModel.currentTab = 3 },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(id = R.string.tab_safe),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = { Text(text = stringResource(id = R.string.tab_safe)) }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // 页面路由
                when (viewModel.currentTab) {
                    0 -> EncryptScreen(viewModel = viewModel)
                    1 -> StegoScreen(viewModel = viewModel)
                    2 -> MirageScreen(viewModel = viewModel)
                    3 -> SafeScreen(viewModel = viewModel)
                }

                // 顶层全局设置弹层
                if (viewModel.isShowMethodAndKeySettingOnTopLayer) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0, 0, 0, 180))
                            .clickable {
                                viewModel.isShowMethodAndKeySettingOnTopLayer = false
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable(enabled = false) {}
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(id = R.string.settings_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { viewModel.isShowMethodAndKeySettingOnTopLayer = false }
                                        .padding(4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            MethodAndKeySettings(viewModel = viewModel)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { viewModel.isShowMethodAndKeySettingOnTopLayer = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("完成")
                            }
                        }
                    }
                }

                // 关于信息弹层
                if (viewModel.isShowInfo) {
                    AppAboutDialog(
                        onDismiss = { viewModel.isShowInfo = false }
                    )
                }

                // 存储权限对话框
                if (viewModel.isShowRequestWritePermissionDialog) {
                    AlertDialog(
                        title = { Text(text = stringResource(id = R.string.request_storage_permission_dialog_title)) },
                        text = { Text(text = stringResource(id = R.string.request_storage_permission_dialog_text)) },
                        onDismissRequest = { viewModel.isShowRequestWritePermissionDialog = false },
                        confirmButton = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(onClick = {
                                    ActivityCompat.requestPermissions(
                                        context as Activity,
                                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                                        0
                                    )
                                    viewModel.isShowRequestWritePermissionDialog = false
                                }) {
                                    Text("Confirm")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
