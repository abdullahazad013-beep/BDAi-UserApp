package com.bdai.azad

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPerm: (() -> Unit)? = null

    private val fileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        fileCallback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data))
        fileCallback = null
    }
    private val audioPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingPerm?.invoke() else js("window._permDenied('audio')")
        pendingPerm = null
    }
    private val cameraPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingPerm?.invoke() else js("window._permDenied('camera')")
        pendingPerm = null
    }
    private val storagePerm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { m ->
        if (m.values.any { it }) pendingPerm?.invoke() else js("window._permDenied('storage')")
        pendingPerm = null
    }
    private val notifPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        swipe   = findViewById(R.id.swipeRefresh)
        swipe.setColorSchemeResources(R.color.accent)
        swipe.setOnRefreshListener { webView.reload() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "BDAiApp/1.0"
        }

        webView.addJavascriptInterface(Bridge(), "Native")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView?, url: String?, f: android.graphics.Bitmap?) { swipe.isRefreshing = true }
            override fun onPageFinished(v: WebView?, url: String?) { swipe.isRefreshing = false }
            override fun onReceivedError(v: WebView?, r: WebResourceRequest?, e: WebResourceError?) { swipe.isRefreshing = false }
            override fun shouldOverrideUrlLoading(v: WebView?, r: WebResourceRequest?): Boolean {
                val url = r?.url?.toString() ?: return false
                if (url.startsWith("file://") || url.contains("googleapis") || url.contains("gstatic")) return false
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(req: PermissionRequest?) { req?.grant(req.resources) }
            override fun onConsoleMessage(m: ConsoleMessage?): Boolean = true
            override fun onShowFileChooser(wv: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                fileCallback?.onReceiveValue(null); fileCallback = cb
                fileLauncher.launch(p?.createIntent()); return true
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !hasPerm(Manifest.permission.POST_NOTIFICATIONS)) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface fun requestPermission(type: String) = runOnUiThread {
            when (type) { "audio" -> askAudio(); "camera" -> askCamera(); "storage" -> askStorage() }
        }
        @JavascriptInterface fun hasPermission(type: String): Boolean = when (type) {
            "audio"   -> hasPerm(Manifest.permission.RECORD_AUDIO)
            "camera"  -> hasPerm(Manifest.permission.CAMERA)
            "storage" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                hasPerm(Manifest.permission.READ_MEDIA_IMAGES)
            else hasPerm(Manifest.permission.READ_EXTERNAL_STORAGE)
            else -> false
        }
        @JavascriptInterface fun copy(text: String) {
            val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("BDAi", text))
            runOnUiThread { Toast.makeText(this@MainActivity, "কপি হয়েছে!", Toast.LENGTH_SHORT).show() }
        }
        @JavascriptInterface fun share(text: String) = runOnUiThread {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text)
            }, "শেয়ার"))
        }
        @JavascriptInterface fun vibrate(ms: Long) {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator).vibrate(ms)
        }
        @JavascriptInterface fun version(): String = "1.0.0"
        @JavascriptInterface fun deviceId(): String =
            android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        @JavascriptInterface fun openSettings() = runOnUiThread {
            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)))
        }
    }

    private fun hasPerm(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun askAudio() {
        if (hasPerm(Manifest.permission.RECORD_AUDIO)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.perm_audio_title))
                .setMessage(getString(R.string.perm_audio_msg))
                .setPositiveButton("অনুমতি দিন") { _, _ -> audioPerm.launch(Manifest.permission.RECORD_AUDIO) }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> js("window._permDenied('audio')") }
                .show()
        } else audioPerm.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun askCamera() {
        if (hasPerm(Manifest.permission.CAMERA)) return
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.perm_camera_title))
                .setMessage(getString(R.string.perm_camera_msg))
                .setPositiveButton("অনুমতি দিন") { _, _ -> cameraPerm.launch(Manifest.permission.CAMERA) }
                .setNegativeButton(getString(R.string.cancel)) { _, _ -> js("window._permDenied('camera')") }
                .show()
        } else cameraPerm.launch(Manifest.permission.CAMERA)
    }

    private fun askStorage() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perms.all { hasPerm(it) }) return
        storagePerm.launch(perms)
    }

    private fun js(code: String) = runOnUiThread { webView.evaluateJavascript(code, null) }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (webView.canGoBack()) webView.goBack() else js("window._onBack()") }
    override fun onResume()  { super.onResume();  webView.onResume()  }
    override fun onPause()   { super.onPause();   webView.onPause()   }
    override fun onDestroy() { webView.destroy(); super.onDestroy()   }
}
