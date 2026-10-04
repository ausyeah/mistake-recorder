# 任务：构建 Android「错题本」App —— 拍照 → MinerU 识别 → 大模型纠错整理 → 手动编辑 → 错题本 → 打印 PDF

> 本文档是给 AI 编码助手（如 OpenCode / Claude Code）的完整提示词。按章节顺序实现，验收标准见第 9 节。

---

## 1. 目标产物

一个**单机运行的原生 Android App**（不上云、不注册账号），核心闭环：

```
拍照 ──► 裁剪压缩 ──► 上传 MinerU v4 API 解析(公式/表格/手写) ──► 下载结果 zip
   ──► 大模型(SenseNova, OpenAI 兼容) 纠错整理成结构化 JSON
   ──► 编辑页人工修正 ──► 本地 Room 入库 ──► 错题本列表/详情
   ──► 勾选题目 ──► App 内生成 A4 PDF（可隐藏答案、可留白重做）
```

附加能力：学科/知识点/错因分类、筛选搜索统计、艾宾浩斯复习提醒、本地备份导出。

### 基本参数

| 项 | 值 |
|---|---|
| 应用名 | 错题本 |
| 包名 | `com.mistakebook` |
| 语言/UI | Kotlin + Jetpack Compose，全中文界面 |
| minSdk / targetSdk / compileSdk | 26 / 35 / 35 |
| JDK / AGP / Kotlin | 17 / 8.7.x / 2.0.21 |
| 构建 | Gradle KTS，单 `app` module，无多 module |

---

## 2. 技术栈与依赖（写死，禁止自选替代品）

| 用途 | 选型 |
|---|---|
| UI | Jetpack Compose BOM 2024.10.01 + Material3 + Compose Navigation |
| 架构 | 单 Activity + Navigation-Compose；MVVM（ViewModel + StateFlow + 不可变 UiState data class）；Repository 层 |
| 网络 | Retrofit 2.11.0 + OkHttp 4.12.0 + kotlinx.serialization converter |
| JSON | kotlinx.serialization（编译期序列化，禁 org.json 手工拼业务对象） |
| 本地库 | Room 2.6.1（KSP），Schema 导出到 `app/schemas` |
| 轻量配置 | DataStore Preferences（非敏感设置） |
| 机密存储 | `androidx.security:security-crypto:1.1.0-alpha06` EncryptedSharedPreferences（API Key） |
| 相机 | CameraX 1.3.4（Preview + ImageCapture，自绘拍照页，不用系统相机 intent） |
| 图片加载 | Coil 2.7.0（coil-compose） |
| PDF | **零依赖**：`android.graphics.pdf.PdfDocument` + `StaticLayout` |
| 裁剪 | **自研裁剪页**（Compose 手势 + Bitmap.createBitmap），禁止引入 uCrop 等 fork 混乱的库 |
| 压缩/解压 | `java.util.zip.ZipInputStream/ZipOutputStream` |
| 后台时机 | 每日提醒用 WorkManager 2.9.1 PeriodicWork；识别流水线用 applicationScope 协程（见 4.4） |

导入顺序：网络 → data(local/prefs/repo) → pipeline → ui。编译零 warning 才允许进入下一层。

### 目录结构

```
app/src/main/java/com/mistakebook/
├── MistakeBookApp.kt            # Application：通知渠道、container 注入
├── MainActivity.kt             # 单 Activity + NavHost
├── data/
│   ├── local/                   # MistakeBookDatabase, *Dao, entities/, Converters
│   ├── prefs/SettingsStore.kt   # DataStore + SecurePrefs 封装
│   └── repo/                    # QuestionRepository, SubjectRepository, CaptureTaskRepository
├── net/
│   ├── mineru/MineruApi.kt + dto
│   ├── llm/LlmApi.kt + dto
│   └── HttpFactory.kt           # OkHttpClient 单例（超时/重试/日志脱敏）
├── pipeline/
│   ├── RecognitionEngine.kt     # 流水线编排（状态机）
│   ├── MineruClient.kt          # 上传+轮询+解压
│   ├── LlmClient.kt             # chat/completions 调用
│   ├── PromptTemplates.kt       # system/user prompt 常量
│   └── JsonExtractor.kt         # 容错 JSON 提取
├── review/                      # ReviewScheduler, ReviewWorker, Notifier
├── print/PdfExporter.kt         # A4 排版 + 分页
└── ui/                          # home/, capture/, crop/, progress/, edit/, detail/,
                                 # print/, settings/, common/(组件)
```

