#ifndef REPIX_PIC_ENCRYPT_SCRAMBLE_H
#define REPIX_PIC_ENCRYPT_SCRAMBLE_H

#include "scramble_common.h"
#include <vector>

namespace repix {

struct LogisticItem {
    double value;
    int32_t index;
};

class BasePicEncryptScramble {
public:
    BasePicEncryptScramble(const int32_t* pixels, int32_t width, int32_t height, double key);
    virtual ~BasePicEncryptScramble() = default;

    static std::vector<LogisticItem> generate_logistic(double x1, int32_t n);
    static std::vector<int32_t> get_sorted_positions(std::vector<LogisticItem>& map, int32_t position_size);

protected:
    const int32_t* pixels_;
    int32_t width_;
    int32_t height_;
    int32_t pixel_count_;
    double key_;
};

class PicEncryptRowScramble : public BasePicEncryptScramble {
public:
    PicEncryptRowScramble(const int32_t* pixels, int32_t width, int32_t height, double key);

    ScrambleResult process(ProcessType process_type);
};

class PicEncryptRowColumnScramble : public BasePicEncryptScramble {
public:
    PicEncryptRowColumnScramble(const int32_t* pixels, int32_t width, int32_t height, double key);

    ScrambleResult process(ProcessType process_type);

private:
    ScrambleResult encrypt();
    ScrambleResult decrypt();
};

} // namespace repix

#endif // REPIX_PIC_ENCRYPT_SCRAMBLE_H
