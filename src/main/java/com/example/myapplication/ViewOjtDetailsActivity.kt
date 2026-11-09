package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore

class ViewOjtDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    private lateinit var tvCompanyName: TextView
    private lateinit var tvSupervisor: TextView
    private lateinit var tvAMTime: TextView
    private lateinit var tvPMTime: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var mainScrollView: ScrollView
    private lateinit var btnGoToEdit: Button

    private var companyLatLng: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_ojt_details)

        // Receive User ID from previous Activity or SharedPreferences
        userId = intent.getStringExtra("user_id") ?: ""

        // Initialize Views
        tvCompanyName = findViewById(R.id.tvViewCompanyName)
        tvSupervisor = findViewById(R.id.tvViewSupervisor)
        tvAMTime = findViewById(R.id.tvAMTime)
        tvPMTime = findViewById(R.id.tvPMTime)
        progressBar = findViewById(R.id.progressBar)
        mainScrollView = findViewById(R.id.mainScrollView)
        btnGoToEdit = findViewById(R.id.btnGoToEdit)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        // Setup Map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.view_map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Button to go to Edit Activity
        btnGoToEdit.setOnClickListener {
            val intent = Intent(this, EditOjtDetailsActivity::class.java)
            intent.putExtra("user_id", userId)
            startActivity(intent)
        }
    }

    /**
     * onResume is called every time you enter this activity,
     * including when you come back from the Edit screen.
     */
    override fun onResume() {
        super.onResume()
        fetchOjtDetails() // Refresh data every time activity becomes visible
    }

    private fun fetchOjtDetails() {
        if (userId.isEmpty()) {
            Toast.makeText(this, "Error: User ID missing", Toast.LENGTH_SHORT).show()
            return
        }

        // Show progress bar while refreshing
        progressBar.visibility = View.VISIBLE

        firestore.collection("ojt_details").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    tvCompanyName.text = document.getString("company_name")
                    tvSupervisor.text = document.getString("supervisor_name")
                    tvAMTime.text = document.getString("am_in_time")
                    tvPMTime.text = document.getString("pm_in_time")

                    val lat = document.getDouble("lat") ?: 0.0
                    val lng = document.getDouble("lng") ?: 0.0
                    companyLatLng = LatLng(lat, lng)

                    updateMapLocation()

                    // Hide loader, show content
                    progressBar.visibility = View.GONE
                    mainScrollView.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "No OJT details found", Toast.LENGTH_SHORT).show()
                    // Optional: If no details exist, you might want to auto-redirect to Edit
                    // val intent = Intent(this, EditOjtDetailsActivity::class.java)
                    // intent.putExtra("user_id", userId)
                    // startActivity(intent)
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load data", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.uiSettings.setAllGesturesEnabled(false) // Keep map static in view mode
        updateMapLocation()
    }

    private fun updateMapLocation() {
        if (::mMap.isInitialized && companyLatLng != null) {
            mMap.clear()
            mMap.addMarker(MarkerOptions().position(companyLatLng!!).title("OJT Location"))
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(companyLatLng!!, 17f))
        }
    }
}