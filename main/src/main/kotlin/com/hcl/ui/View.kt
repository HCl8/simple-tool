package com.hcl.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.hcl.tool.Command
import com.hcl.tool.Request
import com.hcl.tool.SubCommandList
import com.hcl.tool.handleInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

val blackCommand = listOf(Command.error, Command.echo)

@Composable
fun toolList(
    status: Map<Command, SubCommandList>,
    modifier: Modifier = Modifier,
    onSubCommandClick: (Command, String) -> Unit,
    onExitClick: (Command) -> Unit,
    onClick: (Command) -> Unit,
) {
    Column(
        modifier = modifier.padding(5.dp).width(IntrinsicSize.Min).fillMaxHeight()
//            .border(2.dp, color = Color(0xffEC7063),shape = RectangleShape)
            .padding(3.dp).verticalScroll(rememberScrollState())
    ) {
        val showCommand = Command.entries.filter { !blackCommand.contains(it) }
        for (value in showCommand) {

            val subCommandList = status[value]

            Row(
                modifier = Modifier.fillMaxWidth().padding(4.dp)
                    .background(Color(0xffE67E22), shape = RoundedCornerShape(8.dp))
            ) {
                Text(value.name,
                    fontSize = 21.sp,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                        .clickable { onClick(value) }
                        .padding(8.dp))
                Box(modifier = modifier.width(30.dp).align(Alignment.CenterVertically)) {
                    if (subCommandList != null) {
                        val arrow = loadImageBitmap(this::class.java.classLoader.getResourceAsStream("img/close.png")!!)
                        Image(
                            bitmap = arrow,
                            contentDescription = "arrow",
                            modifier = Modifier.clickable { onExitClick(value) }
                        )
                    }
                }
            }

            if (subCommandList != null) {
                Column(
                    modifier = modifier.align(Alignment.End).width(IntrinsicSize.Min)
                ) {

                    for (command in subCommandList.commads) {
                        Text(command.title,
                            fontSize = 18.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                .background(Color(0xff2ecc71), shape = RoundedCornerShape(6.dp))
                                .clickable { onSubCommandClick(value, command.title) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .requestMoreWidth()
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun contentShow(output: Flow<String>, key: KeyEvent?, modifier: Modifier = Modifier, onInputChange: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(text) {
        onInputChange(text)
    }

    LaunchedEffect(key) {
        key ?: return@LaunchedEffect
        if (key.isCtrlPressed && key.key == Key.V) {
            val pastText = clipboardManager.getText()?.text ?: return@LaunchedEffect
            text = pastText
        }
    }

    Row(modifier = modifier) {
        Column(modifier = modifier.weight(1f).fillMaxHeight()) {
            Row {
                Text("Past", modifier = Modifier.clickable {
                    clipboardManager.getText()?.let {
                        text = it.text
                    }
                })
                Text("Clear", modifier = Modifier.padding(start = 12.dp).clickable {
                    text = ""
                })
            }

            scrollView(modifier = modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
                    focus.requestFocus()
                }) {
                BasicTextField(
                    text,
                    modifier = modifier.fillMaxSize().background(Color.White).focusRequester(focus),
                    onValueChange = { text = it }
                )
            }
        }
        DisplayStreamContentRealTime(
            output, key, modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun mainView(key: KeyEvent?, filePathUpdate: String, selectFile: () -> Unit) {
    val scope = rememberCoroutineScope()

    var inputContent by remember { mutableStateOf("") }
    val filePath = remember { mutableStateOf("") }
    var outStream: Flow<String> by remember { mutableStateOf(emptyFlow()) }

    val subCommand: MutableMap<Command, SubCommandList> = remember { mutableStateMapOf() }
    val subCommandUpdateFlow = remember { MergedFlow<Command, SubCommandList>() }

    LaunchedEffect(subCommandUpdateFlow) {
        subCommandUpdateFlow.collect {
            if (it.commads.isEmpty()) subCommand.remove(it.parent) else subCommand[it.parent] = it
        }
    }

    LaunchedEffect(filePathUpdate) {
        filePath.value = filePathUpdate
    }

    Row(modifier = Modifier.onExternalDrag {
        println("drag " + it.dragData.toString())
        if (it.dragData is DragData.FilesList) {
            val first = (it.dragData as DragData.FilesList).readFiles().first()
            filePath.value = File(URI.create(first)).absolutePath
        }
    }) {

        toolList(
            subCommand,
            modifier = Modifier.wrapContentWidth(),
            onSubCommandClick = { command, title ->
                val result = subCommand[command]?.commads?.firstOrNull { it.title == title }?.onClick?.invoke(
                    Request(
                        inputContent.byteInputStream(),
                        filePath.value,
                        subCommand[command]?.parent ?: Command.error
                    )
                )

                result?.let { outStream = it }
            },
            onExitClick = { subCommand[it]?.exit?.invoke(); subCommand.remove(it) }
        ) { command ->
            subCommand[command]?.exit?.invoke()
            val result = handleInput(Request(inputContent.byteInputStream(), filePath.value, command))
            result.content?.let { outStream = it }
            scope.launch {
                if (result.status != null) subCommandUpdateFlow.addFlow(
                    command,
                    result.status
                ) else subCommandUpdateFlow.deleteFlow(command)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            fileSelect(filePath, selectFile)
            contentShow(outStream, key) {
                inputContent = it
            }
        }
    }
}

@Composable
fun fileSelect(filePath: MutableState<String>, selectFile: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {

        TextField(
            filePath.value,
            singleLine = true,
            colors = TextFieldDefaults.textFieldColors(
                textColor = Color(0xFF0079D3),
                backgroundColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f), onValueChange = { filePath.value = it }
        )

        Text("Past",
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                .background(Color(0xff16A085), shape = RoundedCornerShape(6.dp))
                .clickable { filePath.value = clipboardManager.getText()?.text ?: return@clickable }
                .padding(8.dp)
                .requiredWidth(IntrinsicSize.Max)
        )

        Text("Copy",
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 8.dp, horizontal = 4.dp)
                .background(Color(0xff16A085), shape = RoundedCornerShape(6.dp))
                .clickable { clipboardManager.setText(AnnotatedString(filePath.value)) }
                .padding(8.dp)
                .requiredWidth(IntrinsicSize.Max)
        )

        Text("Open",
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
                .background(Color(0xff16A085), shape = RoundedCornerShape(6.dp))
                .clickable { selectFile.invoke() }
                .padding(8.dp)
                .requiredWidth(IntrinsicSize.Max)
        )
    }
}

@Composable
fun DisplayStreamContentRealTime(stringFlow: Flow<String>, key: KeyEvent?, modifier: Modifier = Modifier) {
    var content by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current

    LaunchedEffect(stringFlow) {
        content = ""
        stringFlow.collect { chunk ->
            val handle = if (chunk.endsWith('\n')) chunk else chunk + "\n"
            if (handle == "\r\n") {
                val index = content.trimEnd().indexOfLast { it == '\n' }
                if (index > 0) {
                    content = content.substring(0, index + 1)
                }
            } else {
                content += handle
            }
            print(handle)
        }
    }

    LaunchedEffect(key) {
        key ?: return@LaunchedEffect
        if (key.isCtrlPressed && key.key == Key.C) {
            clipboardManager.setText(AnnotatedString(content))
        }
    }

    Column(modifier) {

        Text("Copy", modifier = Modifier.clickable {
            clipboardManager.setText(AnnotatedString(content))
        })

        scrollView {
            SelectionContainer {
                Text(content, modifier = modifier.fillMaxSize())
            }
        }
    }
}


@Composable
fun scrollView(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = rememberScrollState()
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.padding(end = 10.dp).verticalScroll(state)) {
            content.invoke()
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(state),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
        )
    }
}