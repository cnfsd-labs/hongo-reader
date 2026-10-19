package app.hongo.reader.data.sync

import android.content.SharedPreferences
import app.hongo.reader.data.opds.CredentialCipher
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The KOReader sync account and this phone's sync id, in the `device` preferences (never backed
 * up: the key is encrypted with this phone's Keystore key, and the id names this phone).
 */
class KoSyncSettings(
    private val preferences: SharedPreferences,
    private val cipher: CredentialCipher,
) {
    private val _account = MutableStateFlow(load())

    /** The signed-in account; null when sync is off. */
    val account: StateFlow<KoSyncAccount?> = _account.asStateFlow()

    /** This phone's id towards the sync server, made once (KOReader keeps one per device too). */
    val deviceId: String
        get() = preferences.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().replace("-", "").uppercase()
            .also { preferences.edit().putString(KEY_DEVICE_ID, it).apply() }

    fun signIn(account: KoSyncAccount) {
        preferences.edit()
            .putString(KEY_SERVER, account.server)
            .putString(KEY_USERNAME, account.username)
            .putString(KEY_USER_KEY, cipher.encrypt(account.userKey))
            .putString(KEY_PASSWORD, account.password?.let(cipher::encrypt))
            .apply()
        _account.value = account
    }

    fun signOut() {
        preferences.edit().remove(KEY_SERVER).remove(KEY_USERNAME).remove(KEY_USER_KEY).remove(KEY_PASSWORD).apply()
        _account.value = null
    }

    private fun load(): KoSyncAccount? {
        val server = preferences.getString(KEY_SERVER, null) ?: return null
        val username = preferences.getString(KEY_USERNAME, null) ?: return null
        val key = preferences.getString(KEY_USER_KEY, null)?.let(cipher::decrypt) ?: return null
        val password = preferences.getString(KEY_PASSWORD, null)?.let { cipher.decrypt(it) ?: return null }
        return KoSyncAccount(server, username, key, password)
    }

    private companion object {
        const val KEY_SERVER = "kosync_server"
        const val KEY_USERNAME = "kosync_username"
        const val KEY_USER_KEY = "kosync_user_key"

        /** Only for servers that want the password itself (Calibre-Web Automated). */
        const val KEY_PASSWORD = "kosync_password"
        const val KEY_DEVICE_ID = "kosync_device_id"
    }
}
