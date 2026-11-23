package com.example.picencrypt

import org.junit.Assert.assertEquals
import org.junit.Test

class JavaRandomReverseLcgTest {

    private class JavaRandomSim(seed: Long) {
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

    @Test
    fun testLcgForwardAndBackwardSteppingSymmetry() {
        val total = 50_000
        val rng = JavaRandomSim(42L)

        // Generate forward Fisher-Yates bounds: (total down to 2)
        val forwardResults = IntArray(total - 1)
        for (i in total - 1 downTo 1) {
            forwardResults[total - 1 - i] = rng.nextInt(i + 1)
        }

        // Generate reverse Fisher-Yates bounds: (2 up to total)
        val reverseResults = IntArray(total - 1)
        for (i in 1 until total) {
            reverseResults[i - 1] = rng.previousInt(i + 1)
        }

        // reverseResults should be the exact reverse of forwardResults
        for (i in 0 until total - 1) {
            val expected = forwardResults[forwardResults.size - 1 - i]
            val actual = reverseResults[i]
            assertEquals("Index $i must match", expected, actual)
        }
    }
}
