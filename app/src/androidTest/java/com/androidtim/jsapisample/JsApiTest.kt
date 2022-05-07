package com.androidtim.jsapisample

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.UiThreadTestRule
import com.google.gson.GsonBuilder
import com.yandex.zen.ZenAppInitializer
import com.yandex.zen.infra.initializer.DefaultConfigBuilder
import com.yandex.zenkit.auth.LoginResultListener
import com.yandex.zenkit.auth.ZenAuth
import com.yandex.zenkit.feed.StackHost
import com.yandex.zenkit.feed.ZenController
import com.yandex.zenkit.feed.config.FeedConfigProvider
import com.yandex.zenkit.infra.common.testpalm.TestPalmGroup
import com.yandex.zenkit.infra.environment.TestEnvironment
import com.yandex.zenkit.utils.ZenExecutors
import com.yandex.zenkit.webBrowser.jsinterface.DocumentPhotoParams
import com.yandex.zenkit.webBrowser.jsinterface.ZenKitJsApi
import com.yandex.zenkit.webview.ZenWebChromeClient
import com.yandex.zenkit.webview.ZenWebView
import com.yandex.zenkit.webview.ZenWebViewClient
import com.yandex.zenkit.webview.internal.system.SystemZenWebViewFactory
import io.qameta.allure.android.annotations.Epic
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
class JsApiTest {

    companion object {
        private const val LATCH_TIMEOUT = 20L
        private const val EMPTY_JS_RESULT = "EMPTY_JS_RESULT"
        private const val AWAIT_ASSERTION_MSG = "Listener not called"
        private const val CONSOLE_COUNT_DOWN_PREFIX = "COUNT_DOWN: "
        private val GSON = GsonBuilder().create()
    }

    @get:Rule
    val testRule = UiThreadTestRule()

    private var webView: ZenWebView? = null
    private var jsApi: ZenKitJsApi? = null

    private val webViewClient = JsApiTestWebViewClient()
    private val consoleSyncListener = JsApiTestConsoleSyncListener()
    private val closeListener = JsApiTestOnCloseListener()

    @Before
    fun init() {
        initZen()
        initWebView()
        syncLoadUrl("about:blank")
    }

    @Test
    fun jsApiInjectTest() {
        initJsInterface()

        syncInject()

        val result = syncEvalJs("""
            (function() {
                return window.ZENKIT != null;
            })();
        """, Boolean::class.java)
        assertTrue("window.ZENKIT not found", result)
    }

    @Test
    fun jsApiLaunchOptionsTest() {
        initJsInterface()

        syncInject(launchOptions = mapOf("testKey" to "testValue"))

        syncEvalJs("""
            (async function() {
                window.testValue = await window.ZENKIT_LAUNCH_OPTIONS;
            })();
        """)

        val result = syncEvalJs("""
            (function() {
                return window.testValue.testKey;
            })();
        """, String::class.java)
        assertEquals("Value from launchOptions", "testValue", result)
    }

    @Test
    fun jsApiLaunchOptionsWithTokenTest() {
        val zenAuth = object : ZenAuth() {
            override fun isSupported() = true
            override fun isLoggedIn(context: Context?) = true
            override fun blockingGetAuthToken(context: Context?, url: String?) = "test_token"
        }
        initJsInterface(zenAuth = zenAuth)
        syncInject(sendToken = true)

        syncEvalJs("""
            (async function() {
                window.testValue = await window.ZENKIT_LAUNCH_OPTIONS;
            })();
        """)

        val result = syncEvalJs("""
            (function() {
                return window.testValue.token;
            })();
        """, String::class.java)
        assertEquals("Value from launchOptions", "test_token", result)
    }

    @Test
    fun jsApiLaunchOptionsWithoutTokenTest() {
        val zenAuth = object : ZenAuth() {
            override fun isSupported() = true
            override fun isLoggedIn(context: Context?) = true
            override fun blockingGetAuthToken(context: Context?, url: String?) = "test_token"
        }
        initJsInterface(zenAuth = zenAuth)
        syncInject(sendToken = false)

        syncEvalJs("""
            (async function() {
                window.testValue = await window.ZENKIT_LAUNCH_OPTIONS;
            })();
        """)

        val result = syncEvalJs("""
            (function() {
                return window.testValue.token;
            })();
        """)
        assertEquals("Value from launchOptions", "null", result)
    }

