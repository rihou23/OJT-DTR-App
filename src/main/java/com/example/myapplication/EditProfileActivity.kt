package com.example.myapplication

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class EditProfileActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null
    private var selectedImageUri: Uri? = null

    // Official profile data (used for comparison to decide if admin is needed)
    private var origFirst: String? = ""
    private var origMiddle: String? = ""
    private var origLast: String? = ""
    private var origSection: String? = ""
    private var currentProfileImageUrl: String? = ""

    private var imgChangeCount: Int = 0
    private var lastImgChangeMonth: Int = -1

    private lateinit var ivProfile: ImageView
    private lateinit var etFirst: EditText
    private lateinit var etMiddle: EditText
    private lateinit var etLast: EditText
    private lateinit var spnSection: Spinner
    private lateinit var btnSave: Button
    private lateinit var loadingOverlay: LinearLayout

    private val sectionOptions = arrayOf("BSIT 4-1", "BSIT 4-2","BSIT Irreg", "BSBA 4-1", "BSBA 4-2","BSBA Irreg")

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            selectedImageUri = it
            ivProfile.setImageURI(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        userId = intent.getStringExtra("user_id")
        firestore = FirebaseFirestore.getInstance()

        ivProfile = findViewById(R.id.ivEditProfileImage)
        etFirst = findViewById(R.id.etFirstName)
        etMiddle = findViewById(R.id.etMiddleName)
        etLast = findViewById(R.id.etLastName)
        spnSection = findViewById(R.id.etSection)
        btnSave = findViewById(R.id.btnSaveProfile)
        loadingOverlay = findViewById(R.id.loadingOverlay)

        // UI Adjustments for Darker Text
        etFirst.setTextColor(android.graphics.Color.BLACK)
        etMiddle.setTextColor(android.graphics.Color.BLACK)
        etLast.setTextColor(android.graphics.Color.BLACK)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sectionOptions)
        spnSection.adapter = adapter

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSelectPhoto).setOnClickListener { pickImage.launch("image/*") }
        btnSave.setOnClickListener { validateAndSave() }

        loadUserData()
    }

    private fun loadUserData() {
        val id = userId ?: return

        // 1. Fetch Official Data (Always needed for comparison logic)
        firestore.collection("user").document(id).get().addOnSuccessListener { userDoc ->
            if (userDoc.exists()) {
                origFirst = userDoc.getString("firstname") ?: ""
                origMiddle = userDoc.getString("middlename") ?: ""
                origLast = userDoc.getString("lastname") ?: ""
                origSection = userDoc.getString("section") ?: ""
                currentProfileImageUrl = userDoc.getString("profile_image_url") ?: ""
                imgChangeCount = userDoc.getLong("img_change_count")?.toInt() ?: 0
                lastImgChangeMonth = userDoc.getLong("last_img_change_month")?.toInt() ?: -1

                // 2. Check if there is a pending request to display instead
                firestore.collection("profile_edit_requests").document(id).get().addOnSuccessListener { reqDoc ->
                    if (reqDoc.exists()) {
                        // Display the PENDING data
                        btnSave.text = "UPDATE PENDING"
                        displayData(
                            reqDoc.getString("firstname"),
                            reqDoc.getString("middlename"),
                            reqDoc.getString("lastname"),
                            reqDoc.getString("section"),
                            reqDoc.getString("profile_image_url")
                        )
                    } else {
                        // Display the OFFICIAL data
                        btnSave.text = "SUBMIT REQUEST"
                        displayData(origFirst, origMiddle, origLast, origSection, currentProfileImageUrl)
                    }
                }
            }
        }
    }

    private fun displayData(f: String?, m: String?, l: String?, s: String?, img: String?) {
        etFirst.setText(f ?: "")
        etMiddle.setText(m ?: "")
        etLast.setText(l ?: "")

        val index = sectionOptions.indexOf(s)
        if (index >= 0) spnSection.setSelection(index)

        if (!img.isNullOrEmpty()) {
            Glide.with(this).load(img).circleCrop().into(ivProfile)
        }
    }

    private fun validateAndSave() {
        val newFirst = etFirst.text.toString().trim()
        val newLast = etLast.text.toString().trim()
        val newMiddle = etMiddle.text.toString().trim()
        val newSection = spnSection.selectedItem.toString()

        if (newFirst.isEmpty() || newLast.isEmpty()) {
            Toast.makeText(this, "Names are required", Toast.LENGTH_SHORT).show()
            return
        }

        // Compare against ORIGINAL data to see if admin is needed
        val isTextSameAsOriginal = newFirst == origFirst && newLast == origLast &&
                newMiddle == origMiddle && newSection == origSection
        val isImageChanged = selectedImageUri != null

        if (isTextSameAsOriginal && !isImageChanged) {
            Toast.makeText(this, "No changes detected from official profile", Toast.LENGTH_SHORT).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE

        if (isImageChanged) {
            val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
            var effectiveCount = imgChangeCount
            if (currentMonth != lastImgChangeMonth) effectiveCount = 0

            if (effectiveCount >= 4) {
                loadingOverlay.visibility = View.GONE
                Toast.makeText(this, "Monthly limit reached (4 changes max)", Toast.LENGTH_LONG).show()
                return
            }
            uploadImage(isTextSameAsOriginal, effectiveCount)
        } else {
            submitData(currentProfileImageUrl, false, imgChangeCount)
        }
    }

    private fun uploadImage(onlyImageChanged: Boolean, effectiveCount: Int) {
        val client = OkHttpClient()
        val file = uriToFile(selectedImageUri!!)
        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "profile_${userId}_${System.currentTimeMillis()}.jpg",
                RequestBody.create("image/*".toMediaTypeOrNull(), file))
            .build()

        val request = Request.Builder().url("https://rayio.vercel.app/api/upload").post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this@EditProfileActivity, "Upload Failed", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val resBody = response.body?.string()
                if (response.isSuccessful && resBody != null) {
                    val url = JSONObject(resBody).optString("url")
                    runOnUiThread { submitData(url, onlyImageChanged, effectiveCount) }
                } else {
                    runOnUiThread { loadingOverlay.visibility = View.GONE }
                }
            }
        })
    }

    private fun submitData(imageUrl: String?, onlyImageChanged: Boolean, currentCount: Int) {
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH)
        val data = hashMapOf<String, Any?>(
            "firstname" to etFirst.text.toString().trim(),
            "middlename" to etMiddle.text.toString().trim(),
            "lastname" to etLast.text.toString().trim(),
            "section" to spnSection.selectedItem.toString(),
            "profile_image_url" to imageUrl
        )

        if (onlyImageChanged) {
            // PHOTO ONLY -> Update Official User Table directly
            data["img_change_count"] = currentCount + 1
            data["last_img_change_month"] = currentMonth

            firestore.collection("user").document(userId!!).update(data as Map<String, Any>)
                .addOnSuccessListener {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Profile picture updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
        } else {
            // TEXT CHANGED -> Update Pending Requests Table
            data["userId"] = userId!!
            data["status"] = "pending"
            data["timestamp"] = Timestamp.now()

            firestore.collection("profile_edit_requests").document(userId!!)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    loadingOverlay.visibility = View.GONE
                    Toast.makeText(this, "Pending request updated/sent!", Toast.LENGTH_LONG).show()
                    finish()
                }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val tempFile = File(cacheDir, "temp_img.jpg")
        val out = FileOutputStream(tempFile)
        inputStream?.copyTo(out)
        inputStream?.close()
        out.close()
        return tempFile
    }
}