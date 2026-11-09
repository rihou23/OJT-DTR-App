package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        if (event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {

            // Get UserID from SharedPreferences (Persistent storage)
            val prefs = context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE)
            val userId = prefs.getString("active_user_id", null) ?: return

            val db = FirebaseFirestore.getInstance()
            val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Manila"))
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(cal.time)

            val docRef = db.collection("attendance").document(userId)
                .collection("records").document(todayDate)

            docRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val updates = hashMapOf<String, Any>()

                    // Logic: If they have an am_in but no am_out, this exit is their AM OUT
                    if (doc.getTimestamp("am_in") != null && doc.getTimestamp("am_out") == null) {
                        val amIn = doc.getTimestamp("am_in")!!.toDate().time
                        val diff = (cal.timeInMillis - amIn) / (1000.0 * 60 * 60)

                        updates["am_out"] = Timestamp(cal.time)
                        updates["am_hours"] = diff
                        updates["total_hours"] = diff
                        updates["status"] = "Halfday (Auto-Exit)"
                    }
                    // Logic: If they have a pm_in but no pm_out, this exit is their PM OUT
                    else if (doc.getTimestamp("pm_in") != null && doc.getTimestamp("pm_out") == null) {
                        val pmIn = doc.getTimestamp("pm_in")!!.toDate().time
                        val pmHours = (cal.timeInMillis - pmIn) / (1000.0 * 60 * 60)
                        val amHours = doc.getDouble("am_hours") ?: 0.0

                        updates["pm_out"] = Timestamp(cal.time)
                        updates["pm_hours"] = pmHours
                        updates["total_hours"] = (amHours + pmHours)
                        updates["status"] = if (amHours > 0) "Wholeday (Auto-Exit)" else "Halfday (Auto-Exit)"
                    }

                    if (updates.isNotEmpty()) {
                        docRef.update(updates).addOnSuccessListener {
                            Log.d("GEOFENCE", "Auto-Exit recorded successfully")
                            // Stop the geofence after exit to save battery
                            removeGeofence(context)
                        }
                    }
                }
            }
        }
    }

    private fun removeGeofence(context: Context) {
        val client = com.google.android.gms.location.LocationServices.getGeofencingClient(context)
        client.removeGeofences(listOf("OJT_EXIT_FENCE"))
        context.getSharedPreferences("GeofencePrefs", Context.MODE_PRIVATE).edit().clear().apply()
    }
}