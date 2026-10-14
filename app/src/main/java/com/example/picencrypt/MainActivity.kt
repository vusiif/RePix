package com.example.picencrypt

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.picencrypt.ui.theme.PicEncryptTheme
import androidx.compose.material3.Switch
import com.example.picencrypt.utils.NativeImageScramble
import com.example.picencrypt.utils.NativeShuffleBridge
import com.example.picencrypt.utils.BlockScramble
import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.PerPixelScramble
import com.example.picencrypt.utils.PicEncryptRowColumnScramble
import com.example.picencrypt.utils.PicEncryptRowScramble
import com.example.picencrypt.utils.RowPixelScramble
import com.example.picencrypt.utils.TomatoScramble
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import com.example.picencrypt.utils.SteganographyHelper
import com.example.picencrypt.utils.MirageTankBridge
import java.util.Date
import kotlin.concurrent.thread
import kotlin.math.round

enum class Algorithm {
    TOMATO,
    BLOCK,
    ROW_PIXEL,
    PER_PIXEL,
    PIC_ENCRYPT_ROW,
    PIC_ENCRYPT_ROW_AND_COLUMN,
    LCG_SHUFFLE
}

const val defaultTomatoKey = 1
const val defaultOtherKey = 0.666

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicEncrypt()
        }
    }
}

fun isTomatoOrPicEncryptAlgorithm(algorithm: Algorithm): Boolean {
    return algorithm == Algorithm.TOMATO || algorithm == Algorithm.PIC_ENCRYPT_ROW || algorithm == Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN
}

fun checkTomatoKeyValidity(key: String): Boolean {
    try {
        key.toDouble()
    } catch (e: NumberFormatException) {
        return false
    }
    return key.toDouble() > 0 && key.toDouble() <= 1.618
}

fun checkPicEncryptKeyValidity(key: String): Boolean {
    try {
        key.toDouble()
    } catch (e: NumberFormatException) {
        return false
    }
    return key.toDouble() > 0 && key.toDouble() < 1
}

fun getUriFilename(context: android.content.Context, uri: Uri): String {
    var name = ""
    val cursor = context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (idx != -1) {
                name = it.getString(idx) ?: ""
            }
        }
    }
    if (name.isEmpty()) {
        name = uri.lastPathSegment ?: "unknown"
    }
    return name
}

fun saveStegoImage(context: android.content.Context, coverUri: Uri, archiveUri: Uri): Boolean {
    val appName = context.getString(R.string.app_name)
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val coverName = getUriFilename(context, coverUri)
    val isJpg = coverName.endsWith(".jpg", ignoreCase = true) || coverName.endsWith(".jpeg", ignoreCase = true)
    val ext = if (isJpg) "jpg" else "png"
    val mimeType = if (isJpg) "image/jpeg" else "image/png"
    val fileName = "Stego_${timeStamp}.$ext"

    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/$appName")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                val coverIn = context.contentResolver.openInputStream(coverUri) ?: return false
                val archIn = context.contentResolver.openInputStream(archiveUri) ?: return false
                SteganographyHelper.createStegoImage(coverIn, archIn, out)
            } ?: return false
        } else {
            val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), appName)
            if (!saveDir.exists()) saveDir.mkdirs()
            val outFile = File(saveDir, fileName)
            FileOutputStream(outFile).use { out ->
                val coverIn = context.contentResolver.openInputStream(coverUri) ?: return false
                val archIn = context.contentResolver.openInputStream(archiveUri) ?: return false
                SteganographyHelper.createStegoImage(coverIn, archIn, out)
            }
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://${outFile.absolutePath}")))
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun extractArchiveFromStego(context: android.content.Context, stegoUri: Uri): String? {
    val appName = context.getString(R.string.app_name)
    val cacheFile = File(context.cacheDir, "temp_stego_${System.currentTimeMillis()}.bin")
    return try {
        context.contentResolver.openInputStream(stegoUri)?.use { input ->
            FileOutputStream(cacheFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        val detection = SteganographyHelper.detectArchiveOffset(cacheFile) ?: return null
        val ext = detection.type.extension
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFileName = "Extracted_${timeStamp}.$ext"

        val extractedTempFile = File(context.cacheDir, "temp_extracted_${System.currentTimeMillis()}.$ext")
        val success = SteganographyHelper.extractArchive(cacheFile, extractedTempFile)
        if (!success || !extractedTempFile.exists() || extractedTempFile.length() == 0L) {
            extractedTempFile.delete()
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, outFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/$appName")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                FileInputStream(extractedTempFile).use { input ->
                    input.copyTo(out)
                }
            } ?: return null
        } else {
            val saveDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), appName)
            if (!saveDir.exists()) saveDir.mkdirs()
            val outFile = File(saveDir, outFileName)
            FileInputStream(extractedTempFile).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        extractedTempFile.delete()
        outFileName
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        if (cacheFile.exists()) cacheFile.delete()
    }
}

