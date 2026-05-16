package com.mashopy.passbook.model

data class Pass(
    val id: Long = 0,
    val from: String,
    val to: String,
    val depTime: String,
    val arrTime: String,
    val date: String,
    val passenger: String,
    val train: String,
    val seat: String,
    val bookingRef: String,
    val price: String,
    val barcodeData: String,
    val barcodeFormat: String,
    val backgroundColor: String = "#1E1E3C",
    val foregroundColor: String = "#FFFFFF",
)
