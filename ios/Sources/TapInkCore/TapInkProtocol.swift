import Foundation

public struct TapInkProtocolError: Error, LocalizedError, Equatable, Sendable {
    public let message: String

    public init(message: String) {
        self.message = message
    }

    public var errorDescription: String? {
        return message
    }
}

public struct TapInkIOError: Error, LocalizedError, Equatable, Sendable {
    public let message: String

    public init(_ message: String) {
        self.message = message
    }

    public var errorDescription: String? {
        return message
    }
}

/// Synchronous & asynchronous ISO 14443-4 / ISO 7816 APDU transceiver abstraction.
public protocol IsoDepTransceiver: AnyObject {
    func connect(timeoutMs: Int) throws
    func isConnected() -> Bool
    func transceive(command: [UInt8]) throws -> [UInt8]
    func close()
}

/// Deterministic APDU simulator for XCTest unit tests and iOS Demo Mode (App Review without physical NFC hardware).
/// 100% compatible with Android `SimulatedIsoDepTransceiver.kt`.
public final class SimulatedIsoDepTransceiver: IsoDepTransceiver {

    public static let modeSuccess = "SUCCESS"
    public static let modeSuccess420BWRY = "SUCCESS_420_BWRY"
    public static let modeSuccess420BWRYCosV1 = "SUCCESS_420_BWRY_COSV1"
    public static let modeSuccess290BW = "SUCCESS_290_BW"
    public static let modeSuccess213BWRY = "SUCCESS_213_BWRY"
    public static let modeUnsupportedSpec = "UNSUPPORTED_SPEC"
    public static let modePinFail = "PIN_FAIL"
    public static let modeTagLostMidWrite = "TAG_LOST_MID_WRITE"
    public static let modeRefreshDisconnect = "REFRESH_DISCONNECT"

    private let mode: String
    private let busyPollCountBeforeIdle: Int
    private var connected = false

    public private(set) var writePacketCounter = 0
    public private(set) var busyPollCounter = 0
    public private(set) var pinVerified = false
    public private(set) var refreshTriggered = false
    public private(set) var lastRefreshParam: UInt8 = 0
    public private(set) var receivedCompressedBlocks: [Int: [UInt8]] = [:]

    public init(mode: String = SimulatedIsoDepTransceiver.modeSuccess420BWRY, busyPollCountBeforeIdle: Int = 2) {
        self.mode = mode
        self.busyPollCountBeforeIdle = busyPollCountBeforeIdle
    }

    public func connect(timeoutMs: Int = 50000) throws {
        if mode == "CONNECT_FAIL" {
            throw TapInkIOError("Simulated RF link failure during connect()")
        }
        connected = true
    }

    public func isConnected() -> Bool {
        return connected
    }

