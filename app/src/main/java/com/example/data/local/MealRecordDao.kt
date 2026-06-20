package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MealRecordDao {
    @Query("SELECT * FROM scanned_meals ORDER BY timestamp DESC")
    fun getAllMeals(): Flow<List<MealRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeal(meal: MealRecord)

    @Query("DELETE FROM scanned_meals WHERE id = :id")
    suspend fun deleteMealById(id: Int)

    @Query("DELETE FROM scanned_meals")
    suspend fun clearHistory()
}
