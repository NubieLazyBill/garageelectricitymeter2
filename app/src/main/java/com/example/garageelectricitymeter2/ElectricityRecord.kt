package com.example.garageelectricitymeter2

data class ElectricityRecord(
    val id: String,
    val date: String,
    val previousReading: Double,
    val currentReading: Double,
    val consumption: Double,
    val cost: Double
)