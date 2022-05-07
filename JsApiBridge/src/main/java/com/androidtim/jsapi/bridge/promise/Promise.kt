package com.androidtim.jsapi.bridge.promise

import androidx.annotation.AnyThread
import com.androidtim.jsapi.bridge.TAG
import com.androidtim.jsapi.bridge.log.Logger

class Promise internal constructor(
    private val promiseId: String,
    private val callback: PromiseCallback,
    private val logger: Logger?,
) {

    @Volatile
    private var completed: Boolean = false

    @AnyThread
    fun resolve(data: Any? = null) {
        if (completed) logger?.e(TAG, "already completed")
        completed = true
        callback.sendPromiseResolve(promiseId, data)
    }

    @AnyThread
    fun reject(error: String) {
        if (completed) logger?.e(TAG, "already completed")
        completed = true
        callback.sendPromiseReject(promiseId, error)
    }

}
