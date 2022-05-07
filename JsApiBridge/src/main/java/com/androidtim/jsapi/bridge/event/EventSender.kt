package com.androidtim.jsapi.bridge.event

import androidx.annotation.AnyThread

interface EventSender {
    @AnyThread
    fun send(event: String, data: Any?)
}