---

## 3. 密钥与配置管理（核心决策，先实现再干别的）

**要求：所有 Key 都由用户在 App 内填写、可随时修改、立即生效、不卸载重装；开发者自己的 debug 包可预填，release 包必须为空。**

### 3.1 设置页字段

| 字段 | 存储 | 默认值 | 说明 |
|---|---|---|---|
| MinerU API Key | EncryptedSharedPreferences | 空（debug 从 local.properties 预填） | 密码式输入，可切显隐 |
| 大模型 Base URL | DataStore | `https://api.deepseek.com` | 任何 OpenAI 兼容服务可用 |
| 大模型 API Key | EncryptedSharedPreferences | 空（同上） | 密码式输入 |
| 模型名称 | DataStore | `deepseek-chat` | 点「获取模型列表」可从服务端自动选 |
| MinerU 模型版本 | DataStore | `vlm` | 备选 `pipeline` |
| 识别语言 | DataStore | `ch` | |
| 强制 OCR | DataStore | `true` | 拍照场景确保走 OCR |
| 打印默认项 / 提醒开关 / 备份入口 | DataStore | 见 6.7、6.8 | |

- 读取优先级：**settings 中的值 > buildConfig 预填默认值 > 内置常量**。用户在设置页改动任何 Key 后立即写存储，下一次请求即生效，无需重启。
- Key 永不进日志、永不进异常堆栈输出（OkHttp 的 `HttpLoggingInterceptor` 必须设 `redactHeader("Authorization")`）。
- 「测试连接」按钮：MinerU 用 `GET /extract-results/batch/ping`（返回 `code:-60012 task not found` 即 Key 有效，返回 401 即无效）；LLM 用 `GET {base}/models`，HTTP 200 即有效。测试结果显示为「连接成功 / Key 无效 / 网络不通」。

### 3.2 debug / release 隔离（用户原始诉求的原样落实）

`local.properties`（**已在 .gitignore，禁止提交任何仓库**）：

```properties
MINERU_API_KEY=sk-在MinerU开放平台申请后填入
LLM_API_KEY=sk-在服务商后台申请后填入
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-chat
```

> **不要把真实密钥写进任何提交进仓库的文件。**
> 历史上这里曾出现过两个真实的 MinerU / 大模型密钥，它们随仓库一起公开了。
> 密钥一旦进过公开仓库就必须视为已泄露，请到服务商后台重置后再使用。

```kotlin
// app/build.gradle.kts
fun localProperty(key: String, default: String = "") =
    (project.findProperty(key) as String?) ?: default

android {
    buildTypes {
        debug {
            // 只有开发者自己的 debug 包注入默认 Key，设置页预填、用户可改
            buildConfigField("String", "MINERU_API_KEY",
                "\"${localProperty("MINERU_API_KEY")}\"")
            buildConfigField("String", "LLM_API_KEY",
                "\"${localProperty("LLM_API_KEY")}\"")
            buildConfigField("String", "LLM_BASE_URL",
                "\"${localProperty("LLM_BASE_URL", "https://api.deepseek.com")}\"")
            buildConfigField("String", "LLM_MODEL",
                "\"${localProperty("LLM_MODEL", "deepseek-chat")}\"")
        }
        release {
            // 正式打包的 APK 一律为空，使用者必须自行填写，即使 local.properties 有值也不注入
            buildConfigField("String", "MINERU_API_KEY", "\"\"")
            buildConfigField("String", "LLM_API_KEY", "\"\"")
            buildConfigField("String", "LLM_BASE_URL", "\"https://api.deepseek.com\"")
            buildConfigField("String", "LLM_MODEL", "\"deepseek-chat\"")
        }
    }
}
```

