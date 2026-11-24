# Changelog

All notable changes to the RePix project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.5.0] - 2026-11-24

### 🚀 架构革新与解耦 (Architecture & Clean Code)
- **ViewModel 领域单责拆分**：将原有 1,043 行单体 `MainViewModel` 解耦为 4 个独立特性 ViewModel（`ScrambleViewModel`、`StegoViewModel`、`MirageViewModel`、`SafeViewModel`），主协调器代码行数精简 72.6%（降至 286 行）。
- **统一配置仓储层**：引入 `PreferencesRepository` 接口与实现，彻底解耦 UI Context 反向传参，提供干净可测的依赖反转。
- **表现层组件化重塑**：重构 `EncryptScreen.kt` 巨石组件，抽离 `HistogramChart`、`EmptyStateCard`、`ExifPrivacyDialog` 与 `BatchProcessActionButtons`，消除了加解密按键事件中 90% 的重复分发代码。

### 🔒 安全与算法加固 (Security & Robustness)
- **全平台 LCG 洗牌兜底引擎**：
  - 新增纯 Kotlin 实现的 `LcgShuffleScramble` 引擎，基于 48-bit 线性同余发生器及模 $2^{48}$ 乘法逆元（$a^{-1} = \text{0xdfe05bcb1365}$）实现 $O(1)$ 原位解密。
  - 彻底修复兼容模式 (`isCompatMode = true`) 与 JNI 缺失环境下 `LCG_SHUFFLE` 算法静默返回未加密原图的缺陷。
- **SafeBox 跨平台容器升级 (RPX V3)**：
  - 采用 AES-256-GCM 流式加密与 AAD 头部防篡改认证。
  - 密文容器内嵌原始文件名元数据，自动防目录穿越与路径注入。
  - 口令内存生命周期加固：支持 `clearPassword()` 即时零化擦除，操作成功后自动清空界面与内存，杜绝堆内存驻留。
- **数学与数据边界防护**：
  - TomatoScramble 负 Key 模反演与除零保护。
  - PicEncrypt 混沌映射极值截断，彻底防御 NaN/Infinity 扩散。
  - PNG Chunk 无符号长度溢出防御与图种 V2 尾部 Trailer $O(1)$ 定位校验。

### ⚡ 性能与内存优化 (Performance & Memory)
- **幻影坦克内存防抖**：在 `createMirageBitmap` 中加入安全的 `finally` 阶段资源显式回收机制，解决连续滑动调节亮度/对比度时的中间 Bitmap 内存堆积与 OOM 风险。
- **事务式 MediaStore 写入**：Android 10+ 保存图片采用 `IS_PENDING` 事务机制，杜绝半成品图片被相册扫描捕获。

### 🧪 自动化测试 (Testing)
- 全量单元测试套件扩展至 28 组，涵盖往返可逆性、架构状态管理、安全擦除、边界测试，全部 100% 通过（0 失败, 0 跳过）。
