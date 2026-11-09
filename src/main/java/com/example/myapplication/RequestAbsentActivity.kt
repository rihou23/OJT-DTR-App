package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class RequestAbsentActivity : AppCompatActivity() {

    private lateinit var userId: String
    private val db = FirebaseFirestore.getInstance()
    private val client = OkHttpClient()

    private lateinit var etReason: EditText
    private lateinit var tvLockedDate: TextView
    private lateinit var ivProofPreview: ImageView
    private lateinit var btnSelectImage: Button
    private lateinit var btnSubmit: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingOverlay: View

    private var selectedImageUri: Uri? = null
    private var finalDate: String = ""

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            selectedImageUri = uri
            ivProofPreview.setImageURI(uri)
            ivProofPreview.visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_absent)

        userId = intent.getStringExtra("USER_ID") ?: ""
        val passedDate = intent.getStringExtra("LOCKED_DATE")
        finalDate = passedDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Initialize Views
        etReason = findViewById(R.id.etReason)
        tvLockedDate = findViewById(R.id.tvLockedDate)
        ivProofPreview = findViewById(R.id.ivProofPreview)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        btnSubmit = findViewById(R.id.btnSubmitAbsent)
        progressBar = findViewById(R.id.progressBar)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        tvLockedDate.text = "Recording Absence for: $finalDate"

        // Header Back Button
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        // CHECK IF ANY RECORD ALREADY EXISTS (Attendance or Absent)
        checkExistingRecord()

        btnSelectImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSubmit.setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isEmpty()) {
                Toast.makeText(this, "Please enter a reason", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            setLoading(true)

            if (selectedImageUri != null) {
                uploadImageAndSave(selectedImageUri!!, reason)
            } else {
                saveToFirestore(reason, null)
            }
        }
    }

    private fun checkExistingRecord() {
        db.collection("attendance").document(userId).collection("records").document(finalDate)
            .get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    // Lock form if record already exists
                    etReason.isEnabled = false
                    btnSelectImage.isEnabled = false
                    btnSubmit.isEnabled = false
                    btnSubmit.text = "Record Already Exists"
                    btnSubmit.alpha = 0.5f
                    Toast.makeText(this, "A record already exists for this date.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            loadingOverlay.visibility = View.VISIBLE
            btnSubmit.isEnabled = false
        } else {
            loadingOverlay.visibility = View.GONE
            btnSubmit.isEnabled = true
        }
    }

    private fun uploadImageAndSave(uri: Uri, reason: String) {
        val file = uriToFile(uri)
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
            .build()

        val request = Request.Builder()
            .url("https://rayio.vercel.app/api/upload")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoading(false)
                    Toast.makeText(this@RequestAbsentActivity, "Upload Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseData != null) {
                        try {
                            val imageUrl = JSONObject(responseData).getString("url")
                            saveToFirestore(reason, imageUrl)
                        } catch (e: Exception) {
                            saveToFirestore(reason, null)
                        }
                    } else {
                        setLoading(false)
                        Toast.makeText(this@RequestAbsentActivity, "Upload Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun saveToFirestore(reason: String, imageUrl: String?) {
        val data = hashMapOf(
            "date" to finalDate,
            "reason" to reason,
            "status" to "Absent",
            "proof_url" to imageUrl,
            "created_at" to FieldValue.serverTimestamp()
        )

        db.collection("attendance").document(userId).collection("records")
            .document(finalDate)
            .set(data)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, "Absence Recorded Successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(this, "Database Error", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File.createTempFile("abs_", ".jpg", cacheDir)
        val outputStream = FileOutputStream(tempFile)
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
        return tempFile
    }
}