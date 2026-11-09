package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private var userId: String? = null

    private lateinit var tvProfileName: TextView
    private lateinit var tvSection: TextView
    private lateinit var ivProfileImage: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize Views
        tvProfileName = findViewById(R.id.tvProfileName)
        tvSection = findViewById(R.id.tvSection)
        ivProfileImage = findViewById(R.id.ivProfileImage)
        val btnBack = findViewById<ImageView>(R.id.btnBack)
        val btnEditProfile = findViewById<LinearLayout>(R.id.btnEditProfile)
        val btnOjtDetails = findViewById<LinearLayout>(R.id.btnOjtDetails)

        firestore = FirebaseFirestore.getInstance()
        userId = intent.getStringExtra("user_id")

        if (userId.isNullOrEmpty()) {
            Toast.makeText(this, "User ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Logic for buttons
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, EditProfileActivity::class.java)
            intent.putExtra("user_id", userId)
            startActivity(intent)
        }

        btnOjtDetails.setOnClickListener {
            val intent = Intent(this, ViewOjtDetailsActivity::class.java)
            intent.putExtra("user_id", userId)
            startActivity(intent)
        }

        btnBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        loadUserProfileData()
    }

    private fun loadUserProfileData() {
        userId?.let { id ->
            firestore.collection("user").document(id).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        // 1. Format Name with Middle Initial Only
                        val firstName = document.getString("firstname") ?: ""
                        val middleName = document.getString("middlename") ?: ""
                        val lastName = document.getString("lastname") ?: ""

                        // Show only first letter of middle name
                        val mi = if (middleName.isNotEmpty()) "${middleName[0]}." else ""
                        val fullName = "$firstName $mi $lastName".trim()

                        tvProfileName.text = if (fullName.isEmpty()) "No Name Set" else fullName

                        // Adjust text size based on name length
                        tvProfileName.textSize = when {
                            fullName.length > 25 -> 20f  // Very long name
                            fullName.length > 20 -> 22f  // Long name
                            fullName.length > 15 -> 24f  // Medium name
                            else -> 26f                   // Normal size
                        }

                        // 2. Handle Section
                        tvSection.text = document.getString("section") ?: "N/A"

                        // 3. Glide Image Loading with Fallback and Fade Effect
                        val dbImageUrl = document.getString("profile_image_url")

                        Glide.with(this)
                            .load(dbImageUrl)
                            .transition(DrawableTransitionOptions.withCrossFade()) // Smooth fade in
                            .circleCrop()
                            .placeholder(R.drawable.ic_profile_placeholder) // While loading
                            .error(R.drawable.ic_profile_placeholder)       // If link is broken
                            .into(ivProfileImage)
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error fetching data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}