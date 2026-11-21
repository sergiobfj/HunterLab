package dev.hunterlab.core.playground

import dev.hunterlab.protocol.Confidence
import dev.hunterlab.protocol.Hex
import dev.hunterlab.protocol.TripperIcons

enum class IconSlot(val label: String) {
    LARGE("Ícone grande [2]"),
    SMALL("Seta pequena [7]"),
}

data class SequenceStep(val iconCode: Int, val durationMs: Long, val slot: IconSlot = IconSlot.LARGE)

data class SequenceIssue(val message: String, val blocking: Boolean)

/**
 * Sequência simples de símbolos. Na 0.1.0 ela só toca na prévia do app; a execução no
 * hardware é bloqueada por [HardwareExecutionPolicy]. Só aceita códigos do catálogo (nada de imagens).
 */
data class SymbolSequence(val name: String, val steps: List<SequenceStep>) {

    val totalDurationMs: Long get() = steps.sumOf { it.durationMs }

    fun validate(): List<SequenceIssue> = buildList {
        if (steps.isEmpty()) add(SequenceIssue("A sequência está vazia.", blocking = true))
        if (steps.size > MAX_STEPS) add(SequenceIssue("Máximo de $MAX_STEPS passos (há ${steps.size}).", blocking = true))
        steps.forEachIndexed { i, s ->
            val n = i + 1
            if (s.iconCode !in 0..0xFF) add(SequenceIssue("Passo $n: código fora de 1 byte.", blocking = true))
            if (s.durationMs !in MIN_STEP_MS..MAX_STEP_MS) {
                add(SequenceIssue("Passo $n: duração deve ficar entre $MIN_STEP_MS e $MAX_STEP_MS ms.", blocking = true))
            }
            val icon = TripperIcons.byCode(s.iconCode)
            if (icon == null) {
                add(SequenceIssue("Passo $n: 0x${Hex.byte(s.iconCode)} não está no catálogo.", blocking = true))
            } else if (icon.confidence == Confidence.UNVERIFIED) {
                add(SequenceIssue("Passo $n: ${icon.hex} não é verificado.", blocking = false))
            }
            if (s.slot == IconSlot.SMALL && s.iconCode == TripperIcons.ROUTE_STARTED) {
                add(SequenceIssue("Passo $n: 0x45 na seta pequena não desenhou nada na referência.", blocking = false))
            }
        }
    }

    val isValid: Boolean get() = validate().none { it.blocking }

    /** Índice do passo ativo após [elapsedMs]; `null` se terminou (sem [loop]). */
    fun stepIndexAt(elapsedMs: Long, loop: Boolean): Int? {
        require(elapsedMs >= 0) { "Tempo negativo" }
        if (steps.isEmpty() || totalDurationMs <= 0) return null
        val t = if (loop) elapsedMs % totalDurationMs else elapsedMs
        if (t >= totalDurationMs) return null
        var acc = 0L
        steps.forEachIndexed { i, s ->
            acc += s.durationMs
            if (t < acc) return i
        }
        return null
    }

    companion object {
        const val MAX_STEPS = 12
        const val MIN_STEP_MS = 500L
        const val MAX_STEP_MS = 10_000L
    }
}

/** Ponto único de decisão sobre execução em hardware. Bloqueado nesta versão. */
object HardwareExecutionPolicy {
    const val ALLOWED: Boolean = false
    const val REASON: String =
        "Sequências de símbolos não são transmitidas: tocam apenas na prévia do app. Na 0.2.0, só o teste de " +
            "bússola do Laboratório BLE transmite, com autorização. Sequências reais exigem validação física do handshake."
}
