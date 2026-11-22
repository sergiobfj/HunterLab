package dev.hunterlab.core.playground

import dev.hunterlab.protocol.DayPeriod
import kotlinx.serialization.json.*

enum class ExperimentKind(val label: String) { SIMULATED("Simulado"), PHYSICAL("Físico") }
enum class ExperimentEnd(val label: String) {
    DURATION("Limite de duração"), USER_STOP("Interrompido pelo usuário"), DISCONNECTED("Link encerrado"),
    NAVIGATION_PRIORITY("Navegação tem prioridade"), LEFT_SCREEN("Playground fechado"), ERROR("Falha na sessão"),
}
enum class VisualObservation(val label: String) {
    NOT_RECORDED("Não observado"), MATCHED("Correspondeu à prévia"), DIFFERENT("Símbolo diferente"),
    NO_CHANGE("Sem mudança visível"), BLANK("Visor vazio"), FLICKER("Piscou ou ficou instável"),
}
enum class ClockRecovery(val label: String) {
    NOT_CHECKED("Não conferido"), WATCHDOG("Relógio voltou espontaneamente"),
    IGNITION("Voltou após ciclo de ignição"), NOT_RECOVERED("Ainda não voltou"),
}

/** Sem campos livres: observações estruturadas impedem PIN, MAC, chave ou nome de pessoa no histórico. */
data class PlaygroundExperiment(
    val atEpochMs: Long, val symbols: List<String>, val kind: ExperimentKind, val period: DayPeriod,
    val durationMs: Long, val delivered: Int?, val responses: Int?, val end: ExperimentEnd,
    val observation: VisualObservation = VisualObservation.NOT_RECORDED,
    val clockRecovery: ClockRecovery = ClockRecovery.NOT_CHECKED,
) {
    init {
        require(atEpochMs >= 0 && durationMs in 0..PreviewSequence.MAX_DURATION_MS)
        require(symbols.isNotEmpty() && symbols.size <= PreviewSequence.MAX_STAGES && symbols.all { PlaygroundCatalog.find(it) != null })
        require(delivered == null || delivered in 0..1_000_000)
        require(responses == null || responses in 0..1_000_000)
        require(kind != ExperimentKind.SIMULATED || delivered == null && responses == null)
        require(kind != ExperimentKind.PHYSICAL || symbols.size == 1 && symbols.single() in PlaygroundCatalog.physicalIds)
    }
}

object ExperimentLog {
    const val MAX_RECORDS = 50
    fun encode(records: List<PlaygroundExperiment>): String = buildJsonArray {
        records.takeLast(MAX_RECORDS).forEach { r -> add(buildJsonObject {
            put("schema", 1); put("at", r.atEpochMs); putJsonArray("symbols") { r.symbols.forEach { add(JsonPrimitive(it)) } }
            put("kind", r.kind.name); put("period", r.period.name); put("durationMs", r.durationMs)
            put("delivered", r.delivered?.let(::JsonPrimitive) ?: JsonNull)
            put("responses", r.responses?.let(::JsonPrimitive) ?: JsonNull)
            put("end", r.end.name); put("observation", r.observation.name); put("clock", r.clockRecovery.name)
        }) }
    }.toString()
    fun decode(text: String): List<PlaygroundExperiment> {
        if (text.length > 100_000) return emptyList()
        return runCatching { Json.parseToJsonElement(text).jsonArray.takeLast(MAX_RECORDS).mapNotNull { e -> runCatching {
            val o = e.jsonObject
            require(o.getValue("schema").jsonPrimitive.int == 1)
            PlaygroundExperiment(
                o.getValue("at").jsonPrimitive.long, o.getValue("symbols").jsonArray.map { it.jsonPrimitive.content },
                enumValueOf(o.getValue("kind").jsonPrimitive.content), enumValueOf(o.getValue("period").jsonPrimitive.content),
                o.getValue("durationMs").jsonPrimitive.long,
                o["delivered"]?.jsonPrimitive?.intOrNull, o["responses"]?.jsonPrimitive?.intOrNull,
                enumValueOf(o.getValue("end").jsonPrimitive.content), enumValueOf(o.getValue("observation").jsonPrimitive.content),
                enumValueOf(o.getValue("clock").jsonPrimitive.content),
            )
        }.getOrNull() } }.getOrDefault(emptyList())
    }
    fun report(records: List<PlaygroundExperiment>): String = buildString {
        appendLine("Hunter Lab 0.3.1 — Tripper Playground")
        appendLine("Escrita aceita pelo Android não comprova o efeito no visor. Horário em epoch ms.")
        records.takeLast(MAX_RECORDS).forEach { r ->
            appendLine("${r.atEpochMs} · ${r.kind.label} · ${r.period.label} · ${r.durationMs} ms")
            appendLine(r.symbols.joinToString { requireNotNull(PlaygroundCatalog.find(it)).label })
            appendLine("Entregues: ${r.delivered ?: "não se aplica"}; respostas: ${r.responses ?: "não se aplica"}; ${r.end.label}")
            appendLine("Observação: ${r.observation.label}; relógio: ${r.clockRecovery.label}")
        }
    }
}
