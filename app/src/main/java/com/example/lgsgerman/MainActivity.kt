package com.example.lgsgerman

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
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
                    evaluateJavascript("window.getSelection().toString()") { raw ->
                        val text = try {
                            JSONArray("[$raw]").optString(0)
                        } catch (e: Exception) {
                            ""
                        }
                        if (text.isNotBlank()) onTranslate(text.take(1500))
                    }
                    mode.finish()
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

    // ভাষার তালিকা (প্রথমটা Auto detect)। নতুন ভাষা লাগলে এখানে যোগ করুন
    private val langs = linkedMapOf(
        "Auto detect" to "auto",
        "English" to "en",
        "German" to "de",
        "Bengali" to "bn",
        "Hindi" to "hi",
        "Arabic" to "ar",
        "French" to "fr",
        "Spanish" to "es"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = SelectWebView(this) { text -> showTranslate(text) }
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

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        val a = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        return a
    }

    private fun showTranslate(text: String) {
        val prefs = getSharedPreferences("translate", MODE_PRIVATE)
        val names = langs.keys.toList()
        val codes = langs.values.toList()
        val tgtNames = names.drop(1)
        val tgtCodes = codes.drop(1)

        val srcSpin = Spinner(this)
        val tgtSpin = Spinner(this)
        srcSpin.adapter = spinnerAdapter(names)
        tgtSpin.adapter = spinnerAdapter(tgtNames)
        // ডিফল্ট: মূল ভাষা = অটো, অনুবাদ = ইংরেজি
        srcSpin.setSelection(codes.indexOf(prefs.getString("src", "auto")).coerceAtLeast(0))
        tgtSpin.setSelection(tgtCodes.indexOf(prefs.getString("tgt", "en")).coerceAtLeast(0))

        val swap = Button(this).apply { this.text = "⇄" }

        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.webViewClient = WebViewClient()

        var last = ""
        fun load() {
            val sl = codes[srcSpin.selectedItemPosition]
            val tl = tgtCodes[tgtSpin.selectedItemPosition]
            prefs.edit().putString("src", sl).putString("tgt", tl).apply()
            val url = "https://translate.google.com/?sl=$sl&tl=$tl&op=translate&text=" +
                Uri.encode(text)
            if (url != last) {
                last = url
                wv.loadUrl(url)
            }
        }

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = load()
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        srcSpin.onItemSelectedListener = listener
        tgtSpin.onItemSelectedListener = listener

        swap.setOnClickListener {
            val s = srcSpin.selectedItemPosition
            if (s > 0) {
                val t = tgtSpin.selectedItemPosition
                srcSpin.setSelection(t + 1)
                tgtSpin.setSelection(s - 1)
            }
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(srcSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(swap, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(tgtSpin, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val height = (resources.displayMetrics.heightPixels * 0.6).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(row)
            addView(wv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        }

        AlertDialog.Builder(this)
            .setView(box)
            .setPositiveButton("Close", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }
}
