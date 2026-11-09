package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ViewAbsentsActivity : AppCompatActivity() {

    private lateinit var userId: String
    private val db = FirebaseFirestore.getInstance()
    private lateinit var listContainer: LinearLayout
    private lateinit var spinnerMonthFilter: Spinner

    // Store all entries to allow filtering
    private var allAbsents = mutableListOf<AbsentEntry>()

    data class AbsentEntry(
        val date: String,
        val dateObj: Date,
        val reason: String,
        val hasRecord: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_absents)

        userId = intent.getStringExtra("USER_ID") ?: "default_user"
        listContainer = findViewById(R.id.absentListContainer)
        spinnerMonthFilter = findViewById(R.id.spinnerMonthFilter)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        findViewById<Button>(R.id.btnFileAbsence).setOnClickListener {
            val intent = Intent(this, RequestAbsentActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        setupMonthSpinner()
    }

    private fun setupMonthSpinner() {
        val months = arrayOf("All Months", "Jan", "Feb", "Mar", "Apr", "May", "Jun")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonthFilter.adapter = adapter

        spinnerMonthFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                filterAndDisplay()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        listContainer.removeAllViews()
        fetchAbsents()
    }

    private fun fetchAbsents() {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val recordsMap = mutableMapOf<String, String>()
        val datesList = mutableListOf<Date>()

        allAbsents.clear()

        db.collection("attendance").document(userId).collection("records")
            .get()
            .addOnSuccessListener { result ->
                // 1. Map existing records from Firestore
                for (doc in result.documents) {
                    val status = doc.getString("status") ?: ""
                    recordsMap[doc.id] = status

                    try {
                        sdf.parse(doc.id)?.let {
                            datesList.add(it)
                            if (status.equals("Absent", ignoreCase = true)) {
                                allAbsents.add(AbsentEntry(
                                    doc.id, it,
                                    doc.getString("reason") ?: "No Reason",
                                    true
                                ))
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                // 2. New Logic: Detect Missing Weekdays until TODAY
                if (datesList.isNotEmpty()) {
                    datesList.sort()
                    val startDate = datesList.first()

                    val calendarLoop = Calendar.getInstance()
                    calendarLoop.time = startDate

                    val today = Calendar.getInstance() // Current date and time
                    val todayStr = sdf.format(today.time)

                    while (!calendarLoop.time.after(today.time)) {
                        val dKey = sdf.format(calendarLoop.time)
                        val dow = calendarLoop.get(Calendar.DAY_OF_WEEK)

                        // Check if this date is today
                        val isActuallyToday = dKey == todayStr

                        if (dow != Calendar.SATURDAY && dow != Calendar.SUNDAY) {
                            // If no record exists in Firestore for this date
                            if (!recordsMap.containsKey(dKey)) {

                                var shouldAddAsAbsent = true

                                // Apply 7:00 PM (19:00) Rule for Today
                                if (isActuallyToday) {
                                    val currentHour = today.get(Calendar.HOUR_OF_DAY)
                                    if (currentHour < 19) {
                                        shouldAddAsAbsent = false
                                    }
                                }

                                if (shouldAddAsAbsent) {
                                    allAbsents.add(AbsentEntry(
                                        dKey,
                                        calendarLoop.time.clone() as Date,
                                        "No Reason Added",
                                        false
                                    ))
                                }
                            }
                        }
                        calendarLoop.add(Calendar.DAY_OF_MONTH, 1)
                    }
                }

                // Sort everything newest first
                allAbsents.sortByDescending { it.dateObj }
                filterAndDisplay()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error fetching data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterAndDisplay() {
        listContainer.removeAllViews()
        val selectedMonthIndex = spinnerMonthFilter.selectedItemPosition

        var no = 1
        for (entry in allAbsents) {
            val cal = Calendar.getInstance()
            cal.time = entry.dateObj
            val entryMonth = cal.get(Calendar.MONTH) + 1

            if (selectedMonthIndex == 0 || entryMonth == selectedMonthIndex) {
                addListItem(no, entry.date, entry.reason, entry.hasRecord)
                no++
            }
        }

        // If empty after filtering
        if (no == 1) {
            val emptyTv = TextView(this)
            emptyTv.text = "No absent records found."
            emptyTv.gravity = android.view.Gravity.CENTER
            emptyTv.setPadding(0, 50, 0, 0)
            listContainer.addView(emptyTv)
        }
    }

    private fun addListItem(no: Int, date: String, reason: String, hasRecord: Boolean) {
        val row = layoutInflater.inflate(R.layout.item_absent_record, null)

        row.findViewById<TextView>(R.id.tvNo).text = no.toString()
        row.findViewById<TextView>(R.id.tvDate).text = date
        row.findViewById<TextView>(R.id.tvReason).text = reason

        val btnAction = row.findViewById<Button>(R.id.btnAction)

        // UI logic: If record exists, we "View" it. If missing, we "Add Reason".
        btnAction.text = if (hasRecord) "View" else "Add Reason"

        btnAction.setOnClickListener {
            if (hasRecord) {
                // Takes user to see the details/proof of an existing absent record
                val intent = Intent(this, AbsentDetailActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("DATE", date)
                startActivity(intent)
            } else {
                // Takes user to file a reason for a missing day
                val intent = Intent(this, RequestAbsentActivity::class.java)
                intent.putExtra("USER_ID", userId)
                intent.putExtra("LOCKED_DATE", date)
                startActivity(intent)
            }
        }
        listContainer.addView(row)
    }
}