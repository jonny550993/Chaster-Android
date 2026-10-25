package app.chaster.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = TokenStore(applicationContext)
        setContent { ChasterTheme { ChasterApp(store) } }
    }
}

@Composable private fun ChasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFFB7C5FF),
        secondary = androidx.compose.ui.graphics.Color(0xFFB8D8CB),
        background = androidx.compose.ui.graphics.Color(0xFF101318),
        surface = androidx.compose.ui.graphics.Color(0xFF191E25)
    ), content = content)
}

private enum class Page(val label: String) { Locks("Locks"), History("History"), Profile("Profile"), Settings("Settings") }

class ChasterViewModel(private val repository: ChasterRepository, private val store: TokenStore) : ViewModel() {
    var page by mutableStateOf(Page.Locks); private set
    var tokenInput by mutableStateOf(store.read())
    var locks by mutableStateOf<List<ChasterLock>>(emptyList()); private set
    var history by mutableStateOf<List<HistoryEntry>>(emptyList()); private set
    var profile by mutableStateOf<ChasterProfile?>(null); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf("Add a developer token in Settings to connect."); private set
    var connected by mutableStateOf(store.read().isNotBlank()); private set
    fun navigate(value: Page) { page = value }
    fun saveToken() {
        store.write(tokenInput); connected = tokenInput.isNotBlank()
        message = if (connected) "Token saved securely on this device." else "Token cleared."
        if (connected) refreshLocks()
    }
    fun clearToken() {
        store.clear(); tokenInput = ""; connected = false
        locks = emptyList(); history = emptyList(); profile = null
        message = "Developer token cleared from this device."
    }
    fun refreshLocks() = task("Loading locks…") {
        locks = repository.locks(); message = "Loaded ${locks.size} lock(s)."
    }
    fun refreshProfile() = task("Loading profile…") { profile = repository.profile(); message = "Profile refreshed." }
    fun refreshHistory() = task("Loading lock history…") {
        if (locks.isEmpty()) locks = repository.locks()
        history = repository.history(locks); message = "Loaded ${history.size} history event(s)."
    }
    private fun task(start: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true; message = start
            try { action() } catch (e: Exception) { message = e.message ?: "Request failed. Check API token and permissions." }
            finally { busy = false }
        }
    }
}

@Composable fun ChasterApp(store: TokenStore) {
    val vm: ChasterViewModel = viewModel(factory = ChasterViewModelFactory(store))
    Scaffold(bottomBar = {
        NavigationBar {
            Page.entries.forEach { page ->
                val icon = when (page) {
                    Page.Locks -> Icons.Default.Lock; Page.History -> Icons.Default.History
                    Page.Profile -> Icons.Default.Person; Page.Settings -> Icons.Default.Settings
                }
                NavigationBarItem(selected = vm.page == page, onClick = { vm.navigate(page) },
                    icon = { Icon(icon, contentDescription = page.label) }, label = { Text(page.label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("CHASTER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(when (vm.page) {
                Page.Locks -> "Your locks"; Page.History -> "Lock history"
                Page.Profile -> "Your profile"; Page.Settings -> "Settings"
            }, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            when (vm.page) {
                Page.Locks -> LocksScreen(vm); Page.History -> HistoryScreen(vm)
                Page.Profile -> ProfileScreen(vm); Page.Settings -> SettingsScreen(vm)
            }
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
        OutlinedButton(onClick = vm::refreshLocks, enabled = !vm.busy && vm.connected) {
            Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Refresh")
        }
    }
    StatusText(vm.message, vm.busy)
    if (vm.locks.isEmpty()) EmptyState("No locks loaded", "Add your developer token in Settings, then refresh your locks.")
    else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(vm.locks, key = { it.id }) { lock ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lock.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(lock.status) })
                    }
                    Text("Remaining time: ${countdown(lock.endDate)}", style = MaterialTheme.typography.bodyLarge)
                    lock.endDate?.let { Text("End: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("ID: ${lock.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun HistoryScreen(vm: ChasterViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Events from your locks", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = vm::refreshHistory, enabled = !vm.busy && vm.connected) { Text("Load history") }
    }
    StatusText(vm.message, vm.busy)
    if (vm.history.isEmpty()) EmptyState("No history loaded", "Tap Load history to fetch history entries for your locks.")
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
        Button(onClick = vm::refreshProfile, enabled = !vm.busy && vm.connected) { Text("Refresh") }
    }
    StatusText(vm.message, vm.busy)
    val profile = vm.profile
    if (profile == null) EmptyState("Profile not loaded", "Tap Refresh to fetch your Chaster account profile.")
    else ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(profile.username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            profile.email?.let { Text(it) }
            Text("Connected with developer token", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun SettingsScreen(vm: ChasterViewModel) {
    Text("API connection", style = MaterialTheme.typography.titleMedium)
    Text("Personal use only. Your developer token is encrypted on this device. Never share an APK with your token embedded.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(value = vm.tokenInput, onValueChange = { vm.tokenInput = it }, modifier = Modifier.fillMaxWidth(),
        label = { Text("Developer token") }, placeholder = { Text("Paste Chaster token") }, singleLine = true)
    Button(onClick = vm::saveToken, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Save token & connect") }
    OutlinedButton(onClick = vm::clearToken, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) { Text("Clear token") }
    StatusText(vm.message, vm.busy)
    Text("Required scope: locks. Profile may require profile scope. Requests use HTTPS; error messages do not print response bodies.",
        style = MaterialTheme.typography.bodySmall)
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
