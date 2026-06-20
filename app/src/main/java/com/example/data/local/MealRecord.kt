package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scanned_meals")
data class MealRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val itemName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val estimatedMassG: Int,
    val energyKcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val rawJson: String,
    val imagePath: String? = null
)
