package dev.hunterlab.protocol

/**
 * Nível de confiança de uma informação do protocolo, segundo a referência
 * (jnsdias/wazetripper, docs/PROTOCOL.md), validada num Tripper Pod da **Meteor 350**.
 *
 * Nada aqui foi verificado na Hunter 350 2027: veja [HunterValidation].
 */
public enum class Confidence(public val label: String, public val description: String) {
    CONFIRMED(
        label = "Confirmado",
        description = "Visto funcionando no Tripper Pod da Meteor 350 (segundo a referência).",
    ),
    LIKELY(
        label = "Provável",
        description = "Consistente entre fontes, mas sem verificação em hardware.",
    ),
    UNVERIFIED(
        label = "Não verificado",
        description = "Hipótese ou palpite; exige teste físico.",
    );

    /** A menor confiança entre duas: uma composição é tão confiável quanto sua parte mais fraca. */
    public fun weakest(other: Confidence): Confidence = if (other.ordinal > ordinal) other else this
}

/** Situação de cada item na Hunter 350 2027. Na versão 0.1.0 tudo está [NOT_TESTED]. */
public enum class HunterValidation(public val label: String) {
    NOT_TESTED("Não testado na Hunter 350"),
    WORKS("Funciona na Hunter 350"),
    DIFFERS("Diferente na Hunter 350"),
    FAILS("Não funciona na Hunter 350"),
}
