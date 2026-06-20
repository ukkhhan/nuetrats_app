package com.example.ui.screens

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.local.MealRecord
import com.example.data.model.AdvancedNutritionAnalysis
import com.example.ui.AnalysisState
import com.example.ui.NutritionViewModel
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

// Brand Colors
val SlateDark = Color(0xFF111318)
val SlateBackground = Color(0xFF111318)
val EmeraldPrimary = Color(0xFFD0BCFF)
val EmeraldSecondary = Color(0xFF1C1B1F)
val EmeraldAccent = Color(0xFFD0BCFF)
val CardBackground = Color(0xFF1C1B1F)
val SubtitleColor = Color(0xFF919196)
val DarkText = Color(0xFFE2E2E6)

val CarbColor = Color(0xFFE2E2E6)
val ProteinColor = Color(0xFFE2E2E6)
val FatColor = Color(0xFFE2E2E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NutritionViewModel = viewModel()) {
    val context = LocalContext.current
    val savedMeals by viewModel.savedMeals.collectAsState()
    val analysisState by viewModel.analysisState.collectAsState()
    val selectedBitmap by viewModel.selectedBitmap.collectAsState()

    // For restoring custom detailed view from history items
    var displayedHistoryMeal by remember { mutableStateOf<AdvancedNutritionAnalysis?>(null) }
    var displayedHistoryBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                bitmap?.let { b ->
                    viewModel.selectBitmap(b)
                    displayedHistoryMeal = null
                    displayedHistoryBitmap = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            viewModel.selectBitmap(it)
            displayedHistoryMeal = null
            displayedHistoryBitmap = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            android.widget.Toast.makeText(
                context,
                "Camera permission is required to analyze food items from raw captures.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD0BCFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AI",
                                color = Color(0xFF381E72),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Column {
                            Text(
                                "NutriVision AI",
                                color = DarkText,
                                fontWeight = FontWeight.Bold,
                                style = LocalTextStyle.current.copy(letterSpacing = 1.sp)
                            )
                            Text(
                                "Professional Nutrient Intelligence Engine",
                                color = SubtitleColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SlateDark, SlateBackground)
                    )
                ),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Secret panel alert warning (if user key is default template placeholder)
            if (BuildConfig.GEMINI_API_KEY.isEmpty() || BuildConfig.GEMINI_API_KEY == "MY_GEMINI_API_KEY") {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Security Alert",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                            Column {
                                Text(
                                    text = "Gemini API Key Required",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "To start analyzing food from images, please add your GEMINI_API_KEY parameter into the Secrets panel in AI Studio.",
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // Image Picker selector block
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Analyze New Food item",
                            color = DarkText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Snap a photo of your meal/drink or scan product nutrition labels for dynamic analytics report.",
                            color = SubtitleColor,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected Visual Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SlateDark)
                                .border(
                                    width = 1.dp,
                                    color = if (selectedBitmap != null) EmeraldPrimary else Color(0xFF44474E),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selectedBitmap != null) {
                                Image(
                                    bitmap = selectedBitmap!!.asImageBitmap(),
                                    contentDescription = "Selected dish",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.2f))
                                )
                                IconButton(
                                    onClick = { viewModel.clearSelection() },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .background(SlateDark.copy(alpha = 0.8f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "No photo selected",
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No Photo Loaded",
                                        color = DarkText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Capture a meal dish or select from gallery",
                                        color = SubtitleColor.copy(alpha = 0.7f),
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Two core buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val hasCameraPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.CAMERA
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasCameraPermission) {
                                        cameraLauncher.launch(null)
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("camera_button")
                                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(100)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = "Camera Icon",
                                    tint = Color(0xFFD0BCFF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Camera", color = Color(0xFFD0BCFF))
                            }

                            Button(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("gallery_button")
                                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(100)),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoLibrary,
                                    contentDescription = "Gallery Icon",
                                    tint = Color(0xFFCAC4D0),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Gallery", color = Color(0xFFCAC4D0))
                            }
                        }

                        // Trigger visual analysis button if image selected, but not processed
                        if (selectedBitmap != null && analysisState is AnalysisState.Idle) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.analyzeImage(selectedBitmap!!) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("analyze_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = "Analyze Icon",
                                    tint = Color(0xFF381E72),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Analyze Nutritional Profile",
                                    color = Color(0xFF381E72),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // Realtime analysis states
            item {
                AnimatedVisibility(
                    visible = analysisState !is AnalysisState.Idle,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    when (val state = analysisState) {
                        is AnalysisState.Loading -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(
                                        color = EmeraldAccent,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "AI Deep Analysis Running",
                                        color = DarkText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Running comprehensive dietetics verification, portion extraction, vitamins and minerals breakdown...",
                                        color = SubtitleColor,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        is AnalysisState.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF5C1C13)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = "Analysis Error",
                                        tint = Color(0xFFFCA5A5),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Analysis Issue Detected",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = state.message,
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.clearSelection() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C3E3B))
                                    ) {
                                        Text("Reset", color = Color.White)
                                    }
                                }
                            }
                        }

                        is AnalysisState.Success -> {
                            NutritionResultDashboard(
                                analysis = state.analysis,
                                image = state.bitmap,
                                onSave = {
                                    viewModel.saveMealToHistory(state.analysis, state.bitmap)
                                },
                                onCancel = { viewModel.clearSelection() }
                            )
                        }

                        else -> {}
                    }
                }
            }

            // Render detailed dashboard of restored item from database history (if user clicked a row)
            displayedHistoryMeal?.let { restoredAnalysis ->
                item {
                    NutritionResultDashboard(
                        analysis = restoredAnalysis,
                        image = displayedHistoryBitmap,
                        isFromHistory = true,
                        onSave = {},
                        onCancel = {
                            displayedHistoryMeal = null
                            displayedHistoryBitmap = null
                        }
                    )
                }
            }

            // Tracker Logs Section / Local database history
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nutrition Progress Track (${savedMeals.size} items)",
                            color = DarkText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (savedMeals.isNotEmpty()) {
                            Text(
                                text = "Clear All",
                                color = EmeraldAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable { viewModel.clearAllHistory() }
                                    .padding(4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    if (savedMeals.isEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.MenuBook,
                                        contentDescription = "No saved meals",
                                        tint = SubtitleColor.copy(alpha = 0.4f),
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "No Historic Scanner Records",
                                        color = SubtitleColor.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Render list rows of saved items
            items(savedMeals) { meal ->
                MealHistoryRow(
                    meal = meal,
                    onInspect = {
                        try {
                            val adapter = RetrofitClient.moshiInstance.adapter(AdvancedNutritionAnalysis::class.java)
                            val decoded = adapter.fromJson(meal.rawJson)
                            if (decoded != null) {
                                displayedHistoryMeal = decoded
                                displayedHistoryBitmap = null // Local cached raw base64 isn't re-rendered, or we show item stats
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    onDelete = { viewModel.deleteMeal(meal.id) }
                )
            }
        }
    }
}

@Composable
fun NutritionResultDashboard(
    analysis: AdvancedNutritionAnalysis,
    image: Bitmap?,
    isFromHistory: Boolean = false,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF44474E))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isFromHistory) Icons.Default.CalendarToday else Icons.Default.VerifiedUser,
                        contentDescription = "Success Icon",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isFromHistory) "Retrieved Record" else "Analysis Success",
                        color = EmeraldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = { onCancel() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close report",
                        tint = DarkText.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Identified Item Description
            Text(
                text = analysis.item_name,
                color = DarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Estimated mass: ${analysis.estimated_mass_g}g",
                color = SubtitleColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Macronutrient horizontal progress grid
            Text(
                "Macronutrient Profile",
                color = DarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Calorie energy bar
                MacroProgressCard(
                    label = "Energy",
                    amount = "${analysis.macro_nutrients.energy_kcal.toInt()} kcal",
                    progress = (analysis.macro_nutrients.energy_kcal / 1200.0).toFloat().coerceIn(0f, 1f),
                    color = EmeraldAccent
                )

                // Protein g
                MacroProgressCard(
                    label = "Protein",
                    amount = "${analysis.macro_nutrients.protein_g}g",
                    progress = (analysis.macro_nutrients.protein_g / 80.0).toFloat().coerceIn(0f, 1f),
                    color = ProteinColor
                )

                // Carbohydrates g
                MacroProgressCard(
                    label = "Total Carbs",
                    amount = "${analysis.macro_nutrients.total_carbohydrates_g}g",
                    progress = (analysis.macro_nutrients.total_carbohydrates_g / 250.0).toFloat().coerceIn(0f, 1f),
                    color = CarbColor
                )

                // Fat g
                MacroProgressCard(
                    label = "Total Fat",
                    amount = "${analysis.macro_nutrients.total_fat_g}g",
                    progress = (analysis.macro_nutrients.total_fat_g / 70.0).toFloat().coerceIn(0f, 1f),
                    color = FatColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dietary Flags Box Tags
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DietaryTag(name = "Gluten Free", isMatch = analysis.dietary_flags.is_gluten_free)
                DietaryTag(name = "Dairy Free", isMatch = analysis.dietary_flags.is_dairy_free)
                DietaryTag(name = "Vegan", isMatch = analysis.dietary_flags.is_vegan)
                DietaryTag(name = "Vegetarian", isMatch = analysis.dietary_flags.is_vegetarian)
                DietaryTag(name = "Keto Friendly", isMatch = analysis.dietary_flags.is_keto_friendly)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Allergens Trigger Warning
            if (analysis.allergens_detected.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A1A)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Allergen warning",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Allergens Identified: ${analysis.allergens_detected.joinToString(", ")}",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD0BCFF).copy(alpha = 0.05f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.20f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Professional Dietetics Insight",
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = analysis.professional_dietary_insight,
                        color = DarkText,
                        fontSize = 12.sp,
                        style = LocalTextStyle.current.copy(lineHeight = 16.sp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Micronutrients tables
            Text(
                "Micronutrient Index",
                color = DarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            var showMicros by remember { mutableStateOf(false) }
            Button(
                onClick = { showMicros = !showMicros },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1B1F)),
                modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF44474E), RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (showMicros) "Collapse Micronutrients" else "Expand Deep Vitamins & Minerals",
                    color = Color(0xFFCAC4D0),
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (showMicros) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "",
                    tint = Color(0xFFCAC4D0)
                )
            }

            AnimatedVisibility(visible = showMicros) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text(
                        "Vitamins Profile",
                        color = EmeraldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateDark)
                            .border(1.dp, Color(0xFF44474E), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val v = analysis.micro_nutrients.vitamins
                        MicroRow("Vitamin A", "${v.vitamin_a_mcg_rae} mcg RAE")
                        MicroRow("Vitamin C", "${v.vitamin_c_mg} mg")
                        MicroRow("Vitamin D", "${v.vitamin_d_mcg} mcg")
                        MicroRow("Vitamin E", "${v.vitamin_e_mg} mg")
                        MicroRow("Vitamin K", "${v.vitamin_k_mcg} mcg")
                        MicroRow("Thiamin (B1)", "${v.thiamin_b1_mg} mg")
                        MicroRow("Riboflavin (B2)", "${v.riboflavin_b2_mg} mg")
                        MicroRow("Niacin (B3)", "${v.niacin_b3_mg} mg")
                        MicroRow("Vitamin B6", "${v.vitamin_b6_mg} mg")
                        MicroRow("Folate", "${v.folate_mcg_dfe} mcg DFE")
                        MicroRow("Vitamin B12", "${v.vitamin_b12_mcg} mcg")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "Minerals Profile",
                        color = EmeraldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SlateDark)
                            .border(1.dp, Color(0xFF44474E), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        val m = analysis.micro_nutrients.minerals
                        MicroRow("Sodium", "${m.sodium_mg} mg")
                        MicroRow("Potassium", "${m.potassium_mg} mg")
                        MicroRow("Calcium", "${m.calcium_mg} mg")
                        MicroRow("Iron", "${m.iron_mg} mg")
                        MicroRow("Magnesium", "${m.magnesium_mg} mg")
                        MicroRow("Zinc", "${m.zinc_mg} mg")
                        MicroRow("Phosphorus", "${m.phosphorus_mg} mg")
                    }
                }
            }

            // Save log action trigger (if not viewing static retrieved item)
            if (!isFromHistory) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSave() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_history_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent)
                ) {
                    Icon(
                        imageVector = Icons.Default.SaveAlt,
                        contentDescription = "Save meal",
                        tint = Color(0xFF381E72),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Save to Tracker Log History",
                        color = Color(0xFF381E72),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MacroProgressCard(
    label: String,
    amount: String,
    progress: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = SubtitleColor, fontSize = 12.sp, fontWeight = FontWeight.Normal)
            Text(amount, color = DarkText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = color,
            trackColor = SlateDark
        )
    }
}

