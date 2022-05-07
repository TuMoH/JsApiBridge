package com.androidtim.jsapi.bridge.webview

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.androidtim.jsapi.bridge.JsApiBridge
import com.androidtim.jsapi.bridge.OnInjectComplete
import com.androidtim.jsapi.bridge.event.EventSender
import com.androidtim.jsapi.bridge.log.DefaultLogger
import com.androidtim.jsapi.bridge.util.PatternType

@SuppressLint("SetJavaScriptEnabled")
fun WebView.addJsApi(
    jsApiName: String,
    jsApiObject: Any,
    lifecycle: Lifecycle,
    availableUrlPatterns: List<String>,
    launchOptions: Map<String, Any> = emptyMap(),
    patternType: PatternType = PatternType.GLOB,
    onInjectComplete: OnInjectComplete? = null,
    logsEnabled: Boolean = false,
    syncInjectSrc: String? = null,
): EventSender {
    val logger = if (logsEnabled) DefaultLogger() else null
    settings.javaScriptEnabled = true
    val jsApiBridge = JsApiBridge(
        webView = this,
        jsApiName = jsApiName,
        jsApiObject = jsApiObject,
        logger = logger,
    )
    webViewClient =
        JsApiWebViewClient(
            jsApi = jsApiBridge,
            availableUrlPatterns = availableUrlPatterns,
            patternType = patternType,
            syncInjectSrc = syncInjectSrc,
            launchOptions = launchOptions,
            onInjectComplete = onInjectComplete,
        )
    if (logger != null) {
        webChromeClient = JsApiWebChromeClient(logger)
    }

    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            jsApiBridge.destroy()
        }
    })
    return jsApiBridge.eventSender
}
