package com.dabendan.ezsign

import kotlin.random.Random

object EzSignUnitTest {

    private var passed = 0
    private var failed = 0

    private fun assertTrue(condition: Boolean, message: String) {
        if (!condition) {
            throw AssertionError("ASSERTION FAILED: $message")
        }
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String) {
        if (expected != actual) {
            throw AssertionError("ASSERTION FAILED: $message (expected=$expected, actual=$actual)")
        }
    }

    private fun runTest(name: String, block: () -> Unit) {
        try {
            block()
            passed++
            println("  [PASS] $name")
        } catch (t: Throwable) {
            failed++
            println("  [FAIL] $name -> ${t.message}")
            t.printStackTrace()
        }
    }

    private fun testLzo1xCompressionRoundTrip() {
        val uniform = ByteArray(2000) { 0x55.toByte() }
        val compUniform = Lzo1x.compress(uniform)
        assertTrue(compUniform.size < 100, "Uniform 2000B block should compress under 100B, got ${compUniform.size}")
        val decompUniform = Lzo1x.decompress(compUniform, uniform.size)
        assertTrue(uniform.contentEquals(decompUniform), "Uniform round-trip mismatch")

        val checker = ByteArray(2000) { i -> if (i % 2 == 0) 0x1B.toByte() else 0xE4.toByte() }
        val compChecker = Lzo1x.compress(checker)
        val decompChecker = Lzo1x.decompress(compChecker, checker.size)
        assertTrue(checker.contentEquals(decompChecker), "Checkerboard round-trip mismatch")

        val rng = Random(42)
        val noisy = ByteArray(2000) { rng.nextInt(256).toByte() }
        val compNoisy = Lzo1x.compress(noisy)
        val decompNoisy = Lzo1x.decompress(compNoisy, noisy.size)
        assertTrue(noisy.contentEquals(decompNoisy), "Noisy 2000B round-trip mismatch")
    }

    private fun testFloydSteinbergMultiSpecDithering() {
        // 1. 4.2" 4-Color (300x400 BWRY) -> 30,000 bytes
        val whitePixels = IntArray(300 * 400) { 0xFFFFFFFF.toInt() }
        val whiteRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 300,
            height = 400,
            argbPixels = whitePixels,
            colorMode = FloydSteinbergDitherer.ColorMode.BWRY_4COLOR,
            hwWidth = 400,
            hwHeight = 300
        )
        assertEquals(30000, whiteRes.packedHardwareBytes.size, "4.2\" packed size must be 30,000 bytes")
        assertTrue(whiteRes.packedHardwareBytes.all { it == 0x55.toByte() }, "Solid white must pack to 0x55")

