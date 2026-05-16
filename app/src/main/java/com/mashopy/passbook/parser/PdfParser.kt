/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mashopy.passbook.model.Pass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.core.graphics.createBitmap

object PdfParser {

    suspend fun parse(context: Context, uri: Uri): Pass {
        val (text, bitmaps) = renderAndExtract(context, uri)
        val barcode = bitmaps.firstNotNullOfOrNull { scanBarcode(it) }
        bitmaps.forEach { it.recycle() }
        android.util.Log.d("PdfParser", "=== EXTRACTED TEXT ===\n$text")
        return buildPass(text, barcode)
    }

    private suspend fun renderAndExtract(
        context: Context, uri: Uri
    ): Pair<String, List<Bitmap>> = withContext(Dispatchers.IO) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open PDF")

        pfd.use {
            PdfRenderer(pfd).use { renderer ->
                val textBuilder = StringBuilder()
                val bitmaps     = mutableListOf<Bitmap>()

                for (i in 0 until renderer.pageCount) {
                    renderer.openPage(i).use { page ->
                        // ── Native text extraction (API 35+, no OCR) ──────────
                        val pageText = page.getTextContents()
                            .joinToString("\n") { it.text }
                        textBuilder.append(pageText).append("\n")

                        // ── Bitmap for barcode scanning only ──────────────────
                        val bmp = createBitmap(page.width * 2, page.height * 2)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        bitmaps.add(bmp)
                    }
                }
                textBuilder.toString() to bitmaps
            }
        }
    }

    private suspend fun scanBarcode(bitmap: Bitmap): BarcodeResult? =
        suspendCancellableCoroutine { cont ->
            val options = com.google.mlkit.vision.barcode.BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_CODE_128,
                )
                .build()

            BarcodeScanning.getClient(options)
                .process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { barcodes ->
                    android.util.Log.d("PdfParser", "Barcodes found: ${barcodes.size} — ${barcodes.map { it.format }}")
                    cont.resume(barcodes.firstOrNull()?.let { barcode ->
                        val data = barcode.rawValue
                            ?: barcode.rawBytes?.toString(Charsets.ISO_8859_1)
                            ?: ""
                        android.util.Log.d("PdfParser", "Barcode data: $data")
                        BarcodeResult(data, mlKitFormatName(barcode.format))
                    })
                }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    private fun mlKitFormatName(format: Int) = when (format) {
        Barcode.FORMAT_QR_CODE     -> "QRCODE"
        Barcode.FORMAT_AZTEC       -> "AZTEC"
        Barcode.FORMAT_PDF417      -> "PDF417"
        Barcode.FORMAT_CODE_128    -> "CODE128"
        Barcode.FORMAT_DATA_MATRIX -> "DATAMATRIX"
        else                       -> "QRCODE"
    }

    internal fun buildPass(text: String, barcode: BarcodeResult?): Pass {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val flat  = lines.joinToString("\n")

        fun get(pattern: String) =
            Regex(pattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                .find(flat)?.groupValues?.getOrNull(1)?.trim() ?: ""

        // ── Passenger ─────────────────────────────────────────────────────────────
        // TGV format: "Nom : GHEERAERT" + "Prénom : Elias"
        // TER format: "GHEERAERT\nELIAS" (two consecutive ALL-CAPS lines)
        val lastName  = get("Nom\\s*:\\s*(.+)")
        val firstName = get("Prénom\\s*:\\s*(.+)")
        val passenger = if (lastName.isNotEmpty()) {
            "$firstName $lastName".trim()
        } else {
            // TER: name is the 2 lines immediately before the DOB line (DD/MM/YYYY)
            val dobIdx = lines.indexOfFirst { it.matches(Regex("\\d{2}/\\d{2}/\\d{4}.*")) }
            if (dobIdx >= 2) {
                "${lines[dobIdx - 1]} ${lines[dobIdx - 2]}".trim()
            } else {
                // fallback: two consecutive all-caps lines that aren't known headers
                val skipWords = setOf("MON BILLET", "ITINÉRAIRE ALLER", "ALLER SIMPLE",
                    "ALLER RETOUR", "VSC MOBILE", "DE", "À")
                val nameIdx = lines.indexOfFirst { line ->
                    line !in skipWords &&
                            line.matches(Regex("[A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ -]{2,}")) &&
                            lines.getOrNull(lines.indexOf(line) + 1).let { next ->
                                next != null && next !in skipWords &&
                                        next.matches(Regex("[A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ -]{2,}"))
                            }
                }
                if (nameIdx >= 0) "${lines[nameIdx + 1]} ${lines[nameIdx]}".trim() else ""
            }
        }

        // ── Booking ref ───────────────────────────────────────────────────────────
        // TGV: "Dossier voyage : UGJWNZ"
        // TER: "REF : A72NYE" or "N° DV : A72NYE"
        val bookingRef = get("Dossier voyage\\s*:\\s*(\\w+)")
            .ifEmpty { get("REF\\s*:\\s*(\\w+)") }
            .ifEmpty { get("N°\\s*DV\\s*:\\s*(\\w+)") }

        // ── Price ─────────────────────────────────────────────────────────────────
        // TGV: "30,00 EUR" — TER: "12,60 €"
        val price = get("Prix\\s*:\\s*([\\d,.]+ ?EUR)")
            .ifEmpty { get("([\\d,.]+\\s*€)") }

        // ── Date ──────────────────────────────────────────────────────────────────
        val rawDate = get("((?:LUNDI|MARDI|MERCREDI|JEUDI|VENDREDI|SAMEDI|DIMANCHE)\\s+\\d+\\s+\\w+\\s+\\d{4})")
        val date = rawDate.split(" ").drop(1)
            .joinToString(" ") { it.lowercase().replaceFirstChar { c -> c.uppercase() } }

        // ── Train ─────────────────────────────────────────────────────────────────
        // TGV: "TGV INOUI 7027"  TER: "TRAIN TER HDF K16 / Opéré par 1187"
        val train = Regex(
            "(TGV[\\w ]+?\\d+|OUIGO\\s+\\d+|INTERCIT[ÉE]S?\\s+\\d+|(?:TRAIN\\s+)?TER[\\w ]+)",
            RegexOption.IGNORE_CASE
        ).find(flat)?.groupValues?.get(1)?.trim()
            ?.replace(Regex("\\s+"), " ") ?: ""

        val cls = get("(\\d+[eè]\\s*CLASSE)")
            .ifEmpty { get("Classe\\s+(\\d+)") }

        // ── Seat ──────────────────────────────────────────────────────────────────
        val seatM = Regex("Voiture\\s+(\\S+).*?Place\\s+(\\d+)", RegexOption.IGNORE_CASE).find(flat)
        val seat  = seatM?.let { "Voiture ${it.groupValues[1]} · Place ${it.groupValues[2]}" } ?: ""

        // ── Stops — time and station may be on separate consecutive lines ──────────
        // TGV: "10h45 PARIS NORD" on one line
        // TER: "12h23\nAMIENS" on two lines
        val stops = mutableListOf<Pair<String, String>>()
        val timeRegex = Regex("^(\\d{1,2}h\\d{2})$")
        val stationRegex = Regex("^[A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ][A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ' -]{3,}$")

        // First try same-line format
        Regex("(\\d{1,2}h\\d{2})[ \\t]+([A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ][A-ZÀÂÉÈÊËÎÏÔÙÛÜÇ -]+)")
            .findAll(flat)
            .map { it.groupValues[1] to it.groupValues[2].trim() }
            .filter { it.second.length >= 4 }
            .forEach { stops.add(it) }

        // If nothing found, try time on one line / station on next
        if (stops.isEmpty()) {
            lines.forEachIndexed { i, line ->
                if (timeRegex.matches(line)) {
                    val next = lines.getOrNull(i + 1) ?: return@forEachIndexed
                    if (stationRegex.matches(next)) {
                        stops.add(line to next)
                    }
                }
            }
        }

        // Also try "DE : AMIENS" / "À : PARIS NORD" as fallback for station names
        val fromStation = stops.firstOrNull()?.second
            .takeIf { !it.isNullOrEmpty() }
            ?: get("DE\\s*:\\s*(.+)")
        val toStation = stops.lastOrNull()?.second
            .takeIf { !it.isNullOrEmpty() && it != fromStation }
            ?: get("À\\s*:\\s*(.+)")

        return Pass(
            from          = fromStation,
            to            = toStation,
            depTime       = stops.firstOrNull()?.first ?: "",
            arrTime       = stops.lastOrNull()?.first  ?: "",
            date          = date,
            passenger     = passenger,
            train         = if (cls.isNotEmpty()) "$train · Classe $cls" else train,
            seat          = seat,
            bookingRef    = bookingRef,
            price         = price,
            barcodeData   = barcode?.data   ?: "",
            barcodeFormat = barcode?.format ?: "",
        )
    }
}

data class BarcodeResult(val data: String, val format: String)
