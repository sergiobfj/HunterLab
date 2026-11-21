package dev.hunterlab.app.ui.home

import dev.hunterlab.app.ui.contract.DestinationUi
import dev.hunterlab.app.ui.contract.NavActions
import dev.hunterlab.app.ui.contract.SettingsUi
import dev.hunterlab.core.display.BottomLineMode
import dev.hunterlab.core.display.DayNightMode

/** Na base publicada, só a aparência da prévia muda. Ações de rota ficam desabilitadas. */
class LocalAppearanceActions(private val update: ((SettingsUi) -> SettingsUi) -> Unit) : NavActions {
    override fun setDayNight(mode: DayNightMode) = update { it.copy(dayNight = mode) }
    override fun setBottomLine(mode: BottomLineMode) = update { it.copy(bottomLine = mode) }
    override fun setRadarOnTripper(enabled: Boolean) = Unit
    override fun search(query: String) = Unit
    override fun selectDestination(destination: DestinationUi) = Unit
    override fun setDestinationFromText(text: String): String = "Navegação real indisponível nesta versão."
    override fun clearDestination() = Unit
    override fun previewRoute() = Unit
    override fun startNavigation(simulated: Boolean) = Unit
    override fun stopNavigation() = Unit
    override fun acceptTerms() = Unit
    override fun connectTripper() = Unit
    override fun requestPin() = Unit
    override fun submitPin(pin: String) = Unit
    override fun confirmPinAccepted() = Unit
    override fun reportPinRejected() = Unit
    override fun interruptTripper() = Unit
    override fun setApiKey(key: String) = Unit
    override fun clearApiKey() = Unit
    override fun setRadarEnabled(enabled: Boolean) = Unit
    override fun setRadarThresholds(searchM: Int, alertM: Int) = Unit
    override fun refreshPermissions() = Unit
}
