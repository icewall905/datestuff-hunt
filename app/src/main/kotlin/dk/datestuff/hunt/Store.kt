package dk.datestuff.hunt

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

private val Context.ds by preferencesDataStore("field_agent")

object Store {
    private val URL = stringPreferencesKey("url")
    private val TOKEN = stringPreferencesKey("token")
    private val BRIEFED = stringPreferencesKey("briefed")

    fun pair(c: Context): Flow<PairInfo?> = c.ds.data.map { p ->
        val u = p[URL]; val t = p[TOKEN]
        if (u.isNullOrBlank() || t.isNullOrBlank()) null else PairInfo(u, t)
    }

    suspend fun savePair(c: Context, info: PairInfo) = c.ds.edit { it[URL] = info.url.trim(); it[TOKEN] = info.token.trim() }
    suspend fun clearPair(c: Context) = c.ds.edit { it.remove(URL); it.remove(TOKEN) }

    fun briefed(c: Context): Flow<String?> = c.ds.data.map { it[BRIEFED] }
    suspend fun setBriefed(c: Context, sessionId: String) = c.ds.edit { it[BRIEFED] = sessionId }

    /** Atomic-ish file writes for cache + queue. */
    fun writeText(f: File, text: String) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text); tmp.renameTo(f)
    }
}
