#include "MirageEngine.hpp"
#include <algorithm>
#include <cmath>

inline uint8_t clampToUint8(float v) {
    if (v <= 0.0f) return 0;
    if (v >= 255.0f) return 255;
    return static_cast<uint8_t>(std::round(v));
}

void processColoredMirageTank(
    int32_t* coverPixels,
    int32_t* hiddenPixels,
    int32_t* outPixels,
    int totalPixels
) {
    const float scale_i = 0.35f;  // 里图色阶压缩比例
    const float scale_c = 0.80f;  // 表图色阶保留比例
    const float weight_i = 0.70f; // 里图颜色还原权重

    for (int i = 0; i < totalPixels; ++i) {
        const uint32_t cVal = static_cast<uint32_t>(coverPixels[i]);
        const uint32_t hVal = static_cast<uint32_t>(hiddenPixels[i]);

        const float cr = static_cast<float>(cVal & 0xFF);
        const float cg = static_cast<float>((cVal >> 8) & 0xFF);
        const float cb = static_cast<float>((cVal >> 16) & 0xFF);

        const float hr = static_cast<float>(hVal & 0xFF);
        const float hg = static_cast<float>((hVal >> 8) & 0xFF);
        const float hb = static_cast<float>(hVal >> 16 & 0xFF);

        // 表里图色阶压缩
        const float hr_scaled = hr * scale_i;
        const float hg_scaled = hg * scale_i;
        const float hb_scaled = hb * scale_i;

        const float cr_scaled = 255.0f - (255.0f - cr) * scale_c;
        const float cg_scaled = 255.0f - (255.0f - cg) * scale_c;
        const float cb_scaled = 255.0f - (255.0f - cb) * scale_c;

        // 计算三通道独立 Alpha 并取最大值
        const float alpha_r = (255.0f + hr_scaled - cr_scaled) / 255.0f;
        const float alpha_g = (255.0f + hg_scaled - cg_scaled) / 255.0f;
        const float alpha_b = (255.0f + hb_scaled - cb_scaled) / 255.0f;

        float maxAlpha = std::max({alpha_r, alpha_g, alpha_b});
        maxAlpha = std::clamp(maxAlpha, 0.001f, 1.0f);

        // 双向色彩插值
        const float ai = 255.0f * maxAlpha;
        const float out_r = ((hr_scaled - ai + 255.0f - cr_scaled) * weight_i + ai - 255.0f + cr_scaled) / maxAlpha;
        const float out_g = ((hg_scaled - ai + 255.0f - cg_scaled) * weight_i + ai - 255.0f + cg_scaled) / maxAlpha;
        const float out_b = ((hb_scaled - ai + 255.0f - cb_scaled) * weight_i + ai - 255.0f + cb_scaled) / maxAlpha;

        const uint32_t finalR = clampToUint8(out_r);
        const uint32_t finalG = clampToUint8(out_g);
        const uint32_t finalB = clampToUint8(out_b);
        const uint32_t finalA = clampToUint8(ai);

        outPixels[i] = static_cast<int32_t>(finalR | (finalG << 8) | (finalB << 16) | (finalA << 24));
    }
}

void processGrayMirageTank(
    int32_t* coverPixels,
    int32_t* hiddenPixels,
    int32_t* outPixels,
    int totalPixels
) {
    const float scale_i = 0.35f;
    const float scale_c = 0.80f;

    for (int i = 0; i < totalPixels; ++i) {
        const uint32_t cVal = static_cast<uint32_t>(coverPixels[i]);
        const uint32_t hVal = static_cast<uint32_t>(hiddenPixels[i]);

        // 计算表里图灰度 (BT.601: 0.299 R + 0.587 G + 0.114 B)
        const float cGray = (cVal & 0xFF) * 0.299f + ((cVal >> 8) & 0xFF) * 0.587f + ((cVal >> 16) & 0xFF) * 0.114f;
        const float hGray = (hVal & 0xFF) * 0.299f + ((hVal >> 8) & 0xFF) * 0.587f + ((hVal >> 16) & 0xFF) * 0.114f;

        const float li = hGray * scale_i;
        const float lc = 255.0f - (255.0f - cGray) * scale_c;

        const float a = std::clamp(255.0f + li - lc, 1.0f, 255.0f);
        const float l = std::clamp(li * 255.0f / a, 0.0f, 255.0f);

        const uint32_t grayVal = static_cast<uint32_t>(std::round(l));
        const uint32_t alphaVal = static_cast<uint32_t>(std::round(a));

        outPixels[i] = static_cast<int32_t>(grayVal | (grayVal << 8) | (grayVal << 16) | (alphaVal << 24));
    }
}
