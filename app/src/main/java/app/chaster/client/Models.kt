package app.chaster.client

data class ChasterLock(
    val id: String,
    val name: String,
    val status: String,
    val endDate: String?,
    val createdAt: String?,
    val minDuration: Long? = null,
    val maxDuration: Long? = null,
    val minDate: String? = null,
    val maxDate: String? = null,
    val requirements: Int? = null,
    val apiDetails: List<Pair<String, String>> = emptyList()
)
data class ChasterProfile(val username: String, val email: String?, val apiDetails: List<Pair<String, String>> = emptyList())
data class HistoryEntry(val lockId: String, val lockName: String, val title: String, val date: String?)