    @Test
    fun jsApiAwaitInjectTest() {
        initJsInterface()

        val result1 = syncEvalJs("""
            (function() {
                let launchOptionsPromise = window.ZENKIT_LAUNCH_OPTIONS;
        
                if (!launchOptionsPromise) {
                    let resolve;
                    launchOptionsPromise = window.ZENKIT_LAUNCH_OPTIONS = new Promise((_resolve) => {
                        resolve = _resolve;
                    });
                    launchOptionsPromise.resolve = resolve;
                }
                
                launchOptionsPromise.then((launchOptions) => {
                    window.testValue = launchOptions;
                })
                return window.ZENKIT == null;
            })();
        """, Boolean::class.java)
        assertTrue("window.ZENKIT not null ? 0_o", result1)

        syncInject(launchOptions = mapOf("testKey" to "testValue"))

        val result2 = syncEvalJs("""
            (function() {
                return window.ZENKIT != null;
            })();
        """, Boolean::class.java)
        assertTrue("window.ZENKIT not found", result2)

        val result3 = syncEvalJs("""
            (function() {
                return window.testValue.testKey;
            })();
        """, String::class.java)
        assertEquals("Value from launchOptions", "testValue", result3)
    }

    @Test
    fun jsApiCloseTest() {
        initJsInterface()
        syncInject()
        closeListener.createLatch()

        syncEvalJs("""
            (function() {
                window.ZENKIT.close();
            })();
        """)
        closeListener.await()
    }

    @Test
    fun jsApiOnReadyTest() {
        val readyListener = object : SimpleSyncListener(true), ZenKitJsApi.OnReadyListener {
            override fun onReady() = countDown()
        }
        initJsInterface(readyListener = readyListener)
        syncInject()

        syncEvalJs("""
            (function() {
                window.ZENKIT.onReady();
            })();
        """)
        readyListener.await()
    }

    @Test
    fun jsApiOnErrorTest() {
        val errorListener = object : SimpleSyncListener(true), ZenKitJsApi.OnErrorListener {
            override fun onError() = countDown()
        }
        initJsInterface(errorListener = errorListener)
        syncInject()

        syncEvalJs("""
            (function() {
                window.ZENKIT.onError();
            })();
        """)
        errorListener.await()
    }

    @Test
    fun jsApiOnRefreshChannelTest() {
        val refreshChannelListener = object : SimpleSyncListener(true), ZenKitJsApi.OnRefreshChannelListener {
            override fun onRefreshChannel() = countDown()
        }
        initJsInterface(refreshChannelListener = refreshChannelListener)
        syncInject()

        syncEvalJs("""
            (function() {
                window.ZENKIT.refreshChannel();
            })();
        """)
        refreshChannelListener.await()
    }

    @Test
    fun jsApiRequestCookieAuthUrlTest() {
        var urlFromCall: String? = null
        var tldFromCall: String? = null

        val zenAuth = object : ZenAuth() {
            override fun blockingGetAuthUrl(context: Context?, url: String?, tld: String?): String {
                urlFromCall = url
                tldFromCall = tld
                return "testAuthUrl"
            }
        }

        initJsInterface(zenAuth = zenAuth)
        syncInject()

        consoleSyncListener.createLatch()
        syncEvalJs("""
            (async function() {
                let result = await window.ZENKIT.requestCookieAuthURL('retPath', 'authURLHostLocale', 'tld');
                console.log('$CONSOLE_COUNT_DOWN_PREFIX' + result);
            })();
        """)
        val result = consoleSyncListener.await()

        assertEquals("Value from requestCookieAuthURL", "testAuthUrl", result)
        assertEquals("Value from urlFromCall", "retPath", urlFromCall)
        assertEquals("Value from tldFromCall", "tld", tldFromCall)
    }

    @Test
    fun jsApiDisableTest() {
        initJsInterface()
        jsApi!!.disable()
        syncInject()

        closeListener.createLatch()
        syncEvalJs("""
            (function() {
                window.ZENKIT.close();
                window.NATIVE_ZENKIT.close('hacked!!');
            })();
        """)
        try {
            closeListener.await()
            throw AssertionError("Security is broken!")
        } catch (e: AssertionError) {
            if (e.message != AWAIT_ASSERTION_MSG) {
                //        ^^ for some reason e.message is a different String instance, so !== does not work here
                throw AssertionError("Security is broken!")
            }
        }
    }

