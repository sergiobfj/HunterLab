package dev.hunterlab.app.ui.navigation

import dev.hunterlab.app.ui.contract.GpsUi
import dev.hunterlab.app.ui.contract.NavPhaseUi
import dev.hunterlab.app.ui.contract.NavUi

/** Não apresenta uma instrução antiga como atual durante recálculo ou perda de posição. */
val NavUi.showCurrentManeuver: Boolean
    get() = phase == NavPhaseUi.NAVIGATING && gps == GpsUi.OK && current != null

val NavUi.showJourneyMetrics: Boolean
    get() = phase == NavPhaseUi.NAVIGATING && gps == GpsUi.OK
