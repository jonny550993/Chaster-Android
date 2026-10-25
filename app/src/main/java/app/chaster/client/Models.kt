package app.chaster.client
data class ChasterLock(val id: String, val name: String, val status: String, val endDate: String?, val createdAt: String?)
data class ChasterProfile(val username: String, val email: String?)
data class HistoryEntry(val lockId: String, val lockName: String, val title: String, val date: String?)
