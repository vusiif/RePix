package com.example.picencrypt.utils

import android.graphics.Bitmap

object NativeImageScramble {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("repix_native")
            isLoaded = true
        } catch (t: Throwable) {
            isLoaded = false
        }
    }

    fun isAvailable(): Boolean = isLoaded

    /**
     * @param algorithmCode
     * 0: TOMATO
     * 1: BLOCK
     * 2: ROW_PIXEL
     * 3: PER_PIXEL
     * 4: PIC_ENCRYPT_ROW
     * 5: PIC_ENCRYPT_ROW_COLUMN
     *
     * @param processCode
     * 0: ENCRYPT
     * 1: DECRYPT
     */
    @JvmStatic
    external fun nativeProcessPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        algorithmCode: Int,
        processCode: Int,
        strKey: String?,
        dKey: Double
    ): ImageScramble.Image?

    @JvmStatic
    external fun nativeProcessBitmap(
        bitmap: Bitmap,
        algorithmCode: Int,
        processCode: Int,
        strKey: String?,
        dKey: Double
    ): Boolean

    fun process(
        pixels: IntArray,
        width: Int,
        height: Int,
        algorithmCode: Int,
        processType: ImageScramble.ProcessType,
        key: String
    ): ImageScramble.Image? {
        if (!isLoaded) return null
        val procCode = if (processType == ImageScramble.ProcessType.ENCRYPT) 0 else 1
        val dKey = key.toDoubleOrNull() ?: 1.0
        return nativeProcessPixels(pixels, width, height, algorithmCode, procCode, key, dKey)
    }
}
