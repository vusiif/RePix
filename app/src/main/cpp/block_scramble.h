#ifndef REPIX_BLOCK_SCRAMBLE_H
#define REPIX_BLOCK_SCRAMBLE_H

#include "base_pixel_scramble.h"

namespace repix {

class BlockScramble : public BasePixelScramble {
public:
    BlockScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key,
                  int32_t x_block_count = 32, int32_t y_block_count = 32);

    ScrambleResult process(ProcessType process_type);

private:
    int32_t x_block_count_;
    int32_t y_block_count_;
};

} // namespace repix

#endif // REPIX_BLOCK_SCRAMBLE_H
