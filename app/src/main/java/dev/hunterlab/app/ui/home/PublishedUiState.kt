package dev.hunterlab.app.ui.home

import dev.hunterlab.app.ui.contract.HomeUi
import dev.hunterlab.app.ui.contract.TripperUi
import dev.hunterlab.app.ui.contract.TripperUiStatus
import dev.hunterlab.core.link.LinkSnapshot
import dev.hunterlab.core.link.LinkState

/** Somente apresentação do estado real do laboratório; não abre nem autentica uma sessão. */
fun publishedHomeState(link: LinkSnapshot): HomeUi {
    val status = when (link.state) {
        LinkState.DISCONNECTED -> TripperUiStatus.DISCONNECTED
        LinkState.CONNECTING, LinkState.DISCOVERING -> TripperUiStatus.CONNECTING
        LinkState.READY_FOR_AUTH -> TripperUiStatus.READY_TO_PAIR
        LinkState.WAITING_FOR_PIN -> TripperUiStatus.WAITING_PIN
        LinkState.AUTHENTICATING -> TripperUiStatus.CONFIRM_PIN
        LinkState.AUTHENTICATED -> TripperUiStatus.AUTHENTICATED
        LinkState.DISPLAY_ACTIVE -> TripperUiStatus.SHOWING
        LinkState.STOPPING -> TripperUiStatus.STOPPING
        LinkState.ERROR -> TripperUiStatus.ERROR
    }
    val label = when (status) {
        TripperUiStatus.DISCONNECTED -> "Tripper desconectado"
        TripperUiStatus.CONNECTING -> "Conectando ao Tripper"
        TripperUiStatus.READY_TO_PAIR -> "Tripper pronto para conectar"
        TripperUiStatus.WAITING_PIN -> "Confira o PIN no Tripper"
        TripperUiStatus.CONFIRM_PIN -> "Confirme a conexão no display"
        TripperUiStatus.AUTHENTICATED -> "Tripper conectado"
        TripperUiStatus.SHOWING -> "Tripper exibindo teste"
        TripperUiStatus.STOPPING -> "Encerrando conexão"
        TripperUiStatus.ERROR -> "Falha na conexão do Tripper"
    }
    return HomeUi(tripper = TripperUi(status = status, label = label, canConnect = !link.active))
}
