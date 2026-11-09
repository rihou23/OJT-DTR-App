package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class ExitGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return

        if (event.hasError()) {
            Log.e("EXIT_GEOFENCE", "Geofence error: ${event.errorCode}")
            return
        }

        val userId = intent.getStringExtra("user_id") ?: run {
            Log.e("EXIT_GEOFENCE", "No user_id found in intent")
            return
        }

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Manila")
            }.format(cal.time)

            val currentTime = SimpleDateFormat("hh:mm:ss a", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("Asia/Manila")
            }.format(cal.time)

            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val isAfternoonShift = hour >= 13

            val db = FirebaseFirestore.getInstance()
            val ref = db.collection("attendance")
                .document(userId)
                .collection("records")
                .document(todayDate)

            ref.get().addOnSuccessListener { doc ->
                if (doc.exists()) {

                    if (!isAfternoonShift) {
                        // Morning shift - log AM exit
                        val amIn = doc.getTimestamp("am_in")
                        val amOut = doc.getTimestamp("am_out")

                        if (amIn != null && amOut == null) {
                            val exitData = hashMapOf<String, Any>(
                                "am_exit" to Timestamp(cal.time),
                                "am_exit_time" to currentTime,
                                "exit_type" to "Unauthorized AM Exit"
                            )

                            ref.update(exitData).addOnSuccessListener {
                                Log.d("EXIT_GEOFENCE", "✅ AM Exit logged at $currentTime")
                                Toast.makeText(context, "⚠️ AM Exit Detected at $currentTime", Toast.LENGTH_LONG).show()
                            }.addOnFailureListener { e ->
                                Log.e("EXIT_GEOFENCE", "Failed to log AM exit: ${e.message}")
                            }
                        }

                    } else {
                        // Afternoon shift - log PM exit
                        val pmIn = doc.getTimestamp("pm_in")
                        val pmOut = doc.getTimestamp("pm_out")

                        if (pmIn != null && pmOut == null) {
                            val exitData = hashMapOf<String, Any>(
                                "pm_exit" to Timestamp(cal.time),
                                "pm_exit_time" to currentTime,
                                "exit_type" to "Unauthorized PM Exit"
                            )

                            ref.update(exitData).addOnSuccessListener {
                                Log.d("EXIT_GEOFENCE", "✅ PM Exit logged at $currentTime")
                                Toast.makeText(context, "⚠️ PM Exit Detected at $currentTime", Toast.LENGTH_LONG).show()
                            }.addOnFailureListener { e ->
                                Log.e("EXIT_GEOFENCE", "Failed to log PM exit: ${e.message}")
                            }
                        }
                    }
                }
            }.addOnFailureListener { e ->
                Log.e("EXIT_GEOFENCE", "Failed to fetch attendance: ${e.message}")
            }
        }
    }
}