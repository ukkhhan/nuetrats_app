package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdvancedNutritionAnalysis(
    val status: String,
    val item_name: String,
    val estimated_mass_g: Int,
    val macro_nutrients: MacroNutrients,
    val micro_nutrients: MicroNutrients,
    val dietary_flags: DietaryFlags,
    val allergens_detected: List<String> = emptyList(),
    val professional_dietary_insight: String
)

@JsonClass(generateAdapter = true)
data class MacroNutrients(
    val energy_kcal: Double,
    val protein_g: Double,
    val total_carbohydrates_g: Double,
    val dietary_fiber_g: Double,
    val total_sugars_g: Double,
    val added_sugars_g: Double,
    val total_fat_g: Double,
    val saturated_fat_g: Double,
    val trans_fat_g: Double,
    val polyunsaturated_fat_g: Double,
    val monounsaturated_fat_g: Double,
    val cholesterol_mg: Double
)

@JsonClass(generateAdapter = true)
data class MicroNutrients(
    val vitamins: Vitamins,
    val minerals: Minerals
)

@JsonClass(generateAdapter = true)
data class Vitamins(
    @Json(name = "vitamin_a_mcg_rae") val vitamin_a_mcg_rae: Double = 0.0,
    @Json(name = "vitamin_c_mg") val vitamin_c_mg: Double = 0.0,
    @Json(name = "vitamin_d_mcg") val vitamin_d_mcg: Double = 0.0,
    @Json(name = "vitamin_e_mg") val vitamin_e_mg: Double = 0.0,
    @Json(name = "vitamin_k_mcg") val vitamin_k_mcg: Double = 0.0,
    @Json(name = "thiamin_b1_mg") val thiamin_b1_mg: Double = 0.0,
    @Json(name = "riboflavin_b2_mg") val riboflavin_b2_mg: Double = 0.0,
    @Json(name = "niacin_b3_mg") val niacin_b3_mg: Double = 0.0,
    @Json(name = "vitamin_b6_mg") val vitamin_b6_mg: Double = 0.0,
    @Json(name = "folate_mcg_dfe") val folate_mcg_dfe: Double = 0.0,
    @Json(name = "vitamin_b12_mcg") val vitamin_b12_mcg: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class Minerals(
    val sodium_mg: Double = 0.0,
    val potassium_mg: Double = 0.0,
    val calcium_mg: Double = 0.0,
    val iron_mg: Double = 0.0,
    val magnesium_mg: Double = 0.0,
    val zinc_mg: Double = 0.0,
    val phosphorus_mg: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class DietaryFlags(
    val is_gluten_free: Boolean = false,
    val is_dairy_free: Boolean = false,
    val is_vegan: Boolean = false,
    val is_vegetarian: Boolean = false,
    val is_keto_friendly: Boolean = false
)
