package com.example.myapplication

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

class AttendanceReportActivity : AppCompatActivity() {

    private lateinit var containerRecordsReport: LinearLayout
    private lateinit var spinnerMonthReport: Spinner
    private lateinit var txtScheduleAm: TextView
    private lateinit var txtSchedulePm: TextView
    private val db = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    // Dynamic Late Thresholds
    private var amLimitHour = 7
    private var amLimitMin = 30
    private var pmLimitHour = 13
    private var pmLimitMin = 0
    private var amLimitStr = "07:30 AM"
    private var pmLimitStr = "01:00 PM"

    private var rawDocuments = listOf<DocumentSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_report)

        userId = intent.getStringExtra("USER_ID") ?: "default_value"

        containerRecordsReport = findViewById(R.id.containerRecordsReport)
        spinnerMonthReport = findViewById(R.id.spinnerMonthReport)
        txtScheduleAm = findViewById(R.id.txtScheduleAm)
        txtSchedulePm = findViewById(R.id.txtSchedulePm)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        setupMonthSpinner()
        fetchOjtSettings()
    }

    private fun setupMonthSpinner() {
        val months = arrayOf("All Months", "Jan", "Feb", "Mar", "Apr", "May", "Jun")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonthReport.adapter = adapter

        spinnerMonthReport.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (rawDocuments.isNotEmpty()) {
                    displayFilteredReport()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun fetchOjtSettings() {
        db.collection("ojt_details").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val amTime = document.getString("am_in_time")
                    val pmTime = document.getString("pm_in_time")

                    if (!amTime.isNullOrEmpty()) {
                        amLimitStr = amTime
                        parseTimeString(amTime, true)
                    }
                    if (!pmTime.isNullOrEmpty()) {
                        pmLimitStr = pmTime
                        parseTimeString(pmTime, false)
                    }
                }
                // Update the Top Card UI
                txtScheduleAm.text = "Morning Shift: $amLimitStr"
                txtSchedulePm.text = "Afternoon Shift: $pmLimitStr"

                fetchReport()
            }
            .addOnFailureListener {
                fetchReport()
            }
    }

    private fun parseTimeString(timeStr: String, isAm: Boolean) {
        try {
            val hasMarker = timeStr.contains("AM", true) || timeStr.contains("PM", true)
            val sdf = if (hasMarker) SimpleDateFormat("hh:mm a", Locale.getDefault())
            else SimpleDateFormat("HH:mm", Locale.getDefault())

            val date = sdf.parse(timeStr)
            val cal = Calendar.getInstance()
            if (date != null) {
                cal.time = date
                var hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)

                if (!isAm && !hasMarker && hour < 12) hour += 12

                if (isAm) {
                    amLimitHour = hour; amLimitMin = min
                } else {
                    pmLimitHour = hour; pmLimitMin = min
                }
            }
        } catch (e: Exception) {}
    }

    private fun fetchReport() {
        db.collection("attendance").document(userId).collection("records").get()
            .addOnSuccessListener { result ->
                rawDocuments = result.documents.sortedByDescending { it.id }
                displayFilteredReport()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load reports", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayFilteredReport() {
        containerRecordsReport.removeAllViews()
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedMonthIndex = spinnerMonthReport.selectedItemPosition

        for (doc in rawDocuments) {
            val dateId = doc.id
            try {
                val dateObj = sdfDate.parse(dateId)
                if (dateObj != null) {
                    val cal = Calendar.getInstance(); cal.time = dateObj
                    val docMonth = cal.get(Calendar.MONTH) + 1
                    if (selectedMonthIndex != 0 && docMonth != selectedMonthIndex) continue
                }
            } catch (e: Exception) { continue }

            val amIn = doc.getTimestamp("am_in")
            val pmIn = doc.getTimestamp("pm_in")

            val amLate = amIn?.let { getLateMinutes(it, amLimitHour, amLimitMin) } ?: 0
            val pmLate = pmIn?.let { getLateMinutes(it, pmLimitHour, pmLimitMin) } ?: 0

            if (amLate > 0 || pmLate > 0) {
                val row = LayoutInflater.from(this).inflate(R.layout.item_attendance_report_row, containerRecordsReport, false)
                row.findViewById<TextView>(R.id.txtDateReport).text = dateId

                val txtAmIn = row.findViewById<TextView>(R.id.txtAmIn)
                val txtAmLate = row.findViewById<TextView>(R.id.txtAmLate)
                if (amLate > 0) {
                    txtAmIn.text = timeFormat.format(amIn!!.toDate())
                    txtAmLate.text = "${amLate}m late"
                } else {
                    txtAmIn.text = if (amIn != null) timeFormat.format(amIn.toDate()) else "--"
                    txtAmLate.text = "On Time / Empty"; txtAmLate.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }

                val txtPmIn = row.findViewById<TextView>(R.id.txtPmIn)
                val txtPmLate = row.findViewById<TextView>(R.id.txtPmLate)
                if (pmLate > 0) {
                    txtPmIn.text = timeFormat.format(pmIn!!.toDate())
                    txtPmLate.text = "${pmLate}m late"
                } else {
                    txtPmIn.text = if (pmIn != null) timeFormat.format(pmIn.toDate()) else "--"
                    txtPmLate.text = "On Time"; txtPmLate.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                }
                containerRecordsReport.addView(row)
            }
        }
    }

    private fun getLateMinutes(timestamp: Timestamp, hour: Int, min: Int): Int {
        val checkIn = Calendar.getInstance().apply { time = timestamp.toDate() }
        val limit = Calendar.getInstance().apply {
            time = timestamp.toDate()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, min)
            set(Calendar.SECOND, 0)
        }
        return if (checkIn.after(limit)) ((checkIn.timeInMillis - limit.timeInMillis) / 60000).toInt() else 0
    }
}