@Composable
fun DietaryTag(name: String, isMatch: Boolean) {
    Surface(
        modifier = Modifier.height(26.dp),
        shape = RoundedCornerShape(13.dp),
        color = if (isMatch) EmeraldSecondary.copy(alpha = 0.5f) else Color(0xFF2C3E3B),
        border = BorderStroke(1.dp, if (isMatch) EmeraldAccent else Color.Transparent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isMatch) Icons.Default.Done else Icons.Default.Block,
                contentDescription = null,
                tint = if (isMatch) EmeraldAccent else Color.Gray,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = name,
                color = if (isMatch) DarkText else DarkText.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MicroRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = SubtitleColor, fontSize = 11.sp)
        Text(text = value, color = DarkText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MealHistoryRow(
    meal: MealRecord,
    onInspect: () -> Unit,
    onDelete: () -> Unit
) {
    val dateString = remember(meal.timestamp) {
        val sdf = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
        sdf.format(Date(meal.timestamp))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onInspect() },
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalDining,
                contentDescription = "Meal Icon",
                tint = EmeraldAccent,
                modifier = Modifier
                    .size(36.dp)
                    .background(SlateDark, CircleShape)
                    .padding(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meal.itemName,
                    color = DarkText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = dateString,
                    color = SubtitleColor,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Minimalist inline macro indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniBadge(label = "${meal.energyKcal.toInt()} kcal", color = EmeraldAccent)
                    MiniBadge(label = "P: ${meal.proteinG}g", color = ProteinColor)
                    MiniBadge(label = "C: ${meal.carbsG}g", color = CarbColor)
                    MiniBadge(label = "F: ${meal.fatG}g", color = FatColor)
                }
            }

            IconButton(
                onClick = { onDelete() }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete record",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun MiniBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
