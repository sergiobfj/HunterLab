package dev.hunterlab.protocol

public enum class Side { LEFT, RIGHT, NONE }

/**
 * O que um código de ícone representa, em termos semânticos. A UI usa isto para desenhar uma
 * **aproximação** — não há garantia de que os pixels do display real sejam iguais.
 */
public sealed interface IconDepiction {
    public data object Straight : IconDepiction

    /** [angle]: 45 = leve, 90 = normal, 135 = acentuada. */
    public data class Turn(val side: Side, val angle: Int) : IconDepiction
    public data class UTurn(val clockwise: Boolean) : IconDepiction

    /** [exit] nulo = ícone base da rotatória. */
    public data class Roundabout(val clockwise: Boolean, val exit: Int?) : IconDepiction
    public data class Destination(val side: Side) : IconDepiction
    public data class Merge(val side: Side) : IconDepiction
    public data class Fork(val side: Side) : IconDepiction
    public data class OnRamp(val side: Side, val angle: Int) : IconDepiction
    public data class OffRamp(val side: Side, val angle: Int) : IconDepiction
    public data class SoftExit(val side: Side) : IconDepiction
    public data class KeepLane(val side: Side) : IconDepiction
    public data class Ferry(val train: Boolean) : IconDepiction
    public data object Recalculating : IconDepiction
    public data object Depart : IconDepiction
    public data object RouteStarted : IconDepiction
    public data object NoMobileData : IconDepiction
    public data object LowBattery : IconDepiction
    public data object Blank : IconDepiction
    public data object Unknown : IconDepiction
}

public data class TripperIcon(
    val code: Int,
    val name: String,
    val confidence: Confidence,
    val depiction: IconDepiction,
    val note: String? = null,
    val hunter: HunterValidation = HunterValidation.NOT_TESTED,
) {
    public val hex: String get() = "0x${Hex.byte(code)}"
}

/**
 * Catálogo dos códigos de ícone (bytes `[2]` e `[7]`) conhecidos pela referência
 * (PROTOCOL.md §6), mais faixas não testadas, explicitamente marcadas.
 */
public object TripperIcons {
    /** Byte `[7]` sem próxima manobra. */
    public const val NONE: Int = 0xFF

    /** Ícone usado por outros apps na seta pequena `[7]` para indicar radar ("depart"). */
    public const val RADAR_SMALL: Int = 0x3C

    public const val STRAIGHT: Int = 0x09
    public const val RECALCULATING: Int = 0x1C
    public const val ROUTE_STARTED: Int = 0x45
    public const val DESTINATION: Int = 0x00

    /** Rotatória mão direita (Brasil): `0x32 + (saída − 1)`, saídas 1–9. */
    public const val ROUNDABOUT_CCW_EXIT_1: Int = 0x32

    /** Rotatória mão inglesa: `0x0B + (saída − 1)` — faixa não testada. */
    public const val ROUNDABOUT_CW_EXIT_1: Int = 0x0B

    private val C = Confidence.CONFIRMED
    private val L = Confidence.LIKELY
    private val U = Confidence.UNVERIFIED

