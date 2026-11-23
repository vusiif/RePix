#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>
#include <cstdint>

#include "bitmap_jni_helper.h"
#include "scramble_common.h"
#include "tomato_scramble.h"
#include "block_scramble.h"
#include "row_pixel_scramble.h"
#include "per_pixel_scramble.h"
#include "pic_encrypt_scramble.h"
#include "ScrambleEngine.hpp"

#define LOG_TAG "NativeScramble"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

repix::ScrambleResult dispatch_scramble(
    const int32_t* pixels, int32_t width, int32_t height,
    repix::AlgorithmType algo, repix::ProcessType proc,
    const std::string& str_key, double d_key
) {
    switch (algo) {
        case repix::AlgorithmType::TOMATO: {
            repix::TomatoScramble scramble(pixels, width, height, d_key);
            return scramble.process(proc);
        }
        case repix::AlgorithmType::BLOCK: {
            repix::BlockScramble scramble(pixels, width, height, str_key);
            return scramble.process(proc);
        }
        case repix::AlgorithmType::ROW_PIXEL: {
            repix::RowPixelScramble scramble(pixels, width, height, str_key);
            return scramble.process(proc);
        }
        case repix::AlgorithmType::PER_PIXEL: {
            repix::PerPixelScramble scramble(pixels, width, height, str_key);
            return scramble.process(proc);
        }
        case repix::AlgorithmType::PIC_ENCRYPT_ROW: {
            repix::PicEncryptRowScramble scramble(pixels, width, height, d_key);
            return scramble.process(proc);
        }
        case repix::AlgorithmType::PIC_ENCRYPT_ROW_COLUMN: {
            repix::PicEncryptRowColumnScramble scramble(pixels, width, height, d_key);
            return scramble.process(proc);
        }
    }
    return repix::ScrambleResult{};
}

} // namespace

extern "C" {

JNIEXPORT jobject JNICALL
Java_com_example_picencrypt_utils_NativeImageScramble_nativeProcessPixels(
    JNIEnv* env, jclass /* clazz */,
    jintArray j_pixels, jint width, jint height,
    jint algo_code, jint proc_code,
    jstring j_str_key, jdouble d_key
) {
    if (!j_pixels || width <= 0 || height <= 0) return nullptr;

    int64_t total_pixels_64 = static_cast<int64_t>(width) * height;
    if (total_pixels_64 <= 0 || total_pixels_64 > 250000000LL) {
        LOGE("nativeProcessPixels: Dimensions exceed safety bounds: %lld", static_cast<long long>(total_pixels_64));
        return nullptr;
    }

    const char* str_key_chars = j_str_key ? env->GetStringUTFChars(j_str_key, nullptr) : "";
    std::string str_key = str_key_chars ? str_key_chars : "";
    if (j_str_key && str_key_chars) {
        env->ReleaseStringUTFChars(j_str_key, str_key_chars);
    }

    jint* raw_pixels = env->GetIntArrayElements(j_pixels, nullptr);
    if (!raw_pixels) return nullptr;

    auto result = dispatch_scramble(
        reinterpret_cast<const int32_t*>(raw_pixels), width, height,
        static_cast<repix::AlgorithmType>(algo_code),
        static_cast<repix::ProcessType>(proc_code),
        str_key, d_key
    );

    env->ReleaseIntArrayElements(j_pixels, raw_pixels, JNI_ABORT);

    if (result.pixels.empty()) return nullptr;

    jintArray out_array = env->NewIntArray(static_cast<jsize>(result.pixels.size()));
    if (!out_array) return nullptr;

    env->SetIntArrayRegion(out_array, 0, static_cast<jsize>(result.pixels.size()),
                           reinterpret_cast<const jint*>(result.pixels.data()));

    jclass image_cls = env->FindClass("com/example/picencrypt/utils/ImageScramble$Image");
    if (!image_cls) return nullptr;

    jmethodID ctor = env->GetMethodID(image_cls, "<init>", "([III)V");
    if (!ctor) return nullptr;

    return env->NewObject(image_cls, ctor, out_array, result.width, result.height);
}

JNIEXPORT jboolean JNICALL
Java_com_example_picencrypt_utils_NativeImageScramble_nativeProcessBitmap(
    JNIEnv* env, jclass /* clazz */,
    jobject j_bitmap,
    jint algo_code, jint proc_code,
    jstring j_str_key, jdouble d_key
) {
    if (!j_bitmap) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, j_bitmap, &info) < 0) {
        LOGE("AndroidBitmap_getInfo failed");
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Only RGBA_8888 format is supported, received format: %d", info.format);
        return JNI_FALSE;
    }

    int32_t width = static_cast<int32_t>(info.width);
    int32_t height = static_cast<int32_t>(info.height);
    if (width <= 0 || height <= 0) return JNI_FALSE;

    int64_t total_pixels_64 = static_cast<int64_t>(width) * height;
    if (total_pixels_64 <= 0 || total_pixels_64 > 250000000LL) {
        LOGE("Bitmap dimensions exceed safety bounds");
        return JNI_FALSE;
    }

    repix::BitmapLockGuard lock(env, j_bitmap);
    if (!lock.is_locked()) {
        LOGE("AndroidBitmap_lockPixels failed");
        return JNI_FALSE;
    }

    const char* str_key_chars = j_str_key ? env->GetStringUTFChars(j_str_key, nullptr) : "";
    std::string str_key = str_key_chars ? str_key_chars : "";
    if (j_str_key && str_key_chars) {
        env->ReleaseStringUTFChars(j_str_key, str_key_chars);
    }

    const uint32_t expected_stride = static_cast<uint32_t>(width * sizeof(int32_t));
    const bool is_tight = (info.stride == expected_stride);

    std::vector<int32_t> compact_buffer;
    const int32_t* input_pixels = nullptr;

    if (is_tight) {
        input_pixels = static_cast<const int32_t*>(lock.get_pixels());
    } else {
        compact_buffer.resize(width * height);
        repix::unpack_bitmap_rows(static_cast<const uint8_t*>(lock.get_pixels()),
                                 compact_buffer.data(), width, height, info.stride);
        input_pixels = compact_buffer.data();
    }

    auto result = dispatch_scramble(
        input_pixels, width, height,
        static_cast<repix::AlgorithmType>(algo_code),
        static_cast<repix::ProcessType>(proc_code),
        str_key, d_key
    );

    if (result.pixels.empty() || result.width != width || result.height != height) {
        return JNI_FALSE;
    }

    if (is_tight) {
        std::memcpy(lock.get_pixels(), result.pixels.data(), result.pixels.size() * sizeof(int32_t));
    } else {
        repix::pack_bitmap_rows(result.pixels.data(), static_cast<uint8_t*>(lock.get_pixels()),
                               width, height, info.stride);
    }

    return JNI_TRUE;
}

