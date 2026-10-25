package app.chaster.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = TokenStore(applicationContext)
        setContent { ChasterApp(store) }
    }
}

private enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable private fun ChasterTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) darkColorScheme(
        primary = Color(0xFFB7C5FF),
        secondary = Color(0xFFB8D8CB),
        background = Color(0xFF101318),
        surface = Color(0xFF191E25)
    ) else lightColorScheme(
        primary = Color(0xFF4056A1),
        secondary = Color(0xFF42685B),
        background = Color(0xFFF7F7FB),
        surface = Color(0xFFFFFFFF)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

enum class Page(val label: String) { Locks("Locks"), History("History"), Profile("Profile"), Settings("Settings") }

class ChasterViewModel(private val repository: ChasterRepository, private val store: TokenStore) : ViewModel() {
    var page by mutableStateOf(Page.Locks); private set
    var tokenInput by mutableStateOf(store.read())
    var locks by mutableStateOf<List<ChasterLock>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList()); private set
    var profile by mutableStateOf<ChasterProfile?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf(if (store.read().isBlank()) "Connect your Chaster account to get started." else "Ready to connect.")
        private set
    var connected by mutableStateOf(store.read().isNotBlank()); private set
    fun navigate(value: Page) { page = value }
    fun saveToken() {
        if (tokenInput.isBlank()) {
            message = "Paste your Chaster developer token first."
            return
        }
        store.write(tokenInput)
        connected = true
        message = "Token saved securely. Connecting to Chaster…"
        refreshLocks()
    }
    fun clearToken() {
        store.clear(); tokenInput = ""; connected = false
        locks = emptyList(); history = emptyList(); profile = null
        page = Page.Locks
        message = "Disconnected. Your token was cleared from this device."
    }
    fun refreshLocks() = task("Loading locks…") {
        locks = repository.locks(); message = "Loaded ${locks.size} lock(s)."
    }
    fun addTime(lockId: String, seconds: Long) = lockAction("Adding time…") { repository.addTime(lockId, seconds) }
    fun increaseMaximum(lockId: String, seconds: Long) = lockAction("Increasing maximum duration…") { repository.increaseMaximum(lockId, seconds) }
    fun increaseMinimum(lockId: String, seconds: Long) = lockAction("Increasing minimum duration…") { repository.increaseMinimum(lockId, seconds) }
    private fun lockAction(start: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; message = start
            try {
                action()
                locks = repository.locks()
                message = "Action completed and locks refreshed."
            } catch (e: Exception) {
                message = e.message ?: "Lock action failed. Check lock permissions and API access."
            } finally { busy = false }
        }
    }
        fun refreshProfile() = task("Loading profile…") { profile = repository.profile(); message = "Profile refreshed." }
    fun refreshHistory() = task("Loading lock history…") {
        if (locks.isEmpty()) locks = repository.locks()
        history = repository.history(locks); message = "Loaded ${history.size} history event(s)."
    }
    private fun task(start: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; message = start
            try { action() } catch (e: Exception) {
                message = e.message ?: "Request failed. Check your token and Chaster API access."
            } finally { busy = false }
        }
    }
}

@Composable fun ChasterApp(store: TokenStore) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("chaster_display_settings", android.content.Context.MODE_PRIVATE) }
    var themeMode by remember {
        mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM))
    }
    var showAppearance by remember { mutableStateOf(false) }
    val vm: ChasterViewModel = viewModel(factory = ChasterViewModelFactory(store))

    LaunchedEffect(vm.locks) {
        LockCountdownWidgetProvider.updateFromLocks(context, vm.locks)
    }

    ChasterTheme(themeMode) {
        if (!vm.connected) {
            ConnectScreen(vm)
        } else {
            Scaffold(bottomBar = {
                NavigationBar {
                    Page.entries.forEach { page ->
                        val icon = when (page) {
                            Page.Locks -> Icons.Default.Lock
                            Page.History -> Icons.Default.History
                            Page.Profile -> Icons.Default.Person
                            Page.Settings -> Icons.Default.Settings
                        }
                        NavigationBarItem(selected = vm.page == page, onClick = { vm.navigate(page) },
                            icon = { Icon(icon, contentDescription = page.label) }, label = { Text(page.label) })
                    }
                }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Chaster", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Connected account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (vm.page != Page.Settings) {
                            TextButton(onClick = { vm.navigate(Page.Settings) }) { Text("Settings") }
                        }
                        TextButton(onClick = vm::clearToken, enabled = !vm.busy) { Text("Disconnect") }
                    }
                    when (vm.page) {
                        Page.Locks -> LocksScreen(vm)
                        Page.History -> HistoryScreen(vm)
                        Page.Profile -> ProfileScreen(vm)
                        Page.Settings -> SettingsScreen(
                            vm = vm,
                            themeMode = themeMode,
                            onAppearance = { showAppearance = true },
                            onDisconnect = vm::clearToken
                        )
                    }
                }
            }
        }
        if (showAppearance) {
            AppearanceDialog(
                currentMode = themeMode,
                onModeSelected = {
                    themeMode = it
                    prefs.edit().putString("theme_mode", it.name).apply()
                },
                onDismiss = { showAppearance = false }
            )
        }
    }
}

