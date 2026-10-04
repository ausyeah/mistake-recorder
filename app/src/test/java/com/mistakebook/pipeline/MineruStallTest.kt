package com.mistakebook.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MinerU 停滞判定的计时规则。
 *
 * ## 背景：用户报告「返回有内容但是迟迟不进入下一步」
 *
 * v0.0.14 加的诊断信息把范围缩到了这一步：
 *
 * ```
 * MinerU 排队中 · 5s · state=pending · 返回1项
 * ```
 *
 * - `返回1项` → 服务端认到文件了
 * - 没有「文件名未匹配」→ 匹配的是我们自己的文件
 * - `state=pending` → 服务端确实在排队
 *
 * 也就是说**客户端该做的都做了**（请求正确、文件已登记、名字匹配得上），
 * 剩下的是服务端队列。干等到 6 分钟总超时对用户毫无价值——
 * 他要的是「现在到底行不行」。
 */
class MineruStallTest {

    // ------------------------------------------------------------------
    // 一、哪些状态可以判定停滞
    // ------------------------------------------------------------------

    /** 属于「尚未开工」的状态。 */
    @Test
    fun `排队与等文件属于可判定停滞的状态`() {
        assertTrue("pending 应可判定停滞", isStallable("pending"))
        assertTrue("waiting-file 应可判定停滞", isStallable("waiting-file"))
        assertTrue("服务端还没给状态也算没开工", isStallable(""))
    }

    /**
     * `running` 不能判定停滞。
     *
     * 大 PDF / 多页文档解析几分钟是**正常的**，
     * 早判停滞会把本来能成功的任务误杀。
     */
    @Test
    fun `处理中的状态不判定停滞`() {
        assertFalse("running 是正常的长任务", isStallable("running"))
        assertFalse("converting 同理", isStallable("converting"))
    }

    @Test
    fun `终态不判定停滞`() {
        assertFalse("done 不可能停滞", isStallable("done"))
        assertFalse("failed 是终态", isStallable("failed"))
    }

    // ------------------------------------------------------------------
    // 二、阈值本身
    // ------------------------------------------------------------------

    /** 停滞阈值要比「刚提交」长，否则正常排队也会被误判。 */
    @Test
    fun `停滞阈值明显短于总超时`() {
        assertTrue(
            "阈值 ${STALL_SEC}s 太短——正常排队会被误判为卡死",
            STALL_SEC >= 120
        )
        assertTrue(
            "阈值 ${STALL_SEC}s 应明显短于总超时 ${TOTAL_TIMEOUT_MS}ms，否则提前下结论没有意义",
            STALL_SEC * 1000 < TOTAL_TIMEOUT_MS
        )
    }

    /** `waiting-file` 的宽限应当更短——那是「我们这边」的问题，不该等太久。 */
    @Test
    fun `等文件的宽限短于停滞阈值`() {
        assertTrue(
            "waiting-file 宽限 ${WAITING_FILE_GRACE_SEC}s 应短于停滞阈值 ${STALL_SEC}s",
            WAITING_FILE_GRACE_SEC < STALL_SEC
        )
    }

    // ------------------------------------------------------------------
    // 三、计时规则（本轮修的 bug 就在这里）
    // ------------------------------------------------------------------

    /** 阈值内不算停滞。 */
    @Test
    fun `阈值内不判停滞`() {
        val t = MineruStallTracker(stallSec = 150)
        assertFalse(t.observe("pending", 0))
        assertFalse("第 150 秒恰好等于阈值，不算越过", t.observe("pending", 150))
        assertFalse("第 149 秒还没到", t.observe("pending", 149))
    }

    /** 越过阈值即判停滞。 */
    @Test
    fun `越过阈值判停滞`() {
        val t = MineruStallTracker(stallSec = 150)
        t.observe("pending", 0)
        assertTrue("第 151 秒应判停滞", t.observe("pending", 151))
        assertEquals(151L, t.heldFor(151))
    }

    /**
     * **本轮的核心 bug：计时器必须在状态变化时重置。**
     *
     * 场景：`pending` 撑 100 秒 → 服务端推进到 `waiting-file`。
     *
     * 期望：在 `waiting-file` 又待了 30 秒（共 130 秒）时**不**判停滞——
     * 因为这个状态才刚出现 30 秒。正确实现此时 `changedAtSec` 要重置为 100。
     *
     * 错误实现（计时器从不重置）会在 130 秒就报
     * 「停在 waiting-file 已 2 分钟没有进展」，而实际只等了 30 秒。
     */
    @Test
    fun `状态变化后计时器重置`() {
        val t = MineruStallTracker(stallSec = 150)
        t.observe("pending", 0)
        t.observe("pending", 100)
        // 状态变了，此刻开始重新计时
        assertFalse("状态刚变，不该立刻判停滞", t.observe("waiting-file", 100))
        assertFalse("新状态才过 29 秒", t.observe("waiting-file", 129))
        assertEquals("只应计入新状态的时长", 29L, t.heldFor(129))
    }

