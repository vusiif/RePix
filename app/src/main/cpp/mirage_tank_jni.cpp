#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
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

    // 格式检查：必须是 RGBA_8888
    if (coverInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        hiddenInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
        outInfo.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("processColoredMirageTank: All Bitmaps must be RGBA_8888 format.");
        return JNI_FALSE;
    }

    // 尺寸一致性检查：表图、里图和输出画布必须严格同尺寸
    if (coverInfo.width != hiddenInfo.width || coverInfo.height != hiddenInfo.height ||
        coverInfo.width != outInfo.width || coverInfo.height != outInfo.height) {
        LOGE("processColoredMirageTank: Bitmaps dimensions mismatch.");
        return JNI_FALSE;
    }

    void* coverPixels = nullptr;
    void* hiddenPixels = nullptr;
    void* outPixels = nullptr;

    // 锁定物理内存指针
    if (AndroidBitmap_lockPixels(env, j_cover_bitmap, &coverPixels) < 0) {
        LOGE("Failed to lock cover Bitmap pixels.");
        return JNI_FALSE;
    }

    if (AndroidBitmap_lockPixels(env, j_hidden_bitmap, &hiddenPixels) < 0) {
        LOGE("Failed to lock hidden Bitmap pixels.");
        AndroidBitmap_unlockPixels(env, j_cover_bitmap);
        return JNI_FALSE;
    }

    if (AndroidBitmap_lockPixels(env, j_out_bitmap, &outPixels) < 0) {
        LOGE("Failed to lock out Bitmap pixels.");
        AndroidBitmap_unlockPixels(env, j_hidden_bitmap);
        AndroidBitmap_unlockPixels(env, j_cover_bitmap);
        return JNI_FALSE;
    }

    const int totalPixels = static_cast<int>(coverInfo.width * coverInfo.height);

    // 执行纯 C++ 高性能算法
    processColoredMirageTank(
        reinterpret_cast<int32_t*>(coverPixels),
        reinterpret_cast<int32_t*>(hiddenPixels),
        reinterpret_cast<int32_t*>(outPixels),
        totalPixels
    );

    // 及时释放内存锁定
    AndroidBitmap_unlockPixels(env, j_out_bitmap);
    AndroidBitmap_unlockPixels(env, j_hidden_bitmap);
    AndroidBitmap_unlockPixels(env, j_cover_bitmap);

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

    void* coverPixels = nullptr;
    void* hiddenPixels = nullptr;
    void* outPixels = nullptr;

    if (AndroidBitmap_lockPixels(env, j_cover_bitmap, &coverPixels) < 0) return JNI_FALSE;
    if (AndroidBitmap_lockPixels(env, j_hidden_bitmap, &hiddenPixels) < 0) {
        AndroidBitmap_unlockPixels(env, j_cover_bitmap);
        return JNI_FALSE;
    }
    if (AndroidBitmap_lockPixels(env, j_out_bitmap, &outPixels) < 0) {
        AndroidBitmap_unlockPixels(env, j_hidden_bitmap);
        AndroidBitmap_unlockPixels(env, j_cover_bitmap);
        return JNI_FALSE;
    }

    const int totalPixels = static_cast<int>(coverInfo.width * coverInfo.height);
    processGrayMirageTank(
        reinterpret_cast<int32_t*>(coverPixels),
        reinterpret_cast<int32_t*>(hiddenPixels),
        reinterpret_cast<int32_t*>(outPixels),
        totalPixels
    );

    AndroidBitmap_unlockPixels(env, j_out_bitmap);
    AndroidBitmap_unlockPixels(env, j_hidden_bitmap);
    AndroidBitmap_unlockPixels(env, j_cover_bitmap);

    return JNI_TRUE;
}

} // extern "C"
