#include "per_pixel_scramble.h"
#include <cmath>
#include <thread>
#include <vector>
#include <algorithm>

namespace repix {

PerPixelScramble::PerPixelScramble(const int32_t* pixels, int32_t width, int32_t height, std::string key)
    : BasePixelScramble(pixels, width, height, std::move(key)) {}

ScrambleResult PerPixelScramble::process(ProcessType process_type) {
    const std::vector<int32_t> x_array = shuffle(width_);
    const std::vector<int32_t> y_array = shuffle(height_);
    std::vector<int32_t> new_pixels(pixel_count_);

    auto worker = [&](int32_t begin, int32_t end) {
        if (process_type == ProcessType::ENCRYPT) {
            for (int32_t i = begin; i < end; ++i) {
                for (int32_t j = 0; j < height_; ++j) {
                    const int32_t m = x_array[(x_array[j % width_] + i) % width_];
                    const int32_t n = y_array[(y_array[m % height_] + j) % height_];
                    new_pixels[i + j * width_] = pixels_[m + n * width_];
                }
            }
        } else {
            for (int32_t i = begin; i < end; ++i) {
                for (int32_t j = 0; j < height_; ++j) {
                    const int32_t m = x_array[(x_array[j % width_] + i) % width_];
                    const int32_t n = y_array[(y_array[m % height_] + j) % height_];
                    new_pixels[m + n * width_] = pixels_[i + j * width_];
                }
            }
        }
    };

    int32_t core_count = static_cast<int32_t>(std::thread::hardware_concurrency());
    int32_t task_count = std::max(1, std::min(width_, core_count));
    int32_t step = static_cast<int32_t>(std::ceil(static_cast<double>(width_) / task_count));

    if (task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(task_count);
        for (int32_t k = 0; k < task_count; ++k) {
            int32_t begin = k * step;
            int32_t end = std::min(begin + step, width_);
            if (begin < end) {
                workers.emplace_back(worker, begin, end);
            }
        }
        for (auto& w : workers) {
            w.join();
        }
    } else {
        worker(0, width_);
    }

    return ScrambleResult{std::move(new_pixels), width_, height_};
}

} // namespace repix
