/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mashopy.passbook.BuildConfig
import com.mashopy.passbook.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val annotated = buildAnnotatedString {
        pushLink(
            LinkAnnotation.Url(
                url = "https://github.com/Mashopy",
                styles = TextLinkStyles(
                    style = SpanStyle(
                        color = Color(0xFFE6AF2E),
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    )
                )
            )
        )
        append("Elias Gheeraert")
        pop()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.app_name), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            Text(text = annotated)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.app_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.built_with), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            listOf(
                stringResource(R.string.lib_compose) to stringResource(R.string.lib_compose_desc),
                stringResource(R.string.lib_mlkit) to stringResource(R.string.lib_mlkit_desc),
                stringResource(R.string.lib_zxing) to stringResource(R.string.lib_zxing_desc),
                stringResource(R.string.lib_bouncy) to stringResource(R.string.lib_bouncy_desc),
                stringResource(R.string.lib_pdf) to stringResource(R.string.lib_pdf_desc)
            ).forEach { (lib, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        lib,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        desc,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
