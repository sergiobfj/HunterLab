package dev.hunterlab.core.link

import dev.hunterlab.core.ble.KnownUuids
import dev.hunterlab.protocol.Crc16CcittFalse
import dev.hunterlab.protocol.DecodedPacket
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.PacketDecoder
import dev.hunterlab.protocol.PacketParseResult
import dev.hunterlab.protocol.TripperPacket

enum class LogLevel(val tag: String) { INFO("INFO"), TX("TX"), RX("RX"), WARN("AVISO"), ERROR("ERRO") }

data class LinkLogEntry(val id: Long, val sessionId: Long, val sessionElapsedMs: Long?, val level: LogLevel, val text: String)

/**
 * Histórico técnico, só em memória (nunca gravado em arquivo pelo app). Regras de redação:
 * PIN nunca aparece; endereço é mascarado; bytes recebidos só com captura detalhada ligada.
 */
internal class LinkLog(private val capacity: Int = 400) {
    private val entries = ArrayDeque<LinkLogEntry>()
    private var nextId = 1L

    fun add(sessionId: Long, elapsedMs: Long?, level: LogLevel, text: String) {
        entries.addLast(LinkLogEntry(nextId++, sessionId, elapsedMs, level, text))
        while (entries.size > capacity) entries.removeFirst()
    }

    fun snapshot(): List<LinkLogEntry> = entries.toList()
}

object LinkFormat {
    /** `00:60:37:AB:CD:EF` → `00:60:37:XX:XX:XX` (mantém só o OUI). */
    fun maskAddress(address: String?): String? {
        if (address == null) return null
        val parts = address.split(":")
        return if (parts.size == 6) parts.take(3).joinToString(":") + ":XX:XX:XX" else "endereço oculto"
    }

    private val MAC = Regex("""(?i)\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\b""")

    /** Rede de segurança: mascara qualquer endereço MAC completo num texto livre. */
    fun maskMacs(text: String): String = MAC.replace(text) { maskAddress(it.value) ?: "endereço oculto" }

    fun seconds(ms: Long): String {
        val tenths = ms / 100
        return "${tenths / 10},${tenths % 10} s"
    }
}

/** Descrição segura de um pacote recebido pelo servidor local. Não interpreta significado. */
object InboundClassifier {
    const val MAX_INBOUND_BYTES = 512

    fun describe(value: ByteArray, includeBytes: Boolean): String = buildString {
        append("${value.size} bytes")
        if (value.size == TripperPacket.SIZE) {
            when (val r = TripperPacket.parse(value)) {
                is PacketParseResult.Valid -> {
                    append(" · CRC válido · estrutura ")
                    append(structureName(PacketDecoder.decode(r.packet)))
                }
                is PacketParseResult.Invalid -> {
                    val crc = Crc16CcittFalse.compute(value, 0, TripperPacket.PAYLOAD_SIZE)
                    append(" · CRC não confere (calculado ${Hex.byte(crc shr 8)}${Hex.byte(crc)})")
                }
            }
        } else {
            append(" · formato não documentado")
        }
        if (includeBytes) append(" · ${Hex.encode(value)}")
    }

    /** Só o nome da estrutura — nunca o conteúdo (um eco de PIN não pode vazar). */
    private fun structureName(d: DecodedPacket): String = when (d) {
        DecodedPacket.HandshakeShowPin -> "igual a HANDSHAKE_SHOW_PIN"
        DecodedPacket.HandshakeResume -> "igual a HANDSHAKE_RESUME"
        DecodedPacket.PingFirmware -> "igual a PING_FW"
        DecodedPacket.CallIcon -> "igual ao ícone de ligação"
        is DecodedPacket.Pin -> "de PIN (conteúdo oculto)"
        is DecodedPacket.SetTime -> "de SET_TIME"
        is DecodedPacket.Compass -> "de bússola"
        is DecodedPacket.Navigation -> "de navegação"
        is DecodedPacket.Screen -> "de tela 10 11"
        is DecodedPacket.Unknown -> "desconhecida (comando 0x${Hex.byte(d.command)})"
    }

    fun characteristicName(uuid: java.util.UUID) = KnownUuids.display(uuid)
}
