package com.hcl.ui

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path

@Composable
fun App(key: KeyEvent?, filePathUpdate: String, selectFile : () ->Unit) {
    MaterialTheme {
        mainView(key, filePathUpdate, selectFile)
    }
}

fun main() = application {
    var key: KeyEvent? by remember { mutableStateOf(null) }
    var filePath by remember { mutableStateOf("") }
    var isShow by remember { mutableStateOf(false) }

    Window(onCloseRequest = ::exitApplication,
        state = WindowState(size = DpSize(1100.dp, 640.dp)),
        title = "SimpleTool",
        onKeyEvent = {
            if (it.isCtrlPressed && (it.key == Key.C || it.key == Key.V)) {
                key = it
                true
            } else {
                false
            }
        }
    ) {
        App(key, filePath) {
            isShow = true
        }

        if (isShow) {
            FileDialog("Choose file") {
                it?.let { filePath = it.toFile().absolutePath }
                isShow = false
            }
        }
    }
}

@Composable
fun FrameWindowScope.FileDialog(
    title: String,  onResult: (result: Path?) -> Unit
) = AwtWindow(
    create = {
        object : FileDialog(window, title, LOAD) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    if (file != null) {
                        onResult(File(directory).resolve(file).toPath())
                    } else {
                        onResult(null)
                    }
                }
            }
        }
    }, dispose = FileDialog::dispose
)