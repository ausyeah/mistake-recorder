# 贡献指南

欢迎为本项目贡献代码、文档或反馈。这是一个 Kotlin + Jetpack Compose 的 Android 单机应用，
目标是「拍下错题，它自己认出来、整理好、排版成能打印的 A4」。

## 目录

- [开发环境](#开发环境)
- [快速开始](#快速开始)
- [构建与测试](#构建与测试)
- [代码约定](#代码约定)
- [安全红线（必读）](#安全红线必读)
- [提交 Pull Request](#提交-pull-request)
- [行为准则](#行为准则)

## 开发环境

| 工具 | 版本要求 |
|---|---|
| JDK | 17 |
| Android SDK | `platforms;android-35`、`build-tools;35.0.0`（Android Studio 即可满足） |
| Android Studio | 推荐最新稳定版（非必需，CLI 也能构建） |

## 快速开始

```bash
# 1. 复制模板并填写 SDK 路径（模板无任何密钥）
cp local.properties.template local.properties

# 2. 本地调试构建（可加真实 Key 到 local.properties 以便开发自测）
./gradlew assembleDebug

# 3. 不注入密钥的构建（适合核对发布包）
./gradlew assembleDebug -PprefillKeys=false
```

> 工程路径含非 ASCII 字符时 AGP 会拒绝构建，`gradle.properties` 已带
> `android.overridePathCheck=true` 绕过；Windows 下跑单测建议把仓库放到 ASCII 路径
> （中文路径会让 Gradle test worker 加载测试类失败，属环境问题，CI 为 ASCII 路径不受影响）。

## 构建与测试

```bash
./gradlew assembleDebug         # 构建 debug APK
./gradlew testDebugUnitTest     # 单元测试（450+ 用例）
```

提交代码前请确保 `testDebugUnitTest` 通过。CI（GitHub Actions）会对每个 PR 与 push
自动跑同样的测试与构建，并扫描 APK 确认不含密钥。

## 代码约定

- **界面与提示全中文**；UI 文案集中在 `app/src/main/res/values/strings.xml`；
- **分层**：`data / di / domain / net / pipeline / print / review / ui`，UI 内按屏幕拆分模块；
- **架构**：单 Activity + Navigation-Compose；MVVM（ViewModel + StateFlow + 不可变 UiState）；
- **依赖纪律**：技术栈锁定在 README「技术栈」一节（Room / Retrofit+OkHttp / kotlinx.serialization /
  CameraX / PdfDocument 体系），新增依赖需在讨论中说明理由，不引入 PRD 之外的组件；
- **注释**：非显然的决策必须写注释（「为什么这么做」优先于「做了什么」），中文注释；
- **格式**：Kotlin 官方风格，提交前用 IDE 的 formatter 对齐。

## 安全红线（必读）

- **任何 API Key 一律不得进入仓库**：`local.properties`、`keystore.properties` 已被
  `.gitignore` 拦截，仓库只允许存在带占位符的 `*.template`；
- **公开 Release 不预置密钥**：CI 用 `-PprefillKeys=false` 构建，`BuildConfig` 内为占位符，
  并对产物做密钥扫描；本地 `local.properties` 的 Key 只服务于开发者自测；
- **发现密钥泄露**：立即到服务商后台作废该 Key，并在 Issue 中说明（不要直接在公开渠道贴出 Key）。

## 提交 Pull Request

1. Fork 本仓库，基于 `main` 开分支：`git checkout -b fix/xxx`；
2. 小步提交，提交信息用中文，例如 `feat: 首页支持按学科筛选`、`fix: 打印页全选逻辑`；
3. 推送分支并提交 PR，描述清楚：改了什么、为什么、如何验证（测试结果 / 截图均可）；
4. CI 通过后等待维护者 review；有修改意见请在原分支继续提交。

> 大改动（涉及 Room 表结构、网络 API 契约、依赖变更）请先开 Issue 讨论，避免返工。

## 行为准则

- 友善、就事论事，不人身攻击；
- 尊重不同技术选型的讨论；
- 安全问题优先私下沟通，公开渠道不讨论细节。
