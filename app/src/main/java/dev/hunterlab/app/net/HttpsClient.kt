package dev.hunterlab.app.net

import dev.hunterlab.core.route.HttpFailureKind
import dev.hunterlab.core.route.HttpGet
import dev.hunterlab.core.route.HttpResult
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Único ponto de rede do app (verificado pelo SafetyGuardTest).
 * Só HTTPS e só para [ALLOWED_HOSTS]: rotas (GraphHopper) e radares (Overpass/OpenStreetMap). Sem telemetria,
 * sem cookies, sem cabeçalhos de identificação além de um User-Agent genérico do app (exigido pelo Overpass).
 * Nunca registra a URL (ela pode conter a chave da API).
 */
class HttpsClient(appVersion: String) : HttpGet {
    private val userAgent = "HunterLab/$appVersion (Android; navegação pessoal para Tripper)"

    override fun get(url: String, timeoutMs: Int): HttpResult {
        if (Thread.currentThread().isInterrupted) return HttpResult.Failed(HttpFailureKind.NETWORK, "Consulta cancelada.")
        val parsed = try {
            URL(url)
        } catch (_: Exception) {
            return HttpResult.Failed(HttpFailureKind.NETWORK, "Endereço inválido.")
        }
        if (parsed.protocol != "https" || parsed.host !in ALLOWED_HOSTS) {
            return HttpResult.Failed(HttpFailureKind.NETWORK, "Servidor não permitido pelo app.")
        }
        var connection: HttpsURLConnection? = null
        return try {
            connection = (parsed.openConnection() as HttpsURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            val stream = if (code >= 400) connection.errorStream else connection.inputStream
            val body = stream?.use { readLimited(it) } ?: ""
            HttpResult.Ok(code, body)
        } catch (_: SocketTimeoutException) {
            HttpResult.Failed(HttpFailureKind.TIMEOUT, "Tempo esgotado.")
        } catch (e: IOException) {
            HttpResult.Failed(HttpFailureKind.NETWORK, "Sem conexão (${e.javaClass.simpleName}).")
        } catch (e: SecurityException) {
            HttpResult.Failed(HttpFailureKind.NETWORK, "Sem permissão de rede.")
        } finally {
            connection?.disconnect()
        }
    }

    /** Lê no máximo [MAX_BODY_BYTES] (readNBytes só existe no Android 13+). */
    private fun readLimited(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            if (Thread.currentThread().isInterrupted) throw IOException("Consulta cancelada")
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_BODY_BYTES) throw IOException("Resposta grande demais")
        }
        return out.toString("UTF-8")
    }

    companion object {
        /** Hosts permitidos. Qualquer outro é recusado antes de abrir conexão. */
        val ALLOWED_HOSTS: Set<String> = setOf("graphhopper.com", "overpass-api.de", "api.maptiler.com")
        private const val MAX_BODY_BYTES = 8 * 1024 * 1024
    }
}
