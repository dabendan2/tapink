import Foundation

public enum Lzo1xError: Error, LocalizedError, Equatable {
    case unexpectedEndOfInput
    case decompressedLengthExceedsExpected
    case invalidLookbehindReference
    case missingEndOfStreamMarker
    case trailingBytesAfterEndOfStream
    case decompressedLengthMismatch

    public var errorDescription: String? {
        switch self {
        case .unexpectedEndOfInput:
            return "lzo1x: unexpected end of input"
        case .decompressedLengthExceedsExpected:
            return "lzo1x: decompressed length exceeds expected"
        case .invalidLookbehindReference:
            return "lzo1x: invalid lookbehind reference"
        case .missingEndOfStreamMarker:
            return "lzo1x: missing end-of-stream marker"
        case .trailingBytesAfterEndOfStream:
            return "lzo1x: trailing bytes after end-of-stream marker"
        case .decompressedLengthMismatch:
            return "lzo1x: decompressed length does not match expectedOutputLength"
        }
    }
}

/// 100% byte-for-byte compatible LZO1X-1 compressor and decompressor matching Android `Lzo1x.kt`.
public enum Lzo1x {
    private static let m1MaxOffset = 1024
    private static let m2MaxOffset = 2048
    private static let m3MaxOffset = 16384
    private static let m4MaxOffset = 49151
    private static let m2MaxLen = 8
    private static let m3MaxLen = 33
    private static let m4MaxLen = 9
    private static let m3Marker = 32
    private static let m4Marker = 16
    private static let hashBits = 13
    private static let hashSize = 1 << hashBits
    private static let hashMask = hashSize - 1

    @inline(__always)
    private static func hash3(_ b: [UInt8], _ p: Int) -> Int {
        let b0 = UInt32(b[p])
        let b1 = UInt32(b[p + 1])
        let b2 = UInt32(b[p + 2])
        let v = b0 | (b1 << 8) | (b2 << 16)
        let prod = v &* 506_832_829
        return Int((prod >> (32 - hashBits)) & UInt32(hashMask))
    }

