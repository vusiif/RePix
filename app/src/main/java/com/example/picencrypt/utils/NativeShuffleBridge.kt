package com.example.picencrypt.utils

import android.graphics.Bitmap

object NativeShuffleBridge {
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
     * 原地（In-place）对 Android Bitmap 进行零拷贝混淆或解密
     *
     * @param bitmap 必须为 ARGB_8888 格式的 Bitmap
     * @param seed 随机数种子 (对标 java.util.Random)
     * @param isDecrypt false 为加密/混淆，true 为解密还原
     */
    external fun scrambleBitmap(
        bitmap: Bitmap,
        seed: Long,
        isDecrypt: Boolean
    )

    /**
     * 将密码字符串确定性映射为 64 位 Long 种子
     */
    fun stringToSeed(password: String): Long {
        var h = 0L
        for (ch in password) {
            h = 31L * h + ch.code.toLong()
        }
        return h
    }
}