    public func transceive(command: [UInt8]) throws -> [UInt8] {
        if !connected {
            throw TapInkIOError("Simulated transceiver is disconnected")
        }
        if command.count < 2 {
            throw TapInkIOError("APDU command too short (\(command.count) bytes)")
        }

        let cla = Int(command[0])
        let ins = Int(command[1])

        // 1. Hardware Config Query: 00 D1 00 00
        if cla == 0x00 && ins == 0xD1 {
            switch mode {
            case Self.modeSuccess290BW:
                // 2.9" 2-Color BW: 296x128 (height=0x0080, width=0x0128), color=0x20
                return [
                    0xA0, 0x07, 0x00, 0x80, 0x20, 0x00, 0x80, 0x01, 0x28,
                    0xC1, 0x04, 0x29, 0x00, 0xB1, 0x02,
                    0xD1, 0x02, 0x01, 0x20,
                    0x90, 0x00
                ]
            case Self.modeSuccess213BWRY:
                // 2.13" 4-Color BWRY: 250x122 (height=0x007A, width=0x00FA), color=0x31
                return [
                    0xA0, 0x07, 0x00, 0x80, 0x31, 0x00, 0x7A, 0x00, 0xFA,
                    0xC1, 0x04, 0x21, 0x30, 0xC1, 0x04,
                    0xD1, 0x02, 0x01, 0x20,
                    0x90, 0x00
                ]
            case Self.modeUnsupportedSpec:
                // Unknown/corrupt color code 0xFF
                return [
                    0xA0, 0x07, 0x00, 0x80, 0xFF, 0x00, 0x80, 0x01, 0x28,
                    0x90, 0x00
                ]
            case Self.modeSuccess420BWRYCosV1:
                // Real EZ Sign 4.2" 4-Color BWRY (UID=4E20CF1D): rawHeight=600 (0x0258), rawWidth=400 (0x0190), color=0x20
                return [
                    0xA0, 0x07, 0xF0, 0x12, 0x20, 0x02, 0x58, 0x01, 0x90,
                    0xC1, 0x04, 0x4E, 0x20, 0xCF, 0x1D,
                    0xD1, 0x07, 0x01, 0x10, 0x00, 0x00, 0x00, 0x00, 0x00,
                    0x90, 0x00
                ]
            default:
                // Default 4.2" 4-Color BWRY: 400x300 (height=0x012C, width=0x0190), color=0x31
                return [
                    0xA0, 0x07, 0x00, 0x80, 0x31, 0x01, 0x2C, 0x01, 0x90,
                    0xC1, 0x04, 0xDE, 0xAD, 0xBE, 0xEF,
                    0xD1, 0x02, 0x01, 0x20,
                    0x90, 0x00
                ]
            }
        }

        // 2. PIN Verification: 00 20 00 01 04 20 09 12 10
        if cla == 0x00 && ins == 0x20 {
            if mode == Self.modePinFail {
                return [0x69, 0x82]
            }
            let expectedPin: [UInt8] = [0x00, 0x20, 0x00, 0x01, 0x04, 0x20, 0x09, 0x12, 0x10]
            if command != expectedPin {
                return [0x63, 0x00]
            }
            pinVerified = true
            return [0x90, 0x00]
        }

        // 3. Compressed Image Write: F0 D3 ...
        if cla == 0xF0 && ins == 0xD3 {
            writePacketCounter += 1
            if mode == Self.modeTagLostMidWrite && writePacketCounter >= 5 {
                connected = false
                throw TapInkIOError("Tag was lost (RF field disconnected at packet #\(writePacketCounter))")
            }
            if command.count < 7 {
                return [0x67, 0x00]
            }
            let packetIndex = Int(command[5])
            let payload = Array(command[7..<command.count])
            var existing = receivedCompressedBlocks[packetIndex] ?? []
            existing.append(contentsOf: payload)
            receivedCompressedBlocks[packetIndex] = existing
            return [0x90, 0x00]
        }

        // 4. Uncompressed Image Write: F0 D2 ...
        if cla == 0xF0 && ins == 0xD2 {
            writePacketCounter += 1
            if mode == Self.modeTagLostMidWrite && writePacketCounter >= 5 {
                connected = false
                throw TapInkIOError("Tag was lost (RF field disconnected at packet #\(writePacketCounter))")
            }
            return [0x90, 0x00]
        }

        // 5. Trigger EPD Refresh: F0 D4 <05|85> 80 00
        if cla == 0xF0 && ins == 0xD4 {
            refreshTriggered = true
            if command.count >= 3 {
                lastRefreshParam = command[2]
            }
            return [0x90, 0x00]
        }

        // 6. Busy Status Check: F0 DE 00 00 01
        if cla == 0xF0 && ins == 0xDE {
            busyPollCounter += 1
            if mode == Self.modeRefreshDisconnect {
                connected = false
                throw TapInkIOError("Tag lost during e-paper physical refresh")
            }
            if busyPollCounter <= busyPollCountBeforeIdle {
                return [0x01, 0x90, 0x00] // 0x01 = Busy
            } else {
                return [0x00, 0x90, 0x00] // 0x00 = Idle (Completed)
            }
        }

        return [0x6D, 0x00]
    }

    public func close() {
        connected = false
    }

    public func reconstructDecompressedBuffer() throws -> [UInt8] {
        var out: [UInt8] = []
        let sortedKeys = receivedCompressedBlocks.keys.sorted()
        for k in sortedKeys {
            if let compressed = receivedCompressedBlocks[k] {
                let decompressed = try Lzo1x.decompress(compressed)
                out.append(contentsOf: decompressed)
            }
        }
        return out
    }
}

/// ISO 14443-4 / ISO 7816 APDU state machine for FMSH / Santek EZ Sign / GoodDisplay passive NFC E-Paper tags.
/// 100% compatible with Android `TapInkProtocol.kt`.
public enum TapInkProtocol {

