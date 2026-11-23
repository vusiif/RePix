#include "ScrambleEngine.hpp"
#include "JavaRandom.hpp"
#include <utility>

void processShuffleInPlace(int32_t* pixels, int32_t totalPixels, int64_t seed, bool isDecrypt) {
    if (!pixels || totalPixels <= 1) return;

    JavaRandom rng(seed);

    if (!isDecrypt) {
        // [Encrypt]: Forward Fisher-Yates
        for (int32_t i = totalPixels - 1; i > 0; --i) {
            int32_t j = rng.nextInt(i + 1);
            std::swap(pixels[i], pixels[j]);
        }
    } else {
        // [Decrypt]: O(1) auxiliary memory Reverse Fisher-Yates!
        // 1. Advance LCG to the final state in registers (fast O(N) integer stepping, 0 allocations)
        for (int32_t i = totalPixels - 1; i > 0; --i) {
            rng.nextInt(i + 1);
        }

        // 2. Step LCG backwards using modular inverse to produce j_1 .. j_{N-1} on the fly!
        for (int32_t i = 1; i < totalPixels; ++i) {
            int32_t j = rng.previousInt(i + 1);
            std::swap(pixels[i], pixels[j]);
        }
    }
}
