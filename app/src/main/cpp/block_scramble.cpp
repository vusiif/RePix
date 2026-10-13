#include "block_scramble.h"
#include <cmath>
#include <thread>
#include <vector>
#include <algorithm>

namespace repix {

BlockScramble::BlockScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key,
                             int32_t x_block_count, int32_t y_block_count)
    : BasePixelScramble(pixels, width, height, std::move(key)),
      x_block_count_(x_block_count), y_block_count_(y_block_count) {}

ScrambleResult BlockScramble::process(ProcessType process_type) {
    const std::vector<int32_t> x_array = shuffle(x_block_count_);
    const std::vector<int32_t> y_array = shuffle(y_block_count_);

    int32_t new_width = (width_ % x_block_count_ > 0)
                        ? (width_ + x_block_count_ - width_ % x_block_count_)
                        : width_;
    int32_t new_height = (height_ % y_block_count_ > 0)
                         ? (height_ + y_block_count_ - height_ % y_block_count_)
                         : height_;

    const int32_t block_width = new_width / x_block_count_;
    const int32_t block_height = new_height / y_block_count_;
    std::vector<int32_t> new_pixels(new_width * new_height);

    auto worker = [&](int32_t begin, int32_t end) {
        if (process_type == ProcessType::ENCRYPT) {
            for (int32_t i = begin; i < end; ++i) {
                for (int32_t j = 0; j < new_height; ++j) {
                    int32_t n = j;
                    int32_t m = (x_array[(n / block_height) % x_block_count_] * block_width + i) % new_width;
                    m = x_array[m / block_width] * block_width + (m % block_width);
                    n = (y_array[(m / block_width) % y_block_count_] * block_height + n) % new_height;
                    n = y_array[n / block_height] * block_height + (n % block_height);
                    new_pixels[i + j * new_width] = pixels_[(m % width_) + (n % height_) * width_];
                }
            }
        } else {
            for (int32_t i = begin; i < end; ++i) {
                for (int32_t j = 0; j < new_height; ++j) {
                    int32_t n = j;
                    int32_t m = (x_array[(n / block_height) % x_block_count_] * block_width + i) % new_width;
                    m = x_array[m / block_width] * block_width + (m % block_width);
                    n = (y_array[(m / block_width) % y_block_count_] * block_height + n) % new_height;
                    n = y_array[n / block_height] * block_height + (n % block_height);
                    new_pixels[m + n * new_width] = pixels_[(i % width_) + (j % height_) * width_];
                }
            }
        }
    };

    int32_t core_count = static_cast<int32_t>(std::thread::hardware_concurrency());
    int32_t task_count = std::max(1, std::min(new_width, core_count));
    int32_t step = static_cast<int32_t>(std::ceil(static_cast<double>(new_width) / task_count));

    if (task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(task_count);
        for (int32_t k = 0; k < task_count; ++k) {
            int32_t begin = k * step;
            int32_t end = std::min(begin + step, new_width);
            if (begin < end) {
                workers.emplace_back(worker, begin, end);
            }
        }
        for (auto& w : workers) {
            w.join();
        }
    } else {
        worker(0, new_width);
    }

    return ScrambleResult{std::move(new_pixels), new_width, new_height};
}

} // namespace repix
