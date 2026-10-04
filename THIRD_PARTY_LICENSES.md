# 第三方组件与许可证

本项目自身代码见仓库根目录的 [`LICENSE`](LICENSE)（MIT）。
以下为**随仓库分发的第三方组件**，版权归原作者所有。

---

## KaTeX

- **用途**：在 `WebView` 中渲染 LaTeX 公式
- **来源**：<https://katex.org/> ／ <https://github.com/KaTeX/KaTeX>
- **许可证**：MIT，`Copyright (c) 2013-2020 Khan Academy and other contributors`
- **上游许可证原文**：<https://github.com/KaTeX/KaTeX/blob/main/LICENSE>
- **分发内容**（`app/src/main/assets/katex/`）：
  - `katex.min.js`、`katex.min.css`
  - `fonts/*.ttf`（20 个字体文件）

> 字体文件由 KaTeX 仓库的**根 LICENSE**（MIT）统一覆盖，
> KaTeX 的 `fonts/` 目录下并没有单独的许可证文件。
> 若日后升级 KaTeX，请以上游当时的 LICENSE 为准。

### 一处已知不一致

`katex.min.css` 里每个 `@font-face` 按 `woff2` → `woff` → `truetype` 顺序声明，
但本仓库**只打包了 `.ttf`**，前两种格式缺失。WebView 会依次请求并失败后回退到
`.ttf`，功能正常，但首次渲染每个字体会多两次失败请求。

打包 woff2 可以减小体积并省掉这两次回退。如需修复，见本文末「后续可做」。

---

## Android Jetpack 库

`androidx.*` / `com.google.android.*` 系列均为 **Apache License 2.0**，
版权归 The Android Open Source Project 所有。以 Gradle 依赖形式引入，
**不随本仓库分发**。

---

## 字体二次分发要点

MIT 允许自由使用、修改、再分发与再授权（含商业用途），
唯一要求是**在所有副本或实质性部分中保留版权声明与许可声明**——
这也是本文件与根 `LICENSE` 存在的原因。

| 做法 | 是否允许 |
|---|---|
| 把 `.ttf` 放进 assets 随 App 分发 | ✅ 允许 |
| 用这些字形渲染截图/文本并展示给用户 | ✅ 允许 |
| 修改字体文件 | ✅ 允许 |
| 单独把 `.ttf` 文件拿出来卖 | ✅ MIT 不禁止（但字体文件本身的商标另论） |
| 声称字体是自己设计的 | ❌ 违反版权声明 |

本项目只做「随应用分发并在渲染时使用」，未修改字体文件。

---

## 后续可做

- [ ] 补齐 woff2/woff 字体，消除首次渲染的失败请求
- [ ] 在 `assets/katex/` 下放一份 KaTeX 的 LICENSE 副本，就地声明而不必依赖本文件
