package dev.hunterlab.core.link

import dev.hunterlab.protocol.*
import java.time.LocalTime
import kotlin.random.Random
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/** Synthetic offline frames only. No firmware builder, Bluetooth or device involved. */
class OutboundPacketSafetyTest {
    private val updateOpcodes = listOf(0x02) + (0x04..0x0A)
    private fun synthetic(opcode: Int, fill: Int = 0): TripperPacket =
        TripperPacket.fromPayload(ByteArray(18) { if (it == 0) opcode.toByte() else fill.toByte() })
    private fun allowed(packet: TripperPacket) {
        assertTrue(CommandWhitelist.isTransmittable(packet), packet.toHex())
        assertTrue(CommandWhitelist.isTransmittable(packet.toByteArray()), packet.toHex())
    }

    @Test fun `update opcode is rejected with valid CRC and every declared command kind`() {
        // Complementary fixtures adapted from FirmwareUpdateOpcodeGuardTest @ 4871e00.
        // Fixed seeds make this an offline deterministic regression, not radio probing.
        val random = Random(20261122)
        updateOpcodes.forEach { opcode ->
            val payloads = buildList {
                listOf(0, 1, 0x10, 0x11, 0x21, 0xFF).forEach { add(synthetic(opcode, it).payload()) }
                add(ByteArray(18).also { it[1] = 0x11; it[2] = 0x41; it[14] = 0x10 })
                add(ByteArray(18).also { it[1] = 0x11; it[10] = 0x41; it[5] = 0x01 })
                repeat(2_000) { add(random.nextBytes(18)) }
            }
            payloads.forEach { payload ->
                payload[0] = opcode.toByte()
                val packet = TripperPacket.fromPayload(payload)
                assertTrue(TripperPacket.parse(packet.toByteArray()) is PacketParseResult.Valid)
                assertFalse(CommandWhitelist.isTransmittable(packet))
                assertFalse(CommandWhitelist.isTransmittable(packet.toByteArray()))
                CommandKind.entries.forEach { kind ->
                    val disguised = LinkCommand(kind, packet, "synthetic", null, DedupePolicy.NONE)
                    assertFalse(CommandWhitelist.matches(disguised), "opcode=$opcode kind=$kind")
                }
            }
        }
    }

    @Test fun `all unknown top level opcodes remain blocked instead of a narrow OTA denylist`() {
        val known = setOf(0x03, 0x10, 0x20, 0x21, 0x50)
        (0..255).filterNot { it in known }.forEach { opcode ->
            assertFalse(CommandWhitelist.isTransmittable(synthetic(opcode).toByteArray()), "opcode=$opcode")
        }
    }

    @Test fun `variable length frames are refused including reported OTA sizes`() {
        listOf(0, 1, 19, 21, 22, 41, 247).forEach { size ->
            updateOpcodes.forEach { opcode ->
                val bytes = ByteArray(size)
                if (size > 0) bytes[0] = opcode.toByte()
                assertFalse(CommandWhitelist.isTransmittable(bytes), "size=$size opcode=$opcode")
            }
        }
    }

    @Test fun `corrupted frames never pass the final boundary`() {
        val valid = TripperCommands.handshakeShowPin().toByteArray()
        valid.indices.forEach { i ->
            val corrupted = valid.copyOf()
            corrupted[i] = (corrupted[i].toInt() xor 1).toByte()
            assertFalse(CommandWhitelist.isTransmittable(corrupted), "corruption at $i")
        }
    }

    @Test fun `03 remains exactly the proven ping and not an OTA opcode`() {
        val ping = TripperCommands.pingFirmware()
        assertEquals("03 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 45 D9", ping.toHex())
        allowed(ping)
        assertTrue(CommandWhitelist.matches(CommandKind.PING, ping))
        assertFalse(CommandWhitelist.isTransmittable(synthetic(0x03, 1)))
        val random = Random(3)
        repeat(500) {
            val payload = random.nextBytes(18).also { it[0] = 0x03 }
            // Distorted reserved fields, with a newly valid CRC, must still be refused.
            assertTrue(payload.drop(1).any { it != 0.toByte() })
            assertFalse(CommandWhitelist.isTransmittable(TripperPacket.fromPayload(payload).toByteArray()))
        }
    }

    @Test fun `OTA byte values in documented icon fields are not top level commands`() {
        updateOpcodes.forEach { code ->
            val p = NavigationFrame(code, 300, nextIcon = code).toPacket()
            assertEquals(0x10, p[0]); assertEquals(0x11, p[1])
            assertEquals(code, p[2]); assertEquals(code, p[7])
            allowed(p)
        }
    }

    @Test fun `OTA byte values in clock fields and navigation distances remain valid`() {
        updateOpcodes.forEach { code ->
            allowed(TripperCommands.setTime(code, code, ClockFormat.H24))
            val p = NavigationFrame(0x14, 512 + code, auxDistanceMeters = (code shl 8) or code,
                bottomLine = BottomLine.TotalDistance(code)).toPacket()
            assertEquals(code, p[4]); assertEquals(code, p[8]); assertEquals(code, p[9])
            allowed(p)
        }
        allowed(NavigationFrame(0x14, 2_000).toPacket()) // unit [5] = 02 is not an update
    }

