package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FieldValue
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.imageview.ShapeableImageView
import androidx.appcompat.app.AlertDialog

class DashboardActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var ivProfile: ShapeableImageView
    private lateinit var tvStudentName: TextView
    private lateinit var tvStudentCourse: TextView
    private lateinit var tvGreeting: TextView
    private lateinit var tvHoursMade: TextView
    private lateinit var tvProgressPercent: TextView
    private lateinit var tvRequiredLabel: TextView
    private lateinit var tvRemainingLabel: TextView
    private lateinit var tvDate: TextView
    private lateinit var progressCircle: ProgressBar
    private lateinit var ivMore: ImageView
    private lateinit var tvMissedBadge: TextView
    private lateinit var tvNotificationBadge: TextView // Added for notifications

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    // Navigation Elements
    private lateinit var btnTimeInOut: MaterialButton
    private lateinit var cardAttendance: CardView
    private lateinit var cardReports: CardView
    private lateinit var cardMissed: CardView
    private lateinit var cardDetails: CardView

    // OJT Status Elements
    private lateinit var btnAddOjtDetails: MaterialButton
    private lateinit var layoutFullDashboard: LinearLayout

    // Firebase & Session
    private val db = FirebaseFirestore.getInstance()
    private lateinit var uid: String
    private var missedLogListener: ListenerRegistration? = null
    private var notificationListener: ListenerRegistration? = null // Listener for notifications

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        // 1. Session Management
        val prefs = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        uid = prefs.getString("user_id", null) ?: ""

        if (uid.isEmpty()) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        // 2. Bind UI Views
        initializeViews()

        // 3. Set Up Click Listeners
        setupNavigation()
        setupMenu()

        // 4. Start listening for badges
        listenForMissedLogs()
        listenForNotifications() // Added

        // 5. Swipe to Refresh logic
        swipeRefreshLayout.setOnRefreshListener {
            loadDashboard()
        }
    }

    override fun onResume() {
        super.onResume()
        loadDashboard()
    }

    override fun onDestroy() {
        super.onDestroy()
        missedLogListener?.remove()
        notificationListener?.remove() // Cleanup
    }

    private fun initializeViews() {
        tvStudentName = findViewById(R.id.tvStudentName)
        tvStudentCourse = findViewById(R.id.tvStudentCourse)
        tvGreeting = findViewById(R.id.tvGreeting)
        tvHoursMade = findViewById(R.id.tvHoursMade)
        tvProgressPercent = findViewById(R.id.tvProgressPercent)
        tvRequiredLabel = findViewById(R.id.tvRequiredLabel)
        tvRemainingLabel = findViewById(R.id.tvRemainingLabel)
        tvDate = findViewById(R.id.tvDate)
        progressCircle = findViewById(R.id.progressCircle)
        ivMore = findViewById(R.id.ivMore)
        ivProfile = findViewById(R.id.ivProfile)
        tvMissedBadge = findViewById(R.id.tvMissedBadge)
        tvNotificationBadge = findViewById(R.id.tvNotificationBadge) // Initialized
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout)

        btnTimeInOut = findViewById(R.id.btnTimeInOut)
        cardAttendance = findViewById(R.id.cardAttendance)
        cardReports = findViewById(R.id.cardReports)
        cardMissed = findViewById(R.id.cardMissed)
        cardDetails = findViewById(R.id.cardDetails)

        btnAddOjtDetails = findViewById(R.id.btnAddOjtDetails)
        layoutFullDashboard = findViewById(R.id.layoutFullDashboard)

        // Get server time and display date
        updateDateWithServerTime()
    }

    private fun updateDateWithServerTime() {
        // Create a temporary document to get Firebase server timestamp
        val tempDoc = db.collection("temp_time").document()
        tempDoc.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                tempDoc.get().addOnSuccessListener { doc ->
                    val serverTime = doc.getTimestamp("timestamp")
                    if (serverTime != null) {
                        val sdf = SimpleDateFormat("MM-dd-yyyy", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("Asia/Manila")
                        tvDate.text = sdf.format(serverTime.toDate())

                        // Clean up temp document
                        tempDoc.delete()
                    }
                }
            }
    }

    private fun listenForMissedLogs() {
        missedLogListener = db.collection("missedout")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", 0)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val count = snapshot?.size() ?: 0
                if (count > 0) {
                    tvMissedBadge.text = count.toString()
                    tvMissedBadge.visibility = View.VISIBLE
                } else {
                    tvMissedBadge.visibility = View.GONE
                }
            }
    }

    // NEW: Listen for actual notifications collection
    private fun listenForNotifications() {
        notificationListener = db.collection("notifications")
            .whereEqualTo("user_id", uid) // Matches the PHP side logic
            .whereEqualTo("status", 0)    // 0 = Unread
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("FIRESTORE", "Error: ${e.message}")
                    return@addSnapshotListener
                }

                val count = snapshot?.size() ?: 0
                if (count > 0) {
                    tvNotificationBadge.text = count.toString()
                    tvNotificationBadge.visibility = View.VISIBLE
                } else {
                    tvNotificationBadge.visibility = View.GONE
                }
            }
    }

    private fun setupMenu() {
        ivMore.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.dropdown_menu, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_profile -> {
                        val intent = Intent(this, ProfileActivity::class.java)
                        intent.putExtra("user_id", uid)
                        startActivity(intent)
                        true
                    }
                    R.id.menu_faq -> {
                        val intent = Intent(this, FaqActivity::class.java)
                        intent.putExtra("user_id", uid)
                        startActivity(intent)
                        true
                    }

                    R.id.menu_leaderboard -> {
                        val intent = Intent(this, LeaderboardActivity::class.java)
                        intent.putExtra("user_id", uid)
                        startActivity(intent)
                        true
                    }

                    R.id.menu_signout -> {
                        showSignOutConfirmation()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    private fun showSignOutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Sign Out")
            .setMessage("Are you sure you want to sign out?")
            .setPositiveButton("Yes") { dialog, _ ->
                // User confirmed sign out
                val prefs = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
                prefs.edit().clear().apply()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                // User cancelled
                dialog.dismiss()
            }
            .show()
    }

    private fun setupNavigation() {
        btnTimeInOut.setOnClickListener {
            val intent = Intent(this, AttendanceActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }

        cardAttendance.setOnClickListener {
            val intent = Intent(this, CalendarActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }

        cardReports.setOnClickListener {
            val intent = Intent(this, ReportsActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }

        cardMissed.setOnClickListener {
            val intent = Intent(this, MissedTimeoutActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }

        // Updated for notifications
        cardDetails.setOnClickListener {
            val intent = Intent(this, UserNotificationsActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }

        btnAddOjtDetails.setOnClickListener {
            val intent = Intent(this, OjtDetailsActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }





        ivProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            intent.putExtra("user_id", uid)
            startActivity(intent)
        }
    }

    private fun loadDashboard() {
        swipeRefreshLayout.isRefreshing = true

        db.collection("user").document(uid).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                val fname = doc.getString("firstname") ?: ""
                val mname = doc.getString("middlename") ?: ""
                val lname = doc.getString("lastname") ?: ""
                val nick = doc.getString("nickname") ?: "Student"
                val section = doc.getString("section") ?: "N/A"
                val ojtStatus = doc.getString("ojt_details") ?: "none"
                val profileImageUrl = doc.getString("profile_image_url")

                val mi = if (mname.isNotEmpty()) "${mname[0]}." else ""
                tvStudentName.text = "$fname $mi $lname"
                tvStudentCourse.text = section

                // Get server time for greeting
                getServerTimeForGreeting(nick)

                Glide.with(this)
                    .load(profileImageUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .into(ivProfile)

                if (ojtStatus == "complete") {
                    layoutFullDashboard.visibility = View.VISIBLE
                    btnAddOjtDetails.visibility = View.GONE
                    fetchAttendance()
                    checkAndSyncMissedLogs()
                } else {
                    layoutFullDashboard.visibility = View.GONE
                    btnAddOjtDetails.visibility = View.VISIBLE
                    swipeRefreshLayout.isRefreshing = false
                }
            } else {
                swipeRefreshLayout.isRefreshing = false
            }
        }.addOnFailureListener { e ->
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getServerTimeForGreeting(nickname: String) {
        val tempDoc = db.collection("temp_time").document()
        tempDoc.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                tempDoc.get().addOnSuccessListener { doc ->
                    val serverTime = doc.getTimestamp("timestamp")
                    if (serverTime != null) {
                        val calendar = Calendar.getInstance()
                        calendar.timeZone = TimeZone.getTimeZone("Asia/Manila")
                        calendar.time = serverTime.toDate()
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)

                        val greeting = when (hour) {
                            in 0..11 -> "Good Morning"
                            in 12..17 -> "Good Afternoon"
                            else -> "Good Evening"
                        }

                        tvGreeting.text = "$greeting $nickname!"
                        tempDoc.delete()
                    }
                }
            }
    }

    private fun checkAndSyncMissedLogs() {
        // Get server time first
        val tempDoc = db.collection("temp_time").document()
        tempDoc.set(mapOf("timestamp" to FieldValue.serverTimestamp()))
            .addOnSuccessListener {
                tempDoc.get().addOnSuccessListener { timeDoc ->
                    val serverTime = timeDoc.getTimestamp("timestamp")
                    if (serverTime != null) {
                        val calendar = Calendar.getInstance()
                        calendar.timeZone = TimeZone.getTimeZone("Asia/Manila")
                        calendar.time = serverTime.toDate()

                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        sdf.timeZone = TimeZone.getTimeZone("Asia/Manila")
                        val todayDate = sdf.format(calendar.time)
                        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

                        // Clean up temp doc
                        tempDoc.delete()

                        // Now check attendance records
                        db.collection("attendance").document(uid).collection("records")
                            .get()
                            .addOnSuccessListener { result ->
                                for (doc in result.documents) {
                                    val date = doc.id
                                    if (date == todayDate && currentHour < 19) continue

                                    val amIn = doc.getTimestamp("am_in")
                                    val amOut = doc.getTimestamp("am_out")
                                    val pmIn = doc.getTimestamp("pm_in")
                                    val pmOut = doc.getTimestamp("pm_out")

                                    val missingAM = amIn != null && amOut == null
                                    val missingPM = pmIn != null && pmOut == null

                                    if (missingAM || missingPM) {
                                        val notifId = "${uid}_$date"
                                        db.collection("missedout").document(notifId).get().addOnSuccessListener { nDoc ->
                                            if (!nDoc.exists()) {
                                                val data = hashMapOf(
                                                    "userId" to uid,
                                                    "date" to date,
                                                    "status" to 0,
                                                    "timestamp" to FieldValue.serverTimestamp()
                                                )
                                                db.collection("missedout").document(notifId).set(data)
                                            }
                                        }
                                    }
                                }
                            }
                    }
                }
            }
    }

    private fun fetchAttendance() {
        db.collection("attendance").document(uid).collection("records")
            .get()
            .addOnSuccessListener { snapshot ->
                var totalAccumulatedHours = 0.0
                for (doc in snapshot.documents) {
                    val dayTotal = doc.getDouble("total_hours") ?: 0.0
                    totalAccumulatedHours += dayTotal
                }
                updateProgressUI(totalAccumulatedHours)
            }
            .addOnFailureListener { swipeRefreshLayout.isRefreshing = false }
    }

    private fun updateProgressUI(total: Double) {
        db.collection("system_preferences").get().addOnSuccessListener { system ->
            if (!system.isEmpty) {
                val required = system.documents[0].getDouble("ojt_hours") ?: 0.0
                val percent = if (required > 0) (total / required) * 100 else 0.0
                val remain = if (required - total > 0) required - total else 0.0

                tvHoursMade.text = String.format("%.1f hrs", total)
                tvProgressPercent.text = String.format("%.0f%%", percent)
                tvRequiredLabel.text = String.format("%.0f hours", required)
                tvRemainingLabel.text = String.format("%.1f hours", remain)
                progressCircle.progress = percent.toInt()
            }
            swipeRefreshLayout.isRefreshing = false
        }.addOnFailureListener { swipeRefreshLayout.isRefreshing = false }
    }
}