@Composable private fun ConnectScreen(vm: ChasterViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Chaster", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Connect your Chaster account", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Use a personal developer token from Chaster's developer interface. It stays encrypted on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = vm.tokenInput, onValueChange = { vm.tokenInput = it },
            label = { Text("Developer token") }, placeholder = { Text("Paste your Chaster token") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = vm::saveToken, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.busy) "Connecting…" else "Connect")
        }
        if (vm.message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(vm.message, color = if (vm.message.contains("HTTP") || vm.message.contains("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun LocksScreen(vm: ChasterViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text("Lock sessions", style = MaterialTheme.typography.titleMedium)
            Text("${vm.locks.size} loaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = vm::refreshLocks, enabled = !vm.busy) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh")
        }
    }
    StatusText(vm.message, vm.busy)
    if (vm.locks.isEmpty()) EmptyState("No locks loaded", "Refresh to load your Chaster locks.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.locks, key = { it.id }) { lock ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lock.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(lock.status) })
                    }
                    Text("Time remaining: ${countdown(lock.endDate)}", style = MaterialTheme.typography.bodyLarge)
                    lock.endDate?.let { Text("End: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { vm.addTime(lock.id, 3600L) }, enabled = !vm.busy) { Text("+1 hour") }
                        TextButton(onClick = { vm.increaseMaximum(lock.id, 86400L) }, enabled = !vm.busy) { Text("Max +1 day") }
                        TextButton(onClick = { vm.increaseMinimum(lock.id, 86400L) }, enabled = !vm.busy) { Text("Min +1 day") }
                    }
                }
            }
        }
    }
}

@Composable private fun HistoryScreen(vm: ChasterViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Lock history", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = vm::refreshHistory, enabled = !vm.busy) { Text("Load history") }
    }
    StatusText(vm.message, vm.busy)
    if (vm.history.isEmpty()) EmptyState("No history loaded", "Tap Load history to fetch entries for your locks.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(vm.history) { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(entry.title, style = MaterialTheme.typography.titleSmall)
                    Text(entry.lockName, color = MaterialTheme.colorScheme.secondary)
                    entry.date?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable private fun ProfileScreen(vm: ChasterViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Account details", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = vm::refreshProfile, enabled = !vm.busy) { Text("Refresh") }
    }
    StatusText(vm.message, vm.busy)
    val profile = vm.profile
    if (profile == null) EmptyState("Profile not loaded", "Tap Refresh to fetch your Chaster account profile.")
    else ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Connected with developer token", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingsScreen(
    vm: ChasterViewModel,
    themeMode: ThemeMode,
    onAppearance: () -> Unit,
    onDisconnect: () -> Unit
) {
    Text("Account", style = MaterialTheme.typography.titleMedium)
    Text("Your developer token is encrypted on this device and hidden after setup. To change it, disconnect first, then connect again with the new token.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = onAppearance, modifier = Modifier.fillMaxWidth()) {
        Text("Appearance: " + when (themeMode) {
            ThemeMode.SYSTEM -> "System"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
        })
    }
    Divider()
    Text("Developer token", style = MaterialTheme.typography.titleMedium)
    Text("Developer token saved securely", color = MaterialTheme.colorScheme.secondary)
    OutlinedButton(onClick = onDisconnect, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
        Text("Disconnect and clear token")
    }
    StatusText(vm.message, vm.busy)
    Text("API requests use HTTPS. The Chaster Public API is in beta, so actions will only be added after their request formats are verified.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable private fun AppearanceDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Appearance") },
        text = {
            Column {
                Text("Choose how Chaster should look.")
                Spacer(Modifier.height(8.dp))
                ThemeOption("System", ThemeMode.SYSTEM, currentMode, onModeSelected)
                ThemeOption("Light", ThemeMode.LIGHT, currentMode, onModeSelected)
                ThemeOption("Dark", ThemeMode.DARK, currentMode, onModeSelected)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable private fun ThemeOption(label: String, mode: ThemeMode, currentMode: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = currentMode == mode, onClick = { onSelected(mode) })
        TextButton(onClick = { onSelected(mode) }) { Text(label) }
    }
}

@Composable private fun StatusText(message: String, busy: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun EmptyState(title: String, body: String) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun countdown(endDate: String?): String {
    if (endDate.isNullOrBlank()) return "Not provided by API"
    val end = try {
        try { Instant.parse(endDate) } catch (_: DateTimeParseException) { OffsetDateTime.parse(endDate).toInstant() }
    } catch (_: Exception) { return "See end date" }
    val seconds = Duration.between(Instant.now(), end).seconds
    if (seconds <= 0) return "Ended or unavailable"
    val days = seconds / 86400; val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60; val secs = seconds % 60
    return if (days > 0) "${days}d ${hours}h ${minutes}m" else "%02dh %02dm %02ds".format(hours, minutes, secs)
}

private class ChasterViewModelFactory(private val store: TokenStore) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ChasterViewModel(ChasterRepository(store), store) as T
}
