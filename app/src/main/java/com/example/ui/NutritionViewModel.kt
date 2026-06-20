package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.local.MealDatabase
import com.example.data.local.MealRecord
import com.example.data.model.AdvancedNutritionAnalysis
import com.example.data.repository.FirebaseAuthRepository
import com.example.data.repository.NutritionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AnalysisState {
    object Idle : AnalysisState
    object Loading : AnalysisState
    data class Success(val analysis: AdvancedNutritionAnalysis, val bitmap: Bitmap) : AnalysisState
    data class Error(val message: String) : AnalysisState
}

class NutritionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = MealDatabase.getDatabase(application)
    private val repository = NutritionRepository(database.mealRecordDao, RetrofitClient.moshiInstance)
    val authRepository = FirebaseAuthRepository(application, database.mealRecordDao)

    // Observed list of saved meals
    val savedMeals: StateFlow<List<MealRecord>> = repository.allMeals
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _analysisState = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    // Temporary storage for picked image before analyzer call
    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    fun selectBitmap(bitmap: Bitmap) {
        _selectedBitmap.value = bitmap
        _analysisState.value = AnalysisState.Idle
    }

    fun clearSelection() {
        _selectedBitmap.value = null
        _analysisState.value = AnalysisState.Idle
    }

    fun analyzeImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading
            
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _analysisState.value = AnalysisState.Error(
                    "Gemini API Key is missing. Please add your GEMINI_API_KEY inside the Secrets panel of AI Studio to proceed."
                )
                return@launch
            }

            try {
                val result = repository.analyzeImage(bitmap, apiKey)
                if (result.status == "error") {
                    _analysisState.value = AnalysisState.Error(result.professional_dietary_insight)
                } else {
                    _analysisState.value = AnalysisState.Success(result, bitmap)
                }
            } catch (e: Exception) {
                _analysisState.value = AnalysisState.Error(
                    e.localizedMessage ?: "Failed during visual analysis. Please ensure you have a stable network connection."
                )
            }
        }
    }

    fun saveMealToHistory(analysis: AdvancedNutritionAnalysis, image: Bitmap) {
        viewModelScope.launch {
            // Since storing actual images in Room isn't clean, we serialization rawJson anyways.
            // Also we serialize back to JSON.
            val adapter = RetrofitClient.moshiInstance.adapter(AdvancedNutritionAnalysis::class.java)
            val jsonString = adapter.toJson(analysis)

            val record = MealRecord(
                itemName = analysis.item_name,
                estimatedMassG = analysis.estimated_mass_g,
                energyKcal = analysis.macro_nutrients.energy_kcal,
                proteinG = analysis.macro_nutrients.protein_g,
                carbsG = analysis.macro_nutrients.total_carbohydrates_g,
                fatG = analysis.macro_nutrients.total_fat_g,
                rawJson = jsonString
            )
            repository.insertMeal(record)

            // Auto-upload to cloud Firestore if user is authenticated
            authRepository.uploadMealToCloudSync(record)
            
            // Go back to idle to let user view database / history
            _analysisState.value = AnalysisState.Idle
            _selectedBitmap.value = null
        }
    }

    fun deleteMeal(id: Int) {
        viewModelScope.launch {
            repository.deleteMealById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun performRealtimeSync() {
        viewModelScope.launch {
            authRepository.performLocalCloudSync()
        }
    }

    // Export local SQLite / Room data table to external standard JSON backup
    fun exportDataToJson(outputStream: java.io.OutputStream): Boolean {
        return try {
            val meals = savedMeals.value
            val recordsMap = meals.map {
                mapOf(
                    "itemName" to it.itemName,
                    "timestamp" to it.timestamp,
                    "estimatedMassG" to it.estimatedMassG,
                    "energyKcal" to it.energyKcal,
                    "proteinG" to it.proteinG,
                    "carbsG" to it.carbsG,
                    "fatG" to it.fatG,
                    "rawJson" to it.rawJson,
                    "imagePath" to it.imagePath
                )
            }
            val adapter = RetrofitClient.moshiInstance.adapter(Any::class.java)
            val jsonString = adapter.toJson(recordsMap)
            outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
            outputStream.flush()
            outputStream.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // Import and append backups back into local database
    fun importFromBackupJson(inputStream: java.io.InputStream): String? {
        return try {
            val rawStr = inputStream.bufferedReader().use { it.readText() }
            val adapter = RetrofitClient.moshiInstance.adapter(List::class.java)
            val parsedList = adapter.fromJson(rawStr) as? List<Map<String, Any?>> ?: return "Invalid backup file format."
            
            viewModelScope.launch {
                for (item in parsedList) {
                    try {
                        val itemName = item["itemName"] as? String ?: "Meal"
                        val timestamp = (item["timestamp"] as? Double)?.toLong() ?: System.currentTimeMillis()
                        val estimatedMassG = (item["estimatedMassG"] as? Double)?.toInt() ?: 100
                        val energyKcal = (item["energyKcal"] as? Double) ?: 0.0
                        val proteinG = (item["proteinG"] as? Double) ?: 0.0
                        val carbsG = (item["carbsG"] as? Double) ?: 0.0
                        val fatG = (item["fatG"] as? Double) ?: 0.0
                        val rawJson = item["rawJson"] as? String ?: "{}"
                        val imagePath = item["imagePath"] as? String

                        val record = MealRecord(
                            itemName = itemName,
                            timestamp = timestamp,
                            estimatedMassG = estimatedMassG,
                            energyKcal = energyKcal,
                            proteinG = proteinG,
                            carbsG = carbsG,
                            fatG = fatG,
                            rawJson = rawJson,
                            imagePath = imagePath
                        )
                        repository.insertMeal(record)
                        
                        // Async upload to Firestore too if logged in
                        authRepository.uploadMealToCloudSync(record)
                    } catch (innerEx: Exception) {
                        innerEx.printStackTrace()
                    }
                }
            }
            null // Success
        } catch (e: Exception) {
            e.printStackTrace()
            e.localizedMessage ?: "Failed during backup restoration parser."
        }
    }
}

