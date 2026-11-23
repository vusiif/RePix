#pragma once

#include <jni.h>
#include <android/bitmap.h>
#include <cstring>
#include <cstdint>
#include <vector>

namespace repix {

/**
 * RAII Guard to ensure AndroidBitmap_unlockPixels is always invoked
 * even on early exit or error paths.
 */
struct BitmapLockGuard {
    JNIEnv* env;
    jobject bitmap;
    void* pixels;
    bool locked;

    BitmapLockGuard(JNIEnv* e, jobject b)
        : env(e), bitmap(b), pixels(nullptr), locked(false) {
        if (bitmap && AndroidBitmap_lockPixels(env, bitmap, &pixels) >= 0 && pixels) {
            locked = true;
        }
    }

    ~BitmapLockGuard() {
        if (locked && bitmap) {
            AndroidBitmap_unlockPixels(env, bitmap);
            locked = false;
        }
    }

    bool is_locked() const { return locked; }
    void* get_pixels() const { return pixels; }

    BitmapLockGuard(const BitmapLockGuard&) = delete;
    BitmapLockGuard& operator=(const BitmapLockGuard&) = delete;
};

/**
 * Safely unpack row-padded bitmap pixels into a contiguous int32_t buffer
 */
inline void unpack_bitmap_rows(const uint8_t* src, int32_t* dst, int32_t width, int32_t height, int32_t stride) {
    for (int32_t y = 0; y < height; ++y) {
        std::memcpy(dst + y * width, src + y * stride, width * sizeof(int32_t));
    }
}

/**
 * Safely pack contiguous int32_t buffer pixels back into a row-padded bitmap
 */
inline void pack_bitmap_rows(const int32_t* src, uint8_t* dst, int32_t width, int32_t height, int32_t stride) {
    for (int32_t y = 0; y < height; ++y) {
        std::memcpy(dst + y * stride, src + y * width, width * sizeof(int32_t));
    }
}

} // namespace repix
