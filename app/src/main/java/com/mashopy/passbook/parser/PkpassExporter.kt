/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.mashopy.passbook.model.Pass
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap

object PkpassExporter {

    suspend fun export(context: Context, pass: Pass): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "pkpass").also { it.mkdirs() }
        val out = File(dir, "${pass.bookingRef.ifEmpty { "ticket" }}.pkpass")

        val files = mutableMapOf<String, ByteArray>()

        // ── pass.json ─────────────────────────────────────────────────────────
        files["pass.json"] = buildPassJson(pass).toByteArray(Charsets.UTF_8)

        // ── Images ────────────────────────────────────────────────────────────
        val bg = runCatching { pass.backgroundColor.toColorInt() }
            .getOrDefault("#1E1E3C".toColorInt())

        files["icon.png"]    = makePng(29,  29,  bg, "")
        files["icon@2x.png"] = makePng(58,  58,  bg, "")
        files["logo.png"]    = makePng(160, 50,  bg, pass.from.take(12))
        files["logo@2x.png"] = makePng(320, 100, bg, pass.from.take(12))

        // ── manifest.json ─────────────────────────────────────────────────────
        val manifest = JSONObject()
        files.forEach { (name, bytes) ->
            manifest.put(name, sha1(bytes))
        }
        val manifestBytes = manifest.toString().toByteArray(Charsets.UTF_8)
        files["manifest.json"] = manifestBytes

        // ── signature ─────────────────────────────────────────────────────────
        files["signature"] = PassSigner.sign(context, manifestBytes)

        // ── Package as ZIP ────────────────────────────────────────────────────
        ZipOutputStream(FileOutputStream(out)).use { zip ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        out
    }

    private fun buildPassJson(pass: Pass): String {
        val barcode = JSONObject().apply {
            put("message", pass.barcodeData)
            put("format", appleFormat(pass.barcodeFormat))
            put("messageEncoding", "iso-8859-1")
        }

        return JSONObject().apply {
            put("formatVersion", 1)
            put("passTypeIdentifier", "pass.com.mashopy.passbook")
            put("serialNumber", pass.bookingRef.ifEmpty { System.currentTimeMillis().toString() })
            put("teamIdentifier", "MASHOPY")
            put("organizationName", "SNCF - ${pass.bookingRef.ifEmpty { "Ticket" }}")
            put("description", "${pass.from} → ${pass.to}")
            put("foregroundColor", pass.foregroundColor.cssRgb())
            put("backgroundColor", pass.backgroundColor.cssRgb())
            put("labelColor", pass.foregroundColor.cssRgb())

            if (pass.barcodeData.isNotEmpty()) {
                put("barcodes", JSONArray().put(barcode))
                put("barcode", barcode)
            }

            put("boardingPass", JSONObject().apply {
                put("transitType", "PKTransitTypeTrain")
                put("headerFields", JSONArray().apply {
                    put(field("dep_time", "DÉPART", pass.depTime))
                    put(field("arr_time", "ARRIVÉE", pass.arrTime))
                })
                put("primaryFields", JSONArray().apply {
                    put(field("from", "DE", pass.from))
                    put(field("to", "À",  pass.to))
                })
                put("secondaryFields", JSONArray().apply {
                    put(field("passenger", "PASSAGER", pass.passenger))
                    put(field("train",     "TRAIN",    pass.train))
                })
                put("auxiliaryFields", JSONArray().apply {
                    put(field("date", "DATE",  pass.date))
                    put(field("seat", "PLACE", pass.seat))
                })
                put("backFields", JSONArray().apply {
                    put(field("ref",   "DOSSIER", pass.bookingRef))
                    put(field("price", "PRIX",    pass.price))
                })
            })
        }.toString(2)
    }

    private fun field(key: String, label: String, value: String) = JSONObject().apply {
        put("key", key); put("label", label); put("value", value)
    }

    private fun appleFormat(f: String) = when (f.uppercase()) {
        "AZTEC"     -> "PKBarcodeFormatAztec"
        "PDF417"    -> "PKBarcodeFormatPDF417"
        "CODE128"   -> "PKBarcodeFormatCode128"
        else        -> "PKBarcodeFormatQR"
    }

    private fun String.cssRgb(): String {
        val c = runCatching { this.toColorInt() }.getOrDefault(Color.WHITE)
        return "rgb(${Color.red(c)},${Color.green(c)},${Color.blue(c)})"
    }

    private fun sha1(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun makePng(w: Int, h: Int, bgColor: Int, text: String): ByteArray {
        val bmp = createBitmap(w, h)
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)
        if (text.isNotEmpty()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = h * 0.35f
                isFakeBoldText = true
            }
            canvas.drawText(text, 4f, h * 0.7f, paint)
        }
        return ByteArrayOutputStream().also {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            bmp.recycle()
        }.toByteArray()
    }
}
