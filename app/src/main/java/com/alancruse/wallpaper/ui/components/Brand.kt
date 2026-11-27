package com.alancruse.wallpaper.ui.components

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alancruse.wallpaper.R
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The app logo: same artwork as the launcher icon. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        // Adaptive foreground is drawn on a 108 canvas with a 72 visible area — scale up to fill.
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.requiredSize(size * 1.5f).align(Alignment.Center), contentScale = ContentScale.Fit
        )
    }
}

object QrPayload {
    private const val PREFIX = "wallpaper"
    fun pair(code: String) = "$PREFIX:pair:$code"
    fun group(code: String) = "$PREFIX:group:$code"

    /** Returns the code if [raw] is a matching payload (or a bare code). */
    fun parse(raw: String?, kind: String): String? {
        val s = raw?.trim() ?: return null
        val parts = s.split(':')
        val code = when {
            parts.size == 3 && parts[0] == PREFIX -> if (parts[1] == kind) parts[2] else return null
            else -> s
        }
        return code.takeIf { it.length == 6 && it.all(Char::isLetterOrDigit) }
    }
}

/** QR code tile with dark modules on white, so every scanner can read it in any theme. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val matrix = remember(text) {
        QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
        )
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(size * 0.08f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val n = matrix.width
            val cell = this.size.minDimension / n
            val r = CornerRadius(cell * 0.35f)
            val ink = Color(0xFF14101F)
            for (y in 0 until n) for (x in 0 until n) if (matrix[x, y]) {
                drawRoundRect(ink, Offset(x * cell, y * cell), Size(cell * 1.02f, cell * 1.02f), r)
            }
        }
    }
}

/** Launches the Google Play services QR scanner (no camera permission needed). */
fun scanQr(context: Context, onResult: (String?) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .enableAutoZoom()
        .build()
    val scanner = GmsBarcodeScanning.getClient(context, options)
    // Make sure the scanner module is downloaded; it installs on first use otherwise.
    ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
    scanner.startScan()
        .addOnSuccessListener { onResult(it.rawValue) }
        .addOnCanceledListener { onResult(null) }
        .addOnFailureListener { onResult(null) }
}