    public static func compress(_ input: [UInt8]) -> [UInt8] {
        let inLen = input.count
        let outCap = inLen + ((inLen + 15) >> 4) + 67 + 16
        var out = [UInt8](repeating: 0, count: outCap)
        var op = 0
        var litStart = 0
        var ip = 0
        var ht = [Int](repeating: -1, count: hashSize)
        let trailGuard = 20
        let scanEnd = inLen - trailGuard

        func emitLiteralRun(_ len: Int, _ firstFrame2: Bool) {
            if firstFrame2 && len != 0 && len <= 238 {
                out[op] = UInt8(truncatingIfNeeded: 17 + len)
                op += 1
            } else if len <= 3 {
                out[op - 2] = UInt8(truncatingIfNeeded: (Int(out[op - 2]) | len) & 0xFF)
            } else if len <= 18 {
                out[op] = UInt8(truncatingIfNeeded: len - 3)
                op += 1
            } else {
                out[op] = 0
                op += 1
                var rem = len - 18
                while rem > 255 {
                    out[op] = 0
                    op += 1
                    rem -= 255
                }
                out[op] = UInt8(truncatingIfNeeded: rem)
                op += 1
            }
            for i in 0..<len {
                out[op] = input[litStart + i]
                op += 1
            }
        }

        func emitMatch(_ matchLen: Int, _ matchOff: Int, _ litLen: Int) {
            if matchLen == 2 {
                let off = matchOff - 1
                out[op] = UInt8(truncatingIfNeeded: ((off & 3) << 2) & 0xFF)
                op += 1
                out[op] = UInt8(truncatingIfNeeded: (off >> 2) & 0xFF)
                op += 1
            } else if matchLen <= m2MaxLen && matchOff <= m2MaxOffset {
                let off = matchOff - 1
                out[op] = UInt8(truncatingIfNeeded: (((matchLen - 1) << 5) | ((off & 7) << 2)) & 0xFF)
                op += 1
                out[op] = UInt8(truncatingIfNeeded: (off >> 3) & 0xFF)
                op += 1
            } else if matchLen == 3 && matchOff <= m1MaxOffset + m2MaxOffset && litLen >= 4 {
                let off = matchOff - 1 - m2MaxOffset
                out[op] = UInt8(truncatingIfNeeded: ((off & 3) << 2) & 0xFF)
                op += 1
                out[op] = UInt8(truncatingIfNeeded: (off >> 2) & 0xFF)
                op += 1
            } else if matchOff <= m3MaxOffset {
                let off = matchOff - 1
                if matchLen <= m3MaxLen {
                    out[op] = UInt8(truncatingIfNeeded: (m3Marker | (matchLen - 2)) & 0xFF)
                    op += 1
                } else {
                    out[op] = UInt8(truncatingIfNeeded: m3Marker)
                    op += 1
                    var rem = matchLen - m3MaxLen
                    while rem > 255 {
                        out[op] = 0
                        op += 1
                        rem -= 255
                    }
                    out[op] = UInt8(truncatingIfNeeded: rem)
                    op += 1
                }
                out[op] = UInt8(truncatingIfNeeded: (off << 2) & 0xFF)
                op += 1
                out[op] = UInt8(truncatingIfNeeded: (off >> 6) & 0xFF)
                op += 1
            } else {
                let off = matchOff - 16384
                if matchLen <= m4MaxLen {
                    out[op] = UInt8(truncatingIfNeeded: (m4Marker | ((off & 16384) >> 11) | (matchLen - 2)) & 0xFF)
                    op += 1
                } else {
                    out[op] = UInt8(truncatingIfNeeded: (m4Marker | ((off & 16384) >> 11)) & 0xFF)
                    op += 1
                    var rem = matchLen - m4MaxLen
                    while rem > 255 {
                        out[op] = 0
                        op += 1
                        rem -= 255
                    }
                    out[op] = UInt8(truncatingIfNeeded: rem)
                    op += 1
                }
                out[op] = UInt8(truncatingIfNeeded: (off << 2) & 0xFF)
                op += 1
                out[op] = UInt8(truncatingIfNeeded: (off >> 6) & 0xFF)
                op += 1
            }
        }

        var firstFrame = true
        while ip < scanEnd {
            if ip + 3 > inLen { break }
            let h = hash3(input, ip)
            let ref = ht[h]
            ht[h] = ip
            var matchLen = 0
            var matchOff = 0
            if ref >= 0 && ip - ref <= m4MaxOffset && ip - ref > 0 &&
                input[ref] == input[ip] &&
                input[ref + 1] == input[ip + 1] &&
                input[ref + 2] == input[ip + 2]
            {
                let maxLen = min(scanEnd - ip, inLen - ref)
                var n = 3
                while n < maxLen && input[ref + n] == input[ip + n] {
                    n += 1
                }
                matchLen = n
                matchOff = ip - ref
            }
            if matchLen > 0 {
                let litLen2 = ip - litStart
                let validM1 = matchLen == 2 && matchOff <= m1MaxOffset && litLen2 >= 4 && !firstFrame
                let validM2 = (matchLen >= 3 && matchLen <= m2MaxLen) && matchOff <= m2MaxOffset
                let validM1Long = matchLen == 3 && matchOff > m2MaxOffset && matchOff <= m1MaxOffset + m2MaxOffset && litLen2 >= 4
                let validM3 = matchLen >= 3 && matchOff <= m3MaxOffset
                let validM4 = matchLen >= 3 && matchOff <= m4MaxOffset
                if !(validM1 || validM2 || validM1Long || validM3 || validM4) {
                    matchLen = 0
                }
            }
            if matchLen == 0 {
                ip += 1
                continue
            }
            let litLen = ip - litStart
            emitLiteralRun(litLen, firstFrame && op == 0)
            firstFrame = false
            emitMatch(matchLen, matchOff, litLen)
            ip += matchLen
            litStart = ip
        }
        let finalLitLen = inLen - litStart
        emitLiteralRun(finalLitLen, firstFrame && op == 0)
        out[op] = UInt8(truncatingIfNeeded: m4Marker | 1)
        op += 1
        out[op] = 0
        op += 1
        out[op] = 0
        op += 1
        return Array(out[0..<op])
    }

