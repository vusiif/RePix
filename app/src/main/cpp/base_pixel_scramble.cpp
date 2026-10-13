#include "base_pixel_scramble.h"
#include "md5.h"
#include <utility>

namespace repix {

BasePixelScramble::BasePixelScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key)
    : pixels_(pixels), width_(width), height_(height), pixel_count_(width * height), key_(std::move(key)) {}

std::vector<int32_t> BasePixelScramble::shuffle(int32_t length) const {
    std::vector<int32_t> arr(length);
    for (int32_t i = 0; i < length; ++i) {
        arr[i] = i;
    }

    std::string str_buffer;
    str_buffer.reserve(key_.size() + 16);

    for (int32_t i = length - 1; i > 0; --i) {
        str_buffer.clear();
        str_buffer.append(key_);
        str_buffer.append(std::to_string(i));

        auto digest = MD5::compute(str_buffer);
        uint32_t hex_val = MD5::parse_first_7_hex(digest);
        int32_t rand_idx = static_cast<int32_t>(hex_val % static_cast<uint32_t>(i + 1));

        std::swap(arr[rand_idx], arr[i]);
    }

    return arr;
}

} // namespace repix