验收点：解包 release APK 反编译检查 `BuildConfig.MINERU_API_KEY` 与 `LLM_API_KEY` 均为空字符串。

---

## 4. 外部 API 契约（已实测验证，禁止改动路径与字段名）

### 4.1 MinerU 云 API v4 —— 本地文件上传链路（主链路）

Base：`https://mineru.net/api/v4`；认证：`Authorization: Bearer <key>`。

**Step 1 申请上传地址**

```
POST /file-urls/batch
Content-Type: application/json
Authorization: Bearer <MINERU_API_KEY>

{
  "files": [ { "name": "<文件名>.jpg", "is_ocr": true } ],
  "model_version": "vlm",
  "language": "ch",
  "enable_formula": true,
  "enable_table": true
}
```

成功响应：`{"code":0,"msg":"ok","data":{"batch_id":"...","file_urls":["https://...signed..."]}}`。
`code != 0` 一律抛 `MineruException(msg)`。

**Step 2 PUT 上传文件字节**：对 `file_urls[0]` 发 PUT，body 为文件原始字节流（**不带 Authorization 头**，`Content-Type: application/octet-stream`），HTTP 200 即成功。

**Step 3 轮询结果**

```
GET /extract-results/batch/{batch_id}
Authorization: Bearer <MINERU_API_KEY>
```

响应：`data.extract_result[]` 为数组，按 `name` 匹配本次文件名，条目字段：

| 字段 | 说明 |
|---|---|
| `state` | `running` / `done` / `failed` |
| `full_zip_url` | done 时结果包下载地址 |
| `err_msg` | failed 时错误信息 |
| `extract_progress` | 0-100，有则显示在进度页 |

轮询策略：首次延迟 2s，之后每 3s；连续 5 次 `running` 后按 3→5→8→10s 退避；总超时 **300s**，超时抛异常。

**Step 4 下载并解压 zip**：`full_zip_url` 下载到 `filesDir/mineru/{taskId}/`，解压得到：

- `full.md` —— 主结果（公式为 `$...$` LaTeX、表格为 HTML、图片为 `![](images/xxx.jpg)`）
- `images/*` —— 提取出的题目插图
- `*_content_list.json` —— 中间结构（保存备查，UI 不展示）

解压后把 Markdown 中的 `images/xxx.jpg` 相对路径改写为该任务的**绝对路径**（`file:///.../mineru/{taskId}/images/xxx.jpg`）。

**备用链路（无需实现，仅文档记录）**：`POST /extract/task {"url":"<公网URL>","model_version":"vlm"}` → `data.task_id`；`GET /extract/task/{task_id}` 轮询。本项目只服务本地相册图片，用不到。

**限制**：单文件 ≤200MB；每账号每天约 1000 页高优先级解析额度，超出后降优先级仍可用。

### 4.2 大模型（SenseNova，OpenAI 兼容）

```
POST {llm_base_url}/chat/completions
Authorization: Bearer <LLM_API_KEY>
Content-Type: application/json

{
  "model": "<LLM_MODEL>",
  "messages": [ {"role":"system","content":"<第5节 system prompt>"},
                {"role":"user","content":"<第5节 user prompt>"} ],
  "temperature": 0.2,
  "max_tokens": 4096,
  "response_format": { "type": "json_object" }
}
```

已实测确认的事实（来自 `GET /v1/models`）：

- `deepseek-v4-flash` 存在，上下文 1M，`supported_features` 含 `json_mode`、`tools`、`reasoning`。
- `supported_sampling_parameters` 只有 `temperature` 和 `stop` —— **禁止传 top_p / presence_penalty 等**。
- 输入模态仅 text —— 印证「MinerU 先解析成文本、LLM 只做文本纠错」的链路设计。
- 降级：若 `response_format` 返回 400，自动去掉该字段重试一次；仍失败再把 JSON 要求写进 prompt 末尾重试一次。

超时 120s，失败整体重试 1 次（2s 退避）。输入 Markdown 超过 12000 字符时，保留含 `$` 公式与 `![](` 图片的行优先，其余按原顺序截断（在编辑页提示用户「内容过长已截断」）。

### 4.3 网络层统一错误处理

