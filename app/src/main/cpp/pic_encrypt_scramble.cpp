#include "pic_encrypt_scramble.h"
#include <algorithm>
#include <cmath>
#include <thread>
#include <vector>

namespace repix {

BasePicEncryptScramble::BasePicEncryptScramble(const int32_t* pixels, int32_t width, int32_t height, double key)
    : pixels_(pixels), width_(width), height_(height), pixel_count_(width * height), key_(key) {}

std::vector<LogisticItem> BasePicEncryptScramble::generate_logistic(double x1, int32_t n) {
    std::vector<LogisticItem> arr(n);
    double x = x1;
    arr[0] = {x, 0};
    for (int32_t i = 1; i < n; ++i) {
        x = 3.9999999 * x * (1.0 - x);
        arr[i] = {x, i};
    }
    return arr;
}

std::vector<int32_t> BasePicEncryptScramble::get_sorted_positions(std::vector<LogisticItem>& map, int32_t position_size) {
    std::stable_sort(map.begin(), map.end(), [](const LogisticItem& a, const LogisticItem& b) {
        return a.value < b.value;
    });
    std::vector<int32_t> positions(position_size);
    for (int32_t i = 0; i < position_size; ++i) {
        positions[i] = map[i].index;
    }
    return positions;
}

// ---------------- PicEncryptRowScramble ----------------

PicEncryptRowScramble::PicEncryptRowScramble(const int32_t* pixels, int32_t width, int32_t height, double key)
    : BasePicEncryptScramble(pixels, width, height, key) {}

ScrambleResult PicEncryptRowScramble::process(ProcessType process_type) {
    auto logistic_map = generate_logistic(key_, width_);
    auto positions = get_sorted_positions(logistic_map, width_);

    std::vector<int32_t> new_pixels(pixel_count_);
    const int32_t offset = (height_ - 1) * width_;

    auto worker = [&](int32_t begin, int32_t end) {
        if (process_type == ProcessType::ENCRYPT) {
            for (int32_t i = begin; i < end; ++i) {
                const int32_t m = positions[i];
                for (int32_t j = offset; j >= 0; j -= width_) {
                    new_pixels[i + j] = pixels_[m + j];
                }
            }
        } else {
            for (int32_t i = begin; i < end; ++i) {
                const int32_t m = positions[i];
                for (int32_t j = offset; j >= 0; j -= width_) {
                    new_pixels[m + j] = pixels_[i + j];
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

// ---------------- PicEncryptRowColumnScramble ----------------

PicEncryptRowColumnScramble::PicEncryptRowColumnScramble(const int32_t* pixels, int32_t width, int32_t height, double key)
    : BasePicEncryptScramble(pixels, width, height, key) {}

ScrambleResult PicEncryptRowColumnScramble::process(ProcessType process_type) {
    return (process_type == ProcessType::ENCRYPT) ? encrypt() : decrypt();
}

ScrambleResult PicEncryptRowColumnScramble::encrypt() {
    std::vector<int32_t> new_pixels(pixels_, pixels_ + pixel_count_);
    std::vector<int32_t> working_pixels(pixels_, pixels_ + pixel_count_);

    // 1. Precompute row seeds
    std::vector<double> row_seeds(height_);
    double x = key_;
    for (int32_t j = 0; j < height_; ++j) {
        row_seeds[j] = x;
        for (int32_t step = 1; step < width_; ++step) {
            x = 3.9999999 * x * (1.0 - x);
        }
    }

    // 2. Parallel Row Pass: new_pixels -> working_pixels
    int32_t core_count = static_cast<int32_t>(std::thread::hardware_concurrency());
    int32_t row_task_count = std::max(1, std::min(height_, core_count));
    int32_t row_step = static_cast<int32_t>(std::ceil(static_cast<double>(height_) / row_task_count));

    auto row_worker = [&](int32_t r_begin, int32_t r_end) {
        for (int32_t j = r_begin; j < r_end; ++j) {
            auto logistic_map = generate_logistic(row_seeds[j], width_);
            auto positions = get_sorted_positions(logistic_map, width_);
            int32_t offset = j * width_;
            for (int32_t i = 0; i < width_; ++i) {
                working_pixels[i + offset] = new_pixels[positions[i] + offset];
            }
        }
    };

    if (row_task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(row_task_count);
        for (int32_t k = 0; k < row_task_count; ++k) {
            int32_t begin = k * row_step;
            int32_t end = std::min(begin + row_step, height_);
            if (begin < end) workers.emplace_back(row_worker, begin, end);
        }
        for (auto& w : workers) w.join();
    } else {
        row_worker(0, height_);
    }

    // 3. Precompute column seeds
    std::vector<double> col_seeds(width_);
    x = key_;
    for (int32_t i = 0; i < width_; ++i) {
        col_seeds[i] = x;
        for (int32_t step = 1; step < height_; ++step) {
            x = 3.9999999 * x * (1.0 - x);
        }
    }

    // 4. Parallel Column Pass: working_pixels -> new_pixels
    int32_t col_task_count = std::max(1, std::min(width_, core_count));
    int32_t col_step = static_cast<int32_t>(std::ceil(static_cast<double>(width_) / col_task_count));

    auto col_worker = [&](int32_t c_begin, int32_t c_end) {
        for (int32_t i = c_begin; i < c_end; ++i) {
            auto logistic_map = generate_logistic(col_seeds[i], height_);
            auto positions = get_sorted_positions(logistic_map, height_);
            for (int32_t j = 0; j < height_; ++j) {
                new_pixels[i + j * width_] = working_pixels[i + positions[j] * width_];
            }
        }
    };

    if (col_task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(col_task_count);
        for (int32_t k = 0; k < col_task_count; ++k) {
            int32_t begin = k * col_step;
            int32_t end = std::min(begin + col_step, width_);
            if (begin < end) workers.emplace_back(col_worker, begin, end);
        }
        for (auto& w : workers) w.join();
    } else {
        col_worker(0, width_);
    }

    return ScrambleResult{std::move(new_pixels), width_, height_};
}

ScrambleResult PicEncryptRowColumnScramble::decrypt() {
    std::vector<int32_t> new_pixels(pixels_, pixels_ + pixel_count_);
    std::vector<int32_t> working_pixels(pixels_, pixels_ + pixel_count_);

    // 1. Precompute column seeds
    std::vector<double> col_seeds(width_);
    double x = key_;
    for (int32_t i = 0; i < width_; ++i) {
        col_seeds[i] = x;
        for (int32_t step = 1; step < height_; ++step) {
            x = 3.9999999 * x * (1.0 - x);
        }
    }

    // 2. Parallel Column Pass: new_pixels -> working_pixels
    int32_t core_count = static_cast<int32_t>(std::thread::hardware_concurrency());
    int32_t col_task_count = std::max(1, std::min(width_, core_count));
    int32_t col_step = static_cast<int32_t>(std::ceil(static_cast<double>(width_) / col_task_count));

    auto col_worker = [&](int32_t c_begin, int32_t c_end) {
        for (int32_t i = c_begin; i < c_end; ++i) {
            auto logistic_map = generate_logistic(col_seeds[i], height_);
            auto positions = get_sorted_positions(logistic_map, height_);
            for (int32_t j = 0; j < height_; ++j) {
                working_pixels[i + positions[j] * width_] = new_pixels[i + j * width_];
            }
        }
    };

    if (col_task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(col_task_count);
        for (int32_t k = 0; k < col_task_count; ++k) {
            int32_t begin = k * col_step;
            int32_t end = std::min(begin + col_step, width_);
            if (begin < end) workers.emplace_back(col_worker, begin, end);
        }
        for (auto& w : workers) w.join();
    } else {
        col_worker(0, width_);
    }

    // 3. Precompute row seeds
    std::vector<double> row_seeds(height_);
    x = key_;
    for (int32_t j = 0; j < height_; ++j) {
        row_seeds[j] = x;
        for (int32_t step = 1; step < width_; ++step) {
            x = 3.9999999 * x * (1.0 - x);
        }
    }

    // 4. Parallel Row Pass: working_pixels -> new_pixels
    int32_t row_task_count = std::max(1, std::min(height_, core_count));
    int32_t row_step = static_cast<int32_t>(std::ceil(static_cast<double>(height_) / row_task_count));

    auto row_worker = [&](int32_t r_begin, int32_t r_end) {
        for (int32_t j = r_begin; j < r_end; ++j) {
            auto logistic_map = generate_logistic(row_seeds[j], width_);
            auto positions = get_sorted_positions(logistic_map, width_);
            int32_t offset = j * width_;
            for (int32_t i = 0; i < width_; ++i) {
                new_pixels[positions[i] + offset] = working_pixels[i + offset];
            }
        }
    };

    if (row_task_count > 1) {
        std::vector<std::thread> workers;
        workers.reserve(row_task_count);
        for (int32_t k = 0; k < row_task_count; ++k) {
            int32_t begin = k * row_step;
            int32_t end = std::min(begin + row_step, height_);
            if (begin < end) workers.emplace_back(row_worker, begin, end);
        }
        for (auto& w : workers) w.join();
    } else {
        row_worker(0, height_);
    }

    return ScrambleResult{std::move(new_pixels), width_, height_};
}

} // namespace repix
