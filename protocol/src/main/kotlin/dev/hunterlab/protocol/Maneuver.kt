package dev.hunterlab.protocol

public enum class TurnSharpness(public val label: String) {
    SLIGHT("leve"),
    NORMAL("normal"),
    SHARP("acentuada"),
}

/** Sentido de circulação. O Brasil é mão direita ([RIGHT_HAND]). */
public enum class TrafficSide { RIGHT_HAND, LEFT_HAND }

/**
 * Manobra independente de provedor (Waze, Google Maps, simulador...).
 * Quem fornece navegação traduz para este modelo; só o [ManeuverMapper] conhece códigos do Tripper.
 */
public sealed interface Maneuver {
    public val label: String

    public data object Straight : Maneuver {
        override val label: String get() = "Seguir em frente"
    }

    public data class Turn(val side: Side, val sharpness: TurnSharpness = TurnSharpness.NORMAL) : Maneuver {
        init {
            require(side != Side.NONE) { "Curva precisa de lado (esquerda/direita)" }
        }

        override val label: String
            get() {
                val dir = if (side == Side.LEFT) "esquerda" else "direita"
                return when (sharpness) {
                    TurnSharpness.NORMAL -> "Virar à $dir"
                    TurnSharpness.SLIGHT -> "Curva leve à $dir"
                    TurnSharpness.SHARP -> "Curva acentuada à $dir"
                }
            }
    }

    /** Manter-se à esquerda/direita numa bifurcação suave. */
    public data class Keep(val side: Side) : Maneuver {
        init {
            require(side != Side.NONE) { "Manter precisa de lado" }
        }

        override val label: String get() = if (side == Side.LEFT) "Manter à esquerda" else "Manter à direita"
    }

    /** Saída de via expressa/rodovia. */
    public data class Exit(val side: Side) : Maneuver {
        init {
            require(side != Side.NONE) { "Saída precisa de lado" }
        }

        override val label: String get() = if (side == Side.LEFT) "Saída à esquerda" else "Saída à direita"
    }

    /** Retorno. No Brasil (mão direita) é desenhado no sentido anti-horário. */
    public data object UTurn : Maneuver {
        override val label: String get() = "Fazer retorno"
    }

    public data class Roundabout(val exit: Int) : Maneuver {
        init {
            require(exit >= 1) { "Saída de rotatória deve ser ≥ 1 (recebido $exit)" }
        }

        override val label: String get() = "Rotatória, ${exit}ª saída"
    }

    public data class Arrive(val side: Side = Side.NONE) : Maneuver {
        override val label: String
            get() = when (side) {
                Side.NONE -> "Chegada ao destino"
                Side.LEFT -> "Destino à esquerda"
                Side.RIGHT -> "Destino à direita"
            }
    }

    public data class Merge(val side: Side = Side.NONE) : Maneuver {
        override val label: String
            get() = when (side) {
                Side.NONE -> "Convergir"
                Side.LEFT -> "Convergir à esquerda"
                Side.RIGHT -> "Convergir à direita"
            }
    }

    public data class Fork(val side: Side) : Maneuver {
        init {
            require(side != Side.NONE) { "Bifurcação precisa de lado" }
        }

        override val label: String get() = if (side == Side.LEFT) "Bifurcação à esquerda" else "Bifurcação à direita"
    }

    public data object Depart : Maneuver {
        override val label: String get() = "Partida"
    }
}

/**
 * Ícones de uma manobra: [large] vai no byte `[2]` (manobra atual) e [small] no byte `[7]`
 * (próxima manobra). [confidence] é a confiança da **tradução**, não só do ícone.
 */
public data class ManeuverIcons(val large: Int, val small: Int, val confidence: Confidence)

/** Tradução manobra → códigos do Tripper (PROTOCOL.md §7, adaptado a um modelo neutro). */
public object ManeuverMapper {
    public const val MAX_ROUNDABOUT_EXIT: Int = 9

    public fun map(maneuver: Maneuver, traffic: TrafficSide = TrafficSide.RIGHT_HAND): ManeuverIcons {
        val c = Confidence.CONFIRMED
        val l = Confidence.LIKELY
        val u = Confidence.UNVERIFIED
        return when (maneuver) {
            Maneuver.Straight -> same(TripperIcons.STRAIGHT, c)
            is Maneuver.Turn -> {
                val left = maneuver.side == Side.LEFT
                val code = when (maneuver.sharpness) {
                    TurnSharpness.NORMAL -> if (left) 0x14 else 0x15
                    TurnSharpness.SHARP -> if (left) 0x16 else 0x17
                    TurnSharpness.SLIGHT -> if (left) 0x18 else 0x19
                }
                same(code, l)
            }
            // Manter/sair: ícone grande específico, seta pequena genérica de faixa (2B/2C).
            is Maneuver.Keep ->
                if (maneuver.side == Side.LEFT) ManeuverIcons(0x28, 0x2B, c) else ManeuverIcons(0x27, 0x2C, c)
            is Maneuver.Exit ->
                if (maneuver.side == Side.LEFT) ManeuverIcons(0x2E, 0x2B, c) else ManeuverIcons(0x2D, 0x2C, c)
            Maneuver.UTurn ->
                if (traffic == TrafficSide.RIGHT_HAND) same(0x3D, l) else same(0x1A, u)
            is Maneuver.Roundabout -> {
                val exit = maneuver.exit.coerceIn(1, MAX_ROUNDABOUT_EXIT)
                if (traffic == TrafficSide.RIGHT_HAND) {
                    same(TripperIcons.ROUNDABOUT_CCW_EXIT_1 + exit - 1, l)
                } else {
                    same(TripperIcons.ROUNDABOUT_CW_EXIT_1 + exit - 1, u)
                }
            }
            is Maneuver.Arrive -> when (maneuver.side) {
                // Destino genérico (0x00) aparece em captura real da referência.
                Side.NONE -> same(TripperIcons.DESTINATION, c)
                Side.LEFT -> same(0x01, l)
                Side.RIGHT -> same(0x02, l)
            }
            is Maneuver.Merge -> when (maneuver.side) {
                Side.NONE -> same(0x1B, l)
                Side.LEFT -> same(0x04, l)
                Side.RIGHT -> same(0x03, l)
            }
            is Maneuver.Fork -> same(if (maneuver.side == Side.LEFT) 0x06 else 0x05, l)
            Maneuver.Depart -> same(0x3C, l)
        }
    }

    private fun same(code: Int, confidence: Confidence) = ManeuverIcons(code, code, confidence)
}
