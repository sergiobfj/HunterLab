package dev.hunterlab.protocol

/**
 * CRC-16/CCITT-FALSE: polinômio 0x1021, valor inicial 0xFFFF, MSB primeiro,
 * sem reflexão e sem XOR final. Valor de verificação padrão: `"123456789"` → `0x29B1`.
 *
 * No Tripper, o CRC cobre os bytes `[0..17]` e é gravado big-endian em `[18..19]`.
 */
public object Crc16CcittFalse {
    public const val POLYNOMIAL: Int = 0x1021
    public const val INITIAL: Int = 0xFFFF

    public fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
        require(offset >= 0 && length >= 0 && offset + length <= data.size) {
            "Intervalo inválido para CRC: offset=$offset, length=$length, tamanho=${data.size}"
        }
        var crc = INITIAL
        for (i in offset until offset + length) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) {
                    ((crc shl 1) xor POLYNOMIAL) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }
}
