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
}
