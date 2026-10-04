package com.mistakebook.math

/**
 * 公式在版面里「该占多大、该留多高行距」的**唯一判据**。
 *
 * ## 为什么要抽出来
 *
 * 屏幕（[com.mistakebook.ui.common.RichText]）和打印
 * （[com.mistakebook.print.PdfExporter]）原本各写了一套缩放逻辑，
 * 于是同一个公式在 App 里和 PDF 里大小不同、溢出行为也不同——
 * 用户看到的现象是「屏幕上好好的，打印出来就飞出去了」。
 *
 * 两边共用本文件后，这种漂移在结构上就不可能发生：
 * 想改排版规则只改一处。
 *
 * ## 刻意不依赖 Android
 *
 * 全部是纯计算，因此能直接在 JVM 单元测试里跑。
 * 排版是最容易「看着差不多、实际错很多」的地方，
 * 规则必须能被断言，而不是靠肉眼看截图。
 */
object MathLayout {

    /** 缩放结果。`scale` 是位图原始像素到最终绘制像素的倍数。 */
    data class Fitted(val width: Float, val height: Float, val scale: Float)

    /**
     * 把一张公式位图放到版面上。
     *
     * ## 规则（按顺序，不可交换）
     *
     * 1. **等比缩放到目标字号**：`targetFontPx / srcFontPx`。
     *    位图是 KaTeX 按 `srcFontPx` 渲染的，按这个比值缩放，
     *    公式里的字母才会和正文一样大。
     * 2. **再按可用宽度收缩**：放不下就整体等比缩小。
     *    宁可变小，也**绝不裁剪**——裁剪会把公式切掉一半，
     *    那是「公式缺笔画」这种最让人抓狂的故障。
     * 3. **再按可用高度收缩**：同上，等比，绝不裁剪。
     *
     * ## 三个曾经踩过的坑，这里都堵住了
     *
     * - **长宽比被破坏**：早先代码先按宽缩、再单独对高做 `coerceIn`，
     *   高度被夹住而宽度不变，公式被**拉扁**。现在两步都用同一个 `k`，
     *   等比性天然成立。
     * - **宽度下限把公式顶出屏幕**：`coerceAtLeast(最小宽)` 会在
     *   极长公式上把宽度顶回超过可用宽度，于是横向溢出。
     *   现在没有下限——宁可小到看不清，也不越界。
     * - **静默裁剪**：`MAX_BITMAP_EDGE` 那类上限一旦生效，
     *   公式右边会被切掉且没有任何提示。现在上限只用于**缩小**。
     *
     * @param srcFontPx 位图渲染时实际使用的字号（KaTeX 那边报回来的 `fs`）
     * @param maxWidthPx 版面可用宽度上限；<= 0 表示不限
     * @param maxHeightPx 版面可用高度上限；<= 0 表示不限
     */
    fun fit(
        bitmapW: Int,
        bitmapH: Int,
        srcFontPx: Float,
        targetFontPx: Float,
        maxWidthPx: Float = 0f,
        maxHeightPx: Float = 0f
    ): Fitted {
        if (bitmapW <= 0 || bitmapH <= 0) return Fitted(0f, 0f, 0f)
        val src = srcFontPx.takeIf { it > 0f } ?: targetFontPx
        var scale = if (targetFontPx > 0f) targetFontPx / src else 1f
        if (!scale.isFinite() || scale <= 0f) scale = 1f

        val w = bitmapW * scale
        val h = bitmapH * scale
        if (maxWidthPx > 0f && w > maxWidthPx && w > 0f) scale *= maxWidthPx / w
        if (maxHeightPx > 0f) {
            val hNow = bitmapH * scale
            if (hNow > maxHeightPx && hNow > 0f) scale *= maxHeightPx / hNow
        }
        return Fitted(
            width = (bitmapW * scale).coerceAtLeast(0f),
            height = (bitmapH * scale).coerceAtLeast(0f),
            scale = scale
        )
    }

    /**
     * 公式在目标字号下的**自然高度**，即还没被任何上限约束时有多高。
     *
     * 用来决定行高：行高应该**迁就最高的那个公式**，
     * 而不是把公式压扁去迁就一个固定行高。
     */
    fun naturalHeight(bitmapH: Int, srcFontPx: Float, targetFontPx: Float): Float {
        if (bitmapH <= 0) return 0f
        val src = srcFontPx.takeIf { it > 0f } ?: targetFontPx
        val scale = if (targetFontPx > 0f) targetFontPx / src else 1f
        val h = bitmapH * scale
        return if (h.isFinite() && h > 0f) h else 0f
    }

