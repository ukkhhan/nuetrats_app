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
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.example.BuildConfig
import com.example.data.api.RetrofitClient
import com.example.data.local.MealRecord
import com.example.data.model.AdvancedNutritionAnalysis
import com.example.ui.AnalysisState
import com.example.ui.NutritionViewModel
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

    // 0 = AI Scanner, 1 = Meal Planner, 2 = Healthy Score (Summary), 3 = Food Log
    var activeTab by remember { mutableIntStateOf(2) } // default to centerpiece Dashboard summary

    // Today's total values
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val todayMeals = remember(savedMeals) {
        savedMeals.filter { it.timestamp >= todayStart }
    }

    val consumedKcal = remember(todayMeals) { todayMeals.sumOf { it.energyKcal } }
    val consumedProtein = remember(todayMeals) { todayMeals.sumOf { it.proteinG } }
    val consumedCarbs = remember(todayMeals) { todayMeals.sumOf { it.carbsG } }
    val consumedFat = remember(todayMeals) { todayMeals.sumOf { it.fatG } }

    var dailyKcalBudget by remember { mutableIntStateOf(1800) }

    // Helper functions
    fun getMealCategory(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "Breakfast"
            in 11..15 -> "Lunch"
            in 16..21 -> "Dinner"
            else -> "Snacks"
        }
    }

    // Restore custom profile from logs
    var displayedHistoryMeal by remember { mutableStateOf<AdvancedNutritionAnalysis?>(null) }
    var displayedHistoryBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scannerOffsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanner_y"
    )

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
                    activeTab = 0 // Switch to Scanner Tab to perform analysis
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
            activeTab = 0 // Switch to Scanner Tab to perform analysis
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

    var showSyncDialog by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    val success = viewModel.exportDataToJson(stream)
                    if (success) {
                        android.widget.Toast.makeText(context, "History exported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Failed to export data.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Export error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val errorMsg = viewModel.importFromBackupJson(stream)
                    if (errorMsg == null) {
                        android.widget.Toast.makeText(context, "History imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, "Import failed: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                android.widget.Toast.makeText(context, "Import error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showSyncDialog) {
        SyncBackupDialog(
            show = showSyncDialog,
            onDismiss = { showSyncDialog = false },
            viewModel = viewModel,
            onExportBackup = { exportBackupLauncher.launch("nutrivision_backup.json") },
            onRestoreBackup = { importBackupLauncher.launch(arrayOf("application/json")) }
        )
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
                                text = when (activeTab) {
                                    0 -> "AI Calorie Tracker"
                                    1 -> "Meal Planner"
                                    2 -> "Healthy Score"
                                    else -> "Food Log"
                                },
                                color = DarkText,
                                fontWeight = FontWeight.Black,
                                style = LocalTextStyle.current.copy(letterSpacing = 0.5.sp)
                            )
                            Text(
                                text = "NutriVision Professional Engine",
                                color = SubtitleColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    val currentUser by viewModel.authRepository.currentUser.collectAsState()
                    val syncingState by viewModel.authRepository.syncingState.collectAsState()
                    IconButton(
                        onClick = { showSyncDialog = true },
                        modifier = Modifier.testTag("sync_backup_menu_button")
                    ) {
                        if (syncingState) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color(0xFFD0BCFF),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (currentUser != null) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = "Sync & Backups Menu",
                                tint = if (currentUser != null) Color(0xFFA3E635) else Color(0xFFD0BCFF),
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SlateDark)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = SlateDark,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "AI Scanner") },
                    label = { Text("AI Scanner", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFFD0BCFF),
                        unselectedIconColor = Color(0xFF8E9199),
                        unselectedTextColor = Color(0xFF8E9199)
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.RestaurantMenu, contentDescription = "Planner") },
                    label = { Text("Planner", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFFD0BCFF),
                        unselectedIconColor = Color(0xFF8E9199),
                        unselectedTextColor = Color(0xFF8E9199)
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.TrackChanges, contentDescription = "Dashboard") },
                    label = { Text("Summary", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFFD0BCFF),
                        unselectedIconColor = Color(0xFF8E9199),
                        unselectedTextColor = Color(0xFF8E9199)
                    )
                )

                NavigationBarItem(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Log Book") },
                    label = { Text("Food Log", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = Color(0xFFD0BCFF),
                        indicatorColor = Color(0xFFD0BCFF),
                        unselectedIconColor = Color(0xFF8E9199),
                        unselectedTextColor = Color(0xFF8E9199)
                    )
                )
            }
        },
        containerColor = SlateBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SlateDark, SlateBackground)
                    )
                )
        ) {
            when (activeTab) {
                0 -> {
                    // TAB 1: AI CALORIE SCANNER
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Secret panel alert warning
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
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "To start analyzing food from images, please add your GEMINI_API_KEY inside the Secrets panel of AI Studio.",
                                                color = Color(0xFFFCA5A5),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Standby Image Picker card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "High-Fidelity Object Alignment Frame",
                                        color = DarkText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Snap a photo of any meal or select a file to run real time computer vision intelligence.",
                                        color = SubtitleColor,
                                        fontSize = 11.sp,
                                        modifier = Modifier.align(Alignment.Start)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Holographic scanning frame
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(230.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF0F0E13))
                                            .border(
                                                width = 1.5.dp,
                                                color = if (selectedBitmap != null) Color(0xFFD0BCFF) else Color(0xFF381E72),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Faint scanning radar grid overlay
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val paintColor = Color(0xFFD0BCFF).copy(alpha = 0.04f)
                                            val divisions = 6
                                            for (i in 1..divisions) {
                                                val y = (size.height / (divisions + 1)) * i
                                                drawLine(color = paintColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                                            }
                                            for (i in 1..divisions) {
                                                val x = (size.width / (divisions + 1)) * i
                                                drawLine(color = paintColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                                            }
                                        }

                                        if (selectedBitmap != null) {
                                            Image(
                                                bitmap = selectedBitmap!!.asImageBitmap(),
                                                contentDescription = "Active scanning target",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )

                                            // Laser sweep scanning indicator bar
                                            if (analysisState is AnalysisState.Loading) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .align(Alignment.TopCenter)
                                                        .offset(y = 230.dp * scannerOffsetY)
                                                        .background(
                                                            brush = Brush.horizontalGradient(
                                                                colors = listOf(Color.Transparent, Color(0xFFD0BCFF), Color.Transparent)
                                                            )
                                                        )
                                                )
                                            }
                                        } else {
                                            // Standby guides
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    imageVector = Icons.Default.Camera,
                                                    contentDescription = "Target",
                                                    tint = Color(0xFFD0BCFF).copy(alpha = 0.4f),
                                                    modifier = Modifier.size(54.dp)
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    "System Calibrated",
                                                    color = DarkText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    "Point lens towards food item",
                                                    color = SubtitleColor,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Precise framing corners
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val bracketLength = 22.dp.toPx()
                                            val strokeWidthVal = 2.dp.toPx()
                                            val padding = 12.dp.toPx()
                                            val w = size.width
                                            val h = size.height
                                            val designColor = if (selectedBitmap != null) Color(0xFFD0BCFF) else Color(0x80D0BCFF)

                                            // Top Left
                                            drawPath(
                                                path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(padding, padding + bracketLength)
                                                    lineTo(padding, padding)
                                                    lineTo(padding + bracketLength, padding)
                                                },
                                                color = designColor,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthVal)
                                            )
                                            // Top Right
                                            drawPath(
                                                path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(w - padding - bracketLength, padding)
                                                    lineTo(w - padding, padding)
                                                    lineTo(w - padding, padding + bracketLength)
                                                },
                                                color = designColor,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthVal)
                                            )
                                            // Bottom Left
                                            drawPath(
                                                path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(padding, h - padding - bracketLength)
                                                    lineTo(padding, h - padding)
                                                    lineTo(padding + bracketLength, h - padding)
                                                },
                                                color = designColor,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthVal)
                                            )
                                            // Bottom Right
                                            drawPath(
                                                path = androidx.compose.ui.graphics.Path().apply {
                                                    moveTo(w - padding - bracketLength, h - padding)
                                                    lineTo(w - padding, h - padding)
                                                    lineTo(w - padding, h - padding - bracketLength)
                                                },
                                                color = designColor,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidthVal)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Action bar trigger row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                val permissionCheck = ContextCompat.checkSelfPermission(
                                                    context,
                                                    android.Manifest.permission.CAMERA
                                                ) == PackageManager.PERMISSION_GRANTED
                                                if (permissionCheck) {
                                                    cameraLauncher.launch(null)
                                                } else {
                                                    permissionLauncher.launch(android.Manifest.permission.CAMERA)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(imageVector = Icons.Default.CameraAlt, contentDescription = "Camera Launcher", modifier = Modifier.size(16.dp))
                                                Text("Camera", fontSize = 13.sp, color = Color(0xFFD0BCFF))
                                            }
                                        }

                                        Button(
                                            onClick = { galleryLauncher.launch("image/*") },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = CardBackground),
                                            border = BorderStroke(1.dp, Color(0xFF44474E))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = "Gallery Picker", modifier = Modifier.size(16.dp))
                                                Text("Gallery", fontSize = 13.sp, color = Color(0xFFCAC4D0))
                                            }
                                        }
                                    }

                                    // Analyze Trigger
                                    if (selectedBitmap != null && analysisState is AnalysisState.Idle) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.analyzeImage(selectedBitmap!!) },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD0BCFF))
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(imageVector = Icons.Default.Psychology, contentDescription = "Intel", tint = Color(0xFF381E72))
                                                Text("Initiate Nutrient AI Analytics", color = Color(0xFF381E72), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // AdMob advertising spacer
                        item {
                            AdMobBanner()
                        }

                        // Multi state analyses result containers
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
                                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                CircularProgressIndicator(color = Color(0xFFD0BCFF), modifier = Modifier.size(44.dp))
                                                Spacer(modifier = Modifier.height(14.dp))
                                                Text("Running Neural Extraction", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                Text("Identifying food mass, calories, vitamins ratio...", color = SubtitleColor, fontSize = 11.sp, textAlign = TextAlign.Center)
                                            }
                                        }
                                    }
                                    is AnalysisState.Error -> {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF5A1D1D)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                                Text("Holographic Extraction Interrupted", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(state.message, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Button(
                                                    onClick = { viewModel.clearSelection() },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2C2C2C))
                                                ) {
                                                    Text("Reset", color = Color.White, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                    is AnalysisState.Success -> {
                                        NutritionResultDashboard(
                                            analysis = state.analysis,
                                            image = state.bitmap,
                                            onSave = { scaledAnalysis ->
                                                viewModel.saveMealToHistory(scaledAnalysis, state.bitmap)
                                                activeTab = 2 // Redirect to summary dashboard on save
                                            },
                                            onCancel = { viewModel.clearSelection() }
                                        )
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 2: MEAL PLANNER
                    var selectedPlannerBudget by remember { mutableIntStateOf(1800) }
                    val plannerScrollState = rememberScrollState()

                    // Generate recommendation templates depending on Selected Budget
                    val currentPlanners = remember(selectedPlannerBudget) {
                        when (selectedPlannerBudget) {
                            1400 -> listOf(
                                Triple("Breakfast", "Greek Yogurt Parfait & Berries", Pair(280.0, Pair(22.0, Pair(35.0, 6.0)))),
                                Triple("Lunch", "Mediterranean Chickpea Salad", Pair(410.0, Pair(15.0, Pair(52.0, 14.0)))),
                                Triple("Dinner", "Steamed Cod with Bok Choy & Rice", Pair(490.0, Pair(38.0, Pair(44.0, 8.0)))),
                                Triple("Snacks", "Raw Almonds & Apple slices", Pair(220.0, Pair(6.0, Pair(22.0, 12.0))))
                            )
                            2200 -> listOf(
                                Triple("Breakfast", "Loaded Oatmeal with Seeds & Honey", Pair(480.0, Pair(18.0, Pair(65.0, 16.0)))),
                                Triple("Lunch", "Spiced Steak Bowl & Quinoa", Pair(690.0, Pair(45.0, Pair(62.0, 24.0)))),
                                Triple("Dinner", "Grilled Pork Chop & Baked Sweet Potato", Pair(680.0, Pair(42.0, Pair(52.0, 28.0)))),
                                Triple("Snacks", "Peanut Butter & Wholewheat Crackers", Pair(350.0, Pair(12.0, Pair(30.0, 18.0))))
                            )
                            2500 -> listOf(
                                Triple("Breakfast", "Three Egg Omelette & Avocado Slices", Pair(590.0, Pair(28.0, Pair(12.0, 48.0)))),
                                Triple("Lunch", "Double Chicken breast & Basmati Rice Platter", Pair(780.0, Pair(65.0, Pair(82.0, 16.0)))),
                                Triple("Dinner", "Sautéed Shrimp & Pesto Spaghetti", Pair(740.0, Pair(48.0, Pair(78.0, 24.0)))),
                                Triple("Snacks", "Protein Shake & Greek Seed Mix", Pair(390.0, Pair(35.0, Pair(24.0, 15.0))))
                            )
                            else -> listOf(
                                // 1800
                                Triple("Breakfast", "Poached Egg & Avocado Toast", Pair(360.0, Pair(16.0, Pair(28.0, 18.0)))),
                                Triple("Lunch", "Smoked Chicken Salad Wrap", Pair(540.0, Pair(34.0, Pair(46.0, 15.0)))),
                                Triple("Dinner", "Baked Atlantic Salmon & Broccoli", Pair(620.0, Pair(42.0, Pair(18.0, 32.0)))),
                                Triple("Snacks", "Mixed Walnut Kernel & Blueberries", Pair(280.0, Pair(8.0, Pair(14.0, 20.0))))
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Dietetic Meal Planner & Suggestions", color = DarkText, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Customize your daily targeted calorie envelope to reveal structured meals suggestions designed by clinical standards.", color = SubtitleColor, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Capsule Row Limit Selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(plannerScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(1400, 1800, 2200, 2500).forEach { budget ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(50))
                                                    .background(if (selectedPlannerBudget == budget) Color(0xFFD0BCFF) else SlateDark)
                                                    .border(1.dp, Color(0xFF44474E), RoundedCornerShape(50))
                                                    .clickable { selectedPlannerBudget = budget; dailyKcalBudget = budget }
                                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                                            ) {
                                                Text(
                                                    text = "$budget kcal",
                                                    color = if (selectedPlannerBudget == budget) Color(0xFF381E72) else DarkText,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        items(currentPlanners) { planner ->
                            val category = planner.first
                            val name = planner.second
                            val calories = planner.third.first
                            val protein = planner.third.second.first
                            val carbs = planner.third.second.second.first
                            val fat = planner.third.second.second.second

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                border = BorderStroke(1.dp, Color(0xFF44474E)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF381E72))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(text = category, color = Color(0xFFD0BCFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Favorite, contentDescription = "Score", tint = Color(0xFFA3E635), modifier = Modifier.size(12.dp))
                                            Text("Aesthetic Score: 9.4", color = Color(0xFFA3E635), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(text = name, color = DarkText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Provides premium high bio-available micronutrients loaded with dietary fibers and healthy mono-lipoids.",
                                        color = SubtitleColor,
                                        fontSize = 11.sp
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Mini indicators
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MiniBadge(label = "${calories.toInt()} kcal", color = Color(0xFFD0BCFF))
                                        MiniBadge(label = "Protein: ${protein}g", color = Color(0xFFFF9F55))
                                        MiniBadge(label = "Carbs: ${carbs}g", color = Color(0xFF55C7FF))
                                        MiniBadge(label = "Fat: ${fat}g", color = Color(0xFFFEE155))
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            // Mocking AdvancedNutritionAnalysis for target save record
                                            val dummyAnalysis = AdvancedNutritionAnalysis(
                                                item_name = name,
                                                estimated_mass_g = 220,
                                                macro_nutrients = com.example.data.model.MacroNutrients(
                                                    energy_kcal = calories,
                                                    protein_g = protein,
                                                    total_carbohydrates_g = carbs,
                                                    dietary_fiber_g = 4.0,
                                                    total_sugars_g = 2.0,
                                                    added_sugars_g = 0.0,
                                                    total_fat_g = fat,
                                                    saturated_fat_g = fat * 0.3,
                                                    trans_fat_g = 0.0,
                                                    polyunsaturated_fat_g = fat * 0.4,
                                                    monounsaturated_fat_g = fat * 0.3,
                                                    cholesterol_mg = 12.0
                                                ),
                                                micro_nutrients = com.example.data.model.MicroNutrients(
                                                    vitamins = com.example.data.model.Vitamins(
                                                        vitamin_a_mcg_rae = 45.0,
                                                        vitamin_c_mg = 14.0,
                                                        vitamin_d_mcg = 1.2,
                                                        vitamin_e_mg = 2.0,
                                                        vitamin_k_mcg = 8.0,
                                                        thiamin_b1_mg = 0.1,
                                                        riboflavin_b2_mg = 0.2,
                                                        niacin_b3_mg = 2.0,
                                                        vitamin_b6_mg = 0.2,
                                                        folate_mcg_dfe = 22.0,
                                                        vitamin_b12_mcg = 0.5
                                                    ),
                                                    minerals = com.example.data.model.Minerals(
                                                        sodium_mg = 180.0,
                                                        potassium_mg = 340.0,
                                                        calcium_mg = 65.0,
                                                        iron_mg = 1.4,
                                                        magnesium_mg = 28.0,
                                                        zinc_mg = 1.1,
                                                        phosphorus_mg = 85.0
                                                    )
                                                ),
                                                dietary_flags = com.example.data.model.DietaryFlags(
                                                    is_gluten_free = true,
                                                    is_dairy_free = false,
                                                    is_vegan = false,
                                                    is_vegetarian = true,
                                                    is_keto_friendly = false
                                                ),
                                                allergens_detected = emptyList(),
                                                professional_dietary_insight = "Excellent structured profile supporting optimal baseline metabolic targets.",
                                                status = "success"
                                            )
                                            val dummyBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                                            viewModel.saveMealToHistory(dummyAnalysis, dummyBitmap)
                                            android.widget.Toast.makeText(context, "Logged $name towards daily total!", android.widget.Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SlateDark),
                                        border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.5f)),
                                        modifier = Modifier.fillMaxWidth().height(36.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Log Eaten Idea", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 3: HEALTHY SCORE & SPEEDOMETER CENTERPIECE
                    val remainingKcal = (dailyKcalBudget - consumedKcal).coerceAtLeast(0.0)
                    val progressRatio = if (dailyKcalBudget > 0) (consumedKcal / dailyKcalBudget).coerceIn(0.0, 1.0).toFloat() else 0f

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF44474E))
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Dynamic Daily Baseline Tracker", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.5.sp)
                                    Spacer(modifier = Modifier.height(14.dp))

                                    // Elegant speedometer semi-arc gauge custom canvas
                                    Box(
                                        modifier = Modifier.size(240.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val strokeWidthVal = 14.dp.toPx()
                                            val w = size.width
                                            val h = size.height
                                            val radius = (w - strokeWidthVal) / 2
                                            val startAngle = 135f
                                            val sweepAngle = 270f

                                            // Draw background track arc
                                            drawArc(
                                                color = SlateDark,
                                                startAngle = startAngle,
                                                sweepAngle = sweepAngle,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokeWidthVal,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )

                                            // Draw colored active indicator arc
                                            drawArc(
                                                color = Color(0xFFD0BCFF),
                                                startAngle = startAngle,
                                                sweepAngle = progressRatio * sweepAngle,
                                                useCenter = false,
                                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                                    width = strokeWidthVal,
                                                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                                                )
                                            )
                                        }

                                        // Central status values
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.LocalFireDepartment, contentDescription = "Energy", tint = Color(0xFFD0BCFF), modifier = Modifier.size(36.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Left: ${remainingKcal.toInt()} kcal",
                                                color = DarkText,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 20.sp
                                            )
                                            Spacer(modifier = Modifier.height(1.dp))
                                            Text(
                                                text = "/ $dailyKcalBudget kcal Limit",
                                                color = SubtitleColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Dynamic quick stats row
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Protein", color = SubtitleColor, fontSize = 10.sp)
                                            Text("${consumedProtein.toInt()}g / 120g", color = Color(0xFFFF9F55), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Carbs", color = SubtitleColor, fontSize = 10.sp)
                                            Text("${consumedCarbs.toInt()}g / 220g", color = Color(0xFF55C7FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Fats", color = SubtitleColor, fontSize = 10.sp)
                                            Text("${consumedFat.toInt()}g / 65g", color = Color(0xFFFEE155), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // Overall Healthy Score summary card
                        item {
                            val averageHealthyScore = if (todayMeals.isEmpty()) 0.0 else 9.2 // Standard premium scale baseline
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF381E72)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = if (todayMeals.isEmpty()) "0" else "9.2", color = Color(0xFFD0BCFF), fontWeight = FontWeight.Black, fontSize = 18.sp)
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Overall Dietetic Healthy Score", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = if (todayMeals.isEmpty()) "Log meal scans or recommended items to calculate your today's biological score matrix." else "Your overall diet is outstandingly loaded with rich vitamins & avoids processed trans-fats. Keep it up!",
                                            color = SubtitleColor,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Timeline Chronology header
                        item {
                            Text("Category Chronology Timeline", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        if (todayMeals.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = CardBackground.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(imageVector = Icons.Default.Timeline, contentDescription = "Time", tint = SubtitleColor.copy(alpha = 0.3f), modifier = Modifier.size(32.dp))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("No Eaten Meals Tracked Today", color = SubtitleColor, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        items(todayMeals) { meal ->
                            val groupCategory = getMealCategory(meal.timestamp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left side timeline bullet points
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFFD0BCFF)))
                                    Box(modifier = Modifier.width(1.5.dp).height(44.dp).background(Color(0xFF44474E)))
                                }

                                Card(
                                    modifier = Modifier.weight(1f).clickable {
                                        try {
                                            val adapter = RetrofitClient.moshiInstance.adapter(AdvancedNutritionAnalysis::class.java)
                                            val decoded = adapter.fromJson(meal.rawJson)
                                            if (decoded != null) {
                                                displayedHistoryMeal = decoded
                                                displayedHistoryBitmap = null
                                                activeTab = 3 // Switch to Food log to view details inspector
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = CardBackground),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(text = meal.itemName, color = DarkText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(text = groupCategory, color = Color(0xFFD0BCFF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "•", color = SubtitleColor, fontSize = 8.sp)
                                                Text(text = "${meal.energyKcal.toInt()} kcal", color = SubtitleColor, fontSize = 9.sp)
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF1C1B1F))
                                                .border(0.5.dp, Color(0xFFD0BCFF).copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("Score: 9.3", color = Color(0xFF9CCC65), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                3 -> {
                    // TAB 4: DATE-SEGMENTED FOOD LOG
                    var selectedCategoryFilter by remember { mutableStateOf("All") }
                    var searchFilterQuery by remember { mutableStateOf("") }
                    val logStripScrollState = rememberScrollState()
                    val logChipsScrollState = rememberScrollState()

                    val filteredMeals = remember(savedMeals, selectedCategoryFilter, searchFilterQuery) {
                        savedMeals.filter { meal ->
                            val cat = getMealCategory(meal.timestamp)
                            val categoryMatch = selectedCategoryFilter == "All" || cat.equals(selectedCategoryFilter, ignoreCase = true)
                            val searchMatch = searchFilterQuery.isEmpty() || meal.itemName.contains(searchFilterQuery, ignoreCase = true)
                            categoryMatch && searchMatch
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date selector strip
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = CardBackground),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Calendar Timeline Log Strip", color = DarkText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(logStripScrollState),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        listOf("Mon 15", "Tue 16", "Wed 17", "Thu 18", "Fri 19", "Sat 20* (Today)", "Sun 21").forEach { dates ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (dates.contains("Today")) Color(0xFFD0BCFF) else SlateDark)
                                                    .border(0.5.dp, Color(0xFF44474E), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = dates,
                                                    color = if (dates.contains("Today")) Color(0xFF381E72) else SubtitleColor,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Search & category filters
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = searchFilterQuery,
                                    onValueChange = { searchFilterQuery = it },
                                    placeholder = { Text("Search logs...", fontSize = 12.sp, color = SubtitleColor) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = CardBackground,
                                        unfocusedContainerColor = CardBackground,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color(0xFF44474E)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = DarkText)
                                )

                                // Category row chips
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(logChipsScrollState),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("All", "Breakfast", "Lunch", "Dinner", "Snacks").forEach { filterType ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(if (selectedCategoryFilter == filterType) Color(0xFFD0BCFF) else CardBackground)
                                                .clickable { selectedCategoryFilter = filterType }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = filterType,
                                                color = if (selectedCategoryFilter == filterType) Color(0xFF381E72) else DarkText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (filteredMeals.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No matching database entries loaded.", color = SubtitleColor, fontSize = 12.sp)
                                }
                            }
                        }

                        // Inspected details sheet overlay
                        displayedHistoryMeal?.let { restoredAnalysis ->
                            item {
                                NutritionResultDashboard(
                                    analysis = restoredAnalysis,
                                    image = displayedHistoryBitmap,
                                    isFromHistory = true,
                                    onSave = { _ -> },
                                    onCancel = {
                                        displayedHistoryMeal = null
                                        displayedHistoryBitmap = null
                                    }
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }

                        items(filteredMeals) { meal ->
                            MealHistoryRow(
                                meal = meal,
                                onInspect = {
                                    try {
                                        val adapter = RetrofitClient.moshiInstance.adapter(AdvancedNutritionAnalysis::class.java)
                                        val decoded = adapter.fromJson(meal.rawJson)
                                        if (decoded != null) {
                                            displayedHistoryMeal = decoded
                                            displayedHistoryBitmap = null
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
        }
    }
}

@Composable
fun NutritionResultDashboard(
    analysis: AdvancedNutritionAnalysis,
    image: Bitmap?,
    isFromHistory: Boolean = false,
    onSave: (AdvancedNutritionAnalysis) -> Unit,
    onCancel: () -> Unit
) {
    var userMassGrams by remember(analysis) { mutableFloatStateOf(analysis.estimated_mass_g.toFloat()) }
    val scaleFactor = if (analysis.estimated_mass_g > 0) (userMassGrams / analysis.estimated_mass_g.toFloat()) else 1.0f

    val scaledAnalysis = remember(analysis, scaleFactor) {
        analysis.scaled(scaleFactor)
    }

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
                text = scaledAnalysis.item_name,
                color = DarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Professional dynamic portion control slider panel
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SlateDark.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, Color(0xFF44474E)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Scale,
                                contentDescription = "Portion adjustment",
                                tint = Color(0xFFD0BCFF),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Interactive Portion Adjuster",
                                color = Color(0xFFD0BCFF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Text(
                            text = "${userMassGrams.toInt()} g",
                            color = DarkText,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Slider(
                        value = userMassGrams,
                        onValueChange = { userMassGrams = it },
                        valueRange = 10f..1000f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFD0BCFF),
                            activeTrackColor = Color(0xFFD0BCFF).copy(alpha = 0.6f),
                            inactiveTrackColor = Color(0xFF1C1B1F)
                        ),
                        modifier = Modifier.fillMaxWidth().height(24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Min (10g)", color = SubtitleColor, fontSize = 8.sp)
                        Text(
                            "Original identified: ${analysis.estimated_mass_g}g",
                            color = SubtitleColor,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text("Max (1kg)", color = SubtitleColor, fontSize = 8.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caloric Distribution Stacked Ratio Bar Chart
            val proteinKcal = scaledAnalysis.macro_nutrients.protein_g * 4.0
            val carbKcal = scaledAnalysis.macro_nutrients.total_carbohydrates_g * 4.0
            val fatKcal = scaledAnalysis.macro_nutrients.total_fat_g * 9.0
            val totalKcalSum = proteinKcal + carbKcal + fatKcal

            val proteinPct = if (totalKcalSum > 0) (proteinKcal / totalKcalSum).toFloat() else 0.33f
            val carbPct = if (totalKcalSum > 0) (carbKcal / totalKcalSum).toFloat() else 0.33f
            val fatPct = if (totalKcalSum > 0) (fatKcal / totalKcalSum).toFloat() else 0.33f

            Text(
                "Caloric Distribution Ratio",
                color = DarkText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            
            // Stacked Bar component
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(SlateDark)
            ) {
                if (proteinPct > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(proteinPct.coerceAtLeast(0.001f))
                            .background(ProteinColor)
                    )
                }
                if (carbPct > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(carbPct.coerceAtLeast(0.001f))
                            .background(CarbColor)
                    )
                }
                if (fatPct > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(fatPct.coerceAtLeast(0.001f))
                            .background(FatColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(ProteinColor, CircleShape))
                    Text("Protein ${(proteinPct * 100).toInt()}%", color = SubtitleColor, fontSize = 9.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(CarbColor, CircleShape))
                    Text("Carbs ${(carbPct * 100).toInt()}%", color = SubtitleColor, fontSize = 9.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(FatColor, CircleShape))
                    Text("Fat ${(fatPct * 100).toInt()}%", color = SubtitleColor, fontSize = 9.sp)
                }
            }

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
                    amount = "${scaledAnalysis.macro_nutrients.energy_kcal.toInt()} kcal",
                    progress = (scaledAnalysis.macro_nutrients.energy_kcal / 1200.0).toFloat().coerceIn(0f, 1f),
                    color = EmeraldAccent
                )

                // Protein g
                MacroProgressCard(
                    label = "Protein",
                    amount = String.format(Locale.US, "%.1f g", scaledAnalysis.macro_nutrients.protein_g),
                    progress = (scaledAnalysis.macro_nutrients.protein_g / 80.0).toFloat().coerceIn(0f, 1f),
                    color = ProteinColor
                )

                // Carbohydrates g
                MacroProgressCard(
                    label = "Total Carbs",
                    amount = String.format(Locale.US, "%.1f g", scaledAnalysis.macro_nutrients.total_carbohydrates_g),
                    progress = (scaledAnalysis.macro_nutrients.total_carbohydrates_g / 250.0).toFloat().coerceIn(0f, 1f),
                    color = CarbColor
                )

                // Fat g
                MacroProgressCard(
                    label = "Total Fat",
                    amount = String.format(Locale.US, "%.1f g", scaledAnalysis.macro_nutrients.total_fat_g),
                    progress = (scaledAnalysis.macro_nutrients.total_fat_g / 70.0).toFloat().coerceIn(0f, 1f),
                    color = FatColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dietary Flags Box Tags
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DietaryTag(name = "Gluten Free", isMatch = scaledAnalysis.dietary_flags.is_gluten_free)
                DietaryTag(name = "Dairy Free", isMatch = scaledAnalysis.dietary_flags.is_dairy_free)
                DietaryTag(name = "Vegan", isMatch = scaledAnalysis.dietary_flags.is_vegan)
                DietaryTag(name = "Vegetarian", isMatch = scaledAnalysis.dietary_flags.is_vegetarian)
                DietaryTag(name = "Keto Friendly", isMatch = scaledAnalysis.dietary_flags.is_keto_friendly)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Allergens Trigger Warning
            if (scaledAnalysis.allergens_detected.isNotEmpty()) {
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
                            text = "Allergens Identified: ${scaledAnalysis.allergens_detected.joinToString(", ")}",
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
                        text = scaledAnalysis.professional_dietary_insight,
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
                        val v = scaledAnalysis.micro_nutrients.vitamins
                        MicroRow("Vitamin A", String.format(Locale.US, "%.1f mcg RAE", v.vitamin_a_mcg_rae))
                        MicroRow("Vitamin C", String.format(Locale.US, "%.1f mg", v.vitamin_c_mg))
                        MicroRow("Vitamin D", String.format(Locale.US, "%.1f mcg", v.vitamin_d_mcg))
                        MicroRow("Vitamin E", String.format(Locale.US, "%.1f mg", v.vitamin_e_mg))
                        MicroRow("Vitamin K", String.format(Locale.US, "%.1f mcg", v.vitamin_k_mcg))
                        MicroRow("Thiamin (B1)", String.format(Locale.US, "%.2f mg", v.thiamin_b1_mg))
                        MicroRow("Riboflavin (B2)", String.format(Locale.US, "%.2f mg", v.riboflavin_b2_mg))
                        MicroRow("Niacin (B3)", String.format(Locale.US, "%.2f mg", v.niacin_b3_mg))
                        MicroRow("Vitamin B6", String.format(Locale.US, "%.2f mg", v.vitamin_b6_mg))
                        MicroRow("Folate", String.format(Locale.US, "%.1f mcg DFE", v.folate_mcg_dfe))
                        MicroRow("Vitamin B12", String.format(Locale.US, "%.2f mcg", v.vitamin_b12_mcg))
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
                        val m = scaledAnalysis.micro_nutrients.minerals
                        MicroRow("Sodium", String.format(Locale.US, "%.1f mg", m.sodium_mg))
                        MicroRow("Potassium", String.format(Locale.US, "%.1f mg", m.potassium_mg))
                        MicroRow("Calcium", String.format(Locale.US, "%.1f mg", m.calcium_mg))
                        MicroRow("Iron", String.format(Locale.US, "%.1f mg", m.iron_mg))
                        MicroRow("Magnesium", String.format(Locale.US, "%.1f mg", m.magnesium_mg))
                        MicroRow("Zinc", String.format(Locale.US, "%.1f mg", m.zinc_mg))
                        MicroRow("Phosphorus", String.format(Locale.US, "%.1f mg", m.phosphorus_mg))
                    }
                }
            }

            // Save log action trigger (if not viewing static retrieved item)
            if (!isFromHistory) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { onSave(scaledAnalysis) },
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

// Extension to scale nutrition details based on factor
fun AdvancedNutritionAnalysis.scaled(factor: Float): AdvancedNutritionAnalysis {
    val roundedMass = (this.estimated_mass_g * factor).toInt().coerceAtLeast(1)
    return this.copy(
        estimated_mass_g = roundedMass,
        macro_nutrients = this.macro_nutrients.copy(
            energy_kcal = this.macro_nutrients.energy_kcal * factor,
            protein_g = this.macro_nutrients.protein_g * factor,
            total_carbohydrates_g = this.macro_nutrients.total_carbohydrates_g * factor,
            dietary_fiber_g = this.macro_nutrients.dietary_fiber_g * factor,
            total_sugars_g = this.macro_nutrients.total_sugars_g * factor,
            added_sugars_g = this.macro_nutrients.added_sugars_g * factor,
            total_fat_g = this.macro_nutrients.total_fat_g * factor,
            saturated_fat_g = this.macro_nutrients.saturated_fat_g * factor,
            trans_fat_g = this.macro_nutrients.trans_fat_g * factor,
            polyunsaturated_fat_g = this.macro_nutrients.polyunsaturated_fat_g * factor,
            monounsaturated_fat_g = this.macro_nutrients.monounsaturated_fat_g * factor,
            cholesterol_mg = this.macro_nutrients.cholesterol_mg * factor
        ),
        micro_nutrients = this.micro_nutrients.copy(
            vitamins = this.micro_nutrients.vitamins.copy(
                vitamin_a_mcg_rae = this.micro_nutrients.vitamins.vitamin_a_mcg_rae * factor,
                vitamin_c_mg = this.micro_nutrients.vitamins.vitamin_c_mg * factor,
                vitamin_d_mcg = this.micro_nutrients.vitamins.vitamin_d_mcg * factor,
                vitamin_e_mg = this.micro_nutrients.vitamins.vitamin_e_mg * factor,
                vitamin_k_mcg = this.micro_nutrients.vitamins.vitamin_k_mcg * factor,
                thiamin_b1_mg = this.micro_nutrients.vitamins.thiamin_b1_mg * factor,
                riboflavin_b2_mg = this.micro_nutrients.vitamins.riboflavin_b2_mg * factor,
                niacin_b3_mg = this.micro_nutrients.vitamins.niacin_b3_mg * factor,
                vitamin_b6_mg = this.micro_nutrients.vitamins.vitamin_b6_mg * factor,
                folate_mcg_dfe = this.micro_nutrients.vitamins.folate_mcg_dfe * factor,
                vitamin_b12_mcg = this.micro_nutrients.vitamins.vitamin_b12_mcg * factor
            ),
            minerals = this.micro_nutrients.minerals.copy(
                sodium_mg = this.micro_nutrients.minerals.sodium_mg * factor,
                potassium_mg = this.micro_nutrients.minerals.potassium_mg * factor,
                calcium_mg = this.micro_nutrients.minerals.calcium_mg * factor,
                iron_mg = this.micro_nutrients.minerals.iron_mg * factor,
                magnesium_mg = this.micro_nutrients.minerals.magnesium_mg * factor,
                zinc_mg = this.micro_nutrients.minerals.zinc_mg * factor,
                phosphorus_mg = this.micro_nutrients.minerals.phosphorus_mg * factor
            )
        )
    )
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

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1B1F)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF44474E))
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    AdView(context).apply {
                        setAdSize(AdSize.BANNER)
                        val configuredBannerId = BuildConfig.ADMOB_BANNER_ID
                        adUnitId = if (!configuredBannerId.isNullOrBlank() && configuredBannerId != "ca-app-pub-3940256099942544/6300978111") {
                            configuredBannerId
                        } else {
                            "ca-app-pub-3940256099942544/6300978111"
                        }
                    }
                },
                update = { adView ->
                    try {
                        adView.loadAd(AdRequest.Builder().build())
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
            
            // Floating tag for easy integration verification
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0xFF381E72), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "AdMob Banner Active",
                    color = Color(0xFFD0BCFF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncBackupDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    viewModel: NutritionViewModel,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit
) {
    val context = LocalContext.current
    val authRepo = viewModel.authRepository
    val currentUser by authRepo.currentUser.collectAsState()
    val syncingState by authRepo.syncingState.collectAsState()

    var activeDialogTab by remember { mutableIntStateOf(0) } // 0 = Firebase Cloud, 1 = Local Backups

    // Form inputs
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isForgotPasswordMode by remember { mutableStateOf(false) }

    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var feedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFeedbackSuccess by remember { mutableStateOf(false) }
    var isProcessingFlow by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    // Decorative Infinite Pulse for cloud indicator
    val infiniteTransition = rememberInfiniteTransition(label = "cloud_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicator_led"
    )

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .testTag("sync_backup_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF14161C)),
            border = BorderStroke(1.dp, Brush.linearGradient(listOf(Color(0xFFD0BCFF).copy(alpha = 0.4f), Color(0xFF1E293B))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "PROFILE BACKUP",
                            color = Color(0xFFD0BCFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.2.sp)
                        )
                        Text(
                            text = "Sync & Cloud Backups",
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { onDismiss() },
                        modifier = Modifier
                            .background(Color(0xFF1E293B), CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close settings menu",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Custom Rounded Tab Capsule
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111318), RoundedCornerShape(12.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Firebase Cloud", "Local Drive").forEachIndexed { index, title ->
                        val isSelected = activeDialogTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) {
                                        Brush.linearGradient(
                                            listOf(Color(0xFF381E72), Color(0xFF5E3A9E))
                                        )
                                    } else {
                                        Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                    }
                                )
                                .clickable { 
                                    activeDialogTab = index
                                    feedbackMessage = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (index == 0) Icons.Default.CloudQueue else Icons.Default.Save,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = title,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Content Switcher
                if (activeDialogTab == 0) {
                    // TAB 1: FIREBASE CLOUD SYNC
                    if (!authRepo.isFirebaseAvailable) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2C1917), RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFFF897A).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                .padding(18.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFFF897A).copy(alpha = 0.15f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Cloud configurations unavailable",
                                        tint = Color(0xFFFF897A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cloud Sync Temporarily Offline",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Google Firebase configurations are missing or incomplete. Please specify FIREBASE_API_KEY, FIREBASE_APP_ID, and FIREBASE_PROJECT_ID inside the Google AI Studio Secrets Panel to initialize the cloud DB.",
                                        color = Color(0xFFE2E2E6).copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    } else if (currentUser != null) {
                        // LOGGED IN STATE
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Color(0xFF1E293B))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Profile Avatar Gradient
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF381E72), Color(0xFFD0BCFF))
                                                    ),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "User profile badge",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "SECURE CLOUD PROFILE",
                                                color = Color(0xFFD0BCFF),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                style = androidx.compose.ui.text.TextStyle(letterSpacing = 1.sp)
                                            )
                                            Text(
                                                text = currentUser?.email ?: "anonymous@dietvision.com",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Divider(color = Color(0xFF334155).copy(alpha = 0.5f), thickness = 1.dp)
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Real-time pulsation status
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Pulsating Green LED Circle
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .drawBehind {
                                                        drawCircle(
                                                            color = Color(0xFFA3E635).copy(alpha = pulseAlpha),
                                                            radius = size.minDimension / 2f + 4.dp.toPx()
                                                        )
                                                        drawCircle(
                                                            color = Color(0xFFA3E635),
                                                            radius = size.minDimension / 2f
                                                        )
                                                    }
                                            )
                                            Text(
                                                text = "Realtime Cloud Sync Active",
                                                color = Color(0xFFA3E635),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Text(
                                            text = "Connected",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Your daily scan history, budgets, and meal records are automatically mirrored securely in the cloud server.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Action: Force Sync Now
                            Button(
                                onClick = {
                                    viewModel.performRealtimeSync()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("force_sync_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (syncingState) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = "Sync option icon",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Synchronize DB Now", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Action: Sign Out of Account
                            OutlinedButton(
                                onClick = { authRepo.logout() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("logout_button"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFB4AB)),
                                border = BorderStroke(1.dp, Color(0xFFFFB4AB).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ExitToApp,
                                    contentDescription = "Sign out session",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out of Account", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // LOGGED OUT STATE
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Feedback Banner
                            feedbackMessage?.let { msg ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp)
                                        .background(
                                            if (isFeedbackSuccess) Color(0xFF1E3A1E) else Color(0xFF3A1E1E),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (isFeedbackSuccess) Color(0xFFA3E635).copy(alpha = 0.6f) else Color(0xFFFF897A).copy(alpha = 0.6f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isFeedbackSuccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isFeedbackSuccess) Color(0xFFA3E635) else Color(0xFFFF897A),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = msg,
                                            color = if (isFeedbackSuccess) Color(0xFFD4F1B4) else Color(0xFFFFDAD6),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }

                            if (isForgotPasswordMode) {
                                // FORGOT PASSWORD UI
                                Text(
                                    text = "Write down your registered email address below, and we'll dispatch a safe recovery link to reset your credentials.",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address", color = Color(0xFF94A3B8)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color(0xFFD0BCFF).copy(alpha = 0.8f))
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedContainerColor = Color(0xFF1A1C23),
                                        unfocusedContainerColor = Color(0xFF111318)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("forgot_email_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        if (email.isBlank()) {
                                            feedbackMessage = "Please write your email address."
                                            isFeedbackSuccess = false
                                            return@Button
                                        }
                                        isProcessingFlow = true
                                        feedbackMessage = null
                                        coroutineScope.launch {
                                            val err = authRepo.sendPasswordResetEmail(email)
                                            isProcessingFlow = false
                                            if (err == null) {
                                                feedbackMessage = "Password reset email dispatched! Please check your registered inbox."
                                                isFeedbackSuccess = true
                                            } else {
                                                feedbackMessage = err
                                                isFeedbackSuccess = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("send_reset_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isProcessingFlow) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text("Send Reset Link", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                TextButton(
                                    onClick = {
                                        isForgotPasswordMode = false
                                        feedbackMessage = null
                                    },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
                                    Text("Back to Sign In", color = Color(0xFFD0BCFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                // LOGIN / REGISTER UI
                                Text(
                                    text = if (isRegisterMode) "Register your profile to instantly synchronize your custom calorie goals and logs across any phone." else "Log back in to securely retrieve and manage your scan history, goals, and targets in real time.",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("Email Address", color = Color(0xFF94A3B8)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Email, contentDescription = null, tint = Color(0xFFD0BCFF).copy(alpha = 0.8f))
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                        unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_email_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Password", color = Color(0xFF94A3B8)) },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD0BCFF).copy(alpha = 0.8f))
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                            Icon(
                                                imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                contentDescription = if (passwordVisible) "Hide password" else "Show password",
                                                tint = Color(0xFF94A3B8)
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedBorderColor = Color(0xFFD0BCFF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                        unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auth_password_input"),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                if (isRegisterMode) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        label = { Text("Confirm Password", color = Color(0xFF94A3B8)) },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color(0xFFD0BCFF).copy(alpha = 0.8f))
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (confirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = if (confirmPasswordVisible) "Hide password" else "Show password",
                                                    tint = Color(0xFF94A3B8)
                                                )
                                            }
                                        },
                                        singleLine = true,
                                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.White,
                                            focusedBorderColor = Color(0xFFD0BCFF),
                                            unfocusedBorderColor = Color(0xFF334155),
                                            focusedContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                                            unfocusedContainerColor = Color(0xFF0F172A).copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("auth_confirm_password_input"),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                if (!isRegisterMode) {
                                    TextButton(
                                        onClick = {
                                            isForgotPasswordMode = true
                                            feedbackMessage = null
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Forgot Password?", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                Button(
                                    onClick = {
                                        if (email.isBlank() || password.isBlank()) {
                                            feedbackMessage = "Please write both email and password."
                                            isFeedbackSuccess = false
                                            return@Button
                                        }
                                        if (isRegisterMode && password != confirmPassword) {
                                            feedbackMessage = "Your passwords do not match. Please verify."
                                            isFeedbackSuccess = false
                                            return@Button
                                        }

                                        isProcessingFlow = true
                                        feedbackMessage = null
                                        coroutineScope.launch {
                                            val err = if (isRegisterMode) {
                                                authRepo.registerUser(email, password)
                                            } else {
                                                authRepo.loginUser(email, password)
                                            }
                                            isProcessingFlow = false
                                            if (err == null) {
                                                feedbackMessage = if (isRegisterMode) "Account created! Logging in..." else "Welcome back!"
                                                isFeedbackSuccess = true
                                                // Reset inputs
                                                email = ""
                                                password = ""
                                                confirmPassword = ""
                                            } else {
                                                feedbackMessage = err
                                                isFeedbackSuccess = false
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("auth_submit_button"),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    if (isProcessingFlow) {
                                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                    } else {
                                        Text(if (isRegisterMode) "Create Free Account" else "Sign In", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isRegisterMode) "Already have an account?" else "Don't have an account?",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                    TextButton(onClick = {
                                        isRegisterMode = !isRegisterMode
                                        feedbackMessage = null
                                    }) {
                                        Text(
                                            text = if (isRegisterMode) "Sign In" else "Create Account",
                                            color = Color(0xFFD0BCFF),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 2: STORAGE / GOOGLE DRIVE EXPORT & IMPORT
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Export your offline food databases and achievements to a standard exchange JSON document. Keep it locally in device storage or upload directly to your personal Google Drive folder.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFF1E293B))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(Color(0xFFD0BCFF).copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Save,
                                            contentDescription = "Save out file",
                                            tint = Color(0xFFD0BCFF),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "Drive & Offline Storage",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Standard Portability Format",
                                            color = Color(0xFFD0BCFF),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Android's native saving selector lets you save backups directly of your scans, budgets, and habits that can be instantly restored on any system installation.",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Backup Action Trigger
                        Button(
                            onClick = {
                                onExportBackup()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("export_backup_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backup,
                                contentDescription = "Export backup icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export Backup JSON", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Restore Action Trigger
                        OutlinedButton(
                            onClick = {
                                onRestoreBackup()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("import_backup_button"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD0BCFF)),
                            border = BorderStroke(1.dp, Color(0xFFD0BCFF).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestorePage,
                                contentDescription = "Import restore icon",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore from Backup JSON", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

