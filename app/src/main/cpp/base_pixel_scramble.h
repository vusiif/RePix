#ifndef REPIX_BASE_PIXEL_SCRAMBLE_H
#define REPIX_BASE_PIXEL_SCRAMBLE_H

#include "scramble_common.h"
#include <string>
#include <vector>

namespace repix {

class BasePixelScramble {
public:
    BasePixelScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key);
    virtual ~BasePixelScramble() = default;

    std::vector<int32_t> shuffle(int32_t length) const;

protected:
    const int32_t* pixels_;
    int32_t width_;
    int32_t height_;
    int32_t pixel_count_;
    std::string key_;
};

} // namespace repix

#endif // REPIX_BASE_PIXEL_SCRAMBLE_H