    public val all: List<TripperIcon> = buildList {
        add(TripperIcon(0x00, "Destino", C, IconDepiction.Destination(Side.NONE)))
        add(TripperIcon(0x01, "Destino à esquerda", C, IconDepiction.Destination(Side.LEFT)))
        add(TripperIcon(0x02, "Destino à direita", C, IconDepiction.Destination(Side.RIGHT)))
        add(TripperIcon(0x03, "Convergir à direita", C, IconDepiction.Merge(Side.RIGHT)))
        add(TripperIcon(0x04, "Convergir à esquerda", C, IconDepiction.Merge(Side.LEFT)))
        add(TripperIcon(0x05, "Bifurcação à direita", C, IconDepiction.Fork(Side.RIGHT)))
        add(TripperIcon(0x06, "Bifurcação à esquerda", C, IconDepiction.Fork(Side.LEFT)))
        add(TripperIcon(0x07, "Saída de via à direita", C, IconDepiction.OffRamp(Side.RIGHT, 45)))
        add(TripperIcon(0x08, "Saída de via à esquerda", C, IconDepiction.OffRamp(Side.LEFT, 45)))
        add(TripperIcon(0x09, "Seguir em frente", C, IconDepiction.Straight, "Verificado no Pod pela referência."))
        add(TripperIcon(0x0A, "Rotatória horária (base)", C, IconDepiction.Roundabout(clockwise = true, exit = null)))
        for (exit in 1..9) {
            add(
                TripperIcon(
                    0x0B + exit - 1, "Rotatória horária, saída $exit", U,
                    IconDepiction.Roundabout(clockwise = true, exit = exit),
                    "Faixa 0B–13 não testada; fórmula para mão inglesa, sem uso no Brasil.",
                ),
            )
        }
        add(TripperIcon(0x14, "Virar à esquerda", C, IconDepiction.Turn(Side.LEFT, 90)))
        add(TripperIcon(0x15, "Virar à direita", C, IconDepiction.Turn(Side.RIGHT, 90)))
        add(TripperIcon(0x16, "Curva acentuada à esquerda", C, IconDepiction.Turn(Side.LEFT, 135)))
        add(TripperIcon(0x17, "Curva acentuada à direita", C, IconDepiction.Turn(Side.RIGHT, 135)))
        add(TripperIcon(0x18, "Curva leve à esquerda", C, IconDepiction.Turn(Side.LEFT, 45)))
        add(TripperIcon(0x19, "Curva leve à direita", C, IconDepiction.Turn(Side.RIGHT, 45)))
        add(TripperIcon(0x1A, "Retorno (horário)", C, IconDepiction.UTurn(clockwise = true)))
        add(TripperIcon(0x1B, "Convergir", C, IconDepiction.Merge(Side.NONE)))
        add(
            TripperIcon(
                0x1C, "Recalculando rota", C, IconDepiction.Recalculating,
                "Fontes divergem; a referência observou o ícone de recálculo no Pod.",
            ),
        )
        add(TripperIcon(0x1D, "Acesso à via pela direita", C, IconDepiction.OnRamp(Side.RIGHT, 90)))
        add(
            TripperIcon(
                0x1E, "Acesso à via pela esquerda", L, IconDepiction.OnRamp(Side.LEFT, 90),
                "Fontes divergem (\"faixa fechada\"); não usado pela referência.",
            ),
        )
        add(
            TripperIcon(
                0x1F, "Acesso leve pela direita", L, IconDepiction.OnRamp(Side.RIGHT, 45),
                "Fontes divergem (\"manter\"); não usado pela referência.",
            ),
        )
        add(TripperIcon(0x20, "Acesso leve pela esquerda", C, IconDepiction.OnRamp(Side.LEFT, 45)))
        add(TripperIcon(0x21, "Acesso acentuado pela direita", C, IconDepiction.OnRamp(Side.RIGHT, 135)))
        add(TripperIcon(0x22, "Acesso acentuado pela esquerda", C, IconDepiction.OnRamp(Side.LEFT, 135)))
        for (code in listOf(0x23, 0x24, 0x25, 0x26)) add(unknown(code))
        add(TripperIcon(0x27, "Sair/manter à direita (suave)", C, IconDepiction.SoftExit(Side.RIGHT), "Verificado no Pod."))
        add(TripperIcon(0x28, "Sair/manter à esquerda (suave)", C, IconDepiction.SoftExit(Side.LEFT), "Verificado no Pod."))
        for (code in listOf(0x29, 0x2A)) add(unknown(code))
        add(TripperIcon(0x2B, "Manter faixa, destaque à esquerda", C, IconDepiction.KeepLane(Side.LEFT)))
        add(TripperIcon(0x2C, "Manter faixa, destaque à direita", C, IconDepiction.KeepLane(Side.RIGHT)))
        add(TripperIcon(0x2D, "Saída acentuada à direita", C, IconDepiction.OffRamp(Side.RIGHT, 30), "Verificado no Pod."))
        add(TripperIcon(0x2E, "Saída acentuada à esquerda", C, IconDepiction.OffRamp(Side.LEFT, 30), "Verificado no Pod."))
        add(TripperIcon(0x2F, "Saída: manter/acentuada à direita", C, IconDepiction.OffRamp(Side.RIGHT, 135)))
        add(TripperIcon(0x30, "Saída: manter/acentuada à esquerda", C, IconDepiction.OffRamp(Side.LEFT, 135)))
        add(TripperIcon(0x31, "Rotatória anti-horária (base)", C, IconDepiction.Roundabout(clockwise = false, exit = null)))
        for (exit in 1..9) {
            add(
                TripperIcon(
                    0x32 + exit - 1, "Rotatória, saída $exit", L,
                    IconDepiction.Roundabout(clockwise = false, exit = exit),
                    "Fórmula 0x32 + (saída − 1) usada pela referência; faixa não testada individualmente.",
                ),
            )
        }
        add(unknown(0x3B))
        add(TripperIcon(0x3C, "Partida", C, IconDepiction.Depart, "Também usado na seta pequena para indicar radar."))
        add(TripperIcon(0x3D, "Retorno (anti-horário)", C, IconDepiction.UTurn(clockwise = false)))
        add(TripperIcon(0x3E, "Balsa (barco)", C, IconDepiction.Ferry(train = false)))
        add(TripperIcon(0x3F, "Balsa (trem)", C, IconDepiction.Ferry(train = true)))
        add(unknown(0x40))
        add(
            TripperIcon(
                0x41, "Tela de bússola (seletor)", C, IconDepiction.Unknown,
                "Não é ícone: no byte [2] seleciona a tela de bússola.",
            ),
        )
        add(TripperIcon(0x42, "Sem dados móveis", C, IconDepiction.NoMobileData))
        add(unknown(0x43))
        add(TripperIcon(0x44, "Bateria fraca", C, IconDepiction.LowBattery))
        add(
            TripperIcon(
                0x45, "Rota iniciada", L, IconDepiction.RouteStarted,
                "Visto como ícone grande; na seta pequena o Pod não desenhou nada.",
            ),
        )
        add(TripperIcon(0xFF, "Vazio", C, IconDepiction.Blank, "Sem ícone."))
    }

    private val byCode: Map<Int, TripperIcon> = all.associateBy { it.code }

    public fun byCode(code: Int): TripperIcon? = byCode[code and 0xFF]

    /** Sempre devolve algo: códigos fora do catálogo viram "não verificado / desconhecido". */
    public fun describe(code: Int): TripperIcon = byCode(code) ?: unknown(code and 0xFF)

    private fun unknown(code: Int): TripperIcon = TripperIcon(
        code = code,
        name = "Desconhecido ${"0x" + Hex.byte(code)}",
        confidence = Confidence.UNVERIFIED,
        depiction = IconDepiction.Unknown,
        note = "Faixa não testada; o efeito no display é desconhecido.",
    )
}
