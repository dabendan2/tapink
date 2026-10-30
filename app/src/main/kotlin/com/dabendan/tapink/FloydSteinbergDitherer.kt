package com.dabendan.tapink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Dynamic Floyd-Steinberg error-diffusion dithering and hardware bit-packing
 * supporting any resolution (e.g. 250x122, 296x128, 400x300, 416x240, 200x200)
 * and any FMSC/EZ Sign color palette (2-color BW, 3-color BWR, 4-color BWRY, 6-color Spectra 6).
 */
object FloydSteinbergDitherer {

    enum class ColorMode(
        val code: Int,
        val label: String,
        val paletteRgb: Array<IntArray>,
        val paletteArgb: IntArray,
        val refreshCmdParam: Byte
    ) {
        BW_2COLOR(
            code = 0x20,
            label = "2色 (黑白 BW)",
            paletteRgb = arrayOf(
                intArrayOf(0, 0, 0),       // 0: Black
                intArrayOf(255, 255, 255)  // 1: White
            ),
            paletteArgb = intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt()
            ),
            refreshCmdParam = 0x05.toByte()
        ),
        BWR_3COLOR(
            code = 0x30,
            label = "3色 (黑白紅 BWR)",
            paletteRgb = arrayOf(
                intArrayOf(0, 0, 0),       // 0: Black
                intArrayOf(255, 255, 255), // 1: White
                intArrayOf(255, 0, 0)      // 2: Red
            ),
            paletteArgb = intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFF0000.toInt()
            ),
            refreshCmdParam = 0x85.toByte()
        ),
        BWRY_4COLOR(
            code = 0x31,
            label = "4色 (黑白黃紅 BWRY)",
            paletteRgb = arrayOf(
                intArrayOf(0, 0, 0),       // 0: Black (00b)
                intArrayOf(255, 255, 255), // 1: White (01b)
                intArrayOf(255, 255, 0),   // 2: Yellow (10b) — quantization vertex (0x6c18a8)
                intArrayOf(255, 0, 0)      // 3: Red (11b)
            ),
            paletteArgb = intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFFC000.toInt(),        // Official EZ Sign BMP preview yellow #FFC000 RGB(255,192,0) (0x6c0044/0x6c0204)
                0xFFFF0000.toInt()
            ),
            refreshCmdParam = 0x85.toByte()
        ),
        SPECTRA_6COLOR(
            code = 0x60,
            label = "6色 (Spectra 6)",
            paletteRgb = arrayOf(
                intArrayOf(0, 0, 0),       // 0: Black
                intArrayOf(255, 255, 255), // 1: White
                intArrayOf(255, 0, 0),     // 2: Red
                intArrayOf(255, 255, 0),   // 3: Yellow
                intArrayOf(0, 255, 0),     // 4: Green
                intArrayOf(0, 0, 255)      // 5: Blue
            ),
            paletteArgb = intArrayOf(
                0xFF000000.toInt(),
                0xFFFFFFFF.toInt(),
                0xFFFF0000.toInt(),
                0xFFFFC000.toInt(),        // Official EZ Sign BMP preview yellow #FFC000 RGB(255,192,0) (0x6c03d0)
                0xFF00FF00.toInt(),
                0xFF0000FF.toInt()
            ),
            refreshCmdParam = 0x85.toByte()
        );

        companion object {
            fun fromColorCode(colorByte: Int): ColorMode {
                val b = colorByte and 0xFF
                return when {
                    b == 0x20 -> BW_2COLOR
                    b == 0x30 -> BWR_3COLOR
                    b == 0x31 -> BWRY_4COLOR
                    (b ushr 4) == 6 -> SPECTRA_6COLOR
                    else -> throw TapInkProtocolException(
                        "不支援的硬體色碼: 0x${String.format("%02X", b)}"
                    )
                }
            }
        }
    }

    data class DitherResult(
        val width: Int,
        val height: Int,
        val hwWidth: Int,
        val hwHeight: Int,
        val colorMode: ColorMode,
        val ditheredArgb: IntArray,
        val paletteIndices: ByteArray,
        val packedHardwareBytes: ByteArray
    )

    fun nearestPaletteIndex(paletteRgb: Array<IntArray>, r: Int, g: Int, b: Int): Int {
        var bestDist = Int.MAX_VALUE
        var bestIdx = 0
        for (i in paletteRgb.indices) {
            val p = paletteRgb[i]
            val dr = r - p[0]
            val dg = g - p[1]
            val db = b - p[2]
            val dist = dr * dr + dg * dg + db * db
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun clampChannelInt(v: Int): Int {
        return when {
            v < 0 -> 0
            v > 255 -> 255
            else -> v
        }
    }

    private fun attenuateError(rawErr: Int): Int {
        val absErr = kotlin.math.abs(rawErr)
        return when {
            absErr < 64 -> 0
            absErr < 128 -> rawErr / 2
            else -> rawErr
        }
    }

    /**
     * Determines hardware scan geometry (rotate / flipHorizontal / flipVertical)
     * based on the detected screen dimensions and color mode (matching EzSign & GoodDisplay specs).
     */
    fun resolveTransform(
        hwWidth: Int,
        hwHeight: Int,
        colorMode: ColorMode
    ): Triple<Boolean, Boolean, Boolean> {
        val is42Inch4Color = (hwWidth == 400 && hwHeight == 300) && colorMode == ColorMode.BWRY_4COLOR
        val rotate = is42Inch4Color
        val flipHorizontal = true
        val flipVertical = false
        return Triple(rotate, flipHorizontal, flipVertical)
    }

    /**
     * Expected packed byte count for a given hardware resolution and transform.
     */
    fun expectedPackedByteCount(hwWidth: Int, hwHeight: Int, colorMode: ColorMode): Int {
        val (rotate, _, _) = resolveTransform(hwWidth, hwHeight, colorMode)
        val destW = if (rotate) hwHeight else hwWidth
        val destH = if (rotate) hwWidth else hwHeight
        val bytesPerCol = (destH + 3) / 4
        return destW * bytesPerCol
    }

    /**
     * Pure JVM Floyd-Steinberg error-diffusion dithering and hardware packing for any resolution & ColorMode.
     */
    fun ditherAndPackPixels(
        width: Int,
        height: Int,
        argbPixels: IntArray,
        colorMode: ColorMode = ColorMode.BWRY_4COLOR,
        hwWidth: Int = maxOf(width, height),
        hwHeight: Int = minOf(width, height)
    ): DitherResult {
        require(width > 0 && height > 0) {
            "Invalid dimensions: ${width}x${height}"
        }
        require(argbPixels.size == width * height) {
            "Pixel array size (${argbPixels.size}) does not match ${width}x${height}"
        }
        require((width == hwWidth && height == hwHeight) || (width == hwHeight && height == hwWidth)) {
            "Image dimensions ${width}x${height} do not match hardware dimensions ${hwWidth}x${hwHeight}"
        }

        val total = width * height
        val chR = IntArray(total)
        val chG = IntArray(total)
        val chB = IntArray(total)

        for (i in 0 until total) {
            val c = argbPixels[i]
            val a = (c ushr 24) and 0xFF
            val r = (c ushr 16) and 0xFF
            val g = (c ushr 8) and 0xFF
            val b = c and 0xFF
            if (a < 255) {
                val alpha = a / 255f
                chR[i] = clampChannelInt((r * alpha + 255f * (1f - alpha) + 0.5f).toInt())
                chG[i] = clampChannelInt((g * alpha + 255f * (1f - alpha) + 0.5f).toInt())
                chB[i] = clampChannelInt((b * alpha + 255f * (1f - alpha) + 0.5f).toInt())
            } else {
                chR[i] = r
                chG[i] = g
                chB[i] = b
            }
        }

        val outArgb = IntArray(total)
        val outIndices = ByteArray(total)
        val paletteRgb = colorMode.paletteRgb
        val paletteArgb = colorMode.paletteArgb

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldR = clampChannelInt(chR[idx])
                val oldG = clampChannelInt(chG[idx])
                val oldB = clampChannelInt(chB[idx])

                val palIdx = nearestPaletteIndex(paletteRgb, oldR, oldG, oldB)
                outIndices[idx] = palIdx.toByte()
                outArgb[idx] = paletteArgb[palIdx]

                val p = paletteRgb[palIdx]
                val deltaR = attenuateError(oldR - p[0]) shr 2
                val deltaG = attenuateError(oldG - p[1]) shr 2
                val deltaB = attenuateError(oldB - p[2]) shr 2

                if (deltaR != 0 || deltaG != 0 || deltaB != 0) {
                    if (x + 1 < width) {
                        val rIdx = idx + 1
                        chR[rIdx] = clampChannelInt(chR[rIdx] + deltaR)
                        chG[rIdx] = clampChannelInt(chG[rIdx] + deltaG)
                        chB[rIdx] = clampChannelInt(chB[rIdx] + deltaB)
                    }
                    if (y + 1 < height) {
                        if (x - 1 >= 0) {
                            val blIdx = (y + 1) * width + (x - 1)
                            chR[blIdx] = clampChannelInt(chR[blIdx] + deltaR)
                            chG[blIdx] = clampChannelInt(chG[blIdx] + deltaG)
                            chB[blIdx] = clampChannelInt(chB[blIdx] + deltaB)
                        }
                        val bIdx = (y + 1) * width + x
                        chR[bIdx] = clampChannelInt(chR[bIdx] + deltaR)
                        chG[bIdx] = clampChannelInt(chG[bIdx] + deltaG)
                        chB[bIdx] = clampChannelInt(chB[bIdx] + deltaB)

                        if (x + 1 < width) {
                            val brIdx = (y + 1) * width + (x + 1)
                            chR[brIdx] = clampChannelInt(chR[brIdx] + deltaR)
                            chG[brIdx] = clampChannelInt(chG[brIdx] + deltaG)
                            chB[brIdx] = clampChannelInt(chB[brIdx] + deltaB)
                        }
                    }
                }
            }
        }

        val normalizedIndices: ByteArray = if (width != hwWidth || height != hwHeight) {
            val rotated = ByteArray(hwWidth * hwHeight)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dstX = hwWidth - 1 - y
                    val dstY = x
                    rotated[dstY * hwWidth + dstX] = outIndices[y * width + x]
                }
            }
            rotated
        } else {
            outIndices
        }

        val (rotate, flipH, flipV) = resolveTransform(hwWidth, hwHeight, colorMode)
        val packed = packToHardwareBuffer(
            landscapeIndices = normalizedIndices,
            srcWidth = hwWidth,
            srcHeight = hwHeight,
            rotate = rotate,
            flipHorizontal = flipH,
            flipVertical = flipV
        )

        return DitherResult(
            width = width,
            height = height,
            hwWidth = hwWidth,
            hwHeight = hwHeight,
            colorMode = colorMode,
            ditheredArgb = outArgb,
            paletteIndices = outIndices,
            packedHardwareBytes = packed
        )
    }

    /**
     * Packs 2-bit-per-pixel FMSC e-paper buffer (4 pixels per byte, vertical columns of 4 pixels).
     */
    fun packToHardwareBuffer(
        landscapeIndices: ByteArray,
        srcWidth: Int,
        srcHeight: Int,
        rotate: Boolean,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): ByteArray {
        val destWidth = if (rotate) srcHeight else srcWidth
        val destHeight = if (rotate) srcWidth else srcHeight
        val bytesPerColumn = (destHeight + 3) / 4
        val imageBuffer = ByteArray(destWidth * bytesPerColumn)

        var index = 0
        for (i in destWidth - 1 downTo 0) {
            for (j in 0 until bytesPerColumn) {
                var temp = 0
                for (k in 0 until 4) {
                    temp = temp shl 2
                    val destY = j * 4 + k
                    val destX = i

                    val palIdx = if (destY >= destHeight) {
                        1 // Pad with hardware White (01b) when height is not divisible by 4 (e.g. 250x122)
                    } else {
                        var srcX = destX
                        var srcY = destY

                        if (rotate) {
                            srcX = destY
                            srcY = srcHeight - 1 - destX
                        }
                        if (flipVertical) {
                            srcY = srcHeight - 1 - srcY
                        }
                        if (flipHorizontal) {
                            srcX = srcWidth - 1 - srcX
                        }
                        landscapeIndices[srcY * srcWidth + srcX].toInt() and 0x03
                    }
                    temp = temp or palIdx
                }
                imageBuffer[index++] = temp.toByte()
            }
        }
        return imageBuffer
    }

    /**
     * Resizes/center-crops an arbitrary Android Bitmap to match the detected hardware spec
     * (preserving portrait/landscape orientation of the source image) and applies Floyd-Steinberg dithering.
     */
    fun processBitmapForSpec(
        source: Bitmap,
        hwWidth: Int = 400,
        hwHeight: Int = 300,
        colorMode: ColorMode = ColorMode.BWRY_4COLOR
    ): Pair<Bitmap, DitherResult> {
        val longSide = maxOf(hwWidth, hwHeight)
        val shortSide = minOf(hwWidth, hwHeight)
        val isPortrait = source.height > source.width

        val targetW = if (isPortrait) shortSide else longSide
        val targetH = if (isPortrait) longSide else shortSide

        val scaledBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(scaledBitmap)
        canvas.drawColor(Color.WHITE)

        val scale = maxOf(
            targetW.toFloat() / source.width.toFloat(),
            targetH.toFloat() / source.height.toFloat()
        )
        val dx = (targetW - source.width * scale) * 0.5f
        val dy = (targetH - source.height * scale) * 0.5f

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint().apply {
            isFilterBitmap = false
            isAntiAlias = false
        }
        canvas.drawBitmap(source, matrix, paint)

        val pixels = IntArray(targetW * targetH)
        scaledBitmap.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val normHwW = maxOf(hwWidth, hwHeight)
        val normHwH = minOf(hwWidth, hwHeight)

        val result = ditherAndPackPixels(
            width = targetW,
            height = targetH,
            argbPixels = pixels,
            colorMode = colorMode,
            hwWidth = normHwW,
            hwHeight = normHwH
        )
        val previewBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        previewBitmap.setPixels(result.ditheredArgb, 0, targetW, 0, 0, targetW, targetH)

        return Pair(previewBitmap, result)
    }
}