| 场景 | 行为 |
|---|---|
| 未配置 Key | 弹「去设置填写」引导，不发起请求 |
| 401 / code!=0 鉴权类 | 提示「API Key 无效，请检查设置」 |
| 429 / 503 | 提示「服务端繁忙或额度受限，稍后重试」+ 重试按钮 |
| 超时 / 无网络 | 任务置 FAILED，列表点进去可一键重试 |
| 500+ | 原样展示服务端 msg |

### 4.4 识别流水线运行机制

用** Application 级 CoroutineScope**（`SupervisorJob + Dispatchers.IO`，在 `MistakeBookApp` 创建）驱动，任务状态全部持久化到 Room 的 `CaptureTask` 表，UI 只观察数据库：

```
PENDING ──► UPLOADING ──► PARSING ──► LLM ──► DONE ──► (跳编辑页)
   │            │           │          │
   └────────────┴───────────┴──────────┴──► FAILED(带 errorMessage，可重试)
```

- 每个任务带 `stageText`（如「上传照片 3/3」「MinerU 解析中 42%」「AI 整理中」）供进度页展示。
- 取消 = 协程 job cancel + 删本地临时文件，任务置 FAILED(errorMessage="已取消")。
- App 进程被杀：下次启动时 `RecognitionEngine.resumePending()` 把残留 PENDING/UPLOADING/PARSING/LLM 的任务置 FAILED("任务被中断")，用户可重试（不做断点续传，P2 再考虑 WorkManager 化）。
- 同一时刻允许多个任务顺序执行（内部 Channel/Queue，一次只跑一个，避免打满额度）。

---

## 5. 大模型纠错提示词（PromptTemplates.kt 内置，按此原文实现）

### 5.1 system prompt（完整原文）

```text
你是一个题目纠错整理引擎。用户会给你一份由 MinerU 从学生错题照片中提取的 Markdown 文本，可能包含：OCR 错别字、数学符号错误、页眉页脚、页码、与题目无关的内容；公式以 $...$ 包裹；表格为 HTML；图片以 ![...](路径) 引用。

你的任务：把它整理成一道规范的错题，并严格输出一个 JSON 对象。

硬性规则：
1. 只输出 JSON 本身，不要任何解释、注释、Markdown 代码围栏或思考过程。
2. 不得改变题目原意、不得改变数值与条件；只修正明显的 OCR 错误（形近字、数学符号、上下标丢失）。
3. 删除页眉、页脚、页码、栏目名等非题目内容。
4. 题干(STEM)写完整清晰的题面；选项(OPTIONS)逐条分离，label 用大写字母；没有选项就给空数组。
5. 公式原样保留 $...$ 的 LaTeX 写法，不要转成纯文本。
6. 题干中引用的插图路径全部收集进 image_refs 数组（原样路径字符串）。
7. 答案解析用 ANSWER 与 ANALYSIS 两个字段；ANALYSIS 按步骤组织，步骤间用 \n 分隔。
8. knowledge_points 提取 3~5 个。
9. error_reason_guess 只能从这个枚举里选一个最可能的："概念不清"、"计算失误"、"审题错误"、"思路不会"、"粗心遗漏"、"其他"；无法判断填 "其他"。
10. difficulty 为 1~5 的整数。
11. 任何你没把握的内容（缺字、模糊片段）不要猜，原样放入 uncertain 数组并说明；正文中对应位置也原样保留。

输出 JSON 结构（字段名必须完全一致）：
{
  "subject": "数学|物理|化学|生物|语文|英语|历史|地理|政治|其他",
  "knowledge_points": ["..."],
  "stem": "题干",
  "options": [{"label":"A","text":"选项内容"}],
  "answer": "答案",
  "analysis": "解析",
  "image_refs": ["images/xxx.jpg"],
  "error_reason_guess": "概念不清|计算失误|审题错误|思路不会|粗心遗漏|其他",
  "difficulty": 3,
  "uncertain": ["存疑片段及原因"]
}
```

### 5.2 user prompt 模板

```text
以下是 MinerU 提取的 Markdown，请按系统指令输出整理后的 JSON：

-----MARKDOWN BEGIN-----
{markdown}
-----MARKDOWN END-----
```

