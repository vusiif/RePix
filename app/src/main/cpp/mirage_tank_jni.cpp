#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <vector>
#include <cstring>
#include <cstdint>

#include "bitmap_jni_helper.h"
#include "MirageEngine.hpp"

#define LOG_TAG "MirageTankBridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_picencrypt_utils_MirageTankBridge_processColoredMirageTank(
    JNIEnv* env,
    jclass /* clazz */,
    jobject j_cover_bitmap,
    jobject j_hidden_bitmap,
    jobject j_out_bitmap
) {
    if (!j_cover_bitmap || !j_hidden_bitmap || !j_out_bitmap) {
        LOGE("processColoredMirageTank: Null Bitmap passed to JNI.");
        return JNI_FALSE;
    }

    AndroidBitmapInfo coverInfo, hiddenInfo, outInfo;
    if (AndroidBitmap_getInfo(env, j_cover_bitmap, &coverInfo) < 0 ||
        AndroidBitmap_getInfo(env, j_hidden_bitmap, &hiddenInfo) < 0 ||
        AndroidBitmap_getInfo(env, j_out_bitmap, &outInfo) < 0) {
        LOGE("processColoredMirageTank: Failed to get Bitmap info.");
        return JNI_FALSE;
    }

    // Format check: must be RGBA_8888
    if (coverInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        hiddenInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("processColoredMirageTank: All Bitmaps must be RGBA_8888 format.");
        return JNI_FALSE;
    }

    // Dimension check: must match
    if (coverInfo.width != hiddenInfo.width || coverInfo.height != hiddenInfo.height ||
        coverInfo.width != outInfo.width || coverInfo.height != outInfo.height) {
        LOGE("processColoredMirageTank: Bitmaps dimensions mismatch.");
        return JNI_FALSE;
    }

    int32_t width = static_cast<int32_t>(coverInfo.width);
    int32_t height = static_cast<int32_t>(coverInfo.height);
    if (width <= 0 || height <= 0) return JNI_FALSE;

    int64_t total_pixels_64 = static_cast<int64_t>(width) * height;
    if (total_pixels_64 <= 0 || total_pixels_64 > 250000000LL) {
        LOGE("processColoredMirageTank: Dimensions exceed safety limit.");
        return JNI_FALSE;
    }

    // RAII lock guard guarantees unlocking on all return paths
    repix::BitmapLockGuard coverLock(env, j_cover_bitmap);
    repix::BitmapLockGuard hiddenLock(env, j_hidden_bitmap);
    repix::BitmapLockGuard outLock(env, j_out_bitmap);
    if (!coverLock.is_locked() || !hiddenLock.is_locked() || !outLock.is_locked()) {
        LOGE("processColoredMirageTank: Failed to lock Bitmap pixels.");
        return JNI_FALSE;
    }

    const int32_t totalPixels = static_cast<int32_t>(total_pixels_64);
    const uint32_t expectedStride = static_cast<uint32_t>(width * sizeof(int32_t));
    const bool allTight = (coverInfo.stride == expectedStride &&
                           hiddenInfo.stride == expectedStride &&
                           outInfo.stride == expectedStride);

    if (allTight) {
        processColoredMirageTank(
            static_cast<int32_t*>(coverLock.get_pixels()),
            static_cast<int32_t*>(hiddenLock.get_pixels()),
            static_cast<int32_t*>(outLock.get_pixels()),
            totalPixels
        );
    } else {
        std::vector<int32_t> coverCompact(totalPixels);
        std::vector<int32_t> hiddenCompact(totalPixels);
        std::vector<int32_t> outCompact(totalPixels);

        if (coverInfo.stride == expectedStride) {
            std::memcpy(coverCompact.data(), coverLock.get_pixels(), totalPixels * sizeof(int32_t));
        } else {
            repix::unpack_bitmap_rows(static_cast<const uint8_t*>(coverLock.get_pixels()),
                                     coverCompact.data(), width, height, coverInfo.stride);
        }

        if (hiddenInfo.stride == expectedStride) {
            std::memcpy(hiddenCompact.data(), hiddenLock.get_pixels(), totalPixels * sizeof(int32_t));
        } else {
            repix::unpack_bitmap_rows(static_cast<const uint8_t*>(hiddenLock.get_pixels()),
                                     hiddenCompact.data(), width, height, hiddenInfo.stride);
        }

        processColoredMirageTank(
            coverCompact.data(),
            hiddenCompact.data(),
            outCompact.data(),
            totalPixels
        );

        if (outInfo.stride == expectedStride) {
            std::memcpy(outLock.get_pixels(), outCompact.data(), totalPixels * sizeof(int32_t));
        } else {
            repix::pack_bitmap_rows(outCompact.data(), static_cast<uint8_t*>(outLock.get_pixels()),
                                   width, height, outInfo.stride);
        }
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_picencrypt_utils_MirageTankBridge_processGrayMirageTank(
    JNIEnv* env,
    jclass /* clazz */,
    jobject j_cover_bitmap,
    jobject j_hidden_bitmap,
    jobject j_out_bitmap
) {
    if (!j_cover_bitmap || !j_hidden_bitmap || !j_out_bitmap) return JNI_FALSE;

    AndroidBitmapInfo coverInfo, hiddenInfo, outInfo;
    if (AndroidBitmap_getInfo(env, j_cover_bitmap, &coverInfo) < 0 ||
        AndroidBitmap_getInfo(env, j_hidden_bitmap, &hiddenInfo) < 0 ||
        AndroidBitmap_getInfo(env, j_out_bitmap, &outInfo) < 0) return JNI_FALSE;

    if (coverInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        hiddenInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    if (coverInfo.width != hiddenInfo.width || coverInfo.height != hiddenInfo.height ||
        coverInfo.width != outInfo.width || coverInfo.height != outInfo.height) return JNI_FALSE;

    int32_t width = static_cast<int32_t>(coverInfo.width);
    int32_t height = static_cast<int32_t>(coverInfo.height);
    if (width <= 0 || height <= 0) return JNI_FALSE;

    int64_t total_pixels_64 = static_cast<int64_t>(width) * height;
    if (total_pixels_64 <= 0 || total_pixels_64 > 250000000LL) return JNI_FALSE;

    repix::BitmapLockGuard coverLock(env, j_cover_bitmap);
    repix::BitmapLockGuard hiddenLock(env, j_hidden_bitmap);
    repix::BitmapLockGuard outLock(env, j_out_bitmap);
    if (!coverLock.is_locked() || !hiddenLock.is_locked() || !outLock.is_locked()) return JNI_FALSE;

    const int32_t totalPixels = static_cast<int32_t>(total_pixels_64);
    const uint32_t expectedStride = static_cast<uint32_t>(width * sizeof(int32_t));
    const bool allTight = (coverInfo.stride == expectedStride &&
                           hiddenInfo.stride == expectedStride &&
                           outInfo.stride == expectedStride);

    if (allTight) {
        processGrayMirageTank(
            static_cast<int32_t*>(coverLock.get_pixels()),
            static_cast<int32_t*>(hiddenLock.get_pixels()),
            static_cast<int32_t*>(outLock.get_pixels()),
            totalPixels
        );
    } else {
        std::vector<int32_t> coverCompact(totalPixels);
        std::vector<int32_t> hiddenCompact(totalPixels);
        std::vector<int32_t> outCompact(totalPixels);

        if (coverInfo.stride == expectedStride) {
            std::memcpy(coverCompact.data(), coverLock.get_pixels(), totalPixels * sizeof(int32_t));
        } else {
            repix::unpack_bitmap_rows(static_cast<const uint8_t*>(coverLock.get_pixels()),
                                     coverCompact.data(), width, height, coverInfo.stride);
        }

        if (hiddenInfo.stride == expectedStride) {
            std::memcpy(hiddenCompact.data(), hiddenLock.get_pixels(), totalPixels * sizeof(int32_t));
        } else {
            repix::unpack_bitmap_rows(static_cast<const uint8_t*>(hiddenLock.get_pixels()),
                                     hiddenCompact.data(), width, height, hiddenInfo.stride);
        }

        processGrayMirageTank(
            coverCompact.data(),
            hiddenCompact.data(),
            outCompact.data(),
            totalPixels
        );

        if (outInfo.stride == expectedStride) {
            std::memcpy(outLock.get_pixels(), outCompact.data(), totalPixels * sizeof(int32_t));
        } else {
            repix::pack_bitmap_rows(outCompact.data(), static_cast<uint8_t*>(outLock.get_pixels()),
                                   width, height, outInfo.stride);
        }
    }

    return JNI_TRUE;
}

} // extern "C"
