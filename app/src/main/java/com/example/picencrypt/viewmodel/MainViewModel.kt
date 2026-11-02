package com.example.picencrypt.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.model.MyImage
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.CryptoUtils
import com.example.picencrypt.utils.SteganographyHelper
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.MirageTankBridge
import com.example.picencrypt.utils.MiragePresetGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class MainViewModel : ViewModel() {

    // ---------------- 导航与弹窗全局状态 ----------------
    var currentTab by mutableIntStateOf(0)
    var isShowInfo by mutableStateOf(false)
    var isShowRequestWritePermissionDialog by mutableStateOf(false)
    var isShowMethodAndKeySettingOnTopLayer by mutableStateOf(false)

    // ---------------- 全局偏好与触觉反馈设置 ----------------
    var isHapticFeedbackEnabled by mutableStateOf(true)

    fun initPreferences(context: Context) {
        val sp = context.getSharedPreferences("repix_prefs", Context.MODE_PRIVATE)
        isHapticFeedbackEnabled = sp.getBoolean("key_haptic_feedback", true)
    }

    fun setHapticFeedbackEnabled(enabled: Boolean, context: Context? = null) {
        isHapticFeedbackEnabled = enabled
        context?.let {
            val sp = it.getSharedPreferences("repix_prefs", Context.MODE_PRIVATE)
            sp.edit().putBoolean("key_haptic_feedback", enabled).apply()
        }
    }

    // ---------------- Tab 0: 图片混淆状态 ----------------
    val images = mutableStateListOf<MyImage>()
    var selectedAlgorithm by mutableStateOf(Algorithm.TOMATO)
    var keyString by mutableStateOf("1")
    var isCompatMode by mutableStateOf(false)
    var isDropdownMenuExpanded by mutableStateOf(false)
    var exportFormat by mutableStateOf(ImageExportFormat.PNG)

    var progress by mutableFloatStateOf(0f)
    var isShowProgress by mutableStateOf(false)
    var newImageUriCount by mutableIntStateOf(0)

    // ---------------- Tab 1: 图种隐写状态 ----------------
    var stegoMode by mutableIntStateOf(0)
    var stegoCoverUri by mutableStateOf<Uri?>(null)
    var stegoArchiveUri by mutableStateOf<Uri?>(null)
    var stegoCoverThumbnail by mutableStateOf<Bitmap?>(null)
    var stegoCoverFileSize by mutableLongStateOf(0L)
    var stegoArchiveFileSize by mutableLongStateOf(0L)

    var stegoExtractTargetUri by mutableStateOf<Uri?>(null)
    var stegoExtractThumbnail by mutableStateOf<Bitmap?>(null)
    var stegoExtractFileSize by mutableLongStateOf(0L)
    var stegoInspection by mutableStateOf<SteganographyHelper.StegoInspection?>(null)
    var isStegoInspecting by mutableStateOf(false)
    var isStegoProcessing by mutableStateOf(false)

    // ---------------- Tab 2: 幻影坦克状态 ----------------
    var mirageIsColoredMode by mutableStateOf(true)
    var mirageScaleMode by mutableStateOf(MirageTankBridge.MirageScaleMode.FIT)
    var mirageCoverUri by mutableStateOf<Uri?>(null)
    var mirageHiddenUri by mutableStateOf<Uri?>(null)
    var mirageCoverThumbnail by mutableStateOf<Bitmap?>(null)
    var mirageHiddenThumbnail by mutableStateOf<Bitmap?>(null)
    var miragePreviewBitmap by mutableStateOf<Bitmap?>(null)
    var mirageBgMode by mutableIntStateOf(0) // 0: 白底, 1: 黑底, 2: 棋盘格
    var isMirageProcessing by mutableStateOf(false)
    var mirageCoverBrightness by mutableFloatStateOf(0f)
    var mirageCoverContrast by mutableFloatStateOf(1f)
    var mirageHiddenBrightness by mutableFloatStateOf(0f)
    var mirageHiddenContrast by mutableFloatStateOf(1f)
    var isMirageAdjustExpanded by mutableStateOf(false)

    fun resetMirageAdjustments() {
        mirageCoverBrightness = 0f
        mirageCoverContrast = 1f
        mirageHiddenBrightness = 0f
        mirageHiddenContrast = 1f
        miragePreviewBitmap = null
    }

    // ---------------- Tab 3: 安全保险箱状态 (AES-256-GCM) ----------------
    var safeMode by mutableIntStateOf(0) // 0: 加密为 .rpx, 1: 解密 .rpx
    var safePassword by mutableStateOf("")
    var safeSourceUri by mutableStateOf<Uri?>(null)
    var safeSourceFileName by mutableStateOf("")
    var safeSourceFileSize by mutableLongStateOf(0L)
    var safeRpxHeader by mutableStateOf<CryptoUtils.RpxHeader?>(null)
    var isSafeProcessing by mutableStateOf(false)

    // ---------------- 业务方法：图片混淆 ----------------

    fun selectAlgorithm(algorithm: Algorithm) {
        if (selectedAlgorithm != algorithm) {
            if (algorithm == Algorithm.TOMATO) {
                keyString = Algorithm.DEFAULT_TOMATO_KEY
            } else if (selectedAlgorithm == Algorithm.TOMATO) {
                keyString = Algorithm.DEFAULT_OTHER_KEY
            }
        }
        selectedAlgorithm = algorithm
        isDropdownMenuExpanded = false
    }

    fun addImages(uris: List<Uri>, context: Context, onComplete: () -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            newImageUriCount = uris.size
            val loaded = mutableListOf<MyImage>()
            for (i in uris.indices) {
                loaded.add(MyImage(uris[i], context))
                progress = (i + 1).toFloat() / uris.size
            }
            withContext(Dispatchers.Main) {
                images.addAll(loaded)
                newImageUriCount = 0
                isShowProgress = false
                onComplete()
            }
        }
    }

    fun removeImageAt(index: Int) {
        if (index in images.indices) {
            images.removeAt(index)
        }
    }

    fun clearAllImages() {
        images.clear()
    }

    private var batchProcessJob: Job? = null
    private var batchSaveJob: Job? = null

    fun cancelCurrentBatchTask() {
        batchProcessJob?.cancel()
        batchSaveJob?.cancel()
        isShowProgress = false
    }

    fun processAllImages(
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onFinish: (isEncrypt: Boolean, successCount: Int, failCount: Int, durationMs: Long) -> Unit
    ) {
        if (images.isEmpty() || isShowProgress) return
        val algoSnapshot = selectedAlgorithm
        val keySnapshot = keyString
        val compatSnapshot = isCompatMode
        val typeSnapshot = processType
        val startTime = System.currentTimeMillis()

        batchProcessJob = viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            // 集合与参数双重快照保护，避免遍历中参数修改引发混淆状态污染 (P1-2)
            val snapshot = images.toList()
            var successCount = 0
            var failCount = 0
            for (i in snapshot.indices) {
                if (!isActive) break
                val item = snapshot[i]
                var hadFallback = false
                val success = item.process(
                    algoSnapshot,
                    typeSnapshot,
                    keySnapshot,
                    forceCompat = compatSnapshot,
                    onFallback = { hadFallback = true }
                )
                if (success) {
                    successCount++
                } else {
                    failCount++
                }
                if (hadFallback) {
                    withContext(Dispatchers.Main) { onFallbackToast() }
                } else if (!success) {
                    withContext(Dispatchers.Main) { onFailedToast() }
                }
                progress = (i + 1).toFloat() / snapshot.size
            }
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                // 触发列表通知，确保 Compose 观察到元素属性变化
                for (i in images.indices) {
                    images[i] = images[i]
                }
                isShowProgress = false
                onFinish(processType == ProcessType.ENCRYPT, successCount, failCount, durationMs)
            }
        }
    }

    fun reloadAllImages(onFileNotFound: () -> Unit) {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            val snapshot = images.toList()
            for (i in snapshot.indices.reversed()) {
                val item = snapshot[i]
                try {
                    item.reload()
                } catch (e: FileNotFoundException) {
                    withContext(Dispatchers.Main) {
                        images.remove(item)
                        onFileNotFound()
                    }
                }
                progress = (snapshot.size - i).toFloat() / snapshot.size
            }
            withContext(Dispatchers.Main) {
                for (i in images.indices) {
                    images[i] = images[i]
                }
                isShowProgress = false
            }
        }
    }

    fun rotateAllImages() {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            val snapshot = images.toList()
            for (i in snapshot.indices) {
                snapshot[i].rotate(180f)
                progress = (i + 1).toFloat() / snapshot.size
            }
            withContext(Dispatchers.Main) {
                for (i in images.indices) {
                    images[i] = images[i]
                }
                isShowProgress = false
            }
        }
    }

    fun saveAllImages(onComplete: (successCount: Int, failCount: Int, durationMs: Long) -> Unit) {
        if (images.isEmpty() || isShowProgress) return
        val startTime = System.currentTimeMillis()
        batchSaveJob = viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            val snapshot = images.toList()
            var successCount = 0
            var failCount = 0
            for (i in snapshot.indices) {
                if (!isActive) break
                val ok = snapshot[i].saveWithDetail(i, format = exportFormat) is com.example.picencrypt.model.SaveResult.Success
                if (ok) successCount++ else failCount++
                progress = (i + 1).toFloat() / snapshot.size
            }
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                isShowProgress = false
                onComplete(successCount, failCount, durationMs)
            }
        }
    }

    fun processSingleImage(
        index: Int,
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onSuccess: ((durationMs: Long) -> Unit)? = null
    ) {
        if (index !in images.indices) return
        val item = images[index]
        val startTime = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.Default) {
            var hadFallback = false
            val success = item.process(
                selectedAlgorithm,
                processType,
                keyString,
                forceCompat = isCompatMode,
                onFallback = { hadFallback = true }
            )
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                if (index in images.indices) {
                    images[index] = item // 触发 Snapshot 重绘
                }
                if (hadFallback) onFallbackToast()
                else if (!success) onFailedToast()
                else onSuccess?.invoke(durationMs)
            }
        }
    }

    fun rotateSingleImage(index: Int) {
        if (index in images.indices) {
            val item = images[index]
            item.rotate(180f)
            images[index] = item
        }
    }

    fun reloadSingleImage(index: Int) {
        if (index in images.indices) {
            val item = images[index]
            item.reload()
            images[index] = item
        }
    }

    fun saveSingleImage(index: Int, onComplete: (Boolean) -> Unit) {
        if (index !in images.indices) return
        val item = images[index]
        viewModelScope.launch(Dispatchers.IO) {
            val result = item.saveWithDetail(index, format = exportFormat) is com.example.picencrypt.model.SaveResult.Success
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    // ---------------- 业务方法：图种隐写 ----------------

    fun setStegoCover(uri: Uri?, context: Context) {
        stegoCoverUri = uri
        if (uri == null) {
            stegoCoverThumbnail = null
            stegoCoverFileSize = 0L
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                val size = FileUtils.getUriFileSize(context, uri)
                withContext(Dispatchers.Main) {
                    stegoCoverThumbnail = thumb
                    stegoCoverFileSize = size
                }
            }
        }
    }

    fun setStegoArchive(uri: Uri?, context: Context) {
        stegoArchiveUri = uri
        if (uri == null) {
            stegoArchiveFileSize = 0L
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val size = FileUtils.getUriFileSize(context, uri)
                withContext(Dispatchers.Main) {
                    stegoArchiveFileSize = size
                }
            }
        }
    }

    fun setStegoExtractTarget(uri: Uri?, context: Context) {
        stegoExtractTargetUri = uri
        stegoInspection = null
        if (uri == null) {
            stegoExtractThumbnail = null
            stegoExtractFileSize = 0L
            isStegoInspecting = false
        } else {
            isStegoInspecting = true
            viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                val totalSize = FileUtils.getUriFileSize(context, uri)
                val inspection = try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        SteganographyHelper.detectArchiveFromStream(stream, totalSize)
                    } ?: SteganographyHelper.StegoInspection(hasArchive = false, totalSize = totalSize)
                } catch (e: Exception) {
                    e.printStackTrace()
                    SteganographyHelper.StegoInspection(hasArchive = false, totalSize = totalSize)
                }
                withContext(Dispatchers.Main) {
                    stegoExtractThumbnail = thumb
                    stegoExtractFileSize = totalSize
                    stegoInspection = inspection
                    isStegoInspecting = false
                }
            }
        }
    }

    fun createStego(context: Context, onResult: (Boolean) -> Unit) {
        val cUri = stegoCoverUri ?: return
        val aUri = stegoArchiveUri ?: return
        isStegoProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            val ok = FileUtils.saveStegoImage(context, cUri, aUri)
            withContext(Dispatchers.Main) {
                isStegoProcessing = false
                onResult(ok)
            }
        }
    }

    fun extractStego(context: Context, onResult: (String?) -> Unit) {
        val tUri = stegoExtractTargetUri ?: return
        isStegoProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = FileUtils.extractArchiveFromStego(context, tUri)
            withContext(Dispatchers.Main) {
                isStegoProcessing = false
                onResult(fileName)
            }
        }
    }

    // ---------------- 业务方法：幻影坦克 ----------------

    fun setMirageCover(uri: Uri?, context: Context) {
        mirageCoverUri = uri
        miragePreviewBitmap = null
        if (uri == null) {
            mirageCoverThumbnail = null
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                withContext(Dispatchers.Main) {
                    mirageCoverThumbnail = thumb
                }
            }
        }
    }

    fun setMirageHidden(uri: Uri?, context: Context) {
        mirageHiddenUri = uri
        miragePreviewBitmap = null
        if (uri == null) {
            mirageHiddenThumbnail = null
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                withContext(Dispatchers.Main) {
                    mirageHiddenThumbnail = thumb
                }
            }
        }
    }

    fun loadMiragePreset(theme: MiragePresetGenerator.PresetTheme, context: Context) {
        val (coverBmp, hiddenBmp) = MiragePresetGenerator.generatePresetBitmaps(theme)
        val (coverUri, hiddenUri) = MiragePresetGenerator.createPresetUris(context, theme)
        mirageCoverUri = coverUri
        mirageHiddenUri = hiddenUri
        mirageCoverThumbnail = Bitmap.createScaledBitmap(coverBmp, 140, 140, true)
        mirageHiddenThumbnail = Bitmap.createScaledBitmap(hiddenBmp, 140, 140, true)
        miragePreviewBitmap = null
        generateMiragePreview(context)
    }

    fun swapMirageImages() {
        val tempUri = mirageCoverUri
        mirageCoverUri = mirageHiddenUri
        mirageHiddenUri = tempUri

        val tempThumb = mirageCoverThumbnail
        mirageCoverThumbnail = mirageHiddenThumbnail
        mirageHiddenThumbnail = tempThumb

        miragePreviewBitmap = null
    }

    fun generateMiragePreview(context: Context, onResult: (Boolean) -> Unit = {}) {
        val cUri = mirageCoverUri ?: return
        val hUri = mirageHiddenUri ?: return
        isMirageProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val coverBmp = context.contentResolver.openInputStream(cUri)?.use { BitmapFactory.decodeStream(it) }
                val hiddenBmp = context.contentResolver.openInputStream(hUri)?.use { BitmapFactory.decodeStream(it) }
                if (coverBmp != null && hiddenBmp != null) {
                    val preview = MirageTankBridge.createMirageBitmap(
                        coverBmp,
                        hiddenBmp,
                        isColored = mirageIsColoredMode,
                        maxDimension = 1200,
                        mode = mirageScaleMode,
                        coverBrightness = mirageCoverBrightness,
                        coverContrast = mirageCoverContrast,
                        hiddenBrightness = mirageHiddenBrightness,
                        hiddenContrast = mirageHiddenContrast
                    )
                    withContext(Dispatchers.Main) {
                        miragePreviewBitmap = preview
                        isMirageProcessing = false
                        onResult(preview != null)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        isMirageProcessing = false
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isMirageProcessing = false
                    onResult(false)
                }
            }
        }
    }

    fun saveMiragePreview(context: Context, onResult: (Boolean) -> Unit) {
        val preview = miragePreviewBitmap
        if (preview != null) {
            isMirageProcessing = true
            viewModelScope.launch(Dispatchers.IO) {
                val saved = MirageTankBridge.saveMiragePng(context, preview)
                withContext(Dispatchers.Main) {
                    isMirageProcessing = false
                    onResult(saved)
                }
            }
        } else {
            val cUri = mirageCoverUri ?: return
            val hUri = mirageHiddenUri ?: return
            isMirageProcessing = true
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val coverBmp = context.contentResolver.openInputStream(cUri)?.use { BitmapFactory.decodeStream(it) }
                    val hiddenBmp = context.contentResolver.openInputStream(hUri)?.use { BitmapFactory.decodeStream(it) }
                    if (coverBmp != null && hiddenBmp != null) {
                        val (saved, outBmp) = MirageTankBridge.createAndSaveMirageTank(
                            context,
                            coverBmp,
                            hiddenBmp,
                            isColored = mirageIsColoredMode,
                            mode = mirageScaleMode,
                            coverBrightness = mirageCoverBrightness,
                            coverContrast = mirageCoverContrast,
                            hiddenBrightness = mirageHiddenBrightness,
                            hiddenContrast = mirageHiddenContrast
                        )
                        withContext(Dispatchers.Main) {
                            if (saved && outBmp != null) {
                                miragePreviewBitmap = outBmp
                            }
                            isMirageProcessing = false
                            onResult(saved)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isMirageProcessing = false
                            onResult(false)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        isMirageProcessing = false
                        onResult(false)
                    }
                }
            }
        }
    }

    // ---------------- 业务方法：安全保险箱 (AES-256-GCM) ----------------

    fun setSafeSourceFile(uri: Uri?, context: Context) {
        safeSourceUri = uri
        if (uri == null) {
            safeSourceFileName = ""
            safeSourceFileSize = 0L
            safeRpxHeader = null
            return
        }
        val name = FileUtils.getUriFilename(context, uri)
        val size = FileUtils.getUriFileSize(context, uri)
        safeSourceFileName = name
        safeSourceFileSize = size

        if (safeMode == 1 || name.endsWith(".rpx", ignoreCase = true)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val header = CryptoUtils.inspectHeader(input)
                        withContext(Dispatchers.Main) {
                            safeRpxHeader = header
                        }
                    }
                } catch (_: Exception) {}
            }
        } else {
            safeRpxHeader = null
        }
    }

    fun executeSafeEncryption(
        context: Context,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val uri = safeSourceUri ?: return
        val pwd = safePassword
        if (pwd.length < 6) {
            onResult(false, context.getString(com.example.picencrypt.R.string.safe_password_hint))
            return
        }

        isSafeProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (inputBytes == null || inputBytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isSafeProcessing = false
                        onResult(false, context.getString(com.example.picencrypt.R.string.file_not_found))
                    }
                    return@launch
                }

                val origName = safeSourceFileName.ifEmpty { "file" }
                val outName = "${origName}.rpx"
                val encryptedBytes = CryptoUtils.encryptBytes(inputBytes, pwd.toCharArray(), origName)

                val saved = FileUtils.saveBytesToDownload(context, outName, encryptedBytes)
                withContext(Dispatchers.Main) {
                    isSafeProcessing = false
                    if (saved) {
                        onResult(true, context.getString(com.example.picencrypt.R.string.safe_encrypt_success))
                    } else {
                        onResult(false, context.getString(com.example.picencrypt.R.string.stego_failed))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSafeProcessing = false
                    onResult(false, e.localizedMessage ?: context.getString(com.example.picencrypt.R.string.stego_failed))
                }
            }
        }
    }

    fun executeSafeDecryption(
        context: Context,
        onResult: (success: Boolean, message: String) -> Unit
    ) {
        val uri = safeSourceUri ?: return
        val pwd = safePassword
        if (pwd.isEmpty()) {
            onResult(false, context.getString(com.example.picencrypt.R.string.safe_password_hint))
            return
        }

        isSafeProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encryptedBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (encryptedBytes == null || encryptedBytes.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        isSafeProcessing = false
                        onResult(false, context.getString(com.example.picencrypt.R.string.file_not_found))
                    }
                    return@launch
                }

                val decryptResult = CryptoUtils.decryptBytes(encryptedBytes, pwd.toCharArray())
                when (decryptResult) {
                    is CryptoUtils.DecryptResult.Success -> {
                        val outName = decryptResult.originalFilename.ifEmpty { "decrypted_file" }
                        val saved = FileUtils.saveBytesToDownload(context, outName, decryptResult.plainBytes)
                        withContext(Dispatchers.Main) {
                            isSafeProcessing = false
                            if (saved) {
                                onResult(true, context.getString(com.example.picencrypt.R.string.safe_decrypt_success))
                            } else {
                                onResult(false, context.getString(com.example.picencrypt.R.string.stego_failed))
                            }
                        }
                    }
                    is CryptoUtils.DecryptResult.WrongPasswordOrCorrupted -> {
                        withContext(Dispatchers.Main) {
                            isSafeProcessing = false
                            onResult(false, context.getString(com.example.picencrypt.R.string.safe_decrypt_wrong_password))
                        }
                    }
                    is CryptoUtils.DecryptResult.InvalidFormat -> {
                        withContext(Dispatchers.Main) {
                            isSafeProcessing = false
                            onResult(false, context.getString(com.example.picencrypt.R.string.safe_decrypt_invalid_format))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isSafeProcessing = false
                    onResult(false, e.localizedMessage ?: context.getString(com.example.picencrypt.R.string.stego_failed))
                }
            }
        }
    }
}
