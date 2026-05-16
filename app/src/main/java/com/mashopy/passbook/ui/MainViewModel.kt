/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mashopy.passbook.model.Pass
import com.mashopy.passbook.parser.PdfParser
import com.mashopy.passbook.storage.PassStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ImportState {
    object Idle    : ImportState()
    object Loading : ImportState()
    data class Success(val id: Long) : ImportState()
    data class Error(val message: String) : ImportState()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _passes = MutableStateFlow<List<Pass>>(emptyList())
    val passes = _passes.asStateFlow()

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState = _importState.asStateFlow()

    init { reload() }

    private fun reload() = viewModelScope.launch {
        _passes.value = PassStore.loadAll(getApplication())
    }

    fun importPdf(uri: Uri) = viewModelScope.launch {
        _importState.value = ImportState.Loading
        try {
            val pass = PdfParser.parse(getApplication(), uri)
            val saved = PassStore.save(getApplication(), pass)
            reload()
            _importState.value = ImportState.Success(saved.id)
        } catch (e: Exception) {
            _importState.value = ImportState.Error(e.message ?: "Import failed")
        }
    }

    fun delete(pass: Pass) = viewModelScope.launch {
        PassStore.delete(getApplication(), pass.id)
        reload()
    }

    fun resetImportState() { _importState.value = ImportState.Idle }
}
