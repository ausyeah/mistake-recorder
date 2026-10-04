# 错题本 App — 开工指令（给 AI 编码代理）

你是本仓库的开发代理。目标：按根目录 `PRD.md` 实现 Android 原生「错题本」App 的 P0 范围（完整闭环）。

## 第一步（必做）

1. 通读 `PRD.md` —— 它包含：技术栈与依赖、目录结构、密钥管理方案、MinerU v4 / SenseNova API 契约（已实测验证）、大模型纠错提示词、Room 数据模型、逐屏交互规格、A4 打印 PDF 排版算法、验收标准。
2. 所有实现细节**以 PRD 为准**；PRD 未覆盖的，自行做最合理决策并记录到 `docs/DECISIONS.md`，不要停下来问（除非影响 API 契约或 Room 表结构）。

## 硬性规则（违反即返工）

1. **密钥安全**：`local.properties` 已在 `.gitignore`，任何 API Key 不得进入 git 历史、不得硬编码进 release 构建；CI 从 GitHub Secrets 读取 Key。debug 构建从 `local.properties` 读预填值（见 PRD 3.2）。
2. 技术栈严格按 PRD 第 2 节：Kotlin + Compose + Room + Retrofit/OkHttp + kotlinx.serialization + CameraX + 系统 PdfDocument。**不得擅自替换**（如用 org.json 替代序列化、用第三方 PDF 库替代 PdfDocument、用 uCrop 替代自研裁剪）。
3. **每完成一个可运行增量**，执行：
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\sync.ps1 -Message "feat: 完成设置页与密钥管理"
   ```
   需要给手机测试出新包时执行：
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\release.ps1 -Tag "v0.1.0"
   ```
   （脚本会自动 add/commit/push，tag 推送后 GitHub Actions 自动编译并把 APK 挂到 Release。）
4. 界面与提示**全中文**；UI 文案集中在 `strings.xml`；提交信息用中文。
5. 每次提交前本地能编译则必须编译通过（`.\gradlew assembleDebug`）。本机无 Android SDK，若无法本地编译，至少保证代码按 PRD 的类型/接口签名一致，由 CI 校验。
6. 严禁把 warning 当 error 之外的自作聪明：不引入 PRD 之外的依赖；加依赖必须先查 PRD 是否已锁定。

## 实施顺序（按 PRD 第 10 节：P0 闭环优先）

1. Gradle 骨架（settings/build.gradle.kts、gradle wrapper、AndroidManifest、string 资源）→ 推一次让 CI 变绿
2. 密钥管理（SettingsStore + EncryptedSharedPreferences + buildConfig 注入 + 设置页）
3. MinerU 客户端（apply-upload-url → PUT → 轮询 → zip 解压，PRD 4.1）
4. LLM 客户端 + PromptTemplates + JsonExtractor（PRD 4.2、第 5 节）
5. 识别流水线 RecognitionEngine + CaptureTask 状态机（PRD 4.4）
6. 拍照页（CameraX）+ 自研裁剪页
7. 编辑页（人工修正）
8. 列表页 + 详情页
9. PDF 导出（PRD 第 8 节排版与分页算法）+ 打印选择页
10. 逐条自测 PRD 第 9 节验收标准 1–6 与 11

## 完成定义（DoD）

- PRD 第 9 节验收标准第 1–6、11 条全部通过（7–10 条 P1 可后补）
- `.github/workflows/android.yml` 在最新 commit 上为绿色
- README 的「使用方式」与实际一致
- `docs/DECISIONS.md` 记录所有自创决策