    public static let configInfoCmd: [UInt8] = [0x00, 0xD1, 0x00, 0x00]
    public static let pinVerifyCmd: [UInt8] = [0x00, 0x20, 0x00, 0x01, 0x04, 0x20, 0x09, 0x12, 0x10]
    public static let busyStatusCmd: [UInt8] = [0xF0, 0xDE, 0x00, 0x00, 0x01]

    private static let knownSw1: Set<Int> = [0x90, 0x62, 0x64, 0x65, 0x67, 0x68, 0x69, 0x6A, 0x6C]

    public struct HardwareConfig: Equatable, Sendable {
        public let width: Int
        public let height: Int
        public let colorCode: Int
        public let colorMode: FloydSteinbergDitherer.ColorMode
        public let compressionSupported: Bool
        public let uidHex: String

        public init(
            width: Int,
            height: Int,
            colorCode: Int,
            colorMode: FloydSteinbergDitherer.ColorMode,
            compressionSupported: Bool,
            uidHex: String
        ) {
            self.width = width
            self.height = height
            self.colorCode = colorCode
            self.colorMode = colorMode
            self.compressionSupported = compressionSupported
            self.uidHex = uidHex
        }

        public var normWidth: Int { max(width, height) }
        public var normHeight: Int { min(width, height) }

        public var modelSummary: String {
            let inchStr: String
            if normWidth == 400 && normHeight == 300 {
                inchStr = "4.2吋"
            } else if normWidth == 296 && normHeight == 128 {
                inchStr = "2.9吋"
            } else if normWidth == 250 && normHeight == 122 {
                inchStr = "2.13吋"
            } else if normWidth == 416 && normHeight == 240 {
                inchStr = "3.7吋"
            } else if normWidth == 200 && normHeight == 200 {
                inchStr = "1.54吋"
            } else if normWidth == 600 && normHeight == 400 {
                inchStr = "3.6吋"
            } else {
                inchStr = "\(normWidth)x\(normHeight)"
            }
            return "\(inchStr) \(normWidth)×\(normHeight) \(colorMode.label)"
        }
    }

    public static func bytesToHex(_ bytes: [UInt8]) -> String {
        return bytes.map { String(format: "%02X", $0) }.joined()
    }

    private static func verifyStatusWord(_ response: [UInt8], contextName: String) throws {
        if response.count >= 2 {
            let sw1 = Int(response[response.count - 2])
            let sw2 = Int(response[response.count - 1])
            if knownSw1.contains(sw1) {
                if sw1 != 0x90 || sw2 != 0x00 {
                    let swHex = String(format: "%02X%02X", sw1, sw2)
                    throw TapInkProtocolError(message: "\(contextName) 失敗，APDU 狀態碼異常 (SW=\(swHex))")
                }
            }
        }
    }

    public static func parseHardwareConfig(_ rawResponse: [UInt8]) throws -> HardwareConfig {
        try verifyStatusWord(rawResponse, contextName: "讀取硬體配置 (00D10000)")
        var bytes = rawResponse
        if bytes.count >= 2 && knownSw1.contains(Int(bytes[bytes.count - 2])) {
            bytes = Array(bytes[0..<(bytes.count - 2)])
        }
        if bytes.isEmpty {
            throw TapInkProtocolError(message: "讀取硬體配置 (00D10000) 回傳空資料")
        }

        var rawWidth = -1
        var rawHeight = -1
        var colorCode = -1
        var hasExtendedColorTag = false
        var compressionSupported = true
        var uidHex = "UNKNOWN"

        var i = 0
        while i < bytes.count {
            if i + 2 > bytes.count {
                throw TapInkProtocolError(message: "TLV 格式損毀 (offset=\(i))")
            }
            let tag = Int(bytes[i])
            let len = Int(bytes[i + 1])
            let start = i + 2
            let end = start + len
            if end > bytes.count {
                throw TapInkProtocolError(message: String(format: "TLV 長度溢位 (tag=0x%02X, len=%d)", tag, len))
            }
            let value = Array(bytes[start..<end])
            switch tag {
            case 0xA0:
                if value.count != 7 {
                    throw TapInkProtocolError(message: "Tag 0xA0 長度錯誤 (預期 7 bytes, 實得 \(value.count))")
                }
                colorCode = Int(value[2])
                rawHeight = (Int(value[3]) << 8) | Int(value[4])
                rawWidth = (Int(value[5]) << 8) | Int(value[6])
            case 0xC1:
                uidHex = bytesToHex(value)
            case 0xD1:
                if !value.isEmpty {
                    compressionSupported = Int(value[0]) != 0x00
                }
            case 0xE0:
                hasExtendedColorTag = true
            default:
                break
            }
            i = end
        }

        if rawWidth <= 0 || rawHeight <= 0 || colorCode < 0 {
            throw TapInkProtocolError(message: "硬體回傳缺少有效螢幕規格 TLV (Tag 0xA0: \(rawWidth)x\(rawHeight))")
        }

        let (resolvedWidth, resolvedHeight, resolvedColorMode) = try resolveFmsHardwareSpec(
            rawWidth: rawWidth,
            rawHeight: rawHeight,
            colorCode: colorCode,
            hasExtendedColorTag: hasExtendedColorTag
        )

        return HardwareConfig(
            width: resolvedWidth,
            height: resolvedHeight,
            colorCode: colorCode,
            colorMode: resolvedColorMode,
            compressionSupported: compressionSupported,
            uidHex: uidHex
        )
    }

