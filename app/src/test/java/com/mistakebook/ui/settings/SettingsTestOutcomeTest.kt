package com.mistakebook.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置页「测试连接」的结果归属。
 *
 * ## 用户报告
 *
 * > 测试成功或者失败返回结果依旧没有显示在测试按钮后面
 *
 * ## 三个叠在一起的 bug
 *
 * 1. **MinerU 的结果根本没渲染。**
 *    ViewModel 里 `mineruTestResult` / `mineruTestOk` 算好并进了 state，
 *    但设置页的 `TestButton(...)` 调用**根本没传这两个参数**——
 *    而 `TestButton` 的 `result` 参数当时带默认值 `= null`，
 *    于是漏传也能编译通过：没有报错、没有警告，只是按钮右边永远空空如也。
 *
 * 2. **大模型的结果跨配置串台。**
 *    `llmTestResult` 是**单个全局字段**，而 `ProfileRow` 在
 *    `snapshot.llmProfiles.forEach` 里给每个配置都传了它。
 *    配了第二个模型之后：测第一个的按钮，两行同时显示同一份结果；
 *    点第二个的按钮，两行同时转圈。用户完全对不上结果属于哪个按钮。
 *
 * 3. **改了配置还挂着旧结论。**
 *    `clearMineruTestResult` / `clearLlmTestResult` 写好了却**一个调用点都没有**，
 *    改完 Key 再看，屏幕上仍是上一次那句「连接成功」。
 *
 * 这里测的是状态层的归属规则。UI 接线那部分靠**去掉默认值**来保证：
 * 漏传参数会直接编译不过（见 `TestButton` / `ProfileRow` 的签名）。
 */
class SettingsTestOutcomeTest {

    private fun stateWith(vararg profiles: String) = SettingsUiState(
        llmTestResults = profiles.associateWith { TestOutcome("$it 的结果", ok = true) }
    )

    // ------------------------------------------------------------------
    // 一、MinerU 与大模型的结果必须彼此独立
    // ------------------------------------------------------------------

    /**
     * 早先两个测试共用一对 `testResult` / `testOk`：
     * 点 MinerU 的按钮，结果却渲染在几屏之外的大模型分组下方，
     * 而且后测的那个会把先测的结果冲掉。
     */
    @Test
    fun `MinerU 与大模型的结果互不影响`() {
        val s = SettingsUiState(
            mineruTestResult = "MinerU 连接成功",
            mineruTestOk = true,
            llmTestResults = mapOf("p1" to TestOutcome("大模型连接成功", ok = true))
        )
        assertEquals("MinerU 连接成功", s.mineruTestResult)
        assertTrue(s.mineruTestOk)
        assertEquals("大模型连接成功", s.llmOutcome("p1")?.message)
    }

    @Test
    fun `默认时两边都没结果`() {
        val s = SettingsUiState()
        assertNull(s.mineruTestResult)
        assertFalse(s.mineruTestOk)
        assertTrue(s.llmTestResults.isEmpty())
        assertFalse(s.testingLlm)
        assertFalse(s.testingMineru)
    }

    // ------------------------------------------------------------------
    // 二、结果必须按 profile id 归属（拒绝串台）
    // ------------------------------------------------------------------

    @Test
    fun `结果只返回自己那个配置的`() {
        val s = stateWith("p1", "p2")
        assertEquals("p1 的结果", s.llmOutcome("p1")?.message)
        assertEquals("p2 的结果", s.llmOutcome("p2")?.message)
        // 没测过的配置不能拿别人的结果
        assertNull("p3 从未测过，不应返回任何结果", s.llmOutcome("p3"))
    }

    /**
     * 这就是用户报的那个现象的根因：
     * 配了两个模型之后，两行会显示同一份结果。
     *
     * 老实现的写法是：两行都去拿同一个全局字段。
     */
    @Test
    fun `配了两个模型后不会串台`() {
        val s = stateWith("p1", "p2")
        assertEquals(2, s.llmTestResults.size)
        assertTrue(s.llmOutcome("p1") != s.llmOutcome("p2"))
        // 删掉一个配置的结果，不影响另一个
        val afterDelete = s.copy(llmTestResults = s.llmTestResults - "p1")
        assertNull(afterDelete.llmOutcome("p1"))
        assertNotNull("删掉 p1 不应影响 p2", afterDelete.llmOutcome("p2"))
    }

    // ------------------------------------------------------------------
    // 三、只有正在测试的那一行转圈
    // ------------------------------------------------------------------

    @Test
    fun `只有正在测试的那一行转圈`() {
        val s = SettingsUiState(llmTestingProfileId = "p2")
        assertTrue(s.testingLlm)
        assertTrue("p2 正在测", s.isTestingLlm("p2"))
        assertFalse("p1 不应转圈，否则用户会误以为两个都在测", s.isTestingLlm("p1"))
    }

    @Test
    fun `没有测试时哪个都不转圈`() {
        val s = SettingsUiState(llmTestResults = mapOf("p1" to TestOutcome("旧结果", ok = true)))
        assertFalse(s.testingLlm)
        assertFalse(s.isTestingLlm("p1"))
        // 旧结果仍在，但已经没人在测了
        assertEquals("旧结果", s.llmOutcome("p1")?.message)
    }

    // ------------------------------------------------------------------
    // 四、失败结果也必须归属（不能只记成功的）
    // ------------------------------------------------------------------

    /**
     * 早先 `testResult` 和 `testOk` 是两个独立字段，
     * 而大模型测试的失败路径上有三个早退的 `return@launch`，
     * 任何一个漏赋值都会让「文案说有错、颜色却是绿的」。
     * 合并成 [TestOutcome] 就不可能出现这种半赋状态。
     */
    @Test
    fun `失败结果也带着失败标记`() {
        val s = SettingsUiState(
            llmTestResults = mapOf("bad" to TestOutcome("连接失败（接口不可达）", ok = false))
        )
        val outcome = s.llmOutcome("bad")
        assertNotNull(outcome)
        assertFalse(outcome!!.ok)
        assertTrue(outcome.message.contains("失败"))
    }

    /** MinerU 侧同理：失败不得抬标为成功。 */
    @Test
    fun `MinerU 失败不得抬标为成功`() {
        val s = SettingsUiState(
            mineruTestResult = "连接失败，请检查 Key 与网络",
            mineruTestOk = false
        )
        assertFalse(s.mineruTestOk)
        assertNotNull(s.mineruTestResult)
    }
}