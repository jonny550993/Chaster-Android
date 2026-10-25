package app.chaster.client
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the developer token encrypted at rest. Never log or commit the token. */
class TokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context, "chaster_secure_preferences",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun read(): String = preferences.getString(KEY_TOKEN, "").orEmpty()
    fun write(token: String) { preferences.edit().putString(KEY_TOKEN, token.trim()).apply() }
    fun clear() { preferences.edit().remove(KEY_TOKEN).apply() }
    private companion object { const val KEY_TOKEN = "developer_token" }
}