    /// FMSH COS v1 (0x10) 4-color BWRY controllers store `colorCode = 0x20` in Tag 0xA0 and double
    /// the height dimension (`rawHeight = actualHeight * 2`).
    private static func resolveFmsHardwareSpec(
        rawWidth: Int,
        rawHeight: Int,
        colorCode: Int,
        hasExtendedColorTag: Bool
    ) throws -> (Int, Int, FloydSteinbergDitherer.ColorMode) {
        if colorCode == 0x20 && !hasExtendedColorTag {
            let d1 = rawHeight
            let d2 = rawWidth
            if (d1 == 600 && d2 == 400) || (d1 == 400 && d2 == 600) {
                return (400, 300, .bwry4Color)
            }
            if (d1 == 400 && d2 == 200) || (d1 == 200 && d2 == 400) {
                return (200, 200, .bwry4Color)
            }
            if (d1 == 256 && d2 == 296) || (d1 == 296 && d2 == 256) {
                return (296, 128, .bwry4Color)
            }
            if (d1 == 336 && d2 == 384) || (d1 == 384 && d2 == 336) {
                return (384, 168, .bwry4Color)
            }
            if (d1 == 336 && d2 == 400) || (d1 == 400 && d2 == 336) {
                return (400, 168, .bwry4Color)
            }
            if (d2 == 250 && (d1 == 244 || d1 == 248 || d1 == 256)) ||
               (d1 == 250 && (d2 == 244 || d2 == 248 || d2 == 256))
            {
                return (250, 122, .bwry4Color)
            }
            if (d1 == 304 && d2 == 296) || (d1 == 296 && d2 == 304) {
                return (296, 152, .bwry4Color)
            }
            if (d1 == 368 && d2 == 360) || (d1 == 360 && d2 == 368) {
                return (360, 184, .bwry4Color)
            }
            if (d1 == 480 && d2 == 416) || (d1 == 416 && d2 == 480) {
                return (416, 240, .bwry4Color)
            }
        }

        let colorMode = try FloydSteinbergDitherer.ColorMode.fromColorCode(colorCode)
        return (rawWidth, rawHeight, colorMode)
    }

    public static func buildRefreshCmd(_ colorMode: FloydSteinbergDitherer.ColorMode) -> [UInt8] {
        return [
            0xF0,
            0xD4,
            colorMode.refreshCmdParam,
            0x80,
            0x00
        ]
    }

