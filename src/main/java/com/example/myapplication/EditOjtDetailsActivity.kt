package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class EditOjtDetailsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var currentMarker: Marker? = null
    private val client = OkHttpClient()
    private val API_KEY = "AIzaSyArUNvJlQCTQcHLIHknTr_TjPEbgxKuSzg"

    private lateinit var etCompanyName: EditText
    private lateinit var etSupervisor: EditText
    private lateinit var etSearch: EditText
    private lateinit var lvSuggestions: ListView
    private lateinit var cardSuggestions: View
    private lateinit var btnSubmitUpdate: Button
    private lateinit var btnMarkMyLocation: Button
    private lateinit var mainScrollView: ScrollView
    private lateinit var mapOverlay: View
    private lateinit var spnMapType: Spinner

    private lateinit var spnAMHour: AutoCompleteTextView
    private lateinit var spnAMMin: AutoCompleteTextView
    private lateinit var spnPMHour: AutoCompleteTextView
    private lateinit var spnPMMin: AutoCompleteTextView

    private lateinit var suggestionAdapter: ArrayAdapter<String>
    private val placeIds = mutableListOf<String>()
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var userId: String

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_ojt_details)

        userId = intent.getStringExtra("user_id") ?: ""

        // Initialize Views
        mainScrollView = findViewById(R.id.mainScrollView)
        mapOverlay = findViewById(R.id.map_overlay)
        etCompanyName = findViewById(R.id.etCompanyName)
        etSupervisor = findViewById(R.id.etSupervisor)
        etSearch = findViewById(R.id.etSearch)
        lvSuggestions = findViewById(R.id.lvSuggestions)
        cardSuggestions = findViewById(R.id.cardSuggestions)
        btnSubmitUpdate = findViewById(R.id.btnSubmitUpdate)
        btnMarkMyLocation = findViewById(R.id.btnMarkMyLocation)
        spnAMHour = findViewById(R.id.spnAMHour)
        spnAMMin = findViewById(R.id.spnAMMin)
        spnPMHour = findViewById(R.id.spnPMHour)
        spnPMMin = findViewById(R.id.spnPMMin)
        spnMapType = findViewById(R.id.spnMapType)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Force button colors
        btnMarkMyLocation.setBackgroundColor(Color.parseColor("#4CAF50"))
        btnSubmitUpdate.setBackgroundColor(Color.parseColor("#FF9800"))

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        setupMapTypeSpinner()
        setupTimeDropdowns()
        setupPlaceSearch()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Fix Map Scrolling
        mapOverlay.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    mainScrollView.requestDisallowInterceptTouchEvent(true); false
                }
                MotionEvent.ACTION_UP -> {
                    mainScrollView.requestDisallowInterceptTouchEvent(false); true
                }
                else -> true
            }
        }

        // Fix Suggestion List Scrolling inside ScrollView
        lvSuggestions.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        btnSubmitUpdate.setOnClickListener { confirmUpdate() }

        // Mark My Location Button
        btnMarkMyLocation.setOnClickListener { markMyCurrentLocation() }
    }

    private fun setupMapTypeSpinner() {
        val types = arrayOf("Normal View", "Satellite View", "Hybrid View")
        val adapter = createStyledSpinnerAdapter(types.toList())
        spnMapType.adapter = adapter
        spnMapType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (!::mMap.isInitialized) return
                when (position) {
                    0 -> mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                    1 -> mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
                    2 -> mMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                }
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun createStyledSpinnerAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(
            this,
            android.R.layout.simple_spinner_item,
            items
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#1976D2"))
                textView.setPadding(20, 20, 20, 20)
                textView.textSize = 13f
                textView.setTypeface(null, android.graphics.Typeface.BOLD)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view.findViewById<TextView>(android.R.id.text1)
                textView.setTextColor(Color.parseColor("#212121"))
                textView.setBackgroundColor(Color.parseColor("#FFFFFF"))
                textView.setPadding(35, 30, 35, 30)
                textView.textSize = 14f
                return view
            }
        }
    }

    private fun fetchExistingData() {
        // FIRST: Check for PENDING update request
        firestore.collection("updateojtdetails").document(userId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists() && doc.getString("status") == "pending") {
                    fillFields(doc.data)
                    btnSubmitUpdate.text = "Update Pending Request"
                    btnSubmitUpdate.setBackgroundColor(Color.parseColor("#FF9800"))
                    Toast.makeText(this, "Editing your pending request", Toast.LENGTH_SHORT).show()
                } else {
                    // SECOND: Load official data
                    firestore.collection("ojt_details").document(userId).get()
                        .addOnSuccessListener { officialDoc ->
                            if (officialDoc.exists()) {
                                fillFields(officialDoc.data)
                                btnSubmitUpdate.text = "Submit Update Request"
                                btnSubmitUpdate.setBackgroundColor(Color.parseColor("#FF9800"))
                            }
                        }
                }
            }
    }

    private fun fillFields(data: Map<String, Any>?) {
        if (data == null) return
        etCompanyName.setText(data["company_name"]?.toString())
        etSupervisor.setText(data["supervisor_name"]?.toString())

        val amTime = data["am_in_time"]?.toString()?.split(":")
        if (amTime?.size == 2) {
            spnAMHour.setText(amTime[0], false)
            spnAMMin.setText(amTime[1], false)
        }

        val pmTime = data["pm_in_time"]?.toString()?.split(":")
        if (pmTime?.size == 2) {
            spnPMHour.setText(pmTime[0], false)
            spnPMMin.setText(pmTime[1], false)
        }

        val lat = data["lat"] as? Double ?: 0.0
        val lng = data["lng"] as? Double ?: 0.0
        if (lat != 0.0) {
            val pos = LatLng(lat, lng)
            placeMarker(pos)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 16f))
        }
    }

    private fun setupTimeDropdowns() {
        val hours = (1..12).map { String.format("%02d", it) }
        val minutes = (0..55 step 5).map { String.format("%02d", it) }
        val adapterH = ArrayAdapter(this, android.R.layout.simple_list_item_1, hours)
        val adapterM = ArrayAdapter(this, android.R.layout.simple_list_item_1, minutes)
        spnAMHour.setAdapter(adapterH); spnPMHour.setAdapter(adapterH)
        spnAMMin.setAdapter(adapterM); spnPMMin.setAdapter(adapterM)
    }

    private fun setupPlaceSearch() {
        // Custom adapter with dark text for suggestions
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
                textView.setPadding(35, 40, 35, 40)
                textView.textSize = 14f
                return view
            }
        }
        lvSuggestions.adapter = suggestionAdapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!s.isNullOrBlank() && s.length >= 3) {
                    fetchSuggestions(s.toString())
                } else {
                    cardSuggestions.visibility = View.GONE
                }
            }
        })

        lvSuggestions.setOnItemClickListener { _, _, pos, _ ->
            etCompanyName.setText(suggestionAdapter.getItem(pos))
            getPlaceLatLng(placeIds[pos])
            cardSuggestions.visibility = View.GONE
            etSearch.setText("")
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMapClickListener { placeMarker(it) }
        fetchExistingData()
    }

    private fun placeMarker(latLng: LatLng) {
        if (currentMarker == null) {
            currentMarker = mMap.addMarker(MarkerOptions().position(latLng).title("Selected Location"))
        } else {
            currentMarker!!.position = latLng
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

        // Get current location
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, try marking location again
                markMyCurrentLocation()
            } else {
                Toast.makeText(this, "Location permission is required to use this feature", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmUpdate() {
        val company = etCompanyName.text.toString().trim()
        val supervisor = etSupervisor.text.toString().trim()
        if (company.isEmpty() || supervisor.isEmpty()) {
            Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show(); return
        }

        AlertDialog.Builder(this)
            .setTitle("Request Update")
            .setMessage("Confirm submission for coordinator approval?")
            .setPositiveButton("Submit") { _, _ -> saveUpdateRequest() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveUpdateRequest() {
        val marker = currentMarker ?: run {
            Toast.makeText(this, "Please pin your location on the map", Toast.LENGTH_SHORT).show()
            return
        }

        val updateData = hashMapOf(
            "user_id" to userId,
            "company_name" to etCompanyName.text.toString(),
            "supervisor_name" to etSupervisor.text.toString(),
            "lat" to marker.position.latitude,
            "lng" to marker.position.longitude,
            "am_in_time" to "${spnAMHour.text}:${spnAMMin.text}",
            "pm_in_time" to "${spnPMHour.text}:${spnPMMin.text}",
            "status" to "pending",
            "timestamp" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("updateojtdetails").document(userId).set(updateData)
            .addOnSuccessListener {
                Toast.makeText(this, "Success! Your request is now pending approval.", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error sending request", Toast.LENGTH_SHORT).show()
            }
    }

    private fun fetchSuggestions(query: String) {
        val url = "https://maps.googleapis.com/maps/api/place/autocomplete/json?input=$query&key=$API_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
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
                    if (names.isNotEmpty()) {
                        suggestionAdapter.clear()
                        suggestionAdapter.addAll(names)
                        cardSuggestions.visibility = View.VISIBLE
                    } else {
                        cardSuggestions.visibility = View.GONE
                    }
                }
            }
        })
    }

    private fun getPlaceLatLng(placeId: String) {
        val url = "https://maps.googleapis.com/maps/api/place/details/json?place_id=$placeId&key=$API_KEY"
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: return
                val loc = JSONObject(body).getJSONObject("result").getJSONObject("geometry").getJSONObject("location")
                val latLng = LatLng(loc.getDouble("lat"), loc.getDouble("lng"))
                runOnUiThread {
                    placeMarker(latLng)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 18f))
                }
            }
        })
    }
}