    @Test
    fun jsApiLoginTest() {
        val token = "123"
        var performLoginInvoked = false
        var loginResultListener: LoginResultListener? = null

        val zenAuth = object : ZenAuth() {
            override fun isSupported() = true

            override fun addLoginResultListener(listener: LoginResultListener) {
                loginResultListener = listener
            }

            override fun blockingGetAuthToken(context: Context?, url: String?): String {
                return token
            }

            override fun performLogin(activity: Activity, from: LoginReferrer) {
                performLoginInvoked = true
                loginResultListener!!.onLoginResult()
            }
        }

        initJsInterface(zenAuth = zenAuth)
        syncInject()

        consoleSyncListener.createLatch()
        syncEvalJs("""
            (async function() {
                let account = await window.ZENKIT.login();
                console.log('${CONSOLE_COUNT_DOWN_PREFIX}' + account.token);
            })();
        """)
        val result = consoleSyncListener.await()

        assertTrue(performLoginInvoked)
        assertEquals("Value from login", token, result)
    }

    @Test
    fun jsCaptureDocumentPhotoTestSuccess() {

        val result = JSONObject().apply {
            put("avatarsHost", "resultAvatarsHost")
            put("groupId", 12345)
            put("imageName", "resultImageName")
            put("preview", "resultPreview")
        }

        jsCaptureDocumentPhotoTest(
                expectedResult = result.toString(),
                callMethod = { onDocumentPhotoCaptured(result) }
        )
    }

    @Test
    fun jsCaptureDocumentPhotoTestCancelled() {
        jsCaptureDocumentPhotoTest(
                expectedResult = "undefined",
                callMethod = { onCancelled() }
        )
    }

    @Test
    fun jsCaptureDocumentPhotoTestNoParams() {
        jsCaptureDocumentPhotoTest(
                expectedParams = null,
                expectedResult = "Error: No parameters available"
        )
    }

    @Test
    fun jsCaptureDocumentPhotoTestNoListener() {
        jsCaptureDocumentPhotoTest(
                expectedResult = "Error: Not supported on this screen",
                documentPhotoListenerAvailable = false
        )
    }

    private val documentPhotoParams = DocumentPhotoParams("Паспорт", "Разворот с фотографией")

    private fun jsCaptureDocumentPhotoTest(
            expectedParams: DocumentPhotoParams? = documentPhotoParams,
            expectedResult: String?,
            callMethod: ZenKitJsApi.OnDocumentPhotoResultListener.() -> Unit = {},
            documentPhotoListenerAvailable: Boolean = true
    ) {

        fun DocumentPhotoParams.toJson() = JSONObject().apply {
            put("title", title)
            put("subtitle", subTitle)
        }

        val documentPhotoListener =
                ZenKitJsApi.OnCaptureDocumentPhotoListener { actualParams, resultListener ->
                    assertEquals("title", expectedParams?.title, actualParams.title)
                    assertEquals("subtitle", expectedParams?.subTitle, actualParams.subTitle)
                    resultListener.callMethod()
                }

        initJsInterface()
        syncInject()

        if (documentPhotoListenerAvailable) {
            jsApi?.setCaptureDocumentPhotoListener(documentPhotoListener)
        }
        consoleSyncListener.createLatch()

        syncEvalJs("""
            (async function() {
                try {
                    let result = await window.ZENKIT.captureDocumentPhoto(${expectedParams?.toJson()});
                    console.log('$CONSOLE_COUNT_DOWN_PREFIX' + JSON.stringify(result));
                } catch(err) {
                    console.log('$CONSOLE_COUNT_DOWN_PREFIX' + err);
                }
            })();
        """)
        val actualResult = consoleSyncListener.await()

        assertEquals("result", expectedResult, actualResult)
    }

