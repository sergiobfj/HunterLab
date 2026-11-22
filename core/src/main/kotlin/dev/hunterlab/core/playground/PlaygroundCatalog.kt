package dev.hunterlab.core.playground

import dev.hunterlab.core.display.PreviewMapper
import dev.hunterlab.core.display.TripperPreview
import dev.hunterlab.protocol.*

enum class PlaygroundCategory(val label: String) { COMPASS("Bússola"), NAVIGATION("Navegação"), INDICATORS("Indicadores") }
enum class VisualEvidence(val label: String) {
    HUNTER("Observado na Hunter"), REFERENCE("Confirmado na referência"),
    LIKELY("Provável"), UNVERIFIED("Não verificado"),
}

enum class SpecialPreview { CLOCK, CALL }

data class PlaygroundSymbol(
    val id: String, val label: String, val category: PlaygroundCategory,
    val confidence: Confidence, val iconCode: Int? = null, val direction: CompassDirection? = null, val special: SpecialPreview? = null,
) {
    val hardwareEligible: Boolean get() = direction != null
    val builder: String get() = when {
        special == SpecialPreview.CLOCK -> "TripperScreen.NativeClock (sem pacote)"
        special == SpecialPreview.CALL -> "TripperCommands.callIcon"
        direction != null -> "TripperCommands.compass"
        iconCode == TripperIcons.ROUTE_STARTED -> "TripperCommands.routeStarted"
        iconCode == TripperIcons.RECALCULATING -> "TripperCommands.recalculating"
        else -> "TripperCommands.navigation"
    }
    val command: String get() = when { special == SpecialPreview.CLOCK -> "sem envio"; special == SpecialPreview.CALL -> "40 05"; direction != null -> "10 11 41"; else -> "10 11" }
    fun evidence(period: DayPeriod): VisualEvidence = when {
        special == SpecialPreview.CLOCK -> VisualEvidence.HUNTER
        direction == CompassDirection.N && period == DayPeriod.NIGHT -> VisualEvidence.HUNTER
        direction == CompassDirection.E && period == DayPeriod.DAY -> VisualEvidence.HUNTER
        confidence == Confidence.CONFIRMED -> VisualEvidence.REFERENCE
        confidence == Confidence.LIKELY -> VisualEvidence.LIKELY
        else -> VisualEvidence.UNVERIFIED
    }
    fun preview(period: DayPeriod, slot: IconSlot = IconSlot.LARGE): TripperPreview {
        val screen = when {
            special == SpecialPreview.CLOCK -> TripperScreen.NativeClock
            direction != null -> TripperScreen.Compass(direction, period)
            iconCode == TripperIcons.ROUTE_STARTED -> TripperScreen.RouteStarted(period)
            iconCode == TripperIcons.RECALCULATING -> TripperScreen.Recalculating(period)
            else -> TripperScreen.Navigation(NavigationFrame(
                icon = if (slot == IconSlot.LARGE) (iconCode ?: TripperIcons.STRAIGHT) else TripperIcons.STRAIGHT,
                nextIcon = if (slot == IconSlot.SMALL) (iconCode ?: TripperIcons.STRAIGHT) else TripperIcons.NONE,
                distanceMeters = 250, byte6 = DisplayIntensity.nightFlag(period), bottomLine = BottomLine.TotalDistance(2_500),
            ))
        }
        return PreviewMapper.from(screen, period).let { if (special == SpecialPreview.CALL) it.copy(callOverlay = true) else it }
    }
}

/** Os 71 códigos são catálogo da referência, não 71 confirmações na Hunter. Não aceita bytes livres. */
object PlaygroundCatalog {
    val all: List<PlaygroundSymbol> = CompassDirection.entries.map {
        PlaygroundSymbol("compass-${it.name}", "Bússola ${it.label}", PlaygroundCategory.COMPASS, Confidence.CONFIRMED, direction = it)
    } + TripperIcons.all.map {
        val indicator = it.depiction is IconDepiction.NoMobileData || it.depiction is IconDepiction.LowBattery ||
            it.depiction is IconDepiction.Blank || it.depiction is IconDepiction.RouteStarted || it.depiction is IconDepiction.Recalculating
        PlaygroundSymbol("icon-${it.code}", it.name, if (indicator) PlaygroundCategory.INDICATORS else PlaygroundCategory.NAVIGATION, it.confidence, it.code)
    }
    private val extraSymbols = listOf(
        PlaygroundSymbol("native-clock", "Relógio original", PlaygroundCategory.INDICATORS, Confidence.CONFIRMED, special = SpecialPreview.CLOCK),
        PlaygroundSymbol("call-overlay", "Indicador de ligação", PlaygroundCategory.INDICATORS, Confidence.CONFIRMED, special = SpecialPreview.CALL),
    )
    val entries: List<PlaygroundSymbol> get() = all + extraSymbols
    fun find(id: String): PlaygroundSymbol? = entries.firstOrNull { it.id == id }
    val physicalIds: Set<String> = all.filter { it.hardwareEligible }.map { it.id }.toSet()
}
