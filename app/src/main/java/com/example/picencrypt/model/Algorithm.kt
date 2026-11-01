package com.example.picencrypt.model

import com.example.picencrypt.R

enum class Algorithm(val stringResId: Int) {
    TOMATO(R.string.tomato_algorithm),
    BLOCK(R.string.block_algorithm),
    ROW_PIXEL(R.string.row_pixel_algorithm),
    PER_PIXEL(R.string.per_pixel_algorithm),
    PIC_ENCRYPT_ROW(R.string.pic_encrypt_row_pixel),
    PIC_ENCRYPT_ROW_AND_COLUMN(R.string.pic_encrypt_row_and_column_pixel),
    LCG_SHUFFLE(R.string.lcg_shuffle_algorithm);

    companion object {
        const val DEFAULT_TOMATO_KEY = "1"
        const val DEFAULT_OTHER_KEY = "0.666"

        fun isTomatoOrPicEncrypt(algorithm: Algorithm): Boolean {
            return algorithm == TOMATO ||
                    algorithm == PIC_ENCRYPT_ROW ||
                    algorithm == PIC_ENCRYPT_ROW_AND_COLUMN
        }

        fun checkTomatoKeyValidity(key: String): Boolean {
            val d = key.toDoubleOrNull() ?: return false
            return d > 0.0 && d <= 1.618
        }

        fun checkPicEncryptKeyValidity(key: String): Boolean {
            val d = key.toDoubleOrNull() ?: return false
            return d > 0.0 && d < 1.0
        }
    }
}
