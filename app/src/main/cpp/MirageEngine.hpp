#pragma once
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * 光棱坦克 (全彩幻影坦克) 核心计算引擎
 */
void processColoredMirageTank(
    int32_t* coverPixels,
    int32_t* hiddenPixels,
    int32_t* outPixels,
    int totalPixels
);

/**
 * 经典黑白幻影坦克计算引擎
 */
void processGrayMirageTank(
    int32_t* coverPixels,
    int32_t* hiddenPixels,
    int32_t* outPixels,
    int totalPixels
);

#ifdef __cplusplus
}
#endif
