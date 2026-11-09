package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 1. Handle Nullability: Return early if event is null
        val event = GeofencingEvent.fromIntent(intent) ?: return

        // 2. Check for errors safely
        if (event.hasError()) {
            return
        }

        // Get user info from SharedPreferences
        val prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("active_user_id", null) ?: return
        val shift = prefs.getString("current_shift", "AM") ?: "AM" // "AM" or "PM"

        // CHECK IF GEOFENCE IS ACTIVE (not during break time)
        val isGeofenceActive = prefs.getBoolean("geofence_active", true)

        if (!isGeofenceActive) {
            // Geofence is paused (break time), don't record anything
            return
        }

        val time = SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date())
        val db = FirebaseFirestore.getInstance()

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {

                // Optional: You can still log to geofence_logs if needed
                // saveToFirestore(db, "IN", time)
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {


                // Save exit timestamp to attendance table
                saveExitToAttendance(db, userId, shift)
            }
        }
    }

    private fun saveExitToAttendance(db: FirebaseFirestore, userId: String, shift: String) {
        val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val exitField = if (shift == "AM") "am_exit" else "pm_exit"

        val ref = db.collection("attendance")
            .document(userId)
            .collection("records")
            .document(todayDate)

        // Update the document with exit timestamp
        ref.update(exitField, Timestamp(Date()))
            .addOnSuccessListener {
                // Successfully logged exit
            }
            .addOnFailureListener { e ->
                // If document doesn't exist, create it with exit field
                ref.set(mapOf(exitField to Timestamp(Date())), com.google.firebase.firestore.SetOptions.merge())
            }
    }

    private fun saveToFirestore(db: FirebaseFirestore, status: String, time: String) {
        val log = hashMapOf(
            "status" to status,
            "timestamp" to time,
            "serverTimestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        db.collection("geofence_logs")
            .add(log)
            .addOnFailureListener { e ->
                // Optional: Log error
            }
    }
}