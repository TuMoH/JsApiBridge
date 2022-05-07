package com.androidtim.jsapi.bridge.promise

import androidx.annotation.AnyThread

internal interface PromiseCallback {

    @AnyThread
    fun sendPromiseResolve(promiseId: String, data: Any?)

    @AnyThread
    fun sendPromiseReject(promiseId: String, error: String)

}
