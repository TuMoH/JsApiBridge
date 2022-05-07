package com.androidtim.jsapi.bridge.log

interface Logger {

    fun d(tag: String, msg: String)
    fun e(tag: String, msg: String, tr: Throwable? = null)

}
