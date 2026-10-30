package com.dabendan.ezsign

import android.nfc.tech.IsoDep
import java.io.ByteArrayOutputStream
import java.io.IOException

class EzSignProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface IsoDepTransceiver {
    @Throws(IOException::class)
    fun connect(timeoutMs: Int = 50000)

    fun isConnected(): Boolean

    @Throws(IOException::class)
    fun transceive(command: ByteArray): ByteArray

    fun close()
}

class RealIsoDepTransceiver(private val isoDep: IsoDep) : IsoDepTransceiver {
    override fun connect(timeoutMs: Int) {
        if (isoDep.isConnected) {
            try { isoDep.close() } catch (_: Exception) {}
        }
        isoDep.connect()
        isoDep.timeout = timeoutMs
    }

    override fun isConnected(): Boolean {
        return try {
            isoDep.isConnected
        } catch (_: Exception) {
            false
        }
    }

    override fun transceive(command: ByteArray): ByteArray {
        if (!isConnected()) {
            throw IOException("IsoDep channel is not connected")
        }
        return isoDep.transceive(command)
    }

    override fun close() {
        try {
            isoDep.close()
        } catch (_: Exception) {
        }
    }
}

/**
 * Deterministic APDU simulator for JVM unit tests and Android Emulator E2E verification.
 * Supports multiple hardware models (4.2" BWRY, 2.9" BW, 2.13" BWRY) and failure injection.
 */
