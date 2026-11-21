package dev.hunterlab.protocol

/**
 * Builders dos comandos **documentados** (PROTOCOL.md §3–§4). Todos produzem pacotes em memória.
 *
 * Nenhum comando desconhecido existe aqui por design: não há builder genérico de "byte livre".
 * A transmissão está fora do escopo da versão 0.1.0.
 */
public object TripperCommands {
    public const val CMD_PING: Int = 0x03
    public const val CMD_NAVIGATE: Int = 0x10
    public const val CMD_NAVIGATE_SUB: Int = 0x11
    public const val CMD_PIN: Int = 0x20
    public const val CMD_HANDSHAKE: Int = 0x21
    public const val CMD_KEEPALIVE: Int = 0x40
    public const val CMD_SET_TIME: Int = 0x50

    /** Byte `[2]` que seleciona a tela de bússola. */
    public const val SCREEN_COMPASS: Int = 0x41

    /** Subcomando de `0x40` que desenha o ícone de ligação. */
    public const val KEEPALIVE_SUB_CALL: Int = 0x05

    public const val PIN_MAX_LENGTH: Int = 6

    /** Handshake de dispositivo novo: o Pod mostra um PIN. */
    public fun handshakeShowPin(): TripperPacket = simple(CMD_HANDSHAKE, 0x01)

    /** Handshake de dispositivo já pareado. */
    public fun handshakeResume(): TripperPacket = simple(CMD_HANDSHAKE, 0x00)

    /** Keepalive de firmware. */
    public fun pingFirmware(): TripperPacket = simple(CMD_PING)

    /**
     * PIN mostrado pelo Pod: `[0]=0x20`, `[1..6]` = ASCII.
     * Mais estrito que a referência (que trunca): rejeita PIN vazio, longo ou com caractere não alfanumérico ASCII.
     */
    public fun pin(code: String): TripperPacket {
        require(code.length in 1..PIN_MAX_LENGTH) { "PIN deve ter de 1 a $PIN_MAX_LENGTH caracteres (recebido ${code.length})" }
        require(code.all { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }) { "PIN deve conter apenas letras/dígitos ASCII" }
        val p = ByteArray(TripperPacket.PAYLOAD_SIZE)
        p[0] = CMD_PIN.toByte()
        code.forEachIndexed { i, ch -> p[1 + i] = ch.code.toByte() }
        return TripperPacket.fromPayload(p)
    }

    /**
     * Acerta o relógio do Pod: `[1]` = hora, `[2]` = minuto.
     * 24h: hora crua (0–23). 12h: hora 1–12 com `| 0x40` quando AM (nada em PM).
     */
    public fun setTime(hour: Int, minute: Int, format: ClockFormat): TripperPacket {
        require(hour in 0..23) { "Hora inválida: $hour" }
        require(minute in 0..59) { "Minuto inválido: $minute" }
        val hourByte = when (format) {
            ClockFormat.H24 -> hour
            ClockFormat.H12 -> {
                val h12 = (hour % 12).let { if (it == 0) 12 else it }
                h12 or (if (hour < 12) 0x40 else 0x00)
            }
        }
        val p = ByteArray(TripperPacket.PAYLOAD_SIZE)
        p[0] = CMD_SET_TIME.toByte()
        p[1] = hourByte.toByte()
        p[2] = minute.toByte()
        return TripperPacket.fromPayload(p)
    }

    /** Bússola sem rota: `[2]=0x41`, `[6]` noite, `[7]=[11]=[12]=[13]=FF`, `[14]` direção. */
    public fun compass(direction: CompassDirection, period: DayPeriod): TripperPacket {
        val p = navigateHeader()
        p[2] = SCREEN_COMPASS.toByte()
        p[6] = DisplayIntensity.nightFlag(period).toByte()
        p[7] = 0xFF.toByte()
        p[11] = 0xFF.toByte()
        p[12] = 0xFF.toByte()
        p[13] = 0xFF.toByte()
        p[14] = direction.code.toByte()
        return TripperPacket.fromPayload(p)
    }

    public fun navigation(frame: NavigationFrame): TripperPacket = frame.toPacket()

    /** "Rota iniciada": ícone grande `0x45`, distância 0 m, rodapé `FF FF FF` (~2 s no início da rota). */
    public fun routeStarted(period: DayPeriod = DayPeriod.DAY): TripperPacket = NavigationFrame(
        icon = TripperIcons.ROUTE_STARTED,
        distanceMeters = 0,
        byte6 = DisplayIntensity.nightFlag(period),
        nextIcon = TripperIcons.NONE,
        bottomLine = BottomLine.None,
    ).toPacket()

