package com.example.picencrypt.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.SteganographyHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Feature ViewModel managing state and operations for Image Steganography (Tab 1).
 */
class StegoViewModel(
    private val preferencesRepository: PreferencesRepository = PreferencesRepository.getInstance()
) : ViewModel() {

    var stegoMode by mutableIntStateOf(0) // 0: 制作图种, 1: 提取图种
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

    val isHapticFeedbackEnabled: Boolean
        get() = preferencesRepository.isHapticFeedbackEnabled()

    private var stegoCoverJob: Job? = null
    private var stegoArchiveJob: Job? = null
    private var stegoExtractJob: Job? = null

    fun setStegoCover(uri: Uri?, context: Context) {
        stegoCoverJob?.cancel()
        stegoCoverUri = uri
        if (uri == null) {
            stegoCoverThumbnail = null
            stegoCoverFileSize = 0L
        } else {
            stegoCoverJob = viewModelScope.launch(Dispatchers.IO) {
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
        stegoArchiveJob?.cancel()
        stegoArchiveUri = uri
        if (uri == null) {
            stegoArchiveFileSize = 0L
        } else {
            stegoArchiveJob = viewModelScope.launch(Dispatchers.IO) {
                val size = FileUtils.getUriFileSize(context, uri)
                withContext(Dispatchers.Main) {
                    stegoArchiveFileSize = size
                }
            }
        }
    }

    fun setStegoExtractTarget(uri: Uri?, context: Context) {
        stegoExtractJob?.cancel()
        stegoExtractTargetUri = uri
        stegoInspection = null
        if (uri == null) {
            stegoExtractThumbnail = null
            stegoExtractFileSize = 0L
            isStegoInspecting = false
        } else {
            isStegoInspecting = true
            stegoExtractJob = viewModelScope.launch(Dispatchers.IO) {
                val thumb = FileUtils.loadThumbnail(context, uri, 240)
                val totalSize = FileUtils.getUriFileSize(context, uri)
                val inspection = FileUtils.inspectStegoUri(context, uri)
                withContext(Dispatchers.Main) {
                    stegoExtractThumbnail = thumb
                    stegoExtractFileSize = if (totalSize > 0L) totalSize else inspection.totalSize
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

    fun clearStegoState() {
        stegoCoverJob?.cancel()
        stegoArchiveJob?.cancel()
        stegoExtractJob?.cancel()
        stegoCoverUri = null
        stegoArchiveUri = null
        stegoCoverThumbnail = null
        stegoCoverFileSize = 0L
        stegoArchiveFileSize = 0L
        stegoExtractTargetUri = null
        stegoExtractThumbnail = null
        stegoExtractFileSize = 0L
        stegoInspection = null
        isStegoInspecting = false
        isStegoProcessing = false
    }
}
