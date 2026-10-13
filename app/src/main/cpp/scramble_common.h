#ifndef REPIX_SCRAMBLE_COMMON_H
#define REPIX_SCRAMBLE_COMMON_H

#include <cstdint>
#include <cmath>
#include <vector>

namespace repix {

enum class ProcessType : int {
    ENCRYPT = 0,
    DECRYPT = 1
};

enum class AlgorithmType : int {
    TOMATO = 0,
    BLOCK = 1,
    ROW_PIXEL = 2,
    PER_PIXEL = 3,
    PIC_ENCRYPT_ROW = 4,
    PIC_ENCRYPT_ROW_COLUMN = 5
};

struct ScrambleResult {
    std::vector<int32_t> pixels;
    int32_t width;
    int32_t height;
};

// Math.floorDiv(a, 2) in Java (rounds towards negative infinity)
inline int32_t floor_div_2(int32_t a) {
    return (a >= 0) ? (a / 2) : ((a - 1) / 2);
}

// Math.signum in Java
inline int32_t signum(int32_t v) {
    return (v > 0) ? 1 : ((v < 0) ? -1 : 0);
}

} // namespace repix

#endif // REPIX_SCRAMBLE_COMMON_H
