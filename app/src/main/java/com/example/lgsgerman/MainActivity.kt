package com.example.lgsgerman

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

class SelectWebView(
    context: Context,
    private val onTranslate: (String) -> Unit
) : WebView(context) {

    private val translateId = 9991

    override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
        if (callback == null) return super.startActionMode(callback, type)

        val wrapped = object : ActionMode.Callback2() {
            private fun addItem(menu: Menu) {
                if (menu.findItem(translateId) == null) {
                    menu.add(0, translateId, 0, "Translate")
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                callback.onCreateActionMode(mode, menu)
                addItem(menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                callback.onPrepareActionMode(mode, menu)
                addItem(menu)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId == translateId) {
                    // আগে লেখা পড়ে নিই, তারপর সিলেকশন বন্ধ করি
                    evaluateJavascript("window.getSelection().toString()") { raw ->
                        val text = try {
                            JSONArray("[$raw]").optString(0)
                        } catch (e: Exception) {
                            ""
                        }
                        mode.finish()
                        if (text.isNotBlank()) onTranslate(text.trim().take(2000))
                    }
                    return true
                }
                return callback.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                callback.onDestroyActionMode(mode)
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                if (callback is ActionMode.Callback2) {
                    callback.onGetContentRect(mode, view, outRect)
                } else {
                    super.onGetContentRect(mode, view, outRect)
                }
            }
        }
        return super.startActionMode(wrapped, type)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val startUrl = "https://www.lgsgerman.com/student/dashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = SelectWebView(this) { text -> openTranslate(text) }
        webView.fitsSystemWindows = true
        setContentView(webView)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }

        webView.webViewClient = WebViewClient()

        if (savedInstanceState == null) webView.loadUrl(startUrl)
        else webView.restoreState(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack()
                else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    // Google Translate অ্যাপের নিজস্ব পপআপ খোলে
    private fun openTranslate(text: String) {
        fun makeIntent(pkg: String?) = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
            if (pkg != null) setPackage(pkg)
        }
        try {
            startActivity(makeIntent("com.google.android.apps.translate"))
        } catch (e: Exception) {
            try {
                startActivity(Intent.createChooser(makeIntent(null), "Translate"))
            } catch (e2: Exception) {
                Toast.makeText(this, "Google Translate অ্যাপ পাওয়া যায়নি", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