class SimulatedIsoDepTransceiver(
    private val mode: String = MODE_SUCCESS_420_BWRY,
    private val busyPollCountBeforeIdle: Int = 2
) : IsoDepTransceiver {

    companion object {
        const val MODE_SUCCESS = "SUCCESS"
        const val MODE_SUCCESS_420_BWRY = "SUCCESS_420_BWRY"
        const val MODE_SUCCESS_420_BWRY_COSV1 = "SUCCESS_420_BWRY_COSV1"
        const val MODE_SUCCESS_290_BW = "SUCCESS_290_BW"
        const val MODE_SUCCESS_213_BWRY = "SUCCESS_213_BWRY"
        const val MODE_UNSUPPORTED_SPEC = "UNSUPPORTED_SPEC"
        const val MODE_PIN_FAIL = "PIN_FAIL"
        const val MODE_TAG_LOST_MID_WRITE = "TAG_LOST_MID_WRITE"
        const val MODE_REFRESH_DISCONNECT = "REFRESH_DISCONNECT"
    }

    private var connected = false
    var writePacketCounter = 0
        private set
    var busyPollCounter = 0
        private set
    var pinVerified = false
        private set
    var refreshTriggered = false
        private set
    var lastRefreshParam: Byte = 0
        private set
    val receivedCompressedBlocks = mutableMapOf<Int, ByteArrayOutputStream>()

    override fun connect(timeoutMs: Int) {
        if (mode == "CONNECT_FAIL") {
            throw IOException("Simulated RF link failure during connect()")
        }
        connected = true
    }

    override fun isConnected(): Boolean = connected

    override fun transceive(command: ByteArray): ByteArray {
        if (!connected) {
            throw IOException("Simulated transceiver is disconnected")
        }
        if (command.size < 2) {
            throw IOException("APDU command too short (${command.size} bytes)")
        }

        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF

        // 1. Hardware Config Query: 00 D1 00 00
        if (cla == 0x00 && ins == 0xD1) {
            return when (mode) {
                MODE_SUCCESS_290_BW -> {
                    // 2.9" 2-Color BW: 296x128 (height=0x0080, width=0x0128), color=0x20
                    byteArrayOf(
                        0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x20, 0x00, 0x80.toByte(), 0x01, 0x28,
                        0xC1.toByte(), 0x04, 0x29, 0x00, 0xB1.toByte(), 0x02,
                        0xD1.toByte(), 0x02, 0x01, 0x20,
                        0x90.toByte(), 0x00
                    )
                }
                MODE_SUCCESS_213_BWRY -> {
                    // 2.13" 4-Color BWRY: 250x122 (height=0x007A, width=0x00FA), color=0x31
                    byteArrayOf(
                        0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x31, 0x00, 0x7A, 0x00, 0xFA.toByte(),
                        0xC1.toByte(), 0x04, 0x21, 0x30, 0xC1.toByte(), 0x04,
                        0xD1.toByte(), 0x02, 0x01, 0x20,
                        0x90.toByte(), 0x00
                    )
                }
                MODE_UNSUPPORTED_SPEC -> {
                    // Unknown/corrupt color code 0xFF and 0x00 dimensions
                    byteArrayOf(
                        0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0xFF.toByte(), 0x00, 0x80.toByte(), 0x01, 0x28,
                        0x90.toByte(), 0x00
                    )
                }
                MODE_SUCCESS_420_BWRY_COSV1 -> {
                    // Real EZ Sign 4.2" 4-Color BWRY (UID=4E20CF1D): rawHeight=600 (0x0258), rawWidth=400 (0x0190), color=0x20
                    byteArrayOf(
                        0xA0.toByte(), 0x07, 0xF0.toByte(), 0x12, 0x20, 0x02, 0x58, 0x01, 0x90.toByte(),
                        0xC1.toByte(), 0x04, 0x4E, 0x20, 0xCF.toByte(), 0x1D,
                        0xD1.toByte(), 0x07, 0x01, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00,
                        0x90.toByte(), 0x00
                    )
                }
                else -> {
                    // Default 4.2" 4-Color BWRY: 400x300 (height=0x012C, width=0x0190), color=0x31
                    byteArrayOf(
                        0xA0.toByte(), 0x07, 0x00, 0x80.toByte(), 0x31, 0x01, 0x2C, 0x01, 0x90.toByte(),
                        0xC1.toByte(), 0x04, 0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
                        0xD1.toByte(), 0x02, 0x01, 0x20,
                        0x90.toByte(), 0x00
                    )
                }
            }
        }

        // 2. PIN Verification: 00 20 00 01 04 20 09 12 10
        if (cla == 0x00 && ins == 0x20) {
            if (mode == MODE_PIN_FAIL) {
                return byteArrayOf(0x69, 0x82.toByte())
            }
            val expectedPin = byteArrayOf(0x00, 0x20, 0x00, 0x01, 0x04, 0x20, 0x09, 0x12, 0x10)
            if (!command.contentEquals(expectedPin)) {
                return byteArrayOf(0x63, 0x00)
            }
            pinVerified = true
            return byteArrayOf(0x90.toByte(), 0x00)
        }

        // 3. Compressed Image Write: F0 D3 ...
        if (cla == 0xF0 && ins == 0xD3) {
            writePacketCounter++
            if (mode == MODE_TAG_LOST_MID_WRITE && writePacketCounter >= 5) {
                connected = false
                throw IOException("Tag was lost (RF field disconnected at packet #$writePacketCounter)")
            }
            if (command.size < 7) {
                return byteArrayOf(0x67, 0x00)
            }
            val packetIndex = command[5].toInt() and 0xFF
            val payload = command.copyOfRange(7, command.size)
            val bos = receivedCompressedBlocks.getOrPut(packetIndex) { ByteArrayOutputStream() }
            bos.write(payload)
            return byteArrayOf(0x90.toByte(), 0x00)
        }

        // 4. Uncompressed Image Write: F0 D2 ...
        if (cla == 0xF0 && ins == 0xD2) {
            writePacketCounter++
            if (mode == MODE_TAG_LOST_MID_WRITE && writePacketCounter >= 5) {
                connected = false
                throw IOException("Tag was lost (RF field disconnected at packet #$writePacketCounter)")
            }
            return byteArrayOf(0x90.toByte(), 0x00)
        }

        // 5. Trigger EPD Refresh: F0 D4 <05|85> 80 00
        if (cla == 0xF0 && ins == 0xD4) {
            refreshTriggered = true
            if (command.size >= 3) {
                lastRefreshParam = command[2]
            }
            return byteArrayOf(0x90.toByte(), 0x00)
        }

        // 6. Busy Status Check: F0 DE 00 00 01
        if (cla == 0xF0 && ins == 0xDE) {
            busyPollCounter++
            if (mode == MODE_REFRESH_DISCONNECT) {
                connected = false
                throw IOException("Tag lost during e-paper physical refresh")
            }
            return if (busyPollCounter <= busyPollCountBeforeIdle) {
                byteArrayOf(0x01, 0x90.toByte(), 0x00) // 0x01 = Busy
            } else {
                byteArrayOf(0x00, 0x90.toByte(), 0x00) // 0x00 = Idle (Completed)
            }
        }

        return byteArrayOf(0x6D, 0x00)
    }

    override fun close() {
        connected = false
    }

    fun reconstructDecompressedBuffer(): ByteArray {
        val out = ByteArrayOutputStream()
        val sortedKeys = receivedCompressedBlocks.keys.sorted()
        for (k in sortedKeys) {
            val compressed = receivedCompressedBlocks[k]!!.toByteArray()
            val decompressed = Lzo1x.decompress(compressed)
            out.write(decompressed)
        }
        return out.toByteArray()
    }
}

