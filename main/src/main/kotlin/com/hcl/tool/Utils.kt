package com.hcl.tool

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.EmptyCoroutineContext

inline fun async(crossinline action: () -> Unit) {
    Dispatchers.IO.dispatch(EmptyCoroutineContext) {
        action.invoke()
    }
}