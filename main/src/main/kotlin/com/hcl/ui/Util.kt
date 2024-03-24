package com.hcl.ui

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

private suspend fun runCurrentCoroutine(block: suspend () -> Unit): Job {
    with(CoroutineScope(currentCoroutineContext())) {
        return launch {
            block()
        }
    }
}

class MergedFlow<R, T>(private val sFlow: MutableSharedFlow<T> = MutableSharedFlow()) : Flow<T> by sFlow {

    private val record = mutableMapOf<R, Job>()

    suspend fun addFlow(key: R, flow: Flow<T>) {
        val job = runCurrentCoroutine {
            flow.collect {
                sFlow.emit(it)
            }
        }
        record[key]?.cancel()
        record[key] = job
    }

    fun deleteFlow(key: R) {
        record[key]?.cancel()
    }
}