    @Test fun `every sensitive value can appear in CRC without being blocked`() {
        val seen = mutableSetOf<Int>()
        for (h in 0..23) for (m in 0..59) {
            val p = TripperCommands.setTime(h, m, ClockFormat.H24)
            val hits = listOf(p[18], p[19]).filter { it in updateOpcodes }
            if (hits.isNotEmpty()) { seen += hits; allowed(p) }
        }
        assertEquals(updateOpcodes.toSet(), seen, "fixtures must exercise every sensitive value in actual CRC")
    }

    @Test fun `all existing handshake clock compass and navigation builders retain eligibility`() {
        listOf(LinkCommand.showPin(), LinkCommand.resume(), LinkCommand.pin("1234"),
            LinkCommand.handshakePing(), LinkCommand.keepalivePing(), LinkCommand.setTime(LocalTime.NOON, ClockFormat.H24),
            LinkCommand.navigation(NavigationFrame(0x14, 167)), LinkCommand.recalculating(DayPeriod.NIGHT))
            .forEach { assertTrue(CommandWhitelist.matches(it)); allowed(it.packet) }
        CompassDirection.entries.forEach { d -> DayPeriod.entries.forEach { period ->
            allowed(TripperCommands.compass(d, period))
        } }
    }

    @Test fun `catalogue and documented builders never produce update opcodes`() {
        // Catalogue includes preview-only commands: inspecting them does NOT authorize transmission.
        val packets = CommandCatalog.all.map { it.example } +
            CompassDirection.entries.flatMap { d -> DayPeriod.entries.map { TripperCommands.compass(d, it) } } +
            DayPeriod.entries.flatMap { listOf(TripperCommands.recalculating(it), TripperCommands.idleScreen(it)) } +
            listOf(TripperCommands.pingFirmware(), TripperCommands.callIcon(), TripperCommands.routeStarted())
        packets.forEach { assertFalse(it[0] in updateOpcodes, it.toHex()) }
        listOf(TripperCommands.callIcon(), TripperCommands.routeStarted(), TripperCommands.idleScreen(DayPeriod.DAY))
            .forEach { assertFalse(CommandWhitelist.isTransmittable(it.toByteArray())) }
    }

    // Frozen documented layout + independent CRC fixtures, not 16 physical validations.
    private val compassVectors = listOf(
        Triple(CompassDirection.N, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 10 00 00 00 94 76"),
        Triple(CompassDirection.N, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 10 00 00 00 97 03"),
        Triple(CompassDirection.NE, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 50 00 00 00 FA EA"),
        Triple(CompassDirection.NE, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 50 00 00 00 F9 9F"),
        Triple(CompassDirection.E, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 20 00 00 00 B8 9F"),
        Triple(CompassDirection.E, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 20 00 00 00 BB EA"),
        Triple(CompassDirection.SE, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 70 00 00 00 CD A4"),
        Triple(CompassDirection.SE, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 70 00 00 00 CE D1"),
        Triple(CompassDirection.S, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 40 00 00 00 E1 4D"),
        Triple(CompassDirection.S, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 40 00 00 00 E2 38"),
        Triple(CompassDirection.SW, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 80 00 00 00 52 E9"),
        Triple(CompassDirection.SW, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 80 00 00 00 51 9C"),
        Triple(CompassDirection.W, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 30 00 00 00 A3 38"),
        Triple(CompassDirection.W, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 30 00 00 00 A0 4D"),
        Triple(CompassDirection.NW, DayPeriod.DAY, "10 11 41 00 00 00 00 FF 00 00 00 FF FF FF 60 00 00 00 D6 03"),
        Triple(CompassDirection.NW, DayPeriod.NIGHT, "10 11 41 00 00 00 01 FF 00 00 00 FF FF FF 60 00 00 00 D5 76"),
    )

    @Test fun `all sixteen canonical compass frames and their CRCs remain byte exact and allowed`() {
        compassVectors.forEach { (direction, period, hex) ->
            val expected = TripperPacket.ofHex(hex)
            assertEquals(expected, TripperCommands.compass(direction, period))
            assertEquals(expected, LinkCommand.compass(direction, period).packet)
            assertTrue(CommandWhitelist.matches(CommandKind.COMPASS, expected))
            allowed(expected)
        }
    }

    @Test fun `compass rejects noncanonical fields even with valid CRC`() {
        val canonical = compassVectors.map { TripperPacket.ofHex(it.third) }.toSet()
        canonical.forEach { original ->
            // Keep the screen identifiers [0..2]. Exhaust every value in every remaining field.
            for (index in 3 until TripperPacket.PAYLOAD_SIZE) for (value in 0..255) {
                val payload = original.payload().also { it[index] = value.toByte() }
                val mutated = TripperPacket.fromPayload(payload)
                if (mutated in canonical) continue // Changing only to a documented direction/theme is legitimate.
                assertFalse(CommandWhitelist.matches(CommandKind.COMPASS, mutated), "field=$index value=$value")
                assertFalse(CommandWhitelist.isTransmittable(mutated.toByteArray()), "field=$index value=$value")
            }
        }
    }

    @Test fun `normal authorized sessions for every owner produce no update commands`() {
        SessionPurpose.entries.forEach { purpose ->
            val rig = LinkRig()
            rig.connectToReady(options = ConnectOptions(purpose = purpose))
            rig.pairAndAuthenticate()
            rig.link.syncClock()
            rig.runFor(4_000)
            assertTrue(rig.client.writes.isNotEmpty())
            rig.client.writes.forEach { w ->
                assertFalse((w.bytes[0].toInt() and 255) in updateOpcodes)
                assertTrue(CommandWhitelist.isTransmittable(w.bytes))
            }
        }
    }
}
