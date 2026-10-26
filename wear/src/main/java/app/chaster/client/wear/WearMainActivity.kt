package app.chaster.client.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
            val prefs = remember { getSharedPreferences("wear_status", MODE_PRIVATE) }
            val name = prefs.getString("name", "Chaster") ?: "Chaster"
            val end = prefs.getLong("endMillis", 0L)
            val status = prefs.getString("status", "Waiting for phone") ?: "Waiting for phone"
            val updated = prefs.getLong("updatedAt", 0L)
            val remaining = end - System.currentTimeMillis()
            val countdown = if (end > 0L && remaining > 0L) {
                val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
                "%dd %02dh %02dm".format(seconds / 86400, (seconds % 86400) / 3600, (seconds % 3600) / 60)
            } else if (end > 0L) "Time is up" else "No active timer"
            val stale = updated == 0L || System.currentTimeMillis() - updated > 6 * 60 * 60 * 1000L
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Chaster", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                        Text(name, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Text(countdown, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(status, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        if (stale) Text("Last sync may be old", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
