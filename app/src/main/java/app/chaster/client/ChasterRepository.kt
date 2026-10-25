package app.chaster.client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChasterRepository(private val tokenStore: TokenStore) {
    private fun api(): ChasterApi {
        val token = tokenStore.read()
        require(token.isNotBlank()) { "Add your Chaster developer token in Settings first." }
        return ChasterApi(token)
    }
    suspend fun locks(): List<ChasterLock> = withContext(Dispatchers.IO) { api().getLocks() }
    suspend fun profile(): ChasterProfile = withContext(Dispatchers.IO) { api().getProfile() }
    suspend fun history(locks: List<ChasterLock>): List<HistoryEntry> = withContext(Dispatchers.IO) { api().getHistory(locks) }
}
