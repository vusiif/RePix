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

private:
    int64_t seed_{0};

    static constexpr int64_t MULTIPLIER = 0x5DEECE66DLL;
    static constexpr int64_t ADDEND     = 0xBLL;
    static constexpr int64_t MASK       = (1LL << 48) - 1;
};
