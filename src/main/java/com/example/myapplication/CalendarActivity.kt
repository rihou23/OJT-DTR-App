package com.example.myapplication

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.prolificinteractive.materialcalendarview.CalendarDay
import com.prolificinteractive.materialcalendarview.MaterialCalendarView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.HashMap

class CalendarActivity : AppCompatActivity() {

    private lateinit var calendarView: MaterialCalendarView
    private val db = FirebaseFirestore.getInstance()
    private lateinit var userId: String
    private lateinit var ivBack: ImageView
    private lateinit var spinnerFilter: Spinner
    private val attendanceMap = HashMap<String, DocumentSnapshot>()
    private val calculatedAbsentDates = mutableSetOf<String>()

    // Dynamic Late Thresholds
    private var amLimitHour = 7
    private var amLimitMin = 30
    private var pmLimitHour = 13
    private var pmLimitMin = 0
    private var amLimitStr = "7:30 AM"
    private var pmLimitStr = "1:00 PM"

    private var rawDocuments = listOf<DocumentSnapshot>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calendar)

        userId = intent.getStringExtra("user_id") ?: "default_value"
        calendarView = findViewById(R.id.calendarView)
        ivBack = findViewById(R.id.ivBack)
        spinnerFilter = findViewById(R.id.spinnerFilter)

        ivBack.setOnClickListener { finish() }

        setupSpinner()

        findViewById<CardView>(R.id.cardTotalAttendance).setOnClickListener {
            val intent = Intent(this, AttendanceRecActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardLateDays).setOnClickListener {
            val intent = Intent(this, AttendanceReportActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        findViewById<CardView>(R.id.cardAbsentDays).setOnClickListener {
            val intent = Intent(this, ViewAbsentsActivity::class.java)
            intent.putExtra("USER_ID", userId)
            startActivity(intent)
        }

        calendarView.setOnDateChangedListener { _, date, _ ->
            val dateKey = String.format("%04d-%02d-%02d", date.year, date.month, date.day)
            if (attendanceMap.containsKey(dateKey)) {
                showDetailsDialog(dateKey, attendanceMap[dateKey], false)
            } else if (calculatedAbsentDates.contains(dateKey)) {
                showDetailsDialog(dateKey, null, true)
            }
        }
    }

    private fun setupSpinner() {
        val months = arrayOf("All Months", "January", "February", "March", "April", "May", "June")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, months)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFilter.adapter = adapter

        spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (rawDocuments.isNotEmpty()) {
                    processAndDisplayData(rawDocuments)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onResume() {
        super.onResume()
        fetchOjtSettingsAndAttendance()
    }

    private fun fetchOjtSettingsAndAttendance() {
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
                fetchAttendanceData()
            }
            .addOnFailureListener { fetchAttendanceData() }
    }

    private fun parseTimeString(timeStr: String, isAm: Boolean) {
        try {
            // Check if string contains AM/PM
            val hasMarker = timeStr.contains("AM", true) || timeStr.contains("PM", true)
            val sdf = if (hasMarker) {
                SimpleDateFormat("hh:mm a", Locale.getDefault())
            } else {
                SimpleDateFormat("HH:mm", Locale.getDefault())
            }

            val date = sdf.parse(timeStr)
            val cal = Calendar.getInstance()
            if (date != null) {
                cal.time = date
                var hour = cal.get(Calendar.HOUR_OF_DAY)
                val min = cal.get(Calendar.MINUTE)

                // FIX FOR PM CALCULATION:
                // If we are parsing PM time, it's not marked with AM/PM, and it's like "01:00"
                // we treat it as 1:00 PM (13:00) instead of 1:00 AM.
                if (!isAm && !hasMarker && hour < 12) {
                    hour += 12
                }

                if (isAm) {
                    amLimitHour = hour
                    amLimitMin = min
                } else {
                    pmLimitHour = hour
                    pmLimitMin = min
                }
            }
        } catch (e: Exception) {}
    }

    private fun fetchAttendanceData() {
        db.collection("attendance").document(userId).collection("records")
            .get()
            .addOnSuccessListener { result ->
                rawDocuments = result.documents
                processAndDisplayData(rawDocuments)
            }
    }

    private fun processAndDisplayData(documents: List<DocumentSnapshot>) {
        val lateDates = mutableSetOf<CalendarDay>()
        val onTimeDates = mutableSetOf<CalendarDay>()
        val absentDates = mutableSetOf<CalendarDay>()
        val recordedDates = mutableListOf<Date>()

        attendanceMap.clear()
        calculatedAbsentDates.clear()

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val selectedMonthIndex = spinnerFilter.selectedItemPosition

        for (doc in documents) {
            val dateId = doc.id
            attendanceMap[dateId] = doc
            try {
                val parsedDate = sdf.parse(dateId)
                if (parsedDate != null) {
                    recordedDates.add(parsedDate)
                    val cal = Calendar.getInstance()
                    cal.time = parsedDate
                    val docMonth = cal.get(Calendar.MONTH) + 1
                    val isVisible = selectedMonthIndex == 0 || selectedMonthIndex == docMonth

                    if (isVisible) {
                        val status = doc.getString("status") ?: ""
                        val parts = dateId.split("-")
                        val day = CalendarDay.from(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())

                        if (status.equals("Absent", ignoreCase = true)) {
                            absentDates.add(day)
                        } else {
                            val amIn = doc.getTimestamp("am_in")
                            val pmIn = doc.getTimestamp("pm_in")

                            // Bases late detection on dynamic thresholds
                            val isLate = (amIn?.let { checkIsLate(it, amLimitHour, amLimitMin) } ?: false) ||
                                    (pmIn?.let { checkIsLate(it, pmLimitHour, pmLimitMin) } ?: false)

                            if (isLate) lateDates.add(day) else onTimeDates.add(day)
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        if (recordedDates.isNotEmpty()) {
            recordedDates.sort()
            val startDate = recordedDates.first()
            val calendarLoop = Calendar.getInstance()
            calendarLoop.time = startDate
            val today = Calendar.getInstance()
            val todayStr = sdf.format(today.time)

            while (!calendarLoop.time.after(today.time)) {
                val dateKey = sdf.format(calendarLoop.time)
                val dayOfWeek = calendarLoop.get(Calendar.DAY_OF_WEEK)
                val docMonth = calendarLoop.get(Calendar.MONTH) + 1
                val isVisible = selectedMonthIndex == 0 || selectedMonthIndex == docMonth
                val isActuallyToday = dateKey == todayStr

                if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                    if (!attendanceMap.containsKey(dateKey)) {
                        var shouldMarkAbsent = true
                        if (isActuallyToday) {
                            val currentHour = today.get(Calendar.HOUR_OF_DAY)
                            if (currentHour < 19) { shouldMarkAbsent = false }
                        }
                        if (shouldMarkAbsent && isVisible) {
                            calculatedAbsentDates.add(dateKey)
                            val absDay = CalendarDay.from(calendarLoop.get(Calendar.YEAR), calendarLoop.get(Calendar.MONTH) + 1, calendarLoop.get(Calendar.DAY_OF_MONTH))
                            absentDates.add(absDay)
                        }
                    }
                }
                calendarLoop.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        findViewById<TextView>(R.id.txtTotalCount).text = (onTimeDates.size + lateDates.size).toString()
        findViewById<TextView>(R.id.txtLateCount).text = lateDates.size.toString()
        findViewById<TextView>(R.id.txtAbsentCount).text = absentDates.size.toString()

        calendarView.removeDecorators()
        calendarView.addDecorator(LateDateDecorator(lateDates))
        calendarView.addDecorator(OnTimeDateDecorator(onTimeDates))
        calendarView.addDecorator(AbsentDateDecorator(absentDates))

        if (selectedMonthIndex > 0) {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            calendarView.setCurrentDate(CalendarDay.from(currentYear, selectedMonthIndex, 1))
        }
    }

    private fun showDetailsDialog(date: String, doc: DocumentSnapshot?, isCalculatedAbsent: Boolean) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_attendance_details, null)
        val rootLayout = view.findViewById<LinearLayout>(R.id.rootDialogLayout)
        val tableWrapper = view.findViewById<LinearLayout>(R.id.tableContainerWrapper)
        val statusTv = view.findViewById<TextView>(R.id.detailStatus)
        val detailTotalHours = view.findViewById<TextView>(R.id.detailTotalHours)
        val container = view.findViewById<LinearLayout>(R.id.tableContainer)

        view.findViewById<TextView>(R.id.detailDate).text = "📅 $date"

        if (isCalculatedAbsent) {
            statusTv.text = "Status: Absent"
            detailTotalHours.text = "Reason: No Reason Added"
            tableWrapper.visibility = View.GONE
            val btnAdd = Button(this).apply {
                text = "Add Reason"
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setTextColor(Color.WHITE)
                setOnClickListener {
                    dialog.dismiss()
                    val intent = Intent(this@CalendarActivity, RequestAbsentActivity::class.java)
                    intent.putExtra("USER_ID", userId)
                    intent.putExtra("LOCKED_DATE", date)
                    startActivity(intent)
                }
            }
            rootLayout.addView(btnAdd)
        } else {
            val status = doc?.getString("status") ?: "N/A"
            statusTv.text = "Status: $status"
            if (status.equals("Absent", ignoreCase = true)) {
                detailTotalHours.text = "Reason: ${doc?.getString("reason") ?: "N/A"}"
                tableWrapper.visibility = View.GONE
                val btnView = Button(this).apply {
                    text = "View Full Details"
                    setOnClickListener {
                        dialog.dismiss()
                        val intent = Intent(this@CalendarActivity, AbsentDetailActivity::class.java)
                        intent.putExtra("USER_ID", userId)
                        intent.putExtra("DATE", date)
                        startActivity(intent)
                    }
                }
                rootLayout.addView(btnView)
            } else {
                tableWrapper.visibility = View.VISIBLE
                val totalHours = doc?.get("total_hours")?.toString()?.toDoubleOrNull() ?: 0.0
                detailTotalHours.text = "Total Hours: %.2f".format(totalHours)
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                doc?.getTimestamp("am_in")?.let { addTableRow(container, "MORNING", amLimitStr, it, doc.getTimestamp("am_out"), amLimitHour, amLimitMin, timeFormat) }
                doc?.getTimestamp("pm_in")?.let { addTableRow(container, "AFTERNOON", pmLimitStr, it, doc.getTimestamp("pm_out"), pmLimitHour, pmLimitMin, timeFormat) }
            }
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun addTableRow(container: LinearLayout, title: String, limitStr: String, inTs: Timestamp, outTs: Timestamp?, limH: Int, limM: Int, fmt: SimpleDateFormat) {
        val row = layoutInflater.inflate(R.layout.item_table_row, null)
        row.findViewById<TextView>(R.id.rowShiftTitle).text = title
        row.findViewById<TextView>(R.id.rowLimit).text = limitStr
        row.findViewById<TextView>(R.id.rowIn).text = fmt.format(inTs.toDate())
        val rowOut = row.findViewById<TextView>(R.id.rowOut)
        if (outTs != null) rowOut.text = fmt.format(outTs.toDate()) else { rowOut.text = "No out"; rowOut.setTextColor(Color.RED) }
        val lateMins = getLateMinutes(inTs, limH, limM)
        val rowLate = row.findViewById<TextView>(R.id.rowLate)
        if (lateMins > 0) { rowLate.text = "${lateMins}m late"; rowLate.setTextColor(Color.RED) } else { rowLate.text = "On Time"; rowLate.setTextColor(Color.GRAY) }
        container.addView(row)
    }

    private fun getLateMinutes(ts: Timestamp, h: Int, m: Int): Int {
        val checkIn = Calendar.getInstance().apply { time = ts.toDate() }
        val limit = Calendar.getInstance().apply {
            time = ts.toDate()
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
        }
        // If checkIn time is after the limit time, calculate the difference in minutes
        return if (checkIn.after(limit)) {
            ((checkIn.timeInMillis - limit.timeInMillis) / 60000).toInt()
        } else 0
    }

    private fun checkIsLate(ts: Timestamp?, h: Int, m: Int): Boolean = ts?.let { getLateMinutes(it, h, m) > 0 } ?: false
}