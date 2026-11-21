package dev.hunterlab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.hunterlab.app.ui.HunterLabRoot
import dev.hunterlab.app.ui.theme.HunterLabTheme

class MainActivity : ComponentActivity() {
    private val ble get() = (application as HunterLabApplication).ble

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HunterLabTheme { HunterLabRoot(ble) }
        }
    }

    override fun onResume() {
        super.onResume()
        ble.refresh() // permissões podem ter mudado nas configurações
    }

    /** Segurança: sem o app em primeiro plano não há botão Interromper visível, então a sessão é interrompida. */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) ble.lab.interrupt("app saiu da tela")
    }
}
