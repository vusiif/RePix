package com.example.picencrypt.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.model.AppThemeMode
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.model.MyImage
import com.example.picencrypt.utils.CryptoUtils
import com.example.picencrypt.utils.ExportFilenameGenerator
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.MiragePresetGenerator
import com.example.picencrypt.utils.MirageTankBridge
import com.example.picencrypt.utils.SteganographyHelper

/**
 * Top-level application coordinator ViewModel.
 * Manages global navigation, top-level dialog states, and persistent app preferences,
 * while delegating feature-specific domain logic to modular feature ViewModels:
 * - [scramble]: [ScrambleViewModel] (Tab 0)
 * - [stego]: [StegoViewModel] (Tab 1)
 * - [mirage]: [MirageViewModel] (Tab 2)
 * - [safe]: [SafeViewModel] (Tab 3)
 */
class MainViewModel(
    val scramble: ScrambleViewModel = ScrambleViewModel(),
    val stego: StegoViewModel = StegoViewModel(),
    val mirage: MirageViewModel = MirageViewModel(),
    val safe: SafeViewModel = SafeViewModel(),
    private val preferencesRepository: PreferencesRepository = PreferencesRepository.getInstance()
) : ViewModel() {

    // ---------------- 导航与全局弹窗状态 ----------------
    var currentTab by mutableIntStateOf(0)
    var isShowInfo by mutableStateOf(false)
    var isShowRequestWritePermissionDialog by mutableStateOf(false)
    var isShowMethodAndKeySettingOnTopLayer by mutableStateOf(false)

    // ---------------- 全局偏好与主题外观设置 ----------------
    var isHapticFeedbackEnabled by mutableStateOf(true)
    var appThemeMode by mutableStateOf(AppThemeMode.FOLLOW_SYSTEM)
    var isStripExifOnExport by mutableStateOf(true)
    var exportFormat by mutableStateOf(ImageExportFormat.PNG)
    var exportQuality by mutableIntStateOf(90)
    var exportFilenamePattern by mutableStateOf(ExportFilenameGenerator.PATTERN_TIMESTAMP)
    var exportCustomPrefix by mutableStateOf("RePix")

    init {
        loadPreferencesFromRepository()
    }

    private fun loadPreferencesFromRepository() {
        isHapticFeedbackEnabled = preferencesRepository.isHapticFeedbackEnabled()
        appThemeMode = preferencesRepository.getAppThemeMode()
        isStripExifOnExport = preferencesRepository.isStripExifOnExport()
        exportFormat = preferencesRepository.getExportFormat()
        exportQuality = preferencesRepository.getExportQuality()
        exportFilenamePattern = preferencesRepository.getExportFilenamePattern()
        exportCustomPrefix = preferencesRepository.getExportCustomPrefix()
    }

    fun initPreferences(context: Context? = null) {
        if (context != null) {
            PreferencesRepository.init(context)
        }
        loadPreferencesFromRepository()
    }

    fun setHapticFeedbackEnabled(enabled: Boolean, context: Context? = null) {
        isHapticFeedbackEnabled = enabled
        preferencesRepository.setHapticFeedbackEnabled(enabled)
    }

    fun setAppThemeMode(mode: AppThemeMode, context: Context? = null) {
        appThemeMode = mode
        preferencesRepository.setAppThemeMode(mode)
    }

    fun setStripExifOnExport(enabled: Boolean, context: Context? = null) {
        isStripExifOnExport = enabled
        preferencesRepository.setStripExifOnExport(enabled)
    }

    fun setExportFormat(format: ImageExportFormat, context: Context? = null) {
        exportFormat = format
        preferencesRepository.setExportFormat(format)
    }

    fun setExportQuality(quality: Int, context: Context? = null) {
        val clamped = quality.coerceIn(10, 100)
        exportQuality = clamped
        preferencesRepository.setExportQuality(clamped)
    }

    fun setExportFilenamePattern(pattern: String, context: Context? = null) {
        exportFilenamePattern = pattern
        preferencesRepository.setExportFilenamePattern(pattern)
    }

    fun setExportCustomPrefix(prefix: String, context: Context? = null) {
        exportCustomPrefix = prefix
        preferencesRepository.setExportCustomPrefix(prefix)
    }

    // =========================================================================
    // 兼容层代理：Tab 0 图片混淆 (ScrambleViewModel)
    // =========================================================================
    val images get() = scramble.images

    var selectedAlgorithm: Algorithm
        get() = scramble.selectedAlgorithm
        set(value) { scramble.selectedAlgorithm = value }

    var keyString: String
        get() = scramble.keyString
        set(value) { scramble.keyString = value }

    var isCompatMode: Boolean
        get() = scramble.isCompatMode
        set(value) { scramble.isCompatMode = value }

    var isDropdownMenuExpanded: Boolean
        get() = scramble.isDropdownMenuExpanded
        set(value) { scramble.isDropdownMenuExpanded = value }

    var progress: Float
        get() = scramble.progress
        set(value) { scramble.progress = value }

    var isShowProgress: Boolean
        get() = scramble.isShowProgress
        set(value) { scramble.isShowProgress = value }

    var newImageUriCount: Int
        get() = scramble.newImageUriCount
        set(value) { scramble.newImageUriCount = value }

    var lastProcessType: ProcessType?
        get() = scramble.lastProcessType
        set(value) { scramble.lastProcessType = value }

    val failedProcessImageIds get() = scramble.failedProcessImageIds
    val failedSaveImageIds get() = scramble.failedSaveImageIds

    fun selectAlgorithm(algorithm: Algorithm) = scramble.selectAlgorithm(algorithm)
    fun addImages(uris: List<Uri>, context: Context, onComplete: () -> Unit = {}) = scramble.addImages(uris, context, onComplete)
    fun loadDemoSample(context: Context, onComplete: () -> Unit = {}) = scramble.loadDemoSample(context, onComplete)
    fun removeImageAt(index: Int) = scramble.removeImageAt(index)
    fun clearAllImages() = scramble.clearAllImages()
    fun cancelCurrentBatchTask() = scramble.cancelCurrentBatchTask()

    fun processAllImages(
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onFinish: (isEncrypt: Boolean, successCount: Int, failCount: Int, durationMs: Long) -> Unit
    ) = scramble.processAllImages(processType, onFallbackToast, onFailedToast, onFinish)

    fun retryFailedProcess(
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onFinish: (isEncrypt: Boolean, successCount: Int, failCount: Int, durationMs: Long) -> Unit
    ) = scramble.retryFailedProcess(processType, onFallbackToast, onFailedToast, onFinish)

    fun reloadAllImages(onFileNotFound: () -> Unit) = scramble.reloadAllImages(onFileNotFound)
    fun rotateAllImages() = scramble.rotateAllImages()
    fun saveAllImages(onComplete: (successCount: Int, failCount: Int, durationMs: Long) -> Unit) = scramble.saveAllImages(onComplete)
    fun retryFailedSave(onComplete: (successCount: Int, failCount: Int, durationMs: Long) -> Unit) = scramble.retryFailedSave(onComplete)

    fun processSingleImage(
        index: Int,
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onSuccess: ((durationMs: Long) -> Unit)? = null
    ) = scramble.processSingleImage(index, processType, onFallbackToast, onFailedToast, onSuccess)

    fun rotateSingleImage(index: Int) = scramble.rotateSingleImage(index)
    fun stripImageExifAt(index: Int) = scramble.stripImageExifAt(index)
    fun reloadSingleImage(index: Int) = scramble.reloadSingleImage(index)
    fun saveSingleImage(index: Int, onComplete: (Boolean) -> Unit) = scramble.saveSingleImage(index, onComplete)

    // =========================================================================
    // 兼容层代理：Tab 1 图种隐写 (StegoViewModel)
    // =========================================================================
    var stegoMode: Int
        get() = stego.stegoMode
        set(value) { stego.stegoMode = value }

    var stegoCoverUri: Uri?
        get() = stego.stegoCoverUri
        set(value) { stego.stegoCoverUri = value }

    var stegoArchiveUri: Uri?
        get() = stego.stegoArchiveUri
        set(value) { stego.stegoArchiveUri = value }

    var stegoCoverThumbnail: Bitmap?
        get() = stego.stegoCoverThumbnail
        set(value) { stego.stegoCoverThumbnail = value }

    var stegoCoverFileSize: Long
        get() = stego.stegoCoverFileSize
        set(value) { stego.stegoCoverFileSize = value }

    var stegoArchiveFileSize: Long
        get() = stego.stegoArchiveFileSize
        set(value) { stego.stegoArchiveFileSize = value }

    var stegoExtractTargetUri: Uri?
        get() = stego.stegoExtractTargetUri
        set(value) { stego.stegoExtractTargetUri = value }

    var stegoExtractThumbnail: Bitmap?
        get() = stego.stegoExtractThumbnail
        set(value) { stego.stegoExtractThumbnail = value }

    var stegoExtractFileSize: Long
        get() = stego.stegoExtractFileSize
        set(value) { stego.stegoExtractFileSize = value }

    var stegoInspection: SteganographyHelper.StegoInspection?
        get() = stego.stegoInspection
        set(value) { stego.stegoInspection = value }

    var isStegoInspecting: Boolean
        get() = stego.isStegoInspecting
        set(value) { stego.isStegoInspecting = value }

    var isStegoProcessing: Boolean
        get() = stego.isStegoProcessing
        set(value) { stego.isStegoProcessing = value }

    fun setStegoCover(uri: Uri?, context: Context) = stego.setStegoCover(uri, context)
    fun setStegoArchive(uri: Uri?, context: Context) = stego.setStegoArchive(uri, context)
    fun setStegoExtractTarget(uri: Uri?, context: Context) = stego.setStegoExtractTarget(uri, context)
    fun createStego(context: Context, onResult: (Boolean) -> Unit) = stego.createStego(context, onResult)
    fun extractStego(context: Context, onResult: (String?) -> Unit) = stego.extractStego(context, onResult)

    // =========================================================================
    // 兼容层代理：Tab 2 幻影坦克 (MirageViewModel)
    // =========================================================================
    var mirageIsColoredMode: Boolean
        get() = mirage.mirageIsColoredMode
        set(value) { mirage.mirageIsColoredMode = value }

    var mirageScaleMode: MirageTankBridge.MirageScaleMode
        get() = mirage.mirageScaleMode
        set(value) { mirage.mirageScaleMode = value }

    var mirageCoverUri: Uri?
        get() = mirage.mirageCoverUri
        set(value) { mirage.mirageCoverUri = value }

    var mirageHiddenUri: Uri?
        get() = mirage.mirageHiddenUri
        set(value) { mirage.mirageHiddenUri = value }

    var mirageCoverThumbnail: Bitmap?
        get() = mirage.mirageCoverThumbnail
        set(value) { mirage.mirageCoverThumbnail = value }

    var mirageHiddenThumbnail: Bitmap?
        get() = mirage.mirageHiddenThumbnail
        set(value) { mirage.mirageHiddenThumbnail = value }

    var miragePreviewBitmap: Bitmap?
        get() = mirage.miragePreviewBitmap
        set(value) { mirage.miragePreviewBitmap = value }

    var mirageCoverBrightness: Float
        get() = mirage.mirageCoverBrightness
        set(value) { mirage.mirageCoverBrightness = value }

    var mirageCoverContrast: Float
        get() = mirage.mirageCoverContrast
        set(value) { mirage.mirageCoverContrast = value }

    var mirageHiddenBrightness: Float
        get() = mirage.mirageHiddenBrightness
        set(value) { mirage.mirageHiddenBrightness = value }

    var mirageHiddenContrast: Float
        get() = mirage.mirageHiddenContrast
        set(value) { mirage.mirageHiddenContrast = value }

    var isMirageProcessing: Boolean
        get() = mirage.isMirageProcessing
        set(value) { mirage.isMirageProcessing = value }

    var mirageBgMode: Int
        get() = mirage.mirageBgMode
        set(value) { mirage.mirageBgMode = value }

    var isMirageAdjustExpanded: Boolean
        get() = mirage.isMirageAdjustExpanded
        set(value) { mirage.isMirageAdjustExpanded = value }

    fun resetMirageParams() = mirage.resetMirageParams()
    fun resetMirageAdjustments() = mirage.resetMirageAdjustments()
    fun autoOptimizeMirageParams(context: Context? = null) = mirage.autoOptimizeMirageParams(context)
    fun setMirageCover(uri: Uri?, context: Context) = mirage.setMirageCover(uri, context)
    fun setMirageHidden(uri: Uri?, context: Context) = mirage.setMirageHidden(uri, context)
    fun loadMiragePreset(theme: MiragePresetGenerator.PresetTheme, context: Context) = mirage.loadMiragePreset(theme, context)
    fun swapMirageImages() = mirage.swapMirageImages()
    fun generateMiragePreview(context: Context, onResult: (Boolean) -> Unit = {}) = mirage.generateMiragePreview(context, onResult)
    fun exportMirageFullResolution(context: Context, maxDimension: Int = 0, onResult: (Boolean) -> Unit) = mirage.exportMirageFullResolution(context, maxDimension, onResult)
    fun saveMiragePreview(context: Context, onResult: (Boolean) -> Unit) = mirage.saveMiragePreview(context, onResult)

    // =========================================================================
    // 兼容层代理：Tab 3 安全保险箱 (SafeViewModel)
    // =========================================================================
    var safeMode: Int
        get() = safe.safeMode
        set(value) { safe.safeMode = value }

    var safePassword: String
        get() = safe.safePassword
        set(value) { safe.safePassword = value }

    var safeSourceUri: Uri?
        get() = safe.safeSourceUri
        set(value) { safe.safeSourceUri = value }

    var safeSourceFileName: String
        get() = safe.safeSourceFileName
        set(value) { safe.safeSourceFileName = value }

    var safeSourceFileSize: Long
        get() = safe.safeSourceFileSize
        set(value) { safe.safeSourceFileSize = value }

    var safeRpxHeader: CryptoUtils.RpxHeader?
        get() = safe.safeRpxHeader
        set(value) { safe.safeRpxHeader = value }

    var isSafeProcessing: Boolean
        get() = safe.isSafeProcessing
        set(value) { safe.isSafeProcessing = value }

    fun setSafeSourceFile(uri: Uri?, context: Context) = safe.setSafeSourceFile(uri, context)
    fun executeSafeEncryption(context: Context, onResult: (success: Boolean, message: String) -> Unit) = safe.executeSafeEncryption(context, onResult)
    fun executeSafeDecryption(context: Context, onResult: (success: Boolean, message: String) -> Unit) = safe.executeSafeDecryption(context, onResult)
}
