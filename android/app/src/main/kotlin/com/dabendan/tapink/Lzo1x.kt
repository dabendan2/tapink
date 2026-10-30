package com.dabendan.tapink

object Lzo1x {
    private const val M1_MAX_OFFSET = 1024
    private const val M2_MAX_OFFSET = 2048
    private const val M3_MAX_OFFSET = 16384
    private const val M4_MAX_OFFSET = 49151
    private const val M2_MAX_LEN = 8
    private const val M3_MAX_LEN = 33
    private const val M4_MAX_LEN = 9
    private const val M3_MARKER = 32
    private const val M4_MARKER = 16
    private const val HASH_BITS = 13
    private const val HASH_SIZE = 1 shl HASH_BITS
    private const val HASH_MASK = HASH_SIZE - 1

    private fun hash3(b: ByteArray, p: Int): Int {
        val b0 = b[p].toInt() and 0xFF
        val b1 = b[p + 1].toInt() and 0xFF
        val b2 = b[p + 2].toInt() and 0xFF
        val v = b0 or (b1 shl 8) or (b2 shl 16)
        return ((v * 506832829) ushr (32 - HASH_BITS)) and HASH_MASK
    }

    fun compress(input: ByteArray): ByteArray {
        val inLen = input.size
        val outCap = inLen + ((inLen + 15) ushr 4) + 67 + 16
        val out = ByteArray(outCap)
        var op = 0
        var litStart = 0
        var ip = 0
        val ht = IntArray(HASH_SIZE) { -1 }
        val trailGuard = 20
        val scanEnd = inLen - trailGuard

        fun emitLiteralRun(len: Int, firstFrame2: Boolean) {
            if (firstFrame2 && len != 0 && len <= 238) {
                out[op++] = (17 + len).toByte()
            } else if (len <= 3) {
                out[op - 2] = ((out[op - 2].toInt() or len) and 0xFF).toByte()
            } else if (len <= 18) {
                out[op++] = (len - 3).toByte()
            } else {
                out[op++] = 0
                var rem = len - 18
                while (rem > 255) {
                    out[op++] = 0
                    rem -= 255
                }
                out[op++] = rem.toByte()
            }
            for (i in 0 until len) {
                out[op++] = input[litStart + i]
            }
        }

        fun emitMatch(matchLen: Int, matchOff: Int, litLen: Int) {
            if (matchLen == 2) {
                val off = matchOff - 1
                out[op++] = (((off and 3) shl 2) and 0xFF).toByte()
                out[op++] = ((off ushr 2) and 0xFF).toByte()
            } else if (matchLen <= M2_MAX_LEN && matchOff <= M2_MAX_OFFSET) {
                val off = matchOff - 1
                out[op++] = ((((matchLen - 1) shl 5) or ((off and 7) shl 2)) and 0xFF).toByte()
                out[op++] = ((off ushr 3) and 0xFF).toByte()
            } else if (matchLen == 3 && matchOff <= M1_MAX_OFFSET + M2_MAX_OFFSET && litLen >= 4) {
                val off = matchOff - 1 - M2_MAX_OFFSET
                out[op++] = (((off and 3) shl 2) and 0xFF).toByte()
                out[op++] = ((off ushr 2) and 0xFF).toByte()
            } else if (matchOff <= M3_MAX_OFFSET) {
                val off = matchOff - 1
                if (matchLen <= M3_MAX_LEN) {
                    out[op++] = ((M3_MARKER or (matchLen - 2)) and 0xFF).toByte()
                } else {
                    out[op++] = M3_MARKER.toByte()
                    var rem = matchLen - M3_MAX_LEN
                    while (rem > 255) {
                        out[op++] = 0
                        rem -= 255
                    }
                    out[op++] = rem.toByte()
                }
                out[op++] = ((off shl 2) and 0xFF).toByte()
                out[op++] = ((off ushr 6) and 0xFF).toByte()
            } else {
                val off = matchOff - 16384
                if (matchLen <= M4_MAX_LEN) {
                    out[op++] = ((M4_MARKER or ((off and 16384) ushr 11) or (matchLen - 2)) and 0xFF).toByte()
                } else {
                    out[op++] = ((M4_MARKER or ((off and 16384) ushr 11)) and 0xFF).toByte()
                    var rem = matchLen - M4_MAX_LEN
                    while (rem > 255) {
                        out[op++] = 0
                        rem -= 255
                    }
                    out[op++] = rem.toByte()
                }
                out[op++] = ((off shl 2) and 0xFF).toByte()
                out[op++] = ((off ushr 6) and 0xFF).toByte()
            }
        }

        var firstFrame = true
        while (ip < scanEnd) {
            if (ip + 3 > inLen) break
            val h = hash3(input, ip)
            val ref = ht[h]
            ht[h] = ip
            var matchLen = 0
            var matchOff = 0
            if (ref >= 0 && ip - ref <= M4_MAX_OFFSET && ip - ref > 0 &&
                input[ref] == input[ip] &&
                input[ref + 1] == input[ip + 1] &&
                input[ref + 2] == input[ip + 2]
            ) {
                val maxLen = minOf(scanEnd - ip, inLen - ref)
                var n = 3
                while (n < maxLen && input[ref + n] == input[ip + n]) {
                    n++
                }
                matchLen = n
                matchOff = ip - ref
            }
            if (matchLen > 0) {
                val litLen2 = ip - litStart
                val validM1 = matchLen == 2 && matchOff <= M1_MAX_OFFSET && litLen2 >= 4 && !firstFrame
                val validM2 = matchLen in 3..M2_MAX_LEN && matchOff <= M2_MAX_OFFSET
                val validM1Long = matchLen == 3 && matchOff > M2_MAX_OFFSET && matchOff <= M1_MAX_OFFSET + M2_MAX_OFFSET && litLen2 >= 4
                val validM3 = matchLen >= 3 && matchOff <= M3_MAX_OFFSET
                val validM4 = matchLen >= 3 && matchOff <= M4_MAX_OFFSET
                if (!(validM1 || validM2 || validM1Long || validM3 || validM4)) {
                    matchLen = 0
                }
            }
            if (matchLen == 0) {
                ip++
                continue
            }
            val litLen = ip - litStart
            emitLiteralRun(litLen, firstFrame && op == 0)
            firstFrame = false
            emitMatch(matchLen, matchOff, litLen)
            ip += matchLen
            litStart = ip
        }
        val finalLitLen = inLen - litStart
        emitLiteralRun(finalLitLen, firstFrame && op == 0)
        out[op++] = (M4_MARKER or 1).toByte()
        out[op++] = 0
        out[op++] = 0
        return out.copyOfRange(0, op)
    }

