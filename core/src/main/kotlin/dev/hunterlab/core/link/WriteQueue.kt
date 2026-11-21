package dev.hunterlab.core.link

/**
 * Fila serializada: no máximo **uma** escrita em andamento; a próxima só sai depois do callback de
 * conclusão do Android (ou do timeout). Sem threads — quem chama controla o tempo.
 *
 * Conclusão de escrita significa apenas que o Android entregou o pacote ao controlador Bluetooth.
 * Com WRITE_NO_RESPONSE, **não** é confirmação de que o Tripper recebeu ou processou.
 */
internal class WriteQueue(private val maxSize: Int) {

    data class Entry(
        val id: Long,
        val session: Long,
        val command: LinkCommand,
        val enqueuedAtMs: Long,
        val maxAgeMs: Long,
    ) {
        fun isStale(now: Long) = now - enqueuedAtMs > maxAgeMs
    }

    sealed interface Offer {
        data object Accepted : Offer
        data class Replaced(val previous: Entry) : Offer
        data object Duplicate : Offer
        data object Full : Offer
    }

    private val pending = ArrayDeque<Entry>()

    var inFlight: Entry? = null
        private set

    val size: Int get() = pending.size
    fun isEmpty() = pending.isEmpty()
    fun pendingKinds(): List<CommandKind> = pending.map { it.command.kind }

    fun offer(entry: Entry): Offer {
        val key = entry.command.dedupeKey
        if (key != null) {
            val samePending = pending.indexOfFirst { it.command.dedupeKey == key }
            when (entry.command.policy) {
                DedupePolicy.UNIQUE ->
                    if (samePending >= 0 || inFlight?.command?.dedupeKey == key) return Offer.Duplicate
                DedupePolicy.DROP_NEW -> if (samePending >= 0) return Offer.Duplicate
                DedupePolicy.REPLACE -> if (samePending >= 0) {
                    val old = pending[samePending]
                    pending[samePending] = entry
                    return Offer.Replaced(old)
                }
                DedupePolicy.NONE -> Unit
            }
        }
        if (pending.size >= maxSize) return Offer.Full
        pending.addLast(entry)
        return Offer.Accepted
    }

    /** Próxima entrada válida; descarta (e devolve) as obsoletas encontradas no caminho. */
    fun poll(now: Long): Pair<Entry?, List<Entry>> {
        check(inFlight == null) { "Já existe escrita em andamento" }
        val stale = mutableListOf<Entry>()
        while (pending.isNotEmpty()) {
            val e = pending.removeFirst()
            if (e.isStale(now)) stale += e else return e to stale
        }
        return null to stale
    }

    fun markInFlight(entry: Entry) {
        check(inFlight == null) { "Já existe escrita em andamento" }
        inFlight = entry
    }

    fun completeInFlight(): Entry? = inFlight.also { inFlight = null }

    fun removeKind(kind: CommandKind): Int {
        val before = pending.size
        pending.removeAll { it.command.kind == kind }
        return before - pending.size
    }

    /** Cancelamento imediato: esvazia a fila e esquece a escrita em andamento. */
    fun clear(): Int {
        val n = pending.size + (if (inFlight != null) 1 else 0)
        pending.clear()
        inFlight = null
        return n
    }
}
