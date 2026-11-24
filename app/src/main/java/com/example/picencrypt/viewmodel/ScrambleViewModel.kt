package com.example.picencrypt.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.model.ImageExportFormat
import com.example.picencrypt.model.MyImage
import com.example.picencrypt.utils.ExportFilenameGenerator
import com.example.picencrypt.utils.ImageScramble.ProcessType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

/**
 * Feature ViewModel managing state and operations for Image Scrambling & Batch Processing (Tab 0).
 */
class ScrambleViewModel(
    private val preferencesRepository: PreferencesRepository = PreferencesRepository.getInstance()
) : ViewModel() {

    val images = mutableStateListOf<MyImage>()
    var selectedAlgorithm by mutableStateOf(Algorithm.TOMATO)
    var keyString by mutableStateOf("1")
    var isCompatMode by mutableStateOf(false)
    var isDropdownMenuExpanded by mutableStateOf(false)
    var isShowMethodAndKeySettingOnTopLayer by mutableStateOf(false)
    var isShowRequestWritePermissionDialog by mutableStateOf(false)

    var progress by mutableFloatStateOf(0f)
    var isShowProgress by mutableStateOf(false)
    var newImageUriCount by mutableIntStateOf(0)
    var lastProcessType by mutableStateOf<ProcessType?>(null)
    val failedProcessImageIds = mutableStateListOf<String>()
    val failedSaveImageIds = mutableStateListOf<String>()

    private var batchProcessJob: Job? = null
    private var batchSaveJob: Job? = null

    // Preferences accessors
    val isHapticFeedbackEnabled: Boolean
        get() = preferencesRepository.isHapticFeedbackEnabled()

    val exportFormat: ImageExportFormat
        get() = preferencesRepository.getExportFormat()

    val exportQuality: Int
        get() = preferencesRepository.getExportQuality()

    val isStripExifOnExport: Boolean
        get() = preferencesRepository.isStripExifOnExport()

    val appThemeMode: com.example.picencrypt.model.AppThemeMode
        get() = preferencesRepository.getAppThemeMode()

    val exportFilenamePattern: String
        get() = preferencesRepository.getExportFilenamePattern()

    val exportCustomPrefix: String
        get() = preferencesRepository.getExportCustomPrefix()

    fun setAppThemeMode(mode: com.example.picencrypt.model.AppThemeMode) = preferencesRepository.setAppThemeMode(mode)
    fun setExportFormat(format: ImageExportFormat) = preferencesRepository.setExportFormat(format)
    fun setExportQuality(quality: Int) = preferencesRepository.setExportQuality(quality)
    fun setStripExifOnExport(enabled: Boolean) = preferencesRepository.setStripExifOnExport(enabled)
    fun setExportFilenamePattern(pattern: String) = preferencesRepository.setExportFilenamePattern(pattern)
    fun setExportCustomPrefix(prefix: String) = preferencesRepository.setExportCustomPrefix(prefix)
    fun setHapticFeedbackEnabled(enabled: Boolean) = preferencesRepository.setHapticFeedbackEnabled(enabled)

    data class BatchSaveConfig(
        val format: ImageExportFormat,
        val quality: Int,
        val stripExif: Boolean,
        val filenamePattern: String,
        val customPrefix: String,
        val algorithmName: String
    )

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

    fun loadDemoSample(context: Context, onComplete: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val demoUri = com.example.picencrypt.utils.DemoImageGenerator.createDemoImageUri(context)
            withContext(Dispatchers.Main) {
                addImages(listOf(demoUri), context, onComplete)
            }
        }
    }

    fun removeImageAt(index: Int) {
        if (index in images.indices) {
            val removed = images.removeAt(index)
            failedProcessImageIds.remove(removed.id)
            failedSaveImageIds.remove(removed.id)
        }
    }

    fun clearAllImages() {
        images.clear()
        failedProcessImageIds.clear()
        failedSaveImageIds.clear()
    }

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
        lastProcessType = processType
        failedProcessImageIds.clear()
        val algoSnapshot = selectedAlgorithm
        val keySnapshot = keyString
        val compatSnapshot = isCompatMode
        val typeSnapshot = processType
        val startTime = System.currentTimeMillis()

        batchProcessJob = viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            val snapshot = images.toList()
            var successCount = 0
            var failCount = 0
            val failedIds = mutableListOf<String>()
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
                    failedIds.add(item.id)
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
                failedProcessImageIds.clear()
                failedProcessImageIds.addAll(failedIds)
                for (i in images.indices) {
                    images[i] = images[i]
                }
                isShowProgress = false
                onFinish(processType == ProcessType.ENCRYPT, successCount, failCount, durationMs)
            }
        }
    }

    fun retryFailedProcess(
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onFinish: (isEncrypt: Boolean, successCount: Int, failCount: Int, durationMs: Long) -> Unit
    ) {
        if (failedProcessImageIds.isEmpty() || isShowProgress) return
        val targetIds = failedProcessImageIds.toSet()
        val targetImages = images.filter { it.id in targetIds }
        if (targetImages.isEmpty()) {
            failedProcessImageIds.clear()
            return
        }
        lastProcessType = processType
        val algoSnapshot = selectedAlgorithm
        val keySnapshot = keyString
        val compatSnapshot = isCompatMode
        val typeSnapshot = processType
        val startTime = System.currentTimeMillis()

        batchProcessJob = viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            var successCount = 0
            var failCount = 0
            val newlySucceededIds = mutableListOf<String>()
            for (i in targetImages.indices) {
                if (!isActive) break
                val item = targetImages[i]
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
                    newlySucceededIds.add(item.id)
                } else {
                    failCount++
                }
                if (hadFallback) {
                    withContext(Dispatchers.Main) { onFallbackToast() }
                } else if (!success) {
                    withContext(Dispatchers.Main) { onFailedToast() }
                }
                progress = (i + 1).toFloat() / targetImages.size
            }
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                failedProcessImageIds.removeAll(newlySucceededIds)
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
        failedSaveImageIds.clear()
        val startTime = System.currentTimeMillis()
        val saveConfig = BatchSaveConfig(
            format = exportFormat,
            quality = exportQuality,
            stripExif = isStripExifOnExport,
            filenamePattern = exportFilenamePattern,
            customPrefix = exportCustomPrefix,
            algorithmName = selectedAlgorithm.name
        )
        batchSaveJob = viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            val snapshot = images.toList()
            var successCount = 0
            var failCount = 0
            val failedIds = mutableListOf<String>()
            for (i in snapshot.indices) {
                if (!isActive) break
                val resolvedName = ExportFilenameGenerator.generateFilename(
                    template = saveConfig.filenamePattern,
                    prefix = saveConfig.customPrefix,
                    originalFilename = snapshot[i].filename,
                    index = i,
                    algorithmName = saveConfig.algorithmName,
                    extension = saveConfig.format.extension
                )
                val ok = snapshot[i].saveWithDetail(
                    i,
                    format = saveConfig.format,
                    quality = saveConfig.quality,
                    stripExif = saveConfig.stripExif,
                    customFilename = resolvedName
                ) is com.example.picencrypt.model.SaveResult.Success
                if (ok) {
                    successCount++
                } else {
                    failCount++
                    failedIds.add(snapshot[i].id)
                }
                progress = (i + 1).toFloat() / snapshot.size
            }
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                failedSaveImageIds.clear()
                failedSaveImageIds.addAll(failedIds)
                isShowProgress = false
                onComplete(successCount, failCount, durationMs)
            }
        }
    }

    fun retryFailedSave(onComplete: (successCount: Int, failCount: Int, durationMs: Long) -> Unit) {
        if (failedSaveImageIds.isEmpty() || isShowProgress) return
        val targetIds = failedSaveImageIds.toSet()
        val targetImages = images.filter { it.id in targetIds }
        if (targetImages.isEmpty()) {
            failedSaveImageIds.clear()
            return
        }
        val startTime = System.currentTimeMillis()
        val saveConfig = BatchSaveConfig(
            format = exportFormat,
            quality = exportQuality,
            stripExif = isStripExifOnExport,
            filenamePattern = exportFilenamePattern,
            customPrefix = exportCustomPrefix,
            algorithmName = selectedAlgorithm.name
        )
        batchSaveJob = viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            var successCount = 0
            var failCount = 0
            val newlySucceededIds = mutableListOf<String>()
            for (i in targetImages.indices) {
                if (!isActive) break
                val item = targetImages[i]
                val originalIndex = images.indexOf(item).coerceAtLeast(i)
                val resolvedName = ExportFilenameGenerator.generateFilename(
                    template = saveConfig.filenamePattern,
                    prefix = saveConfig.customPrefix,
                    originalFilename = item.filename,
                    index = originalIndex,
                    algorithmName = saveConfig.algorithmName,
                    extension = saveConfig.format.extension
                )
                val ok = item.saveWithDetail(
                    originalIndex,
                    format = saveConfig.format,
                    quality = saveConfig.quality,
                    stripExif = saveConfig.stripExif,
                    customFilename = resolvedName
                ) is com.example.picencrypt.model.SaveResult.Success
                if (ok) {
                    successCount++
                    newlySucceededIds.add(item.id)
                } else {
                    failCount++
                }
                progress = (i + 1).toFloat() / targetImages.size
            }
            val durationMs = System.currentTimeMillis() - startTime
            withContext(Dispatchers.Main) {
                failedSaveImageIds.removeAll(newlySucceededIds)
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
                    images[index] = item
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

    fun stripImageExifAt(index: Int) {
        if (index in images.indices) {
            images[index].stripExifPrivacy()
            images[index] = images[index]
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
        val resolvedName = ExportFilenameGenerator.generateFilename(
            template = exportFilenamePattern,
            prefix = exportCustomPrefix,
            originalFilename = item.filename,
            index = index,
            algorithmName = selectedAlgorithm.name,
            extension = exportFormat.extension
        )
        viewModelScope.launch(Dispatchers.IO) {
            val result = item.saveWithDetail(
                index,
                format = exportFormat,
                quality = exportQuality,
                stripExif = isStripExifOnExport,
                customFilename = resolvedName
            ) is com.example.picencrypt.model.SaveResult.Success
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }
}
