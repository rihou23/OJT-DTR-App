package com.example.myapplication

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class RequestCompletionActivity : AppCompatActivity() {

    private lateinit var userId: String
    private lateinit var date: String
    private var missingAM: Boolean = false
    private var missingPM: Boolean = false

    private lateinit var rgSession: RadioGroup
    private lateinit var rbAM: RadioButton
    private lateinit var rbPM: RadioButton
    private lateinit var btnPickTime: Button
    private lateinit var etReason: EditText
    private lateinit var btnSubmit: Button
    private lateinit var ivBack: ImageView

    private var selectedCalendar = Calendar.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // Added a boolean to track if user actually picked a time
    private var isTimeSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_completion)

        userId = intent.getStringExtra("user_id") ?: ""
        date = intent.getStringExtra("date") ?: ""
        missingAM = intent.getBooleanExtra("missing_am", false)
        missingPM = intent.getBooleanExtra("missing_pm", false)

        findViewById<TextView>(R.id.txtRequestDate).text = "Request for: $date"

        rgSession = findViewById(R.id.rgSession)
        rbAM = findViewById(R.id.rbAM)
        rbPM = findViewById(R.id.rbPM)
        btnPickTime = findViewById(R.id.btnPickTime)
        etReason = findViewById(R.id.etReason)
        btnSubmit = findViewById(R.id.btnSubmit)
        ivBack = findViewById(R.id.ivBack)

        // Handle Back Button
        ivBack.setOnClickListener { finish() }

        // UI Setup: Disable options that aren't missing
        rbAM.isEnabled = missingAM
        rbPM.isEnabled = missingPM
        if (missingAM) rbAM.isChecked = true else if (missingPM) rbPM.isChecked = true

        btnPickTime.setOnClickListener { showTimePicker() }
        btnSubmit.setOnClickListener { validateAndSubmit() }
    }

    private fun showTimePicker() {
        val cal = Calendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            selectedCalendar.set(Calendar.HOUR_OF_DAY, hour)
            selectedCalendar.set(Calendar.MINUTE, minute)
            isTimeSelected = true

            // Format the time to look nice (e.g., 02:30 PM)
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val formattedTime = sdf.format(selectedCalendar.time)

            // Update the button text to show the chosen time
            btnPickTime.text = "Selected Time: $formattedTime"
        }

        // Create TimePickerDialog with light theme to ensure visibility
        val timePickerDialog = TimePickerDialog(
            this,
            android.R.style.Theme_Holo_Light_Dialog_NoActionBar,
            timeSetListener,
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            false
        )

        timePickerDialog.show()
    }

    private fun validateAndSubmit() {
        val reason = etReason.text.toString().trim()
        val selectedField = if (rbAM.isChecked) "am_out" else "pm_out"

        if (!isTimeSelected) {
            Toast.makeText(this, "Please pick a time first", Toast.LENGTH_SHORT).show()
            return
        }

        if (reason.isEmpty()) {
            Toast.makeText(this, "Please provide a reason", Toast.LENGTH_SHORT).show()
            return
        }

        btnSubmit.isEnabled = false
        checkDuplicateAndSubmit(selectedField, reason)
    }

    private fun checkDuplicateAndSubmit(field: String, reason: String) {
        db.collection("attendance_requests")
            .whereEqualTo("userId", userId)
            .whereEqualTo("date", date)
            .whereEqualTo("missing_field", field)
            .whereEqualTo("status", "Pending")
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    Toast.makeText(this, "You already have a pending request for this session.", Toast.LENGTH_LONG).show()
                    btnSubmit.isEnabled = true
                } else {
                    submitRequest(field, reason)
                }
            }
    }

    private fun submitRequest(field: String, reason: String) {
        // Construct the timestamp for the requested time using the record's date
        val parts = date.split("-")
        selectedCalendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())

        val requestData = hashMapOf(
            "userId" to userId,
            "date" to date,
            "missing_field" to field,
            "requested_time" to Timestamp(selectedCalendar.time),
            "reason" to reason,
            "status" to "Pending",
            "requested_at" to FieldValue.serverTimestamp(),
            "reviewed_by" to null,
            "reviewed_at" to null
        )

        db.collection("attendance_requests")
            .add(requestData)
            .addOnSuccessListener {
                Toast.makeText(this, "Request sent. Waiting for admin approval.", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                btnSubmit.isEnabled = true
                Toast.makeText(this, "Error submitting request", Toast.LENGTH_SHORT).show()
            }
    }
}