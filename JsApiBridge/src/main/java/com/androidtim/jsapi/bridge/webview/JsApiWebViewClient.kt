package com.androidtim.jsapi.bridge.webview

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.androidtim.jsapi.bridge.JsApiBridge
import com.androidtim.jsapi.bridge.OnInjectComplete
import com.androidtim.jsapi.bridge.util.PatternType
import com.androidtim.jsapi.bridge.util.UrlUtils

class JsApiWebViewClient(
    private val jsApi: JsApiBridge,
    private val availableUrlPatterns: List<String>,
    private val patternType: PatternType,
    private val syncInjectSrc: String? = null,
    private val launchOptions: Map<String, Any> = emptyMap(),
    private val onInjectComplete: OnInjectComplete? = null,
) : WebViewClient() {

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (view != null && request != null && syncInjectSrc != null) {
            if (request.url?.toString() == syncInjectSrc) {
                return jsApi.injectSync(launchOptions)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        enableOrDisableJsApi(url, false)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        enableOrDisableJsApi(url, true)
    }

    private fun enableOrDisableJsApi(url: String?, pageFinished: Boolean) {
        val suitableUrl = url != null &&
                UrlUtils.verifyUrl(url, availableUrlPatterns, patternType)
        if (suitableUrl) {
            jsApi.enable()
        } else {
            jsApi.disable()
        }
        if (pageFinished && url != null) {
            jsApi.injectAsync(launchOptions)
        }
    }

}
