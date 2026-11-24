package com.example.picencrypt.utils

import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType

/**
 * Pure Kotlin/Java fallback implementation of the LCG Shuffle scramble algorithm.
 * Features:
 * - Fisher-Yates forward shuffling using Java LCG random sequence for encryption.
 * - O(1) auxiliary memory reverse Fisher-Yates shuffling using LCG modular multiplicative inverse for decryption.
 * - Bit-identical and fully compatible with native C++ [NativeShuffleBridge] / `ScrambleEngine.cpp`.
 */
class LcgShuffleScramble : ImageScramble {

    private val seed: Long

    constructor(image: Image, key: String) : super(image) {
        this.seed = NativeShuffleBridge.stringToSeed(key)
    }

    constructor(pixels: IntArray, width: Int, height: Int, seed: Long) : super(pixels, width, height) {
        this.seed = seed
    }

    override fun process(processType: ProcessType): Image {
        val total = pixelCount
        if (total <= 1) return Image(pixels, width, height)

        val outPixels = pixels.clone()
        val rng = JavaRandom(seed)

        if (processType == ProcessType.ENCRYPT) {
            for (i in total - 1 downTo 1) {
                val j = rng.nextInt(i + 1)
                val temp = outPixels[i]
                outPixels[i] = outPixels[j]
                outPixels[j] = temp
            }
        } else {
            // Advance LCG state forward across all steps to the final register state
            for (i in total - 1 downTo 1) {
                rng.nextInt(i + 1)
            }
            // Step LCG backwards using modular inverse to produce j_1 .. j_{total-1} on the fly
            for (i in 1 until total) {
                val j = rng.previousInt(i + 1)
                val temp = outPixels[i]
                outPixels[i] = outPixels[j]
                outPixels[j] = temp
            }
        }

        return Image(outPixels, width, height)
    }

    override fun encrypt(): Image = process(ProcessType.ENCRYPT)

    override fun decrypt(): Image = process(ProcessType.DECRYPT)

    /**
     * Exact 48-bit linear congruential generator (LCG) mirroring java.util.Random and JavaRandom.hpp.
     */
    class JavaRandom(seed: Long) {
        private var seed_: Long = 0L

        companion object {
            private const val MULTIPLIER = 0x5DEECE66DL
            private const val ADDEND = 0xBL
            private const val INV_MULTIPLIER = 0xdfe05bcb1365L
            private const val INV_ADDEND = 0x615c0e462aa9L
            private const val MASK = (1L shl 48) - 1L
        }

        init {
            setSeed(seed)
        }

        fun setSeed(seed: Long) {
            seed_ = (seed xor MULTIPLIER) and MASK
        }

        fun getSeed(): Long = seed_

        fun next(bits: Int): Int {
            seed_ = (seed_ * MULTIPLIER + ADDEND) and MASK
            return (seed_ ushr (48 - bits)).toInt()
        }

        fun nextInt(bound: Int): Int {
            if (bound <= 0) return 0
            if ((bound and (bound - 1)) == 0) {
                return ((bound.toLong() * next(31)) shr 31).toInt()
            }
            var bits: Int
            var `val`: Int
            do {
                bits = next(31)
                `val` = bits % bound
            } while (bits - `val` + (bound - 1) < 0)
            return `val`
        }

        /**
         * Computes the exact previous nextInt(bound) return value and rolls back the internal seed.
         */
        fun previousInt(bound: Int): Int {
            if (bound <= 0) return 0
            if ((bound and (bound - 1)) == 0) {
                val bits = (seed_ ushr (48 - 31)).toInt()
                seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) and MASK
                return ((bound.toLong() * bits) shr 31).toInt()
            }
            val bits = (seed_ ushr (48 - 31)).toInt()
            val `val` = bits % bound
            seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) and MASK

            while (true) {
                val prevBits = (seed_ ushr (48 - 31)).toInt()
                val prevVal = prevBits % bound
                if (prevBits - prevVal + (bound - 1) < 0) {
                    seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) and MASK
                } else {
                    break
                }
            }
            return `val`
        }
    }
}