// ---------------- NativeShuffleBridge Stride-Aware API ----------------

JNIEXPORT jboolean JNICALL
Java_com_example_picencrypt_utils_NativeShuffleBridge_scrambleBitmap(
    JNIEnv* env,
    jclass /* clazz */,
    jobject j_bitmap,
    jlong seed,
    jboolean is_decrypt
) {
    if (!j_bitmap) return JNI_FALSE;

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, j_bitmap, &info) < 0) return JNI_FALSE;

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    int32_t width = static_cast<int32_t>(info.width);
    int32_t height = static_cast<int32_t>(info.height);
    if (width <= 0 || height <= 0) return JNI_FALSE;

    int64_t total_pixels_64 = static_cast<int64_t>(width) * height;
    if (total_pixels_64 <= 0 || total_pixels_64 > 250000000LL) return JNI_FALSE;

    repix::BitmapLockGuard lock(env, j_bitmap);
    if (!lock.is_locked()) return JNI_FALSE;

    int32_t total_pixels = static_cast<int32_t>(total_pixels_64);
    const uint32_t expected_stride = static_cast<uint32_t>(width * sizeof(int32_t));
    const bool is_tight = (info.stride == expected_stride);

    if (is_tight) {
        processShuffleInPlace(reinterpret_cast<int32_t*>(lock.get_pixels()), total_pixels, seed, is_decrypt == JNI_TRUE);
    } else {
        std::vector<int32_t> compact_buffer(total_pixels);
        repix::unpack_bitmap_rows(static_cast<const uint8_t*>(lock.get_pixels()),
                                 compact_buffer.data(), width, height, info.stride);
        processShuffleInPlace(compact_buffer.data(), total_pixels, seed, is_decrypt == JNI_TRUE);
        repix::pack_bitmap_rows(compact_buffer.data(), static_cast<uint8_t*>(lock.get_pixels()),
                               width, height, info.stride);
    }

    return JNI_TRUE;
}

} // extern "C"
