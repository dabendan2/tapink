import XCTest
@testable import TapInkCore

final class TapInkCoreTests: XCTestCase {

    // 1. LZO1X Compression & Decompression Round-Trip
    func testLzo1xCompressionRoundTrip() throws {
        let uniform = [UInt8](repeating: 0x55, count: 2000)
        let compUniform = Lzo1x.compress(uniform)
        XCTAssertTrue(compUniform.count < 100, "Uniform 2000B block should compress under 100B, got \(compUniform.count)")
        let decompUniform = try Lzo1x.decompress(compUniform, expectedOutputLength: uniform.count)
        XCTAssertEqual(uniform, decompUniform, "Uniform round-trip mismatch")

        let checker = (0..<2000).map { i -> UInt8 in (i % 2 == 0) ? 0x1B : 0xE4 }
        let compChecker = Lzo1x.compress(checker)
        let decompChecker = try Lzo1x.decompress(compChecker, expectedOutputLength: checker.count)
        XCTAssertEqual(checker, decompChecker, "Checkerboard round-trip mismatch")

        // Deterministic PRNG sequence (LCG seed=42) for noisy 2000B block
        var state: UInt64 = 42
        let noisy = (0..<2000).map { _ -> UInt8 in
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return UInt8(truncatingIfNeeded: state >> 33)
        }
        let compNoisy = Lzo1x.compress(noisy)
        let decompNoisy = try Lzo1x.decompress(compNoisy, expectedOutputLength: noisy.count)
        XCTAssertEqual(noisy, decompNoisy, "Noisy 2000B round-trip mismatch")
    }

    // 2. Multi-Spec Floyd-Steinberg Dithering (4.2" BWRY, 2.9" BW, 2.13" BWRY)
    func testFloydSteinbergMultiSpecDithering() {
        // 1. 4.2" 4-Color (300x400 BWRY) -> 30,000 bytes
        let whitePixels = [UInt32](repeating: 0xFFFFFFFF, count: 300 * 400)
        let whiteRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width: 300,
            height: 400,
            argbPixels: whitePixels,
            colorMode: .bwry4Color,
            hwWidth: 400,
            hwHeight: 300
        )
        XCTAssertEqual(30000, whiteRes.packedHardwareBytes.count, "4.2\" packed size must be 30,000 bytes")
        XCTAssertTrue(whiteRes.packedHardwareBytes.allSatisfy { $0 == 0x55 }, "Solid white must pack to 0x55")

        // 2. 2.9" 2-Color (296x128 BW) -> 296 * (128/4) = 9,472 bytes
        let gray290 = [UInt32](repeating: 0xFF808080, count: 296 * 128)
        let bw290Res = FloydSteinbergDitherer.ditherAndPackPixels(
            width: 296,
            height: 128,
            argbPixels: gray290,
            colorMode: .bw2Color,
            hwWidth: 296,
            hwHeight: 128
        )
        XCTAssertEqual(9472, bw290Res.packedHardwareBytes.count, "2.9\" packed size must be 9,472 bytes")
        XCTAssertTrue(
            bw290Res.paletteIndices.allSatisfy { $0 == 0 || $0 == 1 },
            "2-color BW mode must only emit palette indices 0 (Black) and 1 (White)"
        )

        // 3. 2.13" 4-Color (250x122 BWRY) -> 250 * ceil(122/4) = 250 * 31 = 7,750 bytes
        let orange213 = [UInt32](repeating: 0xFFFF8000, count: 250 * 122)
        let bwry213Res = FloydSteinbergDitherer.ditherAndPackPixels(
            width: 250,
            height: 122,
            argbPixels: orange213,
            colorMode: .bwry4Color,
            hwWidth: 250,
            hwHeight: 122
        )
        XCTAssertEqual(7750, bwry213Res.packedHardwareBytes.count, "2.13\" packed size must be 7,750 bytes")

