package com.nicola.rtspviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var overlay: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fullscreen()
        setContentView(R.layout.activity_main)

        webView    = findViewById(R.id.webview)
        statusText = findViewById(R.id.status_text)
        overlay    = findViewById(R.id.overlay)

        setupWebView()

        // Bottone impostazioni
        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // Riceve aggiornamenti di stato dal servizio
        Go2RtcService.statusCallback = { msg ->
            runOnUiThread { statusText.text = msg }
        }

        requestNotificationPermission()
        Go2RtcService.start(this)

        // Carica la UI web di go2rtc dopo 3 secondi (avvio del processo)
        webView.postDelayed({ loadGo2rtc() }, 3000)
    }

    private fun loadGo2rtc() {
        webView.loadUrl("http://localhost:1984")
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled           = true
            domStorageEnabled           = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode            = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            builtInZoomControls         = false
            displayZoomControls         = false
            useWideViewPort             = true
            loadWithOverviewMode        = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                // Nasconde l'overlay quando la pagina è caricata
                overlay.animate().alpha(0f).setDuration(500).withEndAction {
                    overlay.visibility = View.GONE
                }.start()
            }
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                // go2rtc non ancora pronto → riprova tra 2s
                view?.postDelayed({ loadGo2rtc() }, 2000)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            // Permette fullscreen video nella WebView
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                super.onShowCustomView(view, callback)
            }
        }
    }

    private fun fullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        Go2RtcService.statusCallback = { msg ->
            runOnUiThread { statusText.text = msg }
        }
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        Go2RtcService.statusCallback = null
        webView.destroy()
        super.onDestroy()
        // Il ForegroundService continua — stream sempre attivo
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}
