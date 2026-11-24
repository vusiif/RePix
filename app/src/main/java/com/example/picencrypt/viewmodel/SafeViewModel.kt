package com.example.picencrypt.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.picencrypt.data.PreferencesRepository
import com.example.picencrypt.utils.CryptoUtils
import com.example.picencrypt.utils.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Feature ViewModel managing state and operations for the SafeBox (.rpx AES-256-GCM container) (Tab 3).
 */
class SafeViewModel(
    private val preferencesRepository: PreferencesRepository = PreferencesRepository.getInstance()
) : ViewModel() {

    var safeMode by mutableIntStateOf(0) // 0: 加密为 .rpx, 1: 解密 .rpx
    var safePassword by mutableStateOf("")
    var safeSourceUri by mutableStateOf<Uri?>(null)
    var safeSourceFileName by mutableStateOf("")
    var safeSourceFileSize by mutableLongStateOf(0L)
    var safeRpxHeader by mutableStateOf<CryptoUtils.RpxHeader?>(null)
    var isSafeProcessing by mutableStateOf(false)

    val isHapticFeedbackEnabled: Boolean
        get() = preferencesRepository.isHapticFeedbackEnabled()

    private var safeSourceJob: Job? = null

    fun setSafeSourceFile(uri: Uri?, context: Context) {
        safeSourceJob?.cancel()
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
            safeSourceJob = viewModelScope.launch(Dispatchers.IO) {
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
                val origName = FileUtils.sanitizeFilename(safeSourceFileName.ifEmpty { "file" }, "file")
                val outName = "${origName}.rpx"
                val plainLength = safeSourceFileSize.coerceAtLeast(0L)

                val pwdChars = pwd.toCharArray()
                val saved = try {
                    FileUtils.writeStreamToDownload(context, outName) { outStream ->
                        context.contentResolver.openInputStream(uri)?.use { inStream ->
                            CryptoUtils.encryptStream(
                                input = inStream,
                                output = outStream,
                                password = pwdChars,
                                originalFilename = origName,
                                plainLength = plainLength
                            )
                            true
                        } ?: false
                    }
                } finally {
                    pwdChars.fill('\u0000')
                }

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
            val tempFile = File(context.cacheDir, "temp_safe_decrypt_${System.currentTimeMillis()}.tmp")
            try {
                val pwdChars = pwd.toCharArray()
                val decryptResult = try {
                    context.contentResolver.openInputStream(uri)?.use { inStream ->
                        FileOutputStream(tempFile).use { tempOut ->
                            CryptoUtils.decryptStream(inStream, tempOut, pwdChars)
                        }
                    } ?: CryptoUtils.DecryptResult.InvalidFormat(context.getString(com.example.picencrypt.R.string.file_not_found))
                } finally {
                    pwdChars.fill('\u0000')
                }

                when (decryptResult) {
                    is CryptoUtils.DecryptResult.Success -> {
                        val outName = FileUtils.sanitizeFilename(decryptResult.originalFilename, "decrypted_file")
                        val saved = FileInputStream(tempFile).use { tempIn ->
                            FileUtils.saveStreamToDownload(context, outName, tempIn)
                        }
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
            } finally {
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    fun clearPassword() {
        safePassword = ""
    }

    fun clearSafeState() {
        safeSourceJob?.cancel()
        safeSourceUri = null
        safeSourceFileName = ""
        safeSourceFileSize = 0L
        safeRpxHeader = null
        safePassword = ""
        isSafeProcessing = false
    }
}
