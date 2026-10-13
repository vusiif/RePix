#include "ScrambleEngine.hpp"
#include "JavaRandom.hpp"
#include <vector>
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
        // [Decrypt]: Reverse Fisher-Yates
        std::vector<int32_t> swapIndices(totalPixels);
        for (int32_t i = totalPixels - 1; i > 0; --i) {
            swapIndices[i] = rng.nextInt(i + 1);
        }

        for (int32_t i = 1; i < totalPixels; ++i) {
            int32_t j = swapIndices[i];
            std::swap(pixels[i], pixels[j]);
        }
    }
}
