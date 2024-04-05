package com.hcl.tool

import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.InputStream

fun <T> Sequence<T>.section(filter: (T) -> Boolean): Sequence<T> {

    val iterator = iterator()
    var firstValue: T? = null
    while (iterator.hasNext()) {
        val next = iterator.next()
        if (filter(next)) {
            firstValue = next
            break
        }
    }

    if (firstValue == null) return emptySequence()

    val toList = mutableListOf<T>(firstValue)

    while (iterator.hasNext()) {
        toList.add(iterator.next())
    }

    val end = toList.indexOfLast(filter)
    return toList.subList(0, end).asSequence()
}

fun <T> Sequence<T>.section(start: (T) -> Boolean, end: (T) -> Boolean): Sequence<T> {
    val curentIterator = iterator()
    return object : Sequence<T> {
        override fun iterator(): Iterator<T> {
            return object : Iterator<T> {
                var nextItem: T? = null

                init {
                    while (curentIterator.hasNext()) {
                        val t = curentIterator.next()
                        if (start(t)) {
                            nextItem = t
                            break
                        }
                    }
                }

                override fun hasNext(): Boolean {
                    return nextItem != null
                }

                override fun next(): T {
                    val r = nextItem
                    nextItem = if (curentIterator.hasNext()) curentIterator.next() else null
                    nextItem = nextItem?.let { if (end(it)) null else it }
                    return r!!
                }

            }
        }
    }
}

fun <T> List<T>.section(filter: (T) -> Boolean): List<T> = subList(indexOfFirst(filter), indexOfLast(filter))

fun <T> List<T>.section(start: (T) -> Boolean, end: (T) -> Boolean): List<T> =
    subList(indexOfFirst(start), indexOfLast(end))


fun <T> List<T>.splite(containSplite: Boolean = false, predict: (T) -> Boolean): List<List<T>> {
    val result = mutableListOf<List<T>>()
    var start = 0
    for ((index, item) in this.withIndex()) {
        if (predict(item)) {
            result.add(subList(start, if (containSplite) index + 1 else index))
            start = index + 1
        }
    }
    return result
}

data class ExecResult(val command: String, val exit: Int, val out: List<String>, val err: List<String>) {
    suspend fun emitAll(flow: FlowCollector<String>) {
        err.forEach {
            flow.emit("ERR:$it")
        }
        out.forEach {
            flow.emit(it)
        }
        flow.emit("exec $command, exit value $exit")
    }

    suspend fun emitErr(flow: FlowCollector<String>) {
        err.forEach {
            flow.emit("ERR:$it")
        }
    }
}

fun String.exec(env: Array<String>? = null, dir: File? = null): ExecResult {
    println("start exec $this")
    val exec = Runtime.getRuntime().exec(this, env, dir)
    val out = mutableListOf<String>()
    val err = mutableListOf<String>()
    Tool.async {
        exec.inputStream.reader().forEachLine {
            out.add(it)
        }
    }
    Tool.async {
        exec.errorStream.reader().forEachLine {
            println("err: $it")
            err.add(it)
        }
    }
    exec.waitFor()
    val exitValue = exec.exitValue()
    return ExecResult(this, exitValue, out, err)
}

fun <T> Iterator<T>.toList(): List<T> {
    val result = mutableListOf<T>()
    while (hasNext()) {
        result.add(next())
    }
    return result
}

fun String.replaceLast(oldValue: String, newValue: String): String {
    val pos: Int = lastIndexOf(oldValue)
    return if (pos > -1) {
        substring(0, pos) + newValue + substring(pos + oldValue.length)
    } else {
        this
    }
}

fun File.deleteIfExist(): File {
    if (exists()) {
        delete()
    }
    return this
}

fun String.toFile(): File = File(this)

fun String.decapitalize(): String {
    return if (isNotEmpty() && !this[0].isLowerCase()) substring(0, 1).toLowerCase() + substring(1) else this
}

val a = String::class

operator fun JsonNode.div(param: String): JsonNode = this.path(param)
operator fun JsonNode.div(param: Int): JsonNode = this.path(param)
operator fun String.div(path: String): String = this + File.separator + path

val InputStream.content: String
    get() = this.reader().readText()

fun String.toFlow(): Flow<String> = flowOf(this)

fun InputStream.asFlow(): Flow<String> = flow {
    val reader = this@asFlow.bufferedReader()
    var line: String?
    while (true) {
        line = reader.readLine() ?: break
        emit(line + "\n")
    }
}.flowOn(Dispatchers.IO)