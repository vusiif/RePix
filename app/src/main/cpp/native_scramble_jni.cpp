#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <cstring>

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
        LOGE("Only RGBA_8888 format is supported");
        return JNI_FALSE;
    }

    void* pixels_ptr = nullptr;
    if (AndroidBitmap_lockPixels(env, j_bitmap, &pixels_ptr) < 0 || !pixels_ptr) {
        LOGE("AndroidBitmap_lockPixels failed");
        return JNI_FALSE;
    }

    const char* str_key_chars = j_str_key ? env->GetStringUTFChars(j_str_key, nullptr) : "";
    std::string str_key = str_key_chars ? str_key_chars : "";
    if (j_str_key && str_key_chars) {
        env->ReleaseStringUTFChars(j_str_key, str_key_chars);
    }

    int32_t width = static_cast<int32_t>(info.width);
    int32_t height = static_cast<int32_t>(info.height);

    auto result = dispatch_scramble(
        static_cast<const int32_t*>(pixels_ptr), width, height,
        static_cast<repix::AlgorithmType>(algo_code),
        static_cast<repix::ProcessType>(proc_code),
        str_key, d_key
    );

    bool ok = false;
    if (!result.pixels.empty() && result.width == width && result.height == height) {
        std::memcpy(pixels_ptr, result.pixels.data(), result.pixels.size() * sizeof(int32_t));
        ok = true;
    }

    AndroidBitmap_unlockPixels(env, j_bitmap);
    return ok ? JNI_TRUE : JNI_FALSE;
}

// ---------------- NativeShuffleBridge Zero-Copy API ----------------

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
    void* raw_pixels = nullptr;

    // 1. Get Bitmap structure info
    if (AndroidBitmap_getInfo(env, j_bitmap, &info) < 0) return JNI_FALSE;

    // Must be RGBA_8888
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return JNI_FALSE;

    // 2. Lock underlying pixel memory (True zero-copy)
    if (AndroidBitmap_lockPixels(env, j_bitmap, &raw_pixels) < 0 || !raw_pixels) return JNI_FALSE;

    int32_t total_pixels = static_cast<int32_t>(info.width * info.height);

    // 3. Perform in-place LCG Fisher-Yates shuffle / unshuffle
    processShuffleInPlace(reinterpret_cast<int32_t*>(raw_pixels), total_pixels, seed, is_decrypt == JNI_TRUE);

    // 4. Unlock memory, changes are immediately reflected
    AndroidBitmap_unlockPixels(env, j_bitmap);
    return JNI_TRUE;
}

} // extern "C"
