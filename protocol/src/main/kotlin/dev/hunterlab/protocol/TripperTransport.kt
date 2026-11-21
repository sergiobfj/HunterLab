package dev.hunterlab.protocol

import java.util.UUID

/**
 * Fatos de transporte documentados para o Tripper Pod da **Meteor 350** (referência).
 *
 * Observado na **Hunter 350 2027** com a versão 0.1.0 (relato do usuário, nov/2026): nome `RE_DISP`,
 * serviço `01FF0100-…` e característica `01FF0101-…` com **WRITE_NO_RESPONSE**. Handshake, watchdog e
 * janela de anúncio **ainda não** foram verificados na Hunter.
 *
 * A escrita só acontece pelo Laboratório BLE (`core/link`), com autorização explícita por sessão.
 */
public object TripperTransport {
    /** Nome anunciado no scan. Confirmado na Meteor 350; observado na Hunter 350 2027. */
    public const val DEVICE_NAME: String = "RE_DISP"

    /** Serviço "Wireless UART" do SDK BLE da NXP, reaproveitado pelo Pod. */
    public val SERVICE_UUID: UUID = UUID.fromString("01FF0100-BA5E-F4EE-5CA1-EB1E5E4B1CE0")

    /** Característica gravável (WRITE / WRITE_NO_RESPONSE), sem NOTIFY. */
    public val CHARACTERISTIC_UUID: UUID = UUID.fromString("01FF0101-BA5E-F4EE-5CA1-EB1E5E4B1CE0")

    /** Prefixo (OUI) do endereço observado, registrado para a NXP Semiconductors. */
    public const val NXP_OUI_PREFIX: String = "00:60:37"

    /** O Pod anuncia por menos de ~30 s após ligar a ignição (observado). */
    public const val ADVERTISING_WINDOW_MS: Long = 30_000

    /** O Pod derruba o link ~5 s após o último pacote recebido (observado; status 19). */
    public const val FIRMWARE_WATCHDOG_MS: Long = 5_000

    /** Intervalo de keepalive/reenvio usado pela referência (fora do escopo da 0.1.0). */
    public const val KEEPALIVE_INTERVAL_MS: Long = 2_000

    /** Espera entre descoberta de serviços e a primeira escrita (fora do escopo da 0.1.0). */
    public const val POST_DISCOVERY_DELAY_MS: Long = 200
}
