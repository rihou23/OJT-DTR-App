package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.math.ceil

class ReportsActivity : AppCompatActivity() {

    private lateinit var rvGraph: RecyclerView
    private lateinit var tvStats: TextView
    private lateinit var tvPrediction: TextView
    private lateinit var webViewDTR: WebView
    private lateinit var tvYAxisTop: TextView
    private lateinit var tvYAxisMid: TextView

    // Page switching views
    private lateinit var layoutDashboard: ScrollView
    private lateinit var layoutDTRPage: LinearLayout
    private lateinit var tabDashboard: TextView
    private lateinit var tabDTR: TextView
    private lateinit var tvHeaderTitle: TextView

    private val db = FirebaseFirestore.getInstance()
    private val targetHours = 600f

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        // Bind Views
        rvGraph = findViewById(R.id.rvGraph)
        tvStats = findViewById(R.id.tvStats)
        tvPrediction = findViewById(R.id.tvPrediction)
        tvYAxisTop = findViewById(R.id.tvYAxisTop)
        tvYAxisMid = findViewById(R.id.tvYAxisMid)
        webViewDTR = findViewById(R.id.webViewDTR)

        layoutDashboard = findViewById(R.id.layoutDashboard)
        layoutDTRPage = findViewById(R.id.layoutDTRPage)
        tabDashboard = findViewById(R.id.tabDashboard)
        tabDTR = findViewById(R.id.tabDTR)
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        rvGraph.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        setupWebView()
        setupTabSwitching()

        val prefs = getSharedPreferences("APP_PREFS", Context.MODE_PRIVATE)
        val uid = prefs.getString("user_id", null) ?: ""

        if (uid.isNotEmpty()) {
            fetchRealData(uid)
            loadDTR(uid)
        }
    }

    private fun setupTabSwitching() {
        tabDashboard.setOnClickListener {
            switchPage(isDashboard = true)
        }
        tabDTR.setOnClickListener {
            switchPage(isDashboard = false)
        }
    }

    private fun switchPage(isDashboard: Boolean) {
        if (isDashboard) {
            layoutDashboard.visibility = View.VISIBLE
            layoutDTRPage.visibility = View.GONE
            tvHeaderTitle.text = "Attendance Reports"

            // UI Styling for Tabs
            tabDashboard.setBackgroundColor(Color.parseColor("#1976D2"))
            tabDashboard.setTextColor(Color.WHITE)
            tabDTR.setBackgroundColor(Color.TRANSPARENT)
            tabDTR.setTextColor(Color.parseColor("#666666"))
        } else {
            layoutDashboard.visibility = View.GONE
            layoutDTRPage.visibility = View.VISIBLE
            tvHeaderTitle.text = "DTR Document"

            // UI Styling for Tabs
            tabDTR.setBackgroundColor(Color.parseColor("#1976D2"))
            tabDTR.setTextColor(Color.WHITE)
            tabDashboard.setBackgroundColor(Color.TRANSPARENT)
            tabDashboard.setTextColor(Color.parseColor("#666666"))
        }
    }

    private fun setupWebView() {
        val settings = webViewDTR.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webViewDTR.webViewClient = WebViewClient()

        webViewDTR.addJavascriptInterface(object {
            @JavascriptInterface
            fun printPage() {
                runOnUiThread { doPrint() }
            }
        }, "AndroidInterface")
    }

    private fun doPrint() {
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "OJT_DTR_Report"
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .build()
        val printAdapter = webViewDTR.createPrintDocumentAdapter(jobName)
        printManager.print(jobName, printAdapter, attributes)
    }

    private fun loadDTR(uid: String) {
        webViewDTR.loadUrl("https://chmsubin.great-site.net/generate_dtr?user_id=$uid")
    }

    private fun fetchRealData(uid: String) {
        db.collection("attendance").document(uid).collection("records")
            .orderBy(com.google.firebase.firestore.FieldPath.documentId(), Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && !snapshot.isEmpty) {
                    val realData = mutableListOf<Pair<String, Float>>()
                    var totalAccumulated = 0f
                    var maxHours = 10f

                    for (doc in snapshot.documents) {
                        val hours = doc.getDouble("total_hours")?.toFloat() ?: 0f
                        if (hours > maxHours) maxHours = hours
                        val date = doc.id.substringAfterLast("-")
                        realData.add(Pair(date, hours))
                        totalAccumulated += hours
                    }

                    val topValue = ceil(maxHours).toInt()
                    tvYAxisTop.text = topValue.toString()
                    tvYAxisMid.text = (topValue / 2).toString()

                    rvGraph.adapter = GraphAdapter(realData, 180f / topValue) // Adjusted mult for better fit
                    rvGraph.scrollToPosition(realData.size - 1)
                    updateCalculations(totalAccumulated, realData.size)
                }
            }
    }

    private fun updateCalculations(total: Float, daysCount: Int) {
        val average = if (daysCount > 0) total / daysCount else 0f
        tvStats.text = "${"%.1f".format(total)}h / ${targetHours.toInt()}h"

        if (total >= targetHours) {
            tvPrediction.text = "Goal Reached! 🎉 All required hours have been completed."
            tvPrediction.setTextColor(Color.parseColor("#059669"))
        } else if (average > 0) {
            val remaining = targetHours - total
            val daysNeeded = ceil(remaining / average).toInt()
            tvPrediction.text = "Based on your average of ${"%.1f".format(average)}h/day, you need approximately $daysNeeded more working days to finish."
            tvPrediction.setTextColor(Color.parseColor("#4B5563"))
        }
    }

    inner class GraphAdapter(private val list: List<Pair<String, Float>>, private val mult: Float) : RecyclerView.Adapter<GraphAdapter.ViewHolder>() {
        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val bar: View = v.findViewById(R.id.viewBar)
            val dateLabel: TextView = v.findViewById(R.id.tvDateLabel)
            val valueLabel: TextView = v.findViewById(R.id.tvBarValue)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_graph_bar, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (date, hours) = list[position]
            holder.dateLabel.text = date
            holder.valueLabel.text = if(hours > 0) "%.1f".format(hours) else ""

            val params = holder.bar.layoutParams
            // Dynamic height calculation
            params.height = (hours * mult * holder.itemView.resources.displayMetrics.density).toInt().coerceAtLeast(10)
            holder.bar.layoutParams = params

            holder.bar.setBackgroundColor(if (hours >= 8f) Color.parseColor("#1976D2") else Color.parseColor("#BBDEFB"))
        }
        override fun getItemCount(): Int = list.size
    }
}