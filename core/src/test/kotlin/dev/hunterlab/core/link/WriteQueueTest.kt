package dev.hunterlab.core.link

import dev.hunterlab.protocol.CompassDirection
import dev.hunterlab.protocol.DayPeriod
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalTime

class WriteQueueTest {
    private var id = 0L
    private fun entry(cmd: LinkCommand, at: Long = 0, maxAge: Long = 1_500) = WriteQueue.Entry(id++, 1, cmd, at, maxAge)

    @Test
    fun `fifo com uma escrita por vez`() {
        val q = WriteQueue(8)
        val a = entry(LinkCommand.showPin())
        val b = entry(LinkCommand.handshakePing())
        q.offer(a); q.offer(b)
        val (first, _) = q.poll(0)
        assertSame(a, first)
        q.markInFlight(first!!)
        assertThrows<IllegalStateException> { q.poll(0) }
        assertSame(a, q.completeInFlight())
        assertSame(b, q.poll(0).first)
    }

    @Test
    fun `limite de fila`() {
        val q = WriteQueue(2)
        assertEquals(WriteQueue.Offer.Accepted, q.offer(entry(LinkCommand.handshakePing())))
        assertEquals(WriteQueue.Offer.Accepted, q.offer(entry(LinkCommand.handshakePing())))
        assertEquals(WriteQueue.Offer.Full, q.offer(entry(LinkCommand.handshakePing())))
    }

    @Test
    fun `unico rejeita duplicado pendente ou em envio`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.pin("1234")))
        assertEquals(WriteQueue.Offer.Duplicate, q.offer(entry(LinkCommand.pin("9999"))))
        q.markInFlight(q.poll(0).first!!)
        assertEquals(WriteQueue.Offer.Duplicate, q.offer(entry(LinkCommand.pin("9999"))))
    }

    @Test
    fun `keepalive nao acumula e pings da retomada nao sao deduplicados`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.keepalivePing()))
        assertEquals(WriteQueue.Offer.Duplicate, q.offer(entry(LinkCommand.keepalivePing())))
        assertEquals(WriteQueue.Offer.Accepted, q.offer(entry(LinkCommand.handshakePing())))
        assertEquals(WriteQueue.Offer.Accepted, q.offer(entry(LinkCommand.handshakePing())))
        assertEquals(3, q.size)
    }

    @Test
    fun `tela obsoleta e substituida pela mais nova na mesma posicao`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.handshakePing()))
        val old = entry(LinkCommand.compass(CompassDirection.N, DayPeriod.DAY))
        q.offer(old)
        val new = entry(LinkCommand.compass(CompassDirection.S, DayPeriod.DAY))
        assertEquals(WriteQueue.Offer.Replaced(old), q.offer(new))
        assertEquals(2, q.size)
        q.poll(0).first.also { q.markInFlight(it!!) }
        q.completeInFlight()
        assertSame(new, q.poll(0).first)
    }

    @Test
    fun `entradas obsoletas sao descartadas na retirada`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.keepalivePing(), at = 0, maxAge = 1_000))
        val fresh = entry(LinkCommand.setTime(LocalTime.NOON, dev.hunterlab.protocol.ClockFormat.H24), at = 900, maxAge = 1_000)
        q.offer(fresh)
        val (e, stale) = q.poll(1_500)
        assertSame(fresh, e)
        assertEquals(1, stale.size)
    }

    @Test
    fun `cancelamento esvazia tudo, inclusive a escrita em andamento`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.showPin()))
        q.offer(entry(LinkCommand.handshakePing()))
        q.markInFlight(q.poll(0).first!!)
        assertEquals(2, q.clear())
        assertNull(q.inFlight)
        assertTrue(q.isEmpty())
    }

    @Test
    fun `remove todos de um tipo`() {
        val q = WriteQueue(8)
        q.offer(entry(LinkCommand.compass(CompassDirection.N, DayPeriod.DAY)))
        q.offer(entry(LinkCommand.handshakePing()))
        assertEquals(1, q.removeKind(CommandKind.COMPASS))
        assertEquals(listOf(CommandKind.PING), q.pendingKinds())
    }
}
