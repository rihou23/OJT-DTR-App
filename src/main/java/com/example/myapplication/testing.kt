package com.example.myapplication



import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.*

class testting : AppCompatActivity(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var txtResult: TextView

    // Bacolod City (fixed target)
    private val targetLat = 10.6765
    private val targetLng = 122.9509

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_testting)

        txtResult = findViewById(R.id.txtResult)

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
            return
        }

        // GPS only (offline capable)
        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            2000L,
            1f,
            this
        )
    }

    override fun onLocationChanged(location: Location) {

        val myLat = location.latitude
        val myLng = location.longitude

        val distance = distanceInMeters(
            myLat,
            myLng,
            targetLat,
            targetLng
        )

        val near = distance <= 100

        txtResult.text =
            "Your location:\n" +
                    "Lat: $myLat\nLng: $myLng\n\n" +
                    "Distance to Bacolod City:\n" +
                    "${"%.2f".format(distance)} meters\n\n" +
                    if (near) "You are NEAR (within 100m)" else "You are FAR"
    }

    private fun distanceInMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val r = 6371000.0

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return r * c
    }

    override fun onProviderDisabled(provider: String) {
        Toast.makeText(this, "GPS is disabled", Toast.LENGTH_SHORT).show()
    }
}
