package com.example.myapplication

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*

class GeofenceActivity : AppCompatActivity() {

    private lateinit var geofencingClient: GeofencingClient

    private var LAT = 10.109071290794766
    private var LNG = 122.87455738253325
    private val RADIUS = 150f
    private val GEOFENCE_ID = "ATTENDANCE_GEOFENCE"

    private var silentMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if silent mode is enabled
        silentMode = intent.getBooleanExtra("silent_mode", false)

        // If silent mode, don't set content view and finish immediately after setup
        if (!silentMode) {
            setContentView(R.layout.activity_geofence)
        }

        geofencingClient = LocationServices.getGeofencingClient(this)

        // Get lat/lng from AttendanceActivity if provided
        val receivedLat = intent.getDoubleExtra("latitude", LAT)
        val receivedLng = intent.getDoubleExtra("longitude", LNG)

        if (receivedLat != 0.0 && receivedLng != 0.0) {
            LAT = receivedLat
            LNG = receivedLng
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (!silentMode) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    ),
                    101
                )
            } else {
                // In silent mode, just finish if no permission
                finish()
            }
        } else {
            addGeofence()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            addGeofence()
        } else {
            if (!silentMode) {
                Toast.makeText(this, "Permission denied (app will not crash)", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private fun addGeofence() {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(LAT, LNG, RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
            .addGeofence(geofence)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }

        geofencingClient.addGeofences(request, geofencePendingIntent)
            .addOnSuccessListener {
                if (!silentMode) {
                    Toast.makeText(this, "Geofence ACTIVE ✅", Toast.LENGTH_SHORT).show()
                }
                // Close activity immediately after geofence is activated
                finish()
            }
            .addOnFailureListener {
                if (!silentMode) {
                    Toast.makeText(this, "Failed to add geofence ❌", Toast.LENGTH_SHORT).show()
                }
                finish()
            }
    }

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceReceiver::class.java)
        PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }
}