### 5.3 JSON 容错解析（JsonExtractor.kt，按顺序尝试，全部失败走降级）

1. 直接 `Json.decodeFromString`；
2. 去掉首尾 ```` ```json ```` / ```` ``` ```` 围栏后解析；
3. 截取**第一个 `{` 到最后一个 `}`** 之间的子串解析；
4. 失败 → 构造降级对象：`stem = 原始 Markdown 全文`，`analysis = ""`，`error_reason_guess = "其他"`，`difficulty = 3`，其余字段默认；调用方在编辑页顶部显示黄色警告条：「AI 整理失败，已保留原始识别文本，请手动整理」。
5. 字段级容错：缺字段用默认值；`options` 元素缺 `label` 时按序号补 A/B/C；`difficulty` 非 1~5 整数时钳位到 3。

---

## 6. 数据模型（Room）

### 6.1 实体

```kotlin
@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 用户可自建，初始预置：数学/语文/英语/物理/化学/生物/历史/地理/政治/其他
    val sortOrder: Int = 0,
    val colorArgb: Int = 0xFF4F7DF3.toInt(),
)

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,        // FK → subjects.id，允许空
    val imagePath: String,       // 裁剪后原图，app files 内相对路径，必填
    val mineruMarkdown: String,  // MinerU 原始 Markdown（图片已改写为绝对路径）
    val stem: String,
    val optionsJson: String,     // List<Option> 序列化
    val answer: String,
    val analysis: String,
    val knowledgePointsJson: String, // List<String>
    val errorReason: ErrorReason,    // 枚举见 6.2
    val difficulty: Int,             // 1..5
    val status: MasteryStatus,       // ACTIVE / REVIEWING / MASTERED
    val note: String = "",
    val reviewStage: Int = 0,        // 当前复习轮次索引
    val nextReviewAt: Long? = null,  // epochDay；ACTIVE 且从未复习 = createdAt 当天
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,     // 软删除
)

@Entity(tableName = "tags", indices = [Index("name", unique = true)])
data class Tag(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "question_tags", primaryKeys = ["questionId","tagId"],
        foreignKeys = [...])
data class QuestionTagCrossRef(val questionId: Long, val tagId: Long)

@Entity(tableName = "review_logs")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val reviewedAt: Long,
    val stageIndex: Int,
    val result: ReviewResult,    // CORRECT / VAGUE / WRONG
)

@Entity(tableName = "capture_tasks")
data class CaptureTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoPath: String,
    val status: TaskStatus,      // PENDING/UPLOADING/PARSING/LLM/DONE/FAILED
    val stageText: String = "",
    val errorMessage: String? = null,
    val batchId: String? = null,
    val markdown: String? = null,        // MinerU 产物（JSON 容错后的回退也用得上）
    val refinedJson: String? = null,     // LLM 结构化结果原文
    val questionId: Long? = null,        // 保存入库后回填
    val createdAt: Long,
    val updatedAt: Long,
)
```

### 6.2 枚举

- `ErrorReason`：`CONCEPT(概念不清)`、`CALCULATION(计算失误)`、`READING(审题错误)`、`NO_IDEA(思路不会)`、`CARELESS(粗心遗漏)`、`OTHER(其他)`
- `MasteryStatus`：`ACTIVE`、`REVIEWING`、`MASTERED`
- `TaskStatus`：`PENDING`、`UPLOADING`、`PARSING`、`LLM`、`DONE`、`FAILED`
- `ReviewResult`：`CORRECT`、`VAGUE`、`WRONG`

### 6.3 关键 DAO 查询

- 列表：`WHERE deletedAt IS NULL ORDER BY updatedAt DESC`，支持按 `status`、`subjectId`、`errorReason` 组合过滤，`stem LIKE '%kw%'` 搜索，LIMIT 分页（每页 30）。
- 统计：`SELECT subjectId, COUNT(*) ... GROUP BY subjectId`；错因分布同理；用于设置/列表页角标。
- 复习队列：`WHERE deletedAt IS NULL AND status != 'MASTERED' AND nextReviewAt <= :today ORDER BY nextReviewAt`。
- 保留原图引用完整性：删除 Question 时把 `files/questions/{id}/` 目录一并删除（软删保留，`deletedAt` 超过 30 天的由「清空回收站」真删）。