        // 2. 2.9" 2-Color (296x128 BW) -> 296 * (128/4) = 9,472 bytes
        val gray290 = IntArray(296 * 128) { 0xFF808080.toInt() }
        val bw290Res = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 296,
            height = 128,
            argbPixels = gray290,
            colorMode = FloydSteinbergDitherer.ColorMode.BW_2COLOR,
            hwWidth = 296,
            hwHeight = 128
        )
        assertEquals(9472, bw290Res.packedHardwareBytes.size, "2.9\" packed size must be 9,472 bytes")
        assertTrue(
            bw290Res.paletteIndices.all { it.toInt() == 0 || it.toInt() == 1 },
            "2-color BW mode must only emit palette indices 0 (Black) and 1 (White)"
        )

        // 3. 2.13" 4-Color (250x122 BWRY) -> 250 * ceil(122/4) = 250 * 31 = 7,750 bytes
        val orange213 = IntArray(250 * 122) { 0xFFFF8000.toInt() }
        val bwry213Res = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 250,
            height = 122,
            argbPixels = orange213,
            colorMode = FloydSteinbergDitherer.ColorMode.BWRY_4COLOR,
            hwWidth = 250,
            hwHeight = 122
        )
        assertEquals(7750, bwry213Res.packedHardwareBytes.size, "2.13\" packed size must be 7,750 bytes")

        // 4. Official EZ Sign algorithm parity:
        //    a) Warm cream parchment background RGB(255, 244, 216) must stay 100% pure White (index 1) without yellow speckles
        val creamPixels = IntArray(400 * 300) { 0xFFFFF4D8.toInt() }
        val creamRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 400,
            height = 300,
            argbPixels = creamPixels,
            colorMode = FloydSteinbergDitherer.ColorMode.BWRY_4COLOR,
            hwWidth = 400,
            hwHeight = 300
        )
        assertTrue(
            creamRes.paletteIndices.all { it.toInt() == 1 },
            "Warm cream background RGB(255,244,216) must stay 100% pure White (index 1)"
        )

        //    b) Neutral gray RGB(128, 128, 128) in 4-color BWRY must only use Black (0) and White (1), never Red/Yellow
        val neutralGray400 = IntArray(400 * 300) { 0xFF808080.toInt() }
        val gray4ColorRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 400,
            height = 300,
            argbPixels = neutralGray400,
            colorMode = FloydSteinbergDitherer.ColorMode.BWRY_4COLOR,
            hwWidth = 400,
            hwHeight = 300
        )
        assertTrue(
            gray4ColorRes.paletteIndices.all { it.toInt() == 0 || it.toInt() == 1 },
            "Neutral gray in 4-color BWRY must only emit Black (0) and White (1)"
        )

        //    c) Dual-palette Yellow: quantization uses (255, 255, 0) while preview ARGB uses #FFC000 (0xFFFFC000)
        assertTrue(
            FloydSteinbergDitherer.ColorMode.BWRY_4COLOR.paletteRgb[2].contentEquals(intArrayOf(255, 255, 0)),
            "BWRY quantization yellow must be (255, 255, 0)"
        )
        assertEquals(
            0xFFFFC000.toInt(),
            FloydSteinbergDitherer.ColorMode.BWRY_4COLOR.paletteArgb[2],
            "BWRY preview ARGB yellow must be 0xFFFFC000 (#FFC000)"
        )
    }

    private fun testDynamicProtocolAutoDetect420BWRY() {
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_SUCCESS_420_BWRY, busyPollCountBeforeIdle = 3)
        var providedBytes: ByteArray? = null
        val progressHistory = mutableListOf<Int>()

        val cfg = EzSignProtocol.executeDynamicUpdate(
            transceiver = sim,
            refreshInitialWaitMs = 50L,
            refreshPollIntervalMs = 20L,
            imageProvider = { hwCfg ->
                val pixels = IntArray(hwCfg.normWidth * hwCfg.normHeight) { 0xFFFF8000.toInt() }
                val res = FloydSteinbergDitherer.ditherAndPackPixels(
                    width = hwCfg.normWidth,
                    height = hwCfg.normHeight,
                    argbPixels = pixels,
                    colorMode = hwCfg.colorMode,
                    hwWidth = hwCfg.normWidth,
                    hwHeight = hwCfg.normHeight
                )
                providedBytes = res.packedHardwareBytes
                res.packedHardwareBytes
            }
        ) { _, _, pct, _ ->
            progressHistory.add(pct)
        }

        assertEquals(400, cfg.normWidth, "Auto-detected width must be 400")
        assertEquals(300, cfg.normHeight, "Auto-detected height must be 300")
        assertEquals(FloydSteinbergDitherer.ColorMode.BWRY_4COLOR, cfg.colorMode, "Auto-detected color mode must be BWRY_4COLOR")
        assertEquals(0x85.toByte(), sim.lastRefreshParam, "4-color refresh param must be 0x85")
        assertEquals(15, sim.receivedCompressedBlocks.size, "30,000B / 2,000B = 15 blocks")
        assertTrue(providedBytes!!.contentEquals(sim.reconstructDecompressedBuffer()), "Decompressed APDU payload must match")
        assertTrue(progressHistory.isNotEmpty() && progressHistory.first() == 1 && progressHistory.last() == 100, "Progress must start at 1% and end at 100%: $progressHistory")
        for (i in 1 until progressHistory.size) {
            assertTrue(
                progressHistory[i] >= progressHistory[i - 1],
                "Progress must never roll back: index $i (${progressHistory[i]}% < ${progressHistory[i - 1]}%) in $progressHistory"
            )
        }
    }

    private fun testDynamicProtocolAutoDetect290BW() {
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_SUCCESS_290_BW, busyPollCountBeforeIdle = 1)
        var providedBytes: ByteArray? = null

        val cfg = EzSignProtocol.executeDynamicUpdate(
            transceiver = sim,
            refreshInitialWaitMs = 0L,
            refreshPollIntervalMs = 0L,
            imageProvider = { hwCfg ->
                val pixels = IntArray(hwCfg.normWidth * hwCfg.normHeight) { 0xFF808080.toInt() }
                val res = FloydSteinbergDitherer.ditherAndPackPixels(
                    width = hwCfg.normWidth,
                    height = hwCfg.normHeight,
                    argbPixels = pixels,
                    colorMode = hwCfg.colorMode,
                    hwWidth = hwCfg.normWidth,
                    hwHeight = hwCfg.normHeight
                )
                providedBytes = res.packedHardwareBytes
                res.packedHardwareBytes
            }
        ) { _, _, _, _ -> }

        assertEquals(296, cfg.normWidth, "Auto-detected width must be 296")
        assertEquals(128, cfg.normHeight, "Auto-detected height must be 128")
        assertEquals(FloydSteinbergDitherer.ColorMode.BW_2COLOR, cfg.colorMode, "Auto-detected color mode must be BW_2COLOR")
        assertEquals(0x05.toByte(), sim.lastRefreshParam, "2-color BW refresh param must be 0x05")
        assertEquals(5, sim.receivedCompressedBlocks.size, "9,472B / 2,000B = 5 blocks")
        assertTrue(providedBytes!!.contentEquals(sim.reconstructDecompressedBuffer()), "Decompressed 2.9\" payload must match")
    }

    private fun testProtocolFailFastOnUnsupportedColorCode() {
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_UNSUPPORTED_SPEC)
        var caught: EzSignProtocolException? = null
        try {
            EzSignProtocol.executeDynamicUpdate(
                transceiver = sim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(0) }
            ) { _, _, _, _ -> }
        } catch (e: EzSignProtocolException) {
            caught = e
        }
        assertTrue(caught != null, "Expected EzSignProtocolException on unsupported color code 0xFF")
        assertTrue(caught!!.message!!.contains("不支援的硬體色碼"), "Got: ${caught.message}")
    }

    private fun testProtocolFailFastOnMidWriteTagLost() {
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_TAG_LOST_MID_WRITE)
        var caught: EzSignProtocolException? = null
        try {
            EzSignProtocol.executeDynamicUpdate(
                transceiver = sim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(30000) { (it % 251).toByte() } }
            ) { _, _, _, _ -> }
        } catch (e: EzSignProtocolException) {
            caught = e
        }
        assertTrue(caught != null, "Expected EzSignProtocolException on mid-write tag lost")
        assertTrue(caught!!.message!!.contains("感應斷開"), "Got: ${caught.message}")
        assertTrue(!sim.refreshTriggered, "Must not trigger refresh if write was interrupted")
    }

    private fun testFmsCosV1DoubledHeight420BWRYDetection() {
        // Real EZ Sign 4.2" 4-color BWRY tag (UID=4E20CF1D) reports rawHeight=600, rawWidth=400, colorCode=0x20 in Tag 0xA0
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_SUCCESS_420_BWRY_COSV1)
        val cfg = EzSignProtocol.executeDynamicUpdate(
            transceiver = sim,
            refreshInitialWaitMs = 1L,
            refreshPollIntervalMs = 1L,
            imageProvider = { detected ->
                assertEquals(400, detected.normWidth, "Resolved width should be 400")
                assertEquals(300, detected.normHeight, "Resolved height should be 300 (600 / 2)")
                assertEquals(FloydSteinbergDitherer.ColorMode.BWRY_4COLOR, detected.colorMode, "Should resolve to BWRY_4COLOR")
                ByteArray(30000) { ((it * 13) and 0xFF).toByte() }
            }
        ) { _, _, _, _ -> }
        assertEquals("4.2吋 400×300 4色 (黑白黃紅 BWRY)", cfg.modelSummary, "Model summary should match 4.2\" BWRY")
        assertEquals(0x85.toByte(), sim.lastRefreshParam, "4-color BWRY must use 0x85 refresh param")
        assertEquals(15, sim.receivedCompressedBlocks.size, "Must write exact 15 LZO blocks (not 30)")
        assertEquals(30000, sim.reconstructDecompressedBuffer().size, "Must write exact 30,000 bytes (15 LZO blocks)")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        LogManager.androidLogEnabled = false
        println("============================================================")
        println("  EZ SIGN DYNAMIC NFC UPDATER - JVM UNIT TEST SUITE")
        println("============================================================")

        runTest("1. LZO1X Compression & Decompression Round-Trip") { testLzo1xCompressionRoundTrip() }
        runTest("2. Multi-Spec Floyd-Steinberg Dithering (4.2\" BWRY, 2.9\" BW, 2.13\" BWRY)") { testFloydSteinbergMultiSpecDithering() }
        runTest("3. Dynamic Auto-Detection & Transfer: 4.2\" 400x300 4-Color (0x31)") { testDynamicProtocolAutoDetect420BWRY() }
        runTest("4. Dynamic Auto-Detection & Transfer: 2.9\" 296x128 2-Color (0x20)") { testDynamicProtocolAutoDetect290BW() }
        runTest("5. Fail-Fast on Corrupt/Unsupported Hardware Color Code (0xFF)") { testProtocolFailFastOnUnsupportedColorCode() }
        runTest("6. Fail-Fast on Mid-Write Tag Lost (RF Disconnect)") { testProtocolFailFastOnMidWriteTagLost() }
        runTest("7. FMSH COS v1 Doubled-Height 4.2\" 4-Color BWRY (600x400, 0x20 -> 400x300 BWRY)") { testFmsCosV1DoubledHeight420BWRYDetection() }

        println("------------------------------------------------------------")
        println("  Result: $passed passed, $failed failed")
        println("============================================================")
        if (failed > 0) {
            System.exit(1)
        }
    }
}
