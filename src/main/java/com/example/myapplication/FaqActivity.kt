package com.example.myapplication

import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FaqActivity : AppCompatActivity() {

    private lateinit var faqWebView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faq)

        // 1. Setup Header and Footer
        val userId = intent.getStringExtra("user_id") ?: "Unknown"




        findViewById<ImageView>(R.id.ivBack).setOnClickListener { finish() }

        // 2. Initialize WebView
        faqWebView = findViewById(R.id.faqWebView)
        setupWebView()

        // 3. Load the URL
        faqWebView.loadUrl("https://chmsubin.great-site.net/faq.php")
    }

    private fun setupWebView() {
        val settings: WebSettings = faqWebView.settings

        // IMPORTANT: Enable JavaScript so the toggles work!
        settings.javaScriptEnabled = true

        // Allows the webview to use the browser's local storage if needed
        settings.domStorageEnabled = true

        // Ensure links and interactions stay inside the app, not opening Chrome
        faqWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // You can add a loading spinner hide logic here if you want
            }
        }

        // Optional: Improve scaling
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
    }

    // Handle back button press for the webview navigation
    override fun onBackPressed() {
        if (faqWebView.canGoBack()) {
            faqWebView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}