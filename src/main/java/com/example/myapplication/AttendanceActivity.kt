package com.example.myapplication

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.*
import kotlinx.coroutines.*
import java.net.URL
import java.net.HttpURLConnection

class AttendanceActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var tvUserName: TextView
    private lateinit var tvCompanyName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvHours: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvCameraStatus: TextView
    private lateinit var tvExitedArea: TextView
    private lateinit var btnVerify: Button
    private lateinit var btnTimeIn: Button
    private lateinit var btnBreakTime: Button
    private lateinit var previewView: PreviewView

    // Logic Tools
    private val db = FirebaseFirestore.getInstance()
    private var interpreter: Interpreter? = null
    private var savedFaceVector: FloatArray? = null
    private var isFaceVerified = false
    private var isScanning = false
    private var faceFailureCount = 0

    // Biometric Tools
    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    // USER & GEO SETTINGS
    private lateinit var userId: String
    private var ojtLat: Double? = null
    private var ojtLng: Double? = null
    private var amInTime: String? = null
    private var pmInTime: String? = null
    private var isInsideGeofence = false
    private val GEOFENCE_RADIUS_METERS = 100.0

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // Mock location detection
    private var isMockLocationDetected = false

    // Time-in window variables (calculated from database with -40 mins)
    private var amEarlyHour: Int? = null
    private var amEarlyMinute: Int? = null
    private var amScheduledHour: Int? = null
    private var amScheduledMinute: Int? = null

    private var pmEarlyHour: Int? = null
    private var pmEarlyMinute: Int? = null
    private var pmScheduledHour: Int? = null
    private var pmScheduledMinute: Int? = null

    // Break time tracking
    private var isOnBreak = false
    private var currentShift: String? = null

    // Geofencing client
    private lateinit var geofencingClient: GeofencingClient

    // Time sync offset (reliable time sync without Firebase spam)
    private var serverTimeOffset: Long = 0L // Difference between server time and System.currentTimeMillis()
    private var lastSyncTime: Long = 0L

    // Button spam prevention
    private var lastVerifyClickTime: Long = 0L
    private var lastTimeInClickTime: Long = 0L
    private var lastBreakClickTime: Long = 0L
    private val BUTTON_DEBOUNCE_DELAY_MS = 500L

    // ================= ATTENDANCE RULES =================
    private val MORNING_CUTOFF_HOUR = 12
    private val MORNING_CUTOFF_MINUTE = 30

    private val AFTERNOON_CUTOFF_HOUR = 19
    private val AFTERNOON_CUTOFF_MINUTE = 0

    // ================= SETTINGS =================
    private val EYE_CLOSED_THRESHOLD = 0.40f
    private val SIMILARITY_THRESHOLD = 0.80f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)
        userId = intent.getStringExtra("user_id") ?: "default_value"

        tvUserName = findViewById(R.id.tvUserName)
        tvCompanyName = findViewById(R.id.tvCompanyName)
        tvDate = findViewById(R.id.tvDate)
        tvStatus = findViewById(R.id.tvStatus)
        tvHours = findViewById(R.id.tvHours)
        tvDistance = findViewById(R.id.tvDistance)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        tvCameraStatus = findViewById(R.id.tvCameraStatus)
        tvExitedArea = findViewById(R.id.tvExitedArea)
        btnVerify = findViewById(R.id.btnVerify)
        btnTimeIn = findViewById(R.id.btnTimeIn)
        btnBreakTime = findViewById(R.id.btnBreakTime)
        previewView = findViewById(R.id.previewView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)
        executor = ContextCompat.getMainExecutor(this)
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        setupBiometricPrompt()
        loadModel()
        loadUserData()
        loadOjtLocation()
        setupLiveLocation()
        startCamera()

        // Sync time once on startup
        syncServerTime()

        // Verify button with debouncing
        btnVerify.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastVerifyClickTime >= BUTTON_DEBOUNCE_DELAY_MS) {
                lastVerifyClickTime = currentTime
                if (!isScanning && !isFaceVerified) {
                    isScanning = true
                    startAutoCaptureLoop()
                }
            }
        }

        // Time In/Out button with debouncing
        btnTimeIn.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastTimeInClickTime >= BUTTON_DEBOUNCE_DELAY_MS) {
                lastTimeInClickTime = currentTime
                handleAttendanceSubmission()
            }
        }

        // Break Time button with debouncing
        btnBreakTime.setOnClickListener {
            val currentTime = SystemClock.elapsedRealtime()
            if (currentTime - lastBreakClickTime >= BUTTON_DEBOUNCE_DELAY_MS) {
                lastBreakClickTime = currentTime
                handleBreakTime()
            }
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                updateDateTimeDisplay()
                handler.postDelayed(this, 1000)
            }
        })
    }

    // ==================== NEW RELIABLE TIME SYNC ====================

    /**
     * Sync server time ONCE using WorldTimeAPI (fast and reliable)
     * Falls back to Google if needed
     */
    private fun syncServerTime() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Try WorldTimeAPI first (very fast, no rate limits)
                val url = URL("https://worldtimeapi.org/api/timezone/Asia/Manila")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                // Parse JSON manually (simple extraction)
                val unixTimeMatch = "\"unixtime\":(\\d+)".toRegex().find(response)
                if (unixTimeMatch != null) {
                    val serverUnixTime = unixTimeMatch.groupValues[1].toLong() * 1000 // Convert to millis
                    val localTime = System.currentTimeMillis()
                    serverTimeOffset = serverUnixTime - localTime
                    lastSyncTime = localTime

                    withContext(Dispatchers.Main) {
                        Log.d("TimeSync", "✅ Time synced! Offset: ${serverTimeOffset}ms")

                    }
                    return@launch
                }
            } catch (e: Exception) {
                Log.w("TimeSync", "WorldTimeAPI failed, trying Google fallback", e)
            }

            try {
                // Fallback: Use Google's server time header
                val url = URL("https://www.google.com")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "HEAD"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000
                connection.connect()

                val dateHeader = connection.getHeaderField("Date")
                connection.disconnect()

                if (dateHeader != null) {
                    val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                    val serverTime = sdf.parse(dateHeader)?.time ?: throw Exception("Parse failed")
                    val localTime = System.currentTimeMillis()
                    serverTimeOffset = serverTime - localTime
                    lastSyncTime = localTime

                    withContext(Dispatchers.Main) {
                        Log.d("TimeSync", "✅ Time synced via Google! Offset: ${serverTimeOffset}ms")

                    }
                }
            } catch (e: Exception) {
                Log.e("TimeSync", "❌ All time sync methods failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AttendanceActivity, "⚠️ Using local time (sync failed)", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * Get reliable server time instantly (no network call after initial sync)
     */
    private fun getReliableServerTime(): Calendar {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))

        // Use synced time if available (within last hour)
        if (lastSyncTime > 0 && System.currentTimeMillis() - lastSyncTime < 3600000) {
            cal.timeInMillis = System.currentTimeMillis() + serverTimeOffset
        } else {
            // Use local time if sync is old (re-sync in background)
            cal.timeInMillis = System.currentTimeMillis()
            syncServerTime() // Re-sync in background
        }

        return cal
    }

    private fun updateDateTimeDisplay() {
        val cal = getReliableServerTime()
        val sdf = SimpleDateFormat("MMMM dd, yyyy 'at' h:mm:ss a", Locale.ENGLISH)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Manila")
        val formattedDate = sdf.format(cal.time) + " UTC+8"
        tvDate.text = formattedDate

        val dbDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
        loadTodayAttendanceData(dbDate)
    }

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isFaceVerified = true
                faceFailureCount = 0
                runOnUiThread {
                    btnVerify.text = "✅ Identity Verified"
                    btnVerify.isEnabled = false
                    btnVerify.setBackgroundColor(Color.parseColor("#4CAF50"))
                    tvCameraStatus.text = "✅ Verified via Biometrics"
                    updateClockButtonUI()
                }
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Verification Fallback")
            .setSubtitle("Face recognition failed. Use Biometrics/PIN to continue.")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    private fun startAutoCaptureLoop() {
        if (!isScanning) return
        val bitmap = previewView.bitmap ?: run {
            Handler(Looper.getMainLooper()).postDelayed({ startAutoCaptureLoop() }, 100)
            return
        }

        val image = InputImage.fromBitmap(bitmap, 0)
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        tvCameraStatus.text = "Status : Blink your eyes"
        btnVerify.text = "Scanning..."

        FaceDetection.getClient(options).process(image).addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                val face = faces[0]
                val leftEye = face.leftEyeOpenProbability ?: 1f
                val rightEye = face.rightEyeOpenProbability ?: 1f

                if (leftEye < EYE_CLOSED_THRESHOLD || rightEye < EYE_CLOSED_THRESHOLD) {
                    isScanning = false
                    performIdentityMatch(bitmap, face.boundingBox)
                } else {
                    Handler(Looper.getMainLooper()).postDelayed({ startAutoCaptureLoop() }, 50)
                }
            } else {
                tvCameraStatus.text = "Status : No face detected"
                Handler(Looper.getMainLooper()).postDelayed({ startAutoCaptureLoop() }, 200)
            }
        }
    }

    private fun performIdentityMatch(bitmap: Bitmap, boundingBox: android.graphics.Rect) {
        try {
            val faceBitmap = Bitmap.createBitmap(bitmap, max(0, boundingBox.left), max(0, boundingBox.top),
                min(boundingBox.width(), bitmap.width - boundingBox.left), min(boundingBox.height(), bitmap.height - boundingBox.top))
            val liveVector = extractFaceEmbedding(faceBitmap)

            if (savedFaceVector != null) {
                var dot = 0f
                for (i in liveVector.indices) dot += liveVector[i] * savedFaceVector!![i]

                if (dot > SIMILARITY_THRESHOLD) {
                    isFaceVerified = true
                    faceFailureCount = 0
                    btnVerify.text = "✅ Identity Verified"
                    btnVerify.isEnabled = false
                    btnVerify.setBackgroundColor(Color.parseColor("#4CAF50"))
                    tvCameraStatus.text = "✅ Face Verified"
                    updateClockButtonUI()
                } else {
                    faceFailureCount++
                    Toast.makeText(this, "Face Not Recognized ($faceFailureCount/3)", Toast.LENGTH_SHORT).show()
                    if (faceFailureCount >= 3) {
                        biometricPrompt.authenticate(promptInfo)
                    } else {
                        isScanning = false
                        btnVerify.isEnabled = true
                        btnVerify.text = "Retry (Blink Again)"
                    }
                }
            }
        } catch (e: Exception) {
            isScanning = false
            btnVerify.isEnabled = true
        }
    }

    private fun loadTodayAttendanceData(dateStr: String) {
        db.collection("attendance").document(userId).collection("records").document(dateStr)
            .get().addOnSuccessListener { doc ->
                val currentCal = getReliableServerTime()
                val currentHour = currentCal.get(Calendar.HOUR_OF_DAY)
                val currentMinute = currentCal.get(Calendar.MINUTE)
                val currentTotalMinutes = currentHour * 60 + currentMinute

                // Determine if it's afternoon shift time based on 12:30 PM cutoff
                val morningCutoffMinutes = MORNING_CUTOFF_HOUR * 60 + MORNING_CUTOFF_MINUTE
                val isAfternoon = currentTotalMinutes >= morningCutoffMinutes

                Log.d("AttendanceActivity", "Current time: $currentHour:$currentMinute ($currentTotalMinutes mins), isAfternoon: $isAfternoon, cutoff: $morningCutoffMinutes")

                if (doc.exists()) {
                    val totalHrs = doc.getDouble("total_hours") ?: 0.0
                    tvStatus.text = "Status : ${doc.getString("status")}"
                    tvHours.text = String.format("%.2f Hours", totalHrs)

                    // Update exit status based on current shift
                    if (!isAfternoon) {
                        val amExit = doc.getTimestamp("am_exit")
                        if (amExit != null) {
                            val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                            tvExitedArea.text = sdf.format(amExit.toDate())
                            tvExitedArea.setTextColor(Color.parseColor("#FF5722"))
                        } else {
                            tvExitedArea.text = "No exit recorded"
                            tvExitedArea.setTextColor(Color.parseColor("#4CAF50"))
                        }
                    } else {
                        val pmExit = doc.getTimestamp("pm_exit")
                        if (pmExit != null) {
                            val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                            tvExitedArea.text = sdf.format(pmExit.toDate())
                            tvExitedArea.setTextColor(Color.parseColor("#FF5722"))
                        } else {
                            tvExitedArea.text = "No exit recorded"
                            tvExitedArea.setTextColor(Color.parseColor("#4CAF50"))
                        }
                    }

                    // Handle break time button visibility and state
                    if (!isAfternoon) {
                        // AM SHIFT (Current time is before 12:30 PM)
                        val amBreakStart = doc.getTimestamp("am_break_start")
                        val amBreakEnd = doc.getTimestamp("am_break_end")

                        if (doc.getTimestamp("am_in") != null && doc.getTimestamp("am_out") == null) {
                            // Currently clocked in for AM
                            currentShift = "AM"
                            btnBreakTime.visibility = android.view.View.VISIBLE

                            if (amBreakStart != null && amBreakEnd == null) {
                                // Currently on break
                                isOnBreak = true
                                btnBreakTime.text = "End Break (AM)"
                                btnBreakTime.setBackgroundColor(Color.parseColor("#FF9800"))
                                btnTimeIn.isEnabled = false
                            } else {
                                // Not on break
                                isOnBreak = false
                                btnBreakTime.text = "Start Break (AM)"
                                btnBreakTime.setBackgroundColor(Color.parseColor("#2196F3"))
                            }
                        } else {
                            btnBreakTime.visibility = android.view.View.GONE
                        }

                        if (doc.getTimestamp("am_out") != null) {
                            btnTimeIn.text = "AM Shift Done"
                            btnTimeIn.isEnabled = false
                        } else {
                            btnTimeIn.text = if (doc.getTimestamp("am_in") != null) "Time Out (AM)" else "Time In (AM)"
                        }
                    } else {
                        // PM SHIFT (Current time is after 12:30 PM)
                        val pmBreakStart = doc.getTimestamp("pm_break_start")
                        val pmBreakEnd = doc.getTimestamp("pm_break_end")

                        if (doc.getTimestamp("pm_in") != null && doc.getTimestamp("pm_out") == null) {
                            // Currently clocked in for PM
                            currentShift = "PM"
                            btnBreakTime.visibility = android.view.View.VISIBLE

                            if (pmBreakStart != null && pmBreakEnd == null) {
                                // Currently on break
                                isOnBreak = true
                                btnBreakTime.text = "End Break (PM)"
                                btnBreakTime.setBackgroundColor(Color.parseColor("#FF9800"))
                                btnTimeIn.isEnabled = false
                            } else {
                                // Not on break
                                isOnBreak = false
                                btnBreakTime.text = "Start Break (PM)"
                                btnBreakTime.setBackgroundColor(Color.parseColor("#2196F3"))
                            }
                        } else {
                            btnBreakTime.visibility = android.view.View.GONE
                            isOnBreak = false
                        }

                        // KEY FIX: After 12:30 PM, show PM options regardless of AM status
                        if (doc.getTimestamp("pm_out") != null) {
                            // PM already clocked out
                            btnTimeIn.text = "Work Completed"
                            btnTimeIn.isEnabled = false
                        } else if (doc.getTimestamp("pm_in") != null) {
                            // Already clocked in for PM, show time out option
                            btnTimeIn.text = "Time Out (PM)"
                        } else {
                            // Not yet clocked in for PM - ALLOW TIME IN
                            btnTimeIn.text = "Time In (PM)"
                        }
                    }
                } else {
                    // No record exists for today
                    btnTimeIn.text = if (isAfternoon) "Time In (PM)" else "Time In (AM)"
                    btnBreakTime.visibility = android.view.View.GONE
                    tvExitedArea.text = "No exit recorded"
                    tvExitedArea.setTextColor(Color.parseColor("#666666"))
                }
                updateClockButtonUI()
            }
    }

    private fun handleBreakTime() {
        val cal = getReliableServerTime()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
        val ref = db.collection("attendance").document(userId).collection("records").document(todayDate)

        ref.get().addOnSuccessListener { doc ->
            if (!doc.exists()) return@addOnSuccessListener

            val shift = currentShift ?: return@addOnSuccessListener

            if (shift == "AM") {
                val amBreakStart = doc.getTimestamp("am_break_start")
                val amBreakEnd = doc.getTimestamp("am_break_end")

                if (amBreakStart == null) {
                    // Start break
                    val updates = hashMapOf<String, Any>("am_break_start" to Timestamp(cal.time))
                    ref.update(updates).addOnSuccessListener {
                        isOnBreak = true
                        Toast.makeText(this, "☕ Break time started (AM)", Toast.LENGTH_SHORT).show()
                        stopGeofence()
                        loadTodayAttendanceData(todayDate)
                    }
                } else if (amBreakEnd == null) {
                    // End break
                    val breakStartTime = amBreakStart.toDate().time
                    val breakEndTime = cal.timeInMillis
                    val breakDuration = (breakEndTime - breakStartTime) / (1000.0 * 60 * 60)

                    val updates = hashMapOf<String, Any>(
                        "am_break_end" to Timestamp(cal.time),
                        "am_break_duration" to breakDuration
                    )
                    ref.update(updates).addOnSuccessListener {
                        isOnBreak = false
                        Toast.makeText(this, "✅ Break time ended (AM)", Toast.LENGTH_SHORT).show()
                        startGeofence("AM")
                        loadTodayAttendanceData(todayDate)
                    }
                }
            } else if (shift == "PM") {
                val pmBreakStart = doc.getTimestamp("pm_break_start")
                val pmBreakEnd = doc.getTimestamp("pm_break_end")

                if (pmBreakStart == null) {
                    // Start break
                    val updates = hashMapOf<String, Any>("pm_break_start" to Timestamp(cal.time))
                    ref.update(updates).addOnSuccessListener {
                        isOnBreak = true
                        Toast.makeText(this, "☕ Break time started (PM)", Toast.LENGTH_SHORT).show()
                        stopGeofence()
                        loadTodayAttendanceData(todayDate)
                    }
                } else if (pmBreakEnd == null) {
                    // End break
                    val breakStartTime = pmBreakStart.toDate().time
                    val breakEndTime = cal.timeInMillis
                    val breakDuration = (breakEndTime - breakStartTime) / (1000.0 * 60 * 60)

                    val updates = hashMapOf<String, Any>(
                        "pm_break_end" to Timestamp(cal.time),
                        "pm_break_duration" to breakDuration
                    )
                    ref.update(updates).addOnSuccessListener {
                        isOnBreak = false
                        Toast.makeText(this, "✅ Break time ended (PM)", Toast.LENGTH_SHORT).show()
                        startGeofence("PM")
                        loadTodayAttendanceData(todayDate)
                    }
                }
            }
        }
    }

    private fun startGeofence(shift: String) {
        val lat = ojtLat ?: return
        val lng = ojtLng ?: return

        // Save shift info to SharedPreferences for GeofenceReceiver
        val prefs = getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("active_user_id", userId)
            putString("current_shift", shift)
            putFloat("lat", lat.toFloat())
            putFloat("lng", lng.toFloat())
            putBoolean("geofence_active", true)
            apply()
        }

        // Start GeofenceActivity in background silently - NO WINDOW, NO ANIMATION
        val intent = Intent(this, GeofenceActivity::class.java)
        intent.putExtra("latitude", lat)
        intent.putExtra("longitude", lng)
        intent.putExtra("user_id", userId)
        intent.putExtra("shift", shift)
        intent.putExtra("silent_mode", true)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_ANIMATION or
                Intent.FLAG_ACTIVITY_NO_HISTORY or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Error starting geofence activity", e)
        }
    }

    private fun stopGeofence() {
        // Update SharedPreferences to indicate geofence should stop
        val prefs = getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("geofence_active", false)
            apply()
        }

        // Remove geofences
        try {
            geofencingClient.removeGeofences(listOf("ATTENDANCE_GEOFENCE"))
                .addOnSuccessListener {
                    Log.d("AttendanceActivity", "Geofence stopped successfully")
                }
                .addOnFailureListener { e ->
                    Log.e("AttendanceActivity", "Failed to stop geofence", e)
                }
        } catch (e: Exception) {
            Log.e("AttendanceActivity", "Error stopping geofence", e)
        }
    }

    private fun isMockLocationEnabled(location: Location): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }
    }

    private fun isWithinTimeWindow(cal: Calendar): Boolean {
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Determine which shift based on time (12:30 PM cutoff)
        val morningCutoffMinutes = MORNING_CUTOFF_HOUR * 60 + MORNING_CUTOFF_MINUTE
        val isAfternoonTime = currentTotalMinutes >= morningCutoffMinutes

        if (!isAfternoonTime) {
            // AM SHIFT - Check if we're within the 40-minute early window
            if (amEarlyHour != null && amEarlyMinute != null && amScheduledHour != null && amScheduledMinute != null) {
                val earlyWindowMinutes = amEarlyHour!! * 60 + amEarlyMinute!!

                // Too early (before -40 mins window)
                if (currentTotalMinutes < earlyWindowMinutes) {
                    val minutesUntilWindow = earlyWindowMinutes - currentTotalMinutes
                    val hoursUntil = minutesUntilWindow / 60
                    val minsUntil = minutesUntilWindow % 60

                    val timeMsg = if (hoursUntil > 0) {
                        "$hoursUntil hour(s) and $minsUntil minute(s)"
                    } else {
                        "$minsUntil minute(s)"
                    }

                    Toast.makeText(this, "⏰ Too early! AM time-in opens in $timeMsg", Toast.LENGTH_LONG).show()
                    return false
                }
                return true
            }
        } else {
            // PM SHIFT - Check if we're within the 40-minute early window
            if (pmEarlyHour != null && pmEarlyMinute != null && pmScheduledHour != null && pmScheduledMinute != null) {
                val earlyWindowMinutes = pmEarlyHour!! * 60 + pmEarlyMinute!!

                // Too early (before -40 mins window)
                if (currentTotalMinutes < earlyWindowMinutes) {
                    val minutesUntilWindow = earlyWindowMinutes - currentTotalMinutes
                    val hoursUntil = minutesUntilWindow / 60
                    val minsUntil = minutesUntilWindow % 60

                    val timeMsg = if (hoursUntil > 0) {
                        "$hoursUntil hour(s) and $minsUntil minute(s)"
                    } else {
                        "$minsUntil minute(s)"
                    }

                    Toast.makeText(this, "⏰ Too early! PM time-in opens in $timeMsg", Toast.LENGTH_LONG).show()
                    return false
                }
                return true
            }
        }

        // If schedule not loaded yet, allow (failsafe)
        return true
    }

    private fun handleAttendanceSubmission() {
        if (!isFaceVerified || !isInsideGeofence) {
            Toast.makeText(this, "⚠️ Please verify your face and be inside the area", Toast.LENGTH_SHORT).show()
            return
        }

        // CHECK FOR MOCK LOCATION BEFORE PROCEEDING
        if (isMockLocationDetected) {
            android.app.AlertDialog.Builder(this)
                .setTitle("🚫 Mock Location Detected")
                .setMessage("Please disable fake GPS or mock location apps before clocking in/out.\n\nThis is required to ensure accurate attendance tracking.")
                .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                .setCancelable(false)
                .create()
                .show()
            return
        }

        val cal = getReliableServerTime()
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)
        val currentTotalMinutes = currentHour * 60 + currentMinute

        // Determine if it's afternoon shift time based on 12:30 PM cutoff
        val morningCutoffMinutes = MORNING_CUTOFF_HOUR * 60 + MORNING_CUTOFF_MINUTE
        val isAfternoon = currentTotalMinutes >= morningCutoffMinutes

        Log.d("AttendanceActivity", "Submission - Current time: $currentHour:$currentMinute, isAfternoon: $isAfternoon")

        val ref = db.collection("attendance").document(userId).collection("records").document(todayDate)

        ref.get().addOnSuccessListener { doc ->
            if (!isAfternoon) {
                // AM SHIFT - Current time is before 12:30 PM
                if (!doc.exists() || doc.getTimestamp("am_in") == null) {
                    // TIME IN (AM) - Check if within window
                    if (!isWithinTimeWindow(cal)) {
                        return@addOnSuccessListener
                    }

                    val data = hashMapOf<String, Any>("am_in" to Timestamp(cal.time), "status" to "Clocked In (AM)", "total_hours" to 0.0)
                    ref.set(data).addOnSuccessListener { onSuccessfulSubmit(todayDate, true, "AM", cal) }
                } else if (doc.getTimestamp("am_out") == null) {
                    // TIME OUT (AM) - Calculate total hours minus break time
                    val amIn = doc.getTimestamp("am_in")!!.toDate().time

                    // MORNING CUTOFF RULE: Use whichever comes first (actual time-out OR 12:30 PM)
                    val cutoffCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
                    cutoffCal.timeInMillis = cal.timeInMillis
                    cutoffCal.set(Calendar.HOUR_OF_DAY, MORNING_CUTOFF_HOUR)
                    cutoffCal.set(Calendar.MINUTE, MORNING_CUTOFF_MINUTE)
                    cutoffCal.set(Calendar.SECOND, 0)

                    val actualTimeOut = cal.timeInMillis
                    val cutoffTime = cutoffCal.timeInMillis
                    val endTime = minOf(actualTimeOut, cutoffTime)

                    var diff = (endTime - amIn) / (1000.0 * 60 * 60)

                    // Subtract break time if exists
                    val breakDuration = doc.getDouble("am_break_duration") ?: 0.0
                    diff -= breakDuration

                    val updates = hashMapOf<String, Any>("am_out" to Timestamp(cal.time), "am_hours" to diff, "total_hours" to diff, "status" to "Halfday")
                    ref.update(updates).addOnSuccessListener { onSuccessfulSubmit(todayDate, false, "AM", cal) }
                }
            } else {
                // PM SHIFT - Current time is after 12:30 PM
                if (doc.getTimestamp("pm_in") == null) {
                    // TIME IN (PM) - Check if within window
                    if (!isWithinTimeWindow(cal)) {
                        return@addOnSuccessListener
                    }

                    val data = hashMapOf<String, Any>("pm_in" to Timestamp(cal.time), "status" to "Clocked In (PM)")
                    if (!doc.exists()) {
                        // Create new record for PM only (AM is null)
                        ref.set(data).addOnSuccessListener { onSuccessfulSubmit(todayDate, true, "PM", cal) }
                    } else {
                        // Add PM to existing record (AM already exists)
                        ref.update(data).addOnSuccessListener { onSuccessfulSubmit(todayDate, true, "PM", cal) }
                    }
                } else if (doc.getTimestamp("pm_out") == null) {
                    // TIME OUT (PM) - Calculate total hours minus break time
                    val pmIn = doc.getTimestamp("pm_in")!!.toDate().time

                    // AFTERNOON CUTOFF RULE: Use whichever comes first (actual time-out OR 7:00 PM)
                    val cutoffCal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
                    cutoffCal.timeInMillis = cal.timeInMillis
                    cutoffCal.set(Calendar.HOUR_OF_DAY, AFTERNOON_CUTOFF_HOUR)
                    cutoffCal.set(Calendar.MINUTE, AFTERNOON_CUTOFF_MINUTE)
                    cutoffCal.set(Calendar.SECOND, 0)

                    val actualTimeOut = cal.timeInMillis
                    val cutoffTime = cutoffCal.timeInMillis
                    val endTime = minOf(actualTimeOut, cutoffTime)

                    var pmHours = (endTime - pmIn) / (1000.0 * 60 * 60)

                    // Subtract break time if exists
                    val breakDuration = doc.getDouble("pm_break_duration") ?: 0.0
                    pmHours -= breakDuration

                    val amHours = doc.getDouble("am_hours") ?: 0.0
                    val updates = hashMapOf<String, Any>("pm_out" to Timestamp(cal.time), "pm_hours" to pmHours, "total_hours" to (amHours + pmHours), "status" to if (amHours > 0) "Wholeday" else "Halfday")
                    ref.update(updates).addOnSuccessListener { onSuccessfulSubmit(todayDate, false, "PM", cal) }
                }
            }
        }
    }

    private fun onSuccessfulSubmit(dateStr: String, isTimeIn: Boolean, shift: String, cal: Calendar) {
        // Show modal dialog
        if (isTimeIn) {
            showTimeInModal(shift, cal)
        } else {
            showTimeOutModal(shift, cal)
        }

        // Activate geofence silently ONLY when it's a Time In
        if (isTimeIn && ojtLat != null && ojtLng != null) {
            startGeofence(shift)
        }

        resetVerificationState()
        loadTodayAttendanceData(dateStr)
    }

    private fun showTimeInModal(shift: String, cal: Calendar) {
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentMinute = cal.get(Calendar.MINUTE)

        val scheduledTime = if (shift == "AM") amInTime else pmInTime
        val greeting = if (shift == "AM") "Good Morning" else "Good Afternoon"

        var status = "On Time"
        var statusColor = "#4CAF50"
        var message = "You're right on schedule!"

        if (scheduledTime != null) {
            try {
                // Parse the time properly - handle both 12-hour and 24-hour formats
                var schedHour = 0
                var schedMinute = 0

                if (scheduledTime.contains(":")) {
                    val parts = scheduledTime.split(":")
                    schedHour = parts[0].toInt()
                    schedMinute = parts[1].toInt()

                    // If PM shift and hour is 1-12, convert to 24-hour (13-24)
                    if (shift == "PM" && schedHour < 12) {
                        schedHour += 12
                    }
                }

                val currentMinutes = currentHour * 60 + currentMinute
                val scheduledMinutes = schedHour * 60 + schedMinute
                val diff = currentMinutes - scheduledMinutes

                when {
                    diff < -5 -> {
                        status = "Advance"
                        statusColor = "#2196F3"
                        message = "Wow! You're ${-diff} minutes early. Great enthusiasm!"
                    }
                    diff > 5 -> {
                        status = "Late"
                        statusColor = "#FF5722"
                        message = "You're $diff minutes late. Try to arrive on time tomorrow!"
                    }
                    else -> {
                        status = "On Time"
                        statusColor = "#4CAF50"
                        message = "Perfect timing! Keep it up!"
                    }
                }
            } catch (e: Exception) {
                Log.e("AttendanceActivity", "Error parsing schedule time", e)
            }
        }

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("✅ Time In Successful")
            .setMessage("$greeting!\n\nStatus: $status\nShift: $shift\n\n$message")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor(statusColor))
    }

    private fun showTimeOutModal(shift: String, cal: Calendar) {
        val sdf = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
        val timeNow = sdf.format(cal.time)

        val farewell = if (shift == "AM") "See you in the afternoon!" else "Great work today!"
        val message = "You've successfully timed out at $timeNow.\n\n$farewell"

        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("👋 Time Out Successful")
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()

        dialog.show()
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#275BB0"))
    }

    private fun resetVerificationState() {
        isFaceVerified = false
        faceFailureCount = 0
        isScanning = false
        runOnUiThread {
            btnVerify.isEnabled = true
            btnVerify.text = "Verify Face"
            btnVerify.setBackgroundColor(Color.parseColor("#2196F3"))
            tvCameraStatus.text = "Ready to scan"
            updateClockButtonUI()
        }
    }

    private fun updateClockButtonUI() {
        // Enable button if:
        // 1. Face verified AND inside geofence AND no mock location
        // 2. Not on break
        // 3. Button text is not "AM Shift Done" or "Work Completed"
        val canEnable = isFaceVerified && isInsideGeofence && !isMockLocationDetected && !isOnBreak

        // Check if button is showing time in options (not done/completed)
        val isTimeInOption = btnTimeIn.text.toString().contains("Time In") ||
                btnTimeIn.text.toString().contains("Time Out")

        btnTimeIn.isEnabled = canEnable && isTimeInOption

        if (btnTimeIn.isEnabled) {
            btnTimeIn.alpha = 1.0f
            btnTimeIn.setBackgroundColor(Color.parseColor("#4CAF50"))
            btnTimeIn.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            btnTimeIn.alpha = 0.5f
            btnTimeIn.setBackgroundColor(Color.parseColor("#CCCCCC"))
            btnTimeIn.setTextColor(Color.parseColor("#999999"))
        }
    }

    private fun setupLiveLocation() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(res: LocationResult) {
                    val loc = res.lastLocation ?: return

                    // CHECK FOR MOCK LOCATION
                    isMockLocationDetected = isMockLocationEnabled(loc)

                    if (isMockLocationDetected) {
                        tvLocationStatus.text = "⚠️ Mock Location Detected"
                        tvLocationStatus.setTextColor(Color.parseColor("#FF5722"))
                        tvDistance.text = "Distance: --- (Fake GPS)"
                    } else if (ojtLat != null) {
                        val dist = distanceBetween(loc.latitude, loc.longitude, ojtLat!!, ojtLng!!)
                        tvDistance.text = "Distance: %.2f m".format(dist)
                        isInsideGeofence = dist <= GEOFENCE_RADIUS_METERS
                        tvLocationStatus.text = if (isInsideGeofence) "Inside Area" else "Outside Area"
                        tvLocationStatus.setTextColor(if (isInsideGeofence) Color.GREEN else Color.RED)
                    }

                    updateClockButtonUI()
                }
            }, Looper.getMainLooper())
        }
    }

    private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            } catch (e: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun loadUserData() {
        db.collection("user").document(userId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                tvUserName.text = "User : ${doc.getString("firstname")} ${doc.getString("lastname")}"
                val vec = doc.get("face_vector") as? List<Double>
                if (vec != null) savedFaceVector = FloatArray(vec.size) { i -> vec[i].toFloat() }
            }
        }
    }

    private fun loadOjtLocation() {
        db.collection("ojt_details").whereEqualTo("user_id", userId).get().addOnSuccessListener { docs ->
            if (!docs.isEmpty) {
                val doc = docs.documents[0]
                ojtLat = doc.getDouble("lat")
                ojtLng = doc.getDouble("lng")
                amInTime = doc.getString("am_in_time")
                pmInTime = doc.getString("pm_in_time")
                tvCompanyName.text = "Company : ${doc.getString("company_name")}"

                // Calculate time windows with -40 minutes early allowance
                try {
                    if (amInTime != null) {
                        val amParts = amInTime!!.split(":")
                        amScheduledHour = amParts[0].toInt()
                        amScheduledMinute = amParts[1].toInt()

                        // Calculate -40 minutes for early window
                        val totalMinutes = amScheduledHour!! * 60 + amScheduledMinute!! - 40
                        amEarlyHour = totalMinutes / 60
                        amEarlyMinute = totalMinutes % 60

                        Log.d("AttendanceActivity", "AM Schedule: $amInTime -> Early window: $amEarlyHour:$amEarlyMinute")
                    }

                    if (pmInTime != null) {
                        val pmParts = pmInTime!!.split(":")
                        var pmHour = pmParts[0].toInt()
                        pmScheduledMinute = pmParts[1].toInt()

                        // Convert PM time to 24-hour format if needed
                        // If it's 1-12, it means 1:00 PM - 12:59 PM, so add 12
                        if (pmHour < 12) {
                            pmHour += 12
                        }
                        pmScheduledHour = pmHour

                        // Calculate -40 minutes for early window
                        val totalMinutes = pmScheduledHour!! * 60 + pmScheduledMinute!! - 40
                        pmEarlyHour = totalMinutes / 60
                        pmEarlyMinute = totalMinutes % 60

                        Log.d("AttendanceActivity", "PM Schedule: $pmInTime (converted to $pmScheduledHour:$pmScheduledMinute) -> Early window: $pmEarlyHour:$pmEarlyMinute")
                    }
                } catch (e: Exception) {
                    Log.e("AttendanceActivity", "Error parsing time schedule", e)
                }
            }
        }
    }

    private fun extractFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val shape = interpreter!!.getInputTensor(0).shape()
        val resized = Bitmap.createScaledBitmap(faceBitmap, shape[2], shape[1], true)
        val input = ByteBuffer.allocateDirect(shape[2] * shape[1] * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(shape[2] * shape[1])
        resized.getPixels(pixels, 0, shape[2], 0, 0, shape[2], shape[1])
        for (p in pixels) {
            input.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
            input.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }
        val output = Array(1) { FloatArray(interpreter!!.getOutputTensor(0).shape()[1]) }
        interpreter!!.run(input, output)
        var sum = 0f
        for (x in output[0]) sum += x * x
        val norm = sqrt(sum)
        for (i in output[0].indices) output[0][i] /= norm
        return output[0]
    }

    private fun loadModel() {
        try {
            val fd = assets.openFd("facenet_512.tflite")
            val modelBuffer = FileInputStream(fd.fileDescriptor).channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            interpreter = Interpreter(modelBuffer)
        } catch (e: Exception) { }
    }
}