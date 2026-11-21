package dev.hunterlab.app.nav

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dev.hunterlab.app.ui.contract.DestinationUi
import dev.hunterlab.core.geo.GeoPoint
import java.util.Locale
import java.util.concurrent.Executor

/**
 * Busca de destino pelo [Geocoder] do Android (serviço do sistema; sem chave e sem custo para o app).
 * A disponibilidade depende do aparelho (em celulares com Google Play costuma existir). Sem ele, o usuário
 * usa coordenadas ou um link `geo:` ([dev.hunterlab.core.geo.CoordinateParser]).
 * A busca só acontece quando o usuário confirma o texto (sem autocompletar a cada letra).
 */
class DestinationSearch(context: Context, private val background: Executor) {
    private val appContext = context.applicationContext

    sealed interface Result {
        data class Found(val places: List<DestinationUi>) : Result
        data class Failed(val message: String) : Result
    }

    val available: Boolean get() = Geocoder.isPresent()

    fun search(query: String, near: GeoPoint?, callback: (Result) -> Unit) {
        val q = query.trim()
        if (q.length < 3) return callback(Result.Failed("Digite pelo menos 3 letras."))
        if (!available) return callback(Result.Failed("Busca por endereço indisponível neste aparelho. Use coordenadas."))
        val geocoder = Geocoder(appContext, Locale.forLanguageTag("pt-BR"))
        // Viés de ~150 km ao redor da posição atual, quando conhecida.
        val box = near?.let { doubleArrayOf(it.lat - 1.4, it.lon - 1.4, it.lat + 1.4, it.lon + 1.4).map { v -> v } }
        val deliver: (List<Address>?) -> Unit = { list ->
            val places = list.orEmpty().filter { it.hasLatitude() && it.hasLongitude() }.map { it.toDestination() }
            callback(if (places.isEmpty()) Result.Failed("Nada encontrado para \"$q\".") else Result.Found(places))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = deliver(addresses)
                override fun onError(errorMessage: String?) = callback(Result.Failed("Falha na busca. Verifique a internet."))
            }
            if (box != null) {
                geocoder.getFromLocationName(q, MAX_RESULTS, box[0], box[1], box[2], box[3], listener)
            } else {
                geocoder.getFromLocationName(q, MAX_RESULTS, listener)
            }
        } else {
            background.execute {
                val result = runCatching {
                    @Suppress("DEPRECATION")
                    if (box != null) geocoder.getFromLocationName(q, MAX_RESULTS, box[0], box[1], box[2], box[3])
                    else geocoder.getFromLocationName(q, MAX_RESULTS)
                }
                result.fold(onSuccess = deliver, onFailure = { callback(Result.Failed("Falha na busca. Verifique a internet.")) })
            }
        }
    }

    private fun Address.toDestination(): DestinationUi {
        val line = getAddressLine(0)
        val title = featureName?.takeIf { it.isNotBlank() && it != subThoroughfare } ?: thoroughfare ?: line ?: "Destino"
        return DestinationUi(label = title, lat = latitude, lon = longitude, detail = line?.takeIf { it != title })
    }

    private companion object {
        const val MAX_RESULTS = 5
    }
}
