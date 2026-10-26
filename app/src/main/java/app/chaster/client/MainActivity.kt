package app.chaster.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SessionMonitorScheduler.schedule(applicationContext)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
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
    fun addRandomTime(lockId: String, minSeconds: Long, maxSeconds: Long) {
        require(minSeconds > 0L && maxSeconds >= minSeconds) { "Invalid random duration range." }
        val seconds = kotlin.random.Random.nextLong(minSeconds, maxSeconds + 1L)
        lockAction("Adding a random duration (${formatSeconds(seconds)})…") { repository.addTime(lockId, seconds) }
    }
    fun increaseMaximum(lockId: String, seconds: Long) = lockAction("Increasing maximum duration…") { repository.increaseMaximum(lockId, seconds) }
    fun increaseMinimum(lockId: String, seconds: Long) = lockAction("Increasing minimum duration…") { repository.increaseMinimum(lockId, seconds) }
    fun increaseMinimumRandom(lockId: String, minSeconds: Long, maxSeconds: Long) {
        require(minSeconds > 0L && maxSeconds >= minSeconds) { "Invalid random duration range." }
        val seconds = kotlin.random.Random.nextLong(minSeconds, maxSeconds + 1L)
        lockAction("Increasing minimum duration by a random ${formatSeconds(seconds)}…") { repository.increaseMinimum(lockId, seconds) }
    }
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
        val lock = vm.locks.firstOrNull()
        WearStatusSync.publish(
            context = context,
            name = lock?.name?.takeIf { it.isNotBlank() } ?: "Chaster",
            status = lock?.status ?: if (vm.connected) "No active lock" else "Phone disconnected",
            endMillis = WearStatusSync.parseEndMillis(lock?.endDate)
        )
    }

    LaunchedEffect(vm.connected) {
        if (vm.connected) {
            vm.refreshLocks()
            while (true) {
                delay(60_000L)
                if (vm.connected && !vm.busy) vm.refreshLocks()
            }
        }
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
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp),
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
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(lock.name, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        AssistChip(onClick = {}, label = { Text(lock.status) })
                    }
                    Text(countdown(lock.endDate), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    Text("Time remaining", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    DetailRow("Start date", lock.createdAt ?: "Not provided by API")
                    DetailRow("End date", lock.endDate ?: "Not provided by API")
                    DetailRow("Minimum duration", lock.minDuration?.let { formatSeconds(it) } ?: (lock.minDate ?: "Not provided by API"))
                    DetailRow("Maximum duration", lock.maxDuration?.let { formatSeconds(it) } ?: (lock.maxDate ?: "Not provided by API"))
                    DetailRow("Requirements", lock.requirements?.toString() ?: "Not provided by API")
                    Text("Quick actions", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.addTime(lock.id, 3600L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Add 1 hour") }
                        OutlinedButton(onClick = { vm.addTime(lock.id, 86400L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Add 1 day") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.addRandomTime(lock.id, 3600L, 86400L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Random 1–24h") }
                        OutlinedButton(onClick = { vm.increaseMaximum(lock.id, 86400L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Max +1 day") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.increaseMinimum(lock.id, 86400L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Min +1 day") }
                        OutlinedButton(onClick = { vm.increaseMinimumRandom(lock.id, 86400L, 604800L) }, modifier = Modifier.weight(1f), enabled = !vm.busy) { Text("Random min +1–7d") }
                    }
                }
            }
        }
    }
}

@Composable private fun DetailRow(label: String, value: String, compact: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.weight(1f), style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatSeconds(seconds: Long?): String {
    if (seconds == null || seconds < 0L) return "Not provided by API"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    return buildList {
        if (days > 0) add("${days}d")
        if (hours > 0) add("${hours}h")
        if (minutes > 0 || isEmpty()) add("${minutes}m")
    }.joinToString(" ")
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
            Card(Modifier.fillMaxWidth()) {
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Account profile", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Profile information returned by Chaster", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = vm::refreshProfile, enabled = !vm.busy) { Text("Refresh") }
        }
        StatusText(vm.message, vm.busy)
        val profile = vm.profile
        if (profile == null) {
            EmptyState("Profile not loaded", "Tap Refresh to fetch your Chaster account profile.")
        } else {
            val fields = profile.apiDetails
            ProfileInfoSection("Account", fields.filter { (key, _) ->
                val k = key.lowercase()
                listOf("username", "name", "email", "id", "role", "language", "timezone", "description", "created", "member").any { it in k }
            }, listOf("username", "name", "email", "id", "role", "language", "timezone", "description", "created", "member")) {
                DetailRow("Username", profile.username, compact = true)
                profile.email?.let { DetailRow("Email", it, compact = true) }
            }
            ProfileInfoSection("Sessions", fields.filter { (key, _) ->
                listOf("session", "lock", "duration", "lastactivity", "last_activity", "history").any { it in key.lowercase() }
            })
            ProfileInfoSection("Ratings", fields.filter { (key, _) ->
                listOf("rating", "subscription", "financial", "verified", "discord").any { it in key.lowercase() }
            })
            ProfileInfoSection("Visibility", fields.filter { (key, _) ->
                listOf("visible", "visibility", "privacy", "show", "public").any { it in key.lowercase() }
            })
            ProfileInfoSection("Social", fields.filter { (key, _) ->
                listOf("social", "twitter", "discord", "fetlife", "onlyfans", "website", "url").any { it in key.lowercase() }
            })
            val classified = fields.filter { (key, _) ->
                val k = key.lowercase()
                listOf("username", "name", "email", "id", "role", "language", "timezone", "description", "created", "member",
                    "session", "lock", "duration", "lastactivity", "last_activity", "history", "rating", "subscription",
                    "financial", "verified", "discord", "visible", "visibility", "privacy", "show", "public",
                    "social", "twitter", "fetlife", "onlyfans", "website", "url").any { it in k }
            }.map { it.first }.toSet()
            val otherFields = fields.filter { it.first !in classified }
            if (otherFields.isNotEmpty()) ProfileInfoSection("Other details", otherFields)
        }
    }
}

private fun simpleProfileLabel(key: String): String {
    val normalized = key.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ") { word ->
            when (word.lowercase()) {
                "id" -> "ID"
                "url" -> "Link"
                "api" -> "API"
                else -> word.replaceFirstChar { it.uppercase() }
            }
        }
    return when (normalized.lowercase()) {
        "created at", "created date" -> "Joined"
        "last activity", "last activity at" -> "Last active"
        "username" -> "Username"
        "description" -> "About"
        "timezone" -> "Time zone"
        "is verified", "verified" -> "Verified"
        "profile visibility" -> "Visibility"
        else -> normalized
    }
}

@Composable private fun ProfileInfoSection(
    title: String,
    fields: List<Pair<String, String>>,
    identityKeys: List<String> = emptyList(),
    identityContent: @Composable ColumnScope.() -> Unit = {}
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            identityContent()
            val identityNames = identityKeys.toSet()
            val remaining = fields.filterNot { (key, _) -> identityNames.any { it.equals(key, ignoreCase = true) } }
            if (remaining.isEmpty() && identityKeys.isEmpty()) {
                Text("This service does not provide these fields.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                remaining.forEach { (key, value) -> DetailRow(simpleProfileLabel(key), value.ifBlank { "Empty" }, compact = true) }
                if (remaining.isEmpty() && identityKeys.isNotEmpty() && fields.isEmpty()) {
                    Text("No additional fields returned.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
    Card(Modifier.fillMaxWidth()) {
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
