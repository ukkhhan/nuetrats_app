package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.MealRecord
import com.example.data.local.MealRecordDao
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirebaseAuthRepository(
    private val context: Context,
    private val mealRecordDao: MealRecordDao
) {
    private var auth: FirebaseAuth? = null
    private var firestore: FirebaseFirestore? = null

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser

    private val _syncingState = MutableStateFlow<Boolean>(false)
    val syncingState: StateFlow<Boolean> = _syncingState

    init {
        try {
            // Guarantee Firebase is initialized
            FirebaseInitializer.initialize(context)
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                auth = FirebaseAuth.getInstance()
                firestore = FirebaseFirestore.getInstance()
                _currentUser.value = auth?.currentUser
                
                auth?.addAuthStateListener { firebaseAuth ->
                    _currentUser.value = firebaseAuth.currentUser
                }
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthRepository", "Firebase not available", e)
        }
    }

    val isFirebaseAvailable: Boolean
        get() = auth != null

    suspend fun registerUser(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext "Firebase is not configured yet. Configure API key inside the Secrets panel."
        try {
            authInstance.createUserWithEmailAndPassword(email, password).await()
            null // Success
        } catch (e: Exception) {
            e.localizedMessage ?: "Registration failed."
        }
    }

    suspend fun loginUser(email: String, password: String): String? = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext "Firebase is not configured yet. Configure API key inside the Secrets panel."
        try {
            authInstance.signInWithEmailAndPassword(email, password).await()
            // Try automatic sync on login
            performLocalCloudSync()
            null // Success
        } catch (e: Exception) {
            e.localizedMessage ?: "Authentication failed."
        }
    }

    fun logout() {
        auth?.signOut()
        _currentUser.value = null
    }

    suspend fun sendPasswordResetEmail(email: String): String? = withContext(Dispatchers.IO) {
        val authInstance = auth ?: return@withContext "Firebase is not configured yet. Configure API key inside the Secrets panel."
        try {
            authInstance.sendPasswordResetEmail(email).await()
            null // Success
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to send reset email."
        }
    }

    // Bidirectional Room/Firestore Synchronization
    suspend fun performLocalCloudSync() = withContext(Dispatchers.IO) {
        val user = auth?.currentUser ?: return@withContext
        val db = firestore ?: return@withContext
        
        if (_syncingState.value) return@withContext
        _syncingState.value = true

        try {
            Log.d("FirebaseSync", "Starting sync process...")
            
            // 1. Fetch remote records from Firestore
            val mealsRef = db.collection("users").document(user.uid).collection("meals")
            val querySnapshot = mealsRef.get().await()
            
            // Raw list of incoming documents
            val cloudMealsMap = querySnapshot.documents.associateBy { it.id }

            // 2. Fetch local records from Room Database
            // Room is direct interface, we read it
            mealRecordDao.getAllMeals().collect { localMeals ->
                // To avoid infinite collection loop with Flow, we'll do this once per sync check
                val localMealsMap = localMeals.associateBy { it.timestamp.toString() }

                // 3. Sync local to cloud
                for (localMeal in localMeals) {
                    val cloudId = localMeal.timestamp.toString()
                    if (!cloudMealsMap.containsKey(cloudId)) {
                        // Document doesn't exist in cloud, upload it
                        val data = hashMapOf(
                            "itemName" to localMeal.itemName,
                            "timestamp" to localMeal.timestamp,
                            "estimatedMassG" to localMeal.estimatedMassG,
                            "energyKcal" to localMeal.energyKcal,
                            "proteinG" to localMeal.proteinG,
                            "carbsG" to localMeal.carbsG,
                            "fatG" to localMeal.fatG,
                            "rawJson" to localMeal.rawJson,
                            "imagePath" to localMeal.imagePath
                        )
                        mealsRef.document(cloudId).set(data).await()
                        Log.d("FirebaseSync", "Uploaded ${localMeal.itemName} to Firestore.")
                    }
                }

                // 4. Sync cloud to local
                for (doc in querySnapshot.documents) {
                    val key = doc.id
                    if (!localMealsMap.containsKey(key)) {
                        val itemName = doc.getString("itemName") ?: "Meal"
                        val timestamp = doc.getLong("timestamp") ?: key.toLongOrNull() ?: System.currentTimeMillis()
                        val estimatedMassG = doc.getLong("estimatedMassG")?.toInt() ?: 100
                        val energyKcal = doc.getDouble("energyKcal") ?: 0.0
                        val proteinG = doc.getDouble("proteinG") ?: 0.0
                        val carbsG = doc.getDouble("carbsG") ?: 0.0
                        val fatG = doc.getDouble("fatG") ?: 0.0
                        val rawJson = doc.getString("rawJson") ?: "{}"
                        val imagePath = doc.getString("imagePath")

                        val newLocalMeal = MealRecord(
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
                        mealRecordDao.insertMeal(newLocalMeal)
                        Log.d("FirebaseSync", "Downloaded $itemName from Firestore.")
                    }
                }
                
                // Break after one sync collection evaluation
                return@collect
            }
            Log.d("FirebaseSync", "Sync completed successfully.")
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sync failed error", e)
        } finally {
            _syncingState.value = false
        }
    }

    // Helper to upload single meal on instant log
    fun uploadMealToCloudSync(meal: MealRecord) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cloudId = meal.timestamp.toString()
                val data = hashMapOf(
                    "itemName" to meal.itemName,
                    "timestamp" to meal.timestamp,
                    "estimatedMassG" to meal.estimatedMassG,
                    "energyKcal" to meal.energyKcal,
                    "proteinG" to meal.proteinG,
                    "carbsG" to meal.carbsG,
                    "fatG" to meal.fatG,
                    "rawJson" to meal.rawJson,
                    "imagePath" to meal.imagePath
                )
                db.collection("users").document(user.uid)
                    .collection("meals").document(cloudId)
                    .set(data, SetOptions.merge())
                Log.d("FirebaseSync", "Successfully auto-uploaded individual meal: ${meal.itemName}")
            } catch (e: Exception) {
                Log.e("FirebaseSync", "Failed auto-upload individual meal", e)
            }
        }
    }
}
