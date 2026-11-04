package com.dabendan.tapink

object TapInkUnitTest {

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

        var state = 42UL
        val noisy = ByteArray(2000) {
            state = state * 6364136223846793005UL + 1442695040888963407UL
            (state shr 33).toByte()
        }
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

        // 4. Official EZ Sign algorithm parity (libapp.so @ 0x6c07f0 / 0x6c1970):
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

        //    b) Warm golden yellow RGB(240, 179, 54) (e.g. ukiyo-e clouds/boats) must stay 100% pure Yellow (index 2) without red contamination
        val goldenYellowPixels = IntArray(400 * 300) { 0xFFF0B336.toInt() }
        val goldenYellowRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 400,
            height = 300,
            argbPixels = goldenYellowPixels,
            colorMode = FloydSteinbergDitherer.ColorMode.BWRY_4COLOR,
            hwWidth = 400,
            hwHeight = 300
        )
        assertTrue(
            goldenYellowRes.paletteIndices.all { it.toInt() == 2 },
            "Warm golden yellow RGB(240,179,54) must stay 100% pure Yellow (index 2) with zero Red speckle contamination"
        )

        //    c) Official 4-color BWRY Yellow vertex uses RGB(255, 192, 0) (#FFC000, 0x6c1970) for both quantization and preview
        assertTrue(
            FloydSteinbergDitherer.ColorMode.BWRY_4COLOR.paletteRgb[2].contentEquals(intArrayOf(255, 192, 0)),
            "BWRY quantization yellow must be (255, 192, 0) matching official libapp.so @ 0x6c1970"
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

        val cfg = TapInkProtocol.executeDynamicUpdate(
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

        val cfg = TapInkProtocol.executeDynamicUpdate(
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
        var caught: TapInkProtocolException? = null
        try {
            TapInkProtocol.executeDynamicUpdate(
                transceiver = sim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(0) }
            ) { _, _, _, _ -> }
        } catch (e: TapInkProtocolException) {
            caught = e
        }
        assertTrue(caught != null, "Expected TapInkProtocolException on unsupported color code 0xFF")
        assertTrue(caught!!.message!!.contains("不支援的硬體色碼"), "Got: ${caught.message}")
    }

    private fun testProtocolFailFastOnMidWriteTagLost() {
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_TAG_LOST_MID_WRITE)
        var caught: TapInkProtocolException? = null
        try {
            TapInkProtocol.executeDynamicUpdate(
                transceiver = sim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(30000) { (it % 251).toByte() } }
            ) { _, _, _, _ -> }
        } catch (e: TapInkProtocolException) {
            caught = e
        }
        assertTrue(caught != null, "Expected TapInkProtocolException on mid-write tag lost")
        assertTrue(caught!!.message!!.contains("感應斷開"), "Got: ${caught.message}")
        assertTrue(!sim.refreshTriggered, "Must not trigger refresh if write was interrupted")
    }

    private fun testFmsCosV1DoubledHeight420BWRYDetection() {
        // Real EZ Sign 4.2" 4-color BWRY tag (UID=4E20CF1D) reports rawHeight=600, rawWidth=400, colorCode=0x20 in Tag 0xA0
        val sim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_SUCCESS_420_BWRY_COSV1)
        val cfg = TapInkProtocol.executeDynamicUpdate(
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

    private fun testExhaustiveFmsHardwareMatrixAndUncompressedFallback() {
        val matrix = listOf(
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x90.toByte(), 0x00, 0xC8.toByte(), 0x90.toByte(), 0x00), Pair(200, 200), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x00, 0x01, 0x28, 0x90.toByte(), 0x00), Pair(296, 128), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x50, 0x01, 0x80.toByte(), 0x90.toByte(), 0x00), Pair(384, 168), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x50, 0x01, 0x90.toByte(), 0x90.toByte(), 0x00), Pair(400, 168), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x00, 0xF4.toByte(), 0x00, 0xFA.toByte(), 0x90.toByte(), 0x00), Pair(250, 122), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x30, 0x01, 0x28, 0x90.toByte(), 0x00), Pair(296, 152), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0x70, 0x01, 0x68, 0x90.toByte(), 0x00), Pair(360, 184), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x10, 0x20, 0x01, 0xE0.toByte(), 0x01, 0xA0.toByte(), 0x90.toByte(), 0x00), Pair(416, 240), FloydSteinbergDitherer.ColorMode.BWRY_4COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x60, 0x01, 0x90.toByte(), 0x02, 0x58, 0xD1.toByte(), 0x01, 0x00, 0xE0.toByte(), 0x01, 0x01, 0x90.toByte(), 0x00), Pair(600, 400), FloydSteinbergDitherer.ColorMode.SPECTRA_6COLOR),
            Triple(byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x30, 0x00, 0x80.toByte(), 0x01, 0x28, 0x90.toByte(), 0x00), Pair(296, 128), FloydSteinbergDitherer.ColorMode.BWR_3COLOR)
        )
        for ((tlv, dims, expMode) in matrix) {
            val parsed = TapInkProtocol.parseHardwareConfig(tlv)
            assertEquals(dims.first, parsed.normWidth, "Width mismatch for ${parsed.modelSummary}")
            assertEquals(dims.second, parsed.normHeight, "Height mismatch for ${parsed.modelSummary}")
            assertEquals(expMode, parsed.colorMode, "ColorMode mismatch for ${parsed.modelSummary}")
        }

        val semiAlphaPixels = IntArray(200 * 200) { 0x80FF0000.toInt() }
        val bwrRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 200,
            height = 200,
            argbPixels = semiAlphaPixels,
            colorMode = FloydSteinbergDitherer.ColorMode.BWR_3COLOR,
            hwWidth = 200,
            hwHeight = 200
        )
        assertEquals(10000, bwrRes.packedHardwareBytes.size, "200x200 BWR size must be 10,000 bytes")

        val spectraRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width = 200,
            height = 200,
            argbPixels = IntArray(200 * 200) { 0xFF00FF00.toInt() },
            colorMode = FloydSteinbergDitherer.ColorMode.SPECTRA_6COLOR,
            hwWidth = 200,
            hwHeight = 200
        )
        assertEquals(10000, spectraRes.packedHardwareBytes.size, "200x200 Spectra6 size must be 10,000 bytes")

        var f0d2Count = 0
        val uncompMock = object : IsoDepTransceiver {
            private var conn = false
            override fun connect(timeoutMs: Int) { conn = true }
            override fun isConnected(): Boolean = conn
            override fun close() { conn = false }
            override fun transceive(command: ByteArray): ByteArray {
                val c0 = command[0].toInt() and 0xFF
                val c1 = command[1].toInt() and 0xFF
                if (c0 == 0x00 && c1 == 0xD1) {
                    return byteArrayOf(0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x20, 0x00, 0xC8.toByte(), 0x00, 0xC8.toByte(), 0xD1.toByte(), 0x01, 0x00, 0x90.toByte(), 0x00)
                }
                if (c0 == 0xF0 && c1 == 0xD2) {
                    f0d2Count++
                    return byteArrayOf(0x90.toByte(), 0x00)
                }
                if (c0 == 0xF0 && c1 == 0xDE) {
                    return byteArrayOf(0x00, 0x90.toByte(), 0x00)
                }
                return byteArrayOf(0x90.toByte(), 0x00)
            }
        }
        val uncompCfg = TapInkProtocol.executeDynamicUpdate(
            transceiver = uncompMock,
            refreshInitialWaitMs = 0L,
            refreshPollIntervalMs = 0L,
            imageProvider = { ByteArray(10000) { 0x55 } }
        ) { _, _, _, _ -> }
        assertTrue(!uncompCfg.compressionSupported, "Expected compressionSupported=false")
        assertEquals(40, f0d2Count, "10,000B / 250B = 40 F0D2 packets")
    }

    private fun testProtocolAndLzoCorruptInputGuardrails() {
        val pinFailSim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_PIN_FAIL)
        var pinCaught = false
        try {
            TapInkProtocol.executeDynamicUpdate(
                transceiver = pinFailSim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(30000) }
            ) { _, _, _, _ -> }
        } catch (_: TapInkProtocolException) {
            pinCaught = true
        }
        assertTrue(pinCaught, "Expected TapInkProtocolException on PIN fail")

        val refDiscSim = SimulatedIsoDepTransceiver(mode = SimulatedIsoDepTransceiver.MODE_REFRESH_DISCONNECT)
        var refCaught = false
        try {
            TapInkProtocol.executeDynamicUpdate(
                transceiver = refDiscSim,
                refreshInitialWaitMs = 0L,
                refreshPollIntervalMs = 0L,
                imageProvider = { ByteArray(30000) }
            ) { _, _, _, _ -> }
        } catch (_: TapInkProtocolException) {
            refCaught = true
        }
        assertTrue(refCaught, "Expected TapInkProtocolException on refresh disconnect")

        for (badTlv in listOf(
            byteArrayOf(0x90.toByte(), 0x00),
            byteArrayOf(0xA0.toByte()),
            byteArrayOf(0xA0.toByte(), 0x10, 0x00, 0x90.toByte(), 0x00),
            byteArrayOf(0xA0.toByte(), 0x03, 0x00, 0x00, 0x00, 0x90.toByte(), 0x00),
            byteArrayOf(0xC1.toByte(), 0x02, 0x12, 0x34, 0x90.toByte(), 0x00)
        )) {
            var tlvCaught = false
            try {
                TapInkProtocol.parseHardwareConfig(badTlv)
            } catch (_: TapInkProtocolException) {
                tlvCaught = true
            }
            assertTrue(tlvCaught, "Expected corrupt TLV to throw")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        LogManager.androidLogEnabled = false
        println("============================================================")
        println("  TAPINK DYNAMIC NFC UPDATER - JVM UNIT TEST SUITE")
        println("============================================================")

        runTest("1. LZO1X Compression & Decompression Round-Trip") { testLzo1xCompressionRoundTrip() }
        runTest("2. Multi-Spec Floyd-Steinberg Dithering (4.2\" BWRY, 2.9\" BW, 2.13\" BWRY)") { testFloydSteinbergMultiSpecDithering() }
        runTest("3. Dynamic Auto-Detection & Transfer: 4.2\" 400x300 4-Color (0x31)") { testDynamicProtocolAutoDetect420BWRY() }
        runTest("4. Dynamic Auto-Detection & Transfer: 2.9\" 296x128 2-Color (0x20)") { testDynamicProtocolAutoDetect290BW() }
        runTest("5. Fail-Fast on Corrupt/Unsupported Hardware Color Code (0xFF)") { testProtocolFailFastOnUnsupportedColorCode() }
        runTest("6. Fail-Fast on Mid-Write Tag Lost (RF Disconnect)") { testProtocolFailFastOnMidWriteTagLost() }
        runTest("7. FMSH COS v1 Doubled-Height 4.2\" 4-Color BWRY (600x400, 0x20 -> 400x300 BWRY)") { testFmsCosV1DoubledHeight420BWRYDetection() }
        runTest("8. Exhaustive FMSH Hardware Matrix (9 Specs), 3/6-Color & F0D2 Uncompressed Fallback") { testExhaustiveFmsHardwareMatrixAndUncompressedFallback() }
        runTest("9. Protocol & LZO1X Corrupt Input / Error Guardrails (PIN Fail, Refresh Disconnect, Bad TLV)") { testProtocolAndLzoCorruptInputGuardrails() }

        println("------------------------------------------------------------")
        println("  Result: $passed passed, $failed failed")
        println("============================================================")
        if (failed > 0) {
            System.exit(1)
        }
    }
}
