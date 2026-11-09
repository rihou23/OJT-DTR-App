package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import androidx.core.content.edit

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnSignIn: Button
    private lateinit var tvCreateAccount: TextView
    private lateinit var db: FirebaseFirestore

    // 1. First set of permissions (Foreground)
    private val foregroundPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    // 2. Launcher for Background Location (Requested second)
    private val requestBackgroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Background location is required. Exiting.", Toast.LENGTH_LONG).show()
            finishAffinity()
        }
    }

    // 3. Launcher for Foreground Permissions (Requested first)
    private val requestForegroundLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allForegroundGranted = permissions.entries.all { it.value }

        if (allForegroundGranted) {
            // After foreground is granted, check if we need background location
            checkBackgroundPermission()
        } else {
            Toast.makeText(this, "Mandatory permissions denied. Exiting.", Toast.LENGTH_LONG).show()
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- STEP 1: AUTO-REDIRECT LOGIC ---
        val sharedPref = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        if (sharedPref.contains("user_id")) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // --- STEP 2: START PERMISSION FLOW ---
        checkAndRequestPermissions()

        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSignIn = findViewById(R.id.btnSignIn)
        tvCreateAccount = findViewById(R.id.tvCreateAccount)

        db = FirebaseFirestore.getInstance()
        btnSignIn.setOnClickListener { login() }
        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = foregroundPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            // This shows the first standard Android popup
            requestForegroundLauncher.launch(foregroundPermissions)
        } else {
            // Foreground already granted, check background
            checkBackgroundPermission()
        }
    }

    private fun checkBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val hasBackground = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasBackground) {
                // On Android 11+, this will often lead the user to a settings screen
                // because a direct popup for background location is not allowed.
                requestBackgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    }

    // --- REST OF YOUR ORIGINAL LOGIN CODE ---

    private fun login() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        btnSignIn.isEnabled = false
        btnSignIn.text = "Verifying..."

        db.collection("user")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    resetButton()
                    Toast.makeText(this, "Couldn't find your account.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val doc = snapshot.documents[0]
                val storedHash = doc.getString("password") ?: ""
                val inputHash = sha256(password)

                if (storedHash != inputHash) {
                    resetButton()
                    Toast.makeText(this, "Wrong password. Try again.", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val userId = doc.id
                getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE).edit {
                    putString("user_id", userId)
                    apply()
                }

                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                resetButton()
                Toast.makeText(this, "Login failed.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun resetButton() {
        btnSignIn.isEnabled = true
        btnSignIn.text = "Sign In"
    }
}