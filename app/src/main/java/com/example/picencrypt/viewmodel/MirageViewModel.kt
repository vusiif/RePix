package com.example.picencrypt.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.MirageAutoOptimizer
import com.example.picencrypt.utils.MiragePresetGenerator
import com.example.picencrypt.utils.MirageTankBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Feature ViewModel managing state and operations for Mirage Tank (Tab 2).
 */
class MirageViewModel(
    private val preferencesRepository: PreferencesRepository = PreferencesRepository.getInstance()
) : ViewModel() {

    var mirageIsColoredMode by mutableStateOf(true)
    var mirageScaleMode by mutableStateOf(MirageTankBridge.MirageScaleMode.FIT)
    var mirageCoverUri by mutableStateOf<Uri?>(null)
    var mirageHiddenUri by mutableStateOf<Uri?>(null)
    var mirageCoverThumbnail by mutableStateOf<Bitmap?>(null)
    var mirageHiddenThumbnail by mutableStateOf<Bitmap?>(null)
    var miragePreviewBitmap by mutableStateOf<Bitmap?>(null)

    var mirageCoverBrightness by mutableFloatStateOf(0f)
    var mirageCoverContrast by mutableFloatStateOf(1f)
    var mirageHiddenBrightness by mutableFloatStateOf(0f)
    var mirageHiddenContrast by mutableFloatStateOf(1f)
    var isMirageProcessing by mutableStateOf(false)
    var mirageBgMode by mutableIntStateOf(0) // 0: 白底, 1: 黑底, 2: 棋盘格
    var isMirageAdjustExpanded by mutableStateOf(false)

    val isHapticFeedbackEnabled: Boolean
        get() = preferencesRepository.isHapticFeedbackEnabled()

    private var mirageCoverJob: Job? = null
    private var mirageHiddenJob: Job? = null

    fun resetMirageParams() {
        mirageCoverBrightness = 0f
        mirageCoverContrast = 1f
        mirageHiddenBrightness = 0f
        mirageHiddenContrast = 1f
        miragePreviewBitmap = null
    }

    fun resetMirageAdjustments() {
        resetMirageParams()
    }

    fun autoOptimizeMirageParams(context: Context? = null): Boolean {
        val cThumb = mirageCoverThumbnail
        val hThumb = mirageHiddenThumbnail
        if (cThumb == null || hThumb == null) return false

        val params = MirageAutoOptimizer.optimize(cThumb, hThumb)
        mirageCoverBrightness = params.coverBrightness
        mirageCoverContrast = params.coverContrast
        mirageHiddenBrightness = params.hiddenBrightness
        mirageHiddenContrast = params.hiddenContrast
        miragePreviewBitmap = null

        if (context != null) {
            generateMiragePreview(context)
        }
        return true
    }

    fun setMirageCover(uri: Uri?, context: Context) {
        mirageCoverJob?.cancel()
        mirageCoverUri = uri
        miragePreviewBitmap = null
        if (uri == null) {
            mirageCoverThumbnail = null
        } else {
            mirageCoverJob = viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                withContext(Dispatchers.Main) {
                    mirageCoverThumbnail = thumb
                }
            }
        }
    }

    fun setMirageHidden(uri: Uri?, context: Context) {
        mirageHiddenJob?.cancel()
        mirageHiddenUri = uri
        miragePreviewBitmap = null
        if (uri == null) {
            mirageHiddenThumbnail = null
        } else {
            mirageHiddenJob = viewModelScope.launch(Dispatchers.IO) {
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

    fun exportMirageFullResolution(
        context: Context,
        maxDimension: Int = 0,
        onResult: (Boolean) -> Unit
    ) {
        val cUri = mirageCoverUri ?: run {
            onResult(false)
            return
        }
        val hUri = mirageHiddenUri ?: run {
            onResult(false)
            return
        }

        isMirageProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            var coverBmp: Bitmap? = null
            var hiddenBmp: Bitmap? = null
            var outBmp: Bitmap? = null
            try {
                coverBmp = context.contentResolver.openInputStream(cUri)?.use { BitmapFactory.decodeStream(it) }
                hiddenBmp = context.contentResolver.openInputStream(hUri)?.use { BitmapFactory.decodeStream(it) }
                if (coverBmp != null && hiddenBmp != null) {
                    val (saved, generatedBmp) = MirageTankBridge.createAndSaveMirageTank(
                        context,
                        coverBmp,
                        hiddenBmp,
                        isColored = mirageIsColoredMode,
                        maxDimension = maxDimension,
                        mode = mirageScaleMode,
                        coverBrightness = mirageCoverBrightness,
                        coverContrast = mirageCoverContrast,
                        hiddenBrightness = mirageHiddenBrightness,
                        hiddenContrast = mirageHiddenContrast
                    )
                    outBmp = generatedBmp
                    withContext(Dispatchers.Main) {
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
            } finally {
                coverBmp?.takeUnless { it.isRecycled }?.recycle()
                hiddenBmp?.takeUnless { it.isRecycled }?.recycle()
                outBmp?.takeUnless { it.isRecycled }?.recycle()
            }
        }
    }

    fun saveMiragePreview(context: Context, onResult: (Boolean) -> Unit) {
        exportMirageFullResolution(context, maxDimension = 0, onResult = onResult)
    }

    fun clearMirageState() {
        mirageCoverJob?.cancel()
        mirageHiddenJob?.cancel()
        mirageCoverUri = null
        mirageHiddenUri = null
        mirageCoverThumbnail = null
        mirageHiddenThumbnail = null
        miragePreviewBitmap = null
        isMirageProcessing = false
        resetMirageParams()
    }
}
