package com.androidtim.jsapisample

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.androidtim.jsapi.bridge.webview.addJsApi
import com.androidtim.jsapi.bridge.event.EventSender
import com.androidtim.jsapisample.databinding.ActivityMainBinding

private const val JS_API_NAME = "SAMPLE"
const val DEBUG_HTML_URL = "file:///android_asset/js-api-test.html"
const val AVAILABLE_URLS = "file:///android_asset/js-api-test*"
const val SYNC_INJECT_SRC = "local://mobile_js_api_sync_inject"

class MainActivity : AppCompatActivity() {

    private lateinit var eventSender: EventSender
    private lateinit var binding: ActivityMainBinding
    private var articleInfo: ArticleInfo = ArticleInfo()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this))
        setContentView(binding.root)

        WebView.setWebContentsDebuggingEnabled(true)

        val jsApi = SampleJsApi(
            closeListener = { finish() },
            articleUpdateListener = { onArticleUpdated(articleInfo = it, sendToJs = false) },
            loginListener = { loginCallback ->
                AlertDialog.Builder(this)
                    .setTitle("Login?")
                    .setPositiveButton("Yes") { _, _ -> loginCallback("user_token") }
                    .setNegativeButton("No") { _, _ -> loginCallback(null) }
                    .setOnDismissListener { loginCallback(null) }
                    .show()
            }
        )
        eventSender = binding.webView.addJsApi(
            jsApiName = JS_API_NAME,
            jsApiObject = jsApi,
            lifecycle = lifecycle,
            availableUrlPatterns = listOf(AVAILABLE_URLS),
            onInjectComplete = { eventSender.sendArticleUpdate(articleInfo) },
            launchOptions = mapOf("clientName" to "JS sample app"),
            logsEnabled = true,
            syncInjectSrc = SYNC_INJECT_SRC,
        )
        binding.webView.loadUrl(DEBUG_HTML_URL)

        binding.like.setOnClickListener {
            val newState = if (articleInfo.feedback != Feedback.LIKED) {
                Feedback.LIKED
            } else {
                null
            }
            onArticleUpdated(articleInfo = ArticleInfo(newState), sendToJs = true)
        }
        binding.dislike.setOnClickListener {
            val newState = if (articleInfo.feedback != Feedback.DISLIKED) {
                Feedback.DISLIKED
            } else {
                null
            }
            onArticleUpdated(articleInfo = ArticleInfo(newState), sendToJs = true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.webView.apply {
            stopLoading()
            removeAllViews()
            clearCache(false)
            clearHistory()
            destroy()
        }
    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun onArticleUpdated(articleInfo: ArticleInfo, sendToJs: Boolean) {
        this.articleInfo = articleInfo
        when (articleInfo.feedback) {
            Feedback.LIKED -> {
                binding.like.setBackgroundColorRes(R.color.checked_button)
                binding.dislike.setBackgroundColorRes(R.color.unchecked_button)
            }
            Feedback.DISLIKED -> {
                binding.like.setBackgroundColorRes(R.color.unchecked_button)
                binding.dislike.setBackgroundColorRes(R.color.checked_button)
            }
            else -> {
                binding.like.setBackgroundColorRes(R.color.unchecked_button)
                binding.dislike.setBackgroundColorRes(R.color.unchecked_button)
            }
        }
        if (sendToJs) {
            eventSender.sendArticleUpdate(articleInfo)
        }
    }

    @Suppress("DEPRECATION") // getColor with theme available since API 23
    private fun View.setBackgroundColorRes(@ColorRes resId: Int) =
        setBackgroundColor(resources.getColor(resId))

}
