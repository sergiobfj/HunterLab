package dev.hunterlab.core.link

import dev.hunterlab.protocol.ClockFormat
import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import dev.hunterlab.protocol.DecodedPacket
import dev.hunterlab.protocol.NavigationFrame
import dev.hunterlab.protocol.PacketDecoder
import dev.hunterlab.protocol.TripperCommands
import dev.hunterlab.protocol.TripperIcons
import dev.hunterlab.protocol.TripperPacket
import java.time.LocalTime

/** Únicos tipos de comando que podem chegar ao rádio (telas de navegação desde a 0.3.0, só em sessões de navegação). */
enum class CommandKind(val label: String, val handshake: Boolean) {
    SHOW_PIN("HANDSHAKE_SHOW_PIN", true),
    RESUME("HANDSHAKE_RESUME", true),
    PIN("PIN", true),
    SET_TIME("SET_TIME", false),
    PING("PING_FW", false),
    COMPASS("Bússola", false),
    NAVIGATION("Tela de navegação", false),
    RECALCULATING("Tela recalculando", false),
}

enum class DedupePolicy {
    /** Rejeita se já houver um igual pendente ou em envio. */
    UNIQUE,

    /** Substitui o pendente anterior (o antigo ficou obsoleto). */
    REPLACE,

    /** Ignora o novo se já houver um igual pendente. */
    DROP_NEW,

    /** Sem deduplicação (ex.: os dois PING_FW da retomada). */
    NONE,
}

/**
 * Comando pronto para a fila. Construtor e fábricas são `internal`: fora do módulo `core` não há
 * como criar um comando — e não existe fábrica para bytes livres, navegação, OTA ou qualquer outro
 * pacote. Só o [TripperLink] produz comandos.
 */
class LinkCommand internal constructor(
    val kind: CommandKind,
    internal val packet: TripperPacket,
    /** Texto para o histórico. Para PIN não contém nem bytes nem CRC (o CRC revelaria o PIN). */
    val logLabel: String,
    val dedupeKey: String?,
    val policy: DedupePolicy,
) {
    override fun toString(): String = "LinkCommand(${kind.label})"

    internal companion object {
        fun showPin() = documented(CommandKind.SHOW_PIN, TripperCommands.handshakeShowPin(), DedupePolicy.UNIQUE)
        fun resume() = documented(CommandKind.RESUME, TripperCommands.handshakeResume(), DedupePolicy.UNIQUE)
        fun handshakePing() = documented(CommandKind.PING, TripperCommands.pingFirmware(), DedupePolicy.NONE, key = null)
        fun keepalivePing() = documented(CommandKind.PING, TripperCommands.pingFirmware(), DedupePolicy.DROP_NEW, key = "keepalive")

        fun pin(code: String) = LinkCommand(
            CommandKind.PIN, TripperCommands.pin(code), "PIN (conteúdo oculto)", CommandKind.PIN.name, DedupePolicy.UNIQUE,
        )

        fun setTime(time: LocalTime, format: ClockFormat) =
            documented(CommandKind.SET_TIME, TripperCommands.setTime(time.hour, time.minute, format), DedupePolicy.REPLACE)

        fun compass(direction: CompassDirection, period: DayPeriod) =
            documented(CommandKind.COMPASS, TripperCommands.compass(direction, period), DedupePolicy.REPLACE, key = "screen")

        fun navigation(frame: NavigationFrame) =
            documented(CommandKind.NAVIGATION, TripperCommands.navigation(frame), DedupePolicy.REPLACE, key = "screen")

        fun recalculating(period: DayPeriod) =
            documented(CommandKind.RECALCULATING, TripperCommands.recalculating(period), DedupePolicy.REPLACE, key = "screen")

        private fun documented(kind: CommandKind, packet: TripperPacket, policy: DedupePolicy, key: String? = kind.name) =
            LinkCommand(kind, packet, "${kind.label} · ${packet.toHex()}", key, policy)
    }
}

/**
 * Segunda barreira antes do rádio: o pacote decodificado precisa corresponder exatamente ao tipo
 * declarado e estar na lista de comandos documentados permitidos.
 */
object CommandWhitelist {
    val allowed: Set<CommandKind> = CommandKind.entries.toSet()

    fun matches(kind: CommandKind, packet: TripperPacket): Boolean {
        if (kind !in allowed) return false
        return when (val d = PacketDecoder.decode(packet)) {
            DecodedPacket.HandshakeShowPin -> kind == CommandKind.SHOW_PIN
            DecodedPacket.HandshakeResume -> kind == CommandKind.RESUME
            DecodedPacket.PingFirmware -> kind == CommandKind.PING
            is DecodedPacket.Pin -> kind == CommandKind.PIN && d.code.length in 1..TripperCommands.PIN_MAX_LENGTH
            is DecodedPacket.SetTime -> kind == CommandKind.SET_TIME
            is DecodedPacket.Compass -> kind == CommandKind.COMPASS && d.direction != null
            // Tela de navegação documentada; "rota iniciada" (0x45, só provável) continua fora.
            is DecodedPacket.Navigation -> kind == CommandKind.NAVIGATION && d.icon != TripperIcons.ROUTE_STARTED && d.distance != null
            is DecodedPacket.Screen -> kind == CommandKind.RECALCULATING && d.icon == TripperIcons.RECALCULATING
            else -> false
        }
    }

    /** Algum tipo permitido reconhece este pacote? (Rota iniciada, ligação, tela ociosa e bytes livres: não.) */
    fun isTransmittable(packet: TripperPacket): Boolean = CommandKind.entries.any { matches(it, packet) }

    internal fun matches(command: LinkCommand): Boolean = matches(command.kind, command.packet)
}