    /**
     * 行高 = 基础行高与「最高公式 + 上下留白」中的较大者。
     *
     * ## 为什么不直接用固定倍数
     *
     * 早先的写法是「行高固定 1.9 倍字号，公式高过就把它缩小」。
     * 结果是同一段里 `x\to0` 这种矮公式保持原样，
     * 而带分式的公式被压小——**字号忽大忽小，用户一眼就看出来不对**；
     * 同时公式底部的分母伸到行框外，**压到下一行文字上**。
     *
     * 正确做法是反过来：**行高去迁就公式**，而不是公式去迁就行高。
     */
    fun lineHeightFor(
        baseLineHeightPx: Float,
        tallestMathPx: Float,
        paddingPx: Float
    ): Float {
        val needed = if (tallestMathPx > 0f) tallestMathPx + paddingPx * 2f else 0f
        return maxOf(baseLineHeightPx, needed)
    }

    /**
     * 公式里**字母**相对正文的大小。
     *
     * 取 1.0 是有意的：公式里的 x、a、ln 应当和汉字一样大，
     * 这是用户明确要求的观感标准（"正常字母和汉字的大小一致是最佳的"）。
     * 早期用 0.92 偏小，读起来像小一号的注释。
     */
    const val MATH_LETTER_RATIO = 1.0f

    /**
     * 行内公式的**总高度上限**（相对正文字号）。
     *
     * 「字母同大」不等于「整体同高」：`\frac{a}{b}` 在字母同大的前提下
     * 天生就有约 2.5 倍正文高，不压就会顶穿行框、压到上下行文字上。
     * 所以整体按这个上限等比缩小——字母跟着一起变小，
     * 这正是用户要的"分数适当小一点"。
     *
     * 取 2.3 的依据：`x\to0` 这类单层公式约 1.3 倍，**不受影响**；
     * 只有 `\frac`、嵌套指数这些真正高的才会被压。
     * 上限太紧（1.5）会让所有带分式的公式都变小、段内字号不齐；
     * 上限太松（3.5）则一行占掉半屏。
     */
    const val INLINE_MATH_MAX_HEIGHT_RATIO = 2.3f

    /** 独立成行的公式可以更高一点：它自己占一整行，不必迁就行内文字。 */
    const val DISPLAY_MATH_MAX_HEIGHT_RATIO = 3.2f

    /**
     * 行高（相对正文字号）。
     *
     * 必须 **大于** [INLINE_MATH_MAX_HEIGHT_RATIO]，否则被压到上限的公式
     * 仍然会探出行框、压到下一行文字上（用户报"行间互相重合"）。
     * 多出来的 0.15 倍就是公式上下留白。
     */
    const val INLINE_LINE_BOX_RATIO = 2.45f

    /**
     * 公式上下各留多少空隙（相对该公式自身高度）。
     *
     * 没有留白时分式会紧贴上下行文字；留白过大又会显得松散。
     * 0.12 左右是「不粘连」与「不松垮」之间的经验值。
     */
    const val MATH_LINE_PADDING_RATIO = 0.12f

    /** 行高至少要是字号的多少倍，否则中英文混排本身就挤。 */
    const val MIN_LINE_HEIGHT_RATIO = 1.45f

    /**
     * 排版规则一处定义，屏幕（[com.mistakebook.ui.common.RichText]）与
     * 打印（[com.mistakebook.print.PdfExporter]）都从这里取。
     *
     * 这几个常量如果留在各自文件里，迟早会各自被改成一个值，
     * 然后用户就会看到「屏幕上好好的，打印出来就飞出去了」。
     */
    fun inlineMathMaxHeightPx(textSizePx: Float): Float =
        textSizePx * INLINE_MATH_MAX_HEIGHT_RATIO

    fun displayMathMaxHeightPx(textSizePx: Float): Float =
        textSizePx * DISPLAY_MATH_MAX_HEIGHT_RATIO

    fun lineBoxPx(textSizePx: Float): Float = textSizePx * INLINE_LINE_BOX_RATIO

    /** 公式字母的目标字号。 */
    fun letterTargetPx(textSizePx: Float): Float = textSizePx * MATH_LETTER_RATIO
}