    fun decompress(input: ByteArray, expectedOutputLength: Int? = null): ByteArray {
        val inEnd = input.size
        if (inEnd < 3) throw IllegalArgumentException("lzo1x: unexpected end of input")
        val presized = expectedOutputLength != null
        var out = if (presized) ByteArray(expectedOutputLength!!) else ByteArray(maxOf(64, input.size * 2))
        var op = 0

        fun ensure(need: Int) {
            if (presized) {
                if (op + need > out.size) throw IllegalArgumentException("lzo1x: decompressed length exceeds expected")
                return
            }
            if (op + need <= out.size) return
            var cap = out.size
            while (cap < op + need) cap *= 2
            out = out.copyOf(cap)
        }

        var ip = 0
        var state = 0
        var nstate: Int
        var matchLen: Int
        var matchSrc: Int

        val first = input[ip].toInt() and 0xFF
        if (first >= 22) {
            val len = first - 17
            ip++
            if (ip + len > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
            ensure(len)
            for (i in 0 until len) out[op++] = input[ip++]
            state = 4
        } else if (first >= 18) {
            val len = first - 17
            ip++
            if (ip + len > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
            ensure(len)
            for (i in 0 until len) out[op++] = input[ip++]
            state = len
        }

        val sawEos: Boolean
        fun readLengthLadder(): Int {
            var extra = 0
            while (ip < inEnd && (input[ip].toInt() and 0xFF) == 0) {
                extra += 255
                ip++
                if (extra > 262144) throw IllegalArgumentException("lzo1x: unexpected end of input")
            }
            if (ip >= inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
            extra += (input[ip++].toInt() and 0xFF)
            return extra
        }

        while (true) {
            if (ip >= inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
            val inst = input[ip++].toInt() and 0xFF
            if ((inst and 192) != 0) {
                if (ip >= inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                val h = input[ip++].toInt() and 0xFF
                matchSrc = op - ((h shl 3) + ((inst shr 2) and 7) + 1)
                matchLen = (inst shr 5) + 1
                nstate = inst and 3
            } else if ((inst and 32) != 0) {
                matchLen = (inst and 31) + 2
                if (matchLen == 2) {
                    val extra = readLengthLadder()
                    matchLen += extra + 31
                }
                if (ip + 2 > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                val lo = input[ip++].toInt() and 0xFF
                val hi = input[ip++].toInt() and 0xFF
                val word = lo or (hi shl 8)
                matchSrc = op - ((word ushr 2) + 1)
                nstate = word and 3
            } else if ((inst and 16) != 0) {
                matchLen = (inst and 7) + 2
                if (matchLen == 2) {
                    val extra = readLengthLadder()
                    matchLen += extra + 7
                }
                if (ip + 2 > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                val lo = input[ip++].toInt() and 0xFF
                val hi = input[ip++].toInt() and 0xFF
                val word = lo or (hi shl 8)
                val distBase = ((inst and 8) shl 11) + (word ushr 2)
                nstate = word and 3
                if (distBase == 0) {
                    sawEos = true
                    break
                }
                matchSrc = op - distBase - 16384
            } else {
                if (state == 0) {
                    var len = inst + 3
                    if (len == 3) {
                        val extra = readLengthLadder()
                        len += extra + 15
                    }
                    if (ip + len > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                    ensure(len)
                    for (i in 0 until len) out[op++] = input[ip++]
                    state = 4
                    continue
                } else if (state != 4) {
                    if (ip >= inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                    nstate = inst and 3
                    matchSrc = op - ((inst shr 2) + ((input[ip++].toInt() and 0xFF) shl 2) + 1)
                    matchLen = 2
                } else {
                    if (ip >= inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                    nstate = inst and 3
                    matchSrc = op - ((inst shr 2) + ((input[ip++].toInt() and 0xFF) shl 2) + 2049)
                    matchLen = 3
                }
            }
            if (matchSrc < 0) throw IllegalArgumentException("lzo1x: invalid lookbehind reference")
            ensure(matchLen + nstate)
            for (i in 0 until matchLen) out[op++] = out[matchSrc++]
            state = nstate
            if (nstate > 0) {
                if (ip + nstate > inEnd) throw IllegalArgumentException("lzo1x: unexpected end of input")
                for (i in 0 until nstate) out[op++] = input[ip++]
            }
        }

        if (!sawEos || matchLen != 3) throw IllegalArgumentException("lzo1x: missing end-of-stream marker")
        if (ip != inEnd) throw IllegalArgumentException("lzo1x: trailing bytes after end-of-stream marker")
        if (presized && op != expectedOutputLength) {
            throw IllegalArgumentException("lzo1x: decompressed length does not match expectedOutputLength")
        }
        return if (op == out.size) out else out.copyOfRange(0, op)
    }
}
