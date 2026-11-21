package dev.hunterlab.app.nav

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.location.LocationRequest
import android.os.SystemClock
import dev.hunterlab.core.geo.GeoPoint
import dev.hunterlab.core.navigation.LocationFix
import java.util.concurrent.Executor

/**
 * GPS do aparelho via LocationManager (sem Google Play Services). Só enquanto a navegação está ativa,
 * dentro do serviço em primeiro plano do tipo `location` — sem localização em segundo plano.
 *
 * `timeMs` dos fixes usa o relógio monotônico (elapsedRealtime), o mesmo que o controlador usa como "agora".
 */
class AndroidLocationSource(context: Context, private val executor: Executor) {
    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    enum class Availability { NO_PERMISSION, DISABLED, ACTIVE }

    fun hasPermission(): Boolean =
        appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun gpsEnabled(): Boolean = lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true

    /** Inicia atualizações de ~1 s. Devolve o estado inicial. */
    @SuppressLint("MissingPermission") // verificada em hasPermission()
    fun start(onFix: (LocationFix) -> Unit, onProviderChange: (enabled: Boolean) -> Unit): Availability {
        stop()
        val manager = lm ?: return Availability.DISABLED
        if (!hasPermission()) return Availability.NO_PERMISSION
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location.toFix())
            override fun onProviderEnabled(provider: String) = onProviderChange(true)
            override fun onProviderDisabled(provider: String) = onProviderChange(false)
        }
        listener = l
        val request = LocationRequest.Builder(UPDATE_MS)
            .setQuality(LocationRequest.QUALITY_HIGH_ACCURACY)
            .setMinUpdateIntervalMillis(UPDATE_MS / 2)
            .build()
        return try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, request, executor, l)
            // Posição recente conhecida ajuda a pedir a rota antes do primeiro fix do GPS.
            lastKnownFresh()?.let(onFix)
            if (gpsEnabled()) Availability.ACTIVE else Availability.DISABLED
        } catch (_: SecurityException) {
            listener = null
            Availability.NO_PERMISSION
        } catch (_: IllegalArgumentException) {
            listener = null
            Availability.DISABLED
        }
    }

    fun stop() {
        val l = listener ?: return
        listener = null
        try {
            lm?.removeUpdates(l)
        } catch (_: SecurityException) {
            // Permissão revogada: o sistema já parou as atualizações.
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFresh(): LocationFix? {
        if (!hasPermission()) return null
        val candidates = listOf(LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val best = candidates.mapNotNull { p -> runCatching { lm?.getLastKnownLocation(p) }.getOrNull() }
            .filter { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < MAX_LAST_KNOWN_AGE_NS }
            .minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE }
        return best?.toFix()
    }

    private fun Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyM = if (hasAccuracy()) accuracy else null,
        bearingDeg = if (hasBearing()) bearing else null,
        speedMps = if (hasSpeed()) speed else null,
        timeMs = elapsedRealtimeNanos / 1_000_000,
    )

    private companion object {
        const val UPDATE_MS = 1_000L
        const val MAX_LAST_KNOWN_AGE_NS = 120_000_000_000L // 2 min
    }
}
