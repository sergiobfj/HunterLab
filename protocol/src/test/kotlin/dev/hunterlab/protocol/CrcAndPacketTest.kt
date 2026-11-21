package dev.hunterlab.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

/**
 * Origem das expectativas (mesma convenção da referência):
 *  - REAL: pacote capturado de um Tripper real que o desenhou;
 *  - EXT: valor de referência externo (check value do CRC, pacotes de outros apps);
 *  - LOCK: trava de regressão de layout ainda não visto no hardware.
 */
class CrcAndPacketTest {

    /** Implementação independente, por tabela (algoritmo diferente da produção). */
    private val table = IntArray(256) { i ->
        var c = i shl 8
        repeat(8) { c = if (c and 0x8000 != 0) (c shl 1) xor 0x1021 else c shl 1 }
        c and 0xFFFF
    }

    private fun tableCrc(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) crc = ((crc shl 8) xor table[((crc shr 8) xor (b.toInt() and 0xFF)) and 0xFF]) and 0xFFFF
        return crc
    }

    @Test
    fun `EXT crc do check value padrao 123456789 e 0x29B1`() {
        assertEquals(0x29B1, Crc16CcittFalse.compute("123456789".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `crc de entrada vazia e o valor inicial`() {
        assertEquals(0xFFFF, Crc16CcittFalse.compute(ByteArray(0)))
    }

    @Test
    fun `crc de producao concorda com implementacao por tabela em 2000 payloads aleatorios`() {
        val rnd = Random(2027)
        repeat(2000) {
            val payload = rnd.nextBytes(18)
            assertEquals(tableCrc(payload), Crc16CcittFalse.compute(payload))
        }
    }

    @Test
    fun `crc respeita offset e length`() {
        val data = Hex.decode("AA 31 32 33 34 35 36 37 38 39 BB")
        assertEquals(0x29B1, Crc16CcittFalse.compute(data, offset = 1, length = 9))
    }

    @Test
    fun `crc rejeita intervalo invalido`() {
        assertThrows<IllegalArgumentException> { Crc16CcittFalse.compute(ByteArray(4), offset = 2, length = 3) }
        assertThrows<IllegalArgumentException> { Crc16CcittFalse.compute(ByteArray(4), offset = -1, length = 1) }
    }

    @Test
    fun `EXT STOP_NAV de outro app tem CRC F7 82`() {
        val p = TripperPacket.fromPayload(Hex.decode("10 11 1C 00 00 01 00 FF 00 00 00 00 00 00 00 00 00 00"))
        assertEquals("10 11 1C 00 00 01 00 FF 00 00 00 00 00 00 00 00 00 00 F7 82", p.toHex())
    }

    @Test
    fun `pacote sempre tem 20 bytes e CRC big-endian dos 18 primeiros`() {
        val payload = Random(1).nextBytes(18)
        val p = TripperPacket.fromPayload(payload)
        val bytes = p.toByteArray()
        assertEquals(20, bytes.size)
        val crc = tableCrc(payload)
        assertEquals((crc shr 8) and 0xFF, bytes[18].toInt() and 0xFF)
        assertEquals(crc and 0xFF, bytes[19].toInt() and 0xFF)
        assertEquals(crc, p.crc)
        assertArrayEquals(payload, p.payload())
    }

    @Test
    fun `payload com tamanho errado e rejeitado com erro tipado`() {
        for (size in listOf(0, 17, 19, 20)) {
            val e = assertThrows<PacketFormatException> { TripperPacket.fromPayload(ByteArray(size)) }
            assertEquals(PacketError.WrongPayloadLength(18, size), e.error)
        }
    }

    @Test
    fun `parse rejeita comprimento diferente de 20`() {
        for (size in listOf(0, 18, 19, 21, 40)) {
            val r = TripperPacket.parse(ByteArray(size))
            assertEquals(PacketParseResult.Invalid(PacketError.WrongLength(20, size)), r)
        }
    }

    @Test
    fun `parse detecta CRC corrompido em qualquer bit`() {
        val good = TripperCommands.pingFirmware().toByteArray()
        for (byteIndex in 0 until 20) {
            for (bit in 0 until 8) {
                val bad = good.copyOf()
                bad[byteIndex] = (bad[byteIndex].toInt() xor (1 shl bit)).toByte()
                val r = TripperPacket.parse(bad)
                assertTrue(r is PacketParseResult.Invalid && r.error is PacketError.CrcMismatch, "byte $byteIndex bit $bit")
            }
        }
    }

    @Test
    fun `parse aceita pacote valido e parseHex aceita formatos variados`() {
        val hex = "03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 45 D9"
        val a = TripperPacket.parseHex(hex)
        val b = TripperPacket.parseHex(hex.replace(" ", ":").lowercase())
        assertTrue(a is PacketParseResult.Valid)
        assertEquals(a, b)
    }

    @Test
    fun `parseHex devolve erro tipado para texto invalido`() {
        assertTrue((TripperPacket.parseHex("0G 11") as PacketParseResult.Invalid).error is PacketError.MalformedHex)
        assertTrue((TripperPacket.parseHex("0") as PacketParseResult.Invalid).error is PacketError.MalformedHex)
        assertThrows<PacketFormatException> { TripperPacket.ofHex("10 11") }
    }

    @Test
    fun `pacote e imutavel`() {
        val p = TripperCommands.pingFirmware()
        val copy = p.toByteArray()
        copy[0] = 0x7F
        assertEquals(0x03, p[0])
        assertNotEquals(TripperPacket.fromPayload(copy.copyOf(18)), p)
    }

    @Test
    fun `indice fora do pacote e rejeitado`() {
        val p = TripperCommands.pingFirmware()
        assertThrows<IllegalArgumentException> { p[20] }
        assertThrows<IllegalArgumentException> { p[-1] }
    }

    @Test
    fun `hex nao depende de locale`() {
        assertEquals("00 7F 80 FF", Hex.encode(byteArrayOf(0, 0x7F, 0x80.toByte(), 0xFF.toByte())))
        assertEquals("0A", Hex.byte(10))
    }
}