    public static func decompress(_ input: [UInt8], expectedOutputLength: Int? = nil) throws -> [UInt8] {
        let inEnd = input.count
        if inEnd < 3 {
            throw Lzo1xError.unexpectedEndOfInput
        }
        let presized = expectedOutputLength != nil
        var out = presized
            ? [UInt8](repeating: 0, count: expectedOutputLength!)
            : [UInt8](repeating: 0, count: max(64, input.count * 2))
        var op = 0

        func ensure(_ need: Int) throws {
            if presized {
                if op + need > out.count {
                    throw Lzo1xError.decompressedLengthExceedsExpected
                }
                return
            }
            if op + need <= out.count { return }
            var cap = out.count
            while cap < op + need { cap *= 2 }
            var grown = [UInt8](repeating: 0, count: cap)
            for idx in 0..<op {
                grown[idx] = out[idx]
            }
            out = grown
        }

        var ip = 0
        var state = 0
        var nstate = 0
        var matchLen = 0
        var matchSrc = 0

        let first = Int(input[ip])
        if first >= 22 {
            let len = first - 17
            ip += 1
            if ip + len > inEnd { throw Lzo1xError.unexpectedEndOfInput }
            try ensure(len)
            for _ in 0..<len {
                out[op] = input[ip]
                op += 1
                ip += 1
            }
            state = 4
        } else if first >= 18 {
            let len = first - 17
            ip += 1
            if ip + len > inEnd { throw Lzo1xError.unexpectedEndOfInput }
            try ensure(len)
            for _ in 0..<len {
                out[op] = input[ip]
                op += 1
                ip += 1
            }
            state = len
        }

        var sawEos = false
        func readLengthLadder() throws -> Int {
            var extra = 0
            while ip < inEnd && input[ip] == 0 {
                extra += 255
                ip += 1
                if extra > 262_144 { throw Lzo1xError.unexpectedEndOfInput }
            }
            if ip >= inEnd { throw Lzo1xError.unexpectedEndOfInput }
            extra += Int(input[ip])
            ip += 1
            return extra
        }

        while true {
            if ip >= inEnd { throw Lzo1xError.unexpectedEndOfInput }
            let inst = Int(input[ip])
            ip += 1
            if (inst & 192) != 0 {
                if ip >= inEnd { throw Lzo1xError.unexpectedEndOfInput }
                let h = Int(input[ip])
                ip += 1
                matchSrc = op - ((h << 3) + ((inst >> 2) & 7) + 1)
                matchLen = (inst >> 5) + 1
                nstate = inst & 3
            } else if (inst & 32) != 0 {
                matchLen = (inst & 31) + 2
                if matchLen == 2 {
                    let extra = try readLengthLadder()
                    matchLen += extra + 31
                }
                if ip + 2 > inEnd { throw Lzo1xError.unexpectedEndOfInput }
                let lo = Int(input[ip])
                ip += 1
                let hi = Int(input[ip])
                ip += 1
                let word = lo | (hi << 8)
                matchSrc = op - ((word >> 2) + 1)
                nstate = word & 3
            } else if (inst & 16) != 0 {
                matchLen = (inst & 7) + 2
                if matchLen == 2 {
                    let extra = try readLengthLadder()
                    matchLen += extra + 7
                }
                if ip + 2 > inEnd { throw Lzo1xError.unexpectedEndOfInput }
                let lo = Int(input[ip])
                ip += 1
                let hi = Int(input[ip])
                ip += 1
                let word = lo | (hi << 8)
                let distBase = ((inst & 8) << 11) + (word >> 2)
                nstate = word & 3
                if distBase == 0 {
                    sawEos = true
                    break
                }
                matchSrc = op - distBase - 16384
            } else {
                if state == 0 {
                    var len = inst + 3
                    if len == 3 {
                        let extra = try readLengthLadder()
                        len += extra + 15
                    }
                    if ip + len > inEnd { throw Lzo1xError.unexpectedEndOfInput }
                    try ensure(len)
                    for _ in 0..<len {
                        out[op] = input[ip]
                        op += 1
                        ip += 1
                    }
                    state = 4
                    continue
                } else if state != 4 {
                    if ip >= inEnd { throw Lzo1xError.unexpectedEndOfInput }
                    nstate = inst & 3
                    let nextByte = Int(input[ip])
                    ip += 1
                    matchSrc = op - ((inst >> 2) + (nextByte << 2) + 1)
                    matchLen = 2
                } else {
                    if ip >= inEnd { throw Lzo1xError.unexpectedEndOfInput }
                    nstate = inst & 3
                    let nextByte = Int(input[ip])
                    ip += 1
                    matchSrc = op - ((inst >> 2) + (nextByte << 2) + 2049)
                    matchLen = 3
                }
            }
            if matchSrc < 0 { throw Lzo1xError.invalidLookbehindReference }
            try ensure(matchLen + nstate)
            for _ in 0..<matchLen {
                out[op] = out[matchSrc]
                op += 1
                matchSrc += 1
            }
            state = nstate
            if nstate > 0 {
                if ip + nstate > inEnd { throw Lzo1xError.unexpectedEndOfInput }
                for _ in 0..<nstate {
                    out[op] = input[ip]
                    op += 1
                    ip += 1
                }
            }
        }

        if !sawEos || matchLen != 3 { throw Lzo1xError.missingEndOfStreamMarker }
        if ip != inEnd { throw Lzo1xError.trailingBytesAfterEndOfStream }
        if presized && op != expectedOutputLength {
            throw Lzo1xError.decompressedLengthMismatch
        }
        return op == out.count ? out : Array(out[0..<op])
    }
}