object EzSignProtocol {

    val CONFIG_INFO_CMD = byteArrayOf(0x00, 0xD1.toByte(), 0x00, 0x00)
    val PIN_VERIFY_CMD = byteArrayOf(0x00, 0x20, 0x00, 0x01, 0x04, 0x20, 0x09, 0x12, 0x10)
    val BUSY_STATUS_CMD = byteArrayOf(0xF0.toByte(), 0xDE.toByte(), 0x00, 0x00, 0x01)

    private val KNOWN_SW1 = setOf(0x90, 0x62, 0x64, 0x65, 0x67, 0x68, 0x69, 0x6A, 0x6C)

    data class HardwareConfig(
        val width: Int,
        val height: Int,
        val colorCode: Int,
        val colorMode: FloydSteinbergDitherer.ColorMode,
        val compressionSupported: Boolean,
        val uidHex: String
    ) {
        val normWidth: Int get() = maxOf(width, height)
        val normHeight: Int get() = minOf(width, height)

        val modelSummary: String
            get() {
                val inchStr = when {
                    normWidth == 400 && normHeight == 300 -> "4.2吋"
                    normWidth == 296 && normHeight == 128 -> "2.9吋"
                    normWidth == 250 && normHeight == 122 -> "2.13吋"
                    normWidth == 416 && normHeight == 240 -> "3.7吋"
                    normWidth == 200 && normHeight == 200 -> "1.54吋"
                    normWidth == 600 && normHeight == 400 -> "3.6吋"
                    else -> "${normWidth}x${normHeight}"
                }
                return "$inchStr ${normWidth}×${normHeight} ${colorMode.label}"
            }
    }

    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02X", b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    private fun verifyStatusWord(response: ByteArray, contextName: String) {
        if (response.size >= 2) {
            val sw1 = response[response.size - 2].toInt() and 0xFF
            val sw2 = response[response.size - 1].toInt() and 0xFF
            if (KNOWN_SW1.contains(sw1)) {
                if (sw1 != 0x90 || sw2 != 0x00) {
                    val swHex = String.format("%02X%02X", sw1, sw2)
                    throw EzSignProtocolException("$contextName 失敗，APDU 狀態碼異常 (SW=$swHex)")
                }
            }
        }
    }

    fun parseHardwareConfig(rawResponse: ByteArray): HardwareConfig {
        verifyStatusWord(rawResponse, "讀取硬體配置 (00D10000)")
        var bytes = rawResponse
        if (bytes.size >= 2 && KNOWN_SW1.contains(bytes[bytes.size - 2].toInt() and 0xFF)) {
            bytes = bytes.copyOfRange(0, bytes.size - 2)
        }
        if (bytes.isEmpty()) {
            throw EzSignProtocolException("讀取硬體配置 (00D10000) 回傳空資料")
        }

        var rawWidth = -1
        var rawHeight = -1
        var colorCode = -1
        var hasExtendedColorTag = false
        var compressionSupported = true
        var uidHex = "UNKNOWN"

        var i = 0
        while (i < bytes.size) {
            if (i + 2 > bytes.size) {
                throw EzSignProtocolException("TLV 格式損毀 (offset=$i)")
            }
            val tag = bytes[i].toInt() and 0xFF
            val len = bytes[i + 1].toInt() and 0xFF
            val start = i + 2
            val end = start + len
            if (end > bytes.size) {
                throw EzSignProtocolException("TLV 長度溢位 (tag=0x${String.format("%02X", tag)}, len=$len)")
            }
            val value = bytes.copyOfRange(start, end)
            when (tag) {
                0xA0 -> {
                    if (value.size != 7) {
                        throw EzSignProtocolException("Tag 0xA0 長度錯誤 (預期 7 bytes, 實得 ${value.size})")
                    }
                    colorCode = value[2].toInt() and 0xFF
                    rawHeight = ((value[3].toInt() and 0xFF) shl 8) or (value[4].toInt() and 0xFF)
                    rawWidth = ((value[5].toInt() and 0xFF) shl 8) or (value[6].toInt() and 0xFF)
                }
                0xC1 -> {
                    uidHex = bytesToHex(value)
                }
                0xD1 -> {
                    if (value.isNotEmpty()) {
                        compressionSupported = (value[0].toInt() and 0xFF) != 0x00
                    }
                }
                0xE0 -> {
                    hasExtendedColorTag = true
                }
            }
            i = end
        }

        if (rawWidth <= 0 || rawHeight <= 0 || colorCode < 0) {
            throw EzSignProtocolException("硬體回傳缺少有效螢幕規格 TLV (Tag 0xA0: ${rawWidth}x${rawHeight})")
        }

        val (resolvedWidth, resolvedHeight, resolvedColorMode) = resolveFmsHardwareSpec(
            rawWidth = rawWidth,
            rawHeight = rawHeight,
            colorCode = colorCode,
            hasExtendedColorTag = hasExtendedColorTag
        )

        return HardwareConfig(
            width = resolvedWidth,
            height = resolvedHeight,
            colorCode = colorCode,
            colorMode = resolvedColorMode,
            compressionSupported = compressionSupported,
            uidHex = uidHex
        )
    }

    /**
     * FMSH COS v1 (0x10) 4-color BWRY controllers store `colorCode = 0x20` in Tag 0xA0 and double
     * the height dimension (`rawHeight = actualHeight * 2`) so the 1-bpp controller buffer formula
     * `(rawHeight * rawWidth) / 8` allocates the exact byte count needed for 2-bpp 4-color BWRY
     * (`(actualHeight * actualWidth) / 4`).
     *
     * Verified against real hardware:
     * - EZ Sign 4.2" 4-color BWRY (400x300): Tag 0xA0 reports rawHeight=600, rawWidth=400, colorCode=0x20 (30,000 bytes = 15 LZO blocks)
     * - GoodDisplay 1.54" 4-color BWRY (200x200): Tag 0xA0 reports rawHeight=400, rawWidth=200, colorCode=0x20 (10,000 bytes = 5 LZO blocks)
     * - 2.9" 4-color BWRY (296x128): Tag 0xA0 reports rawHeight=256, rawWidth=296, colorCode=0x20
     * - 2.13" 4-color BWRY (250x122): Tag 0xA0 reports rawHeight=244..256, rawWidth=250, colorCode=0x20
     * - 3.7" 4-color BWRY (416x240): Tag 0xA0 reports rawHeight=480, rawWidth=416, colorCode=0x20
     */
    private fun resolveFmsHardwareSpec(
        rawWidth: Int,
        rawHeight: Int,
        colorCode: Int,
        hasExtendedColorTag: Boolean
    ): Triple<Int, Int, FloydSteinbergDitherer.ColorMode> {
        if (colorCode == 0x20 && !hasExtendedColorTag) {
            val d1 = rawHeight
            val d2 = rawWidth
            when {
                // 4.2" 400x300 4-color BWRY (600x400 in Tag 0xA0)
                (d1 == 600 && d2 == 400) || (d1 == 400 && d2 == 600) ->
                    return Triple(400, 300, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 1.54" 200x200 4-color BWRY (400x200 in Tag 0xA0)
                (d1 == 400 && d2 == 200) || (d1 == 200 && d2 == 400) ->
                    return Triple(200, 200, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 2.9" 296x128 4-color BWRY (256x296 in Tag 0xA0)
                (d1 == 256 && d2 == 296) || (d1 == 296 && d2 == 256) ->
                    return Triple(296, 128, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 2.9" HD 384x168 4-color BWRY (336x384 in Tag 0xA0)
                (d1 == 336 && d2 == 384) || (d1 == 384 && d2 == 336) ->
                    return Triple(384, 168, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 3.0" 400x168 4-color BWRY (336x400 in Tag 0xA0)
                (d1 == 336 && d2 == 400) || (d1 == 400 && d2 == 336) ->
                    return Triple(400, 168, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 2.13" 250x122 4-color BWRY (244/248/256 x 250 in Tag 0xA0)
                (d2 == 250 && (d1 == 244 || d1 == 248 || d1 == 256)) ||
                (d1 == 250 && (d2 == 244 || d2 == 248 || d2 == 256)) ->
                    return Triple(250, 122, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 2.66" 296x152 4-color BWRY (304x296 in Tag 0xA0)
                (d1 == 304 && d2 == 296) || (d1 == 296 && d2 == 304) ->
                    return Triple(296, 152, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 2.66" HD 360x184 4-color BWRY (368x360 in Tag 0xA0)
                (d1 == 368 && d2 == 360) || (d1 == 360 && d2 == 368) ->
                    return Triple(360, 184, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)

                // 3.7" 416x240 4-color BWRY (480x416 in Tag 0xA0)
                (d1 == 480 && d2 == 416) || (d1 == 416 && d2 == 480) ->
                    return Triple(416, 240, FloydSteinbergDitherer.ColorMode.BWRY_4COLOR)
            }
        }

        val colorMode = FloydSteinbergDitherer.ColorMode.fromColorCode(colorCode)
        return Triple(rawWidth, rawHeight, colorMode)
    }

    fun buildRefreshCmd(colorMode: FloydSteinbergDitherer.ColorMode): ByteArray {
        return byteArrayOf(
            0xF0.toByte(),
            0xD4.toByte(),
            colorMode.refreshCmdParam,
            0x80.toByte(),
            0x00
        )
    }

    /**
     * Dynamically queries the NFC e-paper tag's HardwareConfig (`00D10000`), invokes [imageProvider]
     * to dither/pack the image for the exact detected resolution and color mode, and transfers it.
     */
    fun executeDynamicUpdate(
        transceiver: IsoDepTransceiver,
        refreshInitialWaitMs: Long = 2000L,
        refreshPollIntervalMs: Long = 500L,
        refreshTimeoutMs: Long = 28000L,
        imageProvider: (HardwareConfig) -> ByteArray,
        onProgress: (step: Int, totalSteps: Int, percent: Int, message: String) -> Unit
    ): HardwareConfig {
        try {
            var highestReportedPct = 1
            val reportMonotonic: (Int, Int, Int, String) -> Unit = { step, totalSteps, pct, msg ->
                val clamped = maxOf(highestReportedPct, pct.coerceIn(1, 100))
                highestReportedPct = clamped
                onProgress(step, totalSteps, clamped, msg)
            }

            reportMonotonic(1, 4, 1, "[1/4] 連線 NFC 並自動偵測電子紙硬體規格...")
            transceiver.connect(50000)

            val cfgResp = transceiver.transceive(CONFIG_INFO_CMD)
            val cfg = parseHardwareConfig(cfgResp)
            LogManager.i(
                "EzSignProtocol",
                "自動偵測硬體規格成功: ${cfg.modelSummary} (UID=${cfg.uidHex}, color=0x${String.format("%02X", cfg.colorCode)}, lzo=${cfg.compressionSupported}, rawTlv=${bytesToHex(cfgResp)})"
            )

            reportMonotonic(1, 4, 2, "[1/4] 偵測到 ${cfg.modelSummary}，正在適配 Floyd-Steinberg 影像...")
            val packedHardwareBytes = imageProvider(cfg)
            val expectedBytes = FloydSteinbergDitherer.expectedPackedByteCount(
                cfg.normWidth,
                cfg.normHeight,
                cfg.colorMode
            )
            if (packedHardwareBytes.size != expectedBytes) {
                throw EzSignProtocolException(
                    "影像緩衝區大小不符 (${cfg.modelSummary}): 預期 $expectedBytes bytes, 實得 ${packedHardwareBytes.size} bytes"
                )
            }

            val isMultiColor = cfg.colorMode != FloydSteinbergDitherer.ColorMode.BW_2COLOR
            // Time-proportional progress weights:
            // - Multi-color (4-color BWRY): ~1.4s data transfer (1% -> 14%), ~24.0s physical refresh (15% -> 99%)
            // - 2-color (BW): ~0.8s data transfer (1% -> 44%), ~3.5s physical refresh (45% -> 99%)
            val pinPct = if (isMultiColor) 3 else 8
            val writeStartPct = if (isMultiColor) 4 else 12
            val writeSpanPct = if (isMultiColor) 10 else 32
            val refreshStartPct = writeStartPct + writeSpanPct + 1

            reportMonotonic(2, 4, pinPct, "[2/4] 驗證裝置 PIN 碼 (${cfg.modelSummary})...")
            val pinResp = transceiver.transceive(PIN_VERIFY_CMD)
            verifyStatusWord(pinResp, "PIN 碼驗證 (00200001)")

            reportMonotonic(3, 4, writeStartPct, "[3/4] 寫入 ${cfg.modelSummary} 影像資料...")
            if (cfg.compressionSupported) {
                sendCompressedImage(
                    transceiver = transceiver,
                    bytes = packedHardwareBytes,
                    modelSummary = cfg.modelSummary,
                    basePct = writeStartPct,
                    spanPct = writeSpanPct,
                    onProgress = reportMonotonic
                )
            } else {
                sendUncompressedImage(
                    transceiver = transceiver,
                    bytes = packedHardwareBytes,
                    modelSummary = cfg.modelSummary,
                    basePct = writeStartPct,
                    spanPct = writeSpanPct,
                    onProgress = reportMonotonic
                )
            }

            reportMonotonic(4, 4, refreshStartPct, "[4/4] 觸發 ${cfg.modelSummary} 物理刷新，請緊貼手機勿移動...")
            val refreshResp = transceiver.transceive(buildRefreshCmd(cfg.colorMode))
            verifyStatusWord(refreshResp, "觸發螢幕刷新 (F0D4)")

            val effectiveInitialWaitMs = if (refreshInitialWaitMs >= 1000L && isMultiColor) {
                maxOf(refreshInitialWaitMs, 15000L)
            } else {
                refreshInitialWaitMs
            }

            val expectedRefreshMs = when {
                effectiveInitialWaitMs < 1000L -> maxOf(effectiveInitialWaitMs * 2L, 300L)
                isMultiColor -> 24000L
                else -> 3500L
            }

            waitForRefreshComplete(
                transceiver = transceiver,
                modelSummary = cfg.modelSummary,
                initialWaitMs = effectiveInitialWaitMs,
                pollIntervalMs = refreshPollIntervalMs,
                timeoutMs = maxOf(refreshTimeoutMs, effectiveInitialWaitMs + 15000L),
                startPct = refreshStartPct,
                expectedRefreshMs = expectedRefreshMs,
                onProgress = reportMonotonic
            )

            reportMonotonic(4, 4, 100, "${cfg.modelSummary} 更新完成！")
            return cfg
        } catch (e: EzSignProtocolException) {
            throw e
        } catch (e: IOException) {
            throw EzSignProtocolException("NFC 通訊中斷或感應移開 (${e.message ?: "IOException"})，請緊貼電子紙重試", e)
        } catch (e: Exception) {
            throw EzSignProtocolException("NFC 協定錯誤: ${e.message}", e)
        }
    }

    private fun sendCompressedImage(
        transceiver: IsoDepTransceiver,
        bytes: ByteArray,
        modelSummary: String,
        basePct: Int,
        spanPct: Int,
        onProgress: (step: Int, totalSteps: Int, percent: Int, message: String) -> Unit
    ) {
        val blockSize = 2000
        val totalBlocks = (bytes.size + blockSize - 1) / blockSize
        val screenIndex = 0

        for (blockIdx in 0 until totalBlocks) {
            val offset = blockIdx * blockSize
            val len = minOf(blockSize, bytes.size - offset)
            val slice = bytes.copyOfRange(offset, offset + len)
            val compressed = Lzo1x.compress(slice)

            val maxChunkPayload = 250
            var chunkOffset = 0
            var chunkIdx = 0

            while (chunkOffset < compressed.size) {
                val chunkLen = minOf(maxChunkPayload, compressed.size - chunkOffset)
                val isLastChunk = (chunkOffset + chunkLen) == compressed.size
                val cmd = ByteArray(chunkLen + 7)
                cmd[0] = 0xF0.toByte()
                cmd[1] = 0xD3.toByte()
                cmd[2] = (screenIndex and 0xFF).toByte()
                cmd[3] = if (isLastChunk) 1 else 0
                cmd[4] = ((chunkLen + 2) and 0xFF).toByte()
                cmd[5] = (blockIdx and 0xFF).toByte()
                cmd[6] = (chunkIdx and 0xFF).toByte()
                System.arraycopy(compressed, chunkOffset, cmd, 7, chunkLen)

                val resp = try {
                    transceiver.transceive(cmd)
                } catch (ioe: IOException) {
                    throw EzSignProtocolException(
                        "寫入影像中斷 (第 ${blockIdx + 1}/$totalBlocks 區塊感應斷開): ${ioe.message}",
                        ioe
                    )
                }
                verifyStatusWord(resp, "寫入壓縮區塊 #${blockIdx + 1}")

                chunkOffset += chunkLen
                chunkIdx++
            }

            val pct = basePct + ((blockIdx + 1) * spanPct / totalBlocks)
            onProgress(3, 4, pct, "[3/4] 寫入 $modelSummary 壓縮區塊 (${blockIdx + 1} / $totalBlocks)...")
        }
    }

    private fun sendUncompressedImage(
        transceiver: IsoDepTransceiver,
        bytes: ByteArray,
        modelSummary: String,
        basePct: Int,
        spanPct: Int,
        onProgress: (step: Int, totalSteps: Int, percent: Int, message: String) -> Unit
    ) {
        val payloadSize = 250
        val totalPackets = (bytes.size + payloadSize - 1) / payloadSize
        val screenIndex = 0

        for (pktIdx in 0 until totalPackets) {
            val offset = pktIdx * payloadSize
            val currentLen = minOf(payloadSize, bytes.size - offset)
            val cmd = ByteArray(payloadSize + 5)
            if (currentLen < payloadSize) {
                for (k in 5 until cmd.size) cmd[k] = 0x01
            }
            cmd[0] = 0xF0.toByte()
            cmd[1] = 0xD2.toByte()
            cmd[2] = (screenIndex and 0xFF).toByte()
            cmd[3] = (pktIdx and 0xFF).toByte()
            cmd[4] = 0xFA.toByte()
            System.arraycopy(bytes, offset, cmd, 5, currentLen)

            val resp = try {
                transceiver.transceive(cmd)
            } catch (ioe: IOException) {
                throw EzSignProtocolException(
                    "寫入未壓縮影像中斷 (第 ${pktIdx + 1}/$totalPackets 包斷開): ${ioe.message}",
                    ioe
                )
            }
            verifyStatusWord(resp, "寫入影像封包 #${pktIdx + 1}")

            val pct = basePct + ((pktIdx + 1) * spanPct / totalPackets)
            onProgress(3, 4, pct, "[3/4] 寫入 $modelSummary 影像封包 (${pktIdx + 1} / $totalPackets)...")
        }
    }

    private fun waitForRefreshComplete(
        transceiver: IsoDepTransceiver,
        modelSummary: String,
        initialWaitMs: Long,
        pollIntervalMs: Long,
        timeoutMs: Long,
        startPct: Int,
        expectedRefreshMs: Long,
        onProgress: (step: Int, totalSteps: Int, percent: Int, message: String) -> Unit
    ) {
        val startTime = System.currentTimeMillis()
        val spanPct = (99 - startPct).coerceAtLeast(1)
        val denomMs = expectedRefreshMs.coerceAtLeast(1L).toDouble()

        fun computeUnifiedPct(elapsedMs: Long): Int {
            val ratio = (elapsedMs.toDouble() / denomMs).coerceIn(0.0, 1.0)
            return (startPct + (ratio * spanPct).toInt()).coerceIn(startPct, 99)
        }

        if (initialWaitMs > 0) {
            if (initialWaitMs >= 1000L) {
                val stepSleep = 250L
                while (System.currentTimeMillis() - startTime < initialWaitMs) {
                    if (!transceiver.isConnected()) {
                        throw EzSignProtocolException("電子紙刷新期間 NFC 供電中斷，畫面可能未完整更新，請重試")
                    }
                    val elapsedMs = System.currentTimeMillis() - startTime
                    val elapsedSec = elapsedMs / 1000.0
                    val pct = computeUnifiedPct(elapsedMs)
                    onProgress(
                        4,
                        4,
                        pct,
                        String.format("[4/4] %s 刷新供電中 (已耗時 %.1f 秒)，請保持緊貼...", modelSummary, elapsedSec)
                    )
                    Thread.sleep(minOf(stepSleep, maxOf(1L, initialWaitMs - elapsedMs)))
                }
            } else {
                Thread.sleep(initialWaitMs)
            }
        }

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (!transceiver.isConnected()) {
                throw EzSignProtocolException("電子紙刷新期間 NFC 供電中斷，畫面可能未完整更新，請重試")
            }
            val resp = try {
                transceiver.transceive(BUSY_STATUS_CMD)
            } catch (ioe: IOException) {
                throw EzSignProtocolException("電子紙刷新期間讀取狀態斷線: ${ioe.message}", ioe)
            }

            if (resp.isEmpty()) {
                throw EzSignProtocolException("電子紙刷新狀態回傳空值 (F0DE000001)")
            }

            val busyFlag = resp[0].toInt() and 0xFF
            if (busyFlag == 0x00) {
                return
            }

            val elapsedMs = System.currentTimeMillis() - startTime
            val elapsedSec = elapsedMs / 1000.0
            val pct = computeUnifiedPct(elapsedMs)
            onProgress(
                4,
                4,
                pct,
                String.format("[4/4] %s 刷新供電中 (已耗時 %.1f 秒)，請保持緊貼...", modelSummary, elapsedSec)
            )
            if (pollIntervalMs > 0) {
                Thread.sleep(pollIntervalMs)
            }
        }

        throw EzSignProtocolException("電子紙刷新逾時 (${timeoutMs / 1000}秒未回傳 0x00 完成訊號)")
    }
}
