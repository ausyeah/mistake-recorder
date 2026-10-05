package com.mistakebook.math

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class AlphaStatsBenchmarkTest {

    private fun alphaStatsBaseline(w: Int, h: Int, getPixel: (Int, Int) -> Int): Pair<Int, String> {
        var total = 0
        var opaque = 0
        var visible = 0
        var maxAlpha = 0
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                total++
                val a = getPixel(x, y) ushr 24
                if (a > maxAlpha) maxAlpha = a
                if (a == 255) opaque++
                if (a > 0) {
                    visible++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        val box = if (maxX < 0) "空" else "($minX,$minY)-($maxX,$maxY)"
        return maxAlpha to box
    }

    private fun alphaStatsOptimized(w: Int, h: Int, pixels: IntArray): Pair<Int, String> {
        var total = 0
        var opaque = 0
        var visible = 0
        var maxAlpha = 0
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        var idx = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                total++
                val a = pixels[idx++] ushr 24
                if (a > maxAlpha) maxAlpha = a
                if (a == 255) opaque++
                if (a > 0) {
                    visible++
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        val box = if (maxX < 0) "空" else "($minX,$minY)-($maxX,$maxY)"
        return maxAlpha to box
    }

    @Test
    fun benchmarkAndVerifyCorrectness() {
        val w = 1000
        val h = 1000
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x in 100..200 && y in 100..200) (0xFF shl 24) or 0x123456
            else if (x in 300..400 && y in 300..400) (0x80 shl 24) or 0x654321
            else 0x00000000
        }

        val getPixel: (Int, Int) -> Int = { x, y -> pixels[y * w + x] }

        val resBaseline = alphaStatsBaseline(w, h, getPixel)
        val resOptimized = alphaStatsOptimized(w, h, pixels)

        assertEquals("Outputs must match", resBaseline, resOptimized)

        // Warm up
        repeat(5) {
            alphaStatsBaseline(w, h, getPixel)
            alphaStatsOptimized(w, h, pixels)
        }

        val baselineTime = measureNanoTime {
            repeat(20) {
                alphaStatsBaseline(w, h, getPixel)
            }
        } / 20.0 / 1e6

        val optTime = measureNanoTime {
            repeat(20) {
                alphaStatsOptimized(w, h, pixels)
            }
        } / 20.0 / 1e6

        println("Baseline average time: %.3f ms".format(baselineTime))
        println("Optimized average time: %.3f ms".format(optTime))
        println("Speedup: %.2fx".format(baselineTime / optTime))
    }
}
