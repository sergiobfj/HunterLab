package dev.hunterlab.app

import android.app.Application
import dev.hunterlab.app.ble.BleController

class HunterLabApplication : Application() {
    lateinit var ble: BleController
        private set

    override fun onCreate() {
        super.onCreate()
        ble = BleController(this)
    }
}
