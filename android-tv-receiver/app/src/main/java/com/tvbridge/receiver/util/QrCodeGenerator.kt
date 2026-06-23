package com.tvbridge.receiver.util

import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Genera un código QR de alto contraste (negro sobre blanco) listo para Compose.
 */
object QrCodeGenerator {

    fun buildPhoneConnectUrl(serverIp: String): String {
        val host = serverIp.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
        return "tvbridge://connect?server=$host"
    }

    /** @deprecated Usar [buildPhoneConnectUrl] para la app emisora Android. */
    fun buildFrontendUrl(serverIp: String): String = buildPhoneConnectUrl(serverIp)

    fun encodeToImageBitmap(
        content: String,
        sizePx: Int = DEFAULT_SIZE_PX,
    ): ImageBitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to QUIET_ZONE_MODULES,
        )

        val bitMatrix = QRCodeWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints,
        )

        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                pixels[rowOffset + x] = if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }

        return android.graphics.Bitmap
            .createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { setPixels(pixels, 0, width, 0, 0, width, height) }
            .asImageBitmap()
    }

    private const val DEFAULT_SIZE_PX = 640
    private const val QUIET_ZONE_MODULES = 2
}