    /** "Recalculando": `[2]=0x1C`, resto zero, `[6]` = noite. */
    public fun recalculating(period: DayPeriod): TripperPacket {
        val p = navigateHeader()
        p[2] = TripperIcons.RECALCULATING.toByte()
        p[6] = DisplayIntensity.nightFlag(period).toByte()
        return TripperPacket.fromPayload(p)
    }

    /** Ícone de ligação: `40 05`, desenhado pelo próprio Pod como sobreposição. */
    public fun callIcon(): TripperPacket = simple(CMD_KEEPALIVE, KEEPALIVE_SUB_CALL)

    /**
     * Tela ociosa documentada (`[2]=0x3C`, `[6]=0x40` +1 à noite). A referência **não a usa**:
     * ela é derrubada pelo watchdog como qualquer tela.
     */
    public fun idleScreen(period: DayPeriod): TripperPacket {
        val p = Hex.decode("10 11 3C 00 00 04 40 15 00 00 41 00 04 03 00 00 00 00")
        p[6] = (0x40 + DisplayIntensity.nightFlag(period)).toByte()
        return TripperPacket.fromPayload(p)
    }

    private fun navigateHeader(): ByteArray = ByteArray(TripperPacket.PAYLOAD_SIZE).also {
        it[0] = CMD_NAVIGATE.toByte()
        it[1] = CMD_NAVIGATE_SUB.toByte()
    }

    private fun simple(command: Int, sub: Int = 0): TripperPacket {
        val p = ByteArray(TripperPacket.PAYLOAD_SIZE)
        p[0] = command.toByte()
        p[1] = sub.toByte()
        return TripperPacket.fromPayload(p)
    }
}

/**
 * Comando documentado, para catálogo/inspeção. [transmittedInThisVersion] refere-se ao catálogo/Playground,
 * que nunca transmite. A transmissão real (0.2.0) passa só pelo Laboratório BLE, com lista branca própria.
 */
public data class DocumentedCommand(
    val name: String,
    val description: String,
    val confidence: Confidence,
    val example: TripperPacket,
    val transmittedInThisVersion: Boolean = false,
)

public object CommandCatalog {
    public val all: List<DocumentedCommand> by lazy {
        listOf(
            DocumentedCommand("HANDSHAKE_SHOW_PIN", "Dispositivo novo: o Pod mostra um PIN.", Confidence.CONFIRMED, TripperCommands.handshakeShowPin()),
            DocumentedCommand("HANDSHAKE_RESUME", "Dispositivo já pareado.", Confidence.CONFIRMED, TripperCommands.handshakeResume()),
            DocumentedCommand("PING_FW", "Keepalive do firmware.", Confidence.CONFIRMED, TripperCommands.pingFirmware()),
            DocumentedCommand("PIN", "Confirma o PIN digitado (exemplo: 1234).", Confidence.LIKELY, TripperCommands.pin("1234")),
            DocumentedCommand("SET_TIME", "Acerta o relógio (exemplo: 13:55, 12h).", Confidence.CONFIRMED, TripperCommands.setTime(13, 55, ClockFormat.H12)),
            DocumentedCommand("COMPASS", "Bússola sem rota (exemplo: SE, noite).", Confidence.CONFIRMED, TripperCommands.compass(CompassDirection.SE, DayPeriod.NIGHT)),
            DocumentedCommand(
                "NAVIGATE", "Tela de navegação (exemplo: virar à esquerda, 167 m).", Confidence.CONFIRMED,
                NavigationFrame(0x14, 167, 0x01, bottomLine = BottomLine.TotalDistance(2542)).toPacket(),
            ),
            DocumentedCommand("ROUTE_STARTED", "Rota iniciada (ícone grande 0x45).", Confidence.LIKELY, TripperCommands.routeStarted()),
            DocumentedCommand("RECALCULATING", "Recalculando (tela 0x1C).", Confidence.CONFIRMED, TripperCommands.recalculating(DayPeriod.DAY)),
            DocumentedCommand("CALL_ICON", "Ícone de ligação (sobreposição do Pod).", Confidence.LIKELY, TripperCommands.callIcon()),
            DocumentedCommand("IDLE_SCREEN", "Tela ociosa (não usada pela referência).", Confidence.LIKELY, TripperCommands.idleScreen(DayPeriod.DAY)),
        )
    }
}