        // 4. Official EZ Sign algorithm parity:
        //    a) Warm cream parchment background RGB(255, 244, 216) must stay 100% pure White (index 1) without yellow speckles
        let creamPixels = [UInt32](repeating: 0xFFFFF4D8, count: 400 * 300)
        let creamRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width: 400,
            height: 300,
            argbPixels: creamPixels,
            colorMode: .bwry4Color,
            hwWidth: 400,
            hwHeight: 300
        )
        XCTAssertTrue(
            creamRes.paletteIndices.allSatisfy { $0 == 1 },
            "Warm cream background RGB(255,244,216) must stay 100% pure White (index 1)"
        )

        //    b) Neutral gray RGB(128, 128, 128) in 4-color BWRY must only use Black (0) and White (1), never Red/Yellow
        let neutralGray400 = [UInt32](repeating: 0xFF808080, count: 400 * 300)
        let gray4ColorRes = FloydSteinbergDitherer.ditherAndPackPixels(
            width: 400,
            height: 300,
            argbPixels: neutralGray400,
            colorMode: .bwry4Color,
            hwWidth: 400,
            hwHeight: 300
        )
        XCTAssertTrue(
            gray4ColorRes.paletteIndices.allSatisfy { $0 == 0 || $0 == 1 },
            "Neutral gray in 4-color BWRY must only emit Black (0) and White (1)"
        )

        //    c) Dual-palette Yellow: quantization uses (255, 255, 0) while preview ARGB uses #FFC000 (0xFFFFC000)
        XCTAssertEqual(
            [255, 255, 0],
            FloydSteinbergDitherer.ColorMode.bwry4Color.paletteRgb[2],
            "BWRY quantization yellow must be (255, 255, 0)"
        )
        XCTAssertEqual(
            0xFFFFC000,
            FloydSteinbergDitherer.ColorMode.bwry4Color.paletteArgb[2],
            "BWRY preview ARGB yellow must be 0xFFFFC000 (#FFC000)"
        )
    }

    // 3. Dynamic Auto-Detection & Transfer: 4.2" 400x300 4-Color (0x31)
    func testDynamicProtocolAutoDetect420BWRY() throws {
        let sim = SimulatedIsoDepTransceiver(mode: SimulatedIsoDepTransceiver.modeSuccess420BWRY, busyPollCountBeforeIdle: 3)
        var providedBytes: [UInt8]?
        var progressHistory: [Int] = []

        let cfg = try TapInkProtocol.executeDynamicUpdate(
            transceiver: sim,
            refreshInitialWaitMs: 50,
            refreshPollIntervalMs: 20,
            imageProvider: { hwCfg in
                let pixels = [UInt32](repeating: 0xFFFF8000, count: hwCfg.normWidth * hwCfg.normHeight)
                let res = FloydSteinbergDitherer.ditherAndPackPixels(
                    width: hwCfg.normWidth,
                    height: hwCfg.normHeight,
                    argbPixels: pixels,
                    colorMode: hwCfg.colorMode,
                    hwWidth: hwCfg.normWidth,
                    hwHeight: hwCfg.normHeight
                )
                providedBytes = res.packedHardwareBytes
                return res.packedHardwareBytes
            },
            onProgress: { _, _, pct, _ in
                progressHistory.append(pct)
            }
        )

        XCTAssertEqual(400, cfg.normWidth, "Auto-detected width must be 400")
        XCTAssertEqual(300, cfg.normHeight, "Auto-detected height must be 300")
        XCTAssertEqual(.bwry4Color, cfg.colorMode, "Auto-detected color mode must be BWRY_4COLOR")
        XCTAssertEqual(0x85, sim.lastRefreshParam, "4-color refresh param must be 0x85")
        XCTAssertEqual(15, sim.receivedCompressedBlocks.count, "30,000B / 2,000B = 15 blocks")
        let reconstructed = try sim.reconstructDecompressedBuffer()
        XCTAssertEqual(providedBytes, reconstructed, "Decompressed APDU payload must match")
        XCTAssertTrue(!progressHistory.isEmpty && progressHistory.first == 1 && progressHistory.last == 100, "Progress must start at 1% and end at 100%: \(progressHistory)")
        for i in 1..<progressHistory.count {
            XCTAssertTrue(
                progressHistory[i] >= progressHistory[i - 1],
                "Progress must never roll back: index \(i) (\(progressHistory[i])% < \(progressHistory[i - 1])%) in \(progressHistory)"
            )
        }
    }

    // 4. Dynamic Auto-Detection & Transfer: 2.9" 296x128 2-Color (0x20)
    func testDynamicProtocolAutoDetect290BW() throws {
        let sim = SimulatedIsoDepTransceiver(mode: SimulatedIsoDepTransceiver.modeSuccess290BW, busyPollCountBeforeIdle: 1)
        var providedBytes: [UInt8]?

        let cfg = try TapInkProtocol.executeDynamicUpdate(
            transceiver: sim,
            refreshInitialWaitMs: 0,
            refreshPollIntervalMs: 0,
            imageProvider: { hwCfg in
                let pixels = [UInt32](repeating: 0xFF808080, count: hwCfg.normWidth * hwCfg.normHeight)
                let res = FloydSteinbergDitherer.ditherAndPackPixels(
                    width: hwCfg.normWidth,
                    height: hwCfg.normHeight,
                    argbPixels: pixels,
                    colorMode: hwCfg.colorMode,
                    hwWidth: hwCfg.normWidth,
                    hwHeight: hwCfg.normHeight
                )
                providedBytes = res.packedHardwareBytes
                return res.packedHardwareBytes
            },
            onProgress: { _, _, _, _ in }
        )

        XCTAssertEqual(296, cfg.normWidth, "Auto-detected width must be 296")
        XCTAssertEqual(128, cfg.normHeight, "Auto-detected height must be 128")
        XCTAssertEqual(.bw2Color, cfg.colorMode, "Auto-detected color mode must be BW_2COLOR")
        XCTAssertEqual(0x05, sim.lastRefreshParam, "2-color BW refresh param must be 0x05")
        XCTAssertEqual(5, sim.receivedCompressedBlocks.count, "9,472B / 2,000B = 5 blocks")
        let reconstructed = try sim.reconstructDecompressedBuffer()
        XCTAssertEqual(providedBytes, reconstructed, "Decompressed 2.9\" payload must match")
    }

    // 5. Fail-Fast on Corrupt/Unsupported Hardware Color Code (0xFF)
    func testProtocolFailFastOnUnsupportedColorCode() {
        let sim = SimulatedIsoDepTransceiver(mode: SimulatedIsoDepTransceiver.modeUnsupportedSpec)
        var caught: TapInkProtocolError?
        do {
            _ = try TapInkProtocol.executeDynamicUpdate(
                transceiver: sim,
                refreshInitialWaitMs: 0,
                refreshPollIntervalMs: 0,
                imageProvider: { _ in [] },
                onProgress: { _, _, _, _ in }
            )
        } catch let e as TapInkProtocolError {
            caught = e
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
        XCTAssertNotNil(caught, "Expected TapInkProtocolError on unsupported color code 0xFF")
        XCTAssertTrue(caught!.message.contains("不支援的硬體色碼"), "Got: \(caught!.message)")
    }

    // 6. Fail-Fast on Mid-Write Tag Lost (RF Disconnect)
    func testProtocolFailFastOnMidWriteTagLost() {
        let sim = SimulatedIsoDepTransceiver(mode: SimulatedIsoDepTransceiver.modeTagLostMidWrite)
        var caught: TapInkProtocolError?
        do {
            _ = try TapInkProtocol.executeDynamicUpdate(
                transceiver: sim,
                refreshInitialWaitMs: 0,
                refreshPollIntervalMs: 0,
                imageProvider: { _ in (0..<30000).map { UInt8($0 % 251) } },
                onProgress: { _, _, _, _ in }
            )
        } catch let e as TapInkProtocolError {
            caught = e
        } catch {
            XCTFail("Unexpected error type: \(error)")
        }
        XCTAssertNotNil(caught, "Expected TapInkProtocolError on mid-write tag lost")
        XCTAssertTrue(caught!.message.contains("感應斷開"), "Got: \(caught!.message)")
        XCTAssertFalse(sim.refreshTriggered, "Must not trigger refresh if write was interrupted")
    }

    // 7. FMSH COS v1 Doubled-Height 4.2" 4-Color BWRY (600x400, 0x20 -> 400x300 BWRY)
    func testFmsCosV1DoubledHeight420BWRYDetection() throws {
        let sim = SimulatedIsoDepTransceiver(mode: SimulatedIsoDepTransceiver.modeSuccess420BWRYCosV1)
        let cfg = try TapInkProtocol.executeDynamicUpdate(
            transceiver: sim,
            refreshInitialWaitMs: 1,
            refreshPollIntervalMs: 1,
            imageProvider: { detected in
                XCTAssertEqual(400, detected.normWidth, "Resolved width should be 400")
                XCTAssertEqual(300, detected.normHeight, "Resolved height should be 300 (600 / 2)")
                XCTAssertEqual(.bwry4Color, detected.colorMode, "Should resolve to BWRY_4COLOR")
                return (0..<30000).map { UInt8(($0 * 13) & 0xFF) }
            },
            onProgress: { _, _, _, _ in }
        )
        XCTAssertEqual("4.2吋 400×300 4色 (黑白黃紅 BWRY)", cfg.modelSummary, "Model summary should match 4.2\" BWRY")
        XCTAssertEqual(0x85, sim.lastRefreshParam, "4-color BWRY must use 0x85 refresh param")
        XCTAssertEqual(15, sim.receivedCompressedBlocks.count, "Must write exact 15 LZO blocks (not 30)")
        let reconstructed = try sim.reconstructDecompressedBuffer()
        XCTAssertEqual(30000, reconstructed.count, "Must write exact 30,000 bytes (15 LZO blocks)")
    }
}
