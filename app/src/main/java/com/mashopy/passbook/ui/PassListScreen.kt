/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.ui

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mashopy.passbook.R
import com.mashopy.passbook.model.Pass
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassListScreen(
    viewModel: MainViewModel,
    onPassClick: (Long) -> Unit,
    onAboutClick: () -> Unit,
) {
    val passes by viewModel.passes.collectAsState()
    val importState by viewModel.importState.collectAsState()

    // File picker
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { viewModel.importPdf(it) }
        }
    }

    // Navigate on success
    LaunchedEffect(importState) {
        if (importState is ImportState.Success) {
            onPassClick((importState as ImportState.Success).id)
            viewModel.resetImportState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = { onAboutClick() }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.about))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                })
            }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_pdf))
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (passes.isEmpty() && importState !is ImportState.Loading) {
                Text(
                    text = stringResource(R.string.empty_state),
                    modifier = Modifier.align(Alignment.Center),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(passes, key = { it.id }) { pass ->
                    PassCard(
                        pass      = pass,
                        onClick   = { onPassClick(pass.id) },
                        onDelete  = { viewModel.delete(pass) },
                    )
                }
            }

            if (importState is ImportState.Loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (importState is ImportState.Error) {
                val msg = (importState as ImportState.Error).message
                LaunchedEffect(msg) {
                    // reset after showing snackbar (handled via SnackbarHost in production)
                    viewModel.resetImportState()
                }
                Snackbar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text(msg)
                }
            }
        }
    }
}

@Composable
fun PassCard(pass: Pass, onClick: () -> Unit, onDelete: () -> Unit) {
    val bg = runCatching { Color(pass.backgroundColor.toColorInt()) }
        .getOrDefault(Color(0xFF1E1E3C))
    val fg = runCatching { Color(pass.foregroundColor.toColorInt()) }
        .getOrDefault(Color.White)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .background(bg)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 36.dp)) {
                // FROM → TO
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = pass.depTime,
                        color      = fg,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text     = "  ${pass.from}",
                        color    = fg,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text("→  ", color = fg.copy(alpha = 0.6f), fontSize = 18.sp)
                    Text(
                        text     = "${pass.arrTime}  ${pass.to}",
                        color    = fg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(pass.date,      color = fg.copy(alpha = 0.85f), fontSize = 13.sp)
                Text(pass.train,     color = fg.copy(alpha = 0.7f),  fontSize = 12.sp)
                Text(pass.passenger, color = fg.copy(alpha = 0.7f),  fontSize = 12.sp)
            }

            IconButton(
                onClick  = onDelete,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete_pass),
                    tint   = fg.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
