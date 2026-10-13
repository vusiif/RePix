#ifndef REPIX_TOMATO_SCRAMBLE_H
#define REPIX_TOMATO_SCRAMBLE_H

#include "scramble_common.h"
#include <vector>

namespace repix {

class TomatoScramble {
public:
    TomatoScramble(const int32_t* pixels, int32_t width, int32_t height, double key);
    
    ScrambleResult process(ProcessType process_type);

private:
    void gilbert2d();
    void generate2d(int32_t x, int32_t y, int32_t ax, int32_t ay, int32_t bx, int32_t by);

    const int32_t* pixels_;
    int32_t width_;
    int32_t height_;
    int32_t pixel_count_;
    int32_t offset_;
    std::vector<int32_t> positions_;
    int32_t pos_;
};

} // namespace repix

#endif // REPIX_TOMATO_SCRAMBLE_H
