<div align="center">

# 错题本 · mistake-recorder（Android）

### 拍下错题，它自己认出来、整理好、排版成能打印的 A4。

单机 Android App：**拍照 / 相册 / PDF → MinerU 解析（公式、表格、手写）→ 大模型纠错整理 → 人工编辑 → 本地错题本 → 勾选打印 A4**，还能针对每道题直接和 AI 对话讲解。

不上云、不注册账号，API Key 只存本机加密存储。

</div>

---

<!-- ============================================================ -->
<!-- 待补充：功能演示录屏（占位）                                -->
<!-- 录制清单见 docs/RECORDING_CHECKLIST.md                      -->
<!-- 建议格式：1 分钟总览视频，嵌入下方                             -->
<!-- ============================================================ -->

## 演示

> 📹 **待补充：功能演示录屏**（录制清单见 [docs/RECORDING_CHECKLIST.md](docs/RECORDING_CHECKLIST.md)）

> 🖼 **待补充：核心界面截图**（首页 / 拍照裁剪 / 识别进度 / 编辑页 / 列表 / 详情 / 打印导出 / AI 对话 / 设置）

---

## 功能

| | |
|---|---|
| **四种录入入口** | 拍照（3×3 网格取景 + 自研裁剪页）/ 相册选图 / PDF（文本 PDF 直接抽文字，图片 PDF 逐页走 MinerU，支持页码范围 `1-5,8`）/ 手动录入（完全不依赖 API） |
| **AI 识别整理** | MinerU v4 解析公式 / 表格 / 手写体 → 大模型纠错整理成结构化错题 → **一律先进人工编辑页确认**，不直接入库 |
| **噪声排除** | 裁剪页拖框选题目、框外压暗；**涂鸦遮蔽**把红笔批注涂白（不透明纯白，避免被 OCR 当成淡字） |
| **一题多识别** | 一次识别可能整理出多道题，编辑页顶部左右翻页，带原始识别文本对照 |
| **间隔复习** | 掌握状态（未掌握 / 已掌握）、难度星级、每日待复习提醒、详情页一键切换 |
| **错题本分类** | 学科与错题本正交：一道题一个学科、可归入多个错题本；支持新建 / 重命名 / 归档 |
| **打印导出** | 勾选题目 → 生成**单个自包含 HTML**（图片 base64 内嵌、公式原生 MathML，不联网也能看）；浏览器打开后可另存为 PDF。可选：含原图 / 显示答案解析 / 留白重做 |
| **AI 对话** | 每题一个固定会话 + 自由会话；流式回复（端点不支持时自动降级为一次性返回）；可附图片 / PDF / TXT / Markdown / DOCX（文档本地抽文本，不上传原文件） |
| **备份恢复** | zip = 数据库 + 全部文件，覆盖式恢复 |

## 技术栈

| 项 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose + Material3，全中文界面 |
| 架构 | 单 Activity + Navigation-Compose；MVVM（ViewModel + StateFlow + 不可变 UiState） |
| 网络 | Retrofit + OkHttp + kotlinx.serialization |
| 本地存储 | Room（KSP，Schema 导出）+ DataStore（非敏感设置）+ EncryptedSharedPreferences（API Key） |
| 相机 / 图像 | CameraX + Coil |
| 公式渲染 | WebView + KaTeX |
| PDF | 自写零依赖文本抽取器 + `PdfRenderer` 栅格化（导出 HTML，PDF 由浏览器打印） |
| 版本 | `minSdk 26`（Android 8.0）/ `targetSdk 35` / JDK 17 / AGP 8.7 |

规模：约 2.1 万行 Kotlin，`data / di / domain / net / pipeline / print / review / ui` 分层，44+ 个单元测试文件（450+ 用例）。

---

## 安装

### 方式一：下载 APK（推荐）

