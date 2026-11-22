package dev.hunterlab.core.playground

import dev.hunterlab.protocol.DayPeriod

data class PlaygroundSelection(val symbolId: String = "compass-N", val period: DayPeriod = DayPeriod.NIGHT, val slot: IconSlot = IconSlot.LARGE) {
    init { require(PlaygroundCatalog.find(symbolId) != null) }
    val symbol get() = requireNotNull(PlaygroundCatalog.find(symbolId))
    val preview get() = symbol.preview(period, slot)
    fun select(id: String) = copy(symbolId = id)
}
