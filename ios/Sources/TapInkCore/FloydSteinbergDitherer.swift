import Foundation

/// Dynamic Floyd-Steinberg error-diffusion dithering and hardware bit-packing
/// 100% algorithmically and byte-for-byte compatible with Android `FloydSteinbergDitherer.kt`.
public enum FloydSteinbergDitherer {

    public enum ColorMode: Equatable, Sendable {
        case bw2Color
        case bwr3Color
        case bwry4Color
        case spectra6Color

        public var code: Int {
            switch self {
            case .bw2Color: return 0x20
            case .bwr3Color: return 0x30
            case .bwry4Color: return 0x31
            case .spectra6Color: return 0x60
            }
        }

        public var label: String {
            switch self {
            case .bw2Color: return "2色 (黑白 BW)"
            case .bwr3Color: return "3色 (黑白紅 BWR)"
            case .bwry4Color: return "4色 (黑白黃紅 BWRY)"
            case .spectra6Color: return "6色 (Spectra 6)"
            }
        }

        public var paletteRgb: [[Int]] {
            switch self {
            case .bw2Color:
                return [
                    [0, 0, 0],       // 0: Black
                    [255, 255, 255]  // 1: White
                ]
            case .bwr3Color:
                return [
                    [0, 0, 0],       // 0: Black
                    [255, 255, 255], // 1: White
                    [255, 0, 0]      // 2: Red
                ]
            case .bwry4Color:
                return [
                    [0, 0, 0],       // 0: Black (00b)
                    [255, 255, 255], // 1: White (01b)
                    [255, 255, 0],   // 2: Yellow (10b) — quantization vertex (0x6c18a8)
                    [255, 0, 0]      // 3: Red (11b)
                ]
            case .spectra6Color:
                return [
                    [0, 0, 0],       // 0: Black
                    [255, 255, 255], // 1: White
                    [255, 0, 0],     // 2: Red
                    [255, 255, 0],   // 3: Yellow
                    [0, 255, 0],     // 4: Green
                    [0, 0, 255]      // 5: Blue
                ]
            }
        }

        public var paletteArgb: [UInt32] {
            switch self {
            case .bw2Color:
                return [
                    0xFF000000,
                    0xFFFFFFFF
                ]
            case .bwr3Color:
                return [
                    0xFF000000,
                    0xFFFFFFFF,
                    0xFFFF0000
                ]
            case .bwry4Color:
                return [
                    0xFF000000,
                    0xFFFFFFFF,
                    0xFFFFC000,      // Official EZ Sign BMP preview yellow #FFC000 RGB(255,192,0)
                    0xFFFF0000
                ]
            case .spectra6Color:
                return [
                    0xFF000000,
                    0xFFFFFFFF,
                    0xFFFF0000,
                    0xFFFFC000,      // Official EZ Sign BMP preview yellow #FFC000 RGB(255,192,0)
                    0xFF00FF00,
                    0xFF0000FF
                ]
            }
        }

        public var refreshCmdParam: UInt8 {
            switch self {
            case .bw2Color:
                return 0x05
            case .bwr3Color, .bwry4Color, .spectra6Color:
                return 0x85
            }
        }

        public static func fromColorCode(_ colorByte: Int) throws -> ColorMode {
            let b = colorByte & 0xFF
            if b == 0x20 { return .bw2Color }
            if b == 0x30 { return .bwr3Color }
            if b == 0x31 { return .bwry4Color }
            if (b >> 4) == 6 { return .spectra6Color }
            throw TapInkProtocolError(message: String(format: "不支援的硬體色碼: 0x%02X", b))
        }
    }

    public struct DitherResult: Equatable, Sendable {
        public let width: Int
        public let height: Int
        public let hwWidth: Int
        public let hwHeight: Int
        public let colorMode: ColorMode
        public let ditheredArgb: [UInt32]
        public let paletteIndices: [UInt8]
        public let packedHardwareBytes: [UInt8]

        public init(
            width: Int,
            height: Int,
            hwWidth: Int,
            hwHeight: Int,
            colorMode: ColorMode,
            ditheredArgb: [UInt32],
            paletteIndices: [UInt8],
            packedHardwareBytes: [UInt8]
        ) {
            self.width = width
            self.height = height
            self.hwWidth = hwWidth
            self.hwHeight = hwHeight
            self.colorMode = colorMode
            self.ditheredArgb = ditheredArgb
            self.paletteIndices = paletteIndices
            self.packedHardwareBytes = packedHardwareBytes
        }
    }

