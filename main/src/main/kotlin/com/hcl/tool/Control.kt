package com.hcl.tool

import com.hcl.tool.Command.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import java.io.InputStream

enum class Command {
    adb,
    echo,
    logcat,
    fotmatJson,
    simpleTool,
    dev,
    error
}

fun handleInput(input: Request): Result {
    return when (input.command) {
        logcat -> Tool.adbLog().build(input)

        fotmatJson -> Tool.formatJson().build(input)

        adb -> Tool.adb().build(input)

        simpleTool -> Tool.simpleTool().build(input)

        echo -> createCommand { it.input.bufferedReader().readText().toFlow() }.build(input)

        dev -> createCommand { "noting in dev".toFlow() }.build(input)

        else -> createCommand { "not impl".toFlow() }.build(input)
    }
}

data class Request(val input: InputStream, val file: String, val command: Command)
data class SubCommand(val title: String, val onClick: (Request) -> Flow<String>?)
data class SubCommandList(val parent: Command, val commads: List<SubCommand>, val exit: () -> Unit = {})
data class Result(val content: Flow<String>?, val status: Flow<SubCommandList>?)

class ResultBuilder {
    val subCommandList = mutableListOf<SubCommand>()
    private val subCommandFlow: MutableSharedFlow<SubCommandList> =
        MutableSharedFlow(1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var mainCommand: (Request) -> Flow<String>? = { null }
    var isRunning = false

    private var onExit: () -> Unit = {}
    private var command: Command = error

    inline fun simpleSubCommand(title: String, crossinline action: (Request) -> Unit) {
        subCommandList.add(SubCommand(title) {
            runCatching { action(it) }
                .fold(
                    { null },
                    { flowOf(it.message ?: "exception") }
                )
                ?.catch { emit("exception $it") }
        })
        refreshSubCommand()
    }

    inline fun subCommand(title: String, crossinline action: (Request) -> Flow<String>) {
        subCommandList.add(SubCommand(title) {
            runCatching { action(it) }
                .fold(
                    { it },
                    { flowOf(it.message ?: "exception") }
                )
                .catch { emit("exception $it") }
        })
        refreshSubCommand()
    }

    fun clearSubCommand() {
        subCommandList.clear()
        refreshSubCommand()
    }

    fun deleteSubCommand(title: String) {
        subCommandList.removeIf { it.title == title }
        refreshSubCommand()
    }

    inline fun mainAction(crossinline action: ResultBuilder.(Request) -> Flow<String>) {
        mainCommand = {
            runCatching { action(it) }
                .fold(
                    { it },
                    { flowOf(it.message ?: "exception") }
                )
                .catch { emit("exception $it") }
                .also { isRunning = true }
        }
    }

    inline fun simpleMainAction(crossinline action: ResultBuilder.(Request) -> Unit) {
        mainCommand = {
            runCatching { action(it) }
                .fold(
                    { null },
                    { flowOf(it.message ?: "exception") }
                )
                ?.catch { emit("exception $it") }
                .also { isRunning = true }
        }
    }

    fun onExit(action: () -> Unit) {
        onExit = action
        refreshSubCommand()
    }

    fun clearExitHook() {
        onExit = {}
        refreshSubCommand()
    }

    fun refreshSubCommand() {
        if (isRunning) {
            runBlocking {
                subCommandFlow.emit(SubCommandList(command, subCommandList.toList(), onExit))
            }
        }
    }

    fun build(input: Request): Result {
        val content = mainCommand(input)
        command = input.command
        runBlocking {
            subCommandFlow.emit(SubCommandList(input.command, subCommandList.toList(), onExit))
        }
        return Result(content, subCommandFlow)
    }

}

inline fun createSimpleCommand(crossinline action: ResultBuilder.(Request) -> Unit): ResultBuilder {
    val builder = ResultBuilder()
    builder.simpleMainAction(action)
    return builder
}

inline fun createCommand(crossinline action: ResultBuilder.(Request) -> Flow<String>): ResultBuilder {
    val builder = ResultBuilder()
    builder.mainAction(action)
    return builder
}