---

## 7. 页面与交互规格（逐个实现）

### 7.1 首页 / 错题列表
- TopAppBar「错题本」+ 右侧图标：设置、打印（进入 7.7）。
- 筛选行：状态 Chip 组（全部 / 未掌握 / 复习中 / 已掌握，默认全部）+ 学科下拉 + 搜索框（防抖 300ms）。
- 卡片：左侧缩略图 72dp 圆角；右侧：学科色点+学科名、题干前两行（最多约 60 字）、底部行：错因 chip + 难度星 + 相对日期；「待复习」题目显示小圆点提示。
- 左滑删除 → Snackbar「已删除 · 撤销」。
- 空状态：插画文案「还没有错题，拍下第一道吧」+ 按钮直达拍照。
- 右下角 ExtendedFAB「拍错题」。

### 7.2 拍照页（CameraX）
- 全屏 PreviewView，叠加 3×3 构图网格线；顶部：闪光灯（auto/on/off）、前后摄切换；底部：相册入口（左）、快门（中）、取消（右）。
- 拍照后不直接存文件，先把 `ImageProxy` 转 Bitmap（按 EXIF 旋转校正），进入裁剪页。
- 权限：`CAMERA` 运行时申请，拒绝则说明用途并引导系统设置。

### 7.3 裁剪页（自研）
- 全屏显示 Bitmap，叠加可拖拽裁剪框：四角 8 个圆形手柄（触摸热区 ≥32dp），框内区域高亮、框外压暗 60%。
- 工具栏：旋转 90°、重置、取消、确认。
- 最小裁剪尺寸 100×100dp 当量；裁剪结果 `Bitmap.createBitmap` 裁出，长边缩到 ≤1600px，JPEG q85 存 `filesDir/crops/{uuid}.jpg`。
- 确认后：创建 `CaptureTask(PENDING)` 提交 `RecognitionEngine`，跳转进度页。

### 7.4 识别进度页
- 中央大图标 + `stageText`（驱动自 CaptureTask 表）+  indeterminate/百分比进度。
- 按钮：取消（确认对话框）。阶段推进：上传照片 → MinerU 解析中（有 `extract_progress` 时显示百分比）→ AI 整理中 → 完成自动跳编辑页。
- 失败态：错误信息 + 「重试」 + 「跳过 AI，直接用原始文本」。（后者把 markdown 灌进编辑页的降级对象。）
- 每次识别完成，把 zip 里被引用的图片复制到 `filesDir/questions/{taskId}/images/`，Markdown 图片路径同步改写为绝对路径（保证后续删除 zip 缓存后图仍在）。

### 7.5 编辑页（人工修正，重点页面）
- 顶部警告条（仅降级/截断时出现）。
- 区块：原图（点击全屏查看，支持双指缩放）→ 学科（下拉，默认 AI 建议）→ 题干（多行输入，2 行起可增长）→ 选项（逐行，可增删，label 自动排 A/B/C…）→ 答案（多行）→ 解析（多行）→ 错因（单选 Chip 组，默认 AI 建议）→ 难度（5 星点选，默认 AI 建议）→ 知识点（Chip 列表 + 输入框回车添加）→ 备注。
- 「查看原始识别文本」：BottomSheet 展示 `mineruMarkdown` 纯文本，可一键复制。
- 底部保存按钮；`stem` 为空时禁用并提示「题干不能为空」。
- 保存：写入 `questions` 表 + `tags`（新知识点自动建 Tag）+ `question_tags`，任务置 DONE 并回填 `questionId`；跳详情页。

### 7.6 详情页
- 完整展示 + 编辑（复用编辑页，带初始值）/ 删除 / 「标记已掌握」/「加入打印清单」。
- 复习区：「本次复习结果」三按钮（✔ 会 / ~ 模糊 / ✘ 不会）→ 写 `review_logs` 并按 6.4 规则推进 `reviewStage` 与 `nextReviewAt`；下方时间线展示历史复习记录。
- 达到最高轮次后 `status = MASTERED`，界面显示「已掌握 🎉」。