1. 前往 [Releases](https://github.com/ausyeah/mistake-recorder/releases) 下载最新的 `mistakebook-<版本>-android.apk`；
2. 传输到手机（或用浏览器直接下载）后点击安装，需允许「安装未知来源应用」；
3. 安装包**不预置任何 API Key**，首次启动后请按下方教程在设置页自行填写。

> 当前 Release 为调试签名安装包，升级不同版本时如遇签名不一致需先卸载旧版。

### 方式二：本地构建

```bash
# 需要 JDK 17 与 Android SDK（platform 35 / build-tools 35）
cp local.properties.template local.properties   # 填 sdk.dir
./gradlew assembleDebug -PprefillKeys=false    # 产出 app/build/outputs/apk/debug/app-debug.apk
```

---

## 使用教程

### 首次配置：两个密钥

App 依赖两个**由使用者自行申请**的云 API，全部在「设置」页配置，只存本机：

1. **MinerU API Key**：用于错题图片识别。在 MinerU 开放平台申请后填入。
2. **大模型接入**：用于纠错整理与 AI 对话。填 **Base URL + API Key + 模型名**（可保存多套配置并切换，点「获取模型列表」可自动带出可选模型）。任何 OpenAI 兼容服务均可，例如 `https://api.deepseek.com` + `deepseek-chat`。

> **没有 Key 也能用**：首页「手动录入题目」完全离线，不发起任何请求。

### 日常使用流程

1. **录入**：首页右下角「拍错题」→ 拍照 / 相册 / PDF / 手动录入四选一；
2. **识别**：进度页展示 上传 → MinerU 解析 → AI 整理 三个阶段，失败可重试，或「跳过 AI 直接用原始文本」；
3. **编辑**：核对整理结果，翻页查看多题，插图可剔除，确认后入库；
4. **复习**：列表页筛选 / 搜索，左滑删除可撤销；详情页标记已掌握、点星改难度；每日待复习提醒；
5. **打印**：右上角打印机图标 → 勾选题目 → 生成 HTML（浏览器打开 → 打印 → 另存为 PDF，可调页边距与缩放）；
6. **问 AI**：详情页右上角进入该题对话（首问预填「请讲解这道题…」不自动发送），或首页右上角进入历史会话。

### API 规格

#### 一、MinerU 识别 API（v4）

- **Base URL**：`https://mineru.net/api/v4/`
- **认证**：请求头 `Authorization: Bearer <API Key>`（上传与下载走预签名 URL，**不带**认证头）
- **必需参数**：`files`（文件名列表）、`model_version`、`language`；可选 `enable_formula`、`enable_table`
- **响应信封**：`{ "code": 0, "msg": "", "data": {...} }`，`code == 0` 为成功，否则 `msg` 为错误说明

四步流程：

1. **申请上传地址**
   `POST file-urls/batch`
   ```json
   { "files": [ { "name": "错题.jpg", "is_ocr": true } ],
     "model_version": "v20240914", "language": "ch",
     "enable_formula": true, "enable_table": true }
   ```
   → `data`: `{ "batch_id": "...", "file_urls": ["https://.../presigned-url"] }`

2. **上传文件**：`PUT <file_url>`，body 为文件原始字节（无需认证头）。

3. **轮询解析结果**
   `GET extract-results/batch/{batch_id}`
   → `data.extract_result[]`：
   ```json
   { "file_name": "错题.jpg",
     "state": "done",
     "extract_progress": { "extracted_pages": 1, "total_pages": 2 },
     "full_zip_url": "https://.../result.zip",
     "err_msg": null }
   ```
   `state` 为解析状态，`extract_progress` 是**对象**（页数，非 0-100 标量）。`batch_id` 传 `"ping"` 可用于测试 Key 是否有效。

4. **下载结果**：`GET <full_zip_url>` 获取 zip（内含 Markdown 等解析产物）。

- **错误处理**：信封 `code != 0` 或单项 `err_msg` 非空表示失败；轮询失败可重试，或跳过 AI 直接手动编辑原始文本。

#### 二、大模型 API（OpenAI 兼容）

- **Base URL**：设置页配置，代码按 `{base}/chat/completions` 与 `{base}/models` 拼接（自动去尾部 `/`）
- **认证**：`Authorization: Bearer <API Key>`
- **必需参数**：`model`、`messages`（`role` + `content`，支持 `text` / `image_url` 多模态片段）；可选 `temperature`、`max_tokens`、`stream`
- **对话参数**：`temperature = 0.6`，`max_tokens = 16384`（撞上限时界面挂「已截断」标记，不会静默残缺）

**流式请求（默认）**

```
POST {base}/chat/completions
Accept: text/event-stream
{
  "model": "deepseek-chat",
  "messages": [ { "role": "user", "content": "请讲解这道题" } ],
  "temperature": 0.6,
  "max_tokens": 16384,
  "stream": true
}
```

SSE 逐帧响应（`data: {...}`）：

```json
{ "choices": [ { "delta": { "content": "增量正文", "reasoning_content": "增量思考" }, "finish_reason": null } ] }
```

- `delta.content`：正文增量；`delta.reasoning_content`：思考过程增量（推理模型）
- **结束信号**（三选一）：`finish_reason` 非空（`stop` 正常 / `length` 截断）、哨兵 `data: [DONE]`、连接关闭

**非流式响应（`stream: false`，或服务端忽略 `stream` 时自动降级）**

```json
{ "choices": [ { "message": { "content": "全文", "reasoning_content": "思考" },
                 "finish_reason": "stop" } ],
  "usage": { "prompt_tokens": 123, "completion_tokens": 456 } }
```

**错误处理约定**

| 场景 | 行为 |
|---|---|
| HTTP 401 / 403（鉴权失败） | 直接报错，不重试 |
| HTTP 429（限流）/ 5xx（服务端错误） | 直接报错，不重试（重发只会白烧配额） |
| 网络不通 / 超时 / 响应不是合法 SSE | 若**尚未吐出任何内容**，自动降级为非流式请求重试一次 |
| 已吐过内容后失败 | 不重发（避免同一段话出现两遍） |
| 用户主动停止 | 不重发，保留已收到内容 |
| `finish_reason = length` | 判定为被 `max_tokens` 截断，界面提示换更大模型或分次问 |

**附件处理**：图片压缩后转 data URL 内联进消息；PDF / TXT / Markdown / DOCX 在**本地抽取文本**拼进提问，原文件不上传。

---

## 项目结构

```
app/src/main/java/com/mistakebook/
├── data/        # Room 实体与 DAO、DataStore、备份、聊天上下文
├── di/          # 手写依赖容器
├── domain/      # 掌握状态等业务模型
├── net/         # MinerU / 大模型（SSE 流式）客户端
├── pipeline/    # 识别流水线、Latex 修复、文本审计
├── print/       # 导出（HTML 排版、字号算法）
├── review/      # 复习调度
└── ui/          # 14 个屏幕级子模块（Compose）
```

技术规格详见 [PRD.md](PRD.md)。

---

## 数据与安全

- **不预置任何 API Key**：`buildConfigField` 注入的字符串会明文落在 DEX，所以公开安装包一律注入占位符，由使用者自行填写；
- **Key 只存本机**：`EncryptedSharedPreferences`，不进日志、不随应用上传；
- 本仓库无密钥文件：`local.properties` / `keystore.properties` 等已被 `.gitignore` 拦截，仓库内只有占位模板。

## 本地编译与测试

```bash
./gradlew assembleDebug                  # 构建（本地 local.properties 有 Key 时注入，仅限本机）
./gradlew testDebugUnitTest              # 运行单元测试（450+ 用例）
```

## 许可证

[MIT](LICENSE)。随包分发的第三方组件（KaTeX 及其字体，MIT）见 [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md)。

## 免责声明

本项目仅提供客户端能力，MinerU 与大模型 API 均为第三方服务，账号注册、配额与费用由使用者自行承担；请在使用前阅读并遵守相应服务条款。
