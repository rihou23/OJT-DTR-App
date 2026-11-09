package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class OjtDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var currentMarker: Marker? = null
    private val client = OkHttpClient()

    // API Key
    private val API_KEY = "AIzaSyArUNvJlQCTQcHLIHknTr_TjPEbgxKuSzg"

    private lateinit var etCompanyName: EditText
    private lateinit var etSupervisor: EditText
    private lateinit var etSearch: EditText
    private lateinit var lvSuggestions: ListView
    private lateinit var btnSave: Button
    private lateinit var btnMarkMyLocation: Button
    private lateinit var mainScrollView: ScrollView
    private lateinit var mapOverlay: View

    private lateinit var spnAMHour: Spinner
    private lateinit var spnAMMin: Spinner
    private lateinit var spnPMHour: Spinner
    private lateinit var spnPMMin: Spinner
    private lateinit var spnMapType: Spinner

    private lateinit var suggestionAdapter: ArrayAdapter<String>
    private val placeIds = mutableListOf<String>()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    private val prefs by lazy { getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE) }
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ojt_details)

        userId = intent.getStringExtra("user_id") ?: prefs.getString("user_id", "") ?: ""
        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        // Initialize Views
        mainScrollView = findViewById(R.id.mainScrollView)
        mapOverlay = findViewById(R.id.map_overlay)
        etCompanyName = findViewById(R.id.etCompanyName)
        etSupervisor = findViewById(R.id.etSupervisor)
        etSearch = findViewById(R.id.etSearch)
        lvSuggestions = findViewById(R.id.lvSuggestions)
        btnSave = findViewById(R.id.btnSubmitUpdate)
        btnMarkMyLocation = findViewById(R.id.btnMarkMyLocation)

        spnAMHour = findViewById(R.id.spnAMHour)
        spnAMMin = findViewById(R.id.spnAMMin)
        spnPMHour = findViewById(R.id.spnPMHour)
        spnPMMin = findViewById(R.id.spnPMMin)
        spnMapType = findViewById(R.id.spnMapType)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Force button colors programmatically
        btnMarkMyLocation.setBackgroundColor(Color.parseColor("#4CAF50"))
        btnSave.setBackgroundColor(Color.parseColor("#2196F3"))

        setupTimeDropdowns()
        setupMapTypeDropdown()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Map Scroll Fix
        mapOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    mainScrollView.requestDisallowInterceptTouchEvent(true)
                    false
                }
                MotionEvent.ACTION_UP -> {
                    mainScrollView.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> true
            }
        }

        // Suggestions List Touch Handler
        lvSuggestions.setOnTouchListener { _, _ ->
            mainScrollView.requestDisallowInterceptTouchEvent(true)
            false
        }

        // Custom Adapter for Suggestions with proper styling
        suggestionAdapter = object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            mutableListOf()
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#212121"))
                textView.setBackgroundColor(Color.parseColor("#FFFFFF"))
                textView.setPadding(40, 45, 40, 45)
                textView.textSize = 14f
                return view
            }
        }
        lvSuggestions.adapter = suggestionAdapter

        // Search Text Watcher
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank() && s.length >= 3) {
                    fetchSuggestions(s.toString())
                } else {
                    lvSuggestions.visibility = View.GONE
                }
            }
        })

        // Search Action
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                if (query.isNotEmpty()) performMapSearch(query)
                lvSuggestions.visibility = View.GONE
                true
            } else false
        }

        // Suggestion Item Click
        lvSuggestions.setOnItemClickListener { _, _, pos, _ ->
            etCompanyName.setText(suggestionAdapter.getItem(pos))
            getPlaceLatLng(placeIds[pos], true)
            lvSuggestions.visibility = View.GONE
            etSearch.setText("")
        }

        // Submit Button
        btnSave.setOnClickListener { showConfirmationDialog() }

        // Mark My Location Button - only places marker when clicked
        btnMarkMyLocation.setOnClickListener { markMyCurrentLocation() }
    }

    private fun setupTimeDropdowns() {
        val amHours = (6..11).map { String.format("%02d", it) }
        val pmHours = (1..12).map { String.format("%02d", it) }
        val minutes = (0..55 step 5).map { String.format("%02d", it) }

        // Custom adapters with proper styling
        val amHourAdapter = createStyledAdapter(amHours)
        val pmHourAdapter = createStyledAdapter(pmHours)
        val minuteAdapter = createStyledAdapter(minutes)

        spnAMHour.adapter = amHourAdapter
        spnAMMin.adapter = minuteAdapter
        spnPMHour.adapter = pmHourAdapter
        spnPMMin.adapter = minuteAdapter

        // Set default values
        spnAMHour.setSelection(amHours.indexOf("08"))
        spnAMMin.setSelection(minutes.indexOf("00"))
        spnPMHour.setSelection(pmHours.indexOf("01"))
        spnPMMin.setSelection(minutes.indexOf("00"))
    }

    private fun setupMapTypeDropdown() {
        val types = arrayOf("Normal", "Satellite", "Terrain", "Hybrid")
        spnMapType.adapter = createStyledAdapter(types.toList())
        spnMapType.setSelection(0)

        spnMapType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!::mMap.isInitialized) return
                mMap.mapType = when (position) {
                    1 -> GoogleMap.MAP_TYPE_SATELLITE
                    2 -> GoogleMap.MAP_TYPE_TERRAIN
                    3 -> GoogleMap.MAP_TYPE_HYBRID
                    else -> GoogleMap.MAP_TYPE_NORMAL
                }
                applyPOIStyle()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun createStyledAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#212121"))
                textView.setPadding(30, 25, 30, 25)
                textView.textSize = 15f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#212121"))
                textView.setBackgroundColor(Color.parseColor("#FFFFFF"))
                textView.setPadding(30, 35, 30, 35)
                textView.textSize = 15f
                return view
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.isBuildingsEnabled = true
        mMap.isTrafficEnabled = true
        mMap.isIndoorEnabled = true
        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isMapToolbarEnabled = true
            isMyLocationButtonEnabled = true
            isCompassEnabled = true
        }
        applyPOIStyle()

        // Map Click with Warning Dialog
        mMap.setOnMapClickListener { latLng ->
            showMarkerPlacementWarning(latLng)
        }

        // Request Location Permission and Move Camera to User Location WITHOUT placing marker
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnSuccessListener { location ->
                    location?.let {
                        val position = LatLng(it.latitude, it.longitude)
                        // Only move camera, DON'T place marker
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 15f))
                    }
                }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }

    private fun applyPOIStyle() {
        val styleJson = """[
            {"featureType":"poi","elementType":"labels","stylers":[{"visibility":"on"}]},
            {"featureType":"poi.business","elementType":"all","stylers":[{"visibility":"on"}]}
        ]"""
        mMap.setMapStyle(MapStyleOptions(styleJson))
    }

    private fun showMarkerPlacementWarning(latLng: LatLng) {
        val currentZoom = mMap.cameraPosition.zoom

        if (currentZoom < 17f) {
            // Show zoom warning
            AlertDialog.Builder(this)
                .setTitle("⚠️ Zoom In Required")
                .setMessage("Please zoom in closer (street level) before placing the marker to ensure accurate location marking.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    // Zoom to the clicked location
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 19f))
                }
                .show()
        } else {
            // Show placement confirmation
            AlertDialog.Builder(this)
                .setTitle("📍 Place Marker Here?")
                .setMessage("Please ensure this is your EXACT company location.\n\n" +
                        "Important reminders:\n" +
                        "• Place the marker precisely at your company building\n" +
                        "• Your teacher can view this marked location\n" +
                        "• Incorrect placement may cause timing issues\n\n" +
                        "Is this the correct location?")
                .setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                }
                .setPositiveButton("Confirm") { dialog, _ ->
                    placeMarker(latLng)
                    dialog.dismiss()
                }
                .show()
        }
    }

    private fun markMyCurrentLocation() {
        // Check for location permission
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Get current location and PLACE MARKER
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val myLatLng = LatLng(location.latitude, location.longitude)
                placeMarker(myLatLng)
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(myLatLng, 18f))
                Toast.makeText(this, "📍 Marked your current location", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Unable to get current location. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error getting location: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showConfirmationDialog() {
        val marker = currentMarker ?: run {
            Toast.makeText(this, "Please select a location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        val company = etCompanyName.text.toString().trim()
        val supervisor = etSupervisor.text.toString().trim()

        if (company.isEmpty()) {
            Toast.makeText(this, "Please enter Company Name", Toast.LENGTH_SHORT).show()
            etCompanyName.requestFocus()
            return
        }

        if (supervisor.isEmpty()) {
            Toast.makeText(this, "Please enter Supervisor Name", Toast.LENGTH_SHORT).show()
            etSupervisor.requestFocus()
            return
        }

        val amTime = "${spnAMHour.selectedItem}:${spnAMMin.selectedItem}"
        val pmTime = "${spnPMHour.selectedItem}:${spnPMMin.selectedItem}"

        AlertDialog.Builder(this)
            .setTitle("📋 Confirm Submission")
            .setMessage(
                "Please verify your information:\n\n" +
                        "Company: $company\n" +
                        "Supervisor: $supervisor\n" +
                        "AM Check-in: $amTime\n" +
                        "PM Check-in: $pmTime\n\n" +
                        "Make sure all information is accurate. This includes your OJT location, supervisor name, and check-in times.\n\n" +
                        "Submit now?"
            )
            .setNegativeButton("Edit") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Submit") { _, _ ->
                saveToFirestore()
            }
            .show()
    }

    private fun placeMarker(latLng: LatLng) {
        if (currentMarker == null) {
            currentMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("OJT Site")
            )
        } else {
            currentMarker!!.position = latLng
        }
    }

    private fun fetchSuggestions(query: String) {
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$query&key=$API_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@OjtDetailsActivity,
                        "Failed to fetch suggestions",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val predictions = JSONObject(body).getJSONArray("predictions")
                val names = mutableListOf<String>()
                placeIds.clear()

                for (i in 0 until predictions.length()) {
                    val item = predictions.getJSONObject(i)
                    names.add(item.getString("description"))
                    placeIds.add(item.getString("place_id"))
                }

                runOnUiThread {
                    suggestionAdapter.clear()
                    suggestionAdapter.addAll(names)
                    lvSuggestions.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        })
    }

    private fun performMapSearch(query: String) {
        val url = "https://maps.googleapis.com/maps/api/place/findplacefromtext/json?input=$query&inputtype=textquery&fields=geometry&key=$API_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@OjtDetailsActivity,
                        "Search failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val candidates = JSONObject(body).getJSONArray("candidates")

                if (candidates.length() > 0) {
                    val loc = candidates.getJSONObject(0)
                        .getJSONObject("geometry")
                        .getJSONObject("location")
                    val latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))

                    runOnUiThread {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
                    }
                }
            }
        })
    }

    private fun getPlaceLatLng(placeId: String, setMarker: Boolean) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&key=$API_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@OjtDetailsActivity,
                        "Failed to get location",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val loc = JSONObject(body)
                    .getJSONObject("result")
                    .getJSONObject("geometry")
                    .getJSONObject("location")
                val latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))

                runOnUiThread {
                    if (setMarker) placeMarker(latLng)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 19f))
                }
            }
        })
    }

    private fun saveToFirestore() {
        val marker = currentMarker ?: return
        val company = etCompanyName.text.toString().trim()
        val supervisor = etSupervisor.text.toString().trim()

        val ojtData = hashMapOf(
            "company_name" to company,
            "supervisor_name" to supervisor,
            "lat" to marker.position.latitude,
            "lng" to marker.position.longitude,
            "user_id" to userId,
            "am_in_time" to "${spnAMHour.selectedItem}:${spnAMMin.selectedItem}",
            "pm_in_time" to "${spnPMHour.selectedItem}:${spnPMMin.selectedItem}"
        )

        firestore.collection("ojt_details").document(userId).set(ojtData)
            .addOnSuccessListener {
                firestore.collection("user").document(userId)
                    .update("ojt_details", "complete")
                    .addOnSuccessListener {
                        Toast.makeText(
                            this,
                            "✓ OJT Details Saved Successfully!",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
            }
            .addOnFailureListener { exception ->
                Toast.makeText(
                    this,
                    "Failed to save: ${exception.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (::mMap.isInitialized) {
                onMapReady(mMap)
            }
        }
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try marking location again
                markMyCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission is required to use this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }
}