package dev.hunterlab.protocol

/** Conversão hexadecimal independente de Locale (sempre dígitos ASCII maiúsculos). */
public object Hex {
    private const val DIGITS = "0123456789ABCDEF"

    public fun byte(value: Int): String {
        val v = value and 0xFF
        return "${DIGITS[v shr 4]}${DIGITS[v and 0x0F]}"
    }

    public fun encode(bytes: ByteArray, separator: String = " "): String =
        bytes.joinToString(separator) { byte(it.toInt()) }

    /**
     * Aceita `"10 11 1C"`, `"10111C"`, `"10:11:1c"` ou `"0x10 0x11"`.
     * @throws IllegalArgumentException se houver caractere inválido ou número ímpar de dígitos.
     */
    public fun decode(text: String): ByteArray {
        val clean = text.replace("0x", "", ignoreCase = true)
            .filterNot { it.isWhitespace() || it == ':' || it == '-' || it == ',' }
        require(clean.length % 2 == 0) { "Número ímpar de dígitos hexadecimais (${clean.length})" }
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[2 * i], 16)
            val lo = Character.digit(clean[2 * i + 1], 16)
            require(hi >= 0 && lo >= 0) { "Caractere não hexadecimal em \"${clean.substring(2 * i, 2 * i + 2)}\"" }
            ((hi shl 4) or lo).toByte()
        }
    }
}
