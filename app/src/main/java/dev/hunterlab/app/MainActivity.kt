package dev.hunterlab.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hunterlab.app.ui.HunterLabRoot
import dev.hunterlab.app.ui.theme.HunterLabTheme

class MainActivity : ComponentActivity() {
    private val ble get() = (application as HunterLabApplication).ble
    private val nav get() = (application as HunterLabApplication).navigation

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleGeoIntent(intent)
        setContent {
            HunterLabTheme { HunterLabRoot(ble, nav.bindings) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGeoIntent(intent)
    }

    /** Destino compartilhado por outro app (`geo:lat,lon`). Só preenche o destino; nada é transmitido. */
    private fun handleGeoIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW && data.scheme == "geo") nav.onExternalDestination(data.toString())
    }

    override fun onResume() {
        super.onResume()
        ble.refresh() // permissões podem ter mudado nas configurações
        nav.refreshPermissions()
    }

    /** Segurança: sem o app em primeiro plano não há botão Interromper visível, então a sessão é interrompida. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            ble.lab.interruptIfLab("app saiu da tela")
            nav.onAppBackgrounded()
        }
    }
}