### 7.7 打印页（勾选 → 生成 PDF）
- 顶部筛选：学科下拉 + 状态 + 关键词；列表每题左侧 checkbox，支持全选/反选；底部固定「已选 N 题 · 生成 PDF」。
- 「打印选项」展开面板：
  - 是否包含题目原图（默认开）
  - 是否显示答案与解析（默认**关**，即只打题目）
  - 是否使用留白重做模式（默认开；留白高度 60 / 100 / 150pt 三档，默认 100pt）
  - 一题一卡，A4 纵向
- 生成：`Dispatchers.IO` + 前台通知显示进度；完成后弹出结果对话框：文件路径、**分享**（`ACTION_SEND`，`application/pdf`，FileProvider）、再次打开。
- 输出路径：`Download/错题本/错题本_YYYYMMDD_HHmmss.pdf`（Android Q+ 用 MediaStore.Downloads 插入；以下用直接文件路径 + `MEDIA_SCANNER` 通知相册）。

### 7.8 设置页
- Key 配置组（6.1）、打印默认组、复习提醒开关 + 每日提醒时间（默认 20:00）。
- 数据管理：「导出备份」（zip：`mistake_book.db` + `files/` 全量，存 Download/错题本/备份_日期.zip）、「从备份恢复」（选择 zip，校验含 db 后覆盖恢复，恢复前弹确认对话框）、「清空回收站」。

### 7.9 复习提醒
- WorkManager `PeriodicWorkRequest(1, TimeUnit.DAYS)` + App 启动时立即 enqueue 一次唯一工作；到点查复习队列，`nextReviewAt <= 今天` 的数量 N>0 时发通知：「今天有 N 道错题待复习」→ 点击进列表（带待复习筛选）。
- 通知渠道：`review`（default importance）；运行时申请 `POST_NOTIFICATIONS`（API 33+），拒绝则静默跳过。

---

## 8. 打印 PDF 引擎规格（PdfExporter.kt）

### 8.1 版面常量

- A4  portrait：595 × 842 pt；页边距 42pt；可用宽度 511pt。
- 页眉：左「错题本」右「YYYY-MM-DD」，10.5pt 灰色；页脚居中「第 n / m 页」，9.5pt 灰色。
- 卡片间距 18pt。卡片顶部色条 3pt 用学科色；标题行：`12. 数学 · 计算失误 · ★★★☆☆`（11pt）。

### 8.2 卡片块顺序（受打印选项控制）

1. 题干 `StaticLayout`，14pt，行距 1.25×，两端对齐（`Layout.Alignment.ALIGN_NORMAL` + 手动断行即可，不追求完美 justify）。
2. 原图（选项开）：宽度 `min(可用宽×45%, 原图等比)` 绘制，居中；图下注 8.5pt 灰字「原题照片」。
3. 选项逐行 12.5pt，行距 1.3×。
4. 答案区（选项开）：12pt，「答案：xxx」；解析紧随，「解析：」12pt 灰色，#555555。
5. 留白区（重做模式开）：按档位高度绘制，浅灰分隔线（#DDDDDD，每 28pt 一条）+ 左侧 9pt 灰字「重做区」。
6. 卡片之间画 0.5pt 浅灰分隔线。

### 8.3 分页算法

```
可用高度 = 842 - 42*2 - 页眉26 - 页脚20
逐块测量：文本块用 StaticLayout 测量高度；图片块按缩放后高度；留白块固定高度；
累加 + 下一个块高度 > 可用高度 ⇒ 结束当前页，开新页；
单个文本块自身超过一页时，按 StaticLayout 行切片（getLineTop/getLineBottom）拆到多页。
```

- 中文渲染：`Typeface.create("sans-serif", NORMAL)`（系统中文字形可用），Paint 开 `isAntiAlias`、`isSubpixelText`；英文数字同字体即可，不引入自定义 ttf。
- 公式：LaTeX 源码（`$...$`）**原样文本输出**（已知限制，P2 升级为 WebView+KaTeX 渲染位图后贴入 PDF，本期不做）。
- 生成过程中捕获单题异常：跳过该题并在完成对话框列出「跳过的题目」。

