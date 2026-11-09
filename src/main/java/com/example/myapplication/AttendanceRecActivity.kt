package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class AttendanceRecActivity : AppCompatActivity() {

    private lateinit var userId: String
    private val db = FirebaseFirestore.getInstance()
    private lateinit var containerRecords: LinearLayout
    private lateinit var spinnerMonth: Spinner
    private var rawDocuments = listOf<DocumentSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_rec)

        userId = intent.getStringExtra("USER_ID") ?: "No ID"

        // Initializing views
        containerRecords = findViewById(R.id.containerAttendanceRec)
        spinnerMonth = findViewById(R.id.spinnerMonthRec)
        val ivBack = findViewById<ImageView>(R.id.ivBack)

        ivBack.setOnClickListener { finish() }

        setupMonthSpinner()
        fetchAttendance()
    }

    private fun setupMonthSpinner() {
        val months = arrayOf("All Months", "Jan", "Feb", "Mar", "Apr", "May", "Jun")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = adapter

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (rawDocuments.isNotEmpty()) {
                    displayFilteredData()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun fetchAttendance() {
        db.collection("attendance").document(userId).collection("records")
            .get()
            .addOnSuccessListener { result ->
                rawDocuments = result.documents.sortedByDescending { it.id } // Sort by date newest first
                displayFilteredData()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching records", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayFilteredData() {
        containerRecords.removeAllViews()
        val selectedMonthIndex = spinnerMonth.selectedItemPosition // 0 = All

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        for (doc in rawDocuments) {
            val status = doc.getString("status") ?: "Present"

            // 1. FILTER: Only show Present/Late records (EXCLUDE ABSENT)
            if (status.equals("Absent", ignoreCase = true)) continue

            val dateId = doc.id // e.g. "2023-10-25"

            // 2. FILTER: Month selection logic
            try {
                val dateObj = sdfDate.parse(dateId)
                if (dateObj != null) {
                    val cal = Calendar.getInstance()
                    cal.time = dateObj
                    val docMonth = cal.get(Calendar.MONTH) + 1

                    if (selectedMonthIndex != 0 && docMonth != selectedMonthIndex) {
                        continue
                    }
                }
            } catch (e: Exception) { continue }

            // 3. Populate Table Row
            val row = LayoutInflater.from(this).inflate(R.layout.item_attendance_row, containerRecords, false)

            row.findViewById<TextView>(R.id.colDate).text = dateId

            // Format timestamps or show "--" if null
            row.findViewById<TextView>(R.id.colAmIn).text = formatTime(doc.getTimestamp("am_in"), timeFormat)
            row.findViewById<TextView>(R.id.colAmOut).text = formatTime(doc.getTimestamp("am_out"), timeFormat)
            row.findViewById<TextView>(R.id.colPmIn).text = formatTime(doc.getTimestamp("pm_in"), timeFormat)
            row.findViewById<TextView>(R.id.colPmOut).text = formatTime(doc.getTimestamp("pm_out"), timeFormat)

            containerRecords.addView(row)
        }

        if (containerRecords.childCount == 0) {
            val emptyTv = TextView(this)
            emptyTv.text = "No records found for this period."
            emptyTv.gravity = android.view.Gravity.CENTER
            emptyTv.setPadding(0, 100, 0, 0)
            containerRecords.addView(emptyTv)
        }
    }

    private fun formatTime(ts: Timestamp?, fmt: SimpleDateFormat): String {
        return if (ts != null) fmt.format(ts.toDate()) else "--:--"
    }
}