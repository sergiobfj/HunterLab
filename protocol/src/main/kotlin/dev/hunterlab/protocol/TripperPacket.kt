package dev.hunterlab.protocol

/**
 * Pacote do Tripper Pod: sempre **20 bytes** = 18 de payload + CRC-16/CCITT-FALSE big-endian.
 *
 * Imutável: o array interno nunca é exposto. Na versão 0.1.0 os pacotes existem
 * **somente em memória** — nenhum código do projeto os transmite.
 */
public class TripperPacket private constructor(private val bytes: ByteArray) {

    public operator fun get(index: Int): Int {
        require(index in 0 until SIZE) { "Índice fora do pacote: $index (0..${SIZE - 1})" }
        return bytes[index].toInt() and 0xFF
    }

    public val command: Int get() = this[0]
    public val crc: Int get() = (this[18] shl 8) or this[19]
    public fun payload(): ByteArray = bytes.copyOfRange(0, PAYLOAD_SIZE)
    public fun toByteArray(): ByteArray = bytes.copyOf()
    public fun toHex(): String = Hex.encode(bytes)

    override fun equals(other: Any?): Boolean = other is TripperPacket && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "TripperPacket(${toHex()})"

    public companion object {
        public const val SIZE: Int = 20
        public const val PAYLOAD_SIZE: Int = 18

        /** Monta um pacote a partir de 18 bytes de payload, calculando o CRC. */
        public fun fromPayload(payload: ByteArray): TripperPacket {
            if (payload.size != PAYLOAD_SIZE) {
                throw PacketFormatException(PacketError.WrongPayloadLength(PAYLOAD_SIZE, payload.size))
            }
            val crc = Crc16CcittFalse.compute(payload)
            val out = payload.copyOf(SIZE)
            out[18] = (crc shr 8).toByte()
            out[19] = crc.toByte()
            return TripperPacket(out)
        }

        /** Valida comprimento e CRC de 20 bytes recebidos/capturados. */
        public fun parse(bytes: ByteArray): PacketParseResult {
            if (bytes.size != SIZE) return PacketParseResult.Invalid(PacketError.WrongLength(SIZE, bytes.size))
            val expected = Crc16CcittFalse.compute(bytes, 0, PAYLOAD_SIZE)
            val actual = ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
            if (expected != actual) return PacketParseResult.Invalid(PacketError.CrcMismatch(expected, actual))
            return PacketParseResult.Valid(TripperPacket(bytes.copyOf()))
        }

        public fun parseHex(text: String): PacketParseResult {
            val bytes = try {
                Hex.decode(text)
            } catch (e: IllegalArgumentException) {
                return PacketParseResult.Invalid(PacketError.MalformedHex(e.message ?: "hex inválido"))
            }
            return parse(bytes)
        }

        /** Atalho para literais conhecidos (testes e constantes); lança se inválido. */
        public fun ofHex(text: String): TripperPacket = when (val r = parseHex(text)) {
            is PacketParseResult.Valid -> r.packet
            is PacketParseResult.Invalid -> throw PacketFormatException(r.error)
        }
    }
}

public sealed interface PacketParseResult {
    public data class Valid(val packet: TripperPacket) : PacketParseResult
    public data class Invalid(val error: PacketError) : PacketParseResult
}

public sealed interface PacketError {
    public val message: String

    public data class WrongLength(val expected: Int, val actual: Int) : PacketError {
        override val message: String get() = "Pacote com $actual bytes; o Tripper usa exatamente $expected."
    }

    public data class WrongPayloadLength(val expected: Int, val actual: Int) : PacketError {
        override val message: String get() = "Payload com $actual bytes; esperado $expected (o CRC ocupa os 2 últimos)."
    }

    public data class CrcMismatch(val expected: Int, val actual: Int) : PacketError {
        override val message: String
            get() = "CRC inválido: calculado ${Hex.byte(expected shr 8)} ${Hex.byte(expected)}, " +
                "recebido ${Hex.byte(actual shr 8)} ${Hex.byte(actual)}."
    }

    public data class MalformedHex(val detail: String) : PacketError {
        override val message: String get() = "Texto hexadecimal inválido: $detail"
    }
}

public class PacketFormatException(public val error: PacketError) : IllegalArgumentException(error.message)