@SuppressLint("SimpleDateFormat", "UnusedMaterial3ScaffoldPaddingParameter")
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PicEncrypt(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    class MyImage(val uri: Uri) {
        var filename: String
        var bitmap: Bitmap
        var thumbnail: Bitmap

        init {
            filename = loadFilename()
            bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            thumbnail = loadThumbnail()
        }

        fun loadFilename(): String {
            val newFilename: String
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val cursor =
                context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                cursor.moveToFirst()
                val filenameIndex =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                newFilename = cursor.getString(filenameIndex).toString()
                cursor.close()
            } else {
                newFilename = ""
            }

            return newFilename
        }

        fun loadThumbnail(): Bitmap {
            val maxSize = 800
            val ratio: Float = if (bitmap.width > bitmap.height) {
                if (bitmap.width > maxSize) bitmap.width.toFloat() / maxSize else 1f
            } else {
                if (bitmap.height > maxSize) bitmap.height.toFloat() / maxSize else 1f
            }
            return ThumbnailUtils.extractThumbnail(bitmap, (bitmap.width.toFloat() / ratio).toInt(), (bitmap.height.toFloat() / ratio).toInt())
        }

        fun process(
            algorithm: Algorithm,
            processType: ProcessType,
            key: String = "0.666",
            forceCompat: Boolean = false,
            onFallback: (() -> Unit)? = null
        ): Boolean {
            // LCG_SHUFFLE: Direct Zero-Copy in-place processing on Bitmap
            if (algorithm == Algorithm.LCG_SHUFFLE) {
                val seed = NativeShuffleBridge.stringToSeed(key)
                NativeShuffleBridge.scrambleBitmap(bitmap, seed, processType == ProcessType.DECRYPT)
                thumbnail = loadThumbnail()
                return true
            }

            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val algoCode = when (algorithm) {
                Algorithm.TOMATO -> 0
                Algorithm.BLOCK -> 1
                Algorithm.ROW_PIXEL -> 2
                Algorithm.PER_PIXEL -> 3
                Algorithm.PIC_ENCRYPT_ROW -> 4
                Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN -> 5
                else -> 0
            }

            var image: Image? = null
            var didFallback = false

            // Default: Use C++ Native Engine
            if (!forceCompat && NativeImageScramble.isAvailable()) {
                try {
                    image = NativeImageScramble.process(pixels, bitmap.width, bitmap.height, algoCode, processType, key)
                } catch (t: Throwable) {
                    image = null
                }
            }

            // Fallback / Compat Mode: Use legacy Java implementation
            if (image == null) {
                if (!forceCompat) {
                    didFallback = true
                }
                try {
                    val fallbackImage = Image(pixels, bitmap.width, bitmap.height)
                    image = when (algorithm) {
                        Algorithm.TOMATO -> TomatoScramble(fallbackImage, key.toDoubleOrNull() ?: 1.0).process(processType)
                        Algorithm.BLOCK -> BlockScramble(fallbackImage, key).process(processType)
                        Algorithm.ROW_PIXEL -> RowPixelScramble(fallbackImage, key).process(processType)
                        Algorithm.PER_PIXEL -> PerPixelScramble(fallbackImage, key).process(processType)
                        Algorithm.PIC_ENCRYPT_ROW -> PicEncryptRowScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                        Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN -> PicEncryptRowColumnScramble(fallbackImage, key.toDoubleOrNull() ?: 0.5).process(processType)
                        else -> fallbackImage
                    }
                } catch (t: Throwable) {
                    return false
                }
            }

            if (didFallback) {
                onFallback?.invoke()
            }

            bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            thumbnail = loadThumbnail()
            return true
        }

        fun rotate(degree: Float) {
            val matrix = Matrix()
            matrix.setRotate(degree)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            thumbnail = loadThumbnail()
        }

        fun reload() {
            filename = loadFilename()
            bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            thumbnail = loadThumbnail()
        }

        fun save(index: Int = 0): Boolean {
            val dateTime =
                SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            var ms = System.currentTimeMillis()
            ms -= ms / 1000 * 1000 + index
            val s = String.format("%03d", ms)
            val imageName = "PicEncrypt_${dateTime + s}.png"
            val appName = context.getString(R.string.app_name)
            val saveDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ), appName
            )
            var isSaveImageSucceeded = false
            var fileAbsPath = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        imageName
                    )
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "image/png"
                    )
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Pictures/${appName}"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)
                        .use { outputStream ->
                            if (outputStream?.let { it1 ->
                                    bitmap.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        it1
                                    )
                                }!!) {
                                fileAbsPath =
                                    "${saveDir}/Pictures/${appName}/" + imageName
                                isSaveImageSucceeded = true
                            }
                        }
                }

            } else {
                if (!saveDir.exists()) {
                    saveDir.mkdir()
                }
                val filePath = File(saveDir, imageName)
                try {
                    val outputStream =
                        FileOutputStream(
                            File(
                                saveDir,
                                imageName
                            )
                        )
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        outputStream
                    )
                    outputStream.flush()
                    outputStream.close()

                    // Update album to add new image.
                    context.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.parse("file://" + filePath.absolutePath)
                        )
                    )
                    fileAbsPath = filePath.absolutePath
                    isSaveImageSucceeded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return isSaveImageSucceeded
        }

    }

    val focusManager = LocalFocusManager.current
    val images = ArrayList<MyImage>()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var newImageUriCount by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var isShowProgress by remember { mutableStateOf(false) }
    var isShowMethodAndKeySettingOnTopLayer by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf(true) }
    var selectedAlgorithm by remember { mutableStateOf(Algorithm.TOMATO) }
    var keyString by remember { mutableStateOf("1") }
    var isDropdownMenuExpanded by remember { mutableStateOf(false) }
    var isShowRequestWritePermissionDialog by remember { mutableStateOf(false) }
    var isCompatMode by remember { mutableStateOf(false) }
    var isShowStegoDialog by remember { mutableStateOf(false) }
    var isShowMirageDialog by remember { mutableStateOf(false) }

    // Select and add new images.
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            val uri = it.data?.data
            val newImageUris = ArrayList<Uri>()
            if (uri == null) {
                val imagesName = it.data?.clipData
                if (imagesName != null) {
                    for (i in 0 until imagesName.itemCount) {
                        newImageUris.add(imagesName.getItemAt(i).uri)
                    }
                }
            } else {
                newImageUris.add(uri)
            }

            if (newImageUris.size >= 1) {
                Toast.makeText(
                    context,
                    context.getString(R.string.start_loading_images),
                    Toast.LENGTH_SHORT
                ).show()
                thread {
                    isShowProgress = true
                    progress = 0f
                    newImageUriCount = newImageUris.size
                    for (i in 0 until newImageUris.size) {
                        images.add(MyImage(newImageUris[i]))
                        progress = (i + 1).toFloat() / newImageUris.size
                        update = !update
                    }
                    newImageUriCount = 0
                    isShowProgress = false
                    Looper.prepare()
                    Toast.makeText(
                        context,
                        context.getString(R.string.image_loading_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                    Looper.loop()
                }
            }
        }
    )

    // Show controls for algorithm selection and password input.
    @Composable
    fun SetMethodAndKey(isExpanded: Boolean = true) {
        val itemStringIds = arrayOf(
            R.string.tomato_algorithm,
            R.string.block_algorithm,
            R.string.row_pixel_algorithm,
            R.string.per_pixel_algorithm,
            R.string.pic_encrypt_row_pixel,
            R.string.pic_encrypt_row_and_column_pixel,
            R.string.lcg_shuffle_algorithm
        )

        ExposedDropdownMenuBox(
            expanded = isDropdownMenuExpanded && isExpanded,
            onExpandedChange = {
                isDropdownMenuExpanded = !isDropdownMenuExpanded
            },
            modifier.padding(5.dp)
        ) {
            TextField(
                value = stringResource(id = itemStringIds[selectedAlgorithm.ordinal]),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.select_algorithm)) },
                colors = TextFieldDefaults.colors(
                    disabledTextColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .menuAnchor()
                    .clip(RoundedCornerShape(20.dp))
            )
            ExposedDropdownMenu(
                expanded = isDropdownMenuExpanded && isExpanded,
                onDismissRequest = { isDropdownMenuExpanded = false }
            ) {
                for (algorithm in Algorithm.values()) {
                    val itemStringId = itemStringIds[algorithm.ordinal]
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = itemStringId)) },
                        onClick = {
                            if (selectedAlgorithm != algorithm) {
                                if (algorithm == Algorithm.TOMATO) {
                                    keyString = defaultTomatoKey.toString()
                                } else if (selectedAlgorithm == Algorithm.TOMATO){
                                    keyString = defaultOtherKey.toString()
                                }
                            }
                            selectedAlgorithm = algorithm
                            isDropdownMenuExpanded = false
                        }
                    )
                }
            }
        }

        val keyboardType: KeyboardType =
            if (isTomatoOrPicEncryptAlgorithm(selectedAlgorithm)) {
                KeyboardType.Decimal
            } else {
                KeyboardType.Text
            }

        TextField(
            value = keyString,
            onValueChange = { keyString = it },
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
                checked = isCompatMode,
                onCheckedChange = { isCompatMode = it }
            )
        }
    }

    PicEncryptTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
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
                                modifier = Modifier
                                    .size(80.dp)
                            )
                        },
                        actions = {
                            Icon(
                                painter = painterResource(id = R.drawable.crop_rotate_119243),
                                contentDescription = stringResource(id = R.string.mirage_tool_title),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable {
                                        isShowMirageDialog = true
                                    }
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.import_download_icon_176152),
                                contentDescription = stringResource(id = R.string.stego_tool_title),
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .clickable {
                                        isShowStegoDialog = true
                                    }
                            )
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 15.dp)
                                    .clickable {
                                        isShowInfo = !isShowInfo
                                    }
                            )
                        }
                    )
                }
            ) {
                if (isShowRequestWritePermissionDialog) {
                    AlertDialog(
                        title = { Text(text = stringResource(id = R.string.request_storage_permission_dialog_title)) },
                        text = { Text(text = stringResource(id = R.string.request_storage_permission_dialog_text)) },
                        onDismissRequest = {},
                        confirmButton = {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(onClick = {
                                    ActivityCompat.requestPermissions(
                                        context as Activity,
                                        arrayOf("android.permission.WRITE_EXTERNAL_STORAGE"),
                                        0
                                    )
                                    isShowRequestWritePermissionDialog = false
                                }) {
                                    Text("Confirm")
                                }
                            }
                        }
                    )
                }

                Box {
                    Spacer(modifier = Modifier.height((if (update) 90 else 90).dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (images.size > 0) Arrangement.Top else Arrangement.Center,
                        modifier = modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(0.dp, 0.dp, 0.dp, 150.dp)
                            .pointerInput(key1 = null) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                })
                            }
                    ) {
                        Spacer(modifier = Modifier.height((if (update) 90 else 90).dp))

                        // Select new images & Stego tool.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Button(
                                onClick = {
                                    launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                        type = "image/*"
                                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    })
                                },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                            ) {
                                Text(text = stringResource(id = R.string.select_image_btn))
                            }

                            Button(
                                onClick = { isShowStegoDialog = true },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                            ) {
                                Text(text = stringResource(id = R.string.stego_tool_title))
                            }

                            Button(
                                onClick = { isShowMirageDialog = true },
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                            ) {
                                Text(text = stringResource(id = R.string.mirage_tool_title))
                            }
                        }

                        if (images.size > 0) {
                            val isEnableExpanded = !isShowMethodAndKeySettingOnTopLayer
                            SetMethodAndKey(isEnableExpanded)

                            // Buttons for encryption and decryption
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {

                                fun processImage(processType: ProcessType) {
                                    val isValid = when (selectedAlgorithm) {
                                        Algorithm.TOMATO -> checkTomatoKeyValidity(keyString)
                                        in arrayOf(Algorithm.PIC_ENCRYPT_ROW, Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN) -> checkPicEncryptKeyValidity(keyString)
                                        else -> true
                                    }
                                    if (!isValid) {
                                        val msg = if (selectedAlgorithm == Algorithm.TOMATO) {
                                            context.getString(R.string.invalid_tomato_key_toast)
                                        } else {
                                            context.getString(R.string.invalid_picencrypt_key_toast)
                                        }
                                        Toast.makeText(
                                            context,
                                            msg,
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return
                                    }

                                    val prompt =
                                        if (processType == ProcessType.ENCRYPT) {
                                            context.getString(R.string.start_encryption)
                                        } else {
                                            context.getString(R.string.start_decryption)
                                        }

                                    Toast.makeText(
                                        context,
                                        prompt,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    thread {
                                        isShowProgress = true
                                        progress = 0f
                                        val tmpImages = images.clone() as ArrayList<MyImage>
                                        for (i in 0 until tmpImages.size) {
                                            if (images.contains(tmpImages[i])) {
                                                var hadFallback = false
                                                val success = tmpImages[i].process(
                                                    selectedAlgorithm,
                                                    processType,
                                                    keyString,
                                                    forceCompat = isCompatMode,
                                                    onFallback = { hadFallback = true }
                                                )
                                                if (hadFallback) {
                                                    Looper.prepare()
                                                    Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                                    Looper.loop()
                                                } else if (!success) {
                                                    Looper.prepare()
                                                    Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                                    Looper.loop()
                                                }
                                            }
                                            progress = (i + 1).toFloat() / tmpImages.size
                                            update = !update
                                        }
                                        tmpImages.clear()
                                        isShowProgress = false

                                        Looper.prepare()
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                if (processType == ProcessType.ENCRYPT)
                                                    R.string.encryption_completed
                                                else R.string.decryption_completed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Looper.loop()
                                    }
                                }

                                Button(
                                    onClick = { processImage(ProcessType.ENCRYPT) },
                                    Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.encrypt_btn))
                                }

                                Button(
                                    onClick = { processImage(ProcessType.DECRYPT)  },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.decrypt_btn))
                                }
                            }

                            // Buttons for Reset and Clear.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Button(
                                    onClick = {
                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            val tmpImages = images.clone() as ArrayList<MyImage>
                                            for (i in tmpImages.size - 1 downTo 0) {
                                                if (images.contains(tmpImages[i])) {
                                                    try {
                                                        tmpImages[i].reload()
                                                    } catch (e: FileNotFoundException) {
                                                        tmpImages.removeAt(i)
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.file_not_found),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                                progress = (tmpImages.size - i).toFloat() / tmpImages.size
                                                update = !update
                                            }
                                            tmpImages.clear()
                                            isShowProgress = false
                                        }
                                    },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.reset_btn))
                                }

                                Button(
                                    onClick = {
                                        images.clear()
                                        update = !update
                                    },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.clear_btn))
                                }
                            }

                            // Buttons for rotation and saving.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Button(
                                    onClick = {
                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            val tmpImages = images.clone() as ArrayList<MyImage>
                                            for (i in 0 until tmpImages.size) {
                                                if (images.contains(tmpImages[i])) {
                                                    images[i].rotate(180f)
                                                }
                                                progress = (i + 1).toFloat() / tmpImages.size
                                                update = !update
                                            }
                                            tmpImages.clear()
                                            isShowProgress = false
                                        }
                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.rotate180_btn))
                                }

                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P
                                            && ContextCompat.checkSelfPermission(context, "android.permission.WRITE_EXTERNAL_STORAGE")
                                            != PackageManager.PERMISSION_GRANTED) {
                                            isShowRequestWritePermissionDialog = true
                                            return@Button
                                        }

                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.start_saving),
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            val tmpImages = images.clone() as ArrayList<MyImage>
                                            for (i in 0 until tmpImages.size) {
                                                if (images.contains(tmpImages[i])) {
                                                    tmpImages[i].save(i)
                                                }
                                                progress = (i + 1).toFloat() / tmpImages.size
                                                update = !update
                                            }
                                            tmpImages.clear()
                                            isShowProgress = false

                                            Looper.prepare()
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.succeeded_save_image_toast),
                                                Toast.LENGTH_LONG
                                            )
                                                .show()
                                            Looper.loop()
                                        }

                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.save_btn))
                                }
                            }

                            // Progress bar to show the progress of image processing.
                            if (isShowProgress) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .padding(vertical = 5.dp)
                                        .fillMaxWidth()
                                        .height(30.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .padding(horizontal = 10.dp)
                                    )
                                    val total = if (newImageUriCount > 0) {
                                        round(images.size.toFloat() / progress).toInt()
                                    } else {
                                        images.size
                                    }
                                    Text(
                                        text = "${round(total * progress).toInt()}/$total",
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            } else {
                                Spacer(modifier = Modifier.height(40.dp))
                            }

                            // Show all of the added images.
                            for (i in 0 until images.size) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .padding(0.dp, 0.dp, 0.dp, 15.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSystemInDarkTheme()) Color(50, 50, 50)
                                            else Color(245, 240, 242)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Show file name and image.
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(0.dp, 0.dp, 50.dp, 0.dp)
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = images[i].filename,
                                                    fontSize = if (update) 12.sp else 12.sp,
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 15.dp)
                                                )
                                            }

                                            Image(
                                                bitmap = images[i].thumbnail.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .padding(15.dp, 0.dp, 15.dp, 10.dp)
                                                    .clip(
                                                        RoundedCornerShape(10.dp)
                                                    )
                                            )
                                        }

                                        // Buttons for operating every individual image.
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.Top,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp)
                                        ) {
                                            // A Close button to remove the image from this App.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images.removeAt(i)
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // A Setting button to change algorithm and password.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Settings,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            isShowMethodAndKeySettingOnTopLayer =
                                                                true
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // A Decrypt button to decrypt the individual image.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ArrowBack,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            val isValid = when (selectedAlgorithm) {
                                                                Algorithm.TOMATO -> checkTomatoKeyValidity(
                                                                    keyString
                                                                )

                                                                in arrayOf(
                                                                    Algorithm.PIC_ENCRYPT_ROW,
                                                                    Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN
                                                                ) -> checkPicEncryptKeyValidity(
                                                                    keyString
                                                                )

                                                                else -> true
                                                            }
                                                            if (!isValid) {
                                                                val msg =
                                                                    if (selectedAlgorithm == Algorithm.TOMATO) {
                                                                        context.getString(R.string.invalid_tomato_key_toast)
                                                                    } else {
                                                                        context.getString(R.string.invalid_picencrypt_key_toast)
                                                                    }
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        msg,
                                                                        Toast.LENGTH_LONG
                                                                    )
                                                                    .show()
                                                                return@clickable
                                                            }

                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_encryption),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                var hadFallback = false
                                                                val success = images[i].process(
                                                                    selectedAlgorithm,
                                                                    ProcessType.ENCRYPT,
                                                                    keyString,
                                                                    forceCompat = isCompatMode,
                                                                    onFallback = { hadFallback = true }
                                                                )
                                                                if (hadFallback) {
                                                                    Looper.prepare()
                                                                    Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                                                    Looper.loop()
                                                                } else if (!success) {
                                                                    Looper.prepare()
                                                                    Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                                                    Looper.loop()
                                                                }
                                                                update = !update
                                                            }
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // An Encrypt button to encrypt the individual image.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ArrowForward,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            val isValid = when (selectedAlgorithm) {
                                                                Algorithm.TOMATO -> checkTomatoKeyValidity(
                                                                    keyString
                                                                )

                                                                in arrayOf(
                                                                    Algorithm.PIC_ENCRYPT_ROW,
                                                                    Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN
                                                                ) -> checkPicEncryptKeyValidity(
                                                                    keyString
                                                                )

                                                                else -> true
                                                            }
                                                            if (!isValid) {
                                                                val msg =
                                                                    if (selectedAlgorithm == Algorithm.TOMATO) {
                                                                        context.getString(R.string.invalid_tomato_key_toast)
                                                                    } else {
                                                                        context.getString(R.string.invalid_picencrypt_key_toast)
                                                                    }
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        msg,
                                                                        Toast.LENGTH_LONG
                                                                    )
                                                                    .show()
                                                                return@clickable
                                                            }

                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_decryption),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                var hadFallback = false
                                                                val success = images[i].process(
                                                                    selectedAlgorithm,
                                                                    ProcessType.DECRYPT,
                                                                    keyString,
                                                                    forceCompat = isCompatMode,
                                                                    onFallback = { hadFallback = true }
                                                                )
                                                                if (hadFallback) {
                                                                    Looper.prepare()
                                                                    Toast.makeText(context, context.getString(R.string.fallback_to_compat_toast), Toast.LENGTH_SHORT).show()
                                                                    Looper.loop()
                                                                } else if (!success) {
                                                                    Looper.prepare()
                                                                    Toast.makeText(context, context.getString(R.string.process_failed_toast), Toast.LENGTH_SHORT).show()
                                                                    Looper.loop()
                                                                }
                                                                update = !update
                                                            }
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // A Rotate button to rotate the individual image 180 degrees.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    painterResource(id = R.drawable.crop_rotate_119243),
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images[i].rotate(180f)
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // A Reset button to restore the individual image
                                            // back to its original appearance when added to this App.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Refresh,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images[i].reload()
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            // A Save button to save the individual image to your phone.
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    painterResource(id = R.drawable.import_download_icon_176152),
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P
                                                                && ContextCompat.checkSelfPermission(
                                                                    context,
                                                                    "android.permission.WRITE_EXTERNAL_STORAGE"
                                                                )
                                                                != PackageManager.PERMISSION_GRANTED
                                                            ) {
                                                                isShowRequestWritePermissionDialog =
                                                                    true
                                                                return@clickable
                                                            }

                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_saving),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                val result = images[i].save(i)
                                                                Looper.prepare()
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        if (result) context.getString(
                                                                            R.string.succeeded_save_image_toast
                                                                        )
                                                                        else context.getString(R.string.failed_save_image_toast),
                                                                        Toast.LENGTH_SHORT
                                                                    )
                                                                    .show()
                                                                Looper.loop()
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

                    if (images.size > 0) {
                        // Buttons pinned to the bottom right conner of the screen.
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(30.dp)
                        ) {
                            // A button to add new images.
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(149, 117, 205, 255))
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    null,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(40.dp)
                                        .clickable {
                                            launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                type = "image/*"
                                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                            })
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // A button to scroll to the top.
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(149, 117, 205, 255))
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    null,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(40.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo(0, TweenSpec(800))
                                            }
                                        }
                                )
                            }
                        }
                    }

                    if (isShowMethodAndKeySettingOnTopLayer) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0, 0, 0, 180))
                                .clickable {
                                    isShowMethodAndKeySettingOnTopLayer = false
                                }
                        ) {
                            SetMethodAndKey()
                        }
                    }

                    // Stego (File Steganography) Dialog
                    if (isShowStegoDialog) {
                        var stegoMode by remember { mutableStateOf(0) }
                        var coverUri by remember { mutableStateOf<Uri?>(null) }
                        var archiveUri by remember { mutableStateOf<Uri?>(null) }
                        var extractTargetUri by remember { mutableStateOf<Uri?>(null) }
                        var isStegoProcessing by remember { mutableStateOf(false) }

                        val coverLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult(),
                            onResult = { result ->
                                result.data?.data?.let { coverUri = it }
                            }
                        )
                        val archiveLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult(),
                            onResult = { result ->
                                result.data?.data?.let { archiveUri = it }
                            }
                        )
                        val extractTargetLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult(),
                            onResult = { result ->
                                result.data?.data?.let { extractTargetUri = it }
                            }
                        )

                        AlertDialog(
                            onDismissRequest = { if (!isStegoProcessing) isShowStegoDialog = false },
                            title = {
                                Text(
                                    text = stringResource(id = R.string.stego_tool_title),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Button(
                                            onClick = { stegoMode = 0 },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (stegoMode == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (stegoMode == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                                        ) {
                                            Text(text = stringResource(id = R.string.stego_create_mode), fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { stegoMode = 1 },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (stegoMode == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (stegoMode == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                                        ) {
                                            Text(text = stringResource(id = R.string.stego_extract_mode), fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    if (stegoMode == 0) {
                                        Button(
                                            onClick = {
                                                coverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "image/*"
                                                })
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = stringResource(id = R.string.stego_select_cover))
                                        }
                                        Text(
                                            text = coverUri?.let { getUriFilename(context, it) } ?: "未选择封面",
                                            fontSize = 12.sp,
                                            color = if (coverUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                archiveLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "*/*"
                                                })
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = stringResource(id = R.string.stego_select_archive))
                                        }
                                        Text(
                                            text = archiveUri?.let { getUriFilename(context, it) } ?: "未选择压缩包",
                                            fontSize = 12.sp,
                                            color = if (archiveUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                val cUri = coverUri
                                                val aUri = archiveUri
                                                if (cUri != null && aUri != null) {
                                                    isStegoProcessing = true
                                                    thread {
                                                        val ok = saveStegoImage(context, cUri, aUri)
                                                        Looper.prepare()
                                                        Toast.makeText(
                                                            context,
                                                            if (ok) context.getString(R.string.stego_create_success) else context.getString(R.string.stego_failed),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        isStegoProcessing = false
                                                        if (ok) isShowStegoDialog = false
                                                        Looper.loop()
                                                    }
                                                }
                                            },
                                            enabled = coverUri != null && archiveUri != null && !isStegoProcessing,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = if (isStegoProcessing) "正在合成..." else stringResource(id = R.string.stego_btn_create))
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                extractTargetLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                    type = "*/*"
                                                })
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = stringResource(id = R.string.stego_select_target))
                                        }
                                        Text(
                                            text = extractTargetUri?.let { getUriFilename(context, it) } ?: "未选择图种文件",
                                            fontSize = 12.sp,
                                            color = if (extractTargetUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Button(
                                            onClick = {
                                                val tUri = extractTargetUri
                                                if (tUri != null) {
                                                    isStegoProcessing = true
                                                    thread {
                                                        val fileName = extractArchiveFromStego(context, tUri)
                                                        Looper.prepare()
                                                        val msg = if (fileName != null) {
                                                            context.getString(R.string.stego_extract_success) + " (" + fileName + ")"
                                                        } else {
                                                            context.getString(R.string.stego_no_archive_found)
                                                        }
                                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                        isStegoProcessing = false
                                                        if (fileName != null) isShowStegoDialog = false
                                                        Looper.loop()
                                                    }
                                                }
                                            },
                                            enabled = extractTargetUri != null && !isStegoProcessing,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(text = if (isStegoProcessing) "正在提取..." else stringResource(id = R.string.stego_btn_extract))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { isShowStegoDialog = false },
                                    enabled = !isStegoProcessing
                                ) {
                                    Text("关闭")
                                }
                            }
                        )
                    }

                    // Mirage Tank (Prism Tank) Dialog
                    if (isShowMirageDialog) {
                        var isColoredMode by remember { mutableStateOf(true) }
                        var coverUri by remember { mutableStateOf<Uri?>(null) }
                        var hiddenUri by remember { mutableStateOf<Uri?>(null) }
                        var isMirageProcessing by remember { mutableStateOf(false) }

                        val mirageCoverLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult(),
                            onResult = { result ->
                                result.data?.data?.let { coverUri = it }
                            }
                        )
                        val mirageHiddenLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.StartActivityForResult(),
                            onResult = { result ->
                                result.data?.data?.let { hiddenUri = it }
                            }
                        )

                        AlertDialog(
                            onDismissRequest = { if (!isMirageProcessing) isShowMirageDialog = false },
                            title = {
                                Text(
                                    text = stringResource(id = R.string.mirage_tool_title),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Button(
                                            onClick = { isColoredMode = true },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (isColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (isColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f).padding(end = 4.dp)
                                        ) {
                                            Text(text = stringResource(id = R.string.mirage_colored_mode), fontSize = 12.sp)
                                        }
                                        Button(
                                            onClick = { isColoredMode = false },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = if (!isColoredMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                                contentColor = if (!isColoredMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            modifier = Modifier.weight(1f).padding(start = 4.dp)
                                        ) {
                                            Text(text = stringResource(id = R.string.mirage_gray_mode), fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            mirageCoverLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                type = "image/*"
                                            })
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = stringResource(id = R.string.mirage_select_cover))
                                    }
                                    Text(
                                        text = coverUri?.let { getUriFilename(context, it) } ?: "未选择表图",
                                        fontSize = 12.sp,
                                        color = if (coverUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            mirageHiddenLauncher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                type = "image/*"
                                            })
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = stringResource(id = R.string.mirage_select_hidden))
                                    }
                                    Text(
                                        text = hiddenUri?.let { getUriFilename(context, it) } ?: "未选择里图",
                                        fontSize = 12.sp,
                                        color = if (hiddenUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Button(
                                        onClick = {
                                            val cUri = coverUri
                                            val hUri = hiddenUri
                                            if (cUri != null && hUri != null) {
                                                isMirageProcessing = true
                                                thread {
                                                    try {
                                                        val coverStream = context.contentResolver.openInputStream(cUri)
                                                        val hiddenStream = context.contentResolver.openInputStream(hUri)
                                                        val coverBmp = BitmapFactory.decodeStream(coverStream)
                                                        val hiddenBmp = BitmapFactory.decodeStream(hiddenStream)
                                                        coverStream?.close()
                                                        hiddenStream?.close()

                                                        if (coverBmp != null && hiddenBmp != null) {
                                                            val (saved, _) = MirageTankBridge.createAndSaveMirageTank(
                                                                context,
                                                                coverBmp,
                                                                hiddenBmp,
                                                                isColored = isColoredMode
                                                            )
                                                            Looper.prepare()
                                                            Toast.makeText(
                                                                context,
                                                                if (saved) context.getString(R.string.mirage_success) else context.getString(R.string.mirage_failed),
                                                                Toast.LENGTH_LONG
                                                            ).show()
                                                            isMirageProcessing = false
                                                            if (saved) isShowMirageDialog = false
                                                            Looper.loop()
                                                        } else {
                                                            Looper.prepare()
                                                            Toast.makeText(context, context.getString(R.string.mirage_failed), Toast.LENGTH_SHORT).show()
                                                            isMirageProcessing = false
                                                            Looper.loop()
                                                        }
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                        Looper.prepare()
                                                        Toast.makeText(context, context.getString(R.string.mirage_failed), Toast.LENGTH_SHORT).show()
                                                        isMirageProcessing = false
                                                        Looper.loop()
                                                    }
                                                }
                                            }
                                        },
                                        enabled = coverUri != null && hiddenUri != null && !isMirageProcessing,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(text = if (isMirageProcessing) "正在生成..." else stringResource(id = R.string.mirage_btn_generate))
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { isShowMirageDialog = false },
                                    enabled = !isMirageProcessing
                                ) {
                                    Text("关闭")
                                }
                            }
                        )
                    }

                    // Show information about the App.
                    if (isShowInfo) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0, 0, 0, 180))
                                .clickable(false) {}
                        ) {

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .fillMaxWidth(0.8f)
                                    .background(
                                        if (isSystemInDarkTheme()) Color(
                                            50,
                                            50,
                                            50
                                        ) else Color(250, 250, 250)
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.clip(CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .clickable { isShowInfo = false }
                                                    .padding(10.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .padding(0.dp, 0.dp, 0.dp, 10.dp)
                                                .size(80.dp)
                                        )

                                        Text(
                                            text = stringResource(id = R.string.app_name),
                                            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
                                        )

                                        Text(
                                            text = "v${
                                                context.packageManager.getPackageInfo(
                                                    context.packageName,
                                                    0
                                                ).versionName
                                            }",
                                            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}