    @inline(__always)
    public static func nearestPaletteIndex(_ paletteRgb: [[Int]], _ r: Int, _ g: Int, _ b: Int) -> Int {
        var bestDist = Int.max
        var bestIdx = 0
        for i in 0..<paletteRgb.count {
            let p = paletteRgb[i]
            let dr = r - p[0]
            let dg = g - p[1]
            let db = b - p[2]
            let dist = dr * dr + dg * dg + db * db
            if dist < bestDist {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    @inline(__always)
    private static func clampChannelInt(_ v: Int) -> Int {
        if v < 0 { return 0 }
        if v > 255 { return 255 }
        return v
    }

    @inline(__always)
    public static func attenuateError(_ rawErr: Int) -> Int {
        let absErr = abs(rawErr)
        if absErr < 64 {
            return 0
        } else if absErr < 128 {
            return rawErr / 2
        } else {
            return rawErr
        }
    }

    /// Determines hardware scan geometry (rotate / flipHorizontal / flipVertical)
    /// based on the detected screen dimensions and color mode (matching EzSign & GoodDisplay specs).
    public static func resolveTransform(
        hwWidth: Int,
        hwHeight: Int,
        colorMode: ColorMode
    ) -> (rotate: Bool, flipHorizontal: Bool, flipVertical: Bool) {
        let is42Inch4Color = (hwWidth == 400 && hwHeight == 300) && colorMode == .bwry4Color
        let rotate = is42Inch4Color
        let flipHorizontal = true
        let flipVertical = false
        return (rotate, flipHorizontal, flipVertical)
    }

    /// Expected packed byte count for a given hardware resolution and transform.
    public static func expectedPackedByteCount(
        hwWidth: Int,
        hwHeight: Int,
        colorMode: ColorMode
    ) -> Int {
        let (rotate, _, _) = resolveTransform(hwWidth: hwWidth, hwHeight: hwHeight, colorMode: colorMode)
        let destW = rotate ? hwHeight : hwWidth
        let destH = rotate ? hwWidth : hwHeight
        let bytesPerCol = (destH + 3) / 4
        return destW * bytesPerCol
    }

    /// Pure Swift Floyd-Steinberg error-diffusion dithering and hardware packing for any resolution & ColorMode.
    public static func ditherAndPackPixels(
        width: Int,
        height: Int,
        argbPixels: [UInt32],
        colorMode: ColorMode = .bwry4Color,
        hwWidth: Int? = nil,
        hwHeight: Int? = nil
    ) -> DitherResult {
        let resolvedHwW = hwWidth ?? max(width, height)
        let resolvedHwH = hwHeight ?? min(width, height)

        precondition(width > 0 && height > 0, "Invalid dimensions: \(width)x\(height)")
        precondition(argbPixels.count == width * height, "Pixel array size (\(argbPixels.count)) does not match \(width)x\(height)")
        precondition(
            (width == resolvedHwW && height == resolvedHwH) || (width == resolvedHwH && height == resolvedHwW),
            "Image dimensions \(width)x\(height) do not match hardware dimensions \(resolvedHwW)x\(resolvedHwH)"
        )

        let total = width * height
        var chR = [Int](repeating: 0, count: total)
        var chG = [Int](repeating: 0, count: total)
        var chB = [Int](repeating: 0, count: total)

        for i in 0..<total {
            let c = argbPixels[i]
            let a = Int((c >> 24) & 0xFF)
            let r = Int((c >> 16) & 0xFF)
            let g = Int((c >> 8) & 0xFF)
            let b = Int(c & 0xFF)
            if a < 255 {
                let alpha = Float(a) / 255.0
                chR[i] = clampChannelInt(Int(Float(r) * alpha + 255.0 * (1.0 - alpha) + 0.5))
                chG[i] = clampChannelInt(Int(Float(g) * alpha + 255.0 * (1.0 - alpha) + 0.5))
                chB[i] = clampChannelInt(Int(Float(b) * alpha + 255.0 * (1.0 - alpha) + 0.5))
            } else {
                chR[i] = r
                chG[i] = g
                chB[i] = b
            }
        }

        var outArgb = [UInt32](repeating: 0, count: total)
        var outIndices = [UInt8](repeating: 0, count: total)
        let paletteRgb = colorMode.paletteRgb
        let paletteArgb = colorMode.paletteArgb

        for y in 0..<height {
            for x in 0..<width {
                let idx = y * width + x
                let oldR = clampChannelInt(chR[idx])
                let oldG = clampChannelInt(chG[idx])
                let oldB = clampChannelInt(chB[idx])

                let palIdx = nearestPaletteIndex(paletteRgb, oldR, oldG, oldB)
                outIndices[idx] = UInt8(truncatingIfNeeded: palIdx)
                outArgb[idx] = paletteArgb[palIdx]

                let p = paletteRgb[palIdx]
                // Matching Kotlin `shr 2` arithmetic right shift on signed Int
                let deltaR = attenuateError(oldR - p[0]) >> 2
                let deltaG = attenuateError(oldG - p[1]) >> 2
                let deltaB = attenuateError(oldB - p[2]) >> 2

                if deltaR != 0 || deltaG != 0 || deltaB != 0 {
                    if x + 1 < width {
                        let rIdx = idx + 1
                        chR[rIdx] = clampChannelInt(chR[rIdx] + deltaR)
                        chG[rIdx] = clampChannelInt(chG[rIdx] + deltaG)
                        chB[rIdx] = clampChannelInt(chB[rIdx] + deltaB)
                    }
                    if y + 1 < height {
                        if x - 1 >= 0 {
                            let blIdx = (y + 1) * width + (x - 1)
                            chR[blIdx] = clampChannelInt(chR[blIdx] + deltaR)
                            chG[blIdx] = clampChannelInt(chG[blIdx] + deltaG)
                            chB[blIdx] = clampChannelInt(chB[blIdx] + deltaB)
                        }
                        let bIdx = (y + 1) * width + x
                        chR[bIdx] = clampChannelInt(chR[bIdx] + deltaR)
                        chG[bIdx] = clampChannelInt(chG[bIdx] + deltaG)
                        chB[bIdx] = clampChannelInt(chB[bIdx] + deltaB)

                        if x + 1 < width {
                            let brIdx = (y + 1) * width + (x + 1)
                            chR[brIdx] = clampChannelInt(chR[brIdx] + deltaR)
                            chG[brIdx] = clampChannelInt(chG[brIdx] + deltaG)
                            chB[brIdx] = clampChannelInt(chB[brIdx] + deltaB)
                        }
                    }
                }
            }
        }

        let normalizedIndices: [UInt8]
        if width != resolvedHwW || height != resolvedHwH {
            var rotated = [UInt8](repeating: 0, count: resolvedHwW * resolvedHwH)
            for y in 0..<height {
                for x in 0..<width {
                    let dstX = resolvedHwW - 1 - y
                    let dstY = x
                    rotated[dstY * resolvedHwW + dstX] = outIndices[y * width + x]
                }
            }
            normalizedIndices = rotated
        } else {
            normalizedIndices = outIndices
        }

        let (rotate, flipH, flipV) = resolveTransform(hwWidth: resolvedHwW, hwHeight: resolvedHwH, colorMode: colorMode)
        let packed = packToHardwareBuffer(
            landscapeIndices: normalizedIndices,
            srcWidth: resolvedHwW,
            srcHeight: resolvedHwH,
            rotate: rotate,
            flipHorizontal: flipH,
            flipVertical: flipV
        )

        return DitherResult(
            width: width,
            height: height,
            hwWidth: resolvedHwW,
            hwHeight: resolvedHwH,
            colorMode: colorMode,
            ditheredArgb: outArgb,
            paletteIndices: outIndices,
            packedHardwareBytes: packed
        )
    }

    /// Packs 2-bit-per-pixel FMSC e-paper buffer (4 pixels per byte, vertical columns of 4 pixels).
    public static func packToHardwareBuffer(
        landscapeIndices: [UInt8],
        srcWidth: Int,
        srcHeight: Int,
        rotate: Bool,
        flipHorizontal: Bool,
        flipVertical: Bool
    ) -> [UInt8] {
        let destWidth = rotate ? srcHeight : srcWidth
        let destHeight = rotate ? srcWidth : srcHeight
        let bytesPerColumn = (destHeight + 3) / 4
        var imageBuffer = [UInt8](repeating: 0, count: destWidth * bytesPerColumn)

        var index = 0
        for i in stride(from: destWidth - 1, through: 0, by: -1) {
            for j in 0..<bytesPerColumn {
                var temp = 0
                for k in 0..<4 {
                    temp = temp << 2
                    let destY = j * 4 + k
                    let destX = i

                    let palIdx: Int
                    if destY >= destHeight {
                        palIdx = 1 // Pad with hardware White (01b) when height is not divisible by 4 (e.g. 250x122)
                    } else {
                        var srcX = destX
                        var srcY = destY

                        if rotate {
                            srcX = destY
                            srcY = srcHeight - 1 - destX
                        }
                        if flipVertical {
                            srcY = srcHeight - 1 - srcY
                        }
                        if flipHorizontal {
                            srcX = srcWidth - 1 - srcX
                        }
                        palIdx = Int(landscapeIndices[srcY * srcWidth + srcX]) & 0x03
                    }
                    temp = temp | palIdx
                }
                imageBuffer[index] = UInt8(truncatingIfNeeded: temp)
                index += 1
            }
        }
        return imageBuffer
    }
}
