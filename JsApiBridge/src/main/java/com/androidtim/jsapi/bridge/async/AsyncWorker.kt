package com.androidtim.jsapi.bridge.async

import android.os.Handler
import java.util.*
import java.util.concurrent.Executor

typealias OnBackground<T> = () -> T
typealias OnMain<T> = (T) -> Unit

internal class AsyncWorker(
    private val executor: Executor,
    private val mainHandler: Handler,
) {

    private val subscriptions = Collections.synchronizedSet(HashSet<Subscription<*>>())

    fun <T> doWork(onBackground: OnBackground<T>) = doWork(onBackground, null)

    fun <T> doWork(onBackground: OnBackground<T>, onMain: OnMain<T>?) {
        val subscription = Subscription(onBackground, onMain)
        subscriptions += subscription
        executor.execute {
            if (!subscription.isCancelled) {
                val backgroundWork = subscription.onBackground
                if (backgroundWork != null) {
                    backgroundWork.invoke().let { result ->
                        mainHandler.post {
                            subscription.onMain?.invoke(result)
                            subscription.complete()
                        }
                    }
                } else {
                    subscription.complete()
                }
            }
            subscriptions -= subscription
        }
    }

    fun cancelAllWork() {
        subscriptions.forEach { it.cancel() }
    }


    private class Subscription<T>(onBackground: OnBackground<T>, onMain: OnMain<T>?) {
        @Volatile
        var onBackground: OnBackground<T>? = onBackground
            private set

        @Volatile
        var onMain: OnMain<T>? = onMain
            private set

        @Volatile
        var isCancelled = false
            private set

        fun cancel() {
            isCancelled = true
            complete()
        }

        fun complete() {
            onBackground = null
            onMain = null
        }
    }

}
