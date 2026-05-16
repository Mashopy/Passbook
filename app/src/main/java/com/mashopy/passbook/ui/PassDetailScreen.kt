/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.provider.MediaStore
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.mashopy.passbook.R
import com.mashopy.passbook.model.Pass
import com.mashopy.passbook.parser.PkpassExporter
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.graphics.toColorInt
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

private suspend fun saveToDownloads(context: Context, file: File) = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, file.name)
        put(MediaStore.Downloads.MIME_TYPE, "application/vnd.apple.pkpass")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: error("Could not create file in Downloads")
    context.contentResolver.openOutputStream(uri)?.use { out ->
        file.inputStream().copyTo(out)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassDetailScreen(
    passId: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val passes by viewModel.passes.collectAsState()
    val pass = passes.firstOrNull { it.id == passId }
    val scope   = rememberCoroutineScope()

    if (pass == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val bg = runCatching { Color(pass.backgroundColor.toColorInt()) }.getOrDefault(Color(0xFF1E1E3C))
    val fg = runCatching { Color(pass.foregroundColor.toColorInt()) }.getOrDefault(Color.White)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ticket), color = fg) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = fg)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                val file = PkpassExporter.export(context, pass)
                                saveToDownloads(context, file)
                                Toast.makeText(context, context.getString(R.string.saved_to_downloads), Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "${context.getString(R.string.export_failed)}: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            tint = fg
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Main pass card ───────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .background(bg)
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    // Times + stations
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(pass.depTime, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text(pass.from,    color = fg, fontSize = 14.sp)
                        }
                        Text("→", color = fg.copy(alpha = 0.5f), fontSize = 24.sp)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(pass.arrTime, color = fg, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                            Text(pass.to,      color = fg, fontSize = 14.sp)
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        color = fg.copy(alpha = 0.2f),
                    )

                    // Detail grid
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PassField(stringResource(R.string.field_date),     pass.date,      fg, Alignment.Start)
                        PassField(stringResource(R.string.field_train),    pass.train,     fg, Alignment.End)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PassField(stringResource(R.string.field_seat),    pass.seat,      fg, Alignment.Start)
                        PassField(stringResource(R.string.field_passenger), pass.passenger, fg, Alignment.End)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        PassField(stringResource(R.string.field_ref),  pass.bookingRef, fg, Alignment.Start)
                        PassField(stringResource(R.string.field_price),     pass.price,      fg, Alignment.End)
                    }
                }
            }

            // ── Barcode card ─────────────────────────────────────────────────
            if (pass.barcodeData.isNotEmpty()) {
                BarcodeCard(pass)
            }
        }
    }
}

@Composable
private fun PassField(label: String, value: String, fg: Color, align: Alignment.Horizontal) {
    Column(horizontalAlignment = align) {
        Text(label, color = fg.copy(alpha = 0.6f), fontSize = 10.sp)
        Text(value, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BarcodeCard(pass: Pass) {
    var barcodeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pass.barcodeData) {
        barcodeBitmap = withContext(Dispatchers.Default) {
            renderBarcode(pass.barcodeData, pass.barcodeFormat)
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp),
    ) {
        barcodeBitmap?.let { bmp ->
            Image(
                bitmap          = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.ticket_barcode),
                contentScale    = ContentScale.Fit,
                modifier        = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .padding(16.dp),
            )
        } ?: Box(
            Modifier.fillMaxWidth().height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
}

private fun renderBarcode(data: String, format: String): Bitmap? = runCatching {
    val zxingFormat = when (format.uppercase()) {
        "AZTEC"     -> BarcodeFormat.AZTEC
        "PDF417"    -> BarcodeFormat.PDF_417
        "CODE128"   -> BarcodeFormat.CODE_128
        else        -> BarcodeFormat.QR_CODE
    }
    val size   = 600
    val matrix = MultiFormatWriter().encode(data, zxingFormat, size, size)
    createBitmap(size, size).also { bmp ->
        for (x in 0 until size) for (y in 0 until size)
            bmp[x, y] = if (matrix[x, y]) AColor.BLACK else AColor.WHITE
    }
}.getOrNull()