---

## 9. 验收标准（逐条自测后再交付）

1. `debug` 包安装后，设置页 MinerU Key 与大模型 Key 均已预填（源自 local.properties），不填任何东西即可走完一次闭环。
2. 拍一张含公式的手写或印刷错题 → 裁剪保存 → 进度页阶段正确推进（上传→解析→AI 整理）→ 编辑页出现结构化结果（学科/题干/选项/答案/解析/错因均非空或有合理降级）→ 手动改两处 → 保存 → 列表与详情可见。
3. 清空两个 Key 后，任何入口点「拍照」只弹「请先到设置填写 API Key」引导，不 crash、不发起请求。
4. 勾选 5 道题生成 PDF：A4、分页无文字截断、含原图、页眉页脚页码齐全。
5. 打印选项关闭「显示答案解析」后，PDF 中不含答案与解析文本（重做模式默认开启）。
6. 详情页做一次「✔ 会」，`reviewStage=1`、`nextReviewAt=次日`、列表出现「待复习」红点；做一次「✘ 不会」`reviewStage` 归 0。
7. 通知权限授予后，手动触发每日worker（WorkManager 一次性测试）能发出「待复习」通知，点击跳转列表。
8. 搜索结果、筛选 Chip、学科下拉可组合过滤且结果数与标题角标一致。
9. 导出备份 zip 可在卸载重装后恢复出全部题目与图片。
10. release 包解包检查 `BuildConfig` 中两个 Key 字段为空字符串；全部 Logcat 输出无完整 Key（允许前 4 位）。
11. 无网络时拍照进入进度页，任务最终 FAILED 并展示可读中文错误（不是 stacktrace）。

---

## 10. 迭代优先级

- **P0（本提示词范围，必须全做）**：3、4、5、6、7.1~7.7、8、9。
- **P1**：7.8 备份恢复、7.9 提醒、8.3 的 KaTeX 公式渲染、统计图表。
- **P2**：OCR 结果与原图左右对照校对模式、多题一次拍摄批量导入、云同步、（可选）WorkManager 化识别任务支持杀进程续跑。

---

## 11. 工程细节清单

- `AndroidManifest`：`INTERNET`、`POST_NOTIFICATIONS`（33+ 运行时）、`CAMERA`；`FileProvider`（`authorities = ${applicationId}.fileprovider`，paths 覆盖 `files/` 与 `Download` 分享场景）；`requestLegacyExternalStorage` 不需要。
- `strings.xml` 集中全部中文文案；Compose 内不允许散落硬编码中文（`contentDescription` 除外可内联）。
- 所有时间用 `System.currentTimeMillis()` epoch 毫秒 + `LocalDate.toEpochDay()`；展示层格式化，存储层不存字符串时间。
- 图片一律存 app-specific files；卸载即清理；备份/恢复走 zip。
- Room 数据库 version=1，`exportSchema = true`，迁移策略暂不需要但留 `fallbackToDestructiveMigration` 注释说明。
- ProGuard/R8 release 开启，保留 kotlinx.serialization 生成类的 keep 规则。
- 每个 ViewModel 暴露单一 `StateFlow<XxxUiState>`；事件（Snackbar/导航）用 `Channel`/`SharedFlow`，不在 state 里塞一次性事件。
- 网络返回一律 `Result<T>` 风格封装（自有 `ApiResult` sealed class），禁止把原始异常抛到 UI 层。

---

## 12. 安全须知（写给所有者的提醒，不属于功能需求）

1. 本中文档中出现过的两个 API Key 曾以明文形式出现，**交付完成后请尽快到 MinerU 与 SenseNova 后台各自轮换（重置）一次**。
2. `local.properties` 已加入 `.gitignore`；任何公开仓库禁止提交 real key；正式分发的 APK 不内置 key（第 3.2 节已从构建层面保证）。
3. 用户自填 Key 仅存于本机 EncryptedSharedPreferences，App 无任何自建服务器、无遥测、无第三方统计 SDK。
