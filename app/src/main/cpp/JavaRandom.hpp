#pragma once

#include <cstdint>

class JavaRandom {
public:
    explicit JavaRandom(int64_t seed) {
        setSeed(seed);
    }

    void setSeed(int64_t seed) {
        seed_ = (seed ^ MULTIPLIER) & MASK;
    }

    int64_t getSeed() const {
        return seed_;
    }

    int32_t next(int32_t bits) {
        seed_ = (seed_ * MULTIPLIER + ADDEND) & MASK;
        return static_cast<int32_t>(seed_ >> (48 - bits));
    }

    int32_t nextInt(int32_t bound) {
        if (bound <= 0) return 0;

        // If bound is a power of 2
        if ((bound & (bound - 1)) == 0) {
            return static_cast<int32_t>((static_cast<int64_t>(bound) * next(31)) >> 31);
        }

        // Standard java.util.Random rejection sampling
        int32_t bits, val;
        do {
            bits = next(31);
            val = bits % bound;
        } while (bits - val + (bound - 1) < 0);

        return val;
    }

    /**
     * Exact inverse of nextInt(bound).
     * Steps the LCG backward by one nextInt call and reconstructs the returned value.
     * Uses modular multiplicative inverse a^-1 = 0xdfe05bcb1365 (mod 2^48).
     */
    int32_t previousInt(int32_t bound) {
        if (bound <= 0) return 0;

        if ((bound & (bound - 1)) == 0) {
            int32_t bits = static_cast<int32_t>(seed_ >> (48 - 31));
            seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) & MASK;
            return static_cast<int32_t>((static_cast<int64_t>(bound) * bits) >> 31);
        }

        int32_t bits = static_cast<int32_t>(seed_ >> (48 - 31));
        int32_t val = bits % bound;
        seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) & MASK;

        // Roll back any rejection attempts that occurred within this same nextInt call
        while (true) {
            int32_t prev_bits = static_cast<int32_t>(seed_ >> (48 - 31));
            int32_t prev_val = prev_bits % bound;
            if (prev_bits - prev_val + (bound - 1) < 0) {
                seed_ = (seed_ * INV_MULTIPLIER + INV_ADDEND) & MASK;
            } else {
                break;
            }
        }

        return val;
    }

private:
    int64_t seed_{0};

    static constexpr int64_t MULTIPLIER     = 0x5DEECE66DLL;
    static constexpr int64_t ADDEND         = 0xBLL;
    static constexpr int64_t INV_MULTIPLIER = 0xdfe05bcb1365LL;
    static constexpr int64_t INV_ADDEND     = 0x615c0e462aa9LL;
    static constexpr int64_t MASK           = (1LL << 48) - 1;
};
