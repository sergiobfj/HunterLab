package dev.hunterlab.protocol

/** O que o Tripper deveria mostrar, em forma tipada. Converte para pacote **apenas em memória**. */
public sealed interface TripperScreen {
    /** Pacote correspondente; `null` quando a tela é obtida **não** enviando pacote de tela. */
    public fun toPacket(): TripperPacket?

    /** Sem pacote de tela após o handshake, o Pod mostra o próprio relógio. */
    public data object NativeClock : TripperScreen {
        override fun toPacket(): TripperPacket? = null
    }

    public data class Navigation(val frame: NavigationFrame) : TripperScreen {
        override fun toPacket(): TripperPacket = frame.toPacket()
    }

    public data class Compass(val direction: CompassDirection, val period: DayPeriod) : TripperScreen {
        override fun toPacket(): TripperPacket = TripperCommands.compass(direction, period)
    }

    public data class RouteStarted(val period: DayPeriod) : TripperScreen {
        override fun toPacket(): TripperPacket = TripperCommands.routeStarted(period)
    }

    public data class Recalculating(val period: DayPeriod) : TripperScreen {
        override fun toPacket(): TripperPacket = TripperCommands.recalculating(period)
    }
}
