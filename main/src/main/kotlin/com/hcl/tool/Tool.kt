package com.hcl.tool

import com.fasterxml.jackson.databind.ObjectMapper
import com.hcl.Config
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object Tool {

    val dateFotmat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    private fun dumpMemory(): Flow<String> {
        return flow {
            val date = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
            val fileName = "${date}.hprof"
            emit("file name $fileName")
            val exec = "adb shell pidof ${Config.memDumpProcess}".exec()
            exec.emitAll(this)
            if (exec.exit != 0) {
                return@flow
            }
            val pid = exec.out.first().trim()
            emit("get xaee pid $pid")
            "adb shell am dumpheap $pid /data/local/tmp/${fileName}".exec().emitAll(this)
            "adb pull /data/local/tmp/${fileName} file/${fileName}".exec().emitAll(this)
            val handleName = fileName.replace(".hprof", "_conv.hprof")
            if (!File("file").exists()) {
                File("file").mkdirs()
            }
            "hprof-conv file/${fileName} file/$handleName".exec().emitAll(this)
            File("file/$handleName").copyTo(File(Config.memDumpPath / fileName.replace(".hprof", "") / handleName))
            File("file/${fileName}").delete()
            File("file/${handleName}").delete()
            emit("dump file $fileName")
        }.flowOn(Dispatchers.IO)
    }

    private fun searchBugreport(rule: Regex, file: File) = flow {
        val zf = ZipFile(file)
        val entries = zf.entries().toList()
        entries.asSequence()
            .filter { isLogFile(it.name) }
            .flatMap {
                extractLog(it.name, zf.getInputStream(it))
            }
            .map {
                it.use {
                    it.name to it
                        .mapIndexedNotNull { index, s -> if (rule.containsMatchIn(s)) index to s else null }
                        .count()
                }
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .forEach {
                emit("file: ${it.first} text line number: ${it.second}")
            }
        zf.close()
        emit("finish!")
        System.gc()
    }.flowOn(Dispatchers.IO)

    data class LogStream(val name: String, private val stream: InputStream) :
        Sequence<String> by stream.bufferedReader().lineSequence(), Closeable by stream

    private fun extractLog(name: String, input: InputStream): List<LogStream> {
        return when {
            name.endsWith(".txt") || name.endsWith(".log") -> listOf(LogStream(name, input))
            name.endsWith(".gz") -> listOf(LogStream(name, GZIPInputStream(input)))
            name.endsWith(".zip") -> run {
                val r = mutableListOf<LogStream>()
                val zipStream = ZipInputStream(input)
                while (true) {
                    val nextEntry = zipStream.nextEntry ?: break
                    val entryName = nextEntry.name
                    if (isLogFile(entryName)) {
                        extractLog(entryName, zipStream.readAllBytes().inputStream()).forEach {
                            r.add(it.copy(name = "${name}:${entryName}"))
                        }
                    }
                }
                r
            }

            else -> emptyList()
        }
    }

    private fun isLogFile(name: String): Boolean {
        return name.endsWith(".log")
                || name.endsWith(".txt")
                || (name.contains("logcat") && name.endsWith(".gz"))
                || name.endsWith(".zip")
    }

    private fun logcatFile(): String {
        val date = SimpleDateFormat("MMdd").format(Date())
        val logPath = Config.logcatPath

        val index = logPath.toFile().list()?.filter {
            it.startsWith(date) && it.endsWith(".log")
        }?.mapNotNull {
            it.substring(4, it.length - 4).toIntOrNull()
        }?.maxOrNull() ?: 1

        val finaName = "${date}${(index + 1).toString().padStart(2, '0')}.log"
        return logPath / finaName
    }

    fun adb() = createSimpleCommand {

        simpleSubCommand("Scrcpy") {
            Runtime.getRuntime().exec("cmd /c scrcpy")
        }

        subCommand("Install") {
            installLastModifyApk(it.file)
        }

        subCommand("InstallBuild") {
            installLastModifyApk(Config.botBuildPath)
        }

        subCommand("InstallDl") {
            installLastModifyApk(Config.downLoadPath)
        }
    }

    fun adbLog() = createCommand {
        val exec = Runtime.getRuntime().exec("adb logcat")
        val logFile = logcatFile().toFile()
        async {
            logFile.deleteIfExist()
            logFile.bufferedWriter().use { bw ->
                exec.inputStream.bufferedReader().lineSequence().forEach {
                    bw.write(it + "\n")
                }
            }
        }

        simpleSubCommand("stop") {
            exec.destroy()
        }
        onExit {
            println("destory on exit!")
            exec.destroyForcibly()
        }

        flow {
            emit("start logcat")
            exec.errorStream.asFlow().collect {
                emit(it)
            }
            exec.waitFor()
            emit("finish logcat, write to ${logFile.name}")

            clearExitHook()
            clearSubCommand()

            simpleSubCommand("open") {
                Runtime.getRuntime().exec(arrayOf(Config.notepadPath, logFile.absolutePath))
            }
        }.flowOn(Dispatchers.IO)
    }

    fun formatJson() = createCommand {
        flow {
            val formatContent = format(ObjectMapper().readTree(it.input)).toString()
            emit(formatContent)

            simpleSubCommand("vscode open") {
                val random = UUID.randomUUID().toString().substring(0, 8)
                val file = File(Config.toolTempPath / random + ".json")
                file.deleteIfExist()
                file.writeText(formatContent)
                file.deleteOnExit()
                Runtime.getRuntime().exec(arrayOf(Config.vscodePath, file.absolutePath))
            }

        }.flowOn(Dispatchers.Default)
    }

    fun simpleTool() = createSimpleCommand {
        subCommand("dumpMem") {
            dumpMemory()
        }
        subCommand("searchTxt") {
            val file = it.file.toFile()
            if (!file.exists()) {
                return@subCommand "file ${file.absolutePath} don't exist!".toFlow()
            }
            searchBugreport(it.input.content.toRegex(), file)
        }
        subCommand("linkLog") {
            flow {
                val pattern = it.input.content
                val headPattern = Regex(".*\\[Speech .+] [^ ]+ ")
                val feature = Regex("\\[Speech \\d+-.+]").find(pattern)?.value ?: "null"
                println(feature)
                val result = it.file.toFile()
                    .bufferedReader()
                    .lineSequence()
                    .dropWhile { !it.startsWith(pattern) }
                    .filter { it.contains(feature) }
                    .zipWithNext()
                    .takeWhile { it.first.length > 3000 }
                    .flatMap {
                        if (it.second.length > 3000) listOf(it.first) else listOf(it.first, it.second)
                    }
                    .map {
                        val head = headPattern.find(it)?.value ?: ""
                        it.substring(head.length)
                    }
                    .joinToString(separator = "")
                emit(result)
                println("link log finish!")
            }.flowOn(Dispatchers.IO)
        }
        subCommand("apkInfo") {
            flow<String> {
                val version = ApkTool.getApkVersion(it.file)
                emit("versionName: $version")
            }.flowOn(Dispatchers.IO)
        }
    }
}

fun installLastModifyApk(path: String) = flow {
    var parent = path.toFile()
    val file = if (path.endsWith(".apk")) {
        parent
    } else {
        val f = parent.listFiles()?.toList()
            ?.filter { it.name.endsWith("apk") || it.name.endsWith(".zip") }
            ?.sortedByDescending { it.lastModified() }
            ?.first {
                it.name.endsWith("apk") || ZipFile(it).use { it.entries().toList().any { it.name.endsWith(".apk") } }
            } ?: if (parent.exists()) parent.also { parent = it.parentFile } else null

        if (f?.name?.endsWith(".zip") == true) {
            ZipFile(f).use {
                val entryList = it.entries().toList()
                val entry = entryList.firstOrNull { it.name.endsWith(".apk") }

                if (entry == null) {
                    emit("can't find apk in zip file!")
                    return@use null
                } else {
                    emit("try install ${f.name} of ${entry.name}!")
                    val extraName = entry.name.substringAfterLast("/")
                    val apkFile = File(parent, extraName)
                    emit("apk name $extraName oringin modify time ${Tool.dateFotmat.format(Date(entry.time))}")
                    it.getInputStream(entry).use { inputStream ->
                        val outputStream = apkFile.outputStream()
                        inputStream.copyTo(outputStream)
                        outputStream.close()
                    }
                    apkFile
                }
            }
        } else f

    }
    if (file == null) {
        emit("can't find file!")
        return@flow
    }
    emit("try install ${file.name} modify time ${Tool.dateFotmat.format(Date(file.lastModified()))}")
    val exec = "adb install -d \"${file.absolutePath}\"".exec()
    exec.err.plus(exec.out).forEach {
        emit(it)
    }
    emit("return value ${exec.exit}")
}.flowOn(Dispatchers.IO)