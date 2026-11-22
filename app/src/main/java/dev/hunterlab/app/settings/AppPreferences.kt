package dev.hunterlab.app.settings

import android.content.Context
import androidx.core.content.edit
import dev.hunterlab.app.ui.contract.DestinationUi
import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.DayNightMode

/**
 * Persistência mínima (MVP): preferências do usuário, a chave da API de rotas e destinos recentes.
 * Único arquivo do app autorizado a usar SharedPreferences (verificado pelo SafetyGuardTest).
 *
 * Nunca guarda: PIN, endereço do Tripper, reconhecimento de pareamento, histórico de posições.
 * `android:allowBackup="false"`: nada disto vai para backup na nuvem.
 */
class AppPreferences(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var routingApiKey: String?
        get() = sp.getString(KEY_API, null)?.takeIf { it.isNotBlank() }
        set(value) = sp.edit { if (value.isNullOrBlank()) remove(KEY_API) else putString(KEY_API, value.trim()) }

    var mapApiKey: String?
        get() = sp.getString("maptiler_api_key", null)?.takeIf { it.isNotBlank() }
        set(value) = sp.edit { if (value.isNullOrBlank()) remove("maptiler_api_key") else putString("maptiler_api_key", value.trim()) }

    var favorites: List<DestinationUi>
        get() = sp.getString("favorite_destinations_v1", null).orEmpty().lines().mapNotNull(::decode)
        set(value) = sp.edit { putString("favorite_destinations_v1", value.take(20).joinToString("\n", transform = ::encode)) }

    fun toggleFavorite(d: DestinationUi) {
        favorites = if (favorites.any { same(it, d) }) favorites.filterNot { same(it, d) } else listOf(d) + favorites
    }

    /** Registro estruturado do Playground: somente IDs de catálogo, contadores e opções de observação. */
    var playgroundHistory: String
        get() = sp.getString("playground_history_v1", "[]") ?: "[]"
        set(value) = sp.edit { putString("playground_history_v1", value) }

    /** Versão dos termos de uso do Tripper aceitos (checklist de 5 itens). */
    var termsAcceptedVersion: Int
        get() = sp.getInt(KEY_TERMS, 0)
        set(value) = sp.edit { putInt(KEY_TERMS, value) }

    var dayNight: DayNightMode
        get() = enumOr(sp.getString(KEY_DAY_NIGHT, null), DayNightMode.AUTO)
        set(value) = sp.edit { putString(KEY_DAY_NIGHT, value.name) }

    var bottomLine: BottomLineMode
        get() = enumOr(sp.getString(KEY_BOTTOM, null), BottomLineMode.TOTAL_DISTANCE)
        set(value) = sp.edit { putString(KEY_BOTTOM, value.name) }

    var radarEnabled: Boolean
        get() = sp.getBoolean(KEY_RADAR, true)
        set(value) = sp.edit { putBoolean(KEY_RADAR, value) }

    var radarVoiceEnabled: Boolean
        get() = sp.getBoolean("radar_voice_enabled", false)
        set(value) = sp.edit { putBoolean("radar_voice_enabled", value) }

    var radarOnTripper: Boolean
        get() = sp.getBoolean(KEY_RADAR_TRIPPER, true)
        set(value) = sp.edit { putBoolean(KEY_RADAR_TRIPPER, value) }

    var radarSearchM: Int
        get() = sp.getInt(KEY_RADAR_SEARCH, 700)
        set(value) = sp.edit { putInt(KEY_RADAR_SEARCH, value) }

    var radarAlertM: Int
        get() = sp.getInt(KEY_RADAR_ALERT, 300)
        set(value) = sp.edit { putInt(KEY_RADAR_ALERT, value) }

    /** Até [MAX_RECENT] destinos, mais recente primeiro. */
    var recent: List<DestinationUi>
        get() = sp.getString(KEY_RECENT, null).orEmpty().lines().mapNotNull(::decode)
        set(value) = sp.edit { putString(KEY_RECENT, value.take(MAX_RECENT).joinToString("\n", transform = ::encode)) }

    fun addRecent(d: DestinationUi) {
        recent = listOf(d) + recent.filterNot { same(it, d) }
    }

    private fun same(a: DestinationUi, b: DestinationUi) =
        kotlin.math.abs(a.lat - b.lat) < 1e-5 && kotlin.math.abs(a.lon - b.lon) < 1e-5

    private fun encode(d: DestinationUi) = DestinationBookmarkCodec.encode(d)
    private fun decode(line: String): DestinationUi? = DestinationBookmarkCodec.decode(line)

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    companion object {
        const val TERMS_VERSION = 1
        const val MAX_RECENT = 5
        private const val FILE = "hunterlab"
        private const val KEY_API = "routing_api_key"
        private const val KEY_TERMS = "terms_version"
        private const val KEY_DAY_NIGHT = "day_night"
        private const val KEY_BOTTOM = "bottom_line"
        private const val KEY_RADAR = "radar_enabled"
        private const val KEY_RADAR_TRIPPER = "radar_on_tripper"
        private const val KEY_RADAR_SEARCH = "radar_search_m"
        private const val KEY_RADAR_ALERT = "radar_alert_m"
        private const val KEY_RECENT = "recent_destinations"

        /** "••••••3f2a" — nunca a chave inteira na interface. */
        fun mask(key: String?): String? = key?.takeIf { it.length >= 4 }?.let { "••••••" + it.takeLast(4) }
    }
}
