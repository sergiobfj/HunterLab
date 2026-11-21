package dev.hunterlab.app

import android.app.Application
import dev.hunterlab.app.ble.BleController
import dev.hunterlab.app.nav.NavigationController

class HunterLabApplication : Application() {
    lateinit var ble: BleController
        private set

    /** Navegação do MVP (criada sob demanda; não conecta nem transmite nada ao ser criada). */
    val navigation: NavigationController by lazy { NavigationController(this, ble) }

    override fun onCreate() {
        super.onCreate()
        ble = BleController(this)
    }
}
