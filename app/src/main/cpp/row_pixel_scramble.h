#ifndef REPIX_ROW_PIXEL_SCRAMBLE_H
#define REPIX_ROW_PIXEL_SCRAMBLE_H

#include "base_pixel_scramble.h"

namespace repix {

class RowPixelScramble : public BasePixelScramble {
public:
    RowPixelScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key);

    ScrambleResult process(ProcessType process_type);
};

} // namespace repix

#endif // REPIX_ROW_PIXEL_SCRAMBLE_H