    /**
     * 换到可判定停滞的状态后，要**重新等满**一个阈值。
     *
     * 这是上一条的下半场：如果只是「不立刻报错」但计时器仍旧不重置，
     * 那 `running` 待了 5 分钟后变回 `pending`，会在下一个轮询瞬间误报停滞。
     */
    @Test
    fun `从非停滞状态转入停滞状态后需重新计时`() {
        val t = MineruStallTracker(stallSec = 150)
        t.observe("pending", 0)
        t.observe("running", 30)
        t.observe("running", 300)   // running 长时间不变很正常，不判停滞
        // 退回 pending：服务端把任务打回去了，这是新的一段等待
        assertFalse("刚退回 pending 不该报停滞", t.observe("pending", 300))
        assertFalse("退回后才过 149 秒", t.observe("pending", 449))
        assertTrue("退回后满 150 秒才判停滞", t.observe("pending", 451))
    }

    /**
     * 没匹配上文件时**不参与判定**。
     *
     * 此时「服务端是什么状态」根本无从谈起。
     * 如果把 `null` 记进去，之后匹配上了会误判成「状态刚变」——
     * 表现为刚找到文件就白白再等一个阈值。
     */
    @Test
    fun `未匹配上文件时不参与判定也不打断计时`() {
        val t = MineruStallTracker(stallSec = 150)
        t.observe("pending", 0)
        assertFalse("null 不判停滞", t.observe(null, 100))
        // 中途 null 不该把计时器清零，也不该让它继续跑
        assertTrue("pending 已经持续够久，null 不该替它续命", t.observe("pending", 200))
    }

    /**
     * 丢失观测后重新匹配上，**状态变化仍然要被识别**。
     *
     * 中间那段时间看不到文件（`matchItem` 返回 null，比如服务端返回里
     * 出现多个同名项导致 `singleOrNull` 拿不准），但状态是**单调**的
     * （`pending → running → done`，不会倒退），所以：
     *
     * - 重新看到**同一个**状态 → 继续计时（见上一条：看不见不等于没在等）
     * - 重新看到**另一个**状态 → 判定为一次变化，重新计时
     */
    @Test
    fun `丢失观测后重新匹配上仍能识别状态变化`() {
        val t = MineruStallTracker(stallSec = 150)
        t.observe("pending", 0)
        t.observe(null, 200)
        t.observe(null, 500)
        assertFalse("刚重新匹配上，不该立刻判停滞", t.observe("waiting-file", 500))
        assertEquals("只应计入新状态的时长", 0L, t.heldFor(500))
        assertFalse("新状态才过 149 秒", t.observe("waiting-file", 649))
        assertTrue("满 150 秒才判停滞", t.observe("waiting-file", 651))
    }

    /** `heldFor` 在还没观测到任何状态时返回 0，而不是负数或 elapsed。 */
    @Test
    fun `未观测过状态时持有时长为零`() {
        val t = MineruStallTracker(stallSec = 150)
        assertEquals(0L, t.heldFor(9999))
        assertEquals("", t.lastSeenState)
    }

    // ------------------------------------------------------------------
    // 四、回归：这正是线上真出现过的写法
    // ------------------------------------------------------------------

    /**
     * **回归用例**——把 v0.0.17 之前那版错误写法原样复刻，与正确实现跑同一串输入。
     *
     * 错误版把 `lastSeenState = item.state` 放在了停滞判定**之前**，
     * 于是 `item.state != lastSeenState` 恒为假，
     * `stateChangedAt` 一次都没被赋值（恒为 0），计时器永不重置。
     *
     * 断言两者在「状态会变化」的场景下必须不同——相同就说明计时器又坏了。
     */
    @Test
    fun `回归-计时器不重置的旧实现与新实现必须给出不同结论`() {
        val script = listOf(
            "pending" to 0L,
            "pending" to 100L,
            "waiting-file" to 100L,
            // 160 秒：旧实现的计时器恒为 0，160 - 0 = 160 > 150 会误报；
            // 正确实现这里是 160 - 100 = 60 秒，远不到阈值。
            "waiting-file" to 160L
        )

        // 旧实现：判定前就把状态记下了
        var oldLast = ""
        var oldChangedAt = 0L
        val oldVerdict = script.map { (state, elapsed) ->
            oldLast = state
            val stalled = isStallable(state) && elapsed - oldChangedAt > STALL_SEC
            if (state != oldLast) { oldChangedAt = elapsed }
            stalled
        }

        // 新实现：计时由对象保证顺序
        val tracker = MineruStallTracker(stallSec = STALL_SEC)
        val newVerdict = script.map { (state, elapsed) -> tracker.observe(state, elapsed) }

        assertTrue(
            "旧实现在 160 秒就报了停滞——这正是 bug 本身，脚本应保持此特征",
            oldVerdict.last()
        )
        assertFalse(
            "waiting-file 才出现 60 秒，不该判停滞；计时器没有重置",
            newVerdict.last()
        )
        assertEquals("pending 阶段两者应一致", oldVerdict[1], newVerdict[1])
    }
}