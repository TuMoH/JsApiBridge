package com.androidtim.jsapi.bridge

import android.content.res.AssetManager
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.androidtim.jsapi.bridge.async.AsyncWorker
import com.androidtim.jsapi.bridge.async.runSync
import com.androidtim.jsapi.bridge.event.EventSender
import com.androidtim.jsapi.bridge.log.DefaultLogger
import com.androidtim.jsapi.bridge.log.Logger
import com.androidtim.jsapi.bridge.promise.Promise
import com.androidtim.jsapi.bridge.promise.PromiseCallback
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.Executors

internal const val TAG = "JsApiBridge"

private const val NATIVE_JS_API_NAME = "NATIVE_JS_API"
private const val JS_API_BRIDGE_ASSET = "js_api_bridge.js"
private const val LAUNCH_OPTIONS_TEMPLATE = "#_launchOptions_#"
private const val URL_FOR_INJECT_TEMPLATE = "#_urlForInject_#"
private const val METHODS_TEMPLATE = "#_methods_#"
private const val API_NAME_TEMPLATE = "#_api_name_#"
private const val API_ACCESS_TOKEN = "#_api_access_token_#"

typealias OnInjectComplete = () -> Unit

class JsApiBridge @MainThread constructor(
    private val webView: WebView,
    private val jsApiName: String,
    private val jsApiObject: Any,
    workExecutor: Executor = Executors.newSingleThreadExecutor(),
    private val mainHandler: Handler = Handler(Looper.getMainLooper()),
    private val assets: AssetManager = webView.context.assets,
    private val logger: Logger? = DefaultLogger(),
) {

    val eventSender: EventSender = BridgeEventSender()

    @Volatile
    private var enabled = true

    @Volatile
    private var isDestroyed = false

    @Volatile
    private var lastInjectedUrl: String? = null

    private val apiAccessToken: String = UUID.randomUUID().toString()
    private val promiseCallback: PromiseCallback = BridgePromiseCallback()
    private val asyncWorker = AsyncWorker(workExecutor, mainHandler)

    private val jsBridgeRawText: String by lazy {
        assets.open(JS_API_BRIDGE_ASSET).bufferedReader().use { it.readText() }
    }
    private val methods: Map<String, Method> by lazy {
        jsApiObject::class.java.methods
            .filter { method ->
                method.annotations.map { it.annotationClass }
                    .contains(JavascriptInterface::class)
            }
            .associateBy { method ->
                if (method.parameterTypes.isEmpty() || method.parameterTypes[0] != Promise::class.java) {
                    throw IllegalArgumentException("First parameter in '${method.name}' should be Promise")
                }
                method.name
            }
    }
    private val methodNames: String by lazy {
        JSONArray(methods.keys.toTypedArray()).toString()
    }

    init {
        webView.addJavascriptInterface(JavascriptInterfaceClass(), NATIVE_JS_API_NAME)
        warmUp()
    }

    @MainThread
    fun destroy() {
        isDestroyed = true
        asyncWorker.cancelAllWork()
        webView.removeJavascriptInterface(NATIVE_JS_API_NAME)
    }

    @AnyThread
    fun enable() {
        logger?.d(TAG, "enable")
        enabled = true
    }

    @AnyThread
    fun disable() {
        logger?.d(TAG, "disable")
        enabled = false
    }

    @MainThread
    fun injectAsync(
        launchOptions: Map<String, Any> = emptyMap(),
        onInjectComplete: OnInjectComplete? = null,
    ) {
        if (!isEnabled()) {
            // TODO check in zen
            logger?.d(TAG, "Inject is skipped because js-api is DISABLED! url=${safeGetUrl()}")
            lastInjectedUrl = ""
            return
        }
        val urlForInject = getUrlForInject() ?: return
        asyncWorker.doWork(
            onBackground = { getProcessedJsBridgeText(urlForInject, launchOptions) },
            onMain = { processedJSApi ->
                val currentUrl = safeGetUrl()
                if (urlForInject == currentUrl) {
                    logger?.d(TAG, "evaluateJavascript for inject, url=$currentUrl")
                    safeEvaluateJs(processedJSApi) {
                        onInjectCompleted(urlForInject, onInjectComplete)
                    }
                } else {
                    logger?.d(
                        TAG, "inject canceled, url changed. " +
                                "urlForInject=$urlForInject, currentUrl=$currentUrl"
                    )
                }
            }
        )
    }

    @WorkerThread
    fun injectSync(
        launchOptions: Map<String, Any> = emptyMap(),
        onInjectComplete: OnInjectComplete? = null,
    ): WebResourceResponse? {
        if (!isEnabled()) {
            mainHandler.post {
                logger?.d(TAG, "Inject is skipped because js-api is DISABLED! url=${safeGetUrl()}")
            }
            lastInjectedUrl = ""
            return null
        }
        val urlForInject = mainHandler.runSync { getUrlForInject() } ?: return null
        val processedJSApi = getProcessedJsBridgeText(urlForInject, launchOptions)
        val response =
            WebResourceResponse("text/javascript", "utf-8", processedJSApi.byteInputStream())
        onInjectCompleted(urlForInject, onInjectComplete)
        return response
    }

    @AnyThread
    private fun warmUp() {
        asyncWorker.doWork {
            jsBridgeRawText
            methods
            methodNames
        }
    }

    private fun getUrlForInject(): String? {
        val urlForInject = safeGetUrl()
        return when {
            urlForInject == null -> {
                logger?.e(TAG, "error on inject, urlForInject == null")
                null
            }
            lastInjectedUrl == urlForInject -> {
                logger?.d(TAG, "already injected for this url=${urlForInject}")
                null
            }
            else -> urlForInject
        }
    }

    private fun getProcessedJsBridgeText(
        urlForInject: String,
        launchOptions: Map<String, Any>,
    ): String {
        logger?.d(TAG, "prepare inject, urlForInject=$urlForInject, methods=${methods.keys}")
        val launchOptionsJson = JSONObject(launchOptions).toString()
        logger?.d(TAG, "inject window.${jsApiName} with launchOptions=$launchOptionsJson")
        return jsBridgeRawText
            .replaceFirst(URL_FOR_INJECT_TEMPLATE, urlForInject)
            .replaceFirst(LAUNCH_OPTIONS_TEMPLATE, launchOptionsJson)
            .replaceFirst(METHODS_TEMPLATE, methodNames)
            .replace(API_NAME_TEMPLATE, jsApiName)
            .replace(API_ACCESS_TOKEN, apiAccessToken)
    }

    private fun onInjectCompleted(urlForInject: String, onInjectComplete: OnInjectComplete?) {
        logger?.d(TAG, "inject completed")
        lastInjectedUrl = urlForInject
        checkAndRunOnUi { onInjectComplete?.invoke() }
    }

    @MainThread
    private fun safeGetUrl(): String? {
        return if (isDestroyed) null else webView.url
    }

    @MainThread
    private fun safeEvaluateJs(script: String, resultCallback: ValueCallback<String>?) {
        if (isDestroyed) return
        webView.evaluateJavascript(script, resultCallback)
    }

    @AnyThread
    private fun sendPromiseResolve(promiseId: String, data: Any?) {
        logger?.d(TAG, "sendPromiseResolve: promiseId=$promiseId, data=$data")
        sendPromiseResult(promiseId, PromiseStatus.RESOLVE, data, null)
    }

    @AnyThread
    private fun sendPromiseReject(promiseId: String, error: String) {
        logger?.e(TAG, "sendPromiseReject: promiseId=$promiseId, error=$error")
        sendPromiseResult(promiseId, PromiseStatus.REJECT, null, error)
    }

    @AnyThread
    private fun sendPromiseResult(
        promiseId: String,
        status: PromiseStatus,
        data: Any?,
        error: String?
    ) {
        val jsonObject = JSONObject().apply {
            put("promiseId", promiseId)
            put("status", status.key)
            if (data != null) put("data", data)
            if (error != null) put("error", error)
        }
        val js = "handleJsApiPromiseResult($jsonObject)"
        checkAndRunOnUi { safeEvaluateJs(js, null) }
    }

    private fun checkAndRunOnUi(runnable: Runnable) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            mainHandler.post(runnable)
        } else {
            runnable.run()
        }
    }

    @AnyThread
    private fun isEnabled(): Boolean {
        logger?.d(TAG, "isEnabled: $enabled")
        return enabled
    }

    @WorkerThread
    private fun invokeMethodInternal(
        apiAccessToken: String,
        promiseId: String,
        methodName: String,
        args: String,
    ) {
        logger?.d(TAG, "invokeMethod: promiseId=$promiseId, methodName=$methodName, args=$args")
        if (!isEnabled()) {
            mainHandler.post {
                logger?.e(TAG, "js-api DISABLED! url=${safeGetUrl()}")
            }
            return
        }
        if (apiAccessToken != this.apiAccessToken) {
            mainHandler.post {
                logger?.e(TAG, "apiAccessToken is wrong! url=${safeGetUrl()}")
            }
            return
        }

        val method = methods[methodName]
        if (method == null) {
            sendPromiseReject(promiseId, "method '$methodName' not found")
            return
        }

        try {
            val parsedArgs = mutableListOf<Any?>(Promise(promiseId, promiseCallback, logger))
            val argsJson = JSONArray(args)
            for (i in 0..(method.parameterTypes.size - 2)) {
                parsedArgs.add(argsJson.opt(i))
            }
            method.invoke(jsApiObject, *parsedArgs.toTypedArray())
        } catch (e: Throwable) {
            val errorMessage = e.message
                ?: (e as? InvocationTargetException)?.targetException?.message
            sendPromiseReject(promiseId, "Unexpected error: message=${errorMessage}")
        }
    }


    private inner class JavascriptInterfaceClass {
        @WorkerThread
        @JavascriptInterface
        fun invokeMethod(
            apiAccessToken: String,
            promiseId: String,
            methodName: String,
            args: String,
        ) {
            asyncWorker.doWork {
                invokeMethodInternal(apiAccessToken, promiseId, methodName, args)
            }
        }
    }

    private inner class BridgePromiseCallback : PromiseCallback {
        override fun sendPromiseResolve(promiseId: String, data: Any?) =
            this@JsApiBridge.sendPromiseResolve(promiseId, data)

        override fun sendPromiseReject(promiseId: String, error: String) =
            this@JsApiBridge.sendPromiseReject(promiseId, error)
    }

    private inner class BridgeEventSender : EventSender {
        @AnyThread
        override fun send(event: String, data: Any?) {
            asyncWorker.doWork { sendInternal(event, data) }
        }

        @WorkerThread
        private fun sendInternal(event: String, data: Any?) {
            if (!isEnabled()) return
            logger?.d(TAG, "sendEvent: ${event}, data: $data")

            val jsonObject = JSONObject().apply {
                put("name", event)
                data?.let { put("data", it) }
            }
            val js = "window.onJsApiEvent($jsonObject)"

            mainHandler.post {
                safeEvaluateJs(js, null)
            }
        }
    }

}

private enum class PromiseStatus(val key: String) {
    RESOLVE("RESOLVE"),
    REJECT("REJECT"),
}
