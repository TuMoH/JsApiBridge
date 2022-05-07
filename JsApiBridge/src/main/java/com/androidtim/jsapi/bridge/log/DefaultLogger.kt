package com.androidtim.jsapi.bridge.log

import android.util.Log

class DefaultLogger : Logger {

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun e(tag: String, msg: String, tr: Throwable?) {
        Log.e(tag, msg, tr)
    }

}
