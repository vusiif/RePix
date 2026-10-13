#include "tomato_scramble.h"
#include <cmath>
#include <cstdlib>
#include <thread>
#include <algorithm>

namespace repix {

TomatoScramble::TomatoScramble(const int32_t* pixels, int32_t width, int32_t height, double key)
    : pixels_(pixels), width_(width), height_(height), pixel_count_(width * height), pos_(0) {
    // Matches Java: (int) Math.round((Math.sqrt(5) - 1) / 2 * pixelCount * key);
    // Note: Math.round in Java performs (long) Math.floor(x + 0.5d)
    double raw_offset = ((std::sqrt(5.0) - 1.0) / 2.0) * static_cast<double>(pixel_count_) * key;
    offset_ = static_cast<int32_t>(std::floor(raw_offset + 0.5));
}

void TomatoScramble::gilbert2d() {
    positions_.resize(pixel_count_);
    pos_ = 0;
    if (width_ >= height_) {
        generate2d(0, 0, width_, 0, 0, height_);
    } else {
        generate2d(0, 0, 0, height_, width_, 0);
    }
}

void TomatoScramble::generate2d(int32_t x, int32_t y, int32_t ax, int32_t ay, int32_t bx, int32_t by) {
    const int32_t w = std::abs(ax + ay);
    const int32_t h = std::abs(bx + by);
    const int32_t dax = signum(ax);
    const int32_t day = signum(ay);
    const int32_t dbx = signum(bx);
    const int32_t dby = signum(by);

    if (h == 1) {
        for (int32_t i = 0; i < w; ++i) {
            positions_[pos_++] = x + y * width_;
            x += dax;
            y += day;
        }
        return;
    }

    if (w == 1) {
        for (int32_t i = 0; i < h; ++i) {
            positions_[pos_++] = x + y * width_;
            x += dbx;
            y += dby;
        }
        return;
    }

    int32_t ax2 = floor_div_2(ax);
    int32_t ay2 = floor_div_2(ay);
    int32_t bx2 = floor_div_2(bx);
    int32_t by2 = floor_div_2(by);
    const int32_t w2 = std::abs(ax2 + ay2);
    const int32_t h2 = std::abs(bx2 + by2);

    if (2 * w > 3 * h) {
        if ((w2 & 1) == 1 && w > 2) {
            ax2 += dax;
            ay2 += day;
        }
        generate2d(x, y, ax2, ay2, bx, by);
        generate2d(x + ax2, y + ay2, ax - ax2, ay - ay2, bx, by);
    } else {
        if ((h2 & 1) == 1 && h > 2) {
            bx2 += dbx;
            by2 += dby;
        }
        generate2d(x, y, bx2, by2, ax2, ay2);
        generate2d(x + bx2, y + by2, ax, ay, bx - bx2, by - by2);
        generate2d(x + (ax - dax) + (bx2 - dbx), y + (ay - day) + (by2 - dby), -bx2, -by2, -(ax - ax2), -(ay - ay2));
    }
}

ScrambleResult TomatoScramble::process(ProcessType process_type) {
    gilbert2d();
    int32_t loop_position = pixel_count_ - offset_;
    std::vector<int32_t> new_pixels(pixel_count_);

    auto process_range = [&](int32_t begin, int32_t end) {
        if (process_type == ProcessType::ENCRYPT) {
            if (begin >= loop_position) {
                for (int32_t j = begin; j < end; ++j) {
                    new_pixels[positions_[j - loop_position]] = pixels_[positions_[j]];
                }
            } else if (end <= loop_position) {
                for (int32_t j = begin; j < end; ++j) {
                    new_pixels[positions_[j + offset_]] = pixels_[positions_[j]];
                }
            } else {
                for (int32_t j = begin; j < loop_position; ++j) {
                    new_pixels[positions_[j + offset_]] = pixels_[positions_[j]];
                }
                for (int32_t j = loop_position; j < end; ++j) {
                    new_pixels[positions_[j - loop_position]] = pixels_[positions_[j]];
                }
            }
        } else {
            if (begin >= loop_position) {
                for (int32_t j = begin; j < end; ++j) {
                    new_pixels[positions_[j]] = pixels_[positions_[j - loop_position]];
                }
            } else if (end <= loop_position) {
                for (int32_t j = begin; j < end; ++j) {
                    new_pixels[positions_[j]] = pixels_[positions_[j + offset_]];
                }
            } else {
                for (int32_t j = begin; j < loop_position; ++j) {
                    new_pixels[positions_[j]] = pixels_[positions_[j + offset_]];
                }
                for (int32_t j = loop_position; j < end; ++j) {
                    new_pixels[positions_[j]] = pixels_[positions_[j - loop_position]];
                }
            }
        }
    };

    int32_t hw_threads = static_cast<int32_t>(std::thread::hardware_concurrency());
    int32_t task_count = std::max<int32_t>(1, hw_threads);

    if (pixel_count_ > 10000 && task_count > 1) {
        int32_t step = static_cast<int32_t>(std::ceil(static_cast<double>(pixel_count_) / task_count));
        std::vector<std::thread> workers;
        workers.reserve(task_count);

        for (int32_t i = 0; i < task_count; ++i) {
            int32_t begin = step * i;
            int32_t end = std::min(begin + step, pixel_count_);
            if (begin < end) {
                workers.emplace_back(process_range, begin, end);
            }
        }
        for (auto& w : workers) {
            w.join();
        }
    } else {
        process_range(0, pixel_count_);
    }

    // Free memory of positions
    positions_.clear();
    positions_.shrink_to_fit();

    return ScrambleResult{std::move(new_pixels), width_, height_};
}

} // namespace repix
