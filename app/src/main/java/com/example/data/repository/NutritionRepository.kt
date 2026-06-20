package com.example.data.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.api.*
import com.example.data.local.*
import com.example.data.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class NutritionRepository(
    private val mealRecordDao: MealRecordDao,
    private val moshi: Moshi
) {
    val allMeals: Flow<List<MealRecord>> = mealRecordDao.getAllMeals()

    suspend fun insertMeal(mealRecord: MealRecord) = withContext(Dispatchers.IO) {
        mealRecordDao.insertMeal(mealRecord)
    }

    suspend fun deleteMealById(id: Int) = withContext(Dispatchers.IO) {
        mealRecordDao.deleteMealById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        mealRecordDao.clearHistory()
    }

    suspend fun analyzeImage(bitmap: Bitmap, apiKey: String): AdvancedNutritionAnalysis = withContext(Dispatchers.IO) {
        val base64Image = bitmap.toBase64()

        val prompt = """
            Analyze this image of a meal, drink, or product nutrition label. Inspect the items deeply to extract an exhaustive, comprehensive nutritional breakdown comparable to professional dietetics software.
            
            Return a JSON object containing the nutritional analysis.
            Use the following JSON schema exactly without markdown formatting (only raw JSON):
            {
              "status": "success",
              "item_name": "Detailed description of identified food/drink",
              "estimated_mass_g": 320,
              "macro_nutrients": {
                "energy_kcal": 450,
                "protein_g": 24.5,
                "total_carbohydrates_g": 52.0,
                "dietary_fiber_g": 7.2,
                "total_sugars_g": 14.1,
                "added_sugars_g": 4.0,
                "total_fat_g": 16.8,
                "saturated_fat_g": 4.2,
                "trans_fat_g": 0.0,
                "polyunsaturated_fat_g": 5.1,
                "monounsaturated_fat_g": 7.5,
                "cholesterol_mg": 55.0
              },
              "micro_nutrients": {
                "vitamins": {
                  "vitamin_a_mcg_rae": 120.0,
                  "vitamin_c_mg": 45.0,
                  "vitamin_d_mcg": 2.5,
                  "vitamin_e_mg": 3.1,
                  "vitamin_k_mcg": 60.0,
                  "thiamin_b1_mg": 0.4,
                  "riboflavin_b2_mg": 0.5,
                  "niacin_b3_mg": 6.2,
                  "vitamin_b6_mg": 0.6,
                  "folate_mcg_dfe": 90.0,
                  "vitamin_b12_mcg": 1.8
                },
                "minerals": {
                  "sodium_mg": 580.0,
                  "potassium_mg": 420.0,
                  "calcium_mg": 150.0,
                  "iron_mg": 4.5,
                  "magnesium_mg": 85.0,
                  "zinc_mg": 3.2,
                  "phosphorus_mg": 210.0
                }
              },
              "dietary_flags": {
                "is_gluten_free": false,
                "is_dairy_free": true,
                "is_vegan": false,
                "is_vegetarian": false,
                "is_keto_friendly": false
              },
              "allergens_detected": ["Gluten", "Wheat"],
              "professional_dietary_insight": "High-protein meal with excellent iron content. Sodium is slightly elevated."
            }
            
            IMPORTANT: If the image does NOT contain food, drink, or product nutrition labels, return the JSON with status = "error" and professional_dietary_insight = "No clear food/drink detected in the image. Please upload a clear photo of food or a nutrition label."
        """.trimIndent()

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(
                        Part(text = prompt),
                        Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                    )
                )
            ),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2f
            ),
            systemInstruction = Content(
                parts = listOf(
                    Part(text = "You are an advanced, professional-grade nutrition analysis AI. Your goal is to analyze the content (meal, drink, or label), extract deep exhaustive nutrition details, and return strict raw JSON structure. Be accurate and professional in estimated portions and daily limit calculations.")
                )
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val fullResponseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response from AI")
            
            val cleanedResponseText = cleanJsonResponse(fullResponseText)
            
            val adapter = moshi.adapter(AdvancedNutritionAnalysis::class.java)
            adapter.fromJson(cleanedResponseText) ?: throw Exception("Failed to parse nutrition analysis")
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cleanJsonResponse(rawResponse: String): String {
        var str = rawResponse.trim()
        if (str.startsWith("```json")) {
            str = str.substringAfter("```json").substringBeforeLast("```").trim()
        } else if (str.startsWith("```")) {
            str = str.substringAfter("```").substringBeforeLast("```").trim()
        }
        return str
    }
}
