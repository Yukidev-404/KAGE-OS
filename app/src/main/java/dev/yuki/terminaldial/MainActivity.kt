package dev.yuki.terminaldial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Ink = Color(0xFF090D12)
private val Panel = Color(0xFF111923)
private val Accent = Color(0xFF76F7C4)
private val Muted = Color(0xFF8A9AA8)
private val ToolGroups = listOf("RECON", "WEB", "REVERSE", "CODEX", "OPENCODE", "SYSTEM")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
                    TerminalDialApp()
                }
            }
        }
    }
}

@Composable
private fun TerminalDialApp() {
    val scope = rememberCoroutineScope()
    var output by remember { mutableStateOf("TERMINAL DIAL  /  DEV BUILD\n\nBridge: not connected\nStart the Termux-side bridge to attach to your Debian session.\n") }
    var status by remember { mutableStateOf("DISCONNECTED") }
    var command by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var socket by remember { mutableStateOf<Socket?>(null) }
    var writer by remember { mutableStateOf<BufferedWriter?>(null) }

    fun sendMessage(message: JSONObject) {
        val currentWriter = writer
        if (currentWriter == null) {
            output += "\n[not connected] Start the local Termux bridge first.\n"
            return
        }
        scope.launch(Dispatchers.IO) {
            runCatching {
                synchronized(currentWriter) {
                    currentWriter.write(message.toString())
                    currentWriter.newLine()
                    currentWriter.flush()
                }
            }.onFailure {
                withContext(Dispatchers.Main) {
                    status = "CONNECTION ERROR"
                    output += "\n[send failed] ${it.message}\n"
                }
            }
        }
    }

    fun connect() {
        if (status == "CONNECTING" || status == "CONNECTED") return
        status = "CONNECTING"
        scope.launch(Dispatchers.IO) {
            try {
                val newSocket = Socket("127.0.0.1", 8765)
                val newWriter = BufferedWriter(OutputStreamWriter(newSocket.getOutputStream(), Charsets.UTF_8))
                val reader = BufferedReader(InputStreamReader(newSocket.getInputStream(), Charsets.UTF_8))
                withContext(Dispatchers.Main) {
                    socket = newSocket
                    writer = newWriter
                    status = "CONNECTED"
                    output += "\n[connected to 127.0.0.1:8765]\n"
                }
                while (!newSocket.isClosed) {
                    val line = reader.readLine() ?: break
                    val packet = runCatching { JSONObject(line) }.getOrNull()
                    if (packet != null && packet.optString("type") == "output") {
                        val chunk = packet.optString("data")
                        withContext(Dispatchers.Main) { output += chunk }
                    } else if (packet != null && packet.optString("type") == "status") {
                        val message = packet.optString("message")
                        withContext(Dispatchers.Main) { output += "\n[$message]\n" }
                    }
                }
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    status = "DISCONNECTED"
                    output += "\n[bridge unavailable] ${error.message ?: "connection failed"}\n"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    socket?.close()
                    socket = null
                    writer = null
                    if (status != "CONNECTION ERROR") status = "DISCONNECTED"
                }
            }
        }
    }

    val landscape = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp

    Column(
        modifier = Modifier.fillMaxSize().background(Ink).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TERMINAL DIAL", color = Accent, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
                Text("LOCAL SESSION CONSOLE", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
            Text("● $status", color = if (status == "CONNECTED") Accent else Muted, fontSize = 10.sp)
        }

        if (landscape) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TerminalPanel(
                    output = output,
                    command = command,
                    onCommandChange = { command = it },
                    onSend = {
                        if (it.isNotBlank()) {
                            sendMessage(JSONObject().put("type", "input").put("data", it + "\n"))
                            command = ""
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
                Column(
                    modifier = Modifier.width(270.dp).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    RotarySelector(
                        selected = selected,
                        rotation = rotation,
                        onRotation = { delta ->
                            rotation += delta
                            selected = ((rotation / 60f).roundToInt() % ToolGroups.size + ToolGroups.size) % ToolGroups.size
                        },
                        onSelect = { selected = it },
                        onActivate = {
                            sendMessage(JSONObject().put("type", "action").put("name", ToolGroups[selected]))
                            output += "\n[requested action: ${ToolGroups[selected]}]\n"
                        },
                        modifier = Modifier.size(250.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { connect() }) { Text(if (status == "CONNECTED") "CONNECTED" else "CONNECT BRIDGE") }
                }
            }
        } else {
            TerminalPanel(
                output = output,
                command = command,
                onCommandChange = { command = it },
                onSend = {
                    if (it.isNotBlank()) {
                        sendMessage(JSONObject().put("type", "input").put("data", it + "\n"))
                        command = ""
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                RotarySelector(
                    selected = selected,
                    rotation = rotation,
                    onRotation = { delta ->
                        rotation += delta
                        selected = ((rotation / 60f).roundToInt() % ToolGroups.size + ToolGroups.size) % ToolGroups.size
                    },
                    onSelect = { selected = it },
                    onActivate = {
                        sendMessage(JSONObject().put("type", "action").put("name", ToolGroups[selected]))
                        output += "\n[requested action: ${ToolGroups[selected]}]\n"
                    },
                    modifier = Modifier.size(220.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SELECTED", color = Muted, fontSize = 10.sp)
                    Text(ToolGroups[selected], color = Accent, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { connect() }) { Text(if (status == "CONNECTED") "CONNECTED" else "CONNECT") }
                }
            }
        }
    }
}

@Composable
private fun TerminalPanel(
    output: String,
    command: String,
    onCommandChange: (String) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(Panel).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("SESSION OUTPUT", color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = output.takeLast(12000),
            color = Color(0xFFD7E4EA),
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Ink).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("$ ", color = Accent, fontFamily = FontFamily.Monospace)
            BasicTextField(
                value = command,
                onValueChange = onCommandChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend(command) }),
                modifier = Modifier.weight(1f)
            )
            Button(onClick = { onSend(command) }) { Text("SEND") }
        }
    }
}

@Composable
private fun RotarySelector(
    selected: Int,
    rotation: Float,
    onRotation: (Float) -> Unit,
    onSelect: (Int) -> Unit,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .clip(CircleShape)
            .background(Panel)
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    onRotation((drag.x - drag.y) * 0.65f)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val diameter = maxWidth
        Canvas(modifier = Modifier.fillMaxSize().padding(7.dp)) {
            drawCircle(color = Color(0xFF263541), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawCircle(color = Color(0xFF18242E), radius = size.minDimension * 0.34f)
            drawCircle(color = Accent.copy(alpha = 0.16f), radius = size.minDimension * 0.28f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
            drawLine(Accent, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * 0.12f), strokeWidth = 3.dp.toPx())
        }

        val radius = diameter.value * 0.34f
        ToolGroups.forEachIndexed { index, name ->
            val angle = (index * 60f + rotation - 90f) * (PI.toFloat() / 180f)
            val x = (radius * cos(angle)).dp
            val y = (radius * sin(angle)).dp
            Box(
                modifier = Modifier.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected == index) Accent else Color(0xFF202E39))
                    .padding(horizontal = 7.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(name, color = if (selected == index) Ink else Color(0xFFD7E4EA), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
        }

        Box(
            modifier = Modifier.size(diameter * 0.30f).clip(CircleShape).background(Accent).clickable { onActivate() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "RUN\n${ToolGroups[selected]}",
                color = Ink,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
