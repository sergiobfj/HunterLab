package dev.hunterlab.app.ui.navigation

import dev.hunterlab.app.ui.contract.NavPhaseUi
import dev.hunterlab.app.ui.contract.NavUi

/** Uma manobra antiga não deve continuar parecendo válida durante um recálculo ou erro. */
val NavUi.showCurrentManeuver: Boolean
    get() = phase == NavPhaseUi.NAVIGATING && current != null

val NavUi.journeyOpen: Boolean
    get() = phase != NavPhaseUi.IDLE

val NavUi.calculating: Boolean
    get() = phase == NavPhaseUi.ROUTING || phase == NavPhaseUi.REROUTING

val NavUi.showJourneyMetrics: Boolean
    get() = phase == NavPhaseUi.NAVIGATING &&
        (remainingDistanceText != null || remainingTimeText != null || arrivalText != null)
