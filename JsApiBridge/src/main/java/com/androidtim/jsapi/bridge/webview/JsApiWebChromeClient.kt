package com.androidtim.jsapi.bridge.webview

import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import com.androidtim.jsapi.bridge.log.Logger

class JsApiWebChromeClient(
    private val logger: Logger,
) : WebChromeClient() {

    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        consoleMessage?.let { logger.d("JsConsole", "${it.message()} on line ${it.lineNumber()}") }
        return super.onConsoleMessage(consoleMessage)
    }

}
