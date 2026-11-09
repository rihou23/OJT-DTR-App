package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MissedTimeoutActivity : AppCompatActivity() {

    private lateinit var containerMissed: LinearLayout
    private val db = FirebaseFirestore.getInstance()
    private lateinit var userId: String
    private val CHANNEL_ID = "missed_log_channel"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_missed_timeout)

        // Receive ID properly
        userId = intent.getStringExtra("user_id") ?: "default_value"
        containerMissed = findViewById(R.id.containerMissed)

        // Handle Back Button
        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
        }

        // Load data on create
        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data every time the activity is resumed (when user comes back to it)
        loadData()
    }

    private fun loadData() {
        // 1. MARK ALL AS READ IMMEDIATELY
        markAllAsRead()

        // 2. FETCH AND DISPLAY TABLE
        fetchMissedTimeouts()
    }

    private fun markAllAsRead() {
        // Updated to use the collection name from your snippet: "missedout"
        db.collection("missedout")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", 0)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val batch = db.batch()
                    for (doc in documents) {
                        batch.update(doc.reference, "status", 1)
                    }
                    batch.commit()
                }
            }
    }

    private fun fetchMissedTimeouts() {
        // First, fetch all pending requests for this user to compare later
        db.collection("attendance_requests")
            .whereEqualTo("userId", userId)
            .whereEqualTo("status", "Pending")
            .get()
            .addOnSuccessListener { requestDocs ->

                // Store pending dates in a set for quick lookup
                val pendingDates = mutableSetOf<String>()
                for (doc in requestDocs) {
                    val d = doc.getString("date")
                    if (d != null) pendingDates.add(d)
                }

                // Now fetch actual attendance records
                db.collection("attendance")
                    .document(userId)
                    .collection("records")
                    .get()
                    .addOnSuccessListener { result ->
                        containerMissed.removeAllViews()

                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        val todayDate = sdf.format(Date())
                        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

                        for (doc in result.documents) {
                            val date = doc.id

                            // Skip today if before 7 PM
                            if (date == todayDate && currentHour < 19) {
                                continue
                            }

                            val amIn = doc.getTimestamp("am_in")
                            val amOut = doc.getTimestamp("am_out")
                            val pmIn = doc.getTimestamp("pm_in")
                            val pmOut = doc.getTimestamp("pm_out")

                            val missingAM = amIn != null && amOut == null
                            val missingPM = pmIn != null && pmOut == null

                            if (missingAM || missingPM) {
                                val issues = mutableListOf<String>()
                                if (missingAM) issues.add("AM")
                                if (missingPM) issues.add("PM")

                                val issueText = issues.joinToString(" & ")

                                // Check if this specific date is in our pending list
                                val isPending = pendingDates.contains(date)

                                displayMissedRow(date, issueText, missingAM, missingPM, isPending)
                            }
                        }
                    }
            }
    }

    private fun displayMissedRow(date: String, issue: String, missingAM: Boolean, missingPM: Boolean, isPending: Boolean) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_missed_timeout, containerMissed, false)

        val txtDate = view.findViewById<TextView>(R.id.txtDate)
        val txtIssue = view.findViewById<TextView>(R.id.txtIssue)
        val btnRequest = view.findViewById<Button>(R.id.btnRequest)

        txtDate.text = date
        txtIssue.text = issue

        if (isPending) {
            // Change UI for Pending Status
            btnRequest.text = "Pending"
            btnRequest.isEnabled = false
            btnRequest.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.LTGRAY))
            btnRequest.setTextColor(Color.DKGRAY)
        } else {
            // Normal "Fix" Status
            btnRequest.setOnClickListener {
                val intent = Intent(this, RequestCompletionActivity::class.java).apply {
                    putExtra("user_id", userId)
                    putExtra("date", date)
                    putExtra("missing_am", missingAM)
                    putExtra("missing_pm", missingPM)
                }
                startActivity(intent)
            }
        }

        containerMissed.addView(view)
    }

    private fun throwStatusBarNotification(date: String, issue: String) {
        createNotificationChannel()

        val intent = Intent(this, MissedTimeoutActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("user_id", userId)
        }

        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Missed Log Detected")
            .setContentText("You missed your $issue log on $date")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(date.hashCode(), builder.build())
            } catch (e: SecurityException) {
                // Handle permission not granted for Android 13+
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Attendance Channel"
            val descriptionText = "Notifications for missed attendance"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}