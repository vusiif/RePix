#ifndef REPIX_PER_PIXEL_SCRAMBLE_H
#define REPIX_PER_PIXEL_SCRAMBLE_H

#include "base_pixel_scramble.h"

namespace repix {

class PerPixelScramble : public BasePixelScramble {
public:
    PerPixelScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key);

    ScrambleResult process(ProcessType process_type);
};

} // namespace repix

#endif // REPIX_PER_PIXEL_SCRAMBLE_H
