package com.androidtim.jsapi.bridge.async

import android.os.Handler
import androidx.annotation.WorkerThread
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@WorkerThread
fun <T> Handler.runSync(work: () -> T): T {
    val latch = CountDownLatch(1)
    var result: T? = null
    post {
        result = work()
        latch.countDown()
    }
    latch.await(30, TimeUnit.SECONDS)
    return result!!
}
