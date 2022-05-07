package com.androidtim.jsapisample

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import androidx.annotation.AnyThread
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.androidtim.jsapi.bridge.event.EventSender
import com.androidtim.jsapi.bridge.promise.Promise
import org.json.JSONException
import org.json.JSONObject

private const val TAG = "JsApi"

class SampleJsApi(
    private val closeListener: OnCloseListener?,
    private val articleUpdateListener: OnArticleUpdateListener?,
    private val loginListener: OnLoginListener?,
) {

    fun interface OnCloseListener {
        @MainThread
        fun onClose()
    }

    fun interface OnArticleUpdateListener {
        @MainThread
        fun onArticleUpdate(articleInfo: ArticleInfo)
    }

    fun interface OnLoginListener {
        @MainThread
        fun onLogin(callback: (String?) -> Unit)
    }


    private val mainHandler: Handler = Handler(Looper.getMainLooper())

    @WorkerThread
    @JavascriptInterface
    fun close(promise: Promise) {
        Log.d(TAG, "close")
        if (closeListener != null) {
            mainHandler.post { closeListener.onClose() }
            promise.resolve()
        } else {
            promise.reject("Not supported on this screen")
        }
    }

    @WorkerThread
    @JavascriptInterface
    fun login(promise: Promise) {
        Log.d(TAG, "login")
        mainHandler.post {
            loginListener?.onLogin { token ->
                if (token?.isNotEmpty() == true) {
                    try {
                        val account = JSONObject().apply {
                            put("token", token)
                        }
                        promise.resolve(account)
                    } catch (e: Exception) {
                        promise.reject("Internal error: ${e.message}")
                    }
                } else {
                    promise.reject("Login canceled")
                }
            }
        }
    }

    @WorkerThread
    @JavascriptInterface
    fun articleUpdate(promise: Promise, infoJson: String) {
        Log.d(TAG, "articleUpdate: info=${infoJson}")
        try {
            val articleUpdateListener = articleUpdateListener
            if (articleUpdateListener != null) {
                val feedbackText = JSONObject(infoJson).optString("feedback")
                val info = ArticleInfo(
                    feedback = if (feedbackText.isNotEmpty()) {
                        Feedback.valueOf(feedbackText.uppercase())
                    } else {
                        null
                    }
                )
                mainHandler.post {
                    articleUpdateListener.onArticleUpdate(info)
                }
                promise.resolve()
            } else {
                promise.reject("Not supported on this screen")
            }
        } catch (e: Exception) {
            promise.reject("Unexpected error: ${e.message}")
        }
    }

    @WorkerThread
    @JavascriptInterface
    fun loadAssetImage(promise: Promise, assetName: String) {
        Log.d(TAG, "loadAssetImage: assetName=${assetName}")
        promise.resolve("file:///android_asset/${assetName}")
    }

}

@AnyThread
fun EventSender.sendArticleUpdate(articleInfo: ArticleInfo) {
    try {
        val articleJson = JSONObject().apply {
            put("feedback", articleInfo.feedback?.name?.lowercase())
        }
        send(JSEvent.ARTICLE_UPDATE, articleJson)
    } catch (e: JSONException) {
        Log.e(TAG, "sendEventArticleUpdate error", e)
    }
}

@AnyThread
private fun EventSender.send(event: JSEvent, data: Any?) = send(event.key, data)

private enum class JSEvent(val key: String) {
    ARTICLE_UPDATE("articleUpdate"),
}
