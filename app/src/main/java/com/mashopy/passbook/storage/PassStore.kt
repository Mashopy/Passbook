/*
 * Copyright (C) 2025 Elias Gheeraert
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.mashopy.passbook.storage

import android.content.Context
import com.mashopy.passbook.model.Pass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PassStore {

    private fun file(context: Context) = File(context.filesDir, "passes.json")

    suspend fun loadAll(context: Context): List<Pass> = withContext(Dispatchers.IO) {
        val f = file(context)
        if (!f.exists()) return@withContext emptyList()
        runCatching {
            JSONArray(f.readText()).let { arr ->
                (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun save(context: Context, pass: Pass): Pass = withContext(Dispatchers.IO) {
        val list = loadAll(context).toMutableList()
        val withId = if (pass.id == 0L) pass.copy(id = System.currentTimeMillis()) else pass
        list.removeAll { it.id == withId.id }
        list.add(0, withId)
        file(context).writeText(JSONArray(list.map { toJson(it) }.toString()).toString())
        withId
    }

    suspend fun delete(context: Context, id: Long) = withContext(Dispatchers.IO) {
        val list = loadAll(context).filter { it.id != id }
        file(context).writeText(JSONArray(list.map { toJson(it) }.toString()).toString())
    }

    private fun toJson(p: Pass) = JSONObject().apply {
        put("id", p.id); put("from", p.from); put("to", p.to)
        put("depTime", p.depTime); put("arrTime", p.arrTime)
        put("date", p.date); put("passenger", p.passenger)
        put("train", p.train); put("seat", p.seat)
        put("bookingRef", p.bookingRef); put("price", p.price)
        put("barcodeData", p.barcodeData); put("barcodeFormat", p.barcodeFormat)
        put("backgroundColor", p.backgroundColor); put("foregroundColor", p.foregroundColor)
    }

    private fun fromJson(j: JSONObject) = Pass(
        id = j.getLong("id"), from = j.getString("from"), to = j.getString("to"),
        depTime = j.getString("depTime"), arrTime = j.getString("arrTime"),
        date = j.getString("date"), passenger = j.getString("passenger"),
        train = j.getString("train"), seat = j.getString("seat"),
        bookingRef = j.getString("bookingRef"), price = j.getString("price"),
        barcodeData = j.getString("barcodeData"), barcodeFormat = j.getString("barcodeFormat"),
        backgroundColor = j.optString("backgroundColor", "#1E1E3C"),
        foregroundColor = j.optString("foregroundColor", "#FFFFFF"),
    )
}