    @inline(__always)
    private static func currentTimeMillis() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000.0)
    }

    @inline(__always)
    private static func sleepMillis(_ ms: Int64) {
        if ms > 0 {
            Thread.sleep(forTimeInterval: Double(ms) / 1000.0)
        }
    }

    /// Dynamically queries the NFC e-paper tag's HardwareConfig (`00D10000`), invokes `imageProvider`
    /// to dither/pack the image for the exact detected resolution and color mode, and transfers it.
    @discardableResult
    public static func executeDynamicUpdate(
        transceiver: IsoDepTransceiver,
        refreshInitialWaitMs: Int64 = 2000,
        refreshPollIntervalMs: Int64 = 500,
        refreshTimeoutMs: Int64 = 28000,
        imageProvider: (HardwareConfig) throws -> [UInt8],
        onProgress: (_ step: Int, _ totalSteps: Int, _ percent: Int, _ message: String) -> Void
    ) throws -> HardwareConfig {
        do {
            var highestReportedPct = 1
            func reportMonotonic(_ step: Int, _ totalSteps: Int, _ pct: Int, _ msg: String) {
                let clampedInput = min(100, max(1, pct))
                let clamped = max(highestReportedPct, clampedInput)
                highestReportedPct = clamped
                onProgress(step, totalSteps, clamped, msg)
            }

            reportMonotonic(1, 4, 1, "[1/4] 連線 NFC 並自動偵測電子紙硬體規格...")
            try transceiver.connect(timeoutMs: 50000)

            let cfgResp = try transceiver.transceive(command: configInfoCmd)
            let cfg = try parseHardwareConfig(cfgResp)

            reportMonotonic(1, 4, 2, "[1/4] 偵測到 \(cfg.modelSummary)，正在適配 Floyd-Steinberg 影像...")
            let packedHardwareBytes = try imageProvider(cfg)
            let expectedBytes = FloydSteinbergDitherer.expectedPackedByteCount(
                hwWidth: cfg.normWidth,
                hwHeight: cfg.normHeight,
                colorMode: cfg.colorMode
            )
            if packedHardwareBytes.count != expectedBytes {
                throw TapInkProtocolError(
                    message: "影像緩衝區大小不符 (\(cfg.modelSummary)): 預期 \(expectedBytes) bytes, 實得 \(packedHardwareBytes.count) bytes"
                )
            }

            let isMultiColor = cfg.colorMode != .bw2Color
            let pinPct = isMultiColor ? 3 : 8
            let writeStartPct = isMultiColor ? 4 : 12
            let writeSpanPct = isMultiColor ? 10 : 32
            let refreshStartPct = writeStartPct + writeSpanPct + 1

            reportMonotonic(2, 4, pinPct, "[2/4] 驗證裝置 PIN 碼 (\(cfg.modelSummary))...")
            let pinResp = try transceiver.transceive(command: pinVerifyCmd)
            try verifyStatusWord(pinResp, contextName: "PIN 碼驗證 (00200001)")

            reportMonotonic(3, 4, writeStartPct, "[3/4] 寫入 \(cfg.modelSummary) 影像資料...")
            if cfg.compressionSupported {
                try sendCompressedImage(
                    transceiver: transceiver,
                    bytes: packedHardwareBytes,
                    modelSummary: cfg.modelSummary,
                    basePct: writeStartPct,
                    spanPct: writeSpanPct,
                    onProgress: reportMonotonic
                )
            } else {
                try sendUncompressedImage(
                    transceiver: transceiver,
                    bytes: packedHardwareBytes,
                    modelSummary: cfg.modelSummary,
                    basePct: writeStartPct,
                    spanPct: writeSpanPct,
                    onProgress: reportMonotonic
                )
            }

            reportMonotonic(4, 4, refreshStartPct, "[4/4] 觸發 \(cfg.modelSummary) 物理刷新，請緊貼手機勿移動...")
            let refreshResp = try transceiver.transceive(command: buildRefreshCmd(cfg.colorMode))
            try verifyStatusWord(refreshResp, contextName: "觸發螢幕刷新 (F0D4)")

            let effectiveInitialWaitMs: Int64
            if refreshInitialWaitMs >= 1000 && isMultiColor {
                effectiveInitialWaitMs = max(refreshInitialWaitMs, 15000)
            } else {
                effectiveInitialWaitMs = refreshInitialWaitMs
            }

            let expectedRefreshMs: Int64
            if effectiveInitialWaitMs < 1000 {
                expectedRefreshMs = max(effectiveInitialWaitMs * 2, 300)
            } else if isMultiColor {
                expectedRefreshMs = 24000
            } else {
                expectedRefreshMs = 3500
            }

            try waitForRefreshComplete(
                transceiver: transceiver,
                modelSummary: cfg.modelSummary,
                initialWaitMs: effectiveInitialWaitMs,
                pollIntervalMs: refreshPollIntervalMs,
                timeoutMs: max(refreshTimeoutMs, effectiveInitialWaitMs + 15000),
                startPct: refreshStartPct,
                expectedRefreshMs: expectedRefreshMs,
                onProgress: reportMonotonic
            )

            reportMonotonic(4, 4, 100, "\(cfg.modelSummary) 更新完成！")
            return cfg
        } catch let e as TapInkProtocolError {
            throw e
        } catch let e as TapInkIOError {
            throw TapInkProtocolError(message: "NFC 通訊中斷或感應移開 (\(e.message))，請緊貼電子紙重試")
        } catch {
            throw TapInkProtocolError(message: "NFC 協定錯誤: \(error.localizedDescription)")
        }
    }

    private static func sendCompressedImage(
        transceiver: IsoDepTransceiver,
        bytes: [UInt8],
        modelSummary: String,
        basePct: Int,
        spanPct: Int,
        onProgress: (Int, Int, Int, String) -> Void
    ) throws {
        let blockSize = 2000
        let totalBlocks = (bytes.count + blockSize - 1) / blockSize
        let screenIndex = 0

        for blockIdx in 0..<totalBlocks {
            let offset = blockIdx * blockSize
            let len = min(blockSize, bytes.count - offset)
            let slice = Array(bytes[offset..<(offset + len)])
            let compressed = Lzo1x.compress(slice)

            let maxChunkPayload = 250
            var chunkOffset = 0
            var chunkIdx = 0

            while chunkOffset < compressed.count {
                let chunkLen = min(maxChunkPayload, compressed.count - chunkOffset)
                let isLastChunk = (chunkOffset + chunkLen) == compressed.count
                var cmd = [UInt8](repeating: 0, count: chunkLen + 7)
                cmd[0] = 0xF0
                cmd[1] = 0xD3
                cmd[2] = UInt8(truncatingIfNeeded: screenIndex & 0xFF)
                cmd[3] = isLastChunk ? 1 : 0
                cmd[4] = UInt8(truncatingIfNeeded: (chunkLen + 2) & 0xFF)
                cmd[5] = UInt8(truncatingIfNeeded: blockIdx & 0xFF)
                cmd[6] = UInt8(truncatingIfNeeded: chunkIdx & 0xFF)
                for k in 0..<chunkLen {
                    cmd[7 + k] = compressed[chunkOffset + k]
                }

                let resp: [UInt8]
                do {
                    resp = try transceiver.transceive(command: cmd)
                } catch let ioe as TapInkIOError {
                    throw TapInkProtocolError(
                        message: "寫入影像中斷 (第 \(blockIdx + 1)/\(totalBlocks) 區塊感應斷開): \(ioe.message)"
                    )
                } catch {
                    throw TapInkProtocolError(
                        message: "寫入影像中斷 (第 \(blockIdx + 1)/\(totalBlocks) 區塊感應斷開): \(error.localizedDescription)"
                    )
                }
                try verifyStatusWord(resp, contextName: "寫入壓縮區塊 #\(blockIdx + 1)")

                chunkOffset += chunkLen
                chunkIdx += 1
            }

            let pct = basePct + ((blockIdx + 1) * spanPct / totalBlocks)
            onProgress(3, 4, pct, "[3/4] 寫入 \(modelSummary) 壓縮區塊 (\(blockIdx + 1) / \(totalBlocks))...")
        }
    }

    private static func sendUncompressedImage(
        transceiver: IsoDepTransceiver,
        bytes: [UInt8],
        modelSummary: String,
        basePct: Int,
        spanPct: Int,
        onProgress: (Int, Int, Int, String) -> Void
    ) throws {
        let payloadSize = 250
        let totalPackets = (bytes.count + payloadSize - 1) / payloadSize
        let screenIndex = 0

        for pktIdx in 0..<totalPackets {
            let offset = pktIdx * payloadSize
            let currentLen = min(payloadSize, bytes.count - offset)
            var cmd = [UInt8](repeating: 0, count: payloadSize + 5)
            if currentLen < payloadSize {
                for k in 5..<cmd.count { cmd[k] = 0x01 }
            }
            cmd[0] = 0xF0
            cmd[1] = 0xD2
            cmd[2] = UInt8(truncatingIfNeeded: screenIndex & 0xFF)
            cmd[3] = UInt8(truncatingIfNeeded: pktIdx & 0xFF)
            cmd[4] = 0xFA
            for k in 0..<currentLen {
                cmd[5 + k] = bytes[offset + k]
            }

            let resp: [UInt8]
            do {
                resp = try transceiver.transceive(command: cmd)
            } catch let ioe as TapInkIOError {
                throw TapInkProtocolError(
                    message: "寫入未壓縮影像中斷 (第 \(pktIdx + 1)/\(totalPackets) 包斷開): \(ioe.message)"
                )
            } catch {
                throw TapInkProtocolError(
                    message: "寫入未壓縮影像中斷 (第 \(pktIdx + 1)/\(totalPackets) 包斷開): \(error.localizedDescription)"
                )
            }
            try verifyStatusWord(resp, contextName: "寫入影像封包 #\(pktIdx + 1)")

            let pct = basePct + ((pktIdx + 1) * spanPct / totalPackets)
            onProgress(3, 4, pct, "[3/4] 寫入 \(modelSummary) 影像封包 (\(pktIdx + 1) / \(totalPackets))...")
        }
    }

    private static func waitForRefreshComplete(
        transceiver: IsoDepTransceiver,
        modelSummary: String,
        initialWaitMs: Int64,
        pollIntervalMs: Int64,
        timeoutMs: Int64,
        startPct: Int,
        expectedRefreshMs: Int64,
        onProgress: (Int, Int, Int, String) -> Void
    ) throws {
        let startTime = currentTimeMillis()
        let spanPct = max(1, 99 - startPct)
        let denomMs = Double(max(1, expectedRefreshMs))

        func computeUnifiedPct(_ elapsedMs: Int64) -> Int {
            let ratio = min(1.0, max(0.0, Double(elapsedMs) / denomMs))
            return min(99, max(startPct, startPct + Int(ratio * Double(spanPct))))
        }

        if initialWaitMs > 0 {
            if initialWaitMs >= 1000 {
                let stepSleep: Int64 = 250
                while currentTimeMillis() - startTime < initialWaitMs {
                    if !transceiver.isConnected() {
                        throw TapInkProtocolError(message: "電子紙刷新期間 NFC 供電中斷，畫面可能未完整更新，請重試")
                    }
                    let elapsedMs = currentTimeMillis() - startTime
                    let elapsedSec = Double(elapsedMs) / 1000.0
                    let pct = computeUnifiedPct(elapsedMs)
                    onProgress(
                        4,
                        4,
                        pct,
                        String(format: "[4/4] %@ 刷新供電中 (已耗時 %.1f 秒)，請保持緊貼...", modelSummary, elapsedSec)
                    )
                    sleepMillis(min(stepSleep, max(1, initialWaitMs - elapsedMs)))
                }
            } else {
                sleepMillis(initialWaitMs)
            }
        }

        while currentTimeMillis() - startTime < timeoutMs {
            if !transceiver.isConnected() {
                throw TapInkProtocolError(message: "電子紙刷新期間 NFC 供電中斷，畫面可能未完整更新，請重試")
            }
            let resp: [UInt8]
            do {
                resp = try transceiver.transceive(command: busyStatusCmd)
            } catch let ioe as TapInkIOError {
                throw TapInkProtocolError(message: "電子紙刷新期間讀取狀態斷線: \(ioe.message)")
            } catch {
                throw TapInkProtocolError(message: "電子紙刷新期間讀取狀態斷線: \(error.localizedDescription)")
            }

            if resp.isEmpty {
                throw TapInkProtocolError(message: "電子紙刷新狀態回傳空值 (F0DE000001)")
            }

            let busyFlag = Int(resp[0])
            if busyFlag == 0x00 {
                return
            }

            let elapsedMs = currentTimeMillis() - startTime
            let elapsedSec = Double(elapsedMs) / 1000.0
            let pct = computeUnifiedPct(elapsedMs)
            onProgress(
                4,
                4,
                pct,
                String(format: "[4/4] %@ 刷新供電中 (已耗時 %.1f 秒)，請保持緊貼...", modelSummary, elapsedSec)
            )
            if pollIntervalMs > 0 {
                sleepMillis(pollIntervalMs)
            }
        }

        throw TapInkProtocolError(message: "電子紙刷新逾時 (\(timeoutMs / 1000)秒未回傳 0x00 完成訊號)")
    }
}