    private fun syncLoadUrl(url: String) {
        webViewClient.createLatch()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView!!.loadUrl(url)
        }
        webViewClient.await()
    }

    private fun syncInject(sendToken: Boolean = false, launchOptions: Map<String, Any>? = null) {
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            jsApi!!.inject("https://ya.ru", sendToken, launchOptions) {
                latch.countDown()
            }
        }
        latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)
    }

    private fun <T> syncEvalJs(script: String, resultClass: Class<T>): T {
        return GSON.fromJson(syncEvalJs(script), resultClass)
    }

    private fun syncEvalJs(script: String): String {
        var result = EMPTY_JS_RESULT
        val latch = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView!!.evaluateJavascript(script) { jsResult ->
                result = jsResult!!
                latch.countDown()
            }
        }
        latch.await(LATCH_TIMEOUT, TimeUnit.SECONDS)
        return result
    }

    private val builder = DefaultConfigBuilder.builder()
    private fun initZen() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            TestEnvironment.Mocked(
                    builder,
                    { ZenAppInitializer.getInstance().initZenAppInternal(builder) }
            ).prepare()
        }
    }

    private fun initWebView() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            webView?.destroy()
            webView = SystemZenWebViewFactory().create(TestEnvironment.targetContext).also {
                it!!.settings.setJavaScriptEnabled(true)
                it.setWebViewClient(webViewClient)
                it.setWebChromeClient(consoleSyncListener)
            }
        }
    }

    private fun initJsInterface(
            zenController: ZenController = ZenController.getInstance(),
            zenAuth: ZenAuth = zenController.zenAuth,
            stackHost: StackHost? = null,
            closeListener: ZenKitJsApi.OnCloseListener = this.closeListener,
            readyListener: ZenKitJsApi.OnReadyListener? = null,
            errorListener: ZenKitJsApi.OnErrorListener? = null,
            refreshChannelListener: ZenKitJsApi.OnRefreshChannelListener? = null,
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            jsApi = ZenKitJsApi(
                    webView!!,
                    ZenExecutors.forJsApi().get(),
                    Handler(Looper.getMainLooper()),
                    zenController,
                    zenController.featuresManager,
                    zenController.rootRouter,
                    zenAuth,
                    FeedConfigProvider.getInstance(webView!!.view.context),
                    Activity(),
                    stackHost,
                    closeListener,
                    refreshChannelListener,
                    readyListener,
                    errorListener
            )
            jsApi!!.attachToWebView()
        }
    }

    private class JsApiTestWebViewClient : SyncListener<Unit>, ZenWebViewClient() {
        private var simpleSyncListener = SimpleSyncListener(false)

        override fun onPageFinished(view: ZenWebView, url: String?) = simpleSyncListener.countDown()
        override fun createLatch() = simpleSyncListener.createLatch()
        override fun await() = simpleSyncListener.await()
    }

    private inner class JsApiTestOnCloseListener
        : SimpleSyncListener(false), ZenKitJsApi.OnCloseListener {

        override fun onClose() = countDown()
    }

    private open class SimpleSyncListener(enableLatch: Boolean) : SyncListener<Unit> {
        private var latch: CountDownLatch? = null

        init {
            if (enableLatch) createLatch()
        }

        fun countDown() {
            val latch = this.latch
            this.latch = null
            latch?.countDown()
        }

        final override fun createLatch() {
            latch = CountDownLatch(1)
        }

        final override fun await() {
            latch?.await(LATCH_TIMEOUT, TimeUnit.SECONDS)
            if (latch != null) {
                throw AssertionError(AWAIT_ASSERTION_MSG)
            }
        }
    }

    private interface SyncListener<T> {
        fun createLatch()
        fun await(): T
    }

    private class JsApiTestConsoleSyncListener : SyncListener<String?>, ZenWebChromeClient() {
        private var simpleSyncListener = SimpleSyncListener(true)
        private var result: String? = null

        override fun onConsoleMessage(message: String?, lineNumber: Int, sourceID: String?) {
            if (message != null && message.startsWith(CONSOLE_COUNT_DOWN_PREFIX)) {
                result = message.replace(CONSOLE_COUNT_DOWN_PREFIX, "")
                simpleSyncListener.countDown()
            }
        }

        override fun createLatch() = simpleSyncListener.createLatch()
        override fun await(): String? {
            simpleSyncListener.await()
            return result
        }
    }
}
