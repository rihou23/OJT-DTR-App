package com.example.myapplication

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore

class AbsentDetailActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_absent_detail)

        val userId = intent.getStringExtra("USER_ID") ?: ""
        val date = intent.getStringExtra("DATE") ?: ""

        val tvDate = findViewById<TextView>(R.id.detailDate)
        val tvReason = findViewById<TextView>(R.id.detailReason)
        val ivProofImage = findViewById<ImageView>(R.id.ivProofImage)
        val tvNoProof = findViewById<TextView>(R.id.tvNoProof)
        val btnBack = findViewById<ImageView>(R.id.btnBack)

        btnBack.setOnClickListener { finish() }
        tvDate.text = "Date: $date"

        // Fetch Full Data from Firestore
        db.collection("attendance").document(userId).collection("records").document(date)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    tvReason.text = doc.getString("reason") ?: "No reason provided."
                    val proofUrl = doc.getString("proof_url")

                    if (!proofUrl.isNullOrEmpty()) {
                        ivProofImage.visibility = View.VISIBLE
                        tvNoProof.visibility = View.GONE
                        Glide.with(this)
                            .load(proofUrl)
                            .placeholder(android.R.drawable.progress_horizontal)
                            .into(ivProofImage)
                    } else {
                        ivProofImage.visibility = View.GONE
                        tvNoProof.visibility = View.VISIBLE
                    }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching details", Toast.LENGTH_SHORT).show()
            }
    }
}