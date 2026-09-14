package ma.elaroui.pos.desktop.presentation.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import ma.elaroui.pos.desktop.presentation.components.touchDragScroll
import ma.elaroui.pos.desktop.presentation.components.PosColors
import ma.elaroui.pos.shared.PlatformLogger

/** Enabled with POS_TOUCH_DIAGNOSTICS=1. It is never shown in the normal POS workflow. */
@Composable
fun TouchDiagnosticScreen(logger: PlatformLogger) {
    var buttonCount by remember { mutableIntStateOf(0) }
    var surfaceCount by remember { mutableIntStateOf(0) }
    val events = remember { mutableStateListOf<String>() }

    fun record(value: String) {
        events.add(0, value)
        while (events.size > 20) events.removeLast()
        logger.info(value, "TouchDiagnostic")
    }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Linux touchscreen diagnostic", style = MaterialTheme.typography.headlineMedium)
        Text("Tap every large target. A working tap increments its counter and adds a log entry.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Button(
                onClick = { buttonCount++; record("Compose Button click #$buttonCount") },
                modifier = Modifier.weight(1f).height(96.dp)
            ) { Text("BUTTON: $buttonCount") }
            Box(
                Modifier.weight(1f).height(96.dp).background(PosColors.Primary)
                    .pointerInput(Unit) {
                        detectTapGestures { surfaceCount++; record("Compose pointer tap #$surfaceCount") }
                    },
                contentAlignment = Alignment.Center
            ) { Text("POINTER TARGET: $surfaceCount", color = Color.White) }
        }
        Text("Recent events", style = MaterialTheme.typography.titleMedium)
        val diagListState = remember { androidx.compose.foundation.lazy.LazyListState() }
        LazyColumn(
            state = diagListState,
            modifier = Modifier
                .fillMaxSize()
                .touchDragScroll(diagListState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(events) { Text(it) }
        }
    }
}
