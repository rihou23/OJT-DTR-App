package com.example.myapplication

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.*
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class RegisterActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private var interpreter: Interpreter? = null
    private var faceEmbedding: FloatArray? = null

    // UI Components
    private lateinit var layoutStep1: ConstraintLayout
    private lateinit var layoutStep2: ConstraintLayout
    private lateinit var layoutStep3: ConstraintLayout
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var spnYearSection: Spinner

    // Camera Components
    private lateinit var previewView: PreviewView
    private lateinit var faceOverlay: FaceOverlayView
    private lateinit var tvFaceStatus: TextView
    private lateinit var btnScanFace: Button
    private lateinit var btnRegister: Button
    private lateinit var cameraExecutor: ExecutorService
    private var faceDetector: FaceDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = FirebaseFirestore.getInstance()
        cameraExecutor = Executors.newSingleThreadExecutor()

        initViews()
        loadModel()
        setupFaceDetector()

        // Navigation Step 1 -> 2
        findViewById<Button>(R.id.btnNext1).setOnClickListener {
            val firstName = etFirstName.text.toString().trim()
            val lastName = etLastName.text.toString().trim()

            when {
                firstName.isEmpty() -> {
                    Toast.makeText(this, "Please enter first name", Toast.LENGTH_SHORT).show()
                }
                lastName.isEmpty() -> {
                    Toast.makeText(this, "Please enter last name", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    checkIfNameExists(firstName, lastName)
                }
            }
        }

        // Back Button Step 1
        findViewById<Button>(R.id.btnBack1).setOnClickListener {
            finish()
        }

        // Navigation Step 2 -> 3
        findViewById<Button>(R.id.btnNext2).setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            when {
                username.isEmpty() -> {
                    Toast.makeText(this, "Please enter username", Toast.LENGTH_SHORT).show()
                }
                password.isEmpty() -> {
                    Toast.makeText(this, "Please enter password", Toast.LENGTH_SHORT).show()
                }
                password.length < 6 -> {
                    Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    layoutStep2.visibility = View.GONE
                    layoutStep3.visibility = View.VISIBLE
                    checkPermissionAndStartCamera()
                }
            }
        }

        // Back Button Step 2
        findViewById<Button>(R.id.btnBack2).setOnClickListener {
            layoutStep2.visibility = View.GONE
            layoutStep1.visibility = View.VISIBLE
            stopCamera()
        }

        // Capture Button
        btnScanFace.setOnClickListener { fastCaptureFace() }

        // Final Registration Button
        btnRegister.setOnClickListener { registerUser() }

        // Back Button Step 3
        findViewById<Button>(R.id.btnBack3).setOnClickListener {
            layoutStep3.visibility = View.GONE
            layoutStep2.visibility = View.VISIBLE
            faceEmbedding = null
            stopCamera()
        }
    }

    private fun initViews() {
        layoutStep1 = findViewById(R.id.layoutStep1)
        layoutStep2 = findViewById(R.id.layoutStep2)
        layoutStep3 = findViewById(R.id.layoutStep3)
        etFirstName = findViewById(R.id.etFirstName)
        etMiddleName = findViewById(R.id.etMiddleName)
        etLastName = findViewById(R.id.etLastName)
        spnYearSection = findViewById(R.id.spnYearSection)
        etUsername = findViewById(R.id.etRegUsername)
        etPassword = findViewById(R.id.etRegPassword)
        previewView = findViewById(R.id.previewView)
        faceOverlay = findViewById(R.id.faceOverlay)
        tvFaceStatus = findViewById(R.id.tvStatus)
        btnScanFace = findViewById(R.id.btnScan)
        btnRegister = findViewById(R.id.btnFinalRegister)

        val sections = arrayOf("BSIT 4-1", "BSIT 4-2", "BSIT Irreg", "BSBA 4-1", "BSBA 4-2", "BSBA Irreg")
        spnYearSection.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
    }

    private fun checkIfNameExists(firstName: String, lastName: String) {
        db.collection("user")
            .whereEqualTo("firstname", firstName)
            .whereEqualTo("lastname", lastName)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Toast.makeText(this, "This name is already registered", Toast.LENGTH_SHORT).show()
                } else {
                    layoutStep1.visibility = View.GONE
                    layoutStep2.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                Log.e("NameCheck", "Error checking name", e)
                Toast.makeText(this, "Error checking name availability", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupFaceDetector() {
        try {
            faceDetector?.close()
        } catch (e: Exception) {
            Log.e("FaceDetector", "Error closing previous detector", e)
        }

        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
        faceDetector = FaceDetection.getClient(options)
    }

    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            setupFaceDetector()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy -> processImageProxy(imageProxy) }
                    }

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageAnalyzer)
            } catch (e: Exception) {
                Log.e("CameraX", "Error binding camera", e)
                runOnUiThread {
                    Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraX", "Error stopping camera", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        faceDetector?.process(image)
            ?.addOnSuccessListener { faces ->
                if (faces.isEmpty()) {
                    updateUI("No face detected", false)
                } else {
                    validateFace(faces[0], imageProxy)
                }
            }
            ?.addOnFailureListener { e ->
                Log.e("FaceDetection", "Face detection failed", e)
            }
            ?.addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun validateFace(face: Face, imageProxy: ImageProxy) {
        val faceWidthRatio = face.boundingBox.width().toFloat() / imageProxy.width.toFloat()

        var error: String? = null

        if (faceWidthRatio < 0.25) {
            error = "Move closer to the camera"
        }

        runOnUiThread {
            if (error != null) {
                updateUI(error, false)
            } else {
                updateUI("Face detected! Press Capture", true)
            }
        }
    }

    private fun updateUI(msg: String, isValid: Boolean) {
        tvFaceStatus.text = msg
        tvFaceStatus.setTextColor(if (isValid) Color.parseColor("#34A853") else Color.parseColor("#EA4335"))
        faceOverlay.setStatus(isValid)
        btnScanFace.isEnabled = isValid
    }

    private fun fastCaptureFace() {
        val bitmap = previewView.bitmap
        if (bitmap == null) {
            Toast.makeText(this, "Camera not ready, please wait", Toast.LENGTH_SHORT).show()
            return
        }

        tvFaceStatus.text = "Processing..."
        btnScanFace.isEnabled = false

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        faceDetector?.process(inputImage)
            ?.addOnSuccessListener { faces ->
                if (faces.isNotEmpty()) {
                    val face = faces[0]
                    val faceBitmap = cropToFace(bitmap, face.boundingBox)
                    faceEmbedding = extractFaceEmbedding(faceBitmap)

                    tvFaceStatus.text = "✅ Face Captured Successfully!"
                    tvFaceStatus.setTextColor(Color.parseColor("#34A853"))
                    btnScanFace.visibility = View.GONE
                    btnRegister.visibility = View.VISIBLE
                } else {
                    Toast.makeText(this, "Capture failed, ensure your face is visible", Toast.LENGTH_SHORT).show()
                    btnScanFace.isEnabled = true
                }
            }
            ?.addOnFailureListener { e ->
                Log.e("FaceCapture", "Face capture failed", e)
                Toast.makeText(this, "Capture error: ${e.message}", Toast.LENGTH_SHORT).show()
                btnScanFace.isEnabled = true
            }
    }

    private fun cropToFace(bitmap: Bitmap, rect: Rect): Bitmap {
        val padding = (rect.width() * 0.1).toInt()
        val left = (rect.left - padding).coerceAtLeast(0)
        val top = (rect.top - padding).coerceAtLeast(0)
        val right = (rect.right + padding).coerceAtMost(bitmap.width)
        val bottom = (rect.bottom + padding).coerceAtMost(bitmap.height)

        val width = right - left
        val height = bottom - top

        return Bitmap.createBitmap(bitmap, left, top, width, height)
    }

    private fun registerUser() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        val firstname = etFirstName.text.toString().trim()
        val middlename = etMiddleName.text.toString().trim()
        val lastname = etLastName.text.toString().trim()
        val section = spnYearSection.selectedItem.toString()

        if (faceEmbedding == null) {
            Toast.makeText(this, "Please capture your face first", Toast.LENGTH_SHORT).show()
            return
        }

        btnRegister.isEnabled = false
        btnRegister.text = "Registering..."

        val user = hashMapOf(
            "username" to username,
            "password" to sha256(password),
            "firstname" to firstname,
            "middlename" to middlename,
            "lastname" to lastname,
            "nickname" to firstname,
            "section" to section,
            "ojt_details" to "none",
            "face_vector" to faceEmbedding!!.toList(),
            "created_at" to System.currentTimeMillis()
        )

        db.collection("user").add(user)
            .addOnSuccessListener {
                Log.d("Registration", "User registered successfully")
                Toast.makeText(this, "Account created successfully!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("Registration", "Registration failed", e)
                Toast.makeText(this, "Registration failed: ${e.message}", Toast.LENGTH_LONG).show()
                btnRegister.isEnabled = true
                btnRegister.text = "Complete Registration"
            }
    }

    private fun loadModel() {
        try {
            val fd = assets.openFd("facenet_512.tflite")
            val inputStream = FileInputStream(fd.fileDescriptor)
            val modelBuffer = inputStream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            interpreter = Interpreter(modelBuffer)
            Log.d("TFLite", "Model loaded successfully")
        } catch (e: Exception) {
            Log.e("TFLite", "Error loading model", e)
            Toast.makeText(this, "Failed to load AI model", Toast.LENGTH_SHORT).show()
        }
    }

    private fun extractFaceEmbedding(faceBitmap: Bitmap): FloatArray {
        val resized = Bitmap.createScaledBitmap(faceBitmap, 160, 160, true)
        val buffer = ByteBuffer.allocateDirect(160 * 160 * 3 * 4).order(ByteOrder.nativeOrder())
        val pixels = IntArray(160 * 160)
        resized.getPixels(pixels, 0, 160, 0, 0, 160, 160)

        for (p in pixels) {
            buffer.putFloat(((p shr 16 and 0xFF) - 127.5f) / 128f)
            buffer.putFloat(((p shr 8 and 0xFF) - 127.5f) / 128f)
            buffer.putFloat(((p and 0xFF) - 127.5f) / 128f)
        }

        val output = Array(1) { FloatArray(512) }
        interpreter?.run(buffer, output)
        return l2Normalize(output[0])
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var sum = 0f
        for (value in v) sum += value * value
        val norm = sqrt(sum)
        if (norm > 0) {
            for (i in v.indices) v[i] /= norm
        }
        return v
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            stopCamera()
            cameraExecutor.shutdown()
            faceDetector?.close()
            interpreter?.close()
        } catch (e: Exception) {
            Log.e("Cleanup", "Error during cleanup", e)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }
}