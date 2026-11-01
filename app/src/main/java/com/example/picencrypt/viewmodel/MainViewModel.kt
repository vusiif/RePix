package com.example.picencrypt.viewmodel

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.R
import com.example.picencrypt.model.Algorithm
import com.example.picencrypt.model.MyImage
import com.example.picencrypt.utils.FileUtils
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.MirageTankBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

class MainViewModel : ViewModel() {

    // ---------------- 导航与弹窗全局状态 ----------------
    var currentTab by mutableStateOf(0)
    var isShowInfo by mutableStateOf(false)
    var isShowRequestWritePermissionDialog by mutableStateOf(false)
    var isShowMethodAndKeySettingOnTopLayer by mutableStateOf(false)

    // ---------------- Tab 0: 图片混淆状态 ----------------
    val images = mutableStateListOf<MyImage>()
    var selectedAlgorithm by mutableStateOf(Algorithm.TOMATO)
    var keyString by mutableStateOf("1")
    var isCompatMode by mutableStateOf(false)
    var isDropdownMenuExpanded by mutableStateOf(false)

    var progress by mutableStateOf(0f)
    var isShowProgress by mutableStateOf(false)
    var newImageUriCount by mutableStateOf(0)

    // ---------------- Tab 1: 图种隐写状态 ----------------
    var stegoMode by mutableStateOf(0)
    var stegoCoverUri by mutableStateOf<Uri?>(null)
    var stegoArchiveUri by mutableStateOf<Uri?>(null)
    var stegoExtractTargetUri by mutableStateOf<Uri?>(null)
    var isStegoProcessing by mutableStateOf(false)

    // ---------------- Tab 2: 幻影坦克状态 ----------------
    var mirageIsColoredMode by mutableStateOf(true)
    var mirageCoverUri by mutableStateOf<Uri?>(null)
    var mirageHiddenUri by mutableStateOf<Uri?>(null)
    var isMirageProcessing by mutableStateOf(false)

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

    fun addImages(uris: List<Uri>, context: Context, onComplete: () -> Unit) {
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

    fun processAllImages(
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit,
        onFinish: (isEncrypt: Boolean) -> Unit
    ) {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            val count = images.size
            for (i in 0 until count) {
                if (i in images.indices) {
                    var hadFallback = false
                    val success = images[i].process(
                        selectedAlgorithm,
                        processType,
                        keyString,
                        forceCompat = isCompatMode,
                        onFallback = { hadFallback = true }
                    )
                    if (hadFallback) {
                        withContext(Dispatchers.Main) { onFallbackToast() }
                    } else if (!success) {
                        withContext(Dispatchers.Main) { onFailedToast() }
                    }
                }
                progress = (i + 1).toFloat() / count
            }
            withContext(Dispatchers.Main) {
                isShowProgress = false
                onFinish(processType == ProcessType.ENCRYPT)
            }
        }
    }

    fun reloadAllImages(onFileNotFound: () -> Unit) {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            val size = images.size
            for (i in size - 1 downTo 0) {
                if (i in images.indices) {
                    try {
                        images[i].reload()
                    } catch (e: FileNotFoundException) {
                        withContext(Dispatchers.Main) {
                            if (i in images.indices) images.removeAt(i)
                            onFileNotFound()
                        }
                    }
                }
                progress = (size - i).toFloat() / size
            }
            withContext(Dispatchers.Main) {
                isShowProgress = false
            }
        }
    }

    fun rotateAllImages() {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            isShowProgress = true
            progress = 0f
            val count = images.size
            for (i in 0 until count) {
                if (i in images.indices) {
                    images[i].rotate(180f)
                }
                progress = (i + 1).toFloat() / count
            }
            withContext(Dispatchers.Main) {
                isShowProgress = false
            }
        }
    }

    fun saveAllImages(onComplete: () -> Unit) {
        if (images.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            isShowProgress = true
            progress = 0f
            val count = images.size
            for (i in 0 until count) {
                if (i in images.indices) {
                    images[i].save(i)
                }
                progress = (i + 1).toFloat() / count
            }
            withContext(Dispatchers.Main) {
                isShowProgress = false
                onComplete()
            }
        }
    }

    fun processSingleImage(
        index: Int,
        processType: ProcessType,
        onFallbackToast: () -> Unit,
        onFailedToast: () -> Unit
    ) {
        if (index !in images.indices) return
        viewModelScope.launch(Dispatchers.Default) {
            var hadFallback = false
            val success = images[index].process(
                selectedAlgorithm,
                processType,
                keyString,
                forceCompat = isCompatMode,
                onFallback = { hadFallback = true }
            )
            withContext(Dispatchers.Main) {
                if (hadFallback) onFallbackToast()
                else if (!success) onFailedToast()
            }
        }
    }

    fun rotateSingleImage(index: Int) {
        if (index in images.indices) {
            images[index].rotate(180f)
        }
    }

    fun reloadSingleImage(index: Int) {
        if (index in images.indices) {
            images[index].reload()
        }
    }

    fun saveSingleImage(index: Int, onComplete: (Boolean) -> Unit) {
        if (index !in images.indices) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = images[index].save(index)
            withContext(Dispatchers.Main) {
                onComplete(result)
            }
        }
    }

    // ---------------- 业务方法：图种隐写 ----------------

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

    fun generateMirage(context: Context, onResult: (Boolean) -> Unit) {
        val cUri = mirageCoverUri ?: return
        val hUri = mirageHiddenUri ?: return
        isMirageProcessing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val coverBmp = context.contentResolver.openInputStream(cUri)?.use { BitmapFactory.decodeStream(it) }
                val hiddenBmp = context.contentResolver.openInputStream(hUri)?.use { BitmapFactory.decodeStream(it) }
                if (coverBmp != null && hiddenBmp != null) {
                    val (saved, _) = MirageTankBridge.createAndSaveMirageTank(
                        context,
                        coverBmp,
                        hiddenBmp,
                        isColored = mirageIsColoredMode
                    )
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
            }
        